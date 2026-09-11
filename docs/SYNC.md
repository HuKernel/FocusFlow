# 同步

## 状态（M5 第一阶段，0.6.0）
服务端（Ktor + JDBC）与客户端 SyncEngine 已实现并通过测试；Android 登录/网络传输接线尚未完成，App 内还不能触发同步。

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

## 后续
M7 Live Presence：WebSocket FOCUS_STARTED/PAUSED/RESUMED/COMPLETED/CANCELLED/OWNER_CHANGED；Observer 只读；Handoff 服务端原子转移 owner。
