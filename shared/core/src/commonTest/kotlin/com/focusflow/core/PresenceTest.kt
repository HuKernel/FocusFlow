package com.focusflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresenceTest {
    private fun snapshot(state: TimerState, anchorAt: Long, elapsedAtAnchor: Long, planned: Long = 25 * 60_000L, owner: String = "phone") =
        PresenceSnapshot("s1", "t1", "Read", owner, PresenceKind.FOCUS_STARTED, state, anchorAt, planned, elapsedAtAnchor, 0)

    @Test fun observerEstimatesElapsedFromSharedAnchor() {
        val focus = estimateRemoteFocus(snapshot(TimerState.FOCUSING, anchorAt = 1_000_000, elapsedAtAnchor = 60_000), nowMillis = 1_060_000, myDeviceId = "tablet")
        assertFalse(focus.isOwner)
        assertEquals(120_000L, focus.elapsedMillis)
        assertEquals(25 * 60_000L - 120_000L, focus.remainingMillis)
    }

    @Test fun pausedFreezesElapsedAndOwnerDetectsItself() {
        val focus = estimateRemoteFocus(snapshot(TimerState.PAUSED, anchorAt = 1_000_000, elapsedAtAnchor = 300_000), nowMillis = 2_000_000, myDeviceId = "phone")
        assertTrue(focus.isOwner)
        assertEquals(300_000L, focus.elapsedMillis, "paused time must not accumulate")
    }

    @Test fun countdownNeverReportsNegativeRemaining() {
        val focus = estimateRemoteFocus(snapshot(TimerState.FOCUSING, anchorAt = 0, elapsedAtAnchor = 0, planned = 60_000), nowMillis = 5_000_000, myDeviceId = "tablet")
        assertEquals(0L, focus.remainingMillis)
        assertTrue(focus.elapsedMillis >= 60_000)
    }

    @Test fun ownerChangeReassignsOwnershipOnlyAfterAtomicTransfer() {
        val before = estimateRemoteFocus(snapshot(TimerState.FOCUSING, 0, 0, owner = "phone"), 1_000, "tablet")
        val after = estimateRemoteFocus(snapshot(TimerState.FOCUSING, 0, 0, owner = "tablet"), 1_000, "tablet")
        assertFalse(before.isOwner)
        assertTrue(after.isOwner)
    }
}
