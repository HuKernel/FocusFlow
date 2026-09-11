package com.focusflow.core

import kotlinx.serialization.Serializable

@Serializable
enum class TimerState { IDLE, PREPARING, FOCUSING, PAUSED, FOCUS_COMPLETED, BREAKING, SESSION_FINISHED, CANCELLED }

interface FocusClock {
    fun epochMillis(): Long
    fun monotonicMillis(): Long
}

/** Persist on state changes, never on every UI tick. Engine implementation belongs to M2. */
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
    suspend fun start(taskId: String, plannedDuration: Long)
    suspend fun pause()
    suspend fun resume()
    suspend fun cancel()
    suspend fun complete()
    suspend fun restore(): TimerAnchor?
}
