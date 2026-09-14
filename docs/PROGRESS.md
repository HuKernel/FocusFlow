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

## M5 第一阶段验收记录（2026-09-11）
- 0.6.0 / versionCode 6：同步服务端与客户端引擎落地；删除 tasks 导航中不可达的 Destination.FOCUS 占位分支。
- 新增 server 模块（Ktor 3.2.2 CIO + JDBC）：auth register/login（PBKDF2 + HMAC token）、sync push/pull、entities 投影 + applied_events 幂等、回声排除、per-user 单调 revision；生产 PostgreSQL、开发/测试 H2 MODE=PostgreSQL（UPDATE-then-INSERT 双兼容）。
- 新增 shared/sync 模块：SyncTransport 接口 + SyncEngine（push outbox → SYNCED 标记 → pull → 事务 apply → cursor 推进）；Task/Project/Tag 按 revision 合并、FocusSession UUID IGNORE 合并、TaskTag tombstone；远端数据不回流 outbox。
- Room schema v4（sync_state 表）+ AutoMigration 3→4；迁移测试扩展为 v1/v2/v3 → v4 全覆盖。
- 全量验证 `E:/FocusFlowTools/m5-final2.log`：BUILD SUCCESSFUL in 2m 30s（含一次 daemon 重启解决 classes.jar 占用），36 项测试全部通过：core 16、database 7、designsystem 4、tasks UI 5、sync 2、server 2。
- 关键测试：双设备离线（手机 25m 正计时 + 平板 30m）经真实 Store 合并后双方 completedMillis=55m、sessions 各 2 条；tombstone 跨设备删除；重复同步 pushed=0/pulled=0；HTTP 幂等重推 accepted=0；cursor 增量与回声排除。
- lint 0 errors / 26 warnings：原 17 条 + 新增 9 条均为新依赖（ktor/h2/postgresql/logback）版本可升级提示。
- 交付 APK：`artifacts/FocusFlow-0.6.0-debug.apk`，apksigner 验证通过，SHA256 8D057E008842FA5B9D24A5A35520B78AC6BABD84964D2FD32AA382CCE4BC7823。
- App 内行为与 0.5.0 相同：Android 登录 UI、Ktor client HTTP transport、自动同步调度未接线，不声称 App 已能云同步。

## M5 第二阶段验收记录（2026-09-11）
- 0.7.0 / versionCode 7：云同步在 App 内可用（Android 与 Desktop 同一代码路径）。
- shared/network：FocusSyncApi 接口 + HttpFocusSyncApi（Ktor client CIO + JSON negotiation）；desktopTest 用真实 CIO server（随机端口）端到端验证注册、幂等 push、echo 排除 pull。
- SyncTransport 接口移入 core 消解 network/sync 循环依赖；sync 新增 SyncCoordinator（登录/注册/退出/手动同步）。
- Room v5：sync_state 增加 serverUrl/token/username（AutoMigration 4→5），迁移测试覆盖 v1/v2/v3/v4 → v5。
- 「我的」页：服务器地址/用户名/密码表单（密码掩码）、注册/登录、已登录面板（用户名、立即同步显示推送/接收计数、退出登录）；HTTP 失败显示中文原因。Android 启动静默同步一次。
- 全量验证 `E:/FocusFlowTools/m5b-final2.log`：BUILD SUCCESSFUL in 2m 22s（daemon 重启一次解决 classes.jar 占用），38 项测试通过：core 16、database 7、designsystem 4、tasks UI 6、sync 2、network 1、server 2。
- 新增 UI 测试：登录表单注册流程写入 token 并切换到已登录面板（fake FocusSyncApi + 真 Room）。
- lint 0 errors / 29 warnings：新增 3 条为 ktor-client 依赖版本提示。
- 交付 APK：`artifacts/FocusFlow-0.7.0-debug.apk`，apksigner 验证通过，SHA256 877519B8C192A9ABFB37F8916298B18D4DFF0B48DA44AEE6F5A6F9E1E0F0533B。
- 未验证：真机双设备通过真实 HTTP 同步（需一台机器运行 ./gradlew :server:run 并配置 DATABASE_URL）；后台周期同步未实现。

## M6 验收记录（2026-09-11）
- 0.8.0 / versionCode 8：完整自适应布局验收。
- 宽窗口（Medium/Expanded）List-Detail：默认选中首个任务，右侧详情栏不再空置；筛选变化后自动重选有效任务，用户主动选择优先。
- Focus 宽屏（≥840dp）侧栏升级为真实进度面板：任务名、计划/正计时、进度条、暂停累计、设备状态说明；准备态显示引导文案。
- 新增自适应冒烟测试：Medium(700dp) Navigation Rail + 220dp 面板且无底栏；Expanded(1100dp) 默认选中任务、详情栏显示"累计专注"；矮横屏(1100x390) Rail/面板/标题正常渲染。旋转/分屏由同一宽度逻辑驱动。
- 全量验证 `E:/FocusFlowTools/m6-final.log`：BUILD SUCCESSFUL in 56s，41 项测试通过：core 16、database 7、designsystem 4、tasks UI 9、sync 2、network 1、server 2。lint 0 errors / 29 warnings（无新增）。
- 交付 APK：`artifacts/FocusFlow-0.8.0-debug.apk`，apksigner 验证通过，SHA256 12FCE02989E1578C273C7F12D2290F3B081CB6E5059D16D49199BB9787EC3D86。
- 真实折叠屏/分屏/键鼠 hover 属真机与 M9 范畴，未在本轮声称验证。

## M7 验收记录（2026-09-11）
- 0.9.0 / versionCode 9：Live Presence + Owner/Observer + 原子 handoff。
- core：PresenceKind/PresenceSnapshot/PresenceMessage + estimateRemoteFocus（共享锚点估算，测试覆盖暂停冻结/非负剩余/owner 判定）。
- server：PresenceHub（内存、每用户单活跃会话）+ WS /api/v1/ws（token query 认证）+ presence 查询 + handoff 原子转移；主引擎换 Netty（CIO 在本环境 WS 升级 400，有最小复现佐证）。
- network：HttpFocusPresence（Ktor client WS + HTTP handoff）；FocusPresence 接口可注入测试。
- focus：FocusViewModel 上报生命周期（STARTED/PAUSED/RESUMED/COMPLETED/CANCELLED）；Observer 面板（任务/状态/剩余/发起设备 +「在这台设备继续」）；adopt 恢复同一 sessionId、releaseLocal 让位不结算；FocusRepository 新增 adopt/currentDeviceId（竞态安全）。
- 全量验证 `E:/FocusFlowTools/m7-final2.log`：BUILD SUCCESSFUL in 2m 27s（daemon 重启一次），48 项测试通过：core 20、database 7、designsystem 4、tasks UI 9、focus 2、sync 2、network 1、server 3。
- lint 0 errors / 33 warnings（新增 4 条 ktor ws/netty 版本提示）。
- 交付 APK：`artifacts/FocusFlow-0.9.0-debug.apk`，apksigner 验证通过，SHA256 81BB604DEA752DBBF4FFCCE29C276C14802BED2782CAF02BD317B7FE19C36070。
- 未验证：真机双设备 WS 稳定性与后台重连；登录后 presence 生效需重启 App。

## M8 验收记录（2026-09-12）
- 0.10.0 / versionCode 10：Focus Guard 四档模式与合规框架。
- core：effectiveMode（EXTREME→STRICT→SOFT→NORMAL 降级）、guardStrength（自检缺失项）、isGuardAllowed（本应用/Launcher/白名单）纯函数 + 单测。
- focus 开始页：模式 chips（普通/软性/严格/极致）、实际生效模式说明、缺权限降级提示与「去开启专注防护」入口；start 携带 effective mode 写入 FocusSession.strictMode。
- androidApp：GuardPrefs（能力检测：无障碍服务启用、Usage Access AppOps API 26-29 兼容、屏幕固定恒可用）；FocusGuardService 无障碍服务（仅窗口包名、guard_active 时提醒、通知含 Emergency Exit action）；GuardSetupScreen 向导（披露同意→权限跳转→白名单管理→自检→OEM 指引）；MainActivity 装配 STRICT 开守护 / EXTREME startLockTask、结束本轮释放。
- manifest：+VIBRATE 已有、+INTERNET（同步网络）、+无障碍 service 声明（BIND_ACCESSIBILITY_SERVICE，isAccessibilityTool=false）。
- 文档：FOCUS_GUARD/ANDROID_PERMISSIONS/OEM_RELIABILITY 按 M8 实况重写。
- 全量验证 `E:/FocusFlowTools/m8-final2.log`：BUILD SUCCESSFUL（--no-parallel），52 项测试通过（core 23、database 7、designsystem 4、tasks UI 10、focus 2、sync 2、network 1、server 3）。
- lint 0 errors / 38 warnings：新增为 KTX SharedPreferences 提示 x5、queries 声明建议、isAccessibilityTool API 31 范围提示、依赖版本提示；均为提示类，不隐藏。
- 交付 APK：`artifacts/FocusFlow-0.10.0-debug.apk`，apksigner 验证通过，SHA256 75228BE6C476391F9AD3C6AF05A343A00D6FFFA6171E6DA6E599B3CDA2A18BA9。
- 未验证（无真机）：无障碍服务实际回调、厂商 ROM 守护行为、屏幕固定交互、SOFT 中断记录（UsageStats 聚合路径未接，文档已注明）。

## M9 验收记录（2026-09-12）
- 1.0.0 / versionCode 11：M0–M9 全部里程碑交付完成。
- Glance 桌面小组件：今日专注分钟（无数据显示"今天，先专注一件事"），点击打开 App；App 进入前台时刷新（MainActivity onStart updateAll），manifest 声明 APPWIDGET_UPDATE receiver。
- accessibility：既有交互均为文本化语义（checkbox contentDescription、导航 label、按钮文本）；读屏与键盘导航系统审计待真机。
- 全量验证 `E:/FocusFlowTools/m9-final.log`：BUILD SUCCESSFUL in 2m 23s（--no-parallel），52 项测试全部通过（core 23、database 7、designsystem 4、tasks UI 10、focus 2、sync 2、network 1、server 3）。
- lint 0 errors / 40 warnings（新增 2 条为 Glance 依赖相关提示类）。
- 交付 APK：`artifacts/FocusFlow-1.0.0-debug.apk`，apksigner 验证通过，SHA256 240A2A2D358A3A07ACE09F5784E79351E60ED29560FBE16ED41D5758DBC2747E。

## 产品完成度与遗留
- 已交付：离线优先任务/项目/标签、锚点计时与进程恢复、Session 进度聚合、Motion/Sound/Haptic、统计与热力图、账号+自建同步服务端（PG/H2）+ 双设备离线合并、自适应 Phone/Tablet、Live Presence/Owner/Observer/原子接管、Focus Guard 四档模式+向导+合规服务、桌面小组件。
- 未完成（不声称）：真机专项验收（通知、守护回调、厂商省电、旋转/折叠、双设备 HTTP/WS 实测）；SOFT 模式的 UsageStats 中断聚合；WS 自动重连；后台周期同步（WorkManager）；正式音频素材（仍为生成测试音）；server 生产部署（PG/Redis 多实例）；Release 签名与商店发布。

## 体验修正（2026-09-12）
- 任务「开始专注」进入准备页时，时长预填该任务的目标专注时长（原来硬编码 25 分钟），目标为 0/未设置时仍用 25 默认；直接打开专注页不变。测试复跑通过（tasks 10、focus 2）。

## 体验修正 2（2026-09-12）
- manifest 补声明 PACKAGE_USAGE_STATS：此前系统「使用情况访问」列表不显示 FocusFlow，用户无法为 SOFT 模式授权（向导跳转后找不到应用）。版本 1.0.1 / versionCode 12。

## 体验修正 3（2026-09-12）
- 极致模式不再级联依赖无障碍/Usage 权限：只看屏幕固定能力（真机恒可用），此前选极致实际被降级为普通导致屏幕固定从未触发。
- 极致模式运行中禁用暂停（提示长按返回可临时退出，计入中断）；取消引入预算：最近 24 小时内最多取消 2 次极致专注（COUNT 查询 CANCELLED+EXTREME 会话），用完后只能等计时结束或系统退出；取消对话框显示剩余次数。
- 版本 1.0.2 / versionCode 13。

## 体验修正 4（2026-09-12）
- 「我的」页重构为分组卡片：账号与同步 / 专注防护（状态 + 设置入口）/ 提醒（通知设置）/ 反馈 / 任务组织，替代原先混排一列。
- Guard 权限与白名单入口常驻「我的」页（原先仅藏在专注准备页降级提示里）；结束提醒通知设置同样直达。版本 1.0.3 / versionCode 14。

## 文档差距补齐（2026-09-12，1.1.0 / versionCode 15）
- 白噪音：Media3 MediaSessionService（mediaPlayback FGS + 系统媒体通知）承载；运行时合成 10 秒无缝棕噪声 WAV（雨声/风声两档，无版权素材）；与音效音量独立（设置页滑条）；专注运行页可切换 无声/雨声/风声；commonMain 走 WhiteNoiseController 接口（LocalWhiteNoise），Desktop 未接线。
- 主题：跟随系统/浅色/深色三档（设置页选择，ThemeMode 进 FeedbackPrefs 持久化），FocusTheme 增加深色配色。
- Stats：新增"今年"范围（1 月 1 日起）与项目分布条形列表（按当前范围聚合各项目专注时长）。
- 任务：新增可选"开始时间 HH:mm"（高级项，校验格式；清除日期时联动清除）。
- 全量验证 `E:/FocusFlowTools/gap-final.log`：BUILD SUCCESSFUL in 3m 19s，53 项测试全部通过，lint 0 errors。
- 交付 APK：`artifacts/FocusFlow-1.1.0-debug.apk`，SHA256 EA30DDE958C8409F750597B7C2D50B0C2B99C9794A374282C0CA6600CF67E494。
- 仍未实现（产品文档遗留清单）：FocusPreset 自定义计时模板、任务提醒 Reminder、重复任务规则、Achievement 成就、正式音效素材；白噪音真机播放与后台行为未实测。

## 体验修正 5（2026-09-12，1.1.1 / versionCode 16）
- 任务级专注模式：创建/编辑任务（高级项）可选"进入时选择/普通/软性/严格/极致"；设置了模式的任务点「开始专注」直接按该模式开始（时长=目标专注分钟，未设目标则正计时），权限不足会降级时则进准备页展示降级说明。
- 从任务进入准备页时模式预选为任务预设；未设置模式的任务行为不变。Room schema v6（tasks.preferredFocusMode），迁移测试覆盖 v1–v5→v6。
- 顺带修复：plannedStartTime 此前未随保存落库（无测试覆盖未暴露），一并接通。
- 全量验证 54 项测试全部通过（新增直开流程 UI 测试）；APK `artifacts/FocusFlow-1.1.1-debug.apk`，SHA256 EF4A4961A784B76B7E9E8CE2FEFED0978945D7E5A8E342D8376F72630D58DEE0。

## 体验修正 6（2026-09-12，1.2.0 / versionCode 17）
- 极致模式完全禁止中途取消：不提供取消按钮，倒计时等待自然结束、正计时用「完成专注」收尾；紧急情况仅保留系统级长按返回退出屏幕固定（合规 Emergency Exit），专注不因退出固定而终止。移除上一版的 24 小时取消次数预算。
- 专注页背景：5 档本地渐变预设（极光紫/深海蓝/森林绿/暮色橙/素雅浅色），准备页一键切换并持久化；不使用图片素材、不申请任何权限（含存储/后台权限）。
- 专注语录：内置 7 条 + 设置页自定义（每行一条，本地保存）；开始专注时按会话轮换显示。
- 白名单改为勾选本机应用列表：通过 launcher intent 查询可启动应用（manifest queries 声明，不申请 QUERY_ALL_PACKAGES），Guard 向导内弹窗勾选；替代手输包名。

## 体验修正 7（2026-09-12，1.3.0 / versionCode 18）
- 极致模式运行中隐藏「返回任务」：专注页无离开入口，仅系统长按返回退出屏幕固定（不终止专注）。
- 休息流程改为番茄循环：点「休息 5 分钟」记录下一轮参数，休息倒计时自然结束后自动开始同任务、同时长、同模式的下一轮专注；主动点「结束休息」则不自动继续。任务被完成/删除时自动继续失败并停留在结束页。
- UI 视觉升级（ui-ux-pro-max 指导，保留品牌紫）：全量定制 Typography（大字轻字重计时数字/加粗标题/带字距标签）、Shapes 更大圆角+胶囊按钮、浅深两套补全容器色与边框色；TimerRing 重写为渐变描边圆帽环（浅色背景自动切换内容色）；任务卡改为细边框卡片+优先级色点+圆角细进度条+胶囊开始按钮。

## 体验修正 8（2026-09-12，1.4.0 / versionCode 19）
- 休息/终态自动解锁：专注进入休息或结束的瞬间解除屏幕固定与守护，休息期间可自由使用手机；休息页明示"休息结束自动开始下一轮"。
- 背景体系重构：预设全部换新（曜石/午夜蓝/玫瑰暮光/青黛/暖米白），每个背景带前景色配比（主文字/次要文字/深浅底标记），专注页所有元素颜色由背景主题派生（LocalContentColor），主按钮为反白胶囊。
- 自定义背景：相册选择（系统 PickVisualMedia，无存储权限），图片拷贝至本机并以 45% 深色遮罩保证可读性；入口在专注准备页背景档位与设置「外观与反馈」。
- 「我的」页重构为标准设置：分组菜单行（账号与同步/专注防护/提醒/外观与反馈/任务组织/关于）+ 子页返回结构；关于页含版本号（读取 PackageManager）、用户协议与隐私政策全文。

## Bugfix（2026-09-12，1.4.1 / versionCode 20）
- 极致模式自动下一轮（休息结束）未重新应用屏幕固定：守护/固定改为由会话状态驱动（FocusScreen 观察运行会话的 strictMode + FOCUSING 即回调应用，幂等），不再依赖开始按钮回调链；同时覆盖进程恢复后重新锁定。新增状态驱动回归测试。

## Bugfix（2026-09-12，1.4.2 / versionCode 21）
- 白噪音切换导致崩溃退出（屏幕固定随之失效）的防御修复：WAV 首次生成移出主线程；服务与 UI 侧全部白噪音操作 runCatching 兜底——即使系统在屏幕固定等状态下拒绝前台服务，也只静默失败不中断专注。若真机仍复现，需 adb logcat -b crash 定位具体栈。

## Bugfix（2026-09-14，1.4.3/1.4.4 / versionCode 22-23）
- 白噪音崩溃根因（真机 logcat 实锤）：ForegroundServiceDidNotStartInTimeException——startForegroundService 的 5 秒前台契约未被满足。修复：WhiteNoiseService 在 onStartCommand 同步 startForeground（mediaPlayback 类型通知），WAV 生成与播放准备随后进行；停止时移除前台通知。
- 专注页顶栏文案此前硬编码"普通专注"，无论选何种模式都显示同一标题造成"没切换成功"的误解；现随实际模式显示（准备页=所选模式，运行页=会话模式），标签函数移入 core 共用。
- 1.4.4 已真机安装验证入口。

## 休息时长自定义 + 休息期白噪音控制（2026-09-14，1.4.5 / versionCode 24）
- 休息时长不再固定 5 分钟：准备页新增「休息分钟（1–120）」输入（默认 5），经 FocusViewModel/TimerController/TimerEngine 贯穿至 FocusRun.breakDuration 持久化（ActiveFocusEntity 已有该列，无 schema 变更）；完成页按钮文案按实际休息时长显示；番茄自动下一轮（AutoNextRound）沿用本轮自定义休息时长。
- 修复：白噪音切换按钮只在 FOCUSING/PAUSED 显示，进入休息后声音仍在播放却无法关闭；现运行态各阶段（含休息、完成）都显示控制按钮。
- 新增 TimerEngineTest.customBreakDurationDrivesBreakPhase 与 FocusRepositoryTest breakDuration 持久化断言；core/database/tasks/focus/sync/network desktopTest 全绿；已真机升级安装（1.4.4 → 1.4.5，android-user debug 签名）。
- 交付 APK：`artifacts/FocusFlow-1.4.5-debug.apk`，SHA256 C0F9B65EE9DF5B48098BCBDFDA8578C0857CF43DDF5080C3764BBAC0AF68B3AA。
- 注：E:\桌面\FocusFlow 是本仓库 1.1.0 时期的旧副本（含未提交半成品），同日亦在其中复刻了相同两项修复（该目录 1.1.1 / versionCode 16，未提交）；主线以本仓库为准。

## 背景图片化 + 极致防退出（2026-09-14，1.4.6 / versionCode 25）
- 修复自定义背景"上传无效果"两个根因：设置页只有选图按钮、从未把 focusBackground 切到 CUSTOM（现外观页有完整背景选择，选图成功即自动切换）；背景文件固定名 focus_bg 导致二次选择路径不变、界面不刷新（现文件名带时间戳，旧文件自动清理）。
- 内置背景由纯渐变升级为照片：drawable-nodpi 内置 5 张 1080x1920 图片（picsum.photos seed focusflow-* 生成，Unsplash 免费商用图库），commonMain 经 LocalBuiltinBackgrounds 注入，无图/解码失败回退原渐变；深色主题压 50% 黑遮罩、暖米白压 60% 白遮罩保证可读性。Desktop 不提供 → 仍渐变。
- 极致模式防退出加强：专注进行中每秒校验 isInLockTaskMode，长按返回等系统退出后 1 秒内自动重新 startLockTask；全部极致模式文案（准备页/运行页/向导）同步改为"确认后直到计时结束无法退出"。平台边界不变：无法阻止强制关机/adb/系统卸载，消费级应用不做按键拦截（Play 政策）。
- tasks/focus desktopTest 全绿；已真机升级安装。交付 APK：`artifacts/FocusFlow-1.4.6-debug.apk`。
