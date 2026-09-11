# FocusFlow

One task. Any device. Android Phone / Tablet 优先的离线专注产品，使用 Kotlin Multiplatform 与 Compose。

当前版本：0.2.0（M1）。支持离线任务创建/编辑/完成/删除、Today、搜索筛选、项目与标签管理；计时和云同步尚未开放。功能和验证状态见 [开发进度](docs/PROGRESS.md)，接手开发先读 [交接文档](docs/HANDOFF.md) 和 [AGENTS.md](AGENTS.md)。

## 构建

需要 JDK 17、Android SDK API 36 / Build Tools 36。依赖版本集中在 `gradle/libs.versions.toml`。
本机工具仅安装在 E 盘，SDK 复用 `E:/WordFlow/android-sdk`。其他机器设置自己的 `JAVA_HOME` 与 `local.properties`，不要提交绝对 SDK 路径。

```powershell
. ./scripts/env.ps1
./gradlew.bat :androidApp:assembleDebug :desktopApp:classes
./gradlew.bat :shared:core:desktopTest :shared:database:desktopTest :shared:designsystem:desktopTest :shared:feature:tasks:desktopTest
./gradlew.bat :desktopApp:run
```

Android APK：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`。
本机交付副本：`artifacts/FocusFlow-0.2.0-debug.apk`（生成文件，不提交 Git；CI 构建上传 APK artifact）。
测试报告：对应模块 `build/reports/tests/desktopTest/index.html`。

产品源资料：`FocusFlow_Product_Spec_v3.docx`、`FocusFlow_Prototype_v3.png`。
