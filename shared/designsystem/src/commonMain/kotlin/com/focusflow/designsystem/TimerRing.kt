package com.focusflow.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun timerText(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1000
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
    else "${minutes.toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
fun TimerRing(millis: Long, progress: Float, label: String) {
    val prefs by LocalFocusFeedback.current.prefs.collectAsState()
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f),
        animationSpec = tween(FocusMotion.duration(prefs.reducedMotion), easing = FocusMotion.easing), label = "ring")
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxSize(), strokeWidth = 12.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timerText(millis), style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
            Text(label, color = FocusColors.Muted)
        }
    }
}
