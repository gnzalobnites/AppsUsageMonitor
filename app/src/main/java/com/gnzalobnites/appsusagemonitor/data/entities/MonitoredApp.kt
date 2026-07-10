package com.gnzalobnites.appsusagemonitor.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_apps")
data class MonitoredApp(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isMonitoring: Boolean = false,
    val timeGoalMinutes: Int = 5, // Meta en minutos (antes selectedInterval)
    val currentSessionStart: Long = 0L,
    val currentSessionDuration: Long = 0L,
    val totalUsageToday: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastGoalNotified: Boolean = false,
    val lastGoalNotifiedTime: Long = 0L// Para no repetir notificaciones
)