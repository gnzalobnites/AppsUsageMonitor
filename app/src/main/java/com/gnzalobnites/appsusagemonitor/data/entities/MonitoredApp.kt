package com.gnzalobnites.appsusagemonitor.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_apps")
data class MonitoredApp(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isMonitoring: Boolean = false,
    val selectedInterval: Long = 60000L,
    val lastNotificationTime: Long = 0L,
    val totalUsageToday: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
