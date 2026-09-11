package com.focusflow.database

import com.focusflow.core.*
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class DatabaseTest {
    @Test fun persistsTasksAndMergesSessionIdsWithoutDoubleCounting() = runBlocking {
        val file = Files.createTempDirectory("focusflow-test").resolve("test.db").toFile()
        var db = openDatabase(file)
        try {
            db.focusDao().upsertTask(TaskEntity(Task("task", "local", "Read", createdAt = 0)))
            for ((device, minutes) in listOf("phone" to 25L, "tablet" to 30L)) {
                val record = SessionEntity(FocusSession(device, "local", "task", device, device,
                    plannedDuration = minutes * 60000, actualDuration = minutes * 60000,
                    startedAt = 0, endedAt = minutes * 60000, status = SessionStatus.COMPLETED))
                db.focusDao().insertSession(record)
                assertEquals(-1L, db.focusDao().insertSession(record))
            }
            db.close()
            db = openDatabase(file)
            assertEquals("Read", db.focusDao().observeTasks().first().single().task.title)
            assertEquals(55 * 60000L, db.focusDao().completedMillis("task"))
            db.focusDao().upsertTask(TaskEntity(Task("task", "local", "Read", createdAt = 0, deletedAt = 10)))
            assertTrue(db.focusDao().observeTasks().first().isEmpty())
        } finally {
            db.close()
            file.parentFile.deleteRecursively()
        }
    }
}
