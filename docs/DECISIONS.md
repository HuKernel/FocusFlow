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
