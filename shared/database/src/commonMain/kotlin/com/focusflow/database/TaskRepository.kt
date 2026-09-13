@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.focusflow.database

import androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE
import androidx.room.useWriterConnection
import com.focusflow.core.*
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class TaskData(
    val tasks: List<Task> = emptyList(), val projects: List<Project> = emptyList(),
    val tags: List<Tag> = emptyList(), val links: List<TaskTag> = emptyList(),
    val sessions: List<FocusSession> = emptyList(),
)

@OptIn(ExperimentalUuidApi::class)
class TaskRepository(
    private val database: FocusDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { Uuid.random().toString() },
) {
    private val dao = database.focusDao()
    val data = combine(dao.observeTasks(), dao.observeProjects(), dao.observeTags(), dao.observeTaskTags(), dao.observeCompletedSessions()) {
            tasks, projects, tags, links, sessions ->
        TaskData(tasks.map { it.task }, projects.map { it.project }, tags.map { it.tag }, links.map { it.link }, sessions.map { it.session })
    }

    private suspend fun <T> write(block: suspend () -> T): T = database.useWriterConnection { connection ->
        connection.withTransaction(IMMEDIATE) { block() }
    }

    private suspend fun identity(): LocalIdentity = dao.identity() ?: LocalIdentity(userId = newId(), deviceId = newId()).also { dao.insertIdentity(it) }

    private suspend inline fun <reified T> event(type: String, id: String, operation: SyncOperation, value: T) {
        dao.insertEvent(SyncEventEntity(SyncEvent(newId(), identity().deviceId, type, id, operation, Json.encodeToString(value), now())))
    }

    private suspend fun current(task: Task): Task {
        val latest = dao.task(task.id)?.task
        require(latest != null && latest.deletedAt == null && latest.revision == task.revision) { "任务已变更，请重新打开后操作" }
        return latest
    }

    suspend fun saveTask(draft: TaskDraft, original: Task? = null): String {
        draft.validate()
        return write {
            val previous = original?.let { current(it) }
            draft.projectId?.let { id -> require(dao.project(id)?.project?.let { it.deletedAt == null } == true) { "项目已删除，请重新选择" } }
            for (tagId in draft.tagIds) require(dao.tag(tagId)?.tag?.let { it.deletedAt == null } == true) { "标签已删除，请重新选择" }
            val timestamp = now()
            val task = (previous ?: Task(newId(), identity().userId, draft.title.trim(), createdAt = timestamp)).copy(
                title = draft.title.trim(), description = draft.description.trim(), plannedDate = draft.plannedDate,
                plannedStartTime = if (draft.plannedDate != null) draft.plannedStartTime else null,
                preferredFocusMode = draft.preferredFocusMode,
                priority = draft.priority, targetFocusMinutes = draft.targetFocusMinutes, projectId = draft.projectId,
                updatedAt = timestamp, revision = (previous?.revision ?: 0) + 1,
            )
            dao.upsertTask(TaskEntity(task))
            event("Task", task.id, if (previous == null) SyncOperation.CREATE else SyncOperation.UPDATE, task)
            val old = dao.tagsForTask(task.id).map { it.link.tagId }.toSet()
            for (tagId in old - draft.tagIds) {
                val link = TaskTag(task.id, tagId)
                dao.deleteTaskTag(task.id, tagId)
                event("TaskTag", "${task.id}:$tagId", SyncOperation.DELETE, link)
            }
            for (tagId in draft.tagIds - old) {
                val link = TaskTag(task.id, tagId)
                dao.insertTaskTag(TaskTagEntity(link))
                event("TaskTag", "${task.id}:$tagId", SyncOperation.CREATE, link)
            }
            task.id
        }
    }

    suspend fun setCompleted(task: Task, completed: Boolean) = write {
        requireNotFocusing(task.id)
        val latest = current(task)
        val timestamp = now()
        val changed = latest.copy(status = if (completed) TaskStatus.DONE else TaskStatus.TODO,
            completedAt = if (completed) timestamp else null, updatedAt = timestamp, revision = latest.revision + 1)
        dao.upsertTask(TaskEntity(changed))
        event("Task", changed.id, SyncOperation.UPDATE, changed)
    }

    suspend fun deleteTask(task: Task) = write {
        requireNotFocusing(task.id)
        val latest = current(task)
        val changed = latest.copy(deletedAt = now(), updatedAt = now(), revision = latest.revision + 1)
        dao.upsertTask(TaskEntity(changed))
        event("Task", changed.id, SyncOperation.DELETE, changed)
        for (entry in dao.tagsForTask(task.id)) {
            dao.deleteTaskTag(task.id, entry.link.tagId)
            event("TaskTag", "${task.id}:${entry.link.tagId}", SyncOperation.DELETE, entry.link)
        }
    }

    suspend fun saveProject(name: String, original: Project? = null) = write {
        require(name.isNotBlank() && name.trim().length <= 50) { "项目名称需要 1–50 个字符" }
        if (original != null) require(dao.project(original.id)?.project == original && original.deletedAt == null) { "项目已变更，请重新打开" }
        val project = (original ?: Project(newId(), identity().userId, name.trim(), 0xFF686DFA)).copy(name = name.trim(), revision = (original?.revision ?: 0) + 1)
        dao.upsertProject(ProjectEntity(project))
        event("Project", project.id, if (original == null) SyncOperation.CREATE else SyncOperation.UPDATE, project)
    }

    suspend fun saveTag(name: String, original: Tag? = null) = write {
        require(name.isNotBlank() && name.trim().length <= 50) { "标签名称需要 1–50 个字符" }
        if (original != null) require(dao.tag(original.id)?.tag == original && original.deletedAt == null) { "标签已变更，请重新打开" }
        val tag = (original ?: Tag(newId(), identity().userId, name.trim())).copy(name = name.trim(), revision = (original?.revision ?: 0) + 1)
        dao.upsertTag(TagEntity(tag))
        event("Tag", tag.id, if (original == null) SyncOperation.CREATE else SyncOperation.UPDATE, tag)
    }

    suspend fun deleteProject(project: Project) = write {
        require(dao.project(project.id)?.project == project && project.deletedAt == null) { "项目已变更，请重新打开" }
        val changed = project.copy(deletedAt = now(), revision = project.revision + 1)
        dao.upsertProject(ProjectEntity(changed))
        event("Project", project.id, SyncOperation.DELETE, changed)
        for (entry in dao.tasksInProject(project.id)) {
            val task = entry.task.copy(projectId = null, updatedAt = now(), revision = entry.task.revision + 1)
            dao.upsertTask(TaskEntity(task))
            event("Task", task.id, SyncOperation.UPDATE, task)
        }
    }

    suspend fun deleteTag(tag: Tag) = write {
        require(dao.tag(tag.id)?.tag == tag && tag.deletedAt == null) { "标签已变更，请重新打开" }
        val changed = tag.copy(deletedAt = now(), revision = tag.revision + 1)
        dao.upsertTag(TagEntity(changed))
        event("Tag", tag.id, SyncOperation.DELETE, changed)
        for (entry in dao.linksForTag(tag.id)) {
            dao.deleteTaskTag(entry.link.taskId, tag.id)
            event("TaskTag", "${entry.link.taskId}:${tag.id}", SyncOperation.DELETE, entry.link)
        }
    }

    private suspend fun requireNotFocusing(taskId: String) {
        val active = dao.activeFocus()?.toRun()
        require(active?.session?.taskId != taskId || active.anchor.state !in listOf(TimerState.FOCUSING, TimerState.PAUSED)) { "请先结束该任务的专注，再修改完成状态或删除" }
    }
}
