# 工程决策

## 2026-09-11
- Android / Desktop 入口分离，共享 Kotlin 代码不直接调用 Android API。
- 首次交付聚焦 M0；后续功能按里程碑推进，不展示假的同步、保护强度或用户统计。
- 以原型的柔和紫色、浅色卡片、手机底部导航、宽窗口侧栏作为视觉依据。
- 工具链与缓存仅放 E:\FocusFlowTools，项目代码仍在用户指定工作区。
- 发现已有 SDK：复用 E:\WordFlow\android-sdk（API 36、Build Tools 36），不更改其现有组件。
- 版本采用已发布的兼容稳定组合 Kotlin 2.2.20 / Compose 1.9.3 / AGP 8.13.0 / Gradle 8.13 / Room 2.8.4 / KSP 2.2.20-2.0.4；不是逐项追求最新版本。AGP 9 的 KMP 插件迁移作为独立升级处理，避免同时引入新 DSL 和首版业务。
- minSdk 26 与 Room 2.8 的 Android 基线一致。compileSdk/targetSdk 36 是本产品指定基线。
- Gradle 镜像包与官方 SHA-256 比对一致：20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78。
- 核心模型的序列化依赖使用 api 暴露：Room KSP 必须解析跨模块序列化模型生成的类型，implementation 会导致 MissingType。
- Room 的官方 expect/actual 构造器模式会产生 Kotlin Beta 语言特性提示；保留官方构造方式，不引入预发布依赖，不隐藏该提示。
- 禁止系统备份复制本地会话/设备状态；显式声明 Android 12+ dataExtractionRules，跨设备数据由后续同步协议处理。

## 官方依据
- [AGP 8.13 兼容性](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Compose Multiplatform 兼容性](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Room 发布记录](https://developer.android.com/jetpack/androidx/releases/room)
- [Room KMP 平台构造器](https://developer.android.com/kotlin/multiplatform/room)

## M1
- 新增一个任务 feature 模块承载导航业务/Today/Tasks/组织管理；designsystem 只保留主题和无业务组件，不为 Today 额外创建空模块。
- 使用稳定版 kotlinx-datetime 0.7.1 做本地日历日期验证与时区换算，KMP ViewModel/Lifecycle 2.9.5 管理跨平台状态。当前 Kotlin 的 Clock/Instant/UUID 标准库 API 需要 opt-in，封装在数据/日期逻辑文件，不在页面散落 Android API。
- 任务新建默认计划今天，可选择明天或未安排；Today 仅显示 plannedDate 等于本地今天的任务，未安排项在全部任务中。
- 本地身份首次业务写入时生成 UUID 并永久保存在 local_identity，记录 deviceId 到 outbox；M5 登录时再设计本地数据归属迁移，不伪造已登录状态。
- 所有 Repository 写入使用 Room useWriterConnection/IMMEDIATE transaction。Task 与关系、项目、标签的 SyncEvent 一起提交；任务编辑/删除检查当前 revision。
- 数据库 v1→v2 只新增表，选择 Room 自动生成迁移；测试从已提交的 v1 schema 创建真实旧库并检查 Task/Session 保留，不使用 destructive migration。
- 项目/标签删除采用自身 tombstone；关联移除发 DELETE 事件；项目删除时任务 projectId 清空并递增 revision，不删除任务或 Session。
- 手机详情用对话框，宽窗口直接展示所选任务。完整折叠屏与多窗口视觉验收仍在 M6。
- [KMP ViewModel 官方用法](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html)、[日期库用法](https://github.com/Kotlin/kotlinx-datetime)。

## M2
- 用户希望任务可以开始专注；保留产品规定的任务 checkbox，但增加列表与详情的开始入口。勾选是任务状态，完成 Session 才贡献时长，两者独立。
- 新增 feature/focus，复用现有 ViewModel/Compose/Room 依赖。只实现 NORMAL；STRICT/EXTREME 屏幕固定按 M8 实施，不把普通计时称为锁屏。
- active_focus 是单行运行快照；终态 Session 与 outbox 在同一 SQLite 事务落库，快照保留到用户关闭总结，以支持进程恢复后展示完成结果。
- 同次 Android 开机使用 elapsedRealtime；跨开机和 Desktop 跨进程按 epoch 估算并显式提示。无法在没有可信外部时间源的情况下保证跨重启改时钟后的准确性。
- Android 结束提醒使用 AlarmManager，不为 tick 启动 FGS；仅主动点击后申请通知。没有新增精确闹钟特殊权限：运行时能力允许则 exact，否则 inexact，SecurityException 再回退。
- lint 对 exact 调用仍有 MissingPermission warning：manifest 未声明特殊权限，但调用前检查 canScheduleExactAlarms 且捕获撤权异常；保留诊断，不隐藏。未来精确提醒向导按产品需要单独设计。
- [AlarmManager 官方约束](https://developer.android.com/develop/background-work/services/alarms)。

## M3
- 反馈框架集中在 designsystem：FocusFeedback 持有 prefs StateFlow 并按开关过滤 SoundController/HapticController 调用；UI 通过 LocalFocusFeedback 读取，Desktop/测试默认 Off，不引入 DI 框架。
- 统一 motion：FocusMotion token（80/140/220/320/600ms）+ 单一 CubicBezierEasing(0.2,0,0,1)；Reduced Motion 时 duration() 返回 0 且转场退化为纯 fade，保留颜色/透明度过渡。
- 音效不下载素材：AndroidSoundController 运行时生成 16-bit PCM 正弦衰减测试音写 cacheDir WAV，SoundPool(USAGE_ASSISTANCE_SONIFICATION) 播放；正式音效与白噪音需求记录在 docs/ASSETS_NEEDED.md。
- 触感走系统 Vibrator：API 29+ VibrationEffect.createPredefined，26–28 回退 createOneShot；manifest 仅新增普通 VIBRATE 权限。
- 反馈偏好持久化用 Android SharedPreferences（focus_feedback），系统"移除动画"（ANIMATOR_DURATION_SCALE=0）作为减弱动效默认值；不为三个布尔引入 DataStore。
- 完成音效由状态驱动（观察 anchor.state 进入 FOCUS_COMPLETED 播一次并按 sessionId 去重），倒计时自然结束与手动完成一致；开始/暂停/继续/休息由按钮触发。操作失败只显示错误文本并播 ERROR，不回滚反馈。
- 任务列表动效用 LazyColumn animateItem + 标题颜色过渡；不做逐项 spring 缩放，避免列表滑动开销。
- 新增 lint 警告 UseKtx x2（SharedPreferences.edit）：不为消警告引入 core-ktx 依赖，保留标准写法。

## M4
- 统计聚合是 core 纯函数（focusStats/rangeStartDate/heatmapWeeks），只读内存 List，无 IO 无新依赖；页面层 remember(sessions,tasks,range,today) 触发重算，数据量（本地会话）不需要增量索引。
- 日期口径与 streak 一致：设备本地时区、ISO 字符串字典序比较；周从周一开始（kotlinx.datetime DayOfWeek.ordinal，0.7.1 无 isoDayNumber 公共 API）。
- Heatmap 组件在 designsystem 只做纯绘制（List<List<Long>> 网格），不懂日期知识；周网格与颜色分档（<15/<30/<60 分钟）在 core 生成，组件可测试性与复用更好。
- 平均专注按毫秒 Long 整除后再转分钟显示，不做小数；本月含跨月的上周数据按自然月裁剪。
- 热力图固定最近 12 周、不可滚动（YAGNI）；Canvas + aspectRatio 自适应，无滚动交互开销。
- UI 测试的 fake clock 基准改为 System.currentTimeMillis()：统计范围用真实系统日期（state.today），硬编码未来 epoch 会让全部范围聚合为 0。

## M5（第一阶段）
- server 存储写成纯 JVM 类（Store，JDBC），Ktor HTTP 层薄封装；shared/sync 的 desktopTest 直接复用真 Store 当传输层，避免用 mock 重复实现服务端语义。
- 协议模型（AuthRequest/PushRequest/ServerChange 等）放 core/Protocol.kt 单一来源，server 依赖 KMP core 模块的 JVM variant。
- 实体投影单表（payload TEXT 全量）+ applied_events 幂等去重，不做事件溯源回放；pull 按 per-user 单调 revision 增量。
- upsert 用 UPDATE-then-INSERT 而非 ON CONFLICT(cols) DO UPDATE：H2 即使 MODE=PostgreSQL 也不支持该语法，开发/测试与生产 PG 保持同一 DDL 路径。
- 密码 PBKDF2WithHmacSHA256（JDK 内置，无新依赖）；token 为 HMAC 签名的无状态凭证，SERVER_SECRET 环境变量注入，默认值仅限本地开发。
- 客户端 SyncEngine 不感知 HTTP：SyncTransport 接口由后续网络层实现；echo 排除 + 本地 revision 检查双保险防回环。
- Room v4 只加 sync_state 表；Android 登录 UI 与自动同步未实现，不声称 App 已能云同步（outbox ≠ 已联网）。

## M5（第二阶段）
- SyncTransport 接口上移 core：network 与 sync 互相需要对方类型，core 是双方共同祖先，接口随协议模型放置消解循环依赖。
- 账号凭据（serverUrl/token/username）存 Room v5 sync_state 而非 SharedPreferences：与 cursor 同处一行、跨平台一致、迁移可测；token 只存本地不上传。
- HTTP 客户端用 Ktor CIO 单引擎（Android/Desktop 同一 commonMain 实现），不做 OkHttp/engine 分平台；错误信息在 network 层集中映射为中文。
- 启动自动同步只做一次静默尝试（LaunchedEffect + runCatching），周期后台同步等真实用户反馈后按需加 WorkManager，YAGNI。

## M6
- 自适应判定维持 windowLayout(widthDp) 三档（600/840），不引入 material3-adaptive 依赖：现有 BoxWithConstraints 已覆盖 compact/medium/expanded 的导航与面板差异，断点与 Material 建议一致。
- 宽窗口 List-Detail 默认选中首个可见任务（LaunchedEffect 幂等回填），避免右侧空态；筛选后选中项失效时自动重选，用户主动选择不被覆盖。
- Focus 宽屏侧栏显示本轮真实进度（计划/已专注/暂停累计 + 进度条）与设备说明；Device presence 数据 M7 才存在，不预做空壳。
- 键鼠 hover/右键菜单属于 M9 polish；折叠屏/分屏由宽度驱动自动适配，真机验收待设备。

## M7
- presence 用每用户单活跃会话的内存 PresenceHub，不用 Redis：单实例语义即可满足协议测试，Redis 属多实例部署需求（DECISIONS 记录替换点）；snapshot 不持久化，重连走 REST 兜底。
- server 主引擎从 CIO 换 Netty：本机/CI 环境 CIO server 对 WS 升级一律 400（最小复现已证），Netty 正常。
- Observer 估算只用共享锚点 + 本地时钟：协议不传实时秒数，避免时钟偏差被误当进度；结算仍以 owner 本地 FocusSession 为准。
- 接管（Continue on this device）：服务端原子转移后由 OWNER_CHANGED 广播驱动两端——新 owner adopt 同一 sessionId（elapsedBeforeAnchor=估算值），旧 owner releaseLocal 不结算不广播，会话最终由新 owner 按原 UUID 结算。
- presence 客户端连接在 VM 构造时读取凭据，登录后需重启 App 生效；断线重连暂依赖页面重进，自动重连留待真机反馈后做。

## M8
- 降级决策是 core 纯函数（effectiveMode/guardStrength/isGuardAllowed），androidApp 只做能力检测与 UI；无障碍/Usage/固定能力检测都在平台层，可测逻辑在 common。
- Guard 状态（模式、同意时间、白名单、guard_active）存本机 SharedPreferences，不进 Room/outbox：隐私条款要求白名单不上传，且守护状态无同步语义。
- 无障碍服务只订阅窗口状态变化、只读包名（不申请内容读取）；isAccessibilityTool=false + 显著披露 + 主动同意，符合 Play 政策的非无障碍工具路径。
- EXTREME 用 Activity.startLockTask（普通消费级屏幕固定，系统确认弹窗 + 长按返回退出），不声称 Device Owner 级不可退出锁定；结束时主动 stopLockTask。
- 白名单 = 本应用 + Launcher（运行时解析）+ 用户手动添加包名；不做安装列表枚举（避免 QUERY_ALL_PACKAGES），DECISIONS 记录未来如需应用选择器再单独设计。
- tasks feature 的 compose 依赖从 implementation 改 api：androidApp 的 Guard 向导需要同一 JB compose 版本的 foundation/material3，避免引入第二套 androidx 坐标。
- 构建环境：Windows 下强杀 daemon 会遗留缓存锁导致后续构建假死，清理 *.lock + --no-parallel 恢复；Netty 依赖经代理缓慢下载属网络现象非工程问题。

## M9
- 桌面小组件用 Glance 1.1.1 最小实现：显示今日专注分钟（或引导文案），点击打开 App；刷新时机是 App 进入前台（onStart updateAll），不做 WorkManager 周期刷新（YAGNI，按用户反馈再加）。
- widget 数据走 Application 直查 Room COMPLETED sessions 按本地日期聚合，与 Stats 同口径；不引入新的数据通道。
- accessibility 现状：关键交互均有文本或 contentDescription（checkbox、FAB、导航 label、统计卡文本化）；TalkBack/键盘导航的系统性审计留待真机（桌面测试无法覆盖读屏）。
- 1.0.0 定为功能里程碑完成线：真机专项（通知/守护/省电/旋转/双设备）与 Beta 打磨仍在清单中，不因版本号宣称已验收。

## 文档差距补齐
- 白噪音音源用运行时合成棕噪声 WAV（10 秒首尾淡出无缝循环、雨/风两档强度），不下载素材；正式音源仍列 ASSETS_NEEDED。
- 白噪音经 Media3 MediaSessionService（mediaPlayback FGS）承载并交由其默认媒体通知，不自写通知；UI 侧 AndroidWhiteNoise 通过服务静态实例直控、未运行时 startForegroundService 拉起。
- 主题偏好并入 FeedbackPrefs（SharedPreferences 持久化），FocusTheme 参数化深浅色；不引入 DataStore。
- Stats 年视图 = 自然年（1 月 1 日）口径，与月/周一致按本地时区；项目分布仅列当前范围内的项目时长条形，不做跨范围切换动画。
- 产品文档核对结论：文档与原型中不存在"自习室"功能，未实现不是缺口；FocusPreset/Reminder/重复规则/Achievement 为文档遗留，见 PROGRESS。
