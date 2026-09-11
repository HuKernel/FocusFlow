package com.focusflow.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.focusflow.core.*
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

class TaskRepositoryTest {
    @Test fun taskLifecycleIsDurableAndEmitsOutboxWithoutInventingFocus() = runBlocking {
        val file = Files.createTempDirectory("focusflow-tasks").resolve("tasks.db").toFile()
        var db = openDatabase(file)
        try {
            val repo = TaskRepository(db)
            repo.saveProject("Study")
            repo.saveTag("Kotlin")
            val initial = repo.data.first()
            val project = initial.projects.single()
            val tag = initial.tags.single()
            val id = repo.saveTask(TaskDraft(" Read ", projectId = project.id, tagIds = setOf(tag.id), plannedDate = "2026-09-11"))
            val created = db.focusDao().task(id)!!.task
            assertEquals("Read", created.title)
            assertEquals(1, db.focusDao().tagsForTask(id).size)
            repo.setCompleted(created, true)
            val completed = db.focusDao().task(id)!!.task
            assertEquals(TaskStatus.DONE, completed.status)
            assertNotNull(completed.completedAt)
            assertEquals(0L, db.focusDao().completedMillis(id))
            assertFailsWith<IllegalArgumentException> { repo.saveTask(TaskDraft("Stale"), created) }
            repo.setCompleted(completed, false)
            repo.deleteProject(project)
            assertNull(db.focusDao().task(id)!!.task.projectId)
            repo.deleteTag(tag)
            assertTrue(db.focusDao().tagsForTask(id).isEmpty())
            val identity = db.focusDao().identity()!!
            db.close()
            db = openDatabase(file)
            assertEquals(identity, db.focusDao().identity())
            val restored = db.focusDao().task(id)!!.task
            assertEquals(TaskStatus.TODO, restored.status)
            assertNull(restored.completedAt)
            TaskRepository(db).deleteTask(restored)
            assertTrue(db.focusDao().observeTasks().first().isEmpty())
            assertNotNull(db.focusDao().task(id)!!.task.deletedAt)
            val events = db.focusDao().events().map { it.event }
            assertEquals(1, events.map { it.deviceId }.toSet().size)
            assertEquals(events.size, events.map { it.id }.toSet().size)
            assertTrue(events.any { it.entityType == "TaskTag" && it.operation == SyncOperation.DELETE })
            assertTrue(events.any { it.entityId == id && it.operation == SyncOperation.DELETE && Json.parseToJsonElement(it.payload).jsonObject["deletedAt"] != JsonNull })
        } finally { db.close(); file.parentFile.deleteRecursively() }
    }

    @Test fun repositoryRollsBackWhenEventInsertFails() = runBlocking {
        val file = Files.createTempDirectory("focusflow-rollback").resolve("tasks.db").toFile()
        val db = openDatabase(file)
        try {
            val repo = TaskRepository(db, newId = { "fixed-id" })
            val id = repo.saveTask(TaskDraft("Original"))
            val task = db.focusDao().task(id)!!.task
            assertFails { repo.saveTask(TaskDraft("Changed"), task) }
            assertEquals("Original", db.focusDao().task(id)!!.task.title)
        } finally { db.close(); file.parentFile.deleteRecursively() }
    }

    @Test fun migratesOldVersionsWithoutLosingTaskOrSession() = runBlocking {
        for (version in 1..2) {
        val file = Files.createTempDirectory("focusflow-migration").resolve("tasks.db").toFile()
        val schema = Json.parseToJsonElement(javaClass.getResourceAsStream("/com.focusflow.database.FocusDatabase/$version.json")!!.bufferedReader().readText()).jsonObject["database"]!!.jsonObject
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        fun sql(query: String) { connection.prepare(query).use { it.step() } }
        try {
            for (entity in schema["entities"]!!.jsonArray) {
                val obj = entity.jsonObject
                val table = obj["tableName"]!!.jsonPrimitive.content
                sql(obj["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                for (index in obj["indices"]?.jsonArray.orEmpty()) sql(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
            for (query in schema["setupQueries"]!!.jsonArray) sql(query.jsonPrimitive.content)
            sql("INSERT INTO tasks (id,userId,title,description,status,priority,targetFocusMinutes,createdAt,updatedAt,revision) VALUES ('old','user','Old task','','TODO','NONE',25,0,0,0)")
            sql("INSERT INTO focus_sessions (id,userId,taskId,deviceId,ownerDeviceId,type,plannedDuration,actualDuration,startedAt,endedAt,pausedDuration,interruptCount,status,strictMode,createdAt,updatedAt,revision) VALUES ('session','user','old','phone','phone','COUNTDOWN',1500000,1500000,0,1500000,0,0,'COMPLETED','NORMAL',0,0,0)")
            sql("PRAGMA user_version = $version")
        } finally { connection.close() }
        val db = openDatabase(file)
        try {
            assertEquals("Old task", db.focusDao().task("old")!!.task.title)
            assertEquals(1500000L, db.focusDao().completedMillis("old"))
            TaskRepository(db).saveProject("After upgrade")
            assertEquals("After upgrade", db.focusDao().observeProjects().first().single().project.name)
            FocusRepository(db, DesktopFocusClock()).start("old", 60_000)
            assertEquals("old", db.focusDao().activeFocus()!!.session.taskId)
        } finally { db.close(); file.parentFile.deleteRecursively() }
        }
    }
}
