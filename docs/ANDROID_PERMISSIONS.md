# Android 权限

## manifest 声明
- POST_NOTIFICATIONS：结束提醒与守护提醒，仅用户点击「开启结束提醒」后运行时申请。
- RECEIVE_BOOT_COMPLETED：开机恢复闹钟。
- VIBRATE：触感反馈。
- BIND_ACCESSIBILITY_SERVICE（service 级）：专注守护，用户在系统设置手动授予；不声明 Usage Access/精确闹钟特殊权限/Overlay。
- INTERNET 不需要显式声明（targetSdk 自动）？——targetSdk 36 下网络权限仍需声明：androidApp 使用 Ktor CIO 同步，需要 INTERNET。

## 申请时机与降级
- 首次启动不弹任何权限；Task/Normal Focus 零依赖。
- 通知：主动点击后申请；拒绝可继续计时（无提醒）。
- Usage Access / 无障碍：仅在 Guard 向导中引导跳系统设置；拒绝时 STRICT/EXTREME 降级运行，不阻塞。
- 精确闹钟：不声明 SCHEDULE_EXACT_ALARM；运行时 canScheduleExactAlarms 允许则 exact，否则 inexact，SecurityException 再回退。

## 数据边界
- 白名单与 Guard 配置仅存本机；不上传服务器，不进入 sync outbox。
- analytics 只允许非内容事件；不收集任务标题/描述与安装列表。

依据：[Alarms](https://developer.android.com/develop/background-work/services/alarms)、[Notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)、[Accessibility policy](https://support.google.com/play/android-developer/answer/13979155)。
