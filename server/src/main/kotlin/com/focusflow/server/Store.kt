package com.focusflow.server

import com.focusflow.core.PullResponse
import com.focusflow.core.ServerChange
import com.focusflow.core.SyncEvent
import com.focusflow.core.SyncOperation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.sql.DriverManager
import java.util.UUID

/** 同步事件投影存储。生产用 PostgreSQL，开发/测试用 H2（MODE=PostgreSQL），DDL 保持双兼容。 */
class Store(url: String) {
    private val connection = DriverManager.getConnection(url)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        connection.autoCommit = false
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """CREATE TABLE IF NOT EXISTS users(
                    id VARCHAR(36) PRIMARY KEY, username VARCHAR(64) UNIQUE NOT NULL,
                    password_salt VARCHAR(64) NOT NULL, password_hash VARCHAR(256) NOT NULL, created_at BIGINT NOT NULL)""")
            statement.executeUpdate(
                """CREATE TABLE IF NOT EXISTS entities(
                    user_id VARCHAR(36) NOT NULL, entity_type VARCHAR(32) NOT NULL, entity_id VARCHAR(128) NOT NULL,
                    revision BIGINT NOT NULL, client_revision BIGINT NOT NULL, payload TEXT NOT NULL,
                    deleted BOOLEAN NOT NULL, last_device_id VARCHAR(36) NOT NULL, updated_at BIGINT NOT NULL,
                    PRIMARY KEY(user_id, entity_type, entity_id))""")
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_entities_user_revision ON entities(user_id, revision)")
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS applied_events(event_id VARCHAR(64) PRIMARY KEY)")
        }
        connection.commit()
    }

    // ponytail: 全局锁防 revision 竞态——单实例够用；多实例部署时改 DB sequence 或 per-user 锁
    fun register(username: String, password: String, now: Long): String = synchronized(this) {
        require(username.length in 3..64 && password.length in 8..128) { "用户名需要 3–64 个字符，密码至少 8 位" }
        require(query("SELECT id FROM users WHERE username = ?") { it.setString(1, username) }.isEmpty()) { "用户名已被使用" }
        val salt = Auth.salt()
        check(insert("INSERT INTO users(id, username, password_salt, password_hash, created_at) VALUES(?,?,?,?,?)",
            UUID.randomUUID().toString(), username, salt, Auth.hash(password, salt), now)) { "注册失败，请重试" }
        query("SELECT id FROM users WHERE username = ?") { it.setString(1, username) }.single().getString(1)
    }

    fun login(username: String, password: String): String = query(
        "SELECT id, password_salt, password_hash FROM users WHERE username = ?",
    ) { it.setString(1, username) }.let { rows ->
        val row = rows.singleOrNull() ?: throw IllegalArgumentException("用户名或密码不正确")
        val stored = row.getString(3)
        require(Auth.hash(password, row.getString(2)) == stored) { "用户名或密码不正确" }
        row.getString(1)
    }

    fun push(userId: String, deviceId: String, events: List<SyncEvent>): Int = synchronized(this) {
        var accepted = 0
        for (event in events) {
            if (!insert("INSERT INTO applied_events(event_id) VALUES(?)", event.id)) continue // 幂等重推
            accepted++
            val key = event.entityType to event.entityId
            when (event.entityType) {
                // 终态记录按 UUID 合并：已有版本不会被后来的事件覆盖
                "FocusSession" -> insertEntity(userId, key, nextRevision(userId), 0, event.payload, false, deviceId, event.clientTimestamp)
                // 关联表按操作语义处理，last-write-wins by arrival
                "TaskTag" -> insertEntity(userId, key, nextRevision(userId), 0, event.payload, event.operation == SyncOperation.DELETE, deviceId, event.clientTimestamp)
                else -> {
                    val clientRevision = payloadRevision(event.payload) ?: 0
                    val existing = selectEntity(userId, key)
                    if (existing == null || clientRevision > existing.clientRevision) {
                        insertEntity(userId, key, nextRevision(userId), clientRevision, event.payload, false, deviceId, event.clientTimestamp)
                    }
                }
            }
        }
        accepted
    }

    fun pull(userId: String, deviceId: String, cursor: Long, limit: Int = 500): PullResponse = synchronized(this) {
        val rows = query(
            "SELECT entity_type, entity_id, revision, payload, deleted FROM entities " +
                "WHERE user_id = ? AND revision > ? AND last_device_id != ? ORDER BY revision LIMIT $limit",
        ) { it.setString(1, userId); it.setLong(2, cursor); it.setString(3, deviceId) }
        val changes = rows.map { ServerChange(it.getString(1), it.getString(2), it.getLong(3), it.getString(4), it.getBoolean(5)) }
        val nextCursor = changes.maxOfOrNull { it.revision } ?: cursor
        PullResponse(changes, nextCursor, System.currentTimeMillis())
    }

    fun close() = connection.close()

    private fun payloadRevision(payload: String): Long? = runCatching {
        json.parseToJsonElement(payload).let { element ->
            (element as? kotlinx.serialization.json.JsonObject)?.get("revision")?.jsonPrimitive?.longOrNull
        }
    }.getOrNull()

    private class EntityRow(val revision: Long, val clientRevision: Long, val payload: String)

    private fun selectEntity(userId: String, key: Pair<String, String>): EntityRow? = query(
        "SELECT revision, client_revision, payload FROM entities WHERE user_id = ? AND entity_type = ? AND entity_id = ?",
    ) { it.setString(1, userId); it.setString(2, key.first); it.setString(3, key.second) }
        .map { EntityRow(it.getLong(1), it.getLong(2), it.getString(3)) }.singleOrNull()

    private fun nextRevision(userId: String): Long = query(
        "SELECT COALESCE(MAX(revision), 0) + 1 FROM entities WHERE user_id = ?",
    ) { it.setString(1, userId) }.single().getLong(1)

    // H2 不支持 ON CONFLICT(cols) DO UPDATE，用 UPDATE-then-INSERT；并发由 Store 全局锁串行化
    private fun insertEntity(
        userId: String, key: Pair<String, String>, revision: Long, clientRevision: Long,
        payload: String, deleted: Boolean, deviceId: String, updatedAt: Long,
    ) {
        val updated = connection.prepareStatement(
            "UPDATE entities SET revision = ?, client_revision = ?, payload = ?, deleted = ?, last_device_id = ?, updated_at = ? " +
                "WHERE user_id = ? AND entity_type = ? AND entity_id = ?",
        ).use { statement ->
            statement.setLong(1, revision); statement.setLong(2, clientRevision); statement.setString(3, payload)
            statement.setBoolean(4, deleted); statement.setString(5, deviceId); statement.setLong(6, updatedAt)
            statement.setString(7, userId); statement.setString(8, key.first); statement.setString(9, key.second)
            statement.executeUpdate()
        }
        if (updated == 0) insert(
            "INSERT INTO entities(user_id, entity_type, entity_id, revision, client_revision, payload, deleted, last_device_id, updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
            userId, key.first, key.second, revision, clientRevision, payload, deleted, deviceId, updatedAt)
        else connection.commit()
    }

    private fun insert(sql: String, vararg args: Any?): Boolean {
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            return try {
                statement.executeUpdate()
                connection.commit()
                true
            } catch (duplicate: java.sql.SQLIntegrityConstraintViolationException) {
                connection.rollback()
                false
            } catch (duplicate: java.sql.SQLException) {
                if (duplicate.sqlState == "23505" || duplicate.sqlState == "23001") { connection.rollback(); false } else throw duplicate
            }
        }
    }

    private fun query(sql: String, bind: (java.sql.PreparedStatement) -> Unit = {}): List<java.sql.ResultSet> {
        connection.prepareStatement(sql).use { statement ->
            bind(statement)
            statement.executeQuery().use { result ->
                val rows = mutableListOf<java.sql.ResultSet>()
                while (result.next()) rows.add(SnapshotRow(result))
                return rows
            }
        }
    }

    /** ResultSet 关闭后不可读，query 返回时做快照。 */
    private class SnapshotRow(result: java.sql.ResultSet) : java.sql.ResultSet by result {
        private val values = Array(result.metaData.columnCount) { result.getObject(it + 1) }
        override fun getObject(columnIndex: Int): Any? = values[columnIndex - 1]
        override fun getString(columnIndex: Int): String = values[columnIndex - 1] as String
        override fun getLong(columnIndex: Int): Long = (values[columnIndex - 1] as Number).toLong()
        override fun getBoolean(columnIndex: Int): Boolean = values[columnIndex - 1] as Boolean
    }
}
