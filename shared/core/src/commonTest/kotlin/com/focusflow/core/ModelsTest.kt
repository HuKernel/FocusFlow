package com.focusflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json

class ModelsTest {
    private fun session(id: String, minutes: Long, status: SessionStatus = SessionStatus.COMPLETED) =
        FocusSession(id, "local", "task", id, id, plannedDuration = minutes * 60_000,
            actualDuration = minutes * 60_000, startedAt = 0, endedAt = minutes * 60_000, status = status)

    @Test fun completedSessionsAggregateAcrossDevices() {
        assertEquals(55 * 60_000L, completedFocusMillis("task", listOf(
            session("phone", 25), session("tablet", 30), session("cancelled", 10, SessionStatus.CANCELLED),
            session("active", 3, SessionStatus.ACTIVE), session("other", 5).copy(taskId = "other"),
        )))
    }

    @Test fun taskValidationRejectsInvalidInput() {
        assertFailsWith<IllegalArgumentException> { Task("id", "local", " ", createdAt = 0) }
        assertFailsWith<IllegalArgumentException> { Task("id", "local", "Read", targetFocusMinutes = -1, createdAt = 0) }
    }

    @Test fun anchorRoundTripsForRecovery() {
        val anchor = TimerAnchor("session", TimerState.PAUSED, 1000, 900, 1500000, 500, 100)
        assertEquals(anchor, Json.decodeFromString<TimerAnchor>(Json.encodeToString(anchor)))
    }
}
