package com.focusflow.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class AdaptiveSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactNavigationOpensFocus() {
        compose.setContent { Box(Modifier.requiredSize(390.dp, 700.dp)) { FocusApp() } }
        compose.onNodeWithTag("bottom_navigation").assertExists()
        compose.onNodeWithTag("navigation_rail").assertDoesNotExist()
        compose.onNodeWithText("专注").performClick()
        compose.onNodeWithText("为下一次专注留出空间").assertExists()
    }

    @Test fun expandedWindowShowsSupportingPane() {
        compose.setContent { Box(Modifier.requiredSize(1100.dp, 700.dp)) { FocusApp() } }
        compose.onNodeWithTag("navigation_rail").assertExists()
        compose.onNodeWithTag("supporting_pane").assertExists()
        compose.onNodeWithTag("bottom_navigation").assertDoesNotExist()
        compose.onNodeWithText("任务详情").assertExists()
    }
}
