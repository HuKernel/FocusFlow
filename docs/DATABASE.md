# 本地数据库

Room schema version 2：tasks、focus_sessions、sync_events、timer_anchors、projects、tags、task_tags、local_identity。
核心领域模型覆盖 PRD 的 14 类实体，其余实体表在相关里程碑实现，当前不宣称已有完整数据库。

Task 无 progress 列；进度查询仅 SUM(COMPLETED.actualDuration)，时间单位统一毫秒。
Session UUID 为主键，终态 insert-only + IGNORE 重试重复记录；活动会话变化在 M2 设计独立写入路径，不能用 IGNORE 吞掉 ACTIVE→COMPLETED。
Task/Project/Tag 删除保留 deletedAt；列表排除 tombstone。TaskRepository 通过 useWriterConnection + IMMEDIATE transaction 写入数据及事件，任一步失败整体回滚。旧版 saveTaskWithEvent 事务测试仍保留。
编辑任务先在事务中检查 revision；不允许旧快照覆盖新值。local_identity 的 userId/deviceId 只生成一次，当前为本地匿名身份。
Room 编译生成 schema 必须提交。1→2 是新增表，使用已声明的 AutoMigration；旧库测试从已提交的 v1 JSON 建库，插入旧 Task/Session，打开 v2 后验证数据与新增表，不允许 destructive fallback。
Desktop 集成测试使用真实 SQLite 文件，覆盖重开持久化、UUID 幂等、25m+30m 聚合、tombstone、事务回滚、组织关联清理和升级。
