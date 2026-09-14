package com.focusflow.core

/** 平台能力快照：由 androidApp 检测，commonMain 只做纯决策。 */
data class GuardCapabilities(
    val accessibilityGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val screenPinningAvailable: Boolean = false,
)

/**
 * 权限不足时降级：EXTREME 只依赖屏幕固定（防切换由固定承担，不要求无障碍）；
 * STRICT 依赖无障碍服务；SOFT 依赖使用情况访问（记录中断）。
 */
fun effectiveMode(requested: FocusMode, capabilities: GuardCapabilities): FocusMode = when (requested) {
    FocusMode.EXTREME -> if (capabilities.screenPinningAvailable) FocusMode.EXTREME else FocusMode.STRICT
    FocusMode.STRICT -> if (capabilities.accessibilityGranted) FocusMode.STRICT else if (capabilities.usageAccessGranted) FocusMode.SOFT else FocusMode.NORMAL
    FocusMode.SOFT -> if (capabilities.usageAccessGranted) FocusMode.SOFT else FocusMode.NORMAL
    FocusMode.NORMAL -> FocusMode.NORMAL
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
        if (requested == FocusMode.EXTREME && !capabilities.screenPinningAvailable) add("屏幕固定（极致模式）")
        if (requested == FocusMode.STRICT && !capabilities.accessibilityGranted) add("无障碍服务（Strict 守护）")
        if (requested == FocusMode.SOFT && !capabilities.usageAccessGranted) add("使用情况访问（记录中断）")
    }
    return GuardStrength(requested, effective, missing)
}

fun focusModeLabel(mode: FocusMode): String = when (mode) {
    FocusMode.NORMAL -> "普通"; FocusMode.SOFT -> "软性"; FocusMode.STRICT -> "严格"; FocusMode.EXTREME -> "极致"
}