package com.focusflow.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * 软性模式中断统计：专注结束后用 UsageStats 事件流聚合"从本应用切到其他应用"的次数，
 * 写入 FocusSession.interruptCount（统计页展示）。只查本机使用记录，不上传。
 */
object SoftInterruptCounter {
    fun countDepartures(context: Context, startedAt: Long, endedAt: Long): Int {
        if (endedAt <= startedAt) return 0
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0
        val events = usage.queryEvents(startedAt, endedAt)
        val own = context.packageName
        var current = own
        var interrupts = 0
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            val pkg = event.packageName ?: continue
            if (pkg != current) {
                if (current == own) interrupts++ // 从本应用切到任何其他应用（含启动器）计一次中断
                current = pkg
            }
        }
        return interrupts
    }
}
