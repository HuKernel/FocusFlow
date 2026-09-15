package com.focusflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class FocusGuardTest {
    private val full = GuardCapabilities(accessibilityGranted = true, usageAccessGranted = true, screenPinningAvailable = true)

    @Test fun degradesStepByStepWhenPermissionsMissing() {
        assertEquals(FocusMode.NORMAL, effectiveMode(FocusMode.NORMAL, GuardCapabilities()))
        assertEquals(FocusMode.NORMAL, effectiveMode(FocusMode.SOFT, GuardCapabilities()))
        assertEquals(FocusMode.SOFT, effectiveMode(FocusMode.SOFT, GuardCapabilities(usageAccessGranted = true)))
        assertEquals(FocusMode.SOFT, effectiveMode(FocusMode.STRICT, GuardCapabilities(usageAccessGranted = true)))
        assertEquals(FocusMode.NORMAL, effectiveMode(FocusMode.STRICT, GuardCapabilities()))
        assertEquals(FocusMode.STRICT, effectiveMode(FocusMode.STRICT, full))
        // 1.5.0 起极致依赖无障碍拉回（不再使用系统屏幕固定）
        assertEquals(FocusMode.EXTREME, effectiveMode(FocusMode.EXTREME, full))
        assertEquals(FocusMode.SOFT, effectiveMode(FocusMode.EXTREME, full.copy(accessibilityGranted = false)))
        assertEquals(FocusMode.EXTREME, effectiveMode(FocusMode.EXTREME, GuardCapabilities(accessibilityGranted = true)))
        assertEquals(FocusMode.SOFT, effectiveMode(FocusMode.EXTREME, GuardCapabilities(usageAccessGranted = true)))
        assertEquals(FocusMode.NORMAL, effectiveMode(FocusMode.EXTREME, GuardCapabilities()))
    }

    @Test fun whitelistAllowsOwnLauncherAndUserAppsOnly() {
        val whitelist = listOf(AllowedApp("com.example.notes", "Notes"))
        assertTrue(isGuardAllowed("com.focusflow.app", "com.focusflow.app", emptySet(), emptyList()))
        assertTrue(isGuardAllowed("com.android.launcher", "com.focusflow.app", setOf("com.android.launcher"), whitelist))
        assertTrue(isGuardAllowed("com.example.notes", "com.focusflow.app", emptySet(), whitelist))
        assertFalse(isGuardAllowed("com.example.game", "com.focusflow.app", emptySet(), whitelist))
        assertTrue(isGuardAllowed(null, "com.focusflow.app", emptySet(), emptyList()))
    }

    @Test fun strengthReportsMissingSteps() {
        val strength = guardStrength(FocusMode.STRICT, GuardCapabilities(usageAccessGranted = true))
        assertEquals(FocusMode.SOFT, strength.effective)
        assertTrue("无障碍服务（Strict 守护）" in strength.missingSteps)
        assertTrue(guardStrength(FocusMode.EXTREME, full).missingSteps.isEmpty())
    }
}
