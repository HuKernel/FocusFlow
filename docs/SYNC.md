# 同步

M0 只包含 SyncEvent 模型和 outbox 表；未实现网络同步。
M5 协议目标：POST /api/v1/sync/push，GET /api/v1/sync/pull?cursor=，响应 changes/nextCursor/serverTime。
Task 依 server revision 合并；delete 为 tombstone；Session 以 UUID 追加，进度本地重聚合。
本地 SQLite 测试证明 25m+30m=55m 与 UUID 幂等，不等同于已完成双设备联网验收。
M7 添加 Live Presence：FOCUS_STARTED/PAUSED/RESUMED/COMPLETED/CANCELLED/OWNER_CHANGED。
Observer 不可操作 Owner 计时器；Handoff 需服务端原子转移 owner 并通知旧设备降为 Observer。
