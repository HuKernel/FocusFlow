package com.focusflow.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.focusflow.designsystem.FeedbackPrefs
import com.focusflow.designsystem.FocusFeedback
import com.focusflow.designsystem.HapticController
import com.focusflow.designsystem.HapticEvent
import com.focusflow.designsystem.SoundController
import com.focusflow.designsystem.SoundEvent
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// 运行时生成的短促测试音（正弦 + 指数衰减），只作为占位反馈，正式音效见 docs/ASSETS_NEEDED.md
private const val SAMPLE_RATE = 44100
private val SOUND_EVENTS: Map<SoundEvent, List<Triple<Double, Double, Double>>> = mapOf(
    SoundEvent.FOCUS_START to listOf(Triple(523.0, 0.0, 0.10), Triple(659.0, 0.10, 0.16)),
    SoundEvent.FOCUS_PAUSE to listOf(Triple(440.0, 0.0, 0.16)),
    SoundEvent.FOCUS_RESUME to listOf(Triple(494.0, 0.0, 0.10), Triple(587.0, 0.09, 0.15)),
    SoundEvent.FOCUS_COMPLETE to listOf(Triple(523.0, 0.0, 0.14), Triple(659.0, 0.12, 0.14), Triple(784.0, 0.24, 0.30)),
    SoundEvent.TASK_COMPLETE to listOf(Triple(659.0, 0.0, 0.12), Triple(880.0, 0.10, 0.22)),
    SoundEvent.BREAK_START to listOf(Triple(392.0, 0.0, 0.18), Triple(330.0, 0.15, 0.25)),
    SoundEvent.ACHIEVEMENT to listOf(Triple(523.0, 0.0, 0.10), Triple(659.0, 0.08, 0.10), Triple(784.0, 0.16, 0.10), Triple(1047.0, 0.24, 0.32)),
    SoundEvent.ERROR to listOf(Triple(233.0, 0.0, 0.12), Triple(233.0, 0.18, 0.18)),
)

private fun wav(tones: List<Triple<Double, Double, Double>>): ByteArray {
    val total = tones.maxOf { it.second + it.third }
    val samples = (total * SAMPLE_RATE).toInt()
    val pcm = ShortArray(samples)
    for ((freq, start, duration) in tones) {
        val from = (start * SAMPLE_RATE).toInt()
        val count = (duration * SAMPLE_RATE).toInt()
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val value = sin(2.0 * PI * freq * t) * 0.5 * exp(-t * 6.0 / duration)
            val index = from + i
            if (index < samples) pcm[index] = (pcm[index] + value * Short.MAX_VALUE).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
    }
    val data = ByteArray(44 + pcm.size * 2)
    fun text(offset: Int, text: String) { for (i in text.indices) data[offset + i] = text[i].code.toByte() }
    fun int(offset: Int, value: Int) { for (i in 0 until 4) data[offset + i] = (value shr (8 * i)).toByte() }
    fun short(offset: Int, value: Int) { for (i in 0 until 2) data[offset + i] = (value shr (8 * i)).toByte() }
    text(0, "RIFF"); int(4, 36 + pcm.size * 2); text(8, "WAVE"); text(12, "fmt ")
    int(16, 16); short(20, 1); short(22, 1); int(24, SAMPLE_RATE); int(28, SAMPLE_RATE * 2)
    short(32, 2); short(34, 16); text(36, "data"); int(40, pcm.size * 2)
    for (i in pcm.indices) short(44 + i * 2, pcm[i].toInt())
    return data
}

class AndroidSoundController(context: Context) : SoundController {
    private val pool = SoundPool.Builder().setMaxStreams(2)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
        .build()
    private val ids: Map<SoundEvent, Int> = SOUND_EVENTS.entries.associate { (event, tones) ->
        val file = File(context.cacheDir, "tone_${event.name.lowercase()}.wav")
        if (!file.exists()) file.writeBytes(wav(tones))
        event to pool.load(file.absolutePath, 1)
    }
    override fun play(event: SoundEvent) {
        ids[event]?.let { pool.play(it, 0.8f, 0.8f, 1, 0, 1f) }
    }
}

class AndroidHapticController(context: Context) : HapticController {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31)
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    override fun perform(event: HapticEvent) {
        if (!vibrator.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(
            when (event) {
                HapticEvent.TAP, HapticEvent.SUCCESS -> VibrationEffect.EFFECT_CLICK
                HapticEvent.SELECTION -> VibrationEffect.EFFECT_TICK
                HapticEvent.START_FOCUS, HapticEvent.HANDOFF -> VibrationEffect.EFFECT_HEAVY_CLICK
                HapticEvent.WARNING -> VibrationEffect.EFFECT_DOUBLE_CLICK
            })
        else VibrationEffect.createOneShot(
            when (event) {
                HapticEvent.TAP, HapticEvent.SELECTION -> 15L
                HapticEvent.START_FOCUS, HapticEvent.HANDOFF, HapticEvent.WARNING -> 40L
                HapticEvent.SUCCESS -> 25L
            }, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(effect)
    }
}

fun androidFeedback(context: Context): FocusFeedback {
    val preferences = context.getSharedPreferences("focus_feedback", Context.MODE_PRIVATE)
    val systemReduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    return FocusFeedback(
        FeedbackPrefs(
            sound = preferences.getBoolean("sound", true),
            haptic = preferences.getBoolean("haptic", true),
            reducedMotion = preferences.getBoolean("reduced_motion", systemReduced),
            themeMode = runCatching { com.focusflow.designsystem.ThemeMode.valueOf(preferences.getString("theme_mode", "SYSTEM")!!) }.getOrDefault(com.focusflow.designsystem.ThemeMode.SYSTEM),
            noiseVolume = preferences.getFloat("noise_volume", 0.6f),
            focusBackground = runCatching { com.focusflow.designsystem.FocusBackground.valueOf(preferences.getString("focus_background", "OBSIDIAN")!!) }.getOrDefault(com.focusflow.designsystem.FocusBackground.OBSIDIAN),
            customQuotes = preferences.getStringSet("custom_quotes", emptySet())?.toList() ?: emptyList(),
            customBackgroundPath = preferences.getString("custom_background_path", null),
        ),
        AndroidSoundController(context),
        AndroidHapticController(context),
    ) { prefs ->
        preferences.edit().putBoolean("sound", prefs.sound)
            .putBoolean("haptic", prefs.haptic).putBoolean("reduced_motion", prefs.reducedMotion)
            .putString("theme_mode", prefs.themeMode.name).putFloat("noise_volume", prefs.noiseVolume)
            .putString("focus_background", prefs.focusBackground.name).putStringSet("custom_quotes", prefs.customQuotes.toSet())
            .putString("custom_background_path", prefs.customBackgroundPath).apply()
    }
}
