# Android 权限

M0 manifest 不声明通知、无障碍、Usage Access、精确闹钟、Overlay 或网络权限。
targetSdk/compileSdk=36，minSdk=26（Room 2.8 系列支持基线）。
M2 结束通知与 exact/inexact alarm 按实际必要性加入；M8 Strict 首次启用向导需显著披露和明确同意。
权限不是 Task/Normal Focus 的先决条件。拒绝、撤销、系统设置回流都必须验证降级。
