package com.focusflow.designsystem

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
    fun duration(reducedMotion: Boolean, duration: Int = standard): Int = if (reducedMotion) 0 else duration
}
enum class SoundEvent { FOCUS_START, FOCUS_PAUSE, FOCUS_RESUME, FOCUS_COMPLETE, TASK_COMPLETE, BREAK_START, ACHIEVEMENT, ERROR }
enum class HapticEvent { TAP, SELECTION, START_FOCUS, SUCCESS, WARNING, HANDOFF }
interface SoundController { fun play(event: SoundEvent) }
interface HapticController { fun perform(event: HapticEvent) }

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FocusColors.Primary, background = FocusColors.Background,
            surface = Color.White, onSurface = FocusColors.Ink,
            onBackground = FocusColors.Ink, secondary = FocusColors.Success,
        ),
        typography = FocusTypography,
        shapes = Shapes(medium = FocusShapes.button, large = FocusShapes.card),
        content = content,
    )
}
