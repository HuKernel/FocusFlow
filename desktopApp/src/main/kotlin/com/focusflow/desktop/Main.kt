package com.focusflow.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.focusflow.database.openDatabase
import com.focusflow.database.TaskRepository
import com.focusflow.tasks.FocusRoute
import java.io.File

fun main() = application {
    val database = remember { openDatabase(File(System.getProperty("user.home"), ".focusflow/focusflow.db")) }
    DisposableEffect(database) { onDispose { database.close() } }
    Window(onCloseRequest = ::exitApplication, title = "FocusFlow", state = rememberWindowState(width = 1100.dp, height = 760.dp)) {
        val repository = remember(database) { TaskRepository(database) }
        FocusRoute(repository)
    }
}
