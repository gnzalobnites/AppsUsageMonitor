package com.gnzalobnites.appsusagemonitor.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.gnzalobnites.appsusagemonitor.data.database.AppDatabase
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import com.gnzalobnites.appsusagemonitor.data.entities.UsageSession
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class AppRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val monitoredAppDao = db.monitoredAppDao()
    private val usageSessionDao = db.usageSessionDao()

    // Usamos getMonitoredApps() que es el método real en tu DAO
    fun getAllMonitoredApps(): Flow<List<MonitoredApp>> = monitoredAppDao.getMonitoredApps()

    fun getMonitoredApps(): Flow<List<MonitoredApp>> = monitoredAppDao.getMonitoredApps()
    
    suspend fun getMonitoredAppsSync(): List<MonitoredApp> = monitoredAppDao.getMonitoredAppsSync()

    /**
     * MODIFICADO: Ahora verifica si la app ya existe en la BD.
     * - Si existe (estaba pausada), solo actualiza su estado a isMonitoring = true.
     * - Si no existe, la inserta como una nueva app monitoreada.
     */
    suspend fun addAppToMonitor(packageName: String, interval: Long) {
        val pm = context.packageManager
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return
        }
        val appName = pm.getApplicationLabel(appInfo).toString()
        
        // 1. Buscamos si la app ya tiene un registro previo
        val existingApp = monitoredAppDao.getAppByPackage(packageName)
        
        if (existingApp != null) {
            // 2a. Si ya existe (estaba pausada/desactivada), solo actualizamos el estado y el intervalo
            val updatedApp = existingApp.copy(
                isMonitoring = true,
                selectedInterval = interval
            )
            monitoredAppDao.update(updatedApp)
        } else {
            // 2b. Solo insertamos si es la primera vez que se monitorea
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
    
    // Método delete que usa el DAO correctamente
    suspend fun deleteMonitoredApp(app: MonitoredApp) {
        monitoredAppDao.delete(app)
    }

    suspend fun startSession(packageName: String, startTime: Long): Long {
        val session = UsageSession(
            packageName = packageName,
            startTime = startTime
        )
        return usageSessionDao.insert(session)
    }

    suspend fun endSession(sessionId: Long) {
        val endTime = System.currentTimeMillis()
        val session = usageSessionDao.getSessionById(sessionId)
        session?.let {
            val duration = endTime - it.startTime
            usageSessionDao.updateSessionEnd(sessionId, endTime, duration)
        }
    }

    suspend fun getTotalUsageForDay(packageName: String, startOfDay: Long, endOfDay: Long): Long {
        return usageSessionDao.getTotalUsageForDay(packageName, startOfDay, endOfDay) ?: 0L
    }

    /**
     * Obtiene el tiempo total de uso de HOY para una app específica
     * Usa la hora actual como límite superior para evitar datos futuros
     * CORREGIDO: ahora usa endOfDay = now en lugar de endOfDay = fin del día
     */
    suspend fun getTotalUsageToday(packageName: String): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()
        
        return getTotalUsageForDay(packageName, startOfDay, now)
    }
    
    @Deprecated("Usa getTotalUsageToday(packageName) que no requiere startOfDay")
    suspend fun getTotalUsageToday(packageName: String, startOfDay: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = startOfDay
        }
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis
        return usageSessionDao.getTotalUsageToday(packageName, startOfDay) ?: 0L
    }

    suspend fun getCurrentSessionDuration(startTime: Long): Long {
        return System.currentTimeMillis() - startTime
    }

    suspend fun getAppByPackage(packageName: String): MonitoredApp? {
        return monitoredAppDao.getAppByPackage(packageName)
    }
    
    suspend fun getSessionsForDay(packageName: String, startOfDay: Long, endOfDay: Long): List<UsageSession> {
        return usageSessionDao.getSessionsForDay(packageName, startOfDay, endOfDay)
    }
}