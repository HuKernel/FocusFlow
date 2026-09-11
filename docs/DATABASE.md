# 本地数据库

Room schema version 1：tasks、focus_sessions、sync_events、timer_anchors。
核心领域模型覆盖 PRD 的 14 类实体，其余实体表在相关里程碑实现，当前不宣称已有完整数据库。

Task 无 progress 列；进度查询仅 SUM(COMPLETED.actualDuration)，时间单位统一毫秒。
Session UUID 为主键，终态 insert-only + IGNORE 重试重复记录；活动会话变化在 M2 设计独立写入路径，不能用 IGNORE 吞掉 ACTIVE→COMPLETED。
Task 删除保留 deletedAt；列表排除 tombstone。任务与 SyncEvent 使用 @Transaction 写入，事件插入失败时整体回滚。
Room 编译生成 schema 必须提交。首次 v1 不存在历史升级路径；v2 起提交显式 migration 与旧库升级测试，禁止 destructive fallback。
Desktop 集成测试使用真实 SQLite 文件，覆盖重开持久化、UUID 幂等、25m+30m 聚合、tombstone。
