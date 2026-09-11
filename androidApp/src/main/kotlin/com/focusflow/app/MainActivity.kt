package com.focusflow.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.focusflow.core.FocusMode
import com.focusflow.designsystem.LocalFocusFeedback
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
            // STRICT 生效时开启守护；EXTREME 追加系统屏幕固定（用户在系统弹窗确认，长按返回可退出）
            val applyGuardMode: (FocusMode) -> Unit = { mode ->
                GuardPrefs.setGuardActive(this, mode == com.focusflow.core.FocusMode.STRICT)
                if (mode == com.focusflow.core.FocusMode.EXTREME) runCatching { startLockTask() }
            }
            val endGuard: () -> Unit = {
                GuardPrefs.setGuardActive(this, false)
                runCatching { stopLockTask() }
            }
            if (guardSetupOpen) {
                GuardSetupScreen(this) { guardSetupOpen = false }
            } else {
                LaunchedEffect(Unit) { runCatching { app.sync.syncOnce() } }
                CompositionLocalProvider(LocalFocusFeedback provides feedback) {
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
                    )
                }
            }
        }
    }
}
