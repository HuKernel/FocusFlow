package com.focusflow.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                if (mode == com.focusflow.core.FocusMode.EXTREME) runCatching { startLockTask() }
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
            // 极致专注进行中每秒校验屏幕固定：长按返回等系统退出后 1 秒内重新固定（消费级应用的极限，见 FOCUS_GUARD 文档）
            val activeRun by app.focus.runs.collectAsState(initial = null)
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    if (activeRun != null && activeRun!!.session.strictMode == FocusMode.EXTREME &&
                        activeRun!!.anchor.state == com.focusflow.core.TimerState.FOCUSING &&
                        !getSystemService(android.app.ActivityManager::class.java).isInLockTaskMode) {
                        runCatching { startLockTask() }
                    }
                }
            }
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
                    )
                }
            }
        }
    }
}
