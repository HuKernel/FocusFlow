# FocusFlow 交接入口

更新时间：2026-09-11。当前版本 0.3.0 / versionCode 3，M2 构建与自动测试通过；下一阶段 M3。真机专项测试尚未完成。

## 开始前必读
1. AGENTS.md、FocusFlow_Product_Spec_v3.docx、FocusFlow_Prototype_v3.png。
2. docs/PROGRESS.md：实际验证状态；docs/DECISIONS.md：工程决策。
3. 用户要求中文沟通、小步 commit + push、每阶段交付可安装 APK，所有开发工具和缓存仅在 E 盘。

## 环境与重建
- 工作区 D:/zhuomian/FocusFlow；远程 https://github.com/HuKernel/FocusFlow.git，main。
- `. ./scripts/env.ps1` 设置环境，SDK 复用 E:/WordFlow/android-sdk。
- JDK E:/FocusFlowTools/jdk/jdk-17.0.18+8；GRADLE_USER_HOME=E:/FocusFlowTools/gradle-home，TEMP/TMP 也在 E 盘。
- local.properties 本机 SDK 配置不提交；不要提交缓存、签名或密钥。Git 代理 http://127.0.0.1:7898。
- 依赖在 gradle/libs.versions.toml，Gradle 8.13 wrapper 已缓存校验。

```powershell
. ./scripts/env.ps1
./gradlew.bat :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:classes :shared:core:desktopTest :shared:database:desktopTest :shared:designsystem:desktopTest :shared:feature:tasks:desktopTest --console=plain
```

## 已交付与验证
- M1 最终提交 09b001a；M2 开发检查点 5ce38f2 已推送，最终修正与本文随随后提交。
- 全量日志 E:/FocusFlowTools/m2-final.log：BUILD SUCCESSFUL in 1m 3s。
- Android build/lint、Desktop classes、26 项测试通过：core 13 / database 7 / designsystem 2 / tasks UI 4。
- 最后改动通知设置回流与 v1/v2→v3 数据库升级测试，E:/FocusFlowTools/m2-release.log 复验 build/lint/database 成功，28s。
- lint 0 errors / 15 warnings：14 条原有版本/备份提示，1 条 exact alarm 权限提示，代码已检查 canScheduleExactAlarms 并捕获撤权异常；没有 baseline 隐藏。
- APK artifacts/FocusFlow-0.3.0-debug.apk，apksigner 验证通过，可覆盖旧版 Debug 安装。
- SHA256：9CDEBE961B5BBC12C2CE21A5DFA346333FA5434167726A9F7622FE9F774BF20D。
- adb devices 当前无设备；未声称真机通知、后台/重启、旋转或省电专项已通过。GitHub Actions 已配置，未核验远程执行结果。

## 当前实现
- feature/tasks：离线任务 CRUD、Today、搜索筛选、项目标签管理，真实 Room Flow + TasksViewModel。
- feature/focus：普通倒计时/正计时、暂停继续、完成/取消、5 分钟休息；列表与详情开始入口、活动/完成记录返回入口。
- core/TimerEngine.kt：纯函数时间锚点状态机；Android elapsedRealtime + BOOT_COUNT，跨开机 epoch 估算并提示，Desktop 跨进程也估算。
- database/FocusRepository.kt：Mutex 串行平台闹钟与 IMMEDIATE 事务；Session/outbox/active_focus 一次提交。旧 sessionId 回调不影响新会话。
- active_focus 是单行运行快照；focus_sessions 只写终态，完成后快照保留给总结界面，关闭总结才清除。旧 timer_anchors 为兼容保留，不作为运行状态源。
- Room schema 3，保留 1/2/3 JSON，AutoMigration 1→2→3；真实 SQLite 测试分别从 v1 和 v2 升级后验证旧 Task/Session 与启动专注。
- 只有 COMPLETED.actualDuration 计入进度；取消不累计、暂停不计时、休息不改 Session。完成专注不自动勾选整个任务；进行中的任务不能勾选完成或删除。
- AndroidFocusAlarm/Receiver 在 androidApp：可选通知、exact/inexact 回退、开机/升级恢复，没有 timer FGS、精确权限或无障碍权限。平台提醒失败不撤销本地事务；系统可能延后/遗漏通知，前台恢复兜底。

## 下一步
1. M3：复用 designsystem 的 Motion/Sound/Haptic 接口与 tokens，实现状态反馈、Reduced Motion；音频没有合法素材时按产品要求保留接口/合法测试音，不随意下载素材。
2. 真机专项：升级安装保留任务；倒计时 1 分钟；暂停离开再恢复；杀进程重开；锁屏/旋转；通知拒绝/撤销；设备重启与厂商省电。
3. M4 Stats，M5 Account/Sync，M6 完整自适应验收，M7 Owner/Observer，M8 Guard。用户曾询问锁屏，已说明本阶段 NORMAL，不能声称 Strict/Extreme 已实现。
4. 云同步、账号、Guard、任务日程提醒、重复任务、编辑草稿进程恢复尚未实现；outbox 不等于已联网同步。
5. 每次可体验阶段更新版本/APK、PROGRESS/HANDOFF，构建和测试通过后 commit + push。

## 已知工程问题
- Windows classes.jar 占用：先 gradlew --stop 再重建，不删除业务数据或全量缓存。
- Room KSP 跨模块解析模型需要 core 的 serialization 使用 api，不能改成 implementation。
- Desktop Compose 测试必须提供 RESUMED LocalLifecycleOwner，结束时清理 ViewModelStore 与数据库。
- 跨重启且系统改时间不能完全可靠估时；已有用户提示，不承诺绝对准确。
- 系统通知是事务后的副作用，提交后突然死亡可能漏提醒，但不会丢已结算时长。
