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
        assertEquals(FocusMode.STRICT, effectiveMode(FocusMode.EXTREME, full.copy(screenPinningAvailable = false)))
        assertEquals(FocusMode.EXTREME, effectiveMode(FocusMode.EXTREME, full))
        // 极致缺固定且缺无障碍：一路降到底
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
        val strength = guardStrength(FocusMode.EXTREME, GuardCapabilities(usageAccessGranted = true))
        assertEquals(FocusMode.SOFT, strength.effective)
        assertTrue("无障碍服务（Strict 守护）" in strength.missingSteps)
        assertTrue("屏幕固定（极致模式）" in strength.missingSteps)
        assertTrue(guardStrength(FocusMode.EXTREME, full).missingSteps.isEmpty())
    }
}
