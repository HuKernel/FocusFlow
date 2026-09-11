package com.focusflow.app

import android.app.Application
import com.focusflow.database.TaskRepository
import com.focusflow.database.FocusRepository
import com.focusflow.core.AndroidFocusClock
import com.focusflow.database.openDatabase
import kotlinx.coroutines.flow.first
import com.focusflow.network.HttpFocusSyncApi
import com.focusflow.sync.SyncCoordinator

class FocusFlowApplication : Application() {
    private val database by lazy { openDatabase(this) }
    val tasks by lazy { TaskRepository(database) }
    val focus by lazy { FocusRepository(database, AndroidFocusClock(this), AndroidFocusAlarm(this)) }
    val feedback by lazy { androidFeedback(this) }
    val sync by lazy { SyncCoordinator(database, HttpFocusSyncApi()) }
    val guardCapabilities: com.focusflow.core.GuardCapabilities get() = GuardPrefs.capabilities(this)
    suspend fun todayFocusMinutes(): Long = database.focusDao().observeCompletedSessions()
        .first().filter { com.focusflow.core.localDateAt(it.session.endedAt ?: 0) == com.focusflow.core.todayDate() }
        .sumOf { it.session.actualDuration } / 60000
}
