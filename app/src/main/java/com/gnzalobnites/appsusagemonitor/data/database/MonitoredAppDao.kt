package com.gnzalobnites.appsusagemonitor.data.database

import androidx.room.*
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps ORDER BY appName ASC")
    fun getAllApps(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_apps")
    suspend fun getAllAppsSync(): List<MonitoredApp>

    @Query("SELECT * FROM monitored_apps WHERE isMonitoring = 1")
    fun getMonitoredApps(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_apps WHERE isMonitoring = 1")
    suspend fun getMonitoredAppsSync(): List<MonitoredApp>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :packageName")
    suspend fun getAppByPackage(packageName: String): MonitoredApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: MonitoredApp)

    @Update
    suspend fun update(app: MonitoredApp)

    @Delete
    suspend fun delete(app: MonitoredApp)
}
