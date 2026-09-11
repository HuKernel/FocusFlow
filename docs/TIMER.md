# Timer

M2 实现 NORMAL 倒计时（1–1440 分钟）与正计时，暂停/继续/取消、完成总结及可跳过的 5 分钟休息。准备页面不创建会话；点击开始后持久化 FOCUSING。

Android 用 elapsedRealtime 与 BOOT_COUNT 判定同次开机；无法读取 BOOT_COUNT 时退化到进程身份。Desktop nanoTime 仅同进程可比较，重开使用 epoch 估算。同次开机改系统时间不影响 Android 专注时长；跨重启按非负 epoch 差估算并显示提示，无法保证跨重启且改系统时间时精确。

Room v3 active_focus 保存单个 FocusRun（Session、TimerAnchor、bootId、任务名、休息长度）。TimerEngine 纯函数计算 elapsed/remaining，UI 每秒读取锚点，不使用 remainingSeconds--。UI tick 仅在 STARTED 生命周期运行，不每秒写库、不运行计时 FGS。恢复、暂停、继续、完成等状态变化立即写库。

FocusRepository 使用 Mutex 串行事务与平台闹钟更新，SQLite IMMEDIATE 事务同时保存终态 Session、CREATE outbox 和已结算快照；重复恢复不重复入账。动作携带 sessionId，旧回调不会取消新一轮。进行中的任务不可勾选完成或删除，Repository 统一检查。

倒计时达到期限才完成，延迟恢复以截止时间归属 endedAt；正计时可主动完成。只有 COMPLETED.actualDuration 计入任务进度；CANCELLED 保存记录但不累计，休息不创建专注 Session。完成专注不会自动勾选整个任务。暂停累计 pausedDuration，完成的 Session 不随休息修改。

Android AlarmManager 在可用时使用 exact，否则 setAndAllowWhileIdle；没有申请精确闹钟特殊权限。结束通知需用户主动授权 POST_NOTIFICATIONS，拒绝不影响计时。开机/升级广播与前台恢复都会重新计算快照。闹钟/通知属于尽力提醒，厂商限制、强制停止、事务提交后进程终止均可能使通知延后或缺失；本地恢复是结算兜底。Desktop 暂无系统通知。

验证覆盖引擎 start/pause/resume/cancel/complete、同次开机进程恢复、系统改时、重启估算、暂停重启、休息不变性；真实 SQLite 关闭重开、结算幂等、事务失败回滚、失效回调和提醒权限异常；Compose UI 从任务开始正计时并验证暂停不入账。
