package com.focusflow.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
