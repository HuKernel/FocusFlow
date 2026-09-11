package com.focusflow.database

import android.content.Context
import androidx.room.Room

fun openDatabase(context: Context): FocusDatabase {
    val app = context.applicationContext
    return buildDatabase(Room.databaseBuilder<FocusDatabase>(app, app.getDatabasePath("focusflow.db").absolutePath))
}
