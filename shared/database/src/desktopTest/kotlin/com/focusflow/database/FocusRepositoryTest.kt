package com.focusflow.database

import com.focusflow.core.*
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class FocusRepositoryTest {
    private class Clock : FocusClock {
        var time = 1_000_000L
        override fun epochMillis() = time
        override fun monotonicMillis() = time
        override fun bootId() = "boot"
    }
    private class Alarm : FocusAlarm {
        var notices = 0
        var scheduled = false
        override val available = false
        override fun schedule(sessionId: String, delayMillis: Long) { scheduled = true }
        override fun cancel() { scheduled = false }
        override fun completed(taskTitle: String, isBreak: Boolean) { notices++ }
    }

    @Test fun processRestoreSettlesExactlyOnceAndProtectsActiveTask() = runBlocking {
        val file = Files.createTempDirectory("focusflow-focus").resolve("focus.db").toFile()
        var db = openDatabase(file)
        val clock = Clock()
        val alarm = Alarm()
        try {
            val taskId = TaskRepository(db).saveTask(TaskDraft("Read"))
            var focus = FocusRepository(db, clock, alarm)
            focus.start(taskId, 60_000, breakDuration = 300_000)
            assertEquals(300_000L, db.focusDao().activeFocus()!!.breakDuration)
            val id = db.focusDao().activeFocus()!!.session.id
            assertTrue(alarm.scheduled)
            assertFails { focus.start(taskId, 60_000) }
            assertFails { TaskRepository(db).deleteTask(db.focusDao().task(taskId)!!.task) }
            clock.time += 20_000
            focus.pause(id)
            assertFalse(alarm.scheduled)
            db.close()
            db = openDatabase(file)
            focus = FocusRepository(db, clock, alarm)
            clock.time += 30_000
            assertEquals(TimerState.PAUSED, focus.restore()!!.anchor.state)
            focus.resume(id)
            clock.time += 40_000
            focus.reconcile(id)
            focus.reconcile(id)
            focus.complete(id)
            assertEquals(60_000L, db.focusDao().completedMillis(taskId))
            assertEquals(30_000L, db.focusDao().session(id)!!.session.pausedDuration)
            assertEquals(1, db.focusDao().events().count { it.event.entityType == "FocusSession" })
            assertEquals(1, alarm.notices)
            focus.startBreak(id)
            clock.time += 300_000
            focus.reconcile(id)
            assertEquals(TimerState.SESSION_FINISHED, db.focusDao().activeFocus()!!.anchor.state)
            assertEquals(60_000L, db.focusDao().completedMillis(taskId))
            focus.dismiss(id)
            assertNull(db.focusDao().activeFocus())
            focus.start(taskId, 0, TimerType.STOPWATCH)
            val newSession = db.focusDao().activeFocus()!!.session.id
            focus.cancel(id) // stale UI action cannot cancel a new session.
            assertEquals(TimerState.FOCUSING, db.focusDao().activeFocus()!!.anchor.state)
            clock.time += 10_000
            focus.cancel(newSession)
            assertEquals(SessionStatus.CANCELLED, db.focusDao().session(newSession)!!.session.status)
            assertEquals(60_000L, db.focusDao().completedMillis(taskId))
        } finally { db.close(); file.parentFile.deleteRecursively() }
    }

    @Test fun notificationFailureDoesNotPreventTimerAndOutboxFailureRollsBackSettlement() = runBlocking {
        val file = Files.createTempDirectory("focusflow-focus-failure").resolve("focus.db").toFile()
        val db = openDatabase(file)
        val clock = Clock()
        val alarm = object : FocusAlarm {
            override val available = false
            override fun schedule(sessionId: String, delayMillis: Long) { throw SecurityException() }
            override fun cancel() { throw SecurityException() }
            override fun completed(taskTitle: String, isBreak: Boolean) { throw SecurityException() }
        }
        try {
            val task = TaskRepository(db).saveTask(TaskDraft("Read"))
            val focus = FocusRepository(db, clock, alarm, newId = { "same-id" })
            focus.start(task, 60_000)
            clock.time += 60_000
            assertFails { focus.reconcile() }
            assertEquals(TimerState.FOCUSING, db.focusDao().activeFocus()!!.anchor.state)
            assertEquals(0L, db.focusDao().completedMillis(task))
            FocusRepository(db, clock, alarm).restore()
            assertEquals(60_000L, db.focusDao().completedMillis(task))
            assertEquals(1, db.focusDao().observeCompletedSessions().first().size)
        } finally { db.close(); file.parentFile.deleteRecursively() }
    }
}
