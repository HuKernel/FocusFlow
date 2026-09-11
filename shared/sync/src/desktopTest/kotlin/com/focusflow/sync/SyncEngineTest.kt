package com.focusflow.sync

import com.focusflow.core.FocusAlarm
import com.focusflow.core.FocusClock
import com.focusflow.core.PullResponse
import com.focusflow.core.SyncTransport
import com.focusflow.core.SyncEvent
import com.focusflow.core.TaskDraft
import com.focusflow.core.TimerType
import com.focusflow.database.FocusDatabase
import com.focusflow.database.FocusRepository
import com.focusflow.database.LocalIdentity
import com.focusflow.database.TaskRepository
import com.focusflow.database.openDatabase
import com.focusflow.server.Store
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SyncEngineTest {
    private class Clock : FocusClock {
        var time = 1_000_000L
        override fun epochMillis() = time
        override fun monotonicMillis() = time
        override fun bootId() = "boot"
    }
    private class NoAlarm : FocusAlarm {
        override val available = false
        override fun schedule(sessionId: String, delayMillis: Long) {}
        override fun cancel() {}
        override fun completed(taskTitle: String, isBreak: Boolean) {}
    }

    private class Device(name: String, store: Store, userId: String) {
        val database: FocusDatabase = openDatabase(Files.createTempDirectory("sync-$name").resolve("db").toFile())
        val clock = Clock()
        val tasks = TaskRepository(database, now = { clock.time })
        val focus = FocusRepository(database, clock, NoAlarm())
        val engine = SyncEngine(database, StoreTransport(store, userId))

        init {
            runBlocking { database.focusDao().insertIdentity(LocalIdentity(userId = userId, deviceId = "device-$name")) }
        }
    }

    /** 直接复用 server 模块的真实投影存储作为传输层，覆盖幂等/cursor/回声排除语义。 */
    private class StoreTransport(private val store: Store, private val userId: String) : SyncTransport {
        override suspend fun push(events: List<SyncEvent>): Int = store.push(userId, events.first().deviceId, events)
        override suspend fun pull(cursor: Long, deviceId: String): PullResponse = store.pull(userId, deviceId, cursor)
    }

    @Test fun offlinePhoneAndTabletMergeTo55Minutes() = runBlocking {
        val store = Store("jdbc:h2:mem:sync-${System.nanoTime()};DB_CLOSE_DELAY=-1")
        val userId = store.register("focus-user", "password123", 0)
        val phone = Device("phone", store, userId)
        val tablet = Device("tablet", store, userId)

        // 离线：手机创建任务并完成 25 分钟专注
        val taskId = phone.tasks.saveTask(TaskDraft("Read Kotlin"))
        phone.focus.start(taskId, 0, TimerType.STOPWATCH)
        phone.clock.time += 25 * 60_000
        phone.focus.complete(phone.database.focusDao().activeFocus()!!.session.id)
        phone.focus.dismiss(phone.database.focusDao().activeFocus()!!.session.id)

        // 手机联网推走事件，平板联网拉到任务，随后双方再次离线
        phone.engine.synchronize()
        tablet.engine.synchronize()
        assertTrue(tablet.tasks.data.first().tasks.any { it.id == taskId })
        tablet.focus.start(taskId, 0, TimerType.STOPWATCH)
        tablet.clock.time += 30 * 60_000
        tablet.focus.complete(tablet.database.focusDao().activeFocus()!!.session.id)
        tablet.focus.dismiss(tablet.database.focusDao().activeFocus()!!.session.id)

        // 双方先后联网：手机推走本地事件，平板推走会话并拉取，手机最后拉回平板的会话
        val phoneSummary = phone.engine.synchronize()
        val tabletSummary = tablet.engine.synchronize()
        val finalPull = phone.engine.synchronize()

        val phoneSessions = phone.database.focusDao().observeCompletedSessions().first()
        val tabletSessions = tablet.database.focusDao().observeCompletedSessions().first()
        assertEquals(2, phoneSessions.size, "phone should merge both devices' sessions")
        assertEquals(2, tabletSessions.size, "tablet should merge both devices' sessions")
        assertEquals(55 * 60_000L, phone.database.focusDao().completedMillis(taskId))
        assertEquals(55 * 60_000L, tablet.database.focusDao().completedMillis(taskId))
        assertTrue(tabletSummary.pushed > 0 && finalPull.pulled > 0, "tablet pushes its session and phone pulls it back")

        // 再次同步：幂等，无新变化
        val again = phone.engine.synchronize()
        assertEquals(0, again.pushed)
        assertEquals(0, again.pulled)
        store.close()
    }

    @Test fun tombstoneDeletesTaskOnOtherDevice() = runBlocking {
        val store = Store("jdbc:h2:mem:tomb-${System.nanoTime()};DB_CLOSE_DELAY=-1")
        val userId = store.register("tomb-user", "password123", 0)
        val phone = Device("phone", store, userId)
        val tablet = Device("tablet", store, userId)

        val taskId = phone.tasks.saveTask(TaskDraft("To delete"))
        phone.engine.synchronize()
        tablet.engine.synchronize()

        phone.tasks.deleteTask(phone.database.focusDao().task(taskId)!!.task)
        phone.engine.synchronize()
        tablet.engine.synchronize()

        assertTrue(tablet.tasks.data.first().tasks.none { it.id == taskId }, "tombstone must hide the task on other device")
        assertTrue(phone.database.focusDao().task(taskId)!!.task.deletedAt != null)
        assertEquals(0, tablet.database.focusDao().completedMillis(taskId))
        store.close()
    }
}
