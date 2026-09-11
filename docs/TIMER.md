# Timer

M0 已定义 TimerState、TimerAnchor、FocusClock 与 TimerController；没有可操作计时器。
Android 时间源使用 elapsedRealtime，Desktop 使用 nanoTime；epoch 用于恢复信息，UI 刷新不能驱动真实计时。

M2 必须实现并测试 start/pause/resume/cancel/complete/restore：状态转换立即落 Room，锚点包含 epoch、monotonic、累计 elapsed、pausedDuration。
同一次开机的进程恢复尽量沿用 monotonic；重启需 boot identity 与 epoch fallback。跨重启且用户改系统时间无法仅靠 epoch 完美恢复，需明确策略并测试，不能承诺不存在误差。
倒计时完成的不可变 Session 与锚点清理必须处于同一事务。
