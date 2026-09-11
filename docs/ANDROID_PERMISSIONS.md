# Android 权限

M2 manifest 声明 POST_NOTIFICATIONS 和 RECEIVE_BOOT_COMPLETED；不声明无障碍、Usage Access、精确闹钟、Overlay 或网络权限。
targetSdk/compileSdk=36，minSdk=26（Room 2.8 系列支持基线）。
仅在专注设置点击“开启结束提醒”后申请通知权限；首次启动不弹权限。Android 13 以下打开应用通知设置。每次前台刷新重新检查通知与 channel 状态。
可用时采用 exact，否则 inexact alarm；权限拒绝或调度异常不回滚已保存的计时状态，数据库测试覆盖平台 adapter 抛 SecurityException 的降级。
M8 Strict 首次启用向导需显著披露和明确同意。权限不是 Task/Normal Focus 的先决条件。
真机通知拒绝/撤销、系统设置回流、厂商省电和开机恢复仍需设备实测，不以桌面测试替代。

依据：[Android 闹钟](https://developer.android.com/develop/background-work/services/alarms)、[通知权限](https://developer.android.com/develop/ui/views/notifications/notification-permission)。
