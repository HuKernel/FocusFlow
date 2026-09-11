# Focus Guard

未实现，属于 M8；当前 App 不请求敏感权限。
NORMAL 无 Guard 依赖；SOFT 记录中断；STRICT 在用户主动开启、披露和同意后配置；EXTREME 使用普通 App 的屏幕固定能力。
必须提供 Emergency Exit。权限撤销时 STRICT → SOFT → NORMAL，不影响 Task 和普通专注。
发布前重新核验 AccessibilityService 政策，不将本产品标记为 accessibility tool，不阻止卸载或禁用服务。
