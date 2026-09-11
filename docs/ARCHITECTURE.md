# 架构

当前模块：`androidApp`、`desktopApp`、`shared/core`、`shared/database`、`shared/designsystem`、`shared/feature/tasks`、`shared/feature/focus`。
入口分别使用 Android Activity 与 Desktop Window，共享 Compose 页面只依赖 Compose API。
core 包含可序列化领域模型、Timer 接口与时间源；database 使用 Room KMP、平台路径构造器和 BundledSQLiteDriver。
designsystem 包含主题、布局分类、StatisticCard/EmptyState/TimerRing；feature/tasks 承载导航、Today、任务与组织管理，使用 TasksViewModel。feature/focus 承载专注设置/运行/休息及 FocusViewModel，根页面负责其可见生命周期刷新。

TimerEngine 位于 core，FocusRepository 在 database 持久化状态转换；AndroidFocusClock 在 core/androidMain，AlarmManager/通知/权限入口在 androidApp。Desktop 使用 NoFocusAlarm。普通页面通过接口及回调使用平台能力。

network/sync/server 等模块在 M5 实现职责时添加，避免空 Gradle 项目。
数据流：Room Flow → TaskRepository → TasksViewModel StateFlow → lifecycle-aware Compose。业务写入走 Repository，所有任务修改与 outbox 事件同一事务落库。
Android Application 持有数据库和 Repository，Activity 配置变更不会关闭数据库。Desktop Window 使用相同 Repository 与 ViewModel，应用退出关闭数据库。

宽度根据当前 Compose 窗口约束判断：<600dp compact，600–839dp medium，≥840dp expanded，不判断设备型号。
手机详情使用对话框；宽窗口 supporting pane 已显示所选任务详情。完整折叠铰链避让、键鼠和多窗口真机验收仍待 M6。
