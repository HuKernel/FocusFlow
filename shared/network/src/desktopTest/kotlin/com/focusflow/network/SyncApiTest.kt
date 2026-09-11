package com.focusflow.network

import com.focusflow.core.SyncEvent
import com.focusflow.core.SyncOperation
import com.focusflow.server.Store
import com.focusflow.server.module
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SyncApiTest {
    @Test fun httpTransportTalksToRealServer() = runBlocking {
        val store = Store("jdbc:h2:mem:net-${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        val server = embeddedServer(CIO, port = 0) { module(store, "test-secret") }.start()
        try {
            val url = "http://127.0.0.1:${server.engine.resolvedConnectors().single().port}"
            val api = HttpFocusSyncApi()
            val auth = api.register(url, "carol", "password123")
            assertTrue(auth.token.isNotEmpty())

            val transport = api.transport(url, auth.token)
            val event = SyncEvent("e1", "device-a", "Task", "t1", SyncOperation.CREATE, """{"id":"t1","revision":1}""", 0)
            assertEquals(1, transport.push(listOf(event)))
            assertEquals(0, transport.push(listOf(event)), "re-push must be idempotent over HTTP")
            assertEquals(1, transport.pull(0, "device-b").changes.size)
            assertTrue(transport.pull(0, "device-a").changes.isEmpty(), "echo exclusion over HTTP")
        } finally {
            server.stop(1000, 2000)
            store.close()
        }
    }
}
