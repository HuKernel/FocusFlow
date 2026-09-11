package com.focusflow.server

import com.focusflow.core.AuthRequest
import com.focusflow.core.AuthResponse
import com.focusflow.core.PresenceKind
import com.focusflow.core.PresenceMessage
import com.focusflow.core.PresenceSnapshot
import com.focusflow.core.TimerState
import com.focusflow.core.TimerType
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.netty.Netty as ServerEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PresenceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun snapshot(owner: String, kind: PresenceKind = PresenceKind.FOCUS_STARTED) = PresenceSnapshot(
        "session-1", "task-1", "Read Kotlin", owner, kind, TimerState.FOCUSING,
        anchorEpochMillis = 1_000_000, plannedDuration = 25 * 60_000, elapsedAtAnchor = 0, pausedDuration = 0, type = TimerType.COUNTDOWN)

    @Test fun ownerPublishesObserverReceivesHandoffTransfersAtomically() = runBlocking {
        val store = Store("jdbc:h2:mem:presence-${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        val hub = PresenceHub()
        val server = embeddedServer(ServerEngine, port = 0) { module(store, "test-secret", hub) }.start()
        val wsUrl = "ws://127.0.0.1:${server.engine.resolvedConnectors().single().port}"
        val httpUrl = "http://127.0.0.1:${server.engine.resolvedConnectors().single().port}"
        val client = HttpClient(CIO) { install(WebSockets) }
        try {
            val auth = json.decodeFromString<AuthResponse>(client.post("$httpUrl/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(AuthRequest("presence-user", "password123")))
            }.bodyAsText())

            val ownerMessages = Channel<PresenceMessage>(10)
            val observerMessages = Channel<PresenceMessage>(10)
            val ownerReady = Channel<Unit>(1)
            val observerReady = Channel<Unit>(1)
            val go = Channel<Unit>(1)

            val ownerJob = launch(Dispatchers.IO) {
                client.webSocket("$wsUrl/api/v1/ws?token=${auth.token}&deviceId=phone") {
                    val relay = launch { for (frame in incoming) (frame as? Frame.Text)?.let { ownerMessages.send(json.decodeFromString(it.readText())) } }
                    ownerReady.send(Unit)
                    go.receive()
                    send(Frame.Text(json.encodeToString(PresenceMessage(PresenceKind.FOCUS_STARTED, "phone", snapshot("phone")))))
                    relay.join()
                }
            }
            val observerJob = launch(Dispatchers.IO) {
                client.webSocket("$wsUrl/api/v1/ws?token=${auth.token}&deviceId=tablet") {
                    observerReady.send(Unit)
                    for (frame in incoming) (frame as? Frame.Text)?.let { observerMessages.send(json.decodeFromString(it.readText())) }
                }
            }
            withTimeout(5000) { ownerReady.receive(); observerReady.receive() }
            go.send(Unit)

            suspend fun next(channel: Channel<PresenceMessage>) = withTimeout(5000) { channel.receive() }

            val observed = next(observerMessages)
            assertEquals(PresenceKind.FOCUS_STARTED, observed.kind)
            assertEquals("phone", observed.snapshot?.ownerDeviceId)

            // 非 owner 的更新被拒绝，不产生广播
            assertNull(hub.publish(auth.userId, "tablet", PresenceMessage(PresenceKind.FOCUS_PAUSED, "tablet", snapshot("tablet", PresenceKind.FOCUS_PAUSED))))

            // handoff：tablet 通过 HTTP 原子接管，双方收到 OWNER_CHANGED
            val handoff = client.post("$httpUrl/api/v1/focus/handoff") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(HandoffRequest("session-1", "tablet")))
                headers.append("Authorization", "Bearer ${auth.token}")
            }
            assertEquals(HttpStatusCode.OK, handoff.status)
            var ownerSawChange = next(ownerMessages)
            while (ownerSawChange.kind != PresenceKind.OWNER_CHANGED) ownerSawChange = next(ownerMessages) // 跳过自己事件的回声
            assertEquals(PresenceKind.OWNER_CHANGED, ownerSawChange.kind)
            assertEquals("tablet", next(observerMessages).snapshot?.ownerDeviceId)
            assertTrue(hub.snapshotOf(auth.userId)?.ownerDeviceId == "tablet")

            // 终态清空 presence
            assertEquals(PresenceKind.FOCUS_COMPLETED, hub.publish(auth.userId, "tablet",
                PresenceMessage(PresenceKind.FOCUS_COMPLETED, "tablet", snapshot("tablet", PresenceKind.FOCUS_COMPLETED)))?.kind)
            assertNull(hub.snapshotOf(auth.userId))
        } finally {
            client.close(); server.stop(1000, 2000); store.close()
        }
    }
}
