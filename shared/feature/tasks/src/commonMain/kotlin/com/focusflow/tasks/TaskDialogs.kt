package com.focusflow.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focusflow.core.*
import com.focusflow.designsystem.*
import com.focusflow.designsystem.FocusSpacing
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
fun TaskEditorDialog(editor: TaskEditor, state: TasksState, onDismiss: () -> Unit, onSave: (TaskDraft) -> Unit) {
    val original = editor.original
    var title by rememberSaveable(original?.id) { mutableStateOf(original?.title ?: "") }
    var description by rememberSaveable(original?.id) { mutableStateOf(original?.description ?: "") }
    var date by rememberSaveable(original?.id) { mutableStateOf(if (original == null) state.today else original.plannedDate ?: "") }
    var startTime by rememberSaveable(original?.id) { mutableStateOf(if (original == null) "" else original.plannedStartTime ?: "") }
    var mode by rememberSaveable(original?.id) { mutableStateOf(original?.preferredFocusMode?.name) }
    var minutes by rememberSaveable(original?.id) { mutableStateOf((original?.targetFocusMinutes ?: 25).toString()) }
    var priority by rememberSaveable(original?.id) { mutableStateOf(original?.priority ?: Priority.NONE) }
    var project by rememberSaveable(original?.id) { mutableStateOf(original?.projectId) }
    var tags by rememberSaveable(original?.id) { mutableStateOf(editor.tagIds.toList()) }
    var advanced by rememberSaveable { mutableStateOf(original != null) }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!state.busy) onDismiss() }, title = { Text(if (original == null) "新建任务" else "编辑任务") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                // 任务标题输入框
                FocusTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "任务标题",
                    placeholder = "输入任务标题",
                    modifier = Modifier.testTag("task_title")
                )
                // 日期快捷选择
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                    val tomorrow = LocalDate.parse(state.today).plus(1, DateTimeUnit.DAY).toString()
                    listOf("今天" to state.today, "明天" to tomorrow, "未安排" to "").forEach { (label, value) ->
                        FocusChip(date == value, { date = value }, label = label)
                    }
                }
                // 展开/收起更多选项
                FocusTextButton(onClick = { advanced = !advanced }) { 
                    Text(if (advanced) "收起选项" else "更多选项", color = FocusColors.Primary) 
                }
                if (advanced) {
                    // 备注输入框
                    FocusTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "备注",
                        placeholder = "添加任务备注",
                        modifier = Modifier.testTag("task_description")
                    )
                    // 计划日期输入框
                    FocusTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = "计划日期",
                        placeholder = "YYYY-MM-DD",
                        modifier = Modifier.testTag("task_date")
                    )
                    // 开始时间输入框
                    FocusTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = "开始时间",
                        placeholder = "HH:mm",
                        modifier = Modifier.testTag("task_start_time")
                    )
                    // 目标专注分钟输入框
                    FocusNumberField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = "目标专注分钟",
                        placeholder = "25",
                        suffix = "分钟",
                        modifier = Modifier.testTag("task_minutes")
                    )
                    // 项目、优先级、专注模式选择
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                        ChoiceMenu("项目", project, state.data.projects.map { it.id to it.name }, { project = it }, "无项目")
                        ChoiceMenu("优先级", priority.name, Priority.entries.map { it.name to priorityLabel(it) }, { priority = it?.let(Priority::valueOf) ?: Priority.NONE }, "无优先级")
                        ChoiceMenu("专注模式", mode, listOf("", FocusMode.NORMAL.name, FocusMode.SOFT.name, FocusMode.STRICT.name, FocusMode.EXTREME.name)
                            .map { name: String -> name to (if (name.isEmpty()) "进入时选择" else focusModeLabel(FocusMode.valueOf(name))) },
                            { value: String? -> mode = if (value.isNullOrEmpty()) null else value })
                    }
                    // 专注模式说明
                    Text("设置了专注模式的任务，点击「开始专注」直接按该模式开始（权限不足时进入准备页查看降级说明）。", color = FocusColors.Muted, style = MaterialTheme.typography.bodySmall)
                    // 标签选择
                    Text("标签（可多选）", style = MaterialTheme.typography.labelLarge, color = FocusColors.Ink)
                    if (state.data.tags.isEmpty()) {
                        Text("先在「管理项目和标签」中添加标签。", style = MaterialTheme.typography.bodySmall, color = FocusColors.Muted)
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                            state.data.tags.forEach { tag -> 
                                FocusChip(tag.id in tags, { tags = if (tag.id in tags) tags - tag.id else tags + tag.id }, label = tag.name)
                            }
                        }
                    }
                }
                // 验证错误提示
                (validation ?: state.error)?.let { 
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) 
                }
            }
        },
        confirmButton = {
            FocusPrimaryButton(
                onClick = {
                    val parsed = minutes.toIntOrNull()
                    if (parsed == null) validation = "目标时长需要填写整数"
                    else {
                        val draft = TaskDraft(title = title, description = description, plannedDate = date.ifBlank { null },
                            plannedStartTime = startTime.ifBlank { null }, preferredFocusMode = mode?.let(FocusMode::valueOf),
                            priority = priority, targetFocusMinutes = parsed, projectId = project, tagIds = tags.toSet())
                        try { draft.validate(); validation = null; onSave(draft) }
                        catch (error: IllegalArgumentException) { validation = error.message }
                    }
                },
                enabled = !state.busy && title.isNotBlank(),
                modifier = Modifier.testTag("save_task")
            ) { 
                Text(if (state.busy) "保存中…" else "保存", color = androidx.compose.ui.graphics.Color.White) 
            }
        },
        dismissButton = { 
            FocusTextButton(onClick = onDismiss, enabled = !state.busy) { 
                Text("取消", color = FocusColors.Muted) 
            } 
        },
    )
}

@Composable
fun OrganizationDialog(state: TasksState, model: TasksViewModel, onDismiss: () -> Unit) {
    var projects by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedVersion by rememberSaveable { mutableStateOf(state.organizationVersion) }
    val entries = if (projects) state.data.projects.map { it.id to it.name } else state.data.tags.map { it.id to it.name }
    LaunchedEffect(state.organizationVersion) {
        if (savedVersion != state.organizationVersion) { name = ""; editingId = null; savedVersion = state.organizationVersion }
    }
    AlertDialog(onDismissRequest = { if (!state.busy) onDismiss() }, title = { Text("项目和标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                // 项目/标签切换
                Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                    FocusChip(projects, { projects = true; editingId = null; name = ""; model.clearError() }, label = "项目")
                    FocusChip(!projects, { projects = false; editingId = null; name = ""; model.clearError() }, label = "标签")
                }
                // 列表
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    if (entries.isEmpty()) item { 
                        Text(if (projects) "还没有项目" else "还没有标签", color = FocusColors.Muted, style = MaterialTheme.typography.bodySmall) 
                    }
                    items(entries, key = { it.first }) { (id, label) ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f))
                            IconButton(onClick = { editingId = id; name = label; model.clearError() }, enabled = !state.busy) { 
                                Icon(Icons.Outlined.Edit, "重命名 $label", tint = FocusColors.Primary) 
                            }
                            IconButton(onClick = { deletingId = id }, enabled = !state.busy) { 
                                Icon(Icons.Outlined.Delete, "删除 $label", tint = FocusColors.PriorityHigh) 
                            }
                        }
                    }
                }
                // 名称输入框
                FocusTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = if (editingId == null) "新${if (projects) "项目" else "标签"}名称" else "重命名",
                    placeholder = "输入名称",
                    modifier = Modifier.testTag("organization_name")
                )
                // 错误提示
                state.error?.let { 
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) 
                }
                // 操作按钮
                Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                    FocusPrimaryButton(
                        onClick = {
                            if (projects) model.saveProject(name, state.data.projects.find { it.id == editingId })
                            else model.saveTag(name, state.data.tags.find { it.id == editingId })
                        },
                        enabled = !state.busy && name.isNotBlank(),
                        modifier = Modifier.testTag("save_organization")
                    ) { 
                        Text(if (editingId == null) "添加" else "保存名称", color = androidx.compose.ui.graphics.Color.White) 
                    }
                    if (editingId != null) {
                        FocusTextButton(onClick = { editingId = null; name = "" }) { 
                            Text("取消修改", color = FocusColors.Muted) 
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("完成") } },
    )
    if (deletingId != null) AlertDialog(onDismissRequest = { deletingId = null }, title = { Text(if (projects) "删除项目？" else "删除标签？") },
        text = { Text("任务会保留，并移除与${if (projects) "此项目" else "此标签"}的关联。") },
        confirmButton = { TextButton(onClick = {
            if (projects) state.data.projects.find { it.id == deletingId }?.let(model::deleteProject)
            else state.data.tags.find { it.id == deletingId }?.let(model::deleteTag)
            if (editingId == deletingId) { editingId = null; name = "" }
            deletingId = null
        }, enabled = !state.busy) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { deletingId = null }) { Text("取消") } })
}
