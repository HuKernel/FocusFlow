package com.focusflow.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.focusflow.core.FocusAlarm
import kotlinx.coroutines.*

class AndroidFocusAlarm(private val context: Context) : FocusAlarm {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    init { notifications.createNotificationChannel(NotificationChannel("focus_end", "专注结束提醒", NotificationManager.IMPORTANCE_DEFAULT)) }
    override val available: Boolean get() = notifications.areNotificationsEnabled() &&
        notifications.getNotificationChannel("focus_end").importance != NotificationManager.IMPORTANCE_NONE

    private fun pending(sessionId: String = "") = PendingIntent.getBroadcast(context, 0,
        Intent(context, FocusAlarmReceiver::class.java).setAction("com.focusflow.FOCUS_END").putExtra("sessionId", sessionId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    override fun schedule(sessionId: String, delayMillis: Long) {
        val trigger = SystemClock.elapsedRealtime() + delayMillis.coerceAtLeast(1)
        val intent = pending(sessionId)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, intent); return }
            catch (_: SecurityException) { /* Permission can be revoked between checking and scheduling. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, intent)
    }
    override fun cancel() { alarms.cancel(pending()) }
    override fun completed(taskTitle: String, isBreak: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!available) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        notifications.notify(1, Notification.Builder(context, "focus_end")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(if (isBreak) "休息结束" else "专注已完成")
            .setContentText(taskTitle).setContentIntent(open).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build())
    }
}

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as FocusFlowApplication).focus
                if (intent.action == "com.focusflow.FOCUS_END") {
                    intent.getStringExtra("sessionId")?.let { repository.reconcile(it) }
                } else repository.restore()
            } catch (error: Exception) {
                android.util.Log.e("FocusFlow", "Focus recovery deferred until next app open", error)
            } finally { pending.finish() }
        }
    }
}
