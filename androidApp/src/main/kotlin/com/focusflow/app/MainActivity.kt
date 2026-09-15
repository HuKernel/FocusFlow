package com.focusflow.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.enableEdgeToEdge
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.focusflow.core.FocusMode
import com.focusflow.designsystem.FocusBackground
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.focusflow.designsystem.LocalCustomBackground
import com.focusflow.designsystem.LocalFocusFeedback
import java.io.File
import com.focusflow.tasks.FocusRoute

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var guardSetupOpen by mutableStateOf(false)

    // 极致专注防退出：系统手势（长按返回/上滑长按）由 SystemUI 处理、应用无法拦截，
    // 这里做的是"无效化"——失锁后重新 startLockTask。
    // 节流规则：从未授权过时（系统对话框可能在等待用户）低频 15s；授权记忆完成后（hasPinnedOnce）
    // 再调用 startLockTask 不会弹任何系统框，可用 250ms 快速重锁，把系统手势的逃离窗口压到最小。
    @Volatile private var extremeActive = false
    @Volatile private var hasPinnedOnce = false
    @Volatile var lastEscapeAt = 0L // 最近一次"失锁→重锁"时刻，驱动逃逸警告页倒计时
    private var lastLockRequest = 0L
    private fun relockIfExtreme() {
        if (!extremeActive) return
        if (getSystemService(android.app.ActivityManager::class.java).isInLockTaskMode) { hasPinnedOnce = true; return }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastLockRequest < if (hasPinnedOnce) 250L else 15000L) return
        lastLockRequest = now
        if (hasPinnedOnce) lastEscapeAt = System.currentTimeMillis()
        runCatching { startLockTask() }
    }
    override fun onStop() {
        super.onStop()
        relockIfExtreme()
    }

    override fun onStart() {
        super.onStart()
        // 桌面小组件随前台进入刷新；不做后台定时刷新（YAGNI，WorkManager 周期更新按需再加）
        lifecycleScope.launch { runCatching { TodayFocusGlanceWidget.updateAll(this@MainActivity) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as FocusFlowApplication
            val feedback = remember { app.feedback }
            // STRICT 生效时开启守护；EXTREME 追加系统屏幕固定（确认后失锁会在 1 秒内重新固定）
            val applyGuardMode: (FocusMode) -> Unit = { mode ->
                GuardPrefs.setGuardActive(this, mode == com.focusflow.core.FocusMode.STRICT)
                if (mode == com.focusflow.core.FocusMode.EXTREME) {
                    lastLockRequest = android.os.SystemClock.elapsedRealtime()
                    runCatching { startLockTask() }
                }
            }
            val endGuard: () -> Unit = {
                GuardPrefs.setGuardActive(this, false)
                runCatching { stopLockTask() }
            }
            // 相册选择专注页背景：拷贝到本机（文件名带时间戳，保证路径变化触发重组刷新），仅存路径（无需存储权限，系统照片选择器）
            val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                uri?.let { source ->
                    runCatching {
                        val previous = app.feedback.prefs.value.customBackgroundPath
                        val target = File(filesDir, "focus_bg_${System.currentTimeMillis()}")
                        contentResolver.openInputStream(source)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                        previous?.let { old -> File(old).takeIf { it.name.startsWith("focus_bg_") }?.delete() }
                        app.feedback.setPrefs(app.feedback.prefs.value.copy(customBackgroundPath = target.absolutePath, focusBackground = FocusBackground.CUSTOM))
                    }
                }
            }
            // 极致专注进行中 200ms 快速校验屏幕固定，失锁即刻重新固定；平时 1s 空转
            val activeRun by app.focus.runs.collectAsState(initial = null)
            LaunchedEffect(activeRun) {
                extremeActive = activeRun != null && activeRun!!.session.strictMode == FocusMode.EXTREME &&
                    activeRun!!.anchor.state == com.focusflow.core.TimerState.FOCUSING
                if (!extremeActive) lastEscapeAt = 0L
            }
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(if (extremeActive) 200 else 1000)
                    relockIfExtreme()
                }
            }
            // 逃逸警告页：极致锁定被系统手势解除并重锁后，显示 5 秒倒计时再回到专注页（替代"闪回"）
            var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(200); nowTick = System.currentTimeMillis() } }
            val prefs by feedback.prefs.collectAsState()
            val customBitmap = remember(prefs.customBackgroundPath) {
                prefs.customBackgroundPath?.let { path -> runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
            }
            // 内置背景：drawable 照片（1080x1920，picsum/Unsplash 免费图库）；解码失败回退渐变
            val builtinBackgrounds = remember {
                listOf(
                    com.focusflow.designsystem.FocusBackground.OBSIDIAN to R.drawable.bg_obsidian,
                    com.focusflow.designsystem.FocusBackground.MIDNIGHT to R.drawable.bg_midnight,
                    com.focusflow.designsystem.FocusBackground.ROSE to R.drawable.bg_rose,
                    com.focusflow.designsystem.FocusBackground.TEAL to R.drawable.bg_teal,
                    com.focusflow.designsystem.FocusBackground.WARM to R.drawable.bg_warm,
                ).mapNotNull { (bg, res) ->
                    runCatching { BitmapFactory.decodeResource(resources, res)?.asImageBitmap() }.getOrNull()?.let { bg to it }
                }.toMap()
            }
            val appVersion = remember { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() }
            if (guardSetupOpen) {
                GuardSetupScreen(this) { guardSetupOpen = false }
            } else {
                LaunchedEffect(Unit) { runCatching { app.sync.syncOnce() } }
                val whiteNoise = remember { AndroidWhiteNoise(this) }
            CompositionLocalProvider(LocalFocusFeedback provides feedback, com.focusflow.designsystem.LocalWhiteNoise provides whiteNoise,
                LocalCustomBackground provides customBitmap,
                com.focusflow.designsystem.LocalBuiltinBackgrounds provides builtinBackgrounds) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        FocusRoute(
                        app.tasks, app.focus, app.sync,
                        onEnableReminders = {
                            val preferences = getPreferences(MODE_PRIVATE)
                            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                                (!preferences.getBoolean("notification_requested", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                                preferences.edit().putBoolean("notification_requested", true).apply()
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            else startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
                        },
                        guardCapabilities = { app.guardCapabilities },
                        onOpenGuardSetup = { guardSetupOpen = true },
                        onGuardModeApplied = applyGuardMode,
                        onGuardFocusEnded = endGuard,
                        onPickCustomBackground = { pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        appVersion = appVersion,
                        onToggleLandscape = { landscape ->
                            // 横屏锁定只在专注页内可选；离开页面由 FocusScreen 复位
                            requestedOrientation = if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                        },
                        )
                        val escapeRemaining = lastEscapeAt + 5000 - nowTick
                        if (escapeRemaining > 0) Box(Modifier.fillMaxSize().background(Color(0xFF0E0E14))) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                Text("已退出专注锁定", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.headlineSmall)
                                Text("专注计时仍在继续", color = Color(0xFFB8B8D9), style = MaterialTheme.typography.bodyMedium)
                                Text("${(escapeRemaining + 999) / 1000}", color = Color.White, style = MaterialTheme.typography.displayLarge)
                                Text("秒后重新锁定并回到专注", color = Color(0xFFB8B8D9), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
