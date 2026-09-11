package com.focusflow.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.core.*
import com.focusflow.database.FocusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FocusState(
    val run: FocusRun? = null, val loaded: Boolean = false, val busy: Boolean = false,
    val elapsed: Long = 0, val remaining: Long = 0, val error: String? = null, val remindersAvailable: Boolean = false,
)

class FocusViewModel(private val repository: FocusRepository) : ViewModel() {
    private val mutable = MutableStateFlow(FocusState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            repository.runs.catch { error ->
                if (error is CancellationException) throw error
                mutable.update { it.copy(error = "无法读取专注状态，请重新打开应用") }
            }.collect { run -> show(run) }
        }
    }
    private fun show(run: FocusRun?) {
        mutable.update { it.copy(run = run, loaded = true, elapsed = run?.let(repository.engine::elapsed) ?: 0,
            remaining = run?.let(repository.engine::remaining) ?: 0, remindersAvailable = repository.remindersAvailable) }
    }
    suspend fun refresh(restore: Boolean = false) {
        try { show(if (restore) repository.restore() else repository.reconcile()) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(error = "专注状态保存失败，请重试；尚未确认结算成功") } }
    }
    private fun change(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block(); refresh() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(error = if (error is IllegalArgumentException) error.message else "操作未保存，请重试") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun start(taskId: String, duration: Long, type: TimerType) = change { repository.start(taskId, duration, type) }
    fun pause(id: String) = change { repository.pause(id) }
    fun resume(id: String) = change { repository.resume(id) }
    fun cancel(id: String) = change { repository.cancel(id) }
    fun complete(id: String) = change { repository.complete(id) }
    fun startBreak(id: String) = change { repository.startBreak(id) }
    fun skipBreak(id: String) = change { repository.skipBreak(id) }
    fun dismiss(id: String) = change { repository.dismiss(id) }
    fun retry() = change { refresh(restore = true) }
}

@Composable
fun ObserveFocusWhileVisible(model: FocusViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, model) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.refresh(restore = true)
            while (true) { delay(1000); model.refresh() }
        }
    }
}
