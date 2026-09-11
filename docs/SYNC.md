# 同步

## 状态（M5 完成，0.7.0）
服务端、HTTP 传输、账号与 App 内登录/同步入口全部接线；真机双设备 HTTP 验收待设备可用后补充。

## 协议（client 与 server 共用 core/Protocol.kt）
- POST /api/v1/auth/register、/login → {userId, token}（PBKDF2 密码散列，HMAC-SHA256 token，30 天）
- POST /api/v1/sync/push {deviceId, events:[SyncEvent]} → {accepted, serverTime}
- GET /api/v1/sync/pull?cursor=&deviceId= → {changes:[ServerChange], nextCursor, serverTime}

## 服务端语义（server/Store.kt）
- 幂等：event.id 全局去重（applied_events 表），重推不再计数、不重复投影。
- 投影：entities(user_id, entity_type, entity_id) 保存最新全量 payload；每次写入分配 per-user 单调 revision。
- 合并策略：Task/Project/Tag 比较 payload 内客户端 revision，旧事件不覆盖新状态；FocusSession insert-if-absent（UUID 合并，不覆盖已有）；TaskTag 按操作覆盖（tombstone 语义）。
- 回声排除：pull 跳过 last_device_id == 请求设备的变更，客户端不会收到自己刚推的数据。
- 生产 PostgreSQL（DATABASE_URL/SERVER_SECRET/PORT 环境变量），开发与测试 H2 MODE=PostgreSQL；UPDATE-then-INSERT upsert 保持双兼容。
- ponytail: Store 全局锁防 revision 竞态（单实例）；多实例部署时改 DB sequence / per-user 锁。

## 客户端（shared/sync/SyncEngine.kt）
- synchronize()：push 全部未 SYNCED outbox 事件 → 标记 SYNCED → pull(cursor) → 事务内 apply → 推进 sync_state.cursor。
- apply：Task/Project/Tag 本地 revision 不小于远端时跳过；FocusSession IGNORE 插入（UUID 幂等，不改已有）；TaskTag 按 deleted 标志插删。远端数据不回流 outbox。
- Room schema v4 新增 sync_state(cursor)；AutoMigration 3→4，迁移测试覆盖 v1/v2/v3 → v4。

## 已验证（shared/sync desktopTest + server test）
- 双设备离线：手机 25m + 平板 30m，联网后双方 completedMillis=55m，sessions 各 2 条（真 Room + 真 Store，非模拟传输）。
- tombstone 跨设备删除；重复同步幂等（pushed=0/pulled=0）；HTTP 幂等重推 accepted=0；cursor 增量拉取；回声排除。
- 未完成：Android 登录 UI、Ktor client HTTP transport、自动同步调度、冲突 UI（Task 编辑已有 revision 防护兜底）。

## Live Presence / Owner-Observer（M7，0.9.0）
- server PresenceHub（内存，每用户单活跃会话）：WS /api/v1/ws（query token+deviceId 认证）、GET /api/v1/focus/presence 重连兜底、POST /api/v1/focus/handoff 原子转移 owner（请求者不能已是 owner，sessionId 必须匹配）。
- 事件：FOCUS_STARTED/PAUSED/RESUMED/COMPLETED/CANCELLED/OWNER_CHANGED；广播含共享锚点（anchorEpochMillis/planned/elapsedAtAnchor/paused），Observer 用本地时钟估算剩余，时钟偏差只影响显示不影响结算。
- 仅 owner 可 publish；非 owner 消息静默拒绝；终态清空 presence；server 主引擎换 Netty（CIO 引擎在该环境拒绝 WS 升级，返回 400）。
- 客户端：HttpFocusPresence（Ktor client WS，断线静默重连靠重进/重启）；FocusViewModel 上报生命周期、收到 OWNER_CHANGED 且 owner 是自己时按锚点 adopt 同一 sessionId（完成时按原 UUID 结算）；旧 owner 立即 releaseLocal（不结算、不发事件）。Observer 面板：任务名/状态/剩余/发起设备 +「在这台设备继续」。
- ponytail: PresenceHub 单机内存态，多实例部署以 Redis pub/sub 替换 socket 分发；presence 不持久化，重启后 Observer 经 REST 拉当前快照恢复。

## 已验证
- server PresenceTest（真 Netty WS 双客户端）：owner 广播、非 owner 拒绝、handoff 双方收到 OWNER_CHANGED、终态清空。
- core PresenceTest：锚点估算（暂停冻结、剩余不为负、owner 判定）。
- FocusPresenceViewModelTest（真 Room + fake presence）：STARTED/PAUSED/COMPLETED 上报、Observer 状态、接管 adopt 同 sessionId、旧 owner 让位不结算。
- 未验证：真机双设备 WS 长连接稳定性；登录后 presence 需重启 App 生效（凭据在连接时读取）。
