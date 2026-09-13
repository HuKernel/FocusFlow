@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.focusflow.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

data class TaskDraft(
    val title: String,
    val description: String = "",
    val plannedDate: String? = null,
    val plannedStartTime: String? = null,
    val priority: Priority = Priority.NONE,
    val preferredFocusMode: FocusMode? = null,
    val targetFocusMinutes: Int = 25,
    val projectId: String? = null,
    val tagIds: Set<String> = emptySet(),
) {
    fun validate() {
        require(title.isNotBlank() && title.trim().length <= 200) { "标题需要 1–200 个字符" }
        require(description.length <= 10000) { "备注不能超过 10000 个字符" }
        require(targetFocusMinutes in 0..10080) { "目标时长需要在 0–10080 分钟之间" }
        require(plannedDate == null || isValidDate(plannedDate)) { "日期格式应为 YYYY-MM-DD，且必须是真实日期" }
        require(plannedStartTime == null || plannedStartTime.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { "开始时间格式应为 HH:mm（00:00–23:59）" }
    }
}

fun isValidDate(value: String): Boolean =
    runCatching { LocalDate.parse(value).toString() == value }.getOrDefault(false)

fun localDateAt(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date.toString()

fun todayDate(): String = localDateAt(Clock.System.now().toEpochMilliseconds())

fun focusStreak(sessions: List<FocusSession>, today: String): Int {
    val days = sessions.filter { it.status == SessionStatus.COMPLETED && it.actualDuration > 0 }
        .mapNotNull { it.endedAt?.let { end -> localDateAt(end) } }.toSet()
    var date = LocalDate.parse(today)
    if (date.toString() !in days) date = date.minus(1, DateTimeUnit.DAY)
    var count = 0
    while (date.toString() in days) { count++; date = date.minus(1, DateTimeUnit.DAY) }
    return count
}

enum class TaskFilter(val label: String) { ALL("全部"), TODAY("今天"), UPCOMING("即将到来"), COMPLETED("已完成") }

fun filterTasks(
    tasks: List<Task>, filter: TaskFilter, today: String, search: String = "",
    projectId: String? = null, tagId: String? = null, priority: Priority? = null,
    taskTags: List<TaskTag> = emptyList(),
): List<Task> {
    val tagged = tagId?.let { tag -> taskTags.filter { it.tagId == tag }.map { it.taskId }.toSet() }
    return tasks.filter { task ->
        task.deletedAt == null && task.status != TaskStatus.ARCHIVED &&
            when (filter) {
                TaskFilter.ALL -> true
                TaskFilter.TODAY -> task.plannedDate == today
                TaskFilter.UPCOMING -> task.plannedDate != null && task.plannedDate > today && task.status != TaskStatus.DONE
                TaskFilter.COMPLETED -> task.status == TaskStatus.DONE
            } &&
            (search.isBlank() || task.title.contains(search.trim(), ignoreCase = true) || task.description.contains(search.trim(), ignoreCase = true)) &&
            (projectId == null || task.projectId == projectId) &&
            (tagged == null || task.id in tagged) && (priority == null || task.priority == priority)
    }.sortedWith(compareBy<Task> { it.status == TaskStatus.DONE }.thenByDescending { it.priority.ordinal }.thenByDescending { it.createdAt })
}
