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
