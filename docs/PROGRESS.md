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

## M2 开发中（2026-09-11）
- 已实现锚点计时引擎、Room v3 活动快照、事务结算和会话 outbox；core/database 测试通过（日志 E:/FocusFlowTools/m2-data.log）。
- 已接入任务“开始专注”、正计时/倒计时、暂停/恢复、完成/取消和休息页面，以及 Android 可选结束通知。
- 当前正在执行 Android/Desktop 构建和界面测试，尚未验收 M2；首次构建遇到 Windows classes.jar 占用，停止 Gradle daemon 后重试。
- 本阶段实现 NORMAL；STRICT/EXTREME 屏幕固定仍按产品计划在 M8 实现。
