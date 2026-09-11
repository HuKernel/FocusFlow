# Focus Guard

## 状态（M8，0.10.0）
四档模式、降级链、Setup 向导、白名单与 Emergency Exit 已实现；真机权限/守护行为待实测。

## 四档模式（FocusScreen 开始专注时选择）
- NORMAL：普通计时，无任何守护依赖。
- SOFT：允许切走；中断记录依赖 Usage Access，缺权限时按 NORMAL 运行。
- STRICT：AccessibilityService（专注守护）检测非白名单应用并弹「回到专注」通知；缺权限时降 SOFT/NORMAL。
- EXTREME：Activity.startLockTask 屏幕固定（普通消费级路径，用户在系统弹窗确认，长按返回可退出）；不可用时降 STRICT。
- 降级链 EXTREME→STRICT→SOFT→NORMAL 由 core `effectiveMode` 纯函数决定，UI 显示实际生效模式与缺失项。

## 专注守护服务（FocusGuardService）
- 只订阅 TYPE_WINDOW_STATE_CHANGED，只读包名；不申请 canRetrieveWindowContent，不读页面内容。
- 白名单 = 本应用 + 系统 Launcher + 用户白名单（GuardPrefs，本机 SharedPreferences，不上传）。
- 仅 guard_active 标志为真（STRICT/EXTREME 专注运行中）时提醒；系统 UI/无启动器入口的窗口变化不打扰。
- 合规：isAccessibilityTool=false；服务描述显著披露读取范围与数据去向；用户可随时在系统设置停用，不阻止卸载/禁用。

## Setup Wizard（GuardSetupScreen，从专注页「去开启专注防护」进入）
1. 显著披露 + 明确同意（StrictModeConfig.consentAt，未同意不引导授权）。
2. 权限状态与跳转：通知、Usage Access、无障碍（选择「专注守护」）。
3. 白名单管理（添加/移除包名）。
4. 自检：guardStrength 显示请求模式、实际生效模式与缺失步骤。
5. OEM 后台可靠性文字指引（不代改设置）。

## Emergency Exit
- EXTREME：系统屏幕固定原生退出（长按返回/概览），App 在结束本轮时主动 stopLockTask。
- STRICT：守护通知上的「退出守护」action（服务 stopSelf + 清 guard_active）。
- 任何模式：系统设置停用无障碍服务即彻底关闭守护。

## 已验证 / 未验证
- core FocusGuardTest：降级链、白名单判定、强度自检。
- UI 冒烟：模式 chips、STRICT 文案、无权限降级提示与向导入口。
- 未验证（无真机）：服务实际回调、厂商 ROM 行为、锁屏/固定交互。
