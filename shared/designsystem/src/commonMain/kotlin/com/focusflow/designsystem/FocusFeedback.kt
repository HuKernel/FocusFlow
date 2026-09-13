package com.focusflow.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FeedbackPrefs(
    val sound: Boolean = true, val haptic: Boolean = true, val reducedMotion: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM, val noiseVolume: Float = 0.6f,
    val focusBackground: FocusBackground = FocusBackground.AURORA, val customQuotes: List<String> = emptyList(),
)

class FocusFeedback(
    initial: FeedbackPrefs = FeedbackPrefs(),
    private val sound: SoundController? = null,
    private val haptic: HapticController? = null,
    private val onPrefsChange: (FeedbackPrefs) -> Unit = {},
) {
    private val mutable = MutableStateFlow(initial)
    val prefs: StateFlow<FeedbackPrefs> = mutable.asStateFlow()
    fun setPrefs(prefs: FeedbackPrefs) { mutable.value = prefs; onPrefsChange(prefs) }
    fun play(event: SoundEvent) { if (mutable.value.sound) sound?.play(event) }
    fun perform(event: HapticEvent) { if (mutable.value.haptic) haptic?.perform(event) }
    companion object { val Off = FocusFeedback() }
}

val LocalFocusFeedback = staticCompositionLocalOf { FocusFeedback.Off }

val LocalWhiteNoise = staticCompositionLocalOf<WhiteNoiseController?> { null }
