package com.gnzalobnites.appsusagemonitor.data.repository

import android.app.Application
import android.content.pm.PackageManager
import com.gnzalobnites.appsusagemonitor.data.database.AppDatabase
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class AppRepository(private val application: Application) {
    private val db = AppDatabase.getInstance(application)
    private val monitoredAppDao = db.monitoredAppDao()
    private val packageManager: PackageManager = application.packageManager

    fun getAllMonitoredApps(): Flow<List<MonitoredApp>> = 
        monitoredAppDao.getMonitoredApps().distinctUntilChanged()

    fun getMonitoredApps(): Flow<List<MonitoredApp>> = 
        monitoredAppDao.getMonitoredApps().distinctUntilChanged()
    
    suspend fun getMonitoredAppsSync(): List<MonitoredApp> = 
        monitoredAppDao.getMonitoredAppsSync()

    suspend fun addAppToMonitor(packageName: String, goalMinutes: Int) {
        val appInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return
        }
        val appName = packageManager.getApplicationLabel(appInfo).toString()
        
        val existingApp = monitoredAppDao.getAppByPackage(packageName)
        
        if (existingApp != null) {
            val updatedApp = existingApp.copy(
                isMonitoring = true,
                timeGoalMinutes = goalMinutes
            )
            monitoredAppDao.update(updatedApp)
        } else {
            val app = MonitoredApp(
                packageName = packageName,
                appName = appName,
                isMonitoring = true,
                timeGoalMinutes = goalMinutes
            )
            monitoredAppDao.insert(app)
        }
    }

    suspend fun updateAppMonitoring(app: MonitoredApp) {
        monitoredAppDao.update(app)
    }

    suspend fun removeAppFromMonitor(app: MonitoredApp) {
        val updatedApp = app.copy(isMonitoring = false)
        monitoredAppDao.update(updatedApp)
    }
    
    suspend fun deleteMonitoredApp(app: MonitoredApp) {
        monitoredAppDao.delete(app)
    }

    suspend fun getAppByPackage(packageName: String): MonitoredApp? {
        return monitoredAppDao.getAppByPackage(packageName)
    }
}