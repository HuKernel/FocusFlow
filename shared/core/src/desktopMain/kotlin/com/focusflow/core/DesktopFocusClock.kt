package com.focusflow.core

class DesktopFocusClock : FocusClock {
    override fun epochMillis(): Long = System.currentTimeMillis()
    override fun monotonicMillis(): Long = System.nanoTime() / 1_000_000
}
