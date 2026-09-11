package com.focusflow.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class StatsRange(val label: String) { TODAY("今天"), WEEK("本周"), MONTH("本月") }

data class FocusStats(
    val focusMillis: Long, val sessionCount: Int, val interruptCount: Int,
    val avgSessionMillis: Long, val completedTasks: Int,
)

fun rangeStartDate(range: StatsRange, today: String): String {
    val date = LocalDate.parse(today)
    return when (range) {
        StatsRange.TODAY -> today
        StatsRange.WEEK -> date.minus((date.dayOfWeek.ordinal).toLong(), DateTimeUnit.DAY).toString()
        StatsRange.MONTH -> LocalDate(date.year, date.monthNumber, 1).toString()
    }
}

/** 只统计 COMPLETED Session；日期按设备本地时区，与 streak 一致。 */
fun focusStats(sessions: List<FocusSession>, tasks: List<Task>, range: StatsRange, today: String): FocusStats {
    val start = rangeStartDate(range, today)
    val inRange = { millis: Long -> localDateAt(millis) in start..today }
    val completed = sessions.filter { it.status == SessionStatus.COMPLETED && it.endedAt != null && inRange(it.endedAt!!) }
    val focusMillis = completed.sumOf { it.actualDuration }
    return FocusStats(
        focusMillis = focusMillis,
        sessionCount = completed.size,
        interruptCount = completed.sumOf { it.interruptCount },
        avgSessionMillis = if (completed.isEmpty()) 0 else focusMillis / completed.size,
        completedTasks = tasks.count { it.deletedAt == null && it.status == TaskStatus.DONE && it.completedAt != null && inRange(it.completedAt!!) },
    )
}

/** 最近 weeks 周（含本周）的每日专注毫秒，外层周从旧到新，内层周一到周日。 */
fun heatmapWeeks(sessions: List<FocusSession>, today: String, weeks: Int = 12): List<List<Long>> {
    val byDay = sessions.filter { it.status == SessionStatus.COMPLETED && it.endedAt != null }
        .groupBy { localDateAt(it.endedAt!!) }
        .mapValues { (_, list) -> list.sumOf { it.actualDuration } }
    val date = LocalDate.parse(today)
    val thisMonday = date.minus((date.dayOfWeek.ordinal).toLong(), DateTimeUnit.DAY)
    return (weeks - 1 downTo 0).map { w ->
        val monday = thisMonday.minus((w * 7).toLong(), DateTimeUnit.DAY)
        (0..6).map { d -> byDay[monday.plus(d.toLong(), DateTimeUnit.DAY).toString()] ?: 0L }
    }
}
