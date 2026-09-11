# 架构

M0 模块：`androidApp`、`desktopApp`、`shared/core`、`shared/database`、`shared/designsystem`。
入口分别使用 Android Activity 与 Desktop Window，共享 Compose 页面只依赖 Compose API。
core 包含可序列化领域模型、Timer 接口与时间源；database 使用 Room KMP、平台路径构造器和 BundledSQLiteDriver。
designsystem 暂时包含自适应导航壳；M1 添加任务功能时将业务页面移入 feature 模块，保持设计系统无业务依赖。

不一次性创建空 network/sync/server/feature 模块：分别在 M1/M5 实现其职责时添加。此调整减少空 Gradle 项目，不改变平台隔离要求。
本地数据流目前为 Room Flow → Compose 状态。M1 引入 ViewModel，所有任务修改与 outbox 事件同一事务落库。

宽度根据当前 Compose 窗口约束判断：<600dp compact，600–839dp medium，≥840dp expanded，不判断设备型号。
M0 提供导航与 supporting pane；真正任务 List-Detail 和折叠铰链避让尚待 M6。
