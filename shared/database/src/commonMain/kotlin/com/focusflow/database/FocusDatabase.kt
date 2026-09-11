package com.focusflow.database

import androidx.room.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.focusflow.core.FocusSession
import com.focusflow.core.SyncEvent
import com.focusflow.core.Task
import com.focusflow.core.TimerAnchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks", primaryKeys = ["id"])
data class TaskEntity(@Embedded val task: Task)

@Entity(tableName = "focus_sessions", primaryKeys = ["id"], indices = [Index("taskId")])
data class SessionEntity(@Embedded val session: FocusSession)

@Entity(tableName = "sync_events", primaryKeys = ["id"])
data class SyncEventEntity(@Embedded val event: SyncEvent)

@Entity(tableName = "timer_anchors", primaryKeys = ["sessionId"])
data class TimerAnchorEntity(@Embedded val anchor: TimerAnchor)

@Dao
interface FocusDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Upsert suspend fun upsertTask(task: TaskEntity)

    // Terminal records are insert-only: duplicate UUIDs cannot overwrite completed work.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: SessionEntity): Long

    @Query("SELECT COALESCE(SUM(actualDuration), 0) FROM focus_sessions WHERE taskId = :taskId AND status = 'COMPLETED'")
    suspend fun completedMillis(taskId: String): Long

    @Insert suspend fun insertEvent(event: SyncEventEntity)
    @Upsert suspend fun saveAnchor(anchor: TimerAnchorEntity)
    @Query("SELECT * FROM timer_anchors WHERE sessionId = :sessionId")
    suspend fun anchor(sessionId: String): TimerAnchorEntity?

    @Transaction
    suspend fun saveTaskWithEvent(task: TaskEntity, event: SyncEventEntity) {
        upsertTask(task)
        insertEvent(event)
    }
}

@Database(entities = [TaskEntity::class, SessionEntity::class, SyncEventEntity::class, TimerAnchorEntity::class], version = 1)
@ConstructedBy(FocusDatabaseConstructor::class)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao
}

@Suppress("KotlinNoActualForExpect")
expect object FocusDatabaseConstructor : RoomDatabaseConstructor<FocusDatabase> {
    override fun initialize(): FocusDatabase
}

fun buildDatabase(builder: RoomDatabase.Builder<FocusDatabase>): FocusDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
