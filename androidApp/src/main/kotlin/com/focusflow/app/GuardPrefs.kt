package com.focusflow.app

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.focusflow.core.AllowedApp
import com.focusflow.core.FocusMode
import com.focusflow.core.GuardCapabilities
import com.focusflow.core.StrictModeConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Focus Guard 状态只存本机 SharedPreferences：模式选择、同意时间、白名单均不上传（隐私条款）。 */
object GuardPrefs {
    private const val FILE = "focus_guard"
    private const val KEY_CONFIG = "strict_config"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_ACTIVE = "guard_active"
    private const val KEY_EXTREME_ACTIVE = "extreme_active"
    private const val KEY_LAST_ESCAPE = "last_escape_at"
    private const val KEY_STRICT_ESCAPE = "strict_escape_at"
    private val json = Json { ignoreUnknownKeys = true }

    fun config(context: Context): StrictModeConfig =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_CONFIG, null)
            ?.let { runCatching { json.decodeFromString(StrictModeConfig.serializer(), it) }.getOrNull() } ?: StrictModeConfig()

    fun saveConfig(context: Context, config: StrictModeConfig) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_CONFIG, json.encodeToString(StrictModeConfig.serializer(), config)).apply()
    }

    fun whitelist(context: Context): List<AllowedApp> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_WHITELIST, null)
            ?.let { runCatching { json.decodeFromString(ListSerializer, it) }.getOrNull() } ?: defaultWhitelist()

    fun saveWhitelist(context: Context, apps: List<AllowedApp>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_WHITELIST, json.encodeToString(ListSerializer, apps)).apply()
    }

    fun setGuardActive(context: Context, active: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, active).apply()
    }

    fun isGuardActive(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    /** 极致拉回运行中（由 MainActivity 在极致专注起止时设置，FocusGuardService 读取）。 */
    fun setExtremeActive(context: Context, active: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_EXTREME_ACTIVE, active).apply()
        if (!active) context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY_LAST_ESCAPE).apply()
    }

    fun isExtremeActive(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_EXTREME_ACTIVE, false)

    fun setLastEscape(context: Context, at: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_ESCAPE, at).apply()
    }

    fun setStrictEscape(context: Context, at: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(KEY_STRICT_ESCAPE, at).apply()
    }

    fun strictEscape(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_STRICT_ESCAPE, 0L)

    fun lastEscape(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_LAST_ESCAPE, 0L)

    fun capabilities(context: Context): GuardCapabilities = GuardCapabilities(
        accessibilityGranted = accessibilityEnabled(context),
        usageAccessGranted = usageAccessGranted(context),
        screenPinningAvailable = true, // startLockTask 普通应用可用，需用户在系统弹窗确认；无 Device Owner 级强制
    )

    fun accessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ComponentName(context, FocusGuardService::class.java) }
    }

    fun usageAccessGranted(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private val ListSerializer = kotlinx.serialization.builtins.ListSerializer(AllowedApp.serializer())

    private fun defaultWhitelist(): List<AllowedApp> = listOf(
        AllowedApp("com.android.dialer", "电话"),
        AllowedApp("com.android.contacts", "联系人"),
    )
}
