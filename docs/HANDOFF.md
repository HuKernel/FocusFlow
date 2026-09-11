# FocusFlow 交接入口

更新时间：2026-09-11。当前阶段：M0 工程基线构建/测试已通过，下一阶段 M1。

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
- 最终日志 `E:/FocusFlowTools/build-final.log`：BUILD SUCCESSFUL in 28s。
- Android Debug APK 构建、Desktop classes、Android lintDebug、9 项测试通过。
- APK：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`（约 22MiB）。
- 测试 XML：三个 shared 模块的 `build/test-results/desktopTest/`；包含真实 Compose 导航和 SQLite 文件测试。
- Room schema：`shared/database/schemas/com.focusflow.database.FocusDatabase/1.json`。
- lint 保留新版本提示，零错误；Room 官方 expect/actual 构造器有 Kotlin Beta 特性提示，原因见 DECISIONS。
- `adb devices` 未发现设备：Android 真机启动、旋转、进程死亡等尚未验证。
- GitHub Actions 已配置，尚未核验远程执行结果。

## 重建命令
```powershell
. ./scripts/env.ps1
./gradlew.bat :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:classes :shared:core:desktopTest :shared:database:desktopTest :shared:designsystem:desktopTest --console=plain
```
Wrapper 发行包已缓存到 E 盘，可正常运行。`local.properties` 本机内容为 `sdk.dir=E\:/WordFlow/android-sdk`，不提交。

## 踩坑与修复
- Room KSP 无法解析跨模块 @Serializable 模型时，core 的 serialization 依赖需 api 可见，不能改成 implementation。
- PowerShell 传 Gradle `-D...` 参数需使用单引号，避免被拆成任务名。
- Windows SDK 路径的冒号在 .properties 中必须转义，未转义会使 lint 失败。
- 先前 build.log/build-verify.log 有失败记录，应以最后 build-final.log 为准。

## 下一步
1. M1 实现 Task/Today/Project/Tag 和 ViewModel，将业务页面移出 designsystem 到 feature 模块。
2. 当前页面为真实空状态导航壳；任务统计之外没有 CRUD 功能，也没有伪造样例数据。
3. 任务改动必须 Room 与 SyncEvent 同一事务，Task 没有 progress 字段。
4. M2 实现真正 Timer；终态 Session 与活动锚点分离，不能用 insert IGNORE 实现 ACTIVE→COMPLETED 更新。
5. 有 Android 设备后安装 APK 验证启动/旋转；M2 测杀进程和系统时间变化。
6. 小步验证后 commit + push；每次停止更新本文件和 PROGRESS。
