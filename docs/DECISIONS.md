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
