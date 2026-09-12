package com.focusflow.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object FocusColors {
    val Primary = Color(0xFF686DFA)
    val Background = Color(0xFFF6F7FD)
    val Ink = Color(0xFF1B2437)
    val Muted = Color(0xFF7C879F)
    val Success = Color(0xFF28B47A)
}
object FocusSpacing { val small = 8.dp; val medium = 16.dp; val large = 24.dp; val page = 32.dp }
object FocusShapes { val card = RoundedCornerShape(20.dp); val button = RoundedCornerShape(16.dp) }
val FocusTypography = Typography()

object FocusMotion {
    const val instant = 80
    const val fast = 140
    const val standard = 220
    const val emphasized = 320
    const val celebration = 600
    val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun duration(reducedMotion: Boolean, duration: Int = standard): Int = if (reducedMotion) 0 else duration
}
enum class SoundEvent { FOCUS_START, FOCUS_PAUSE, FOCUS_RESUME, FOCUS_COMPLETE, TASK_COMPLETE, BREAK_START, ACHIEVEMENT, ERROR }
enum class HapticEvent { TAP, SELECTION, START_FOCUS, SUCCESS, WARNING, HANDOFF }
fun interface SoundController { fun play(event: SoundEvent) }
fun interface HapticController { fun perform(event: HapticEvent) }

enum class WhiteNoiseKind { RAIN, WIND, SILENCE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 白噪音与音效音量独立；实现负责后台播放与音频焦点。 */
interface WhiteNoiseController {
    val playing: Boolean
    fun start(kind: WhiteNoiseKind)
    fun stop()
    fun setVolume(volume: Float)
}

@Composable
fun FocusTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = FocusColors.Primary, background = Color(0xFF141826),
            surface = Color(0xFF1C2133), onSurface = Color(0xFFE7EAF6),
            onBackground = Color(0xFFE7EAF6), secondary = FocusColors.Success,
            surfaceVariant = Color(0xFF272D45),
        ) else lightColorScheme(
            primary = FocusColors.Primary, background = FocusColors.Background,
            surface = Color.White, onSurface = FocusColors.Ink,
            onBackground = FocusColors.Ink, secondary = FocusColors.Success,
        ),
        typography = FocusTypography,
        shapes = Shapes(medium = FocusShapes.button, large = FocusShapes.card),
        content = content,
    )
}
