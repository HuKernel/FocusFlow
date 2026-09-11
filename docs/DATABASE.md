# 本地数据库

Room schema version 3：tasks、focus_sessions、sync_events、timer_anchors、projects、tags、task_tags、local_identity、active_focus。
核心领域模型覆盖 PRD 的 14 类实体，其余实体表在相关里程碑实现，当前不宣称已有完整数据库。

Task 无 progress 列；进度查询仅 SUM(COMPLETED.actualDuration)，时间单位统一毫秒。
Session UUID 为主键，终态 insert-only；active_focus 单行保存活动及结算后的快照。FocusRepository 仅在非终态转终态时插入 Session，插入冲突视为事务失败，不能用 IGNORE 吞掉 ACTIVE→COMPLETED。已结算快照重复恢复不再插入。旧 timer_anchors 保留兼容，但不再作为运行状态来源。
Task/Project/Tag 删除保留 deletedAt；列表排除 tombstone。TaskRepository 通过 useWriterConnection + IMMEDIATE transaction 写入数据及事件，任一步失败整体回滚。旧版 saveTaskWithEvent 事务测试仍保留。
编辑任务先在事务中检查 revision；不允许旧快照覆盖新值。local_identity 的 userId/deviceId 只生成一次，当前为本地匿名身份。
Room 编译生成 schema 必须提交。1→2、2→3 均新增表，使用 AutoMigration；旧库测试从已提交的 v1 JSON 建库，插入旧 Task/Session，升级到当前版本验证数据与新增表，不允许 destructive fallback。保留 1/2/3 JSON，不修改历史版本。
Desktop 集成测试使用真实 SQLite 文件，覆盖重开持久化、UUID 幂等、25m+30m 聚合、tombstone、事务回滚、组织关联清理和升级。
