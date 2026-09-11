package com.focusflow.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusflow.core.*
import com.focusflow.database.TaskRepository
import com.focusflow.database.FocusRepository
import com.focusflow.focus.*
import com.focusflow.designsystem.*
import com.focusflow.network.HttpFocusPresence
import com.focusflow.network.toSyncMessage
import com.focusflow.sync.SyncCoordinator

private enum class Destination(val label: String, val icon: ImageVector) {
    TODAY("今天", Icons.Outlined.Today), TASKS("任务", Icons.Outlined.CheckCircle),
    FOCUS("专注", Icons.Outlined.Timer), STATS("统计", Icons.Outlined.BarChart), SETTINGS("我的", Icons.Outlined.Person),
}

@Composable
fun FocusRoute(
    repository: TaskRepository, focusRepository: FocusRepository, sync: SyncCoordinator? = null, onEnableReminders: (() -> Unit)? = null,
    guardCapabilities: (() -> GuardCapabilities?)? = null, onOpenGuardSetup: (() -> Unit)? = null,
    onGuardModeApplied: ((FocusMode) -> Unit)? = null, onGuardFocusEnded: (() -> Unit)? = null,
) {
    val model = viewModel { TasksViewModel(repository) }
    // 登录后重启 App 生效：presence 凭据在连接时读取，未登录时静默不连接
    val presence = remember(sync) {
        sync?.let { coordinator -> HttpFocusPresence(
            serverUrl = { coordinator.presenceContext()?.first },
            token = { coordinator.presenceContext()?.second },
            deviceId = { coordinator.presenceContext()?.third },
        ) }
    }
    val focus = viewModel { FocusViewModel(focusRepository, presence) }
    FocusApp(model, focus, sync, onEnableReminders, guardCapabilities, onOpenGuardSetup, onGuardModeApplied, onGuardFocusEnded)
}

@Composable
fun FocusApp(
    model: TasksViewModel, focus: FocusViewModel, sync: SyncCoordinator? = null, onEnableReminders: (() -> Unit)? = null,
    guardCapabilities: (() -> GuardCapabilities?)? = null, onOpenGuardSetup: (() -> Unit)? = null,
    onGuardModeApplied: ((FocusMode) -> Unit)? = null, onGuardFocusEnded: (() -> Unit)? = null,
) = FocusTheme {
    val state by model.state.collectAsStateWithLifecycle()
    val focusState by focus.state.collectAsStateWithLifecycle()
    ObserveFocusWhileVisible(focus)
    var selected by rememberSaveable { mutableStateOf(Destination.TODAY) }
    var requestedTask by rememberSaveable { mutableStateOf<String?>(null) }
    if (selected == Destination.FOCUS) {
        FocusScreen(focus, state.data.tasks, requestedTask, { selected = Destination.TODAY }, onEnableReminders,
            guardCapabilities, onOpenGuardSetup, onGuardModeApplied, onGuardFocusEnded)
        return@FocusTheme
    }
    var filter by rememberSaveable { mutableStateOf(TaskFilter.ALL) }
    var search by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var tagId by rememberSaveable { mutableStateOf<String?>(null) }
    var priority by rememberSaveable { mutableStateOf<Priority?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var statsRange by rememberSaveable { mutableStateOf(StatsRange.WEEK) }
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
        // 宽窗口 List-Detail：默认选中首个任务，避免右侧空态
        LaunchedEffect(visible, compact) {
            if (!compact && (detailId == null || visible.none { it.id == detailId })) detailId = visible.firstOrNull()?.id
        }
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
                        focusState.run?.let { run -> item {
                            OutlinedButton(onClick = { selected = Destination.FOCUS }, modifier = Modifier.fillMaxWidth()) {
                                Text("${run.taskTitle} · ${if (run.anchor.state == TimerState.FOCUSING) "正在专注" else "查看本轮专注"}")
                            }
                        } }
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
                            TaskCard(task, state, progress[task.id] ?: 0, modifier = Modifier.animateItem(),
                                onOpen = { detailId = task.id; detailOpen = true },
                                onComplete = { model.complete(task, it) },
                                onStart = { requestedTask = task.id; selected = Destination.FOCUS })
                        }
                    } else item {
                        when (selected) {
                            Destination.STATS -> {
                                val hasCompleted = state.data.sessions.any { it.status == SessionStatus.COMPLETED }
                                if (!hasCompleted) EmptyState("每一段专注都值得记录", "完成第一次专注后，这里会呈现你的时间分布。")
                                else {
                                    val stats = remember(state.data.sessions, state.data.tasks, statsRange, state.today) {
                                        focusStats(state.data.sessions, state.data.tasks, statsRange, state.today)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                        StatsRange.entries.forEach { range ->
                                            FilterChip(statsRange == range, { statsRange = range }, label = { Text(range.label) },
                                                modifier = Modifier.testTag("stats_range_${range.name}"))
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                        StatisticCard("专注时间", "${stats.focusMillis / 60000} 分钟", Modifier.weight(1f))
                                        StatisticCard("完成专注", "${stats.sessionCount} 次", Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                        StatisticCard("完成任务", "${stats.completedTasks} 个", Modifier.weight(1f))
                                        StatisticCard("平均专注", "${stats.avgSessionMillis / 60000} 分钟", Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                        StatisticCard("中断次数", "${stats.interruptCount} 次", Modifier.weight(1f))
                                        StatisticCard("连续专注", "${focusStreak(state.data.sessions, state.today)} 天", Modifier.weight(1f))
                                    }
                                    Text("最近 12 周热力图", style = MaterialTheme.typography.titleMedium)
                                    val weeks = remember(state.data.sessions, state.today) { heatmapWeeks(state.data.sessions, state.today) }
                                    Heatmap(weeks, Modifier.testTag("stats_heatmap"))
                                }
                            }
                            else -> {
                                EmptyState("你的专注空间", "本地模式 · 任务与专注记录保存在这台设备。")
                                OutlinedButton(onClick = { managing = true; model.clearError() }, Modifier.padding(top = FocusSpacing.medium)) { Text("管理项目和标签") }
                                FeedbackSettings()
                                SyncPanel(sync)
                            }
                        }
                    }
                }
                if (!compact) Surface(Modifier.testTag("supporting_pane").width(if (layout == WindowLayout.EXPANDED) 280.dp else 220.dp).fillMaxHeight()) {
                    Column(Modifier.padding(FocusSpacing.large).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FocusSpacing.medium)) {
                        Text("任务详情", style = MaterialTheme.typography.titleMedium)
                        if (detail != null && taskPage) TaskDetail(detail, state, progress[detail.id] ?: 0,
                            onEdit = { model.edit(detail) }, onDelete = { deleteId = detail.id },
                            onStart = { requestedTask = detail.id; selected = Destination.FOCUS })
                        else Text("选择一项任务，查看计划与专注进度。", color = FocusColors.Muted)
                    }
                }
            }
        }
        if (compact && detailOpen && detail != null && taskPage) AlertDialog(
            onDismissRequest = { detailOpen = false }, title = { Text("任务详情") },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                TaskDetail(detail, state, progress[detail.id] ?: 0, { model.edit(detail); detailOpen = false }, { deleteId = detail.id; detailOpen = false },
                    { requestedTask = detail.id; detailOpen = false; selected = Destination.FOCUS })
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
private fun SyncPanel(sync: SyncCoordinator?) {
    val account by (sync?.account ?: kotlinx.coroutines.flow.flowOf(null)).collectAsStateWithLifecycle(null)
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun run(block: suspend () -> String) {
        scope.launch {
            busy = true
            message = try { block() } catch (error: Exception) { error.toSyncMessage() }
            busy = false
        }
    }
    Column(Modifier.padding(top = FocusSpacing.large), verticalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
        Text("云同步", style = MaterialTheme.typography.titleMedium)
        if (sync == null) Text("此构建未接入云同步。", color = FocusColors.Muted)
        else if (account?.token == null) {
            var serverUrl by rememberSaveable { mutableStateOf("") }
            var username by rememberSaveable { mutableStateOf("") }
            var password by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(serverUrl, { serverUrl = it }, Modifier.fillMaxWidth().testTag("sync_server"), label = { Text("服务器地址，如 http://10.0.2.2:8080") }, singleLine = true)
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth().testTag("sync_username"), label = { Text("用户名（3–64 字符）") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().testTag("sync_password"), label = { Text("密码（至少 8 位）") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation())
            Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                Button({ run { sync.login(serverUrl, username, password); "登录成功" } }, enabled = !busy, modifier = Modifier.testTag("sync_login")) { Text("登录") }
                OutlinedButton({ run { sync.register(serverUrl, username, password); "注册成功" } }, enabled = !busy, modifier = Modifier.testTag("sync_register")) { Text("注册") }
            }
        } else {
            Text("已登录：${account?.username}")
            Text("服务器：${account?.serverUrl}", color = FocusColors.Muted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                Button({ run { val summary = sync.syncOnce() ?: error("未登录"); "已同步：推送 ${summary.pushed} 项，接收 ${summary.pulled} 项" } },
                    enabled = !busy, modifier = Modifier.testTag("sync_now")) { Text("立即同步") }
                OutlinedButton({ run { sync.logout(); "已退出登录" } }, enabled = !busy) { Text("退出登录") }
            }
            Text("数据仍以本机为准；联网同步失败不影响本地使用。", color = FocusColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("sync_message")) }
    }
}

@Composable
private fun FeedbackSettings() {
    val feedback = LocalFocusFeedback.current
    val prefs by feedback.prefs.collectAsState()
    Column(Modifier.padding(top = FocusSpacing.large), verticalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
        Text("反馈", style = MaterialTheme.typography.titleMedium)
        SettingSwitch("音效", prefs.sound, "task_setting_sound") { value -> feedback.setPrefs(prefs.copy(sound = value)); feedback.perform(HapticEvent.TAP) }
        SettingSwitch("震动反馈", prefs.haptic, "task_setting_haptic") { value -> feedback.setPrefs(prefs.copy(haptic = value)); if (value) feedback.perform(HapticEvent.TAP) }
        SettingSwitch("减弱动效", prefs.reducedMotion, "task_setting_reduced") { value -> feedback.setPrefs(prefs.copy(reducedMotion = value)) }
        Text("减弱动效会减少位移和缩放动画，保留颜色与淡入淡出。", color = FocusColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingSwitch(label: String, value: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange, modifier = Modifier.testTag(tag))
    }
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
private fun TaskCard(task: Task, state: TasksState, millis: Long, modifier: Modifier = Modifier, onOpen: () -> Unit, onComplete: (Boolean) -> Unit, onStart: () -> Unit) {
    val feedback = LocalFocusFeedback.current
    val prefs by feedback.prefs.collectAsState()
    val done = task.status == TaskStatus.DONE
    val titleColor by animateColorAsState(if (done) FocusColors.Muted else MaterialTheme.colorScheme.onSurface,
        tween(FocusMotion.duration(prefs.reducedMotion, FocusMotion.fast), easing = FocusMotion.easing), label = "task_title")
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag("task_${task.id}"), shape = FocusShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(vertical = FocusSpacing.small, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(done, {
                onComplete(it)
                if (it) { feedback.play(SoundEvent.TASK_COMPLETE); feedback.perform(HapticEvent.SUCCESS) }
                else feedback.perform(HapticEvent.SELECTION)
            }, enabled = !state.busy, modifier = Modifier.testTag("complete_${task.id}").semantics { contentDescription = "完成任务：${task.title}" })
            Column(Modifier.weight(1f).padding(end = FocusSpacing.medium), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(task.title, fontWeight = FontWeight.SemiBold, color = titleColor, textDecoration = if (done) TextDecoration.LineThrough else null)
                Text(listOfNotNull(state.data.projects.find { it.id == task.projectId }?.name, task.plannedDate, if (task.priority != Priority.NONE) priorityLabel(task.priority) else null).joinToString(" · "),
                    color = FocusColors.Muted, style = MaterialTheme.typography.labelMedium)
                Text("${millis / 60000} / ${task.targetFocusMinutes} 分钟", color = FocusColors.Muted, style = MaterialTheme.typography.labelMedium)
                if (task.targetFocusMinutes > 0) LinearProgressIndicator(progress = { (millis.toFloat() / (task.targetFocusMinutes * 60000L)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                val names = state.data.links.filter { it.taskId == task.id }.mapNotNull { link -> state.data.tags.find { it.id == link.tagId }?.name }
                if (names.isNotEmpty()) Text(names.joinToString("  ") { "#$it" }, color = FocusColors.Primary, style = MaterialTheme.typography.labelMedium)
                if (task.status == TaskStatus.TODO || task.status == TaskStatus.IN_PROGRESS) Button(onClick = onStart,
                    modifier = Modifier.testTag("quick_focus_${task.id}")) { Text("开始专注") }
            }
        }
    }
}

@Composable
private fun TaskDetail(task: Task, state: TasksState, millis: Long, onEdit: () -> Unit, onDelete: () -> Unit, onStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.medium)) {
        Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(if (task.status == TaskStatus.DONE) "已完成" else "待完成", color = FocusColors.Primary)
        if (task.description.isNotBlank()) Text(task.description)
        Text("计划日期：${task.plannedDate ?: "未安排"}")
        Text("项目：${state.data.projects.find { it.id == task.projectId }?.name ?: "无"}")
        Text(priorityLabel(task.priority))
        Text("累计专注：${millis / 60000} / ${task.targetFocusMinutes} 分钟")
        Text("完成任务不会自动增加专注时长。", style = MaterialTheme.typography.bodySmall, color = FocusColors.Muted)
        if (task.status == TaskStatus.TODO || task.status == TaskStatus.IN_PROGRESS) Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("开始专注") }
        OutlinedButton(onClick = onEdit, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("编辑任务") }
        OutlinedButton(onClick = onDelete, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("删除任务") }
    }
}
