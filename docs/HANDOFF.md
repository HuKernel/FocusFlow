# FocusFlow 交接入口

更新时间：2026-09-11。当前阶段：M0 开发中，尚未通过构建验收。

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

## 下一步
1. 检查 `E:/FocusFlowTools/build.log` 的完整构建结果。core 3 项测试已通过。
2. 运行新增 Compose UI smoke 测试及 Android lint。
3. 维护 Room schema、验证结果，commit + push 后更新交接。
4. M0 未验收前不得标完成；M1 实现 Task/Today/Project/Tag 和 ViewModel，M2 才有真实 Timer。
