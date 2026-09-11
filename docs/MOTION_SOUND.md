# Motion / Sound / Haptic

## 状态（M3，0.4.0）
Motion token 与反馈框架已实现并有测试；真机音质/触感体感待验收。

## Motion
- FocusMotion 集中定义 duration token：instant 80 / fast 140 / standard 220 / emphasized 320 / celebration 600（ms），统一 easing `CubicBezierEasing(0.2f, 0f, 0f, 1f)`，均在 designsystem commonMain。
- Reduced Motion：`FocusMotion.duration(reducedMotion)` 返回 0，动画瞬移到终态；转场退化为 fade，仅保留颜色/透明度过渡，去掉 slide/scale/spring。
- 已落地动效：任务插入/删除/重排（LazyColumn animateItem）、任务完成标题颜色过渡、Today 统计数字滚动（slide+fade）、Focus 准备↔运行转场（slide+fade，emphasized）、TimerRing 进度平滑过渡、暂停/继续按钮图标文字 morph（Crossfade）、专注完成庆祝 scale（spring，非 reduced 时）。
- Stats 数字过渡在 M4 统计页复用；跨设备进度与 Handoff 动效随 M5/M7 实现。

## Sound
- commonMain：SoundEvent（focusStart/pause/resume/complete、taskComplete、breakStart、achievement、error）+ SoundController 接口；业务不接触 raw resource id。
- Android：SoundPool（ASSISTANCE_SONIFICATION，maxStreams 2）播放运行时生成的正弦衰减测试音（cacheDir WAV），作为合法占位；音效音量当前固定 0.8，正式版本接入音量设置。
- 触发点：开始/暂停/继续/休息=按钮；专注完成=状态驱动（含倒计时自然结束）；任务勾选完成；错误出现。用户可在「我的」页关闭音效。
- Desktop：SoundController 未接线（no-op），桌面端反馈待后续。
- 白噪音（Media3 MediaSessionService + mediaPlayback FGS）不在 M3；实现时音效与白噪音音量必须独立。

## Haptic
- commonMain：HapticEvent（tap/selection/startFocus/success/warning/handoff）+ HapticController。
- Android：Vibrator + createPredefined（click/tick/heavyClick/doubleClick 映射），API 26–28 回退 createOneShot；仅 manifest 普通 VIBRATE 权限，无前台服务。
- 只在重要状态使用：开始专注、完成、任务完成、取消警告、设置开关确认。
- Desktop：no-op。

## Reduced Motion 来源
- 应用内开关（我的 → 减弱动效，SharedPreferences focus_feedback/reduced_motion）。
- 首次默认值取系统「移除动画」设置（ANIMATOR_DURATION_SCALE == 0）。
- 逻辑层测试：designsystem DesignSystemTest（duration 归零、prefs 过滤、Off 静默）。
