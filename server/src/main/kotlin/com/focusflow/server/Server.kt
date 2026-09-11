package com.focusflow.server

import com.focusflow.core.AuthRequest
import com.focusflow.core.PresenceMessage
import com.focusflow.core.AuthResponse
import com.focusflow.core.PullResponse
import com.focusflow.core.PushRequest
import com.focusflow.core.PushResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.serialization.json.Json

fun main() {
    val url = System.getenv("DATABASE_URL")
        ?: "jdbc:h2:file:./data/focusflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
    val secret = System.getenv("SERVER_SECRET") ?: "focusflow-dev-secret" // 生产必须通过环境变量覆盖
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(io.ktor.server.netty.Netty, port = port) { module(Store(url), secret) }.start(wait = true)
}

fun Application.module(store: Store, secret: String, hub: PresenceHub = PresenceHub()) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(io.ktor.server.websocket.WebSockets)
    install(Authentication) {
        bearer("auth") {
            authenticate { credential ->
                Auth.verifyToken(credential.token, secret, System.currentTimeMillis())?.let { UserIdPrincipal(it) }
            }
        }
    }
    routing {
        post("/api/v1/auth/register") {
            val request = call.receive<AuthRequest>()
            try {
                val userId = store.register(request.username, request.password, System.currentTimeMillis())
                call.respond(HttpStatusCode.Created, AuthResponse(userId, Auth.issueToken(userId, secret, System.currentTimeMillis())))
            } catch (invalid: IllegalArgumentException) { call.respond(HttpStatusCode.Conflict, mapOf("error" to (invalid.message ?: "注册失败"))) }
        }
        post("/api/v1/auth/login") {
            val request = call.receive<AuthRequest>()
            try {
                val userId = store.login(request.username, request.password)
                call.respond(AuthResponse(userId, Auth.issueToken(userId, secret, System.currentTimeMillis())))
            } catch (invalid: IllegalArgumentException) { call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (invalid.message ?: "登录失败"))) }
        }
        authenticate("auth") {
            post("/api/v1/sync/push") {
                val request = call.receive<PushRequest>()
                val accepted = store.push(call.principal<UserIdPrincipal>()!!.name, request.deviceId, request.events)
                call.respond(PushResponse(accepted, System.currentTimeMillis()))
            }
            get("/api/v1/focus/presence") {
                call.respond(hub.snapshotOf(call.principal<UserIdPrincipal>()!!.name) ?: mapOf<String, String>())
            }
            post("/api/v1/focus/handoff") {
                val request = call.receive<HandoffRequest>()
                val transferred = hub.handoff(call.principal<UserIdPrincipal>()!!.name, request.deviceId, request.sessionId)
                if (transferred == null) call.respond(HttpStatusCode.Conflict, mapOf("error" to "会话不存在或你已是发起设备"))
                else {
                    hub.broadcast(call.principal<UserIdPrincipal>()!!.name, PresenceMessage(com.focusflow.core.PresenceKind.OWNER_CHANGED, transferred.ownerDeviceId, transferred))
                    call.respond(transferred)
                }
            }
            get("/api/v1/sync/pull") {
                val cursor = call.request.queryParameters["cursor"]?.toLongOrNull() ?: 0L
                val deviceId = call.request.queryParameters["deviceId"] ?: ""
                call.respond(store.pull(call.principal<UserIdPrincipal>()!!.name, deviceId, cursor))
            }
        }
        webSocket("/api/v1/ws") {
            val token = call.request.queryParameters["token"]
            val deviceId = call.request.queryParameters["deviceId"] ?: ""
            val userId = token?.let { Auth.verifyToken(it, secret, System.currentTimeMillis()) }
            if (userId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
                return@webSocket
            }
            hub.join(userId, this)?.let { snapshot -> send(Frame.Text(Json.encodeToString(PresenceMessage.serializer(), snapshot))) }
            for (frame in incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val message = runCatching { Json.decodeFromString(PresenceMessage.serializer(), text) }.getOrNull() ?: continue
                hub.publish(userId, message.deviceId.ifBlank { deviceId }, message)?.let { hub.broadcast(userId, it) }
            }
            hub.leave(userId, this)
        }
    }
}

@kotlinx.serialization.Serializable
data class HandoffRequest(val sessionId: String, val deviceId: String)
