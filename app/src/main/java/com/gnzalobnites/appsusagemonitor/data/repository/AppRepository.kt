package com.gnzalobnites.appsusagemonitor.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.gnzalobnites.appsusagemonitor.data.database.AppDatabase
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import kotlinx.coroutines.flow.Flow

class AppRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val monitoredAppDao = db.monitoredAppDao()

    fun getAllMonitoredApps(): Flow<List<MonitoredApp>> = monitoredAppDao.getMonitoredApps()

    fun getMonitoredApps(): Flow<List<MonitoredApp>> = monitoredAppDao.getMonitoredApps()
    
    suspend fun getMonitoredAppsSync(): List<MonitoredApp> = monitoredAppDao.getMonitoredAppsSync()

    suspend fun addAppToMonitor(packageName: String, interval: Long) {
        val pm = context.packageManager
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return
        }
        val appName = pm.getApplicationLabel(appInfo).toString()
        
        val existingApp = monitoredAppDao.getAppByPackage(packageName)
        
        if (existingApp != null) {
            val updatedApp = existingApp.copy(
                isMonitoring = true,
                selectedInterval = interval
            )
            monitoredAppDao.update(updatedApp)
        } else {
            val app = MonitoredApp(
                packageName = packageName,
                appName = appName,
                isMonitoring = true,
                selectedInterval = interval
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