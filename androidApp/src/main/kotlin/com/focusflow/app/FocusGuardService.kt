package com.focusflow.app

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.focusflow.core.isGuardAllowed

/**
 * Strict 守护：只读取窗口包名判断是否离开白名单，不读取任何页面内容。
 * 合规：非无障碍工具（isAccessibilityTool=false），仅在用户明确开启并授予后运行；
 * Emergency Exit 见通知 action 与系统设置（用户可随时停用本服务）。
 */
class FocusGuardService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val eventPackage = event.packageName?.toString() ?: return
        // 极致拉回（1.5.0 起替代系统屏幕固定）：检测到切出本应用即拉回并记录逃逸（驱动警告页）。
        // 不用 startLockTask → 无系统授权框、无"应用已固定"提示条。systemui 窗口（下拉/最近任务）不触发，避免抖动。
        if (GuardPrefs.isExtremeActive(this)) {
            if (eventPackage == packageName || eventPackage.startsWith("com.android.systemui")) return
            GuardPrefs.setLastEscape(this, System.currentTimeMillis())
            runCatching {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            return
        }
        if (!GuardPrefs.isGuardActive(this)) return
        val launchers = homePackages()
        if (isGuardAllowed(eventPackage, packageName, launchers, GuardPrefs.whitelist(this))) return
        val launchable = packageManager.getLaunchIntentForPackage(eventPackage) != null
        if (!launchable && !isInterestingPackage(eventPackage)) return // 系统内部窗口变化不打扰
        pullBack(eventPackage)
    }

    override fun onInterrupt() = Unit

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            GuardPrefs.setGuardActive(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun pullBack(packageName: String) {
        val exit = PendingIntent.getService(this, 1,
            Intent(this, FocusGuardService::class.java).setAction(ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "专注守护", NotificationManager.IMPORTANCE_HIGH))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("回到专注")
            .setContentText("检测到 $packageName 不在白名单，点击返回 FocusFlow。")
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 2,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(Notification.Action.Builder(null, "退出守护（Emergency Exit）", exit).build())
            .build()
        manager.notify(NOTICE_ID, notification)
    }

    private fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(intent, 0).mapNotNull { it.activityInfo?.packageName }.toSet()
    }

    private fun isInterestingPackage(packageName: String): Boolean = !packageName.startsWith("com.android.systemui")

    companion object {
        private const val CHANNEL = "focus_guard"
        private const val NOTICE_ID = 4001
        const val ACTION_EXIT = "com.focusflow.app.GUARD_EXIT"
    }
}
