package com.focusflow.server

import com.focusflow.core.AuthRequest
import com.focusflow.core.AuthResponse
import com.focusflow.core.PullResponse
import com.focusflow.core.PushRequest
import com.focusflow.core.PushResponse
import com.focusflow.core.SyncEvent
import com.focusflow.core.SyncOperation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {
    private fun testServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(Store("jdbc:h2:mem:${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1"), "test-secret") }
        block()
    }

    private fun event(id: String, entityId: String, payload: String = """{"id":"$entityId","revision":1}""") =
        SyncEvent(id, "device-a", "Task", entityId, SyncOperation.CREATE, payload, 0)

    @Test fun registerLoginAndRejectWrongPassword() = testServer {
        val client = createClient { }
        val register = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AuthRequest("alice", "password123")))
        }
        assertEquals(HttpStatusCode.Created, register.status)
        assertTrue(Json.decodeFromString<AuthResponse>(register.bodyAsText()).token.isNotEmpty())
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AuthRequest("alice", "password123")))
        }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AuthRequest("alice", "wrongpass123")))
        }.status)
        assertEquals(HttpStatusCode.Conflict, client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AuthRequest("alice", "password4567")))
        }.status)
    }

    @Test fun pushIsIdempotentAndPullFollowsCursor() = testServer {
        val client = createClient { }
        val auth = Json.decodeFromString<AuthResponse>(client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AuthRequest("bob", "password123")))
        }.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/sync/pull?cursor=0&deviceId=x").status)

        suspend fun push(events: List<SyncEvent>): Int = Json.decodeFromString<PushResponse>(client.post("/api/v1/sync/push") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(PushRequest("device-a", events)))
        }.bodyAsText()).accepted

        assertEquals(1, push(listOf(event("e1", "task-1"))))
        // 同一事件重推：幂等，不再计数、不重复投影
        assertEquals(0, push(listOf(event("e1", "task-1"))))

        suspend fun pull(deviceId: String, cursor: Long): PullResponse = Json.decodeFromString<PullResponse>(
            client.get("/api/v1/sync/pull?cursor=$cursor&deviceId=$deviceId") {
                header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            }.bodyAsText())

        // 自己的 change 不回声给自己
        assertTrue(pull("device-a", 0).changes.isEmpty())
        // 其他设备按 cursor 增量拉取
        val otherPull = pull("device-b", 0)
        assertEquals(1, otherPull.changes.size)
        assertEquals("task-1", otherPull.changes.single().entityId)
        assertTrue(otherPull.nextCursor > 0)
        val emptyPull = pull("device-b", otherPull.nextCursor)
        assertTrue(emptyPull.changes.isEmpty())
        assertEquals(otherPull.nextCursor, emptyPull.nextCursor)
    }
}
