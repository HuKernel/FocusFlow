package com.focusflow.core

import kotlinx.serialization.Serializable

/** WebSocket 实时事件；payload 为共享锚点，Observer 用本地时钟估算剩余。 */
@Serializable
enum class PresenceKind { FOCUS_STARTED, FOCUS_PAUSED, FOCUS_RESUMED, FOCUS_COMPLETED, FOCUS_CANCELLED, OWNER_CHANGED }

@Serializable
data class PresenceSnapshot(
    val sessionId: String,
    val taskId: String,
    val taskTitle: String,
    val ownerDeviceId: String,
    val kind: PresenceKind,
    val timerState: TimerState,
    /** 所有设备共享的锚点：owner 最近一次状态变更时刻（epoch ms）与其锚点快照 */
    val anchorEpochMillis: Long,
    val plannedDuration: Long,
    val elapsedAtAnchor: Long,
    val pausedDuration: Long,
    val type: TimerType = TimerType.COUNTDOWN,
)

@Serializable
data class PresenceMessage(
    val kind: PresenceKind,
    val deviceId: String,
    val snapshot: PresenceSnapshot? = null,
)

/** Observer 侧基于共享锚点估算当前进度；时钟偏差只影响显示，不影响结算。 */
data class RemoteFocus(
    val snapshot: PresenceSnapshot,
    val isOwner: Boolean,
    val elapsedMillis: Long,
    val remainingMillis: Long,
)

fun estimateRemoteFocus(snapshot: PresenceSnapshot, nowMillis: Long, myDeviceId: String): RemoteFocus {
    val wallElapsed = (nowMillis - snapshot.anchorEpochMillis).coerceAtLeast(0)
    val elapsed = when (snapshot.timerState) {
        TimerState.FOCUSING -> snapshot.elapsedAtAnchor + wallElapsed
        TimerState.PAUSED, TimerState.FOCUS_COMPLETED, TimerState.BREAKING, TimerState.SESSION_FINISHED -> snapshot.elapsedAtAnchor
        else -> snapshot.elapsedAtAnchor
    }
    return RemoteFocus(
        snapshot = snapshot,
        isOwner = snapshot.ownerDeviceId == myDeviceId,
        elapsedMillis = elapsed,
        remainingMillis = (snapshot.plannedDuration - elapsed).coerceAtLeast(0),
    )
}
