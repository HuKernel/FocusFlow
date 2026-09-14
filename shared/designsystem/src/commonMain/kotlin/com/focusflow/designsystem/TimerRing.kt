package com.focusflow.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun timerText(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1000
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
    else "${minutes.toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** 渐变描边计时环；lightContent 用于浅色背景页面切换文字/轨道色。 */
@Composable
fun TimerRing(millis: Long, progress: Float, label: String, lightContent: Boolean = false) {
    val prefs by LocalFocusFeedback.current.prefs.collectAsState()
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f),
        animationSpec = tween(FocusMotion.duration(prefs.reducedMotion), easing = FocusMotion.easing), label = "ring")
    val primary = MaterialTheme.colorScheme.primary
    val brush = Brush.sweepGradient(listOf(FocusColors.PrimaryDeep, primary, Color(0xFF9EA1FF), FocusColors.PrimaryDeep))
    val contentColor = if (lightContent) MaterialTheme.colorScheme.onSurface else Color.White
    val track = if (lightContent) MaterialTheme.colorScheme.surfaceVariant else Color.White.copy(alpha = 0.16f)
    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            val inset = 9.dp.toPx() / 2 + 1.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = stroke)
            if (animated > 0f) rotate(degrees = -90f) {
                drawArc(brush = brush, startAngle = 0f, sweepAngle = 360f * animated, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = stroke)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timerText(millis), color = contentColor,
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
            Text(label, color = contentColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
        }
    }
}
