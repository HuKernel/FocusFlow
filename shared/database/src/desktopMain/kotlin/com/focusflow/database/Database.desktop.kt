package com.focusflow.database

import androidx.room.Room
import java.io.File

fun openDatabase(file: File): FocusDatabase {
    file.parentFile?.mkdirs()
    return buildDatabase(Room.databaseBuilder<FocusDatabase>(file.absolutePath))
}
