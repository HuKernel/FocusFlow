package com.focusflow.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import com.focusflow.core.TaskStatus
import com.focusflow.database.*
import java.nio.file.Files
import org.junit.*

class TaskUiTest {
    @get:Rule val compose = createComposeRule()
    private val directory = Files.createTempDirectory("focusflow-ui").toFile()
    private val database = openDatabase(directory.resolve("ui.db"))
    private val model = TasksViewModel(TaskRepository(database))
    private val store = ViewModelStore().apply { put("tasks", model) }

    @After fun close() {
        compose.runOnIdle { store.clear() }
        database.close()
        directory.deleteRecursively()
    }

    private fun show(width: Int = 390) {
        compose.setContent { Box(Modifier.requiredSize(width.dp, 760.dp)) { FocusApp(model) } }
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
        compose.onNodeWithText("为下一次专注留出空间").assertExists()
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
}
