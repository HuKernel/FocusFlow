# 开发进度

## M0 验收记录（2026-09-11）
- 已检查初始仓库、读取产品文档与原型。
- 已创建交接入口，确认所有工具安装到 E 盘。
- M0 工程基线构建/测试验收通过；Android 真机运行待验证。
- 产品基线提交 `8e02769` 已推送 origin/main。
- 已建立 Android/Desktop 入口、core/database/designsystem、版本目录、Wrapper、CI 和文档。
- 已复用 E:\WordFlow\android-sdk；独立 JDK/Gradle/缓存位于 E:\FocusFlowTools。
- 工程检查点 `373b199` 已推送 origin/main。
- 最终 Wrapper 构建：`BUILD SUCCESSFUL in 28s`；Android assembleDebug、Desktop classes、Android lintDebug 全部通过。
- 9 项测试全部通过：core 3、Room/SQLite 2、设计 token/布局边界 2、真实 Compose UI smoke 2。
- Room 测试包含数据库关闭重开、UUID 重试幂等、25m+30m=55m、tombstone 过滤和 outbox 失败事务回滚。
- lint：0 error；保留依赖有新版本可升级的提示，不通过 baseline 隐藏问题。
- 已生成 Room v1 schema 和约 22MiB 的 Debug APK。
- `adb devices` 无设备，未进行 Android 安装/真机运行；没有声称完整 Timer、双设备同步或 Guard 已工作。
- GitHub Actions 已配置；本地验证通过不等同于已核验远程 CI 结果。

## M1 验收记录（2026-09-11）
- 新增 shared/feature/tasks，业务页面从 designsystem 移出。
- 已实现任务表单、完成/重开、软删除、搜索、日期/项目/标签/优先级筛选与详情。
- 新增项目/标签的创建、重命名、删除；删除组织结构保留任务并清理关联。
- 数据库 v2 增加 projects/tags/task_tags/local_identity；使用 Room AutoMigration 1→2，保留 v1 schema。
- TaskRepository 所有业务写入与 SyncEvent 在一个 IMMEDIATE 事务中完成；编辑检查 revision 防止旧快照覆盖新值。
- Android 数据库提升到 Application 生命周期，避免旋转时关闭 ViewModel 正在使用的数据库。
- Today 显示真实今日任务与专注统计，连续专注按完成 Session 的本地日期计算。
- 开发检查点 `a78e057` 已推送 origin/main。
- 版本 0.2.0 / versionCode 2；Android assembleDebug、Desktop classes、lintDebug 通过。最终日志：`E:/FocusFlowTools/m1-ui.log`（BUILD SUCCESSFUL in 49s）。
- 16 项测试全部通过：core 6、database 5、designsystem 2、feature/tasks UI 3。
- 迁移测试保留旧 Task 和 Session；UI 测试实际创建/编辑/完成/删除任务、管理项目和标签、拒绝无效日期。
- lint 0 errors / 14 warnings：13 条依赖可升级提示，1 条已有 Android 旧版备份配置提示；没有使用 baseline 隐藏。
- APK 通过 apksigner 校验；交付路径 `artifacts/FocusFlow-0.2.0-debug.apk`。Android 真机升级/旋转尚未自动验证；本轮没有声称完整计时、同步或 Guard 可用。

## M2 验收记录（2026-09-11）
- 0.3.0 / versionCode 3：任务列表/详情可开始 NORMAL 正计时或倒计时，支持暂停/恢复、完成/取消、5 分钟休息与返回本轮入口。
- TimerEngine 使用时间锚点；Android 同次开机 elapsedRealtime、跨开机 epoch 估算并提示；后台不每秒持久化或启动计时 FGS。
- Room v3 active_focus + FocusRepository 事务结算；完成 Session/outbox/快照同时提交。取消不入账、暂停不累计、休息不修改 Session；失效回调不会操作新会话。
- 进行中任务不可完成/删除；勾选任务与累计专注时长仍独立。
- Android 可选通知、exact 能力检查/inexact fallback、开机/升级恢复。权限拒绝不阻止计时。
- 检查点 `5ce38f2` 已推送 origin/main。
- 全量验证 `E:/FocusFlowTools/m2-final.log`：BUILD SUCCESSFUL in 1m 3s，Android assembleDebug/lintDebug、Desktop classes、26 项测试全部通过（core 13、database 7、designsystem 2、feature/tasks UI 4）。
- 补充 v1/v2→v3 迁移后启动专注的数据库检查，以及通知重复拒绝设置入口；`E:/FocusFlowTools/m2-release.log` 构建/lint/数据库复验通过，28s。
- lint 0 errors / 15 warnings：原有 14 条加 exact alarm 权限提示；已有 runtime 能力检查和异常回退，详见 DECISIONS。
- Windows classes.jar 占用通过停止 daemon 后重建解决。Android 真机通知、重启、旋转与省电恢复仍待设备实测，桌面测试不替代真机验收。
- 交付 APK：`artifacts/FocusFlow-0.3.0-debug.apk`。

## M3 验收记录（2026-09-11）
- 0.4.0 / versionCode 4：Motion/Sound/Haptic 框架落地。
- designsystem：FocusFeedback（prefs StateFlow 按开关过滤 sound/haptic 调用）+ LocalFocusFeedback；FocusMotion 增加统一 easing；TimerRing 进度平滑动画；StatisticCard 数字滚动。
- tasks：任务插入/删除/重排 animateItem、完成标题颜色过渡、勾选完成音效+触感；「我的」页新增音效/震动/减弱动效开关。
- focus：准备↔运行转场（reduced 时纯 fade）、暂停/继续按钮 morph、完成庆祝 spring scale；开始/暂停/继续/休息按钮触发反馈，专注完成由状态驱动播一次（倒计时自然结束也覆盖），错误播 ERROR。
- Android：SoundPool 播放运行时生成的测试音（不下载素材）；Vibrator createPredefined + API 26–28 回退；prefs 持久化 SharedPreferences，系统"移除动画"作为减弱动效默认值；manifest 新增 VIBRATE 权限。Desktop 反馈为 no-op，未声称桌面有声音。
- 全量验证 `E:/FocusFlowTools/m3-full.log`：BUILD SUCCESSFUL in 52s，Android assembleDebug/lintDebug、Desktop classes、28 项测试全部通过（core 13、database 7、designsystem 4、feature/tasks UI 4）。
- 新增测试：feedbackRespectPrefsSwitches（开关过滤 + 回调持久化）、offVariantIsSilent；既有 Reduced Motion duration 归零测试保留。
- lint 0 errors / 17 warnings：原 15 条（版本升级/备份/exact alarm）+ 2 条 UseKtx（SharedPreferences.edit）；不为消警告引入 core-ktx，见 DECISIONS。
- 交付 APK：`artifacts/FocusFlow-0.4.0-debug.apk`，apksigner 验证通过（Debug 证书，可覆盖旧版），SHA256 395715bc2bef7cebd3c43d97e99eeea374ecbba02e55fd63e69038a6f54e62e2。
- 真机体感（音质、震动强度、动画流畅度、Reduced Motion 开关实效）与省电专项仍未验证；动画性能目标 60fps 待 M4 统计页宏观看板时用 Macrobenchmark 类工具量化，本轮以编译/测试/UI 冒烟为验收。

## M4 验收记录（2026-09-11）
- 0.5.0 / versionCode 5：Statistics + Heatmap 落地。
- core 新增 focusStats/rangeStartDate/heatmapWeeks：Today/Week/Month 聚合专注时长、完成次数、任务完成数、平均专注（毫秒整除）、中断次数；只统计 COMPLETED Session，日期按设备本地时区，周从周一开始。
- designsystem 新增 Heatmap 组件（Canvas 纯绘制，12 周网格，4 档颜色 + 无数据浅底）。
- STATS 页接真实数据：范围 FilterChip（今天/本周/本月）、6 张统计卡（复用带数字动效的 StatisticCard）、连续专注（复用 focusStreak）、12 周热力图；无完成记录时显示空态。
- 全量验证 `E:/FocusFlowTools/m4-final.log`：BUILD SUCCESSFUL in 31s，Android assembleDebug/lintDebug、Desktop classes、32 项测试全部通过（core 16、database 7、designsystem 4、feature/tasks UI 5）。
- 新增测试：FocusStatsTest（Today/Week/Month 聚合与 CANCELLED 排除、完成任务范围计数、热力图周一开头本周在末列）；statsPageAggregatesCompletedSessionsAndSwitchesRange（真实 Room 写入 1 次 1 分钟专注后 STATS 卡片/范围切换/热力图渲染）。fake clock 基准改为系统当前时间以对齐统计日期口径。
- lint 0 errors / 17 warnings，与 M3 相同，无新增。
- 交付 APK：`artifacts/FocusFlow-0.5.0-debug.apk`，apksigner 验证通过，SHA256 7276D6515A02F192047ABED3937ADBC45DC8877CD726CC8428601057B1E73890。
- 真机渲染（热力图色阶可读性、统计切换流畅度）未实测；60fps 量化仍待性能阶段。

## 下一阶段
M5：Account + Ktor 后端 + 离线同步（SyncEvent/outbox 已就绪）。M6 完整自适应验收，M7 Owner/Observer，M8 Focus Guard。
