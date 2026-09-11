package com.focusflow.network

import com.focusflow.core.AuthRequest
import com.focusflow.core.AuthResponse
import com.focusflow.core.PullResponse
import com.focusflow.core.PushRequest
import com.focusflow.core.PushResponse
import com.focusflow.core.SyncEvent
import com.focusflow.core.SyncTransport
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** 账号 + 同步传输的服务端接口；HTTP 实现供 Android/Desktop 使用，测试可替换为内存实现。 */
interface FocusSyncApi {
    suspend fun register(serverUrl: String, username: String, password: String): AuthResponse
    suspend fun login(serverUrl: String, username: String, password: String): AuthResponse
    fun transport(serverUrl: String, token: String): SyncTransport
}

class HttpFocusSyncApi : FocusSyncApi {
    private val format = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(format) }
    }

    override suspend fun register(serverUrl: String, username: String, password: String): AuthResponse =
        post("$serverUrl/api/v1/auth/register", username, password)

    override suspend fun login(serverUrl: String, username: String, password: String): AuthResponse =
        post("$serverUrl/api/v1/auth/login", username, password)

    override fun transport(serverUrl: String, token: String): SyncTransport = object : SyncTransport {
        override suspend fun push(events: List<SyncEvent>): Int = client.post("$serverUrl/api/v1/sync/push") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PushRequest(events.firstOrNull()?.deviceId ?: "", events))
        }.body<PushResponse>().accepted

        override suspend fun pull(cursor: Long, deviceId: String): PullResponse = client.get("$serverUrl/api/v1/sync/pull") {
            bearerAuth(token)
            url { parameters.append("cursor", cursor.toString()); parameters.append("deviceId", deviceId) }
        }.body()
    }

    private suspend fun post(url: String, username: String, password: String): AuthResponse =
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest(username, password))
        }.body()
}

/** 把 HTTP/网络异常转为用户可读的中文提示。 */
fun Throwable.toSyncMessage(): String = when (this) {
    is io.ktor.client.plugins.ClientRequestException -> when (response.status.value) {
        401 -> "用户名或密码不正确"
        409 -> "用户名已被使用"
        else -> "服务器拒绝请求（${response.status.value}）"
    }
    is io.ktor.client.network.sockets.ConnectTimeoutException,
    is io.ktor.client.plugins.HttpRequestTimeoutException -> "连接超时，请检查服务器地址与网络"
    is java.net.UnknownHostException, is java.net.ConnectException -> "无法连接服务器，请检查地址"
    is IllegalArgumentException -> message ?: "输入不正确"
    else -> "网络错误，请稍后重试"
}
