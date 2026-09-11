package com.focusflow.sync

import com.focusflow.database.FocusDatabase
import com.focusflow.database.SyncStateEntity
import com.focusflow.network.FocusSyncApi
import kotlinx.coroutines.flow.Flow

/** 账号与手动同步的入口；token 只存本地 sync_state，不同步上传。 */
class SyncCoordinator(private val database: FocusDatabase, private val api: FocusSyncApi) {
    private val dao = database.focusDao()

    val account: Flow<SyncStateEntity?> = dao.observeSyncState()

    suspend fun register(serverUrl: String, username: String, password: String) = signIn(
        api.register(serverUrl.trimEnd('/'), username.trim(), password), serverUrl.trimEnd('/'), username.trim())

    suspend fun login(serverUrl: String, username: String, password: String) = signIn(
        api.login(serverUrl.trimEnd('/'), username.trim(), password), serverUrl.trimEnd('/'), username.trim())

    private suspend fun signIn(auth: com.focusflow.core.AuthResponse, serverUrl: String, username: String) {
        val current = dao.syncState()
        dao.saveSyncState(SyncStateEntity(
            cursor = current?.cursor ?: 0, serverUrl = serverUrl, token = auth.token, username = username))
    }

    suspend fun logout() {
        val current = dao.syncState() ?: return
        dao.saveSyncState(current.copy(serverUrl = null, token = null, username = null))
    }

    /** presence 连接所需凭据：url、token、deviceId；未登录返回 null。 */
    suspend fun presenceContext(): Triple<String?, String?, String?>? {
        val state = dao.syncState() ?: return null
        return Triple<String?, String?, String?>(state.serverUrl, state.token, dao.identity()?.deviceId)
    }

    /** 未登录返回 null；网络/服务端异常向上抛出由调用方展示。 */
    suspend fun syncOnce(): SyncEngine.Summary? {
        val state = dao.syncState()
        val url = state?.serverUrl ?: return null
        val token = state.token ?: return null
        return SyncEngine(database, api.transport(url, token)).synchronize()
    }
}
