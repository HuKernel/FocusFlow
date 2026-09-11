# FocusFlow 交接入口

更新时间：2026-09-11。当前版本 0.5.0 / versionCode 5，M4 构建与自动测试通过；下一阶段 M5。真机专项测试尚未完成。

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
- M3（Motion/Sound/Haptic）与 M4（Statistics/Heatmap）已交付；当前版本 0.5.0 / versionCode 5。
- 全量日志 E:/FocusFlowTools/m4-final.log：BUILD SUCCESSFUL in 31s；Android build/lint、Desktop classes、32 项测试通过：core 16 / database 7 / designsystem 4 / tasks UI 5。
- lint 0 errors / 17 warnings：原 15 条 + 2 条 UseKtx（SharedPreferences.edit 标准写法提示，不加 core-ktx）。
- APK artifacts/FocusFlow-0.5.0-debug.apk，apksigner 验证通过，可覆盖旧版 Debug 安装。
- SHA256：7276D6515A02F192047ABED3937ADBC45DC8877CD726CC8428601057B1E73890。
- adb devices 当前无设备；未声称真机音质/触感/动画流畅度或省电专项已通过。GitHub Actions 已配置，未核验远程执行结果。

## 当前实现
- feature/tasks：离线任务 CRUD、Today、搜索筛选、项目标签管理；M3 起列表项有插入/删除/重排动画与完成颜色过渡，「我的」页有音效/震动/减弱动效开关。
- feature/focus：普通倒计时/正计时、暂停继续、完成/取消、5 分钟休息；M3 起有准备↔运行转场、暂停/继续按钮 morph、完成庆祝动效与状态驱动音触反馈。
- designsystem：FocusMotion token + 统一 easing + Reduced Motion（duration 归零、转场退化 fade）；FocusFeedback（LocalFocusFeedback）按 prefs 过滤 Sound/Haptic 调用；TimerRing 进度平滑；StatisticCard 数字滚动。
- Android 反馈实现：SoundPool 播运行时生成测试音（未下载素材，正式素材见 docs/ASSETS_NEEDED.md）；Vibrator createPredefined（26–28 回退 oneShot）；prefs 存 SharedPreferences(focus_feedback)，系统"移除动画"作为减弱动效默认值。Desktop 反馈 no-op。
- M4：core 新增 focusStats/rangeStartDate/heatmapWeeks 纯函数；designsystem 新增 Heatmap 组件；STATS 页有范围切换、6 张统计卡与 12 周热力图，无完成记录时空态。
- database 层无变化；Room schema 仍为 3。

## 下一步
1. M5 Account/Backend/Sync：Ktor Server（auth/user/device/task/focus/sync/presence）、PostgreSQL schema、SyncEvent push/pull、离线 25m+30m=55m 端到端；客户端 outbox 已就绪。
2. 真机专项：升级安装保留任务与设置；音质/触感/动效体感；倒计时 1 分钟、暂停恢复、杀进程、锁屏/旋转、通知拒权、重启与厂商省电。
3. M6 完整自适应验收，M7 Owner/Observer，M8 Guard（含 Strict/Extreme 屏幕固定）。
4. 白噪音（Media3）、音量设置、正式音效素材、Desktop 声音未实现；outbox 不等于已联网同步。
5. 每次可体验阶段更新版本/APK、PROGRESS/HANDOFF，构建和测试通过后 commit + push。

## 已知工程问题
- Windows classes.jar 占用：先 gradlew --stop 再重建，不删除业务数据或全量缓存。
- Room KSP 跨模块解析模型需要 core 的 serialization 使用 api，不能改成 implementation。
- Desktop Compose 测试必须提供 RESUMED LocalLifecycleOwner，结束时清理 ViewModelStore 与数据库。
- 跨重启且系统改时间不能完全可靠估时；已有用户提示，不承诺绝对准确。
- 系统通知是事务后的副作用，提交后突然死亡可能漏提醒，但不会丢已结算时长。
- SoundPool 首次加载为异步：极快点击首音可能哑一声，启动后即正常。
