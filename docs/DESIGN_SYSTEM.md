# 设计系统

沿用用户原型的柔和紫 #686DFA、背景 #F6F7FD、深色文字、圆角卡片、绿色成功态。
FocusTheme/FocusColors/FocusTypography/FocusShapes/FocusSpacing 集中管理主题。
当前共享组件：StatisticCard、EmptyState 和窗口布局分类。业务导航、TaskCard、任务详情、表单在 feature/tasks；TimerRing/Heatmap 等跟随之后功能实现。
页面图标采用 Material Icons；导航保留文字标签与语义。计时器上线时使用 tabular numerals。
真实 Compose UI 测试已覆盖窄窗口任务 CRUD、宽窗口导航/组织管理、无效输入保留表单；仍需后续真机/模拟器字体缩放、旋转、多窗口和键鼠检查。
