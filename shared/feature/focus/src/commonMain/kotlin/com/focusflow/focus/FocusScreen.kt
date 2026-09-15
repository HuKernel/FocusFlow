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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
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
    onPickCustomBackground: (() -> Unit)? = null, appVersion: String? = null,
    onToggleLandscape: ((Boolean) -> Unit)? = null,
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
    var breakMinutes by rememberSaveable { mutableStateOf("5") }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    var cancelling by rememberSaveable { mutableStateOf(false) }
    var confirmingExtreme by rememberSaveable { mutableStateOf(false) }
    var landscapeLocked by rememberSaveable { mutableStateOf(false) }
    // 离开专注页即归还方向控制权（横屏锁定只在专注页内可选）
    DisposableEffect(Unit) { onDispose { if (landscapeLocked) onToggleLandscape?.invoke(false) } }
    var selectedMode by rememberSaveable(requestedTask) { mutableStateOf(requestedTask?.let { id -> tasks.firstOrNull { it.id == id }?.preferredFocusMode } ?: FocusMode.NORMAL) }
    fun startFocus() {
        val duration = minutes.toLongOrNull()
        val breakDuration = breakMinutes.toLongOrNull()
        if (type == TimerType.COUNTDOWN && (duration == null || duration !in 1L..1440L)) { validation = "请输入 1–1440 之间的整数分钟"; return }
        if (breakDuration == null || breakDuration !in 1L..120L) { validation = "请输入 1–120 之间的休息分钟"; return }
        validation = null
        selectedTask?.let {
            feedback.play(SoundEvent.FOCUS_START); feedback.perform(HapticEvent.START_FOCUS)
            val effective = guardCapabilities?.invoke()?.let { effectiveMode(selectedMode, it) } ?: FocusMode.NORMAL
            if (effective != FocusMode.NORMAL) onGuardModeApplied?.invoke(effective)
            model.start(it, if (type == TimerType.STOPWATCH) 0 else duration!! * 60000, type, effective, breakDuration * 60000)
        }
    }
    val run = state.run
    LaunchedEffect(state.error) { if (state.error != null) feedback.play(SoundEvent.ERROR) }
    // 专注进入休息或终态即解除屏幕固定与守护：休息期间允许自由使用手机
    LaunchedEffect(state.run?.anchor?.state) {
        val phase = state.run?.anchor?.state
        if (phase == TimerState.BREAKING || phase == TimerState.FOCUS_COMPLETED || phase == TimerState.CANCELLED || phase == TimerState.SESSION_FINISHED) {
            onGuardFocusEnded?.invoke()
        }
    }
    // 守卫随会话状态驱动而非仅按钮回调：自动下一轮（休息结束）与进程恢复后同样应用严格/极致守护
    LaunchedEffect(state.run?.session?.id, state.run?.anchor?.state == TimerState.FOCUSING) {
        val run = state.run ?: return@LaunchedEffect
        if (run.anchor.state == TimerState.FOCUSING && run.session.strictMode != FocusMode.NORMAL) {
            onGuardModeApplied?.invoke(run.session.strictMode)
        }
    }
    var announcedCompletion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(run?.session?.id, run?.anchor?.state) {
        val activeRun = run
        if (activeRun != null && activeRun.anchor.state == TimerState.FOCUS_COMPLETED && announcedCompletion != activeRun.session.id) {
            announcedCompletion = activeRun.session.id
            feedback.play(SoundEvent.FOCUS_COMPLETE); feedback.perform(HapticEvent.SUCCESS)
        }
    }
    val theme = focusBackgroundTheme(prefs.focusBackground)
    val customBitmap = LocalCustomBackground.current
    // 内置背景优先用平台层提供的照片（无则回退渐变）；自定义背景用用户图片
    val backgroundImage = if (prefs.focusBackground == FocusBackground.CUSTOM) customBitmap
        else LocalBuiltinBackgrounds.current[prefs.focusBackground]
    val quote = remember(state.run?.session?.id) {
        FocusQuotes.pick(prefs.customQuotes, (state.run?.session?.startedAt ?: requestedTask?.hashCode()?.toLong() ?: System.currentTimeMillis()).coerceAtLeast(0))
    }
    Box(Modifier.fillMaxSize()) {
        if (backgroundImage != null) {
            Image(backgroundImage, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            // 遮罩保证可读性：深色主题压黑，浅色主题（暖米白）压白；遮罩不透明度以最差图片（高亮/高对比）也能读清文字为准
            Box(Modifier.fillMaxSize().background(
                if (theme.dark) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f)
                else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f)))
        } else Box(Modifier.fillMaxSize().background(theme.brush ?: androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(androidx.compose.ui.graphics.Color(0xFF12121C), androidx.compose.ui.graphics.Color(0xFF101018)))))
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides theme.content) {
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 840.dp
            val landscape = maxWidth > maxHeight
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()
                    .then(if (landscape) Modifier else Modifier.verticalScroll(rememberScrollState()))
                    .padding(FocusSpacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FocusSpacing.large)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // 极致模式运行中不提供离开入口：长按返回等系统退出后会在 1 秒内重新固定
                        val extremeRunning = state.run?.let { it.session.strictMode == FocusMode.EXTREME && (it.anchor.state == TimerState.FOCUSING || it.anchor.state == TimerState.PAUSED) } == true
                        if (!extremeRunning) TextButton(onClick = onBack) { Text("返回任务") }
                        Spacer(Modifier.weight(1f))
                        if (onToggleLandscape != null) TextButton(onClick = {
                            landscapeLocked = !landscapeLocked
                            onToggleLandscape?.invoke(landscapeLocked)
                        }) { Text(if (landscapeLocked) "竖屏" else "横屏") }
                        Text("${focusModeLabel(state.run?.session?.strictMode ?: selectedMode)}专注", color = theme.secondary, style = MaterialTheme.typography.labelLarge)
                    }
                    if (!landscape) Text(quote, color = theme.secondary, style = MaterialTheme.typography.bodyMedium)
                    if (!state.loaded) CircularProgressIndicator()
                    else AnimatedContent(run == null,
                        transitionSpec = {
                            val duration = FocusMotion.duration(prefs.reducedMotion, FocusMotion.emphasized)
                            if (prefs.reducedMotion) fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                            else (fadeIn(tween(duration)) + slideInVertically(tween(duration, easing = FocusMotion.easing)) { it / 6 }) togetherWith
                                (fadeOut(tween(duration)) + slideOutVertically(tween(duration, easing = FocusMotion.easing)) { -it / 6 })
                        }, label = "focus_stage") { setupStage ->
                        // 拆成左右两组内容：竖屏单列顺排，横屏双栏并排（设置项在左、模式与开始按钮在右；运行时计时环在左、控制在右）
                        val noise = LocalWhiteNoise.current
                        var noiseKind by rememberSaveable { mutableStateOf(WhiteNoiseKind.SILENCE) }
                        val choices = tasks.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.IN_PROGRESS }
                        @Composable fun setupLeft() {
                            state.remote?.let { remote -> ObserverPanel(remote, state.busy, model::takeover) }
                            Text("准备好，专注一件事", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                            OutlinedTextField(breakMinutes, { breakMinutes = it }, label = { Text("休息分钟（1–120）") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.testTag("break_minutes"))
                        }
                        @Composable fun setupRight() {
                            val capabilities = guardCapabilities?.invoke()
                            if (capabilities == null) Text("普通模式可随时离开或取消，不限制其他应用。", color = theme.secondary)
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
                                    FocusMode.EXTREME -> "极致模式：使用系统屏幕固定，开始后无法退出，直到计时结束自动解锁。"
                                }, color = theme.secondary, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                    FocusBackground.entries.forEach { bg ->
                                        FilterChip(prefs.focusBackground == bg, {
                                            if (bg == FocusBackground.CUSTOM) onPickCustomBackground?.invoke()
                                            feedback.setPrefs(prefs.copy(focusBackground = bg))
                                        }, label = { Text(bg.label) }, modifier = Modifier.testTag("bg_${bg.name}"))
                                    }
                                }
                                if (strength.missingSteps.isNotEmpty()) {
                                    Text("缺少：${strength.missingSteps.joinToString("、")}。开启后按 ${strength.effective.name} 模式运行。", color = theme.secondary, style = MaterialTheme.typography.bodySmall)
                                    if (onOpenGuardSetup != null) TextButton(onClick = onOpenGuardSetup) { Text("去开启专注防护") }
                                }
                            }
                            Text(if (state.remindersAvailable) "结束提醒已开启，系统省电时可能延后" else "结束提醒未开启，计时仍正常保存", style = MaterialTheme.typography.bodySmall)
                            if (!state.remindersAvailable && onEnableReminders != null) OutlinedButton(onClick = onEnableReminders) { Text("开启结束提醒") }
                            validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(shape = FocusShapes.button, colors = ButtonDefaults.buttonColors(
                                containerColor = theme.content, contentColor = if (theme.dark) androidx.compose.ui.graphics.Color(0xFF1B2437) else androidx.compose.ui.graphics.Color.White),
                                modifier = Modifier.testTag("start_focus").height(52.dp).padding(horizontal = FocusSpacing.large),
                                onClick = {
                                    val duration = minutes.toLongOrNull()
                                    val breakDuration = breakMinutes.toLongOrNull()
                                    if (type == TimerType.COUNTDOWN && (duration == null || duration !in 1L..1440L)) validation = "请输入 1–1440 之间的整数分钟"
                                    else if (breakDuration == null || breakDuration !in 1L..120L) validation = "请输入 1–120 之间的休息分钟"
                                    else {
                                        val effective = guardCapabilities?.invoke()?.let { effectiveMode(selectedMode, it) } ?: FocusMode.NORMAL
                                        // 极致模式经应用内确认弹窗二次确认（不可退出性质的开始需明确仪式感）
                                        if (effective == FocusMode.EXTREME) confirmingExtreme = true else startFocus()
                                    }
                                }, enabled = !state.busy && choices.any { it.id == selectedTask }) { Text("开始专注", style = MaterialTheme.typography.labelLarge) }
                        }
                        @Composable fun runLeft() {
                            val active = run ?: return
                            Text(active.taskTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            val phase = active.anchor.state
                            val breaking = phase == TimerState.BREAKING
                            val stopwatch = active.session.type == TimerType.STOPWATCH && !breaking
                            val running = phase == TimerState.FOCUSING || phase == TimerState.PAUSED
                            val display = if (!running && !breaking) active.session.actualDuration else if (stopwatch) state.elapsed / 1000 * 1000 else state.remaining
                            TimerRing(display,
                                if (stopwatch) 1f else state.elapsed.toFloat() / active.anchor.plannedDuration.coerceAtLeast(1),
                                when (phase) { TimerState.PAUSED -> "已暂停"; TimerState.BREAKING -> "休息中"; TimerState.FOCUSING -> if (stopwatch) "已专注" else "剩余时间"; else -> "本轮已结束" },
                                lightContent = !theme.dark, ringSize = if (landscape) 220.dp else 280.dp)
                            if (active.recoveredWithWallClock) Text("设备重启后的时长按系统时间估算。", style = MaterialTheme.typography.bodySmall, color = theme.secondary)
                        }
                        @Composable fun runRight() {
                            val active = run ?: return
                            val phase = active.anchor.state
                            val running = phase == TimerState.FOCUSING || phase == TimerState.PAUSED
                            val extreme = active.session.strictMode == FocusMode.EXTREME
                            val stopwatch = active.session.type == TimerType.STOPWATCH && phase != TimerState.BREAKING
                            if (noise != null) Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.small)) {
                                listOf(WhiteNoiseKind.SILENCE to "无声", WhiteNoiseKind.RAIN to "雨声", WhiteNoiseKind.WIND to "风声").forEach { (kind, label) ->
                                    FilterChip(noiseKind == kind, {
                                        noiseKind = kind
                                        if (kind == WhiteNoiseKind.SILENCE) noise.stop() else noise.start(kind)
                                    }, label = { Text(label) })
                                }
                            }
                            when {
                                running -> {
                                    if (extreme) {
                                        Text("极致模式不提供暂停，也无法中途退出：长按返回等系统退出会在 1 秒内被重新固定，请等计时结束。", color = theme.secondary, style = MaterialTheme.typography.bodySmall)
                                    } else Button(onClick = {
                                        if (phase == TimerState.PAUSED) { feedback.play(SoundEvent.FOCUS_RESUME); model.resume(active.session.id) }
                                        else { feedback.play(SoundEvent.FOCUS_PAUSE); model.pause(active.session.id) }
                                        feedback.perform(HapticEvent.TAP)
                                    }, enabled = !state.busy, modifier = Modifier.testTag("pause_resume")) {
                                        Crossfade(phase == TimerState.PAUSED, animationSpec = tween(FocusMotion.duration(prefs.reducedMotion, FocusMotion.fast)), label = "pause_morph") { paused ->
                                            Row {
                                                if (paused) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(FocusSpacing.small)); Text("继续专注") }
                                                else { Icon(Icons.Filled.Pause, null); Spacer(Modifier.width(FocusSpacing.small)); Text("暂停") }
                                            }
                                        }
                                    }
                                    if (stopwatch) Button(onClick = { model.complete(active.session.id) },
                                        enabled = !state.busy, modifier = Modifier.testTag("complete_focus")) { Text("完成专注") }
                                    if (!extreme) OutlinedButton(onClick = { cancelling = true }, enabled = !state.busy) { Text("取消本次专注") }
                                    else Text("极致模式不支持中途取消：请等待计时结束（正计时请点「完成专注」）；计时结束前无法退出，长按返回也会被重新固定。", color = theme.secondary, style = MaterialTheme.typography.bodySmall)
                                }
                                phase == TimerState.BREAKING -> {
                                    Text("休息中：现在可以自由使用手机，休息结束会自动开始下一轮专注；时间不会计入任务进度。")
                                    OutlinedButton(onClick = { model.skipBreak(active.session.id) }, enabled = !state.busy) { Text("结束休息") }
                                }
                                else -> {
                                    val celebrated = phase == TimerState.FOCUS_COMPLETED
                                    val scale by animateFloatAsState(if (celebrated) 1f else 0.92f,
                                        if (prefs.reducedMotion) tween(0) else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "celebration")
                                    Text(if (phase == TimerState.CANCELLED) "已取消，本轮不计入专注进度" else "专注已完成，记录 ${timerText(active.session.actualDuration)}",
                                        color = FocusColors.Primary, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
                                    if (celebrated) Button(onClick = { feedback.play(SoundEvent.BREAK_START); model.startBreak(active.session.id) }, enabled = !state.busy) { Text("休息 ${active.breakDuration / 60_000} 分钟") }
                                    OutlinedButton(onClick = { onGuardFocusEnded?.invoke(); model.dismiss(active.session.id) }, enabled = !state.busy, modifier = Modifier.testTag("dismiss_focus")) { Text("结束本轮") }
                                }
                            }
                        }
                        if (landscape) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.large, Alignment.CenterHorizontally)) {
                            // ponytail: 横屏双栏收窄居中；勿加 fillMaxHeight/Center 与 scroll 的组合（无限重测，测试实证）。内栏纯滚动仅任务过多溢出时生效
                            Column(Modifier.weight(1f, fill = false).widthIn(max = 460.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FocusSpacing.large)) {
                                if (setupStage) setupLeft() else runLeft()
                            }
                            Column(Modifier.weight(1f, fill = false).widthIn(max = 460.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FocusSpacing.large)) {
                                if (setupStage) setupRight() else runRight()
                            }
                        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FocusSpacing.large)) {
                            if (setupStage) { setupLeft(); setupRight() } else { runLeft(); runRight() }
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = model::retry) { Text("重试恢复") } }
                }
                if (wide && !landscape) Surface(Modifier.width(260.dp).fillMaxHeight().testTag("focus_side_pane")) {
                    val activeRun = run
                    Column(Modifier.padding(FocusSpacing.large), verticalArrangement = Arrangement.spacedBy(FocusSpacing.medium)) {
                        Text("本轮进度", style = MaterialTheme.typography.titleMedium)
                        if (activeRun == null) Text("开始专注后，这里显示本轮进度与设备状态。", color = theme.secondary)
                        else {
                            Text(activeRun.taskTitle, style = MaterialTheme.typography.titleSmall)
                            val planned = activeRun.anchor.plannedDuration
                            val done = activeRun.session.actualDuration
                            if (planned > 0) LinearProgressIndicator(progress = { (state.elapsed.toFloat() / planned).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                            Text(if (planned > 0) "计划 ${timerText(planned)}" else "正计时 · 不设上限", color = theme.secondary)
                            Text("暂停累计 ${timerText(activeRun.session.pausedDuration)}", color = theme.secondary)
                        }
                        Text("设备", style = MaterialTheme.typography.titleSmall)
                        Text("本机控制 · 记录保存在这台设备", color = theme.secondary)
                        Text("离开页面不会停止计时；暂停时不累计专注时长。", color = theme.secondary)
                        Text("跨设备观察与接力将在多设备阶段开启。", color = theme.secondary)
                    }
                }
            }
        }
    }
    if (confirmingExtreme && run == null) AlertDialog(onDismissRequest = { confirmingExtreme = false }, title = { Text("开始极致专注？") },
        text = { Text("确认后屏幕将被固定：直到计时结束（正计时为手动完成）都无法退出，长按返回等系统退出方式会在数秒内被重新固定。首次开始时系统会再弹一次「应用固定」授权，请点「开始使用」。") },
        confirmButton = { TextButton(onClick = { confirmingExtreme = false; startFocus() }, enabled = !state.busy) { Text("确认开始") } },
        dismissButton = { TextButton(onClick = { confirmingExtreme = false }) { Text("再想想") } })
    if (cancelling && run != null) AlertDialog(onDismissRequest = { cancelling = false }, title = { Text("取消本次专注？") },
        text = { Text("已用时间会保存为取消记录，但不会增加任务的专注进度。") },
        confirmButton = { TextButton(onClick = { feedback.perform(HapticEvent.WARNING); model.cancel(run.session.id); cancelling = false }, enabled = !state.busy) { Text("确认取消") } },
        dismissButton = { TextButton(onClick = { cancelling = false }) { Text("继续专注") } })
    }
    }
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
            }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("发起设备 ${remote.snapshot.ownerDeviceId.takeLast(8)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onTakeover, enabled = !busy, modifier = Modifier.testTag("takeover_focus")) { Text("在这台设备继续") }
            Text("接管后原设备转为观察；计时基于共享锚点估算。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

