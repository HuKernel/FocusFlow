package com.focusflow.core

import android.os.SystemClock
import android.content.Context
import android.provider.Settings
import java.util.UUID

class AndroidFocusClock(context: Context) : FocusClock {
    private val boot = try {
        "android:${Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)}"
    } catch (_: Exception) { "process:${UUID.randomUUID()}" }
    override fun epochMillis(): Long = System.currentTimeMillis()
    override fun monotonicMillis(): Long = SystemClock.elapsedRealtime()
    override fun bootId(): String = boot
}
