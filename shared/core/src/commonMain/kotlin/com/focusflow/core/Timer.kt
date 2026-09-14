package com.focusflow.core

import kotlinx.serialization.Serializable

@Serializable
enum class TimerState { IDLE, PREPARING, FOCUSING, PAUSED, FOCUS_COMPLETED, BREAKING, SESSION_FINISHED, CANCELLED }

interface FocusClock {
    fun epochMillis(): Long
    fun monotonicMillis(): Long
    fun bootId(): String
}

/** Persist on state changes, never on every UI tick. */
@Serializable
data class TimerAnchor(
    val sessionId: String,
    val state: TimerState,
    val epochMillis: Long,
    val monotonicMillis: Long,
    val plannedDuration: Long,
    val elapsedBeforeAnchor: Long = 0,
    val pausedDuration: Long = 0,
) {
    init { require(plannedDuration >= 0 && elapsedBeforeAnchor >= 0 && pausedDuration >= 0) }
}

interface TimerController {
    suspend fun start(taskId: String, plannedDuration: Long, type: TimerType = TimerType.COUNTDOWN, mode: FocusMode = FocusMode.NORMAL, breakDuration: Long = 5 * 60_000)
    suspend fun pause(sessionId: String)
    suspend fun resume(sessionId: String)
    suspend fun cancel(sessionId: String)
    suspend fun complete(sessionId: String)
    suspend fun restore(): FocusRun?
}

@Serializable
data class FocusRun(
    val session: FocusSession,
    val anchor: TimerAnchor,
    val bootId: String,
    val taskTitle: String,
    val breakDuration: Long = 5 * 60_000,
    val recoveredWithWallClock: Boolean = false,
)

interface FocusAlarm {
    val available: Boolean
    fun schedule(sessionId: String, delayMillis: Long)
    fun cancel()
    fun completed(taskTitle: String, isBreak: Boolean)
}

object NoFocusAlarm : FocusAlarm {
    override val available = false
    override fun schedule(sessionId: String, delayMillis: Long) = Unit
    override fun cancel() = Unit
    override fun completed(taskTitle: String, isBreak: Boolean) = Unit
}
