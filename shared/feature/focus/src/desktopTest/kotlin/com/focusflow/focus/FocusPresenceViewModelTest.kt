package com.focusflow.focus

import com.focusflow.core.FocusClock
import com.focusflow.core.PresenceKind
import com.focusflow.core.PresenceMessage
import com.focusflow.core.PresenceSnapshot
import com.focusflow.core.RemoteFocus
import com.focusflow.core.TaskDraft
import com.focusflow.core.TimerState
import com.focusflow.core.TimerType
import com.focusflow.database.FocusRepository
import com.focusflow.database.LocalIdentity
import com.focusflow.database.TaskRepository
import com.focusflow.database.openDatabase
import com.focusflow.network.FocusPresence
import java.nio.file.Files
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class FocusPresenceViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private class Clock : FocusClock {
        var time = 1_000_000L
        override fun epochMillis() = time
        override fun monotonicMillis() = time
        override fun bootId() = "boot"
    }
    private class FakePresence(val device: String) : FocusPresence {
        val sent = mutableListOf<PresenceMessage>()
        val flow = MutableSharedFlow<PresenceMessage>(replay = 1, extraBufferCapacity = 16)
        override val incoming: SharedFlow<PresenceMessage> = flow
        var started = false
        var takeoverOf: String? = null
        var takeoverSnapshot: PresenceSnapshot? = null
        override fun start(scope: CoroutineScope) { started = true }
        override fun stop() {}
        override suspend fun send(message: PresenceMessage) { sent += message }
        override suspend fun takeover(sessionId: String) = takeoverOf.let { if (it == sessionId) takeoverSnapshot else null }
    }

    private class Device(val name: String, val clock: Clock) {
        val database = openDatabase(Files.createTempDirectory("presence-$name").resolve("db").toFile())
        val presence = FakePresence("device-$name")
        init { kotlinx.coroutines.runBlocking { database.focusDao().insertIdentity(LocalIdentity(userId = "user-1", deviceId = presence.device)) } }
        val tasks = TaskRepository(database, now = { clock.time })
        val model = FocusViewModel(FocusRepository(database, clock), presence) { clock.time }
        fun close() = database.close()
    }

    private lateinit var phone: Device
    private lateinit var tablet: Device

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        phone = Device("phone", Clock())
        tablet = Device("tablet", Clock())
    }
    @After fun tearDown() { Dispatchers.resetMain(); phone.close(); tablet.close() }

    private fun snapshot(sessionId: String, owner: String, kind: PresenceKind, elapsedAtAnchor: Long, anchorAt: Long, planned: Long = 25 * 60_000) =
        PresenceSnapshot(sessionId, "task-x", "Read Kotlin", owner, kind, TimerState.FOCUSING, anchorAt, planned, elapsedAtAnchor, 0)

    private suspend fun awaitRun(model: FocusViewModel) = kotlinx.coroutines.withTimeout(5000) { model.state.first { it.run != null } }
    private suspend fun awaitSent(presence: FakePresence, count: Int) = kotlinx.coroutines.withTimeout(5000) { while (presence.sent.size < count) kotlinx.coroutines.delay(10) }

    @Test fun ownerReportsLifecycleAndObserverEstimatesThenTakesOver() = kotlinx.coroutines.runBlocking {
        val taskId = phone.tasks.saveTask(TaskDraft("Read Kotlin"))
        phone.model.start(taskId, 25 * 60_000, TimerType.COUNTDOWN)
        awaitRun(phone.model)
        awaitSent(phone.presence, 1)
        assertEquals(PresenceKind.FOCUS_STARTED, phone.presence.sent.first().kind)
        assertEquals("device-phone", phone.presence.sent.first().snapshot?.ownerDeviceId)

        phone.model.pause(phone.model.state.value.run!!.session.id)
        awaitSent(phone.presence, 2)
        assertEquals(PresenceKind.FOCUS_PAUSED, phone.presence.sent.last().kind)

        // Observer 收到 owner 状态，进入观察面板
        val phoneSessionId = phone.model.state.value.run!!.session.id
        val started = snapshot(phoneSessionId, "device-phone", PresenceKind.FOCUS_STARTED, elapsedAtAnchor = 60_000, anchorAt = phone.clock.time)
        tablet.presence.flow.emit(PresenceMessage(PresenceKind.FOCUS_STARTED, "device-phone", started))
        kotlinx.coroutines.withTimeout(5000) { tablet.model.state.first { it.remote != null } }
        val remote: RemoteFocus = assertNotNull(tablet.model.state.value.remote)
        assertEquals("device-phone", remote.snapshot.ownerDeviceId)

        // Continue on this device：takeover 走 HTTP，成功后收到 OWNER_CHANGED 才本地接管
        tablet.presence.takeoverOf = phoneSessionId
        tablet.presence.takeoverSnapshot = snapshot(phoneSessionId, "device-tablet", PresenceKind.OWNER_CHANGED, 60_000, tablet.clock.time)
        tablet.model.takeover()
        tablet.presence.flow.emit(PresenceMessage(PresenceKind.OWNER_CHANGED, "device-tablet", tablet.presence.takeoverSnapshot))
        awaitRun(tablet.model)
        val adopted = assertNotNull(tablet.model.state.value.run, "new owner must adopt the session locally")
        kotlinx.coroutines.withTimeout(5000) { tablet.model.state.first { it.remote == null } }
        assertEquals(phoneSessionId, adopted.session.id)
        assertTrue(adopted.session.plannedDuration == 25 * 60_000L)
        assertNull(tablet.model.state.value.remote)

        // 旧 owner 收到 OWNER_CHANGED：立即让位，本地运行态清空且不结算
        phone.presence.flow.emit(PresenceMessage(PresenceKind.OWNER_CHANGED, "device-tablet", tablet.presence.takeoverSnapshot))
        kotlinx.coroutines.withTimeout(5000) { phone.model.state.first { it.run == null } }
        assertNull(phone.model.state.value.run)
        assertTrue(phone.database.focusDao().observeCompletedSessions().first().none { it.session.id == phoneSessionId },
            "released session must not settle on the old owner")
    }

    @Test fun ownerCompleteReportsTerminalKind() = kotlinx.coroutines.runBlocking {
        val taskId = phone.tasks.saveTask(TaskDraft("Stopwatch"))
        phone.model.start(taskId, 0, TimerType.STOPWATCH)
        awaitRun(phone.model)
        awaitSent(phone.presence, 1)
        phone.clock.time += 60_000
        phone.model.complete(phone.model.state.value.run!!.session.id)
        kotlinx.coroutines.withTimeout(5000) { phone.model.state.first { it.run?.session?.status == com.focusflow.core.SessionStatus.COMPLETED } }
        awaitSent(phone.presence, 2)
        assertEquals(PresenceKind.FOCUS_COMPLETED, phone.presence.sent.last().kind)
    }
}
