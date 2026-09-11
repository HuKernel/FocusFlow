package com.focusflow.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.core.*
import com.focusflow.database.TaskData
import com.focusflow.database.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TaskEditor(val original: Task?, val tagIds: Set<String>)
data class TasksState(
    val data: TaskData = TaskData(), val loaded: Boolean = false, val loadError: Boolean = false,
    val busy: Boolean = false, val error: String? = null, val editor: TaskEditor? = null,
    val today: String = todayDate(), val organizationVersion: Int = 0,
)

class TasksViewModel(private val repository: TaskRepository) : ViewModel() {
    private val mutable = MutableStateFlow(TasksState())
    val state = mutable.asStateFlow()
    private var loading: Job? = null

    init {
        retryLoading()
        viewModelScope.launch {
            while (true) {
                mutable.update { it.copy(today = todayDate()) }
                delay(30_000)
            }
        }
    }

    fun retryLoading() {
        loading?.cancel()
        mutable.update { it.copy(loadError = false) }
        loading = viewModelScope.launch {
            repository.data.catch { error ->
                if (error is CancellationException) throw error
                mutable.update { it.copy(loadError = true) }
            }.collect { data -> mutable.update { it.copy(data = data, loaded = true) } }
        }
    }

    fun edit(task: Task? = null) {
        mutable.update { it.copy(error = null, editor = TaskEditor(task, it.data.links.filter { link -> link.taskId == task?.id }.map { link -> link.tagId }.toSet())) }
    }
    fun dismissEditor() { if (!state.value.busy) mutable.update { it.copy(editor = null, error = null) } }
    fun clearError() { mutable.update { it.copy(error = null) } }

    private fun change(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { it.copy(error = if (error is IllegalArgumentException) error.message else "保存失败，内容仍然保留，请重试") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun save(draft: TaskDraft) {
        val editor = state.value.editor ?: return
        change {
            repository.saveTask(draft, editor.original)
            mutable.update { it.copy(editor = null) }
        }
    }
    fun complete(task: Task, completed: Boolean) = change { repository.setCompleted(task, completed) }
    fun delete(task: Task) = change { repository.deleteTask(task) }
    fun saveProject(name: String, original: Project?) = change {
        repository.saveProject(name, original)
        mutable.update { it.copy(organizationVersion = it.organizationVersion + 1) }
    }
    fun saveTag(name: String, original: Tag?) = change {
        repository.saveTag(name, original)
        mutable.update { it.copy(organizationVersion = it.organizationVersion + 1) }
    }
    fun deleteProject(project: Project) = change { repository.deleteProject(project) }
    fun deleteTag(tag: Tag) = change { repository.deleteTag(tag) }
}
