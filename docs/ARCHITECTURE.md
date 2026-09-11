# 架构

当前模块：`androidApp`、`desktopApp`、`shared/core`、`shared/database`、`shared/designsystem`、`shared/feature/tasks`。
入口分别使用 Android Activity 与 Desktop Window，共享 Compose 页面只依赖 Compose API。
core 包含可序列化领域模型、Timer 接口与时间源；database 使用 Room KMP、平台路径构造器和 BundledSQLiteDriver。
designsystem 仅包含主题、布局分类、StatisticCard/EmptyState；feature/tasks 承载导航、Today、任务与组织管理，使用 TasksViewModel。

network/sync/server 等模块在 M5 实现职责时添加，避免空 Gradle 项目。
数据流：Room Flow → TaskRepository → TasksViewModel StateFlow → lifecycle-aware Compose。业务写入走 Repository，所有任务修改与 outbox 事件同一事务落库。
Android Application 持有数据库和 Repository，Activity 配置变更不会关闭数据库。Desktop Window 使用相同 Repository 与 ViewModel，应用退出关闭数据库。

宽度根据当前 Compose 窗口约束判断：<600dp compact，600–839dp medium，≥840dp expanded，不判断设备型号。
手机详情使用对话框；宽窗口 supporting pane 已显示所选任务详情。完整折叠铰链避让、键鼠和多窗口真机验收仍待 M6。
