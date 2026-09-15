package com.focusflow.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModelStore
import com.focusflow.core.TaskStatus
import com.focusflow.core.FocusClock
import com.focusflow.core.TimerState
import com.focusflow.focus.FocusViewModel
import com.focusflow.database.*
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.*

class TaskUiTest {
    @get:Rule val compose = createComposeRule()
    private val directory = Files.createTempDirectory("focusflow-ui").toFile()
    private val database = openDatabase(directory.resolve("ui.db"))
    private val repository = TaskRepository(database)
    private val model = TasksViewModel(repository)
    private val clockStart = System.currentTimeMillis() // 与 state.today 同源，保证统计范围命中
    private val clock = object : FocusClock {
        var time = 0L
        override fun epochMillis() = clockStart + time
        override fun monotonicMillis() = time
        override fun bootId() = "test-boot"
    }
    private val focus = FocusViewModel(FocusRepository(database, clock))
    private val store = ViewModelStore().apply { put("tasks", model); put("focus", focus) }
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    }

    @After fun close() {
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.DESTROYED; store.clear() }
        database.close()
        directory.deleteRecursively()
    }

    private fun show(
        width: Int = 390, height: Int = 760, sync: com.focusflow.sync.SyncCoordinator? = null,
        guardCapabilities: (() -> com.focusflow.core.GuardCapabilities?)? = null,
        onOpenGuardSetup: (() -> Unit)? = null,
        onEnableReminders: (() -> Unit)? = null,
        onGuardModeApplied: ((com.focusflow.core.FocusMode) -> Unit)? = null,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    FocusApp(model, focus, sync, onEnableReminders, guardCapabilities, onOpenGuardSetup, onGuardModeApplied)
                }
            }
        }
        compose.waitUntil(10000) { model.state.value.loaded }
    }

    @Test fun compactTaskLifecycleWritesRealDatabase() {
        show()
        compose.onNodeWithTag("bottom_navigation").assertExists()
        compose.onNodeWithTag("navigation_rail").assertDoesNotExist()
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput("Read Kotlin")
        compose.onNodeWithTag("save_task").performClick()
        compose.waitUntil(10000) { model.state.value.data.tasks.size == 1 && model.state.value.editor == null }
        val id = model.state.value.data.tasks.single().id
        compose.onNodeWithTag("complete_$id").performClick()
        compose.waitUntil(10000) { model.state.value.data.tasks.single().status == TaskStatus.DONE && !model.state.value.busy }
        compose.onNodeWithTag("task_$id").performClick()
        compose.onNodeWithText("编辑任务").performClick()
        compose.onNodeWithTag("task_title").performTextReplacement("Read Compose")
        compose.onNodeWithTag("save_task").performClick()
        compose.waitUntil(10000) { model.state.value.data.tasks.single().title == "Read Compose" && model.state.value.editor == null }
        compose.onNodeWithTag("task_$id").performClick()
        compose.onNodeWithText("删除任务").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(10000) { model.state.value.data.tasks.isEmpty() && !model.state.value.busy }
        compose.onNodeWithText("Read Compose").assertDoesNotExist()
    }

    @Test fun expandedNavigationAndOrganizationManagement() {
        show(1100)
        compose.onNodeWithTag("navigation_rail").assertExists()
        compose.onNodeWithTag("supporting_pane").assertExists()
        compose.onNodeWithTag("bottom_navigation").assertDoesNotExist()
        compose.onNodeWithContentDescription("管理项目和标签").performClick()
        compose.onNodeWithTag("organization_name").performTextInput("Study")
        compose.onNodeWithTag("save_organization").performClick()
        compose.waitUntil(10000) { model.state.value.data.projects.size == 1 && !model.state.value.busy }
        compose.onNodeWithText("标签").performClick()
        compose.onNodeWithTag("organization_name").performTextInput("Kotlin")
        compose.onNodeWithTag("save_organization").performClick()
        compose.waitUntil(10000) { model.state.value.data.tags.size == 1 && !model.state.value.busy }
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("专注").performClick()
        compose.onNodeWithText("准备好，专注一件事").assertExists()
    }

    @Test fun invalidDateKeepsEditorAndDoesNotWriteTask() {
        show()
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput("Read")
        compose.onNodeWithText("更多选项").performClick()
        compose.onNodeWithTag("task_date").performScrollTo().performTextReplacement("2026-02-30")
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("save_task").assertExists()
        Assert.assertTrue(model.state.value.data.tasks.isEmpty())
        Assert.assertNotNull(model.state.value.editor)
    }

    @Test fun taskStartsStopwatchAndOnlyCompletedFocusCounts() {
        show()
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput("Focus test")
        compose.onNodeWithTag("save_task").performClick()
        compose.waitUntil(10000) { model.state.value.data.tasks.size == 1 && model.state.value.editor == null }
        val id = model.state.value.data.tasks.single().id
        compose.onNodeWithTag("quick_focus_$id").performClick()
        compose.onNodeWithText("正计时").performClick()
        compose.onNodeWithTag("start_focus").performScrollTo().performClick()
        compose.waitUntil(10000) { focus.state.value.run?.anchor?.state == TimerState.FOCUSING && !focus.state.value.busy }
        compose.runOnIdle { clock.time += 60_000 }
        compose.onNodeWithTag("pause_resume").performScrollTo().performClick()
        compose.waitUntil(10000) { focus.state.value.run?.anchor?.state == TimerState.PAUSED && !focus.state.value.busy }
        Assert.assertTrue(model.state.value.data.sessions.isEmpty())
        compose.runOnIdle { clock.time += 30_000 }
        compose.onNodeWithTag("pause_resume").performClick()
        compose.waitUntil(10000) { focus.state.value.run?.anchor?.state == TimerState.FOCUSING && !focus.state.value.busy }
        compose.runOnIdle { clock.time += 60_000 }
        compose.onNodeWithTag("complete_focus").performScrollTo().performClick()
        compose.waitUntil(10000) { model.state.value.data.sessions.size == 1 && !focus.state.value.busy }
        Assert.assertEquals(120_000L, model.state.value.data.sessions.single().actualDuration)
        compose.onNodeWithTag("dismiss_focus").performScrollTo().performClick()
        compose.waitUntil(10000) { focus.state.value.run == null && !focus.state.value.busy }
        compose.onNodeWithText("返回任务").performClick()
        compose.onNodeWithTag("task_$id").assertExists()
    }

    @Test fun statsPageAggregatesCompletedSessionsAndSwitchesRange() {
        show()
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput("Stats")
        compose.onNodeWithTag("save_task").performClick()
        compose.waitUntil(10000) { model.state.value.data.tasks.size == 1 && model.state.value.editor == null }
        val id = model.state.value.data.tasks.single().id
        compose.onNodeWithTag("quick_focus_$id").performClick()
        compose.onNodeWithText("正计时").performClick()
        compose.onNodeWithTag("start_focus").performScrollTo().performClick()
        compose.waitUntil(10000) { focus.state.value.run?.anchor?.state == TimerState.FOCUSING && !focus.state.value.busy }
        compose.runOnIdle { clock.time += 60_000 }
        compose.onNodeWithTag("complete_focus").performScrollTo().performClick()
        compose.waitUntil(10000) { model.state.value.data.sessions.size == 1 && !focus.state.value.busy }
        compose.onNodeWithTag("dismiss_focus").performScrollTo().performClick()
        compose.waitUntil(10000) { focus.state.value.run == null && !focus.state.value.busy }
        compose.onNodeWithText("返回任务").performClick()
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithText("完成专注").assertExists()
        compose.onNodeWithText("1 次").assertExists()
        compose.onNodeWithTag("stats_heatmap").assertExists()
        compose.onNodeWithTag("stats_range_MONTH").performClick()
        compose.onNodeWithText("1 次").assertExists()
        compose.onNodeWithTag("stats_range_TODAY").performClick()
        compose.onNodeWithTag("stats_heatmap").assertExists()
    }

    @Test fun syncPanelRegistersAndShowsSignedInState() {
        val fake = object : com.focusflow.network.FocusSyncApi {
            override suspend fun register(serverUrl: String, username: String, password: String) =
                com.focusflow.core.AuthResponse("user-1", "fake-token")
            override suspend fun login(serverUrl: String, username: String, password: String) =
                com.focusflow.core.AuthResponse("user-1", "fake-token")
            override fun transport(serverUrl: String, token: String) = throw UnsupportedOperationException()
        }
        show(sync = com.focusflow.sync.SyncCoordinator(database, fake))
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("账号与同步").performClick()
        compose.onNodeWithTag("sync_server").performScrollTo().performTextInput("http://10.0.2.2:8080")
        compose.onNodeWithTag("sync_username").performScrollTo().performTextInput("alice")
        compose.onNodeWithTag("sync_password").performScrollTo().performTextInput("password123")
        compose.onNodeWithTag("sync_register").performScrollTo().performClick()
        compose.waitUntil(10000) { runBlocking { database.focusDao().syncState()?.token == "fake-token" } }
        compose.onNodeWithText("已登录：alice").assertExists()
        compose.onNodeWithTag("sync_now").assertExists()
    }

    @Test fun mediumLayoutUsesRailAndPaneWithoutBottomBar() {
        show(700)
        compose.onNodeWithTag("navigation_rail").assertExists()
        compose.onNodeWithTag("supporting_pane").assertExists()
        compose.onNodeWithTag("bottom_navigation").assertDoesNotExist()
    }

    @Test fun expandedSelectsFirstTaskInDetailPane() {
        show(1100)
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput("First task")
        compose.onNodeWithTag("save_task").performClick()
        compose.waitUntil(10000) { model.state.value.data.tasks.size == 1 && model.state.value.editor == null }
        compose.waitUntil(10000) { runBlocking { database.focusDao().task(model.state.value.data.tasks.single().id) } != null }
        compose.onNodeWithText("选择一项任务，查看计划与专注进度。").assertDoesNotExist()
        compose.onNodeWithText("累计专注：0 / 25 分钟").assertExists()
    }

    @Test fun landscapeShortHeightKeepsRailVisible() {
        show(1100, 390)
        compose.onNodeWithTag("navigation_rail").assertExists()
        compose.onNodeWithTag("supporting_pane").assertExists()
        compose.onNodeWithText("FocusFlow").assertExists()
    }

    @Test fun guardModeSelectionShowsEffectiveModeAndDegradesWithoutPermissions() {
        val full = com.focusflow.core.GuardCapabilities(accessibilityGranted = true, usageAccessGranted = true, screenPinningAvailable = true)
        show(guardCapabilities = { full })
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput("Guard")
        compose.onNodeWithTag("save_task").performClick()
        compose.waitUntil(10000) { model.state.value.data.tasks.size == 1 && model.state.value.editor == null }
        val id = model.state.value.data.tasks.single().id
        compose.onNodeWithTag("quick_focus_$id").performClick()
        compose.onNodeWithTag("guard_mode_STRICT").performScrollTo().performClick()
        compose.onNodeWithText("严格模式：离开白名单应用会收到回到专注的提醒。").assertExists()
        // 极致只依赖屏幕固定：无障碍/Usage 缺失时仍为极致
        show(guardCapabilities = { com.focusflow.core.GuardCapabilities(screenPinningAvailable = true) }, onOpenGuardSetup = { })
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithText("专注").performClick()
        compose.onNodeWithTag("guard_mode_EXTREME").performScrollTo().performClick()
        compose.onNodeWithText("极致模式：使用系统屏幕固定，开始后无法退出，直到计时结束自动解锁。").assertExists()
        // 严格缺无障碍时降级为普通并提示向导入口
        compose.onNodeWithTag("guard_mode_STRICT").performScrollTo().performClick()
        compose.onNodeWithText("当前权限下按普通模式计时：可随时离开，不限制其他应用。").assertExists()
        compose.onNodeWithText("去开启专注防护").performScrollTo().assertExists()
    }

    @Test fun settingsPageShowsMenuAndGuardSubpage() {
        show(guardCapabilities = { com.focusflow.core.GuardCapabilities(screenPinningAvailable = true) }, onOpenGuardSetup = { }, onEnableReminders = { })
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("账号与同步").assertExists()
        compose.onNodeWithText("专注防护").assertExists()
        compose.onNodeWithText("外观与反馈").assertExists()
        compose.onNodeWithText("关于").performClick()
        compose.onNodeWithText("用户协议").assertExists()
        compose.onNodeWithText("隐私政策").assertExists()
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("专注防护").performClick()
        compose.onNodeWithText("专注防护设置").assertExists()
    }

    @Test fun extremeRoundDrivesGuardFromSessionState() {
        var appliedExtreme = 0
        show(
            guardCapabilities = { com.focusflow.core.GuardCapabilities(accessibilityGranted = true, usageAccessGranted = true, screenPinningAvailable = true) },
            onGuardModeApplied = { if (it == com.focusflow.core.FocusMode.EXTREME) appliedExtreme++ },
        )
        runBlocking {
            repository.saveTask(com.focusflow.core.TaskDraft(
                title = "Extreme round", targetFocusMinutes = 25,
                preferredFocusMode = com.focusflow.core.FocusMode.EXTREME))
        }
        compose.waitUntil(10000) { model.state.value.data.tasks.size == 1 }
        val task = model.state.value.data.tasks.single()
        compose.onNodeWithText("任务").performClick()
        compose.onNodeWithTag("quick_focus_${task.id}").performClick()
        // 极致模式不直开：先经准备页，再经应用内确认弹窗
        compose.onNodeWithText("准备好，专注一件事").assertExists()
        compose.onNodeWithTag("start_focus").performScrollTo().performClick()
        compose.onNodeWithText("开始极致专注？").assertExists()
        compose.onNodeWithText("确认开始").performClick()
        compose.waitUntil(10000) { focus.state.value.run?.session?.strictMode == com.focusflow.core.FocusMode.EXTREME }
        // 守护由会话状态驱动应用（同一驱动覆盖休息后的自动下一轮与进程恢复）
        compose.waitUntil(10000) { appliedExtreme >= 1 }
    }
}
