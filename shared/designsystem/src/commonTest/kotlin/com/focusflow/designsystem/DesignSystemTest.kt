package com.focusflow.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class DesignSystemTest {
    @Test fun windowSizeBoundaries() {
        assertEquals(WindowLayout.COMPACT, windowLayout(599))
        assertEquals(WindowLayout.MEDIUM, windowLayout(600))
        assertEquals(WindowLayout.MEDIUM, windowLayout(839))
        assertEquals(WindowLayout.EXPANDED, windowLayout(840))
    }
    @Test fun reducedMotionDisablesMovementDuration() {
        assertEquals(0, FocusMotion.duration(true))
        assertEquals(220, FocusMotion.duration(false))
    }
}
