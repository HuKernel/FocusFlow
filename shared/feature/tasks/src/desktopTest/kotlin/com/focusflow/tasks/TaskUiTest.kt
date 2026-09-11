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
import org.junit.*

class TaskUiTest {
    @get:Rule val compose = createComposeRule()
    private val directory = Files.createTempDirectory("focusflow-ui").toFile()
    private val database = openDatabase(directory.resolve("ui.db"))
    private val model = TasksViewModel(TaskRepository(database))
    private val clock = object : FocusClock {
        var time = 0L
        override fun epochMillis() = 1_800_000_000_000L + time
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

    private fun show(width: Int = 390) {
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                Box(Modifier.requiredSize(width.dp, 760.dp)) { FocusApp(model, focus) }
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
}
