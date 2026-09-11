package com.focusflow.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class WindowLayout { COMPACT, MEDIUM, EXPANDED }
fun windowLayout(widthDp: Int): WindowLayout = when {
    widthDp < 600 -> WindowLayout.COMPACT
    widthDp < 840 -> WindowLayout.MEDIUM
    else -> WindowLayout.EXPANDED
}

@Composable
fun StatisticCard(label: String, value: String, modifier: Modifier = Modifier) {
    val prefs by LocalFocusFeedback.current.prefs.collectAsState()
    Card(modifier, shape = FocusShapes.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(FocusSpacing.medium), verticalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
            AnimatedContent(value, transitionSpec = {
                val duration = FocusMotion.duration(prefs.reducedMotion, FocusMotion.fast)
                (slideInVertically(tween(duration, easing = FocusMotion.easing)) { it / 3 } + fadeIn(tween(duration))) togetherWith
                    (slideOutVertically(tween(duration, easing = FocusMotion.easing)) { -it / 3 } + fadeOut(tween(duration)))
            }, label = "stat_value") { text ->
                Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = FocusColors.Muted)
        }
    }
}

@Composable
fun EmptyState(title: String, description: String) {
    Card(Modifier.fillMaxWidth(), shape = FocusShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(FocusSpacing.page),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.medium), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Spa, null, Modifier.size(48.dp), tint = FocusColors.Primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = FocusColors.Muted)
        }
    }
}

/** 最近 N 周每日专注热力图；weeks 外层从旧到新，内层周一到周日，值为毫秒。 */
@Composable
fun Heatmap(weeks: List<List<Long>>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().aspectRatio(weeks.size.toFloat() / 7f)
        .semantics { contentDescription = "最近${weeks.size}周每日专注时长热力图" }) {
        val cell = minOf(size.width / weeks.size, size.height / 7f)
        val padding = cell * 0.15f
        val startX = (size.width - cell * weeks.size) / 2f
        weeks.forEachIndexed { w, days ->
            days.forEachIndexed { d, millis ->
                val alpha = when {
                    millis <= 0 -> 0f
                    millis < 15 * 60_000L -> 0.2f
                    millis < 30 * 60_000L -> 0.45f
                    millis < 60 * 60_000L -> 0.7f
                    else -> 1f
                }
                drawRoundRect(
                    color = if (alpha == 0f) primary.copy(alpha = 0.08f) else primary.copy(alpha = alpha),
                    topLeft = Offset(startX + w * cell + padding / 2, d * cell + padding / 2),
                    size = Size(cell - padding, cell - padding),
                    cornerRadius = CornerRadius(cell * 0.2f),
                )
            }
        }
    }
}
