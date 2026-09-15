package com.focusflow.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusflow.core.AllowedApp
import com.focusflow.designsystem.FocusTheme
import com.focusflow.core.guardStrength

/**
 * Focus Guard Setup Wizard（单页向导）：显著披露 → 权限引导 → 白名单 → 自检。
 * 用户未同意前不申请任何敏感权限；普通任务与 NORMAL 专注完全不依赖这些能力。
 */
@Composable
fun GuardSetupScreen(context: android.content.Context, onBack: () -> Unit) {
    val config = remember { GuardPrefs.config(context) }
    var consented by remember { mutableStateOf(config.consentAt != null) }
    var whitelist by remember { mutableStateOf(GuardPrefs.whitelist(context)) }
    var pickerOpen by remember { mutableStateOf(false) }
    // 可启动应用列表（launcher intent 查询，无需 QUERY_ALL_PACKAGES）
    val installedApps = remember {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { info -> info.activityInfo?.let { (info.loadLabel(context.packageManager).toString()) to it.packageName } }
            .filter { it.second != context.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first }
    }
    var capabilities by remember { mutableStateOf(GuardPrefs.capabilities(context)) }
    var requestedMode by remember { mutableStateOf(config.mode) }
    FocusTheme {
        Scaffold { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row {
                        TextButton(onClick = onBack) { Text("返回") }
                        Spacer(Modifier.weight(1f))
                    }
                    Text("专注防护设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                if (!consented) item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("启用严格专注前，请了解", style = MaterialTheme.typography.titleMedium)
                            Text("专注守护会在严格模式运行时读取当前窗口的应用包名，用于检测您是否离开白名单应用，并提醒您回到专注。它不会读取页面内容，不会保存浏览历史，也不会上传任何数据到服务器。")
                            Text("您可以随时在系统设置中停用该服务，或使用守护通知上的「退出守护」按钮。")
                            Button(onClick = {
                                GuardPrefs.saveConfig(context, config.copy(mode = requestedMode, consentAt = System.currentTimeMillis()))
                                consented = true
                            }, modifier = Modifier.testTag("guard_consent")) { Text("我已了解并同意") }
                        }
                    }
                } else {
                    item {
                        capabilities = GuardPrefs.capabilities(context)
                        val strength = guardStrength(requestedMode, capabilities)
                        Text("防护强度自检", style = MaterialTheme.typography.titleMedium)
                        Text("请求模式：${requestedMode.name} · 实际生效：${strength.effective.name}")
                        if (strength.missingSteps.isEmpty()) Text("所有权限就绪。", color = MaterialTheme.colorScheme.secondary)
                        else Text("缺少：${strength.missingSteps.joinToString("、")}", color = MaterialTheme.colorScheme.error)
                    }
                    item {
                        Text("权限与系统设置", style = MaterialTheme.typography.titleMedium)
                        PermissionRow("通知（结束提醒）", capabilities.usageAccessGranted || true) { }
                        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("打开通知设置") }
                        PermissionRow("使用情况访问（软性模式记录中断）", capabilities.usageAccessGranted) { }
                        TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }) { Text("打开使用情况访问") }
                        PermissionRow("无障碍服务（严格模式守护）", capabilities.accessibilityGranted) { }
                        TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }) { Text("打开无障碍设置（选择「专注守护」）") }
                        Text("极致模式通过本服务的无障碍事件检测切出并立即拉回（无系统弹窗、无固定提示条）；离开本应用会显示 5 秒警告倒计时后回到专注。", style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        Text("白名单应用", style = MaterialTheme.typography.titleMedium)
                        Text("严格模式运行时，白名单内应用与启动器、本应用不会触发提醒。勾选列表来自本机可启动应用，仅保存在本机、不上传。", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { pickerOpen = true }, Modifier.testTag("guard_whitelist_picker")) { Text("选择白名单应用（已选 ${whitelist.size}）") }
                    }
                    if (pickerOpen) item {
                        AlertDialog(onDismissRequest = { pickerOpen = false }, title = { Text("白名单应用") },
                            text = {
                                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                                    items(installedApps, key = { it.second }) { (label, packageName) ->
                                        val checked = whitelist.any { it.packageName == packageName }
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Checkbox(checked, { onToggle ->
                                                whitelist = if (onToggle) whitelist + AllowedApp(packageName, label)
                                                else whitelist.filterNot { it.packageName == packageName }
                                                GuardPrefs.saveWhitelist(context, whitelist)
                                            })
                                            Column { Text(label, fontWeight = FontWeight.SemiBold); Text(packageName, style = MaterialTheme.typography.bodySmall) }
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { pickerOpen = false }) { Text("完成") } })
                    }
                    item {
                        Text("厂商后台可靠性", style = MaterialTheme.typography.titleMedium)
                        Text("若提醒被延后，请在系统设置中允许 FocusFlow 后台运行：小米/华为等机型可在电池与性能设置中解除限制、锁定最近任务。具体入口因机型而异，应用不会替您修改这些设置。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Text(if (granted) "已开启" else "未开启", color = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
    }
}
