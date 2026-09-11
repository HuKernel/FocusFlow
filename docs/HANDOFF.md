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
- 系统 PATH 未提供 Java/Gradle/Android SDK；正在 E 盘配置独立工具链。

## 下一步
完成 M0 工程、核心模型、Room skeleton、共享 UI 和测试；执行 Android 构建与 Desktop 编译后再声明 M0 完成。
