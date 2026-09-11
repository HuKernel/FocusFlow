package com.focusflow.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class WindowLayout { COMPACT, MEDIUM, EXPANDED }
fun windowLayout(widthDp: Int): WindowLayout = when {
    widthDp < 600 -> WindowLayout.COMPACT
    widthDp < 840 -> WindowLayout.MEDIUM
    else -> WindowLayout.EXPANDED
}

private enum class Destination(val label: String, val icon: ImageVector) {
    TODAY("今天", Icons.Outlined.Today), TASKS("任务", Icons.Outlined.CheckCircle),
    FOCUS("专注", Icons.Outlined.Timer), STATS("统计", Icons.Outlined.BarChart),
    SETTINGS("我的", Icons.Outlined.Person),
}

@Composable
fun FocusApp(taskCount: Int = 0) = FocusTheme {
    var selected by rememberSaveable { mutableStateOf(Destination.TODAY) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = windowLayout(maxWidth.value.toInt())
        val compact = layout == WindowLayout.COMPACT
        Scaffold(
            bottomBar = {
                if (compact) NavigationBar(Modifier.testTag("bottom_navigation")) {
                    Destination.entries.forEach { page ->
                        NavigationBarItem(selected == page, { selected = page },
                            icon = { Icon(page.icon, null) }, label = { Text(page.label) })
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (!compact) NavigationRail(Modifier.testTag("navigation_rail")) {
                    Text("F", Modifier.padding(FocusSpacing.large), color = FocusColors.Primary,
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Destination.entries.forEach { page ->
                        NavigationRailItem(selected == page, { selected = page },
                            icon = { Icon(page.icon, null) }, label = { Text(page.label) })
                    }
                }
                Column(Modifier.weight(1f).padding(FocusSpacing.large), verticalArrangement = Arrangement.spacedBy(FocusSpacing.large)) {
                    Text("FocusFlow", style = MaterialTheme.typography.labelLarge, color = FocusColors.Primary)
                    Text(selected.label, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    when (selected) {
                        Destination.TODAY -> {
                            Text("One task. Any device.", color = FocusColors.Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                StatisticCard("今日专注", "0 分钟", Modifier.weight(1f))
                                StatisticCard("待办任务", "$taskCount", Modifier.weight(1f))
                            }
                            EmptyState("从一件重要的小事开始", "任务创建将在下一阶段开放。")
                        }
                        Destination.TASKS -> EmptyState("还没有任务", "在这里安排计划，专注进度将由完成的 Session 累计。")
                        Destination.FOCUS -> EmptyState("为下一次专注留出空间", "可靠计时功能正在开发，尚未开始计时。")
                        Destination.STATS -> EmptyState("每一段专注都值得记录", "完成专注后，这里将呈现你的时间分布。")
                        Destination.SETTINGS -> EmptyState("你的专注空间", "当前为本地模式，尚未连接账号或启用 Focus Guard。")
                    }
                }
                if (layout != WindowLayout.COMPACT) {
                    Surface(Modifier.testTag("supporting_pane").width(if (layout == WindowLayout.EXPANDED) 280.dp else 220.dp).fillMaxHeight()) {
                        Column(Modifier.padding(FocusSpacing.large), verticalArrangement = Arrangement.spacedBy(FocusSpacing.medium)) {
                            Text(if (layout == WindowLayout.EXPANDED) "任务详情" else "专注提示", style = MaterialTheme.typography.titleMedium)
                            Text("一次专注一件事", fontWeight = FontWeight.SemiBold)
                            Text("你的任务保存在本机。跨设备同步将在账号与同步服务就绪后开放。", color = FocusColors.Muted)
                        }
                    }
                }
            }
        }
    }
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
