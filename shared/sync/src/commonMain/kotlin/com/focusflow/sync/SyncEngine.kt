package com.focusflow.sync

import androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE
import androidx.room.useWriterConnection
import com.focusflow.core.*
import com.focusflow.database.*
import kotlinx.serialization.json.Json

class SyncEngine(private val database: FocusDatabase, private val transport: SyncTransport) {
    private val dao = database.focusDao()
    private val json = Json { ignoreUnknownKeys = true }

    data class Summary(val pushed: Int, val pulled: Int)

    suspend fun synchronize(): Summary {
        val pending = dao.pendingEvents()
        val pushed = if (pending.isEmpty()) 0 else transport.push(pending.map { it.event })
        for (event in pending) dao.saveEvent(SyncEventEntity(event.event.copy(state = SyncState.SYNCED)))
        val deviceId = dao.identity()?.deviceId ?: ""
        val pull = transport.pull(dao.syncState()?.cursor ?: 0, deviceId)
        apply(pull.changes)
        dao.saveSyncState(SyncStateEntity(cursor = pull.nextCursor))
        return Summary(pushed, pull.changes.size)
    }

    /** 远端变更直接落库，不产生新的 outbox 事件，避免回环；revision 旧于本地的变更跳过。 */
    private suspend fun apply(changes: List<ServerChange>) {
        database.useWriterConnection { connection ->
            connection.withTransaction(IMMEDIATE) {
                for (change in changes) {
                    when (change.entityType) {
                        "Task" -> {
                            val remote = json.decodeFromString(Task.serializer(), change.payload)
                            val local = dao.task(change.entityId)?.task
                            if (local == null || remote.revision >= local.revision) dao.upsertTask(TaskEntity(remote))
                        }
                        "Project" -> {
                            val remote = json.decodeFromString(Project.serializer(), change.payload)
                            val local = dao.project(change.entityId)?.project
                            if (local == null || remote.revision >= local.revision) dao.upsertProject(ProjectEntity(remote))
                        }
                        "Tag" -> {
                            val remote = json.decodeFromString(Tag.serializer(), change.payload)
                            val local = dao.tag(change.entityId)?.tag
                            if (local == null || remote.revision >= local.revision) dao.upsertTag(TagEntity(remote))
                        }
                        "TaskTag" -> {
                            val link = json.decodeFromString(TaskTag.serializer(), change.payload)
                            if (change.deleted) dao.deleteTaskTag(link.taskId, link.tagId)
                            else dao.insertTaskTag(TaskTagEntity(link))
                        }
                        // 终态 UUID 合并：IGNORE 插入，永不覆盖已有记录
                        "FocusSession" -> dao.insertSession(SessionEntity(json.decodeFromString(FocusSession.serializer(), change.payload)))
                    }
                }
            }
        }
    }
}
