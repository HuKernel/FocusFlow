# FocusFlow — Codex / Coding Agent Master Prompt v3.0

你是一名资深 Kotlin Multiplatform / Android 架构师、Compose UI 工程师、后端工程师和测试工程师。请在当前工作区创建并实现一个 production-oriented 的跨设备专注效率产品：FocusFlow。

不要只做 Demo、静态页面或伪代码。每个 Milestone 都必须可以真实编译、运行、测试；重要设计决策写入 docs/DECISIONS.md，进度写入 docs/PROGRESS.md。

## 0. 产品目标

FocusFlow = ToDo + Focus Timer + Session-based Task Progress + Cross-device Sync + Focus Guard + Statistics + Motion + Sound + Haptic。  
核心理念：**One task. Any device.**

第一阶段：Android Phone + Android Tablet，同一 App，自适应布局。  
第二阶段：Windows/macOS，因此从第一天开始隔离 Android API，保证 shared/domain/database/network/sync 可复用。

## 1. 非协商架构原则

1. Offline First：客户端所有操作先写 Room，本地数据库为客户端 Single Source of Truth；网络不可用时 Task/Timer/History/Stats 可正常工作。
2. Task progress 不能由一个可覆盖的 `task.progress` 字段作为事实源。完成进度必须由 `FocusSession(actualDuration, status=COMPLETED)` 聚合得到。
3. Timer 真实时间不能使用 `remainingSeconds--`。前台锚点使用 `SystemClock.elapsedRealtime()`，同时持久化 epoch、plannedDuration、pausedDuration、state，支持 background/lock/process death recovery。
4. 普通页面不得直接依赖 Android 系统 API。Android 专属能力放 `androidMain`，commonMain 只依赖接口。
5. Motion、Sound、Haptic 都必须是 Design System 的一部分，不允许 Feature 页面散落 magic duration、raw sound resource、任意震动代码。
6. 权限不齐必须优雅降级，绝不能导致 Task/Normal Focus 不能使用。

## 2. 推荐技术栈

- Kotlin / Kotlin Multiplatform
- Compose Multiplatform / Jetpack Compose
- Material 3 + Material 3 Adaptive / WindowAdaptiveInfo
- ViewModel + StateFlow + Coroutines + Flow
- Room KMP
- DataStore
- Ktor Client + kotlinx.serialization
- Koin
- Ktor Server
- PostgreSQL
- Redis（presence / rate limit / temporary realtime state；不得成为核心 Task 永久存储）
- WebSocket（Active Focus presence / handoff）
- FCM abstraction
- Media3 / MediaSessionService（后台 white noise）
- Jetpack Glance
- JUnit / kotlin.test / Compose UI Test
- ktlint / detekt
- GitHub Actions

优先使用当前稳定、互相兼容的版本。除非必要，不使用 alpha。若必须使用，记录原因。

## 3. 平台和 SDK

- Android Phone / Tablet / Foldable
- targetSdk / compileSdk 以当前 Google Play 要求为准；在 2026-09 环境应至少考虑 API 36。
- Desktop target 可以先建立可编译入口，但不做完整 Desktop UI。

## 4. 模块建议

focusflow/

- androidApp/
- desktopApp/
- shared/core/
- shared/designsystem/
- shared/database/
- shared/network/
- shared/sync/
- shared/domain/
- shared/feature/today/
- shared/feature/tasks/
- shared/feature/focus/
- shared/feature/statistics/
- shared/feature/settings/
- server/
- docs/

可以按 Gradle 最佳实践调整，但必须在 docs/ARCHITECTURE.md 解释。

## 5. 核心实体

至少实现：User, Device, Task, Project, Tag, TaskTag, FocusSession, FocusPreset, Reminder, SyncEvent, Achievement, UserAchievement, StrictModeConfig, AllowedApp。

Task:

- id, userId, title, description
- status: TODO/IN\_PROGRESS/DONE/ARCHIVED
- priority: NONE/LOW/MEDIUM/HIGH
- projectId, plannedDate, plannedStartTime
- targetFocusMinutes
- completedAt, createdAt, updatedAt, deletedAt
- revision

FocusSession:

- id UUID
- userId, taskId, deviceId, ownerDeviceId
- type COUNTDOWN/STOPWATCH
- plannedDuration, actualDuration
- startedAt, endedAt, pausedDuration
- interruptCount
- status ACTIVE/PAUSED/COMPLETED/CANCELLED
- strictMode
- createdAt, updatedAt, revision

## 6. Timer Engine

状态机：  
IDLE → PREPARING → FOCUSING ↔ PAUSED → FOCUS\_COMPLETED → BREAKING → SESSION\_FINISHED；任意适用阶段可 CANCELLED。

实现要求：

- UI tick 与真实时间源解耦。
- process death 后读取 Session anchor 计算剩余时间。
- Pause/Resume 累加 pausedDuration。
- 完成后写 immutable/append-oriented FocusSession 记录。
- 编写 unit tests：start/pause/resume/cancel/complete/process restore/system time change。

## 7. 多设备同步

同步四层：

1. Task Sync：字段 + revision。
2. Session Sync：UUID merge，不允许 LWW 把另一个设备 Session 覆盖掉。
3. Progress：聚合 COMPLETED FocusSession。
4. Live Focus Presence：WebSocket。

离线案例必须通过测试：Phone 离线完成 25m，Tablet 离线完成 30m，联网后双方都得到 55m。

实现 SyncEvent：id, deviceId, entityType, entityId, operation CREATE/UPDATE/DELETE, payload, clientTimestamp, serverRevision, retryCount, state。  
Delete = soft delete/tombstone。  
API：

- POST /api/v1/sync/push
- GET /api/v1/sync/pull?cursor=...  
返回 changes, nextCursor, serverTime。

## 8. Session Owner / Observer / Handoff

ACTIVE Session 同时只能有一个 ownerDeviceId。  
其他设备显示 Observer：任务名、状态、基于共享锚点估算的 remaining、owner device。  
WebSocket events 至少建模：FOCUS\_STARTED, FOCUS\_PAUSED, FOCUS\_RESUMED, FOCUS\_COMPLETED, FOCUS\_CANCELLED, OWNER\_CHANGED。

至少完成 Observer UI 和协议。若实现 Handoff：`Continue on this device` 必须通过服务端原子转移 owner，旧 Owner 收到事件后立即变为 Observer。

## 9. 四档 Focus Mode

- NORMAL：普通计时。
- SOFT：允许切 App，但记录中断；优先使用 UsageStats/生命周期等合规方式。
- STRICT：用户明确开启后，使用 AccessibilityService 等合规能力识别非白名单 App，并显示 Focus Guard/引导回专注。
- EXTREME：使用普通消费级 App 可用的 Screen Pinning/startLockTask 路径；不得声称拥有企业 Device Owner 级不可退出锁定。

必须提供 Emergency Exit。

## 10. Focus Guard Setup / 权限

不要在首次 App launch 连续申请敏感权限。普通 Task + Normal Focus 必须不依赖 Accessibility/Usage Access。  
第一次用户主动启用 Strict Focus 时，进入 Setup Wizard：

1. Prominent disclosure：解释访问什么、为什么、是否上传，并显式同意。
2. Notification。
3. Usage Access（如需要）。
4. AccessibilityService（Strict）。
5. Alarm capability（仅精确提醒必要时）。
6. Background Reliability 检查：识别 Restricted/Optimized 等状态并引导。
7. OEM Guide：自启动/后台活动/最近任务锁定等只作为厂商特定用户引导，不假设统一 API，不偷偷改变设置。
8. Allowed Apps 白名单。
9. Self test，显示 Protection Strength。

AccessibilityService 必须遵守 Google Play 当前政策：非 accessibility tool 不设置 isAccessibilityTool=true；做清晰披露和 affirmative consent；不得阻止卸载/禁用、绕过系统安全控制或欺骗性操控 UI。

缺权限降级：STRICT → SOFT → NORMAL。

## 11. Background / Alarm / Media

- 不为了 Timer 每秒运行永久 Foreground Service。
- AlarmManager 用于结束通知；根据权限状态 exact/inexact fallback。
- Session recovery 永远是最终兜底。
- White Noise 需要后台播放时使用 Media3 MediaSessionService + mediaPlayback foreground service type。
- 不把 Timer、Sync、Analytics 全塞进一个万能 FGS。

## 12. Adaptive UI

禁止 `if (isTablet)` 作为主架构。使用当前 Window Size / WindowAdaptiveInfo。  
Compact：Bottom Navigation + single pane。  
Medium：Navigation Rail + supporting pane。  
Expanded：Navigation Rail + List-Detail；Focus 主计时 + Device/Progress side pane。  
支持 rotation、split screen、multi-window、foldable、keyboard、mouse。

## 13. 主要页面

- Splash / lightweight onboarding
- Today
- All Tasks / Today / Upcoming / Completed
- Create/Edit Task
- Task Detail
- Focus Setup
- Focus Running
- Focus Complete / Break
- Statistics Overview / Heatmap
- Focus Guard Dashboard
- Focus Guard Setup Wizard
- Allowed Apps
- Device Management
- Account / Login
- Settings / Theme / Notification / Sound / Haptic

## 14. Design System

视觉关键词：Calm, Soft, Modern, Focused, Friendly, Premium。不要复制番茄ToDo品牌/视觉。  
定义 FocusTheme, FocusColors, FocusTypography, FocusShapes, FocusSpacing, FocusMotion, FocusSound, FocusHaptic。  
Timer 使用 tabular numerals。  
组件：FocusButton, TaskCard, TaskCheckbox, ProgressBar, TimerRing, ProjectChip, TagChip, StatisticCard, Heatmap, DevicePresenceChip, SyncStatus, AdaptiveNavigation, EmptyState, AchievementToast。

## 15. Motion System

MotionTokens 集中定义：  
instant≈80ms, fast≈140ms, standard≈220ms, emphasized≈320ms, celebration≈500-700ms。  
定义统一 easing/spring。  
实现：Task Complete、Task insert/remove、Start Focus transition、Timer Ring、Pause/Resume Morph、Focus Complete、Stats number transition、Cross-device progress update、Device Handoff state。  
支持 Reduced Motion：改用 Fade/Color，减少 spring/scale/shared transition。  
目标无明显 jank，至少 60fps。

## 16. Sound / Haptic

commonMain: SoundController / HapticController。  
SoundEvent: focusStart, focusPause, focusResume, focusComplete, taskComplete, breakStart, achievement, error。  
Sound effects 和 White Noise 音量独立；业务代码不能直接使用 raw resource id。  
如果仓库没有合法音频素材，创建 abstraction + placeholder/test tone（如合适），并在 docs/ASSETS\_NEEDED.md 列出所需原创/royalty-free 资源，不下载未知版权素材。  
Haptic tokens：tap, selection, startFocus, success, warning, handoff。只在重要状态使用。

## 17. Today / Tasks / Stats

Today：日期、今日 Focus 时间、streak、Active Focus、今日任务、完成任务、FAB。  
Task Card：checkbox、title、project/category、target/actual progress、quick start。  
Tasks：All/Today/Upcoming/Completed/Search/Project/Tag/Priority；Expanded 做 List-Detail。  
Stats：Today/Week/Month；Focus Time, Session Count, Completed Tasks, Average Session, Interrupt Count, Streak；Heatmap 本地计算优先。

## 18. 后端

Ktor Server modules：auth, user, device, task, focus, sync, presence。  
PostgreSQL 保存核心永久数据。Redis 只做 presence/cache/rate-limit/temp realtime state。  
至少设计 REST DTO、数据库 schema、migration、sync cursor、idempotency。

## 19. 隐私与 Analytics

默认不上传 Task title/description 到 analytics。  
默认不把完整 Installed App list 上传服务器。  
Allowed app/package 信息优先本地保存；若将来确有跨设备同步需求，必须单独设计用户知情与最小化方案。  
Analytics 事件可包含 task\_created/focus\_started/focus\_completed/sync\_success 等非内容事件。

## 20. 测试

必须自动测试：

- Timer start/pause/resume/complete/cancel/recovery
- Session aggregation
- Phone 25m + Tablet 30m → 55m
- Sync idempotency / retry / tombstone
- Permission rejected/revoked degradation
- Observer/Owner state
- Room migrations
- Adaptive smoke tests
- Reduced Motion behavior（至少逻辑层）

## 21. 文档

创建并持续维护：

- docs/README\_PRODUCT.md
- docs/ARCHITECTURE.md
- docs/DATABASE.md
- docs/SYNC.md
- docs/TIMER.md
- docs/FOCUS\_GUARD.md
- docs/ANDROID\_PERMISSIONS.md
- docs/OEM\_RELIABILITY.md
- docs/DESIGN\_SYSTEM.md
- docs/MOTION\_SOUND.md
- docs/ASSETS\_NEEDED.md
- docs/DECISIONS.md
- docs/PROGRESS.md

## 22. Milestones

M0：Project scaffold + CI + Design System + Adaptive shell + core models + Room skeleton。  
M1：Task + Today + Project/Tag。  
M2：Reliable Timer + FocusSession + process recovery。  
M3：Motion + Sound + Haptic framework。  
M4：Stats + Heatmap。  
M5：Account + Backend + Offline Sync。  
M6：Tablet Expanded / List-Detail。  
M7：Live Presence + Owner/Observer。  
M8：Focus Guard + background reliability + OEM guide + Strict/Extreme。  
M9：Widget + performance + accessibility + beta polish。

每完成一个 Milestone：

1. build；2. run tests；3. 修复 compilation/test；4. 更新 docs/PROGRESS.md；5. 不允许在 build 失败时声称完成。

## 23. 现在开始执行

先检查工作区。如果项目不存在，创建 KMP 工程并从 M0 开始。自行选择当前稳定且兼容的 dependency versions，并集中版本管理。建立 Android 可运行入口、Desktop 可编译入口、设计系统、Adaptive navigation shell、Room skeleton、核心 entities、Timer interfaces/state model、Unit Test 基础、CI 和文档。

遇到普通工程选择时自行判断并记录，不要因小问题停下来问我。只有真正需要外部凭据、签名、账号密钥或无法从仓库/SDK推断的信息才提出阻塞项。

优先级永远是：

1. Reliable Timer
2. Correct Session-based Progress
3. Offline First / Sync correctness
4. Adaptive Phone+Tablet
5. Focus Guard 合规与可靠性
6. Motion/Sound/Haptic 质感

最终产物必须是能真实继续开发、编译、测试并扩展到 Desktop 的工程，而不是一次性 Demo。
