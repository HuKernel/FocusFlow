package com.focusflow.core

import kotlinx.serialization.Serializable

@Serializable enum class TaskStatus { TODO, IN_PROGRESS, DONE, ARCHIVED }
@Serializable enum class Priority { NONE, LOW, MEDIUM, HIGH }
@Serializable enum class SessionStatus { ACTIVE, PAUSED, COMPLETED, CANCELLED }
@Serializable enum class TimerType { COUNTDOWN, STOPWATCH }
@Serializable enum class FocusMode { NORMAL, SOFT, STRICT, EXTREME }
@Serializable enum class SyncOperation { CREATE, UPDATE, DELETE }
@Serializable enum class SyncState { PENDING, IN_FLIGHT, SYNCED, FAILED }

// Epoch timestamps and durations use milliseconds; dates use ISO-8601 local dates.
@Serializable data class Task(
    val id: String,
    val userId: String,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    val priority: Priority = Priority.NONE,
    val projectId: String? = null,
    val plannedDate: String? = null,
    val plannedStartTime: String? = null,
    val targetFocusMinutes: Int = 25,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val revision: Long = 0,
) {
    init {
        require(id.isNotBlank() && userId.isNotBlank())
        require(title.isNotBlank())
        require(targetFocusMinutes >= 0)
        require(revision >= 0)
    }
}

@Serializable data class FocusSession(
    val id: String,
    val userId: String,
    val taskId: String,
    val deviceId: String,
    val ownerDeviceId: String,
    val type: TimerType = TimerType.COUNTDOWN,
    val plannedDuration: Long,
    val actualDuration: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val pausedDuration: Long = 0,
    val interruptCount: Int = 0,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val strictMode: FocusMode = FocusMode.NORMAL,
    val createdAt: Long = startedAt,
    val updatedAt: Long = createdAt,
    val revision: Long = 0,
) {
    init {
        require(id.isNotBlank() && ownerDeviceId.isNotBlank())
        require(plannedDuration >= 0 && actualDuration >= 0 && pausedDuration >= 0)
        require(interruptCount >= 0 && revision >= 0)
        require(status != SessionStatus.COMPLETED || endedAt != null)
    }
}

/** Input is the UUID-unique local session table, not a stream of sync events. */
fun completedFocusMillis(taskId: String, sessions: Collection<FocusSession>): Long =
    sessions.filter { it.taskId == taskId && it.status == SessionStatus.COMPLETED }
        .sumOf { it.actualDuration }

@Serializable data class User(val id: String, val displayName: String, val createdAt: Long)
@Serializable data class Device(val id: String, val userId: String, val name: String, val platform: String, val lastActiveAt: Long)
@Serializable data class Project(val id: String, val userId: String, val name: String, val color: Long, val deletedAt: Long? = null, val revision: Long = 0)
@Serializable data class Tag(val id: String, val userId: String, val name: String, val deletedAt: Long? = null, val revision: Long = 0)
@Serializable data class TaskTag(val taskId: String, val tagId: String)
@Serializable data class FocusPreset(val id: String, val name: String, val focusDuration: Long, val breakDuration: Long)
@Serializable data class Reminder(val id: String, val taskId: String, val scheduledAt: Long, val enabled: Boolean)
@Serializable data class SyncEvent(
    val id: String, val deviceId: String, val entityType: String, val entityId: String,
    val operation: SyncOperation, val payload: String, val clientTimestamp: Long,
    val serverRevision: Long? = null, val retryCount: Int = 0, val state: SyncState = SyncState.PENDING,
)
@Serializable data class Achievement(val id: String, val title: String, val requiredSessions: Int)
@Serializable data class UserAchievement(val userId: String, val achievementId: String, val unlockedAt: Long)
@Serializable data class StrictModeConfig(val mode: FocusMode = FocusMode.NORMAL, val consentAt: Long? = null)
@Serializable data class AllowedApp(val packageName: String, val label: String)
