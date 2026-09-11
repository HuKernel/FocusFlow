package com.focusflow.core

import kotlin.test.*

class FocusStatsTest {
    private fun session(id: String, end: Long, minutes: Long, status: SessionStatus = SessionStatus.COMPLETED, interrupts: Int = 0) =
        FocusSession(id, "user", "task", "phone", "phone", plannedDuration = minutes * 60000,
            actualDuration = minutes * 60000, startedAt = end - minutes * 60000, endedAt = end,
            interruptCount = interrupts, status = status)
    private val noon = 43200000L // 1970-01-01 12:00 UTC，常见时区下都在同一天
    private val today = "1970-01-01" // 周四；本周一 1969-12-29

    @Test fun aggregatesTodayWeekAndMonthWithCompletedSessionsOnly() {
        val sessions = listOf(
            session("a", noon, 25, interrupts = 1), session("b", noon, 30, interrupts = 2),
            session("cancelled", noon, 99, status = SessionStatus.CANCELLED),
            session("yesterday", noon - 86400000, 40),      // 1969-12-31 周三，本周
            session("last-week", noon - 4 * 86400000, 10),  // 1969-12-28 周日，上周且属于 12 月
        )
        val todayStats = focusStats(sessions, emptyList(), StatsRange.TODAY, today)
        assertEquals(55 * 60000L, todayStats.focusMillis)
        assertEquals(2, todayStats.sessionCount)
        assertEquals(3, todayStats.interruptCount)
        assertEquals(1_650_000L, todayStats.avgSessionMillis) // 55m / 2 精确均分
        val week = focusStats(sessions, emptyList(), StatsRange.WEEK, today)
        assertEquals(95 * 60000L, week.focusMillis)
        assertEquals(3, week.sessionCount)
        assertEquals(1_900_000L, week.avgSessionMillis) // 95m / 3，Long 整除
        assertEquals(55 * 60000L, focusStats(sessions, emptyList(), StatsRange.MONTH, today).focusMillis)
    }

    @Test fun countsCompletedTasksWithinRangeAndSkipsDeletedOrPending() {
        fun task(id: String, completedAt: Long?, deleted: Long? = null, status: TaskStatus = TaskStatus.DONE) =
            Task(id, "user", id, status = status, completedAt = completedAt, createdAt = 0, deletedAt = deleted)
        val tasks = listOf(task("today", noon), task("yesterday", noon - 86400000),
            task("pending", null, status = TaskStatus.TODO), task("deleted", noon, deleted = 1))
        assertEquals(1, focusStats(emptyList(), tasks, StatsRange.TODAY, today).completedTasks)
        assertEquals(2, focusStats(emptyList(), tasks, StatsRange.WEEK, today).completedTasks)
    }

    @Test fun heatmapLaysOutMondayFirstWithCurrentWeekLast() {
        val sessions = listOf(session("a", noon, 55), session("yesterday", noon - 86400000, 40))
        val weeks = heatmapWeeks(sessions, today)
        assertEquals(12, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        val thisWeek = weeks.last()
        assertEquals(40 * 60000L, thisWeek[2]) // 1969-12-31 周三
        assertEquals(55 * 60000L, thisWeek[3]) // 1970-01-01 周四
        assertEquals(0L, thisWeek[6])          // 周日尚未到来
        assertTrue(weeks.dropLast(1).flatten().all { it == 0L })
    }
}
