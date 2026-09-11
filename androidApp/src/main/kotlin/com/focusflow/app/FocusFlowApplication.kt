package com.focusflow.app

import android.app.Application
import com.focusflow.database.TaskRepository
import com.focusflow.database.openDatabase

class FocusFlowApplication : Application() {
    private val database by lazy { openDatabase(this) }
    val tasks by lazy { TaskRepository(database) }
}
