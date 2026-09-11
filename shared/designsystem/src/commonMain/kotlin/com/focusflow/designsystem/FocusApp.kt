package com.focusflow.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    Card(modifier, shape = FocusShapes.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(FocusSpacing.medium), verticalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
