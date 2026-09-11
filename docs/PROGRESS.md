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

## 下一阶段
M3：Motion/Sound/Haptic 框架与状态反馈，保留 Reduced Motion。STRICT/EXTREME 屏幕固定仍在 M8；M5/M7 才实现云同步与 Owner/Observer。
