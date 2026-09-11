# FocusFlow 交接入口

更新时间：2026-09-11。当前版本 0.8.0 / versionCode 8，M6 自适应验收完成；下一阶段 M7。真机专项测试尚未完成。

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
- M3–M6 已交付；宽窗口 List-Detail 默认选中任务、Focus 侧栏为真实进度面板，版本 0.8.0 / versionCode 8。
- 全量日志 E:/FocusFlowTools/m6-final.log：BUILD SUCCESSFUL in 56s；41 项测试通过：core 16、database 7、designsystem 4、tasks UI 9、sync 2、network 1、server 2。
- lint 0 errors / 17 warnings：原 15 条 + 2 条 UseKtx（SharedPreferences.edit 标准写法提示，不加 core-ktx）。
- APK artifacts/FocusFlow-0.8.0-debug.apk，apksigner 验证通过，可覆盖旧版 Debug 安装。
- SHA256：12FCE02989E1578C273C7F12D2290F3B081CB6E5059D16D49199BB9787EC3D86。
- adb devices 当前无设备；未声称真机音质/触感/动画流畅度或省电专项已通过。GitHub Actions 已配置，未核验远程执行结果。

## 当前实现
- feature/tasks：离线任务 CRUD、Today、搜索筛选、项目标签管理；M3 起列表项有插入/删除/重排动画与完成颜色过渡，「我的」页有音效/震动/减弱动效开关。
- feature/focus：普通倒计时/正计时、暂停继续、完成/取消、5 分钟休息；M3 起有准备↔运行转场、暂停/继续按钮 morph、完成庆祝动效与状态驱动音触反馈。
- designsystem：FocusMotion token + 统一 easing + Reduced Motion（duration 归零、转场退化 fade）；FocusFeedback（LocalFocusFeedback）按 prefs 过滤 Sound/Haptic 调用；TimerRing 进度平滑；StatisticCard 数字滚动。
- Android 反馈实现：SoundPool 播运行时生成测试音（未下载素材，正式素材见 docs/ASSETS_NEEDED.md）；Vibrator createPredefined（26–28 回退 oneShot）；prefs 存 SharedPreferences(focus_feedback)，系统"移除动画"作为减弱动效默认值。Desktop 反馈 no-op。
- M5：server/、shared/network（HttpFocusSyncApi + 中文错误映射）、shared/sync（Engine+Coordinator）、Room v5（sync_state 含 serverUrl/token/username）、「我的」页登录/同步 UI、Android 启动静默同步。详见 docs/SYNC.md。
- database：Room schema 5，迁移测试 v1–v4 → v5。

## 下一步
1. 真机/本机联调：一台机器 `./gradlew :server:run`（DATABASE_URL 指向 PostgreSQL，缺省 H2 文件），手机填 http://<局域网IP>:8080 注册同步；双设备验证 25m+30m=55m。
2. 真机专项：升级安装保留任务与设置；音质/触感/动效体感；倒计时 1 分钟、暂停恢复、杀进程、锁屏/旋转、通知拒权、重启与厂商省电。
3. M7 Owner/Observer + WebSocket Presence（FOCUS_* 事件、共享锚点估算、服务端原子 owner 转移；Redis 做 presence），M8 Guard（含 Strict/Extreme 屏幕固定）。折叠屏/分屏真机验收、键鼠 hover、后台周期同步（WorkManager）按需补。
4. 白噪音（Media3）、音量设置、正式音效素材、Desktop 声音未实现；outbox 不等于已联网同步。
5. 每次可体验阶段更新版本/APK、PROGRESS/HANDOFF，构建和测试通过后 commit + push。

## 已知工程问题
- Windows classes.jar 占用：先 gradlew --stop 再重建，不删除业务数据或全量缓存。
- Room KSP 跨模块解析模型需要 core 的 serialization 使用 api，不能改成 implementation。
- Desktop Compose 测试必须提供 RESUMED LocalLifecycleOwner，结束时清理 ViewModelStore 与数据库。
- 跨重启且系统改时间不能完全可靠估时；已有用户提示，不承诺绝对准确。
- 系统通知是事务后的副作用，提交后突然死亡可能漏提醒，但不会丢已结算时长。
- SoundPool 首次加载为异步：极快点击首音可能哑一声，启动后即正常。
