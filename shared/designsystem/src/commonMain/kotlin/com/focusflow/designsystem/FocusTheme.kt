package com.focusflow.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

object FocusColors {
    val Primary = Color(0xFF686DFA)
    val PrimaryDeep = Color(0xFF4F54D9)
    val Background = Color(0xFFF4F5FC)
    val Ink = Color(0xFF1B2437)
    val Muted = Color(0xFF7C879F)
    val Success = Color(0xFF28B47A)
    val Warning = Color(0xFFE8912D)
    val CardBorder = Color(0xFFE5E8F5)
    val PriorityHigh = Color(0xFFE85D5D)
    val PriorityMedium = Color(0xFFE8912D)
    val PriorityLow = Color(0xFF5B8DEF)
}
object FocusSpacing { val small = 8.dp; val medium = 16.dp; val large = 24.dp; val page = 32.dp }
object FocusShapes { val card = RoundedCornerShape(24.dp); val button = RoundedCornerShape(28.dp) }

// 高级感三件套：大字轻字重标题、加粗强调、小标签带字距；计时数字恒用 tabular
val FocusTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 60.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontWeight = FontWeight.Light, fontSize = 46.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
)

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

/**
 * 专注页背景：4 个深色系 + 1 个浅色系预设，全部为本地渐变（无素材、无权限）；
 * 每个背景自带前景色配比（主文字/次要文字/是否深底），界面元素颜色一律从背景主题派生。
 * CUSTOM 为用户从相册选择的自定义图片（平台层注入 ImageBitmap，加深色遮罩保证可读性）。
 */
enum class FocusBackground(val label: String) {
    NONE("无背景"), OBSIDIAN("曜石"), MIDNIGHT("午夜蓝"), ROSE("玫瑰暮光"), TEAL("青黛"), WARM("暖米白"), CUSTOM("自定义")
}

data class FocusBackgroundTheme(
    val brush: Brush?,
    val content: Color,      // 主文字/强调
    val secondary: Color,    // 次要文字
    val dark: Boolean,       // 深底（影响按钮反白、环轨道等）
)

fun focusBackgroundTheme(background: FocusBackground): FocusBackgroundTheme = when (background) {
    // 无背景 = 干净的纸白底，深色文字；作为默认
    FocusBackground.NONE -> FocusBackgroundTheme(
        Brush.verticalGradient(listOf(Color(0xFFFDFDFB), Color(0xFFF3F4F6))),
        Color(0xFF1F2430), Color(0xFF6B7280), dark = false)
    FocusBackground.OBSIDIAN -> FocusBackgroundTheme(
        Brush.verticalGradient(listOf(Color(0xFF12121C), Color(0xFF262540), Color(0xFF101018))),
        Color(0xFFF4F4FF), Color(0xFFB8B8D9), dark = true)
    FocusBackground.MIDNIGHT -> FocusBackgroundTheme(
        Brush.verticalGradient(listOf(Color(0xFF0A1E3C), Color(0xFF14487E), Color(0xFF081830))),
        Color(0xFFEAF4FF), Color(0xFFA9C4E4), dark = true)
    FocusBackground.ROSE -> FocusBackgroundTheme(
        Brush.verticalGradient(listOf(Color(0xFF33192E), Color(0xFF7A3B5E), Color(0xFF241220))),
        Color(0xFFFFEAF2), Color(0xFFD9AFC2), dark = true)
    FocusBackground.TEAL -> FocusBackgroundTheme(
        Brush.verticalGradient(listOf(Color(0xFF0C2B2A), Color(0xFF1F6B62), Color(0xFF0A211F))),
        Color(0xFFE8FFFA), Color(0xFFA7CFC8), dark = true)
    FocusBackground.WARM -> FocusBackgroundTheme(
        Brush.verticalGradient(listOf(Color(0xFFFBF8F2), Color(0xFFEDE6D8))),
        Color(0xFF2A2A33), Color(0xFF6B6B78), dark = false)
    FocusBackground.CUSTOM -> FocusBackgroundTheme(
        null, Color(0xFFFFFFFF), Color(0xFFDEDEEA), dark = true) // 位图由平台层叠加，配深色遮罩
}

/** 内置专注语录；用户可在设置中追加自定义（
 分隔存 FeedbackPrefs）。 */
object FocusQuotes {
    val builtIn = listOf(
        "一次只做一件事，做到底。",
        "开始，就是最难的部份。",
        "专注是安静的坚持。",
        "别急，时间会给出答案。",
        "此刻，只属于这一件事。",
        "深度来自不被打扰的时长。",
        "种一棵树最好的时间是二十五分钟前。",
    )
    fun pick(custom: List<String>, seed: Long): String {
        val all = builtIn + custom
        if (all.isEmpty()) return "专注"
        return all[(seed % all.size).toInt().coerceAtLeast(0)]
    }
}
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
            primary = FocusColors.Primary, onPrimary = Color.White,
            primaryContainer = FocusColors.PrimaryDeep, onPrimaryContainer = Color.White,
            background = Color(0xFF12162A), onBackground = Color(0xFFE7EAF6),
            surface = Color(0xFF1B2036), onSurface = Color(0xFFE7EAF6),
            surfaceVariant = Color(0xFF272D4A), onSurfaceVariant = Color(0xFFB9C0D9),
            secondary = FocusColors.Success, outline = Color(0xFF394064),
            error = Color(0xFFFF8A80),
        ) else lightColorScheme(
            primary = FocusColors.Primary, onPrimary = Color.White,
            primaryContainer = Color(0xFFE4E5FF), onPrimaryContainer = FocusColors.PrimaryDeep,
            background = FocusColors.Background, onBackground = FocusColors.Ink,
            surface = Color.White, onSurface = FocusColors.Ink,
            surfaceVariant = Color(0xFFEEF0FA), onSurfaceVariant = FocusColors.Muted,
            secondary = FocusColors.Success, outline = FocusColors.CardBorder,
        ),
        typography = FocusTypography,
        shapes = Shapes(medium = FocusShapes.button, large = FocusShapes.card),
        content = content,
    )
}
