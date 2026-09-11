# 开发进度

## 2026-09-11
- 已检查初始仓库、读取产品文档与原型。
- 已创建交接入口，确认所有工具安装到 E 盘。
- M0 工程基线构建/测试验收通过；Android 真机运行待验证。M1–M9 未开始。
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

## 下一阶段
M1：任务创建/编辑/完成/软删除、Today 真实筛选、Project/Tag、ViewModel；保持 Room 与 outbox 事务一致。
M2：可靠计时引擎与完整恢复测试。当前 Timer 只有接口/状态/锚点，不能开始计时。

## M1 开发中（2026-09-11）
- 新增 shared/feature/tasks，业务页面从 designsystem 移出。
- 已实现任务表单、完成/重开、软删除、搜索、日期/项目/标签/优先级筛选与详情。
- 新增项目/标签的创建、重命名、删除；删除组织结构保留任务并清理关联。
- 数据库 v2 增加 projects/tags/task_tags/local_identity；使用 Room AutoMigration 1→2，保留 v1 schema。
- TaskRepository 所有业务写入与 SyncEvent 在一个 IMMEDIATE 事务中完成；编辑检查 revision 防止旧快照覆盖新值。
- Android 数据库提升到 Application 生命周期，避免旋转时关闭 ViewModel 正在使用的数据库。
- 版本更新为 0.2.0 / versionCode 2。完整构建/UI/迁移验收仍在执行，尚未交付新版 APK。
