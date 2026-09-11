package com.focusflow.core

import android.os.SystemClock

class AndroidFocusClock : FocusClock {
    override fun epochMillis(): Long = System.currentTimeMillis()
    override fun monotonicMillis(): Long = SystemClock.elapsedRealtime()
}
