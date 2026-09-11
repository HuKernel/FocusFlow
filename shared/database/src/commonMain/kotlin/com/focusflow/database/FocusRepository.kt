package com.focusflow.database

import androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE
import androidx.room.useWriterConnection
import com.focusflow.core.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FocusRepository(
    private val database: FocusDatabase,
    clock: FocusClock,
    private val alarm: FocusAlarm = NoFocusAlarm,
    private val newId: () -> String = { Uuid.random().toString() },
) : TimerController {
    private val clock = clock
    private val dao = database.focusDao()
    val engine = TimerEngine(clock)
    val runs = dao.observeActiveFocus().map { it?.toRun() }
    val remindersAvailable: Boolean get() = alarm.available
    // Serialize platform alarm updates with commits, so an old pause cannot cancel a new start's alarm.
    private val mutations = Mutex()

    private suspend fun <T> write(block: suspend () -> T): T = database.useWriterConnection { connection ->
        connection.withTransaction(IMMEDIATE) { block() }
    }

    override suspend fun start(taskId: String, plannedDuration: Long, type: TimerType) = mutations.withLock {
        val run = write {
            val existing = dao.activeFocus()?.toRun()
            require(existing == null || existing.anchor.state in listOf(TimerState.FOCUS_COMPLETED, TimerState.CANCELLED, TimerState.SESSION_FINISHED)) { "已有专注或休息正在进行，请先结束" }
            val task = dao.task(taskId)?.task
            require(task != null && task.deletedAt == null && task.status in listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS)) { "请选择未完成的任务" }
            val identity = dao.identity() ?: LocalIdentity(userId = newId(), deviceId = newId()).also { dao.insertIdentity(it) }
            val timestamp = clock.epochMillis()
            val session = FocusSession(newId(), task.userId, task.id, identity.deviceId, identity.deviceId,
                type = type, plannedDuration = if (type == TimerType.STOPWATCH) 0 else plannedDuration, startedAt = timestamp)
            val started = engine.start(session, task.title)
            if (task.status == TaskStatus.TODO) {
                val changed = task.copy(status = TaskStatus.IN_PROGRESS, updatedAt = timestamp, revision = task.revision + 1)
                dao.upsertTask(TaskEntity(changed))
                dao.insertEvent(SyncEventEntity(SyncEvent(newId(), identity.deviceId, "Task", task.id, SyncOperation.UPDATE, Json.encodeToString(changed), timestamp)))
            }
            dao.saveActiveFocus(ActiveFocusEntity(started))
            started
        }
        updateAlarm(run)
    }

    private suspend fun transition(sessionId: String?, action: (FocusRun) -> FocusRun): FocusRun? = mutations.withLock {
        val (before, after) = write {
            val before = dao.activeFocus()?.toRun() ?: return@write null to null
            if (sessionId != null && before.session.id != sessionId) return@write before to before
            val after = action(before)
            if (before != after) {
                if (before.session.status in listOf(SessionStatus.ACTIVE, SessionStatus.PAUSED) && after.session.status in listOf(SessionStatus.COMPLETED, SessionStatus.CANCELLED)) {
                    val inserted = dao.insertSession(SessionEntity(after.session))
                    check(inserted != -1L) { "会话记录已存在，无法重复结算" }
                    dao.insertEvent(SyncEventEntity(SyncEvent(newId(), after.session.deviceId, "FocusSession", after.session.id,
                        SyncOperation.CREATE, Json.encodeToString(after.session), clock.epochMillis())))
                }
                dao.saveActiveFocus(ActiveFocusEntity(after))
            }
            before to after
        }
        if (before != after) {
            updateAlarm(after)
            if (after?.anchor?.state == TimerState.FOCUS_COMPLETED ||
                before?.anchor?.state == TimerState.BREAKING && after?.anchor?.state == TimerState.SESSION_FINISHED) {
                runCatching { alarm.completed(after!!.taskTitle, after.anchor.state == TimerState.SESSION_FINISHED) }
            }
        }
        after
    }

    suspend fun reconcile(sessionId: String? = null): FocusRun? = transition(sessionId, engine::recover)
    override suspend fun restore(): FocusRun? {
        reconcile()
        return mutations.withLock { dao.activeFocus()?.toRun().also(::updateAlarm) }
    }
    override suspend fun pause(sessionId: String) { transition(sessionId) { if (it.anchor.state == TimerState.FOCUSING) engine.pause(it) else it } }
    override suspend fun resume(sessionId: String) { transition(sessionId) { if (it.anchor.state == TimerState.PAUSED) engine.resume(it) else it } }
    override suspend fun complete(sessionId: String) { transition(sessionId) { if (it.session.status in listOf(SessionStatus.ACTIVE, SessionStatus.PAUSED)) engine.complete(it) else it } }
    override suspend fun cancel(sessionId: String) { transition(sessionId) { if (it.session.status in listOf(SessionStatus.ACTIVE, SessionStatus.PAUSED)) engine.cancel(it) else it } }
    suspend fun startBreak(sessionId: String) { transition(sessionId, engine::startBreak) }
    suspend fun skipBreak(sessionId: String) { transition(sessionId, engine::skipBreak) }
    suspend fun dismiss(sessionId: String) = mutations.withLock {
        write {
            val run = dao.activeFocus()?.toRun() ?: return@write
            if (run.session.id != sessionId) return@write
            require(run.anchor.state in listOf(TimerState.FOCUS_COMPLETED, TimerState.CANCELLED, TimerState.SESSION_FINISHED)) { "请先结束专注或休息" }
            dao.clearActiveFocus()
        }
        updateAlarm(dao.activeFocus()?.toRun())
    }

    private fun updateAlarm(run: FocusRun?) {
        // Missing notification/alarm capability must never undo a successful database write.
        runCatching {
            if (run != null && (run.anchor.state == TimerState.BREAKING || run.anchor.state == TimerState.FOCUSING && run.session.type == TimerType.COUNTDOWN))
                alarm.schedule(run.session.id, engine.remaining(run))
            else alarm.cancel()
        }
    }
}
