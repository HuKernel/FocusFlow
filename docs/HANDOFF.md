# FocusFlow 交接入口

## M2 进行中检查点（优先于下方 M1 历史说明）
- 已新增 TimerEngine、FocusRepository、Room v3 active_focus、feature/focus 和 Android 原生结束通知。
- core/database 测试通过；Android assembleDebug、Desktop classes、原有 3 个任务 UI 测试通过（E:/FocusFlowTools/m2-ui.log，46s）。
- 新增 taskStartsStopwatchAndOnlyCompletedFocusCounts UI 测试，尚待下一轮运行；接下来执行完整测试/lint、更新文档与版本 0.3.0、交付 APK。
- 当前仍是 NORMAL 专注，没有屏幕固定、Accessibility 或跨设备同步；M8 再实现 Guard。
- Windows classes.jar 占用已通过 gradlew --stop 后重建解决。

更新时间：2026-09-11。当前版本：0.2.0 / versionCode 2；M1 构建和测试验收通过，下一阶段 M2。

## 开始前必读
1. `AGENTS.md`：架构、里程碑和不可妥协约束。
2. `FocusFlow_Product_Spec_v3.docx` 与 `FocusFlow_Prototype_v3.png`：产品与视觉依据。
3. `docs/PROGRESS.md`：实际验证结果；不要把计划当成已实现。
4. `docs/DECISIONS.md`：工程决策。

## 用户约定
- 中文沟通；小步实现、验证、commit、push 到 origin。
- 所有开发工具、SDK 仅安装到 E 盘；使用 `E:\FocusFlowTools`，构建缓存也放该目录。
- 项目代码保留在 `D:\zhuomian\FocusFlow`。
- 每次停止前更新本文件，记录命令、失败原因、下一步。
- 每个可体验阶段都要提供新版 APK，不能只给代码或截图。
- 不提交 local.properties、缓存、密钥或签名文件。

## 当前环境
- 远程：`https://github.com/HuKernel/FocusFlow.git`。
- 初始提交：`ad5f4e3`。
- README 原先只有项目标题；详细约束实际在 AGENTS.md。
- `. ./scripts/env.ps1` 设置当前终端环境；SDK 复用 `E:/WordFlow/android-sdk`。
- JDK：`E:/FocusFlowTools/jdk/jdk-17.0.18+8`；Gradle：`E:/FocusFlowTools/gradle/gradle-8.13/bin/gradle.bat`。
- `GRADLE_USER_HOME=E:/FocusFlowTools/gradle-home`，临时文件在 E 盘。
- 本机网络：Git 使用 `http://127.0.0.1:7898` 代理。Gradle 首轮 Maven 依赖直连可下载。
- Wrapper 的 Gradle 发行包官方地址跳转 GitHub，直连超时；镜像下载后已与官方 SHA-256 对比。
- 额外的 `E:/FocusFlowTools/android-sdk` 是发现旧 SDK 前安装的副本，当前项目不使用；未擅自删除。

## 已验证
- M1 最终日志 `E:/FocusFlowTools/m1-ui.log`：BUILD SUCCESSFUL in 49s。早期 m1-build.log 的失败已修复。
- Android Debug APK、Desktop classes、Android lintDebug 通过；16 项测试全通过（core 6、database 5、designsystem 2、feature/tasks 3）。
- APK 原始输出：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`；固定版本交付：`artifacts/FocusFlow-0.2.0-debug.apk`，签名验证通过。
- 测试 XML：四个 shared 模块 `build/test-results/desktopTest/`，含真实 Compose CRUD 和真实 SQLite 1→2 升级测试。
- Room schemas：`shared/database/schemas/com.focusflow.database.FocusDatabase/{1,2}.json`，不可覆盖修改旧 schema。
- lint 零错误、14 warnings（13 条依赖新版本提示、1 条旧版备份规则提示）；Room 官方 expect/actual 构造器有 Kotlin Beta 提示。
- M0 时 adb 无连接设备；本轮未执行 Android 真机安装/旋转。用户已手动看过 M0 页面；M1 提供新 APK 供体验。
- GitHub Actions 已配置，尚未核验远程执行结果。

## 重建命令
```powershell
. ./scripts/env.ps1
./gradlew.bat :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:classes :shared:core:desktopTest :shared:database:desktopTest :shared:designsystem:desktopTest :shared:feature:tasks:desktopTest --console=plain
```
Wrapper 发行包已缓存到 E 盘，可正常运行。`local.properties` 本机内容为 `sdk.dir=E\:/WordFlow/android-sdk`，不提交。

## 踩坑与修复
- Room KSP 无法解析跨模块 @Serializable 模型时，core 的 serialization 依赖需 api 可见，不能改成 implementation。
- PowerShell 传 Gradle `-D...` 参数需使用单引号，避免被拆成任务名。
- Windows SDK 路径的冒号在 .properties 中必须转义，未转义会使 lint 失败。
- Windows 增量构建可能占用 classes.jar；本次通过 `./gradlew.bat --stop` 后重建解决，不要直接删除数据或整体清缓存。
- Compose Desktop 裸测试环境没有 LocalLifecycleOwner，测试已显式提供 RESUMED LifecycleRegistry；不要因此取消产品的 lifecycle-aware 收集。
- v1 schema 对无索引的表会省略 indices，升级测试需要处理该合法缺省情况。

## 下一步
1. 从 M2 开始：倒计时/正计时、暂停/继续/取消/完成、后台与进程死亡恢复。现有 core/Timer.kt 只有接口和锚点；AndroidFocusClock 使用 elapsedRealtime。
2. 新建 feature/focus，沿用 tasks 的 Application 级 Repository 生命周期和 KMP ViewModel；业务 UI 不调用 Android 系统 API。
3. 终态 Session 与活动锚点分离，不能用 insert IGNORE 实现 ACTIVE→COMPLETED 更新；结算 Session/outbox/锚点清理应为一个事务。
4. M1 已有 TaskRepository，所有业务写入与 outbox 在 IMMEDIATE 事务内，检查 revision；不要绕过它增加直接 UI DAO 写入。
5. Today 仅显示 plannedDate=本地今天；未安排任务在全部列表。勾选完成不改变专注时间。搜索/筛选目前在本地内存完成，规模达到性能瓶颈再转 SQL/Paging。
6. 当前未实现云同步/账号/提醒/重复任务、Timer、Guard；未保存编辑草稿以跨进程恢复。不要把 outbox 的存在当成已同步。
7. 保留当前清淡紫色页面风格。下一阶段验收后更新版本号、打包带版本 APK、commit + push，并更新交接。

## M1 主要代码
- `shared/feature/tasks/`：FocusRoute、TasksViewModel、导航/任务列表、任务表单、项目标签管理。
- `shared/database/.../TaskRepository.kt`：任务与组织 CRUD、UUID 本机身份、事务 outbox。
- `shared/core/.../TaskPlanning.kt`：日期验证、Today/Upcoming 等过滤、连续专注。
- `androidApp/.../FocusFlowApplication.kt`：本地库与 Repository 的应用生命周期。
