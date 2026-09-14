@file:OptIn(kotlin.time.ExperimentalTime::class)

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
import com.focusflow.network.FocusPresence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class FocusState(
    val run: FocusRun? = null, val loaded: Boolean = false, val busy: Boolean = false,
    val elapsed: Long = 0, val remaining: Long = 0, val error: String? = null, val remindersAvailable: Boolean = false,
    val remote: RemoteFocus? = null,
    /** 休息结束后自动开始的下一轮参数；skipBreak/取消会清除。 */
    val autoNext: AutoNextRound? = null,
)

data class AutoNextRound(val taskId: String, val plannedDuration: Long, val type: TimerType, val mode: FocusMode)

class FocusViewModel(
    private val repository: FocusRepository,
    private val presence: FocusPresence? = null,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    private val mutable = MutableStateFlow(FocusState())
    val state = mutable.asStateFlow()
    private var reported: Pair<String, TimerState>? = null
    private var myDeviceId: String = ""

    init {
        viewModelScope.launch { myDeviceId = repository.currentDeviceId() ?: "" }
        viewModelScope.launch {
            repository.runs.catch { error ->
                if (error is CancellationException) throw error
                mutable.update { it.copy(error = "无法读取专注状态，请重新打开应用") }
            }.collect { run -> show(run); report(run) }
        }
        presence?.let { link ->
            link.start(viewModelScope)
            viewModelScope.launch {
                link.incoming.collect { message -> onPresence(message) }
            }
        }
    }

    private suspend fun onPresence(message: PresenceMessage) {
        val snapshot = message.snapshot ?: return
        val myDevice = repository.currentDeviceId() ?: return
        if (snapshot.ownerDeviceId == myDevice) {
            if (message.kind == PresenceKind.OWNER_CHANGED && state.value.run?.session?.id != snapshot.sessionId) {
                // Continue on this device：服务端已原子转移 owner，本机按共享锚点恢复同一会话
                try {
                    val focus = estimateRemoteFocus(snapshot, now(), myDevice)
                    repository.adopt(snapshot.taskId, snapshot.taskTitle, snapshot.sessionId,
                        snapshot.plannedDuration, snapshot.type, focus.elapsedMillis)
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { mutable.update { it.copy(error = "接管失败，请稍后重试") } }
            }
            mutable.update { it.copy(remote = null) }
        } else {
            if (state.value.run?.session?.id == snapshot.sessionId) repository.releaseLocal(snapshot.sessionId)
            mutable.update { it.copy(remote = estimateRemoteFocus(snapshot, now(), myDevice)) }
        }
    }

    /** owner 状态变化时上报共享锚点；观察者据此估算剩余时间。 */
    private suspend fun report(run: FocusRun?) {
        val link = presence ?: return
        if (run == null) { reported = null; return }
        val previous = reported
        val current = run.session.id to run.anchor.state
        if (current == previous) return
        reported = current
        val kind = when {
            previous == null && run.anchor.state == TimerState.FOCUSING -> PresenceKind.FOCUS_STARTED
            run.anchor.state == TimerState.PAUSED -> PresenceKind.FOCUS_PAUSED
            previous?.second == TimerState.PAUSED && run.anchor.state == TimerState.FOCUSING -> PresenceKind.FOCUS_RESUMED
            run.session.status == SessionStatus.COMPLETED -> PresenceKind.FOCUS_COMPLETED
            run.anchor.state == TimerState.CANCELLED -> PresenceKind.FOCUS_CANCELLED
            else -> return // 休息与结束画面属于本地体验，不进入 presence
        }
        val device = repository.currentDeviceId() ?: return
        link.send(PresenceMessage(kind, device, PresenceSnapshot(
            sessionId = run.session.id, taskId = run.session.taskId, taskTitle = run.taskTitle, ownerDeviceId = device,
            kind = kind, timerState = if (run.anchor.state == TimerState.PAUSED) TimerState.PAUSED else TimerState.FOCUSING,
            anchorEpochMillis = run.anchor.epochMillis, plannedDuration = run.anchor.plannedDuration,
            elapsedAtAnchor = repository.engine.elapsed(run), pausedDuration = run.session.pausedDuration, type = run.session.type,
        )))
    }

    fun takeover() {
        val snapshot = state.value.remote?.snapshot ?: return
        val link = presence ?: return
        change {
            val accepted = link.takeover(snapshot.sessionId)
            checkNotNull(accepted) { "接管失败：会话可能已结束" }
        }
    }

    private suspend fun show(run: FocusRun?) {
        // 休息倒计时自然结束：自动开始下一轮专注（用户点「结束休息」则不触发，skipBreak 已清除标记）
        val state = mutable.value
        if (run?.anchor?.state == TimerState.SESSION_FINISHED && state.autoNext != null) {
            val next = state.autoNext
            mutable.update { it.copy(autoNext = null) }
            try {
                repository.start(next.taskId, next.plannedDuration, next.type, next.mode)
                return
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* 任务已完成或被删除：停在结束页 */ }
        }
        mutable.update {
            it.copy(run = run, loaded = true, elapsed = run?.let(repository.engine::elapsed) ?: 0,
                remaining = run?.let(repository.engine::remaining) ?: 0,
                remindersAvailable = repository.remindersAvailable,
                remote = it.remote?.let { remote -> estimateRemoteFocus(remote.snapshot, now(), myDeviceId) })
        }
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
    fun start(taskId: String, duration: Long, type: TimerType, mode: FocusMode = FocusMode.NORMAL) = change { repository.start(taskId, duration, type, mode) }
    fun pause(id: String) = change { repository.pause(id) }
    fun resume(id: String) = change { repository.resume(id) }
    fun cancel(id: String) = change { repository.cancel(id) }
    fun complete(id: String) = change { repository.complete(id) }
    fun startBreak(id: String) = change {
        val current = state.value.run ?: throw IllegalArgumentException("本轮已结束")
        if (current.anchor.state == TimerState.FOCUS_COMPLETED && current.session.status == SessionStatus.COMPLETED) {
            mutable.update { it.copy(autoNext = AutoNextRound(current.session.taskId, current.anchor.plannedDuration, current.session.type, current.session.strictMode)) }
        }
        repository.startBreak(id)
    }
    fun skipBreak(id: String) = change {
        mutable.update { it.copy(autoNext = null) } // 主动结束休息 = 不自动继续
        repository.skipBreak(id)
    }
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
