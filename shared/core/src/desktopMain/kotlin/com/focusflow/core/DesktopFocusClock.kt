package com.focusflow.core

class DesktopFocusClock : FocusClock {
    // nanoTime is not guaranteed to share an origin across JVM processes.
    private val boot = "desktop:${java.lang.management.ManagementFactory.getRuntimeMXBean().startTime}"
    override fun epochMillis(): Long = System.currentTimeMillis()
    override fun monotonicMillis(): Long = System.nanoTime() / 1_000_000
    override fun bootId(): String = boot
}
