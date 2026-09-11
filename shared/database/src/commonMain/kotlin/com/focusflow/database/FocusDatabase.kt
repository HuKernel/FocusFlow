package com.focusflow.database

import androidx.room.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.focusflow.core.FocusSession
import com.focusflow.core.SyncEvent
import com.focusflow.core.Task
import com.focusflow.core.TimerAnchor
import com.focusflow.core.Project
import com.focusflow.core.Tag
import com.focusflow.core.TaskTag
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

@Entity(tableName = "projects", primaryKeys = ["id"])
data class ProjectEntity(@Embedded val project: Project)

@Entity(tableName = "tags", primaryKeys = ["id"])
data class TagEntity(@Embedded val tag: Tag)

@Entity(tableName = "task_tags", primaryKeys = ["taskId", "tagId"], indices = [Index("tagId")])
data class TaskTagEntity(@Embedded val link: TaskTag)

@Entity(tableName = "local_identity")
data class LocalIdentity(@PrimaryKey val singleton: Int = 0, val userId: String, val deviceId: String)

@Dao
interface FocusDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Upsert suspend fun upsertTask(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: String): TaskEntity?
    @Query("SELECT * FROM tasks WHERE projectId = :id AND deletedAt IS NULL") suspend fun tasksInProject(id: String): List<TaskEntity>
    @Query("SELECT * FROM projects WHERE deletedAt IS NULL ORDER BY name") fun observeProjects(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name") fun observeTags(): Flow<List<TagEntity>>
    @Query("SELECT * FROM task_tags") fun observeTaskTags(): Flow<List<TaskTagEntity>>
    @Query("SELECT * FROM focus_sessions WHERE status = 'COMPLETED'") fun observeCompletedSessions(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun project(id: String): ProjectEntity?
    @Query("SELECT * FROM tags WHERE id = :id") suspend fun tag(id: String): TagEntity?
    @Upsert suspend fun upsertProject(project: ProjectEntity)
    @Upsert suspend fun upsertTag(tag: TagEntity)
    @Query("SELECT * FROM task_tags WHERE taskId = :id") suspend fun tagsForTask(id: String): List<TaskTagEntity>
    @Query("SELECT * FROM task_tags WHERE tagId = :id") suspend fun linksForTag(id: String): List<TaskTagEntity>
    @Insert suspend fun insertTaskTag(link: TaskTagEntity)
    @Query("DELETE FROM task_tags WHERE taskId = :taskId AND tagId = :tagId") suspend fun deleteTaskTag(taskId: String, tagId: String)
    @Query("SELECT * FROM local_identity WHERE singleton = 0") suspend fun identity(): LocalIdentity?
    @Insert suspend fun insertIdentity(identity: LocalIdentity)
    @Query("SELECT * FROM sync_events ORDER BY clientTimestamp, id") suspend fun events(): List<SyncEventEntity>

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

@Database(
    entities = [TaskEntity::class, SessionEntity::class, SyncEventEntity::class, TimerAnchorEntity::class,
        ProjectEntity::class, TagEntity::class, TaskTagEntity::class, LocalIdentity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
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
