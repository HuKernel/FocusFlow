package com.focusflow.core

/** 平台能力快照：由 androidApp 检测，commonMain 只做纯决策。 */
data class GuardCapabilities(
    val accessibilityGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val screenPinningAvailable: Boolean = false,
)

/**
 * 权限不足时按 EXTREME→STRICT→SOFT→NORMAL 逐级降级：
 * EXTREME 需要屏幕固定；STRICT 需要无障碍服务；SOFT 需要使用情况访问（用于记录中断）。
 */
fun effectiveMode(requested: FocusMode, capabilities: GuardCapabilities): FocusMode {
    var mode = requested
    if (mode == FocusMode.EXTREME && !capabilities.screenPinningAvailable) mode = FocusMode.STRICT
    if (mode == FocusMode.STRICT && !capabilities.accessibilityGranted) mode = FocusMode.SOFT
    if (mode == FocusMode.SOFT && !capabilities.usageAccessGranted) mode = FocusMode.NORMAL
    return mode
}

/** 白名单判定：自身包、启动器与用户添加的应用不打扰。 */
fun isGuardAllowed(packageName: String?, ownPackage: String, launcherPackages: Set<String>, whitelist: List<AllowedApp>): Boolean {
    if (packageName.isNullOrBlank()) return true
    if (packageName == ownPackage || packageName in launcherPackages) return true
    return whitelist.any { it.packageName == packageName }
}

/** Setup Wizard 自检：报告当前保护强度，缺什么提示什么。 */
data class GuardStrength(val requested: FocusMode, val effective: FocusMode, val missingSteps: List<String>)

fun guardStrength(requested: FocusMode, capabilities: GuardCapabilities): GuardStrength {
    val effective = effectiveMode(requested, capabilities)
    val missing = buildList {
        if (requested >= FocusMode.SOFT && !capabilities.usageAccessGranted) add("使用情况访问（记录中断）")
        if (requested >= FocusMode.STRICT && !capabilities.accessibilityGranted) add("无障碍服务（Strict 守护）")
        if (requested == FocusMode.EXTREME && !capabilities.screenPinningAvailable) add("屏幕固定（极致模式）")
    }
    return GuardStrength(requested, effective, missing)
}
