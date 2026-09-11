package com.focusflow.network

import com.focusflow.core.PresenceMessage
import com.focusflow.core.PresenceSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 客户端 presence 接口：WS 收发 + HTTP handoff；测试可注入内存实现。 */
interface FocusPresence {
    val incoming: SharedFlow<PresenceMessage>
    fun start(scope: CoroutineScope)
    fun stop()
    suspend fun send(message: PresenceMessage)
    suspend fun takeover(sessionId: String): PresenceSnapshot?
}

class HttpFocusPresence(
    private val serverUrl: (suspend () -> String?)? = null,
    private val token: (suspend () -> String?)? = null,
    private val deviceId: (suspend () -> String?)? = null,
) : FocusPresence {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(io.ktor.client.engine.cio.CIO) { install(io.ktor.client.plugins.websocket.WebSockets) }
    private val outgoing = MutableSharedFlow<PresenceMessage>(extraBufferCapacity = 32)
    private val mutableIncoming = MutableSharedFlow<PresenceMessage>(replay = 1, extraBufferCapacity = 64)
    override val incoming: SharedFlow<PresenceMessage> = mutableIncoming
    private var job: Job? = null

    override fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val url = serverUrl?.invoke()?.trimEnd('/') ?: return@launch
            val bearer = token?.invoke() ?: return@launch
            val device = deviceId?.invoke() ?: ""
            // 断线重连由调用方重启 start 负责；这里静默失败不打扰本地计时
            runCatching {
                client.webSocket(url.replace("http://", "ws://") + "/api/v1/ws?token=$bearer&deviceId=$device") {
                    val relay = launch { this@HttpFocusPresence.outgoing.collect { send(Frame.Text(json.encodeToString(PresenceMessage.serializer(), it))) } }
                    for (frame in incoming) {
                        (frame as? Frame.Text)?.let { text ->
                            runCatching { json.decodeFromString(PresenceMessage.serializer(), text.readText()) }
                                .getOrNull()?.let { mutableIncoming.emit(it) }
                        }
                    }
                    relay.cancel()
                }
            }
        }
    }

    override fun stop() { job?.cancel(); job = null }

    override suspend fun send(message: PresenceMessage) { outgoing.emit(message) }

    override suspend fun takeover(sessionId: String): PresenceSnapshot? {
        val url = serverUrl?.invoke()?.trimEnd('/') ?: return null
        val bearer = token?.invoke() ?: return null
        val response: HttpResponse = client.post("$url/api/v1/focus/handoff") {
            bearerAuth(bearer)
            contentType(ContentType.Application.Json)
            setBody("""{"sessionId":"$sessionId","deviceId":"${deviceId?.invoke() ?: ""}"}""")
        }
        return if (response.status.value == 200) runCatching { response.body<PresenceSnapshot>() }.getOrNull() else null
    }
}
