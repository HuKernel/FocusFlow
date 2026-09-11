package com.focusflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.focusflow.database.openDatabase
import com.focusflow.designsystem.FocusApp

class MainActivity : ComponentActivity() {
    private val database by lazy { openDatabase(applicationContext) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val tasks by database.focusDao().observeTasks().collectAsState(emptyList())
            FocusApp(tasks.size)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        database.close()
    }
}
