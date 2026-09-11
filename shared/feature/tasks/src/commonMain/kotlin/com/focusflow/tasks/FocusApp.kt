package com.focusflow.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusflow.core.*
import com.focusflow.database.TaskRepository
import com.focusflow.designsystem.*

private enum class Destination(val label: String, val icon: ImageVector) {
    TODAY("今天", Icons.Outlined.Today), TASKS("任务", Icons.Outlined.CheckCircle),
    FOCUS("专注", Icons.Outlined.Timer), STATS("统计", Icons.Outlined.BarChart), SETTINGS("我的", Icons.Outlined.Person),
}

@Composable
fun FocusRoute(repository: TaskRepository) {
    val model = viewModel { TasksViewModel(repository) }
    FocusApp(model)
}

@Composable
fun FocusApp(model: TasksViewModel) = FocusTheme {
    val state by model.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(Destination.TODAY) }
    var filter by rememberSaveable { mutableStateOf(TaskFilter.ALL) }
    var search by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var tagId by rememberSaveable { mutableStateOf<String?>(null) }
    var priority by rememberSaveable { mutableStateOf<Priority?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var managing by rememberSaveable { mutableStateOf(false) }
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        if (state.error != null && state.editor == null && !managing) {
            snackbars.showSnackbar(state.error!!)
            model.clearError()
        }
    }
    LaunchedEffect(state.data.projects, state.data.tags) {
        if (projectId != null && state.data.projects.none { it.id == projectId }) projectId = null
        if (tagId != null && state.data.tags.none { it.id == tagId }) tagId = null
    }
    val detail = state.data.tasks.find { it.id == detailId }
    val taskPage = selected == Destination.TODAY || selected == Destination.TASKS
    val visible = filterTasks(state.data.tasks, if (selected == Destination.TODAY) TaskFilter.TODAY else filter,
        state.today, if (selected == Destination.TASKS) search else "",
        if (selected == Destination.TASKS) projectId else null, if (selected == Destination.TASKS) tagId else null,
        if (selected == Destination.TASKS) priority else null, state.data.links)
    val progress = remember(state.data.sessions) { state.data.sessions.groupBy { it.taskId }.mapValues { (_, sessions) -> sessions.sumOf { it.actualDuration } } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = windowLayout(maxWidth.value.toInt())
        val compact = layout == WindowLayout.COMPACT
        Scaffold(
            snackbarHost = { SnackbarHost(snackbars) },
            floatingActionButton = {
                if (taskPage && state.loaded && !state.loadError) FloatingActionButton(onClick = { model.edit() }, modifier = Modifier.testTag("add_task")) {
                    Icon(Icons.Outlined.Add, "新建任务")
                }
            },
            bottomBar = {
                if (compact) NavigationBar(Modifier.testTag("bottom_navigation")) {
                    Destination.entries.forEach { page -> NavigationBarItem(selected == page, { selected = page },
                        icon = { Icon(page.icon, null) }, label = { Text(page.label) }) }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (!compact) NavigationRail(Modifier.testTag("navigation_rail")) {
                    Text("F", Modifier.padding(FocusSpacing.large), color = FocusColors.Primary, style = MaterialTheme.typography.headlineMedium)
                    Destination.entries.forEach { page -> NavigationRailItem(selected == page, { selected = page },
                        icon = { Icon(page.icon, null) }, label = { Text(page.label) }) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxHeight().testTag("task_list"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(FocusSpacing.medium)) {
                    item {
                        Text("FocusFlow", style = MaterialTheme.typography.labelLarge, color = FocusColors.Primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selected.label, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            if (taskPage) IconButton(onClick = { managing = true; model.clearError() }, enabled = state.loaded && !state.busy) {
                                Icon(Icons.Outlined.Folder, "管理项目和标签")
                            }
                        }
                    }
                    if (state.loadError) item {
                        Text("暂时无法读取本地数据，请重试", color = MaterialTheme.colorScheme.error)
                        Button(onClick = model::retryLoading) { Text("重新加载") }
                    } else if (!state.loaded) item { CircularProgressIndicator() }
                    else if (taskPage) {
                        if (selected == Destination.TODAY) item {
                            Text(state.today, color = FocusColors.Muted)
                            Spacer(Modifier.height(FocusSpacing.medium))
                            val minutes = state.data.sessions.filter { it.endedAt?.let { end -> localDateAt(end) == state.today } == true }.sumOf { it.actualDuration } / 60000
                            Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                StatisticCard("今日专注", "$minutes 分钟", Modifier.weight(1f))
                                StatisticCard("今日任务", "${visible.count { it.status == TaskStatus.DONE }} / ${visible.size}", Modifier.weight(1f))
                                StatisticCard("连续专注", "${focusStreak(state.data.sessions, state.today)} 天", Modifier.weight(1f))
                            }
                        } else item {
                            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("搜索任务") }, singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, null) })
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                TaskFilter.entries.forEach { choice -> FilterChip(filter == choice, { filter = choice }, label = { Text(choice.label) }) }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                ChoiceMenu("项目", projectId, state.data.projects.map { it.id to it.name }, { projectId = it })
                                ChoiceMenu("标签", tagId, state.data.tags.map { it.id to it.name }, { tagId = it })
                                ChoiceMenu("优先级", priority?.name, Priority.entries.map { it.name to priorityLabel(it) }, { priority = it?.let(Priority::valueOf) })
                            }
                        }
                        if (visible.isEmpty()) item { EmptyState(if (selected == Destination.TODAY) "今天，先做好一件事" else "没有符合条件的任务", "点击右下角 + 创建任务，也可以调整筛选。") }
                        items(visible, key = { it.id }) { task ->
                            TaskCard(task, state, progress[task.id] ?: 0, onOpen = { detailId = task.id; detailOpen = true },
                                onComplete = { model.complete(task, it) })
                        }
                    } else item {
                        when (selected) {
                            Destination.FOCUS -> EmptyState("为下一次专注留出空间", "任务已经可以安排，计时功能将在下一阶段开放。")
                            Destination.STATS -> EmptyState("每一段专注都值得记录", "完成专注后，这里将呈现你的时间分布。")
                            else -> {
                                EmptyState("你的专注空间", "本地模式 · 数据保存在这台设备，尚未开启云同步。")
                                OutlinedButton(onClick = { managing = true; model.clearError() }, Modifier.padding(top = FocusSpacing.medium)) { Text("管理项目和标签") }
                            }
                        }
                    }
                }
                if (!compact) Surface(Modifier.testTag("supporting_pane").width(if (layout == WindowLayout.EXPANDED) 280.dp else 220.dp).fillMaxHeight()) {
                    Column(Modifier.padding(FocusSpacing.large).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FocusSpacing.medium)) {
                        Text("任务详情", style = MaterialTheme.typography.titleMedium)
                        if (detail != null && taskPage) TaskDetail(detail, state, progress[detail.id] ?: 0,
                            onEdit = { model.edit(detail) }, onDelete = { deleteId = detail.id })
                        else Text("选择一项任务，查看计划与专注进度。", color = FocusColors.Muted)
                    }
                }
            }
        }
        if (compact && detailOpen && detail != null && taskPage) AlertDialog(
            onDismissRequest = { detailOpen = false }, title = { Text("任务详情") },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                TaskDetail(detail, state, progress[detail.id] ?: 0, { model.edit(detail); detailOpen = false }, { deleteId = detail.id; detailOpen = false })
            } }, confirmButton = { TextButton(onClick = { detailOpen = false }) { Text("关闭") } },
        )
    }
    state.editor?.let { editor -> TaskEditorDialog(editor, state, onDismiss = model::dismissEditor, onSave = model::save) }
    val deleting = state.data.tasks.find { it.id == deleteId }
    if (deleting != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("删除任务？") },
        text = { Text("“${deleting.title}”将从任务列表移除，已有专注记录会保留。") },
        confirmButton = { TextButton(onClick = { model.delete(deleting); deleteId = null }, enabled = !state.busy) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } })
    if (managing) OrganizationDialog(state, model, onDismiss = { managing = false; model.clearError() })
}

fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.NONE -> "无优先级"; Priority.LOW -> "低优先级"; Priority.MEDIUM -> "中优先级"; Priority.HIGH -> "高优先级"
}

@Composable
fun ChoiceMenu(label: String, value: String?, choices: List<Pair<String, String>>, onSelect: (String?) -> Unit, noneLabel: String = "不限") {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(choices.find { it.first == value }?.second ?: "$label · $noneLabel") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onSelect(null); expanded = false })
            choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

@Composable
private fun TaskCard(task: Task, state: TasksState, millis: Long, onOpen: () -> Unit, onComplete: (Boolean) -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("task_${task.id}"), shape = FocusShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(vertical = FocusSpacing.small, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(task.status == TaskStatus.DONE, onComplete, enabled = !state.busy, modifier = Modifier.testTag("complete_${task.id}").semantics { contentDescription = "完成任务：${task.title}" })
            Column(Modifier.weight(1f).padding(end = FocusSpacing.medium), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(task.title, fontWeight = FontWeight.SemiBold, textDecoration = if (task.status == TaskStatus.DONE) TextDecoration.LineThrough else null)
                Text(listOfNotNull(state.data.projects.find { it.id == task.projectId }?.name, task.plannedDate, if (task.priority != Priority.NONE) priorityLabel(task.priority) else null).joinToString(" · "),
                    color = FocusColors.Muted, style = MaterialTheme.typography.labelMedium)
                Text("${millis / 60000} / ${task.targetFocusMinutes} 分钟", color = FocusColors.Muted, style = MaterialTheme.typography.labelMedium)
                if (task.targetFocusMinutes > 0) LinearProgressIndicator(progress = { (millis.toFloat() / (task.targetFocusMinutes * 60000L)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                val names = state.data.links.filter { it.taskId == task.id }.mapNotNull { link -> state.data.tags.find { it.id == link.tagId }?.name }
                if (names.isNotEmpty()) Text(names.joinToString("  ") { "#$it" }, color = FocusColors.Primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TaskDetail(task: Task, state: TasksState, millis: Long, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.medium)) {
        Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(if (task.status == TaskStatus.DONE) "已完成" else "待完成", color = FocusColors.Primary)
        if (task.description.isNotBlank()) Text(task.description)
        Text("计划日期：${task.plannedDate ?: "未安排"}")
        Text("项目：${state.data.projects.find { it.id == task.projectId }?.name ?: "无"}")
        Text(priorityLabel(task.priority))
        Text("累计专注：${millis / 60000} / ${task.targetFocusMinutes} 分钟")
        Text("完成任务不会自动增加专注时长。", style = MaterialTheme.typography.bodySmall, color = FocusColors.Muted)
        Button(onClick = onEdit, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("编辑任务") }
        OutlinedButton(onClick = onDelete, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("删除任务") }
    }
}
