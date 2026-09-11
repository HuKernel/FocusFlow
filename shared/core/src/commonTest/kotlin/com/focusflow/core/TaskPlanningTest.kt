package com.focusflow.core

import kotlinx.datetime.TimeZone
import kotlin.test.*

class TaskPlanningTest {
    @Test fun streakCountsCompletedDaysAndAllowsTodayToBePending() {
        fun session(id: String, end: Long) = FocusSession(id, "user", "task", "phone", "phone",
            plannedDuration = 60000, actualDuration = 60000, startedAt = end - 60000, endedAt = end, status = SessionStatus.COMPLETED)
        val sessions = listOf(session("one", 0), session("duplicate-day", 0), session("previous", -86400000))
        assertEquals(2, focusStreak(sessions, localDateAt(0)))
        assertEquals(2, focusStreak(sessions, localDateAt(86400000)))
        assertEquals(0, focusStreak(sessions, localDateAt(172800000)))
        assertEquals(0, focusStreak(sessions.map { it.copy(status = SessionStatus.CANCELLED) }, localDateAt(0)))
    }

    @Test fun filtersDatesCompletionTagsAndSearch() {
        val today = Task("today", "user", "Read Kotlin", plannedDate = "2026-09-11", priority = Priority.HIGH, createdAt = 0)
        val tasks = listOf(today, today.copy(id = "later", plannedDate = "2026-09-12"),
            today.copy(id = "undated", plannedDate = null), today.copy(id = "done", status = TaskStatus.DONE),
            today.copy(id = "deleted", deletedAt = 1))
        assertEquals(listOf("today", "done"), filterTasks(tasks, TaskFilter.TODAY, "2026-09-11").map { it.id })
        assertEquals(listOf("later"), filterTasks(tasks, TaskFilter.UPCOMING, "2026-09-11").map { it.id })
        assertEquals(listOf("done"), filterTasks(tasks, TaskFilter.COMPLETED, "2026-09-11").map { it.id })
        assertEquals(listOf("today"), filterTasks(tasks, TaskFilter.ALL, "2026-09-11", search = "KOTLIN", tagId = "tag",
            taskTags = listOf(TaskTag("today", "tag"))).map { it.id })
        assertTrue(filterTasks(tasks, TaskFilter.ALL, "2026-09-11", priority = Priority.LOW).isEmpty())
    }

    @Test fun validatesCalendarDatesAndUsesLocalDay() {
        assertTrue(isValidDate("2024-02-29"))
        assertFalse(isValidDate("2025-02-29"))
        assertFalse(isValidDate("2026-2-1"))
        assertEquals("1970-01-01", localDateAt(0, TimeZone.of("Asia/Shanghai")))
        assertEquals("1969-12-31", localDateAt(0, TimeZone.of("America/Los_Angeles")))
        assertFailsWith<IllegalArgumentException> { TaskDraft("Read", plannedDate = "2026-02-30").validate() }
        assertFailsWith<IllegalArgumentException> { TaskDraft("Read", targetFocusMinutes = -1).validate() }
    }
}
