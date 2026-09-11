package com.focusflow.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    @Test fun feedbackRespectPrefsSwitches() {
        val sounds = mutableListOf<SoundEvent>()
        val haptics = mutableListOf<HapticEvent>()
        var saved: FeedbackPrefs? = null
        val feedback = FocusFeedback(FeedbackPrefs(), sound = { sounds.add(it) }, haptic = { haptics.add(it) }, onPrefsChange = { saved = it })
        feedback.play(SoundEvent.FOCUS_START)
        feedback.perform(HapticEvent.START_FOCUS)
        assertEquals(listOf(SoundEvent.FOCUS_START), sounds)
        assertEquals(listOf(HapticEvent.START_FOCUS), haptics)
        feedback.setPrefs(FeedbackPrefs(sound = false, haptic = false))
        assertEquals(FeedbackPrefs(sound = false, haptic = false), saved)
        feedback.play(SoundEvent.TASK_COMPLETE)
        feedback.perform(HapticEvent.SUCCESS)
        assertTrue(sounds.size == 1 && haptics.size == 1, "disabled prefs must suppress feedback")
    }
    @Test fun offVariantIsSilent() {
        val feedback = FocusFeedback.Off
        feedback.play(SoundEvent.ERROR)
        feedback.perform(HapticEvent.WARNING)
        assertEquals(FeedbackPrefs(), feedback.prefs.value)
    }
}
