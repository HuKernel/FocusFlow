package com.focusflow.app

import android.app.Application
import com.focusflow.database.TaskRepository
import com.focusflow.database.FocusRepository
import com.focusflow.core.AndroidFocusClock
import com.focusflow.database.openDatabase

class FocusFlowApplication : Application() {
    private val database by lazy { openDatabase(this) }
    val tasks by lazy { TaskRepository(database) }
    val focus by lazy { FocusRepository(database, AndroidFocusClock(this), AndroidFocusAlarm(this)) }
}
