package com.focusflow.focus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusflow.core.*
import com.focusflow.designsystem.*

@Composable
fun FocusScreen(
    model: FocusViewModel, tasks: List<Task>, requestedTask: String?, onBack: () -> Unit, onEnableReminders: (() -> Unit)?,
    guardCapabilities: (() -> GuardCapabilities?)? = null, onOpenGuardSetup: (() -> Unit)? = null,
    onGuardModeApplied: ((FocusMode) -> Unit)? = null, onGuardFocusEnded: (() -> Unit)? = null,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val feedback = LocalFocusFeedback.current
    val prefs by feedback.prefs.collectAsState()
    var selectedTask by rememberSaveable(requestedTask) { mutableStateOf(requestedTask) }
    var type by rememberSaveable { mutableStateOf(TimerType.COUNTDOWN) }
    // 从任务的「开始专注」进入时，预填该任务的目标专注时长；直接打开专注页时用默认 25 分钟
    var minutes by rememberSaveable(requestedTask) {
        mutableStateOf(requestedTask?.let { id -> tasks.firstOrNull { it.id == id }?.targetFocusMinutes }?.takeIf { it > 0 }?.toString() ?: "25")
    }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    var cancelling by rememberSaveable { mutableStateOf(false) }
    var selectedMode by rememberSaveable { mutableStateOf(FocusMode.NORMAL) }
    val run = state.run
    LaunchedEffect(state.error) { if (state.error != null) feedback.play(SoundEvent.ERROR) }
    var announcedCompletion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(run?.session?.id, run?.anchor?.state) {
        val activeRun = run
        if (activeRun != null && activeRun.anchor.state == TimerState.FOCUS_COMPLETED && announcedCompletion != activeRun.session.id) {
            announcedCompletion = activeRun.session.id
            feedback.play(SoundEvent.FOCUS_COMPLETE); feedback.perform(HapticEvent.SUCCESS)
        }
    }
    Scaffold { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(FocusSpacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FocusSpacing.large)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { Text("返回任务") }
                        Spacer(Modifier.weight(1f))
                        Text("普通专注", color = FocusColors.Primary)
                    }
                    if (!state.loaded) CircularProgressIndicator()
                    else AnimatedContent(run == null,
                        transitionSpec = {
                            val duration = FocusMotion.duration(prefs.reducedMotion, FocusMotion.emphasized)
                            if (prefs.reducedMotion) fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                            else (fadeIn(tween(duration)) + slideInVertically(tween(duration, easing = FocusMotion.easing)) { it / 6 }) togetherWith
                                (fadeOut(tween(duration)) + slideOutVertically(tween(duration, easing = FocusMotion.easing)) { -it / 6 })
                        }, label = "focus_stage") { setupStage ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FocusSpacing.large)) {
                            if (setupStage) {
                                state.remote?.let { remote -> ObserverPanel(remote, state.busy, model::takeover) }
                                Text("准备好，专注一件事", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                val choices = tasks.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.IN_PROGRESS }
                                if (choices.isEmpty()) Text("请先创建一项未完成的任务。")
                                choices.forEach { task -> FilterChip(selectedTask == task.id, { selectedTask = task.id }, label = { Text(task.title) }, modifier = Modifier.fillMaxWidth()) }
                                Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                    FilterChip(type == TimerType.COUNTDOWN, { type = TimerType.COUNTDOWN }, label = { Text("倒计时") })
                                    FilterChip(type == TimerType.STOPWATCH, { type = TimerType.STOPWATCH }, label = { Text("正计时") })
                                }
                                if (type == TimerType.COUNTDOWN) {
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                        listOf(15, 25, 45).forEach { preset -> FilterChip(minutes == "$preset", { minutes = "$preset" }, label = { Text("$preset 分钟") }) }
                                    }
                                    OutlinedTextField(minutes, { minutes = it }, label = { Text("专注分钟（1–1440）") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.testTag("focus_minutes"))
                                }
                                val capabilities = guardCapabilities?.invoke()
                                if (capabilities == null) Text("普通模式可随时离开或取消，不限制其他应用。", color = FocusColors.Muted)
                                else {
                                    val strength = guardStrength(selectedMode, capabilities)
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                        listOf(FocusMode.NORMAL to "普通", FocusMode.SOFT to "软性", FocusMode.STRICT to "严格", FocusMode.EXTREME to "极致").forEach { (choice, label) ->
                                            FilterChip(selectedMode == choice, { selectedMode = choice }, label = { Text(label) }, modifier = Modifier.testTag("guard_mode_${choice.name}"))
                                        }
                                    }
                                    Text(when (strength.effective) {
                                        FocusMode.NORMAL -> "当前权限下按普通模式计时：可随时离开，不限制其他应用。"
                                        FocusMode.SOFT -> "软性模式：允许切换应用，专注结束后可查看中断记录（需使用情况访问）。"
                                        FocusMode.STRICT -> "严格模式：离开白名单应用会收到回到专注的提醒。"
                                        FocusMode.EXTREME -> "极致模式：使用系统屏幕固定，长按返回键可退出（Emergency Exit）。"
                                    }, color = FocusColors.Muted, style = MaterialTheme.typography.bodySmall)
                                    if (strength.missingSteps.isNotEmpty()) {
                                        Text("缺少：${strength.missingSteps.joinToString("、")}。开启后按 ${strength.effective.name} 模式运行。", color = FocusColors.Muted, style = MaterialTheme.typography.bodySmall)
                                        if (onOpenGuardSetup != null) TextButton(onClick = onOpenGuardSetup) { Text("去开启专注防护") }
                                    }
                                }
                                Text(if (state.remindersAvailable) "结束提醒已开启，系统省电时可能延后" else "结束提醒未开启，计时仍正常保存", style = MaterialTheme.typography.bodySmall)
                                if (!state.remindersAvailable && onEnableReminders != null) OutlinedButton(onClick = onEnableReminders) { Text("开启结束提醒") }
                                validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                Button(onClick = {
                                    val duration = minutes.toLongOrNull()
                                    if (type == TimerType.COUNTDOWN && (duration == null || duration !in 1L..1440L)) validation = "请输入 1–1440 之间的整数分钟"
                                    else {
                                        validation = null
                                        selectedTask?.let {
                                            feedback.play(SoundEvent.FOCUS_START); feedback.perform(HapticEvent.START_FOCUS)
                                            val effective = guardCapabilities?.invoke()?.let { effectiveMode(selectedMode, it) } ?: FocusMode.NORMAL
                                            if (effective != FocusMode.NORMAL) onGuardModeApplied?.invoke(effective)
                                            model.start(it, if (type == TimerType.STOPWATCH) 0 else duration!! * 60000, type, effective)
                                        }
                                    }
                                }, enabled = !state.busy && choices.any { it.id == selectedTask }, modifier = Modifier.testTag("start_focus")) { Text("开始专注") }
                            } else if (run != null) {
                                val phase = run.anchor.state
                                val running = phase == TimerState.FOCUSING || phase == TimerState.PAUSED
                                val breaking = phase == TimerState.BREAKING
                                Text(run.taskTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                val stopwatch = run.session.type == TimerType.STOPWATCH && !breaking
                                val display = if (!running && !breaking) run.session.actualDuration else if (stopwatch) state.elapsed / 1000 * 1000 else state.remaining
                                TimerRing(display,
                                    if (stopwatch) 1f else state.elapsed.toFloat() / run.anchor.plannedDuration.coerceAtLeast(1),
                                    when (phase) { TimerState.PAUSED -> "已暂停"; TimerState.BREAKING -> "休息中"; TimerState.FOCUSING -> if (stopwatch) "已专注" else "剩余时间"; else -> "本轮已结束" })
                                if (run.recoveredWithWallClock) Text("设备重启后的时长按系统时间估算。", style = MaterialTheme.typography.bodySmall, color = FocusColors.Muted)
                                when {
                                    running -> {
                                        Button(onClick = {
                                            if (phase == TimerState.PAUSED) { feedback.play(SoundEvent.FOCUS_RESUME); model.resume(run.session.id) }
                                            else { feedback.play(SoundEvent.FOCUS_PAUSE); model.pause(run.session.id) }
                                            feedback.perform(HapticEvent.TAP)
                                        }, enabled = !state.busy, modifier = Modifier.testTag("pause_resume")) {
                                            Crossfade(phase == TimerState.PAUSED, animationSpec = tween(FocusMotion.duration(prefs.reducedMotion, FocusMotion.fast)), label = "pause_morph") { paused ->
                                                Row {
                                                    if (paused) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(FocusSpacing.small)); Text("继续专注") }
                                                    else { Icon(Icons.Filled.Pause, null); Spacer(Modifier.width(FocusSpacing.small)); Text("暂停") }
                                                }
                                            }
                                        }
                                        if (stopwatch) Button(onClick = { model.complete(run.session.id) },
                                            enabled = !state.busy, modifier = Modifier.testTag("complete_focus")) { Text("完成专注") }
                                        OutlinedButton(onClick = { cancelling = true }, enabled = !state.busy) { Text("取消本次专注") }
                                    }
                                    breaking -> {
                                        Text("专注已记录，休息时间不会计入任务进度。")
                                        OutlinedButton(onClick = { model.skipBreak(run.session.id) }, enabled = !state.busy) { Text("结束休息") }
                                    }
                                    else -> {
                                        val celebrated = phase == TimerState.FOCUS_COMPLETED
                                        val scale by animateFloatAsState(if (celebrated) 1f else 0.92f,
                                            if (prefs.reducedMotion) tween(0) else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "celebration")
                                        Text(if (phase == TimerState.CANCELLED) "已取消，本轮不计入专注进度" else "专注已完成，记录 ${timerText(run.session.actualDuration)}",
                                            color = FocusColors.Primary, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
                                        if (celebrated) Button(onClick = { feedback.play(SoundEvent.BREAK_START); model.startBreak(run.session.id) }, enabled = !state.busy) { Text("休息 5 分钟") }
                                        OutlinedButton(onClick = { onGuardFocusEnded?.invoke(); model.dismiss(run.session.id) }, enabled = !state.busy, modifier = Modifier.testTag("dismiss_focus")) { Text("结束本轮") }
                                    }
                                }
                            }
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = model::retry) { Text("重试恢复") } }
                }
                if (wide) Surface(Modifier.width(260.dp).fillMaxHeight().testTag("focus_side_pane")) {
                    val activeRun = run
                    Column(Modifier.padding(FocusSpacing.large), verticalArrangement = Arrangement.spacedBy(FocusSpacing.medium)) {
                        Text("本轮进度", style = MaterialTheme.typography.titleMedium)
                        if (activeRun == null) Text("开始专注后，这里显示本轮进度与设备状态。", color = FocusColors.Muted)
                        else {
                            Text(activeRun.taskTitle, style = MaterialTheme.typography.titleSmall)
                            val planned = activeRun.anchor.plannedDuration
                            val done = activeRun.session.actualDuration
                            if (planned > 0) LinearProgressIndicator(progress = { (state.elapsed.toFloat() / planned).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                            Text(if (planned > 0) "计划 ${timerText(planned)}" else "正计时 · 不设上限", color = FocusColors.Muted)
                            Text("暂停累计 ${timerText(activeRun.session.pausedDuration)}", color = FocusColors.Muted)
                        }
                        Text("设备", style = MaterialTheme.typography.titleSmall)
                        Text("本机控制 · 记录保存在这台设备", color = FocusColors.Muted)
                        Text("离开页面不会停止计时；暂停时不累计专注时长。", color = FocusColors.Muted)
                        Text("跨设备观察与接力将在多设备阶段开启。", color = FocusColors.Muted)
                    }
                }
            }
        }
    }
    if (cancelling && run != null) AlertDialog(onDismissRequest = { cancelling = false }, title = { Text("取消本次专注？") },
        text = { Text("已用时间会保存为取消记录，但不会增加任务的专注进度。") },
        confirmButton = { TextButton(onClick = { feedback.perform(HapticEvent.WARNING); model.cancel(run.session.id); cancelling = false }, enabled = !state.busy) { Text("确认取消") } },
        dismissButton = { TextButton(onClick = { cancelling = false }) { Text("继续专注") } })
}

@Composable
fun ObserverPanel(remote: RemoteFocus, busy: Boolean, onTakeover: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("observer_panel"), shape = FocusShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(FocusSpacing.large), verticalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
            Text("另一台设备正在专注", style = MaterialTheme.typography.labelMedium, color = FocusColors.Primary)
            Text(remote.snapshot.taskTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val stopwatch = remote.snapshot.plannedDuration == 0L
            Text(when {
                remote.snapshot.timerState == TimerState.PAUSED -> "已暂停"
                stopwatch -> "已专注 ${timerText(remote.elapsedMillis)}"
                else -> "剩余 ${timerText(remote.remainingMillis)}"
            }, color = FocusColors.Muted)
            Text("发起设备 ${remote.snapshot.ownerDeviceId.takeLast(8)}", style = MaterialTheme.typography.bodySmall, color = FocusColors.Muted)
            Button(onClick = onTakeover, enabled = !busy, modifier = Modifier.testTag("takeover_focus")) { Text("在这台设备继续") }
            Text("接管后原设备转为观察；计时基于共享锚点估算。", style = MaterialTheme.typography.bodySmall, color = FocusColors.Muted)
        }
    }
}
