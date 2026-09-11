package com.focusflow.core

import kotlinx.serialization.Serializable

/** 客户端与服务端共用的同步协议模型；payload 为实体的 JSON 全量状态。 */
@Serializable data class AuthRequest(val username: String, val password: String)
@Serializable data class AuthResponse(val userId: String, val token: String)
@Serializable data class PushRequest(val deviceId: String, val events: List<SyncEvent>)
@Serializable data class PushResponse(val accepted: Int, val serverTime: Long)
@Serializable data class ServerChange(
    val entityType: String, val entityId: String, val revision: Long, val payload: String, val deleted: Boolean,
)
@Serializable data class PullResponse(val changes: List<ServerChange>, val nextCursor: Long, val serverTime: Long)

/** 同步传输抽象：shared/network 提供 HTTP 实现，测试直接复用 server 模块的 Store。 */
interface SyncTransport {
    suspend fun push(events: List<SyncEvent>): Int
    suspend fun pull(cursor: Long, deviceId: String): PullResponse
}
