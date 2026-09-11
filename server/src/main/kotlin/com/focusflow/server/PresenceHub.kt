package com.focusflow.server

import com.focusflow.core.PresenceKind
import com.focusflow.core.PresenceMessage
import com.focusflow.core.PresenceSnapshot
import io.ktor.server.websocket.WebSocketServerSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 每用户单活跃会话的实时 presence。内存态：服务重启即清空（观察者重新拉 REST 兜底）；
 * ponytail: 多实例部署时以 Redis pub/sub 替换 sockets 分发，snapshot 语义不变。
 */
class PresenceHub {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }
    private class Room {
        var snapshot: PresenceSnapshot? = null
        val sockets = mutableSetOf<WebSocketServerSession>()
    }
    private val rooms = mutableMapOf<String, Room>()

    fun join(userId: String, session: WebSocketServerSession): PresenceMessage? = synchronized(lock) {
        val room = rooms.getOrPut(userId) { Room() }
        room.sockets += session
        room.snapshot?.let { PresenceMessage(it.kind, it.ownerDeviceId, it) }
    }

    fun leave(userId: String, session: WebSocketServerSession) = synchronized(lock) {
        val room = rooms[userId] ?: return
        room.sockets -= session
        if (room.sockets.isEmpty() && room.snapshot == null) rooms -= userId
    }

    /** 仅 owner 可更新状态；终态广播后清空 presence。返回需要广播的消息。 */
    fun publish(userId: String, deviceId: String, message: PresenceMessage): PresenceMessage? = synchronized(lock) {
        val room = rooms[userId] ?: return null
        val current = room.snapshot
        if (current == null) {
            if (message.kind != PresenceKind.FOCUS_STARTED || message.snapshot == null) return null
            room.snapshot = message.snapshot
            return message
        }
        if (current.ownerDeviceId != deviceId || message.snapshot == null) return null
        return when (message.kind) {
            PresenceKind.FOCUS_COMPLETED, PresenceKind.FOCUS_CANCELLED -> {
                room.snapshot = null
                message
            }
            PresenceKind.OWNER_CHANGED -> null // 只能经 handoff 端点触发
            else -> {
                room.snapshot = message.snapshot
                message
            }
        }
    }

    /** Continue on this device：原子转移 owner；请求者不能已是 owner。 */
    fun handoff(userId: String, toDeviceId: String, sessionId: String): PresenceSnapshot? = synchronized(lock) {
        val room = rooms[userId] ?: return null
        val current = room.snapshot ?: return null
        if (current.sessionId != sessionId || current.ownerDeviceId == toDeviceId) return null
        val transferred = current.copy(kind = PresenceKind.OWNER_CHANGED, ownerDeviceId = toDeviceId)
        room.snapshot = transferred
        transferred
    }

    suspend fun broadcast(userId: String, message: PresenceMessage) {
        val encoded = json.encodeToString(message)
        val targets = synchronized(lock) { rooms[userId]?.sockets?.toList() } ?: return
        for (socket in targets) runCatching { socket.send(io.ktor.websocket.Frame.Text(encoded)) }
    }

    fun snapshotOf(userId: String): PresenceSnapshot? = synchronized(lock) { rooms[userId]?.snapshot }
}
