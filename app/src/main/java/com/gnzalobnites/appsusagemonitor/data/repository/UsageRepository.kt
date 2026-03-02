package com.gnzalobnites.appsusagemonitor.data.repository

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.gnzalobnites.appsusagemonitor.data.model.UsageStat
import java.util.Calendar

/**
 * Repositorio para obtener estadísticas de uso del sistema Android
 * Utiliza UsageStatsManager para consultar el tiempo de uso de apps
 */
class UsageRepository(private val application: Application) {
    
    private val usageStatsManager = application.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = application.packageManager
    
    /**
     * Obtiene estadísticas de uso del día actual
     * @return Lista de UsageStat ordenada por tiempo de uso (mayor a menor)
     */
    fun getTodayUsageStats(): List<UsageStat> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        return getUsageStatsInRange(startTime, endTime)
    }
    
    /**
     * Obtiene estadísticas de uso de ayer
     * @return Lista de UsageStat ordenada por tiempo de uso
     */
    fun getYesterdayUsageStats(): List<UsageStat> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val startTime = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endTime = calendar.timeInMillis
        
        return getUsageStatsInRange(startTime, endTime)
    }
    
    /**
     * Obtiene estadísticas de uso de la última semana
     * @return Lista de UsageStat ordenada por tiempo de uso
     */
    fun getWeeklyUsageStats(): List<UsageStat> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        return getUsageStatsInRange(startTime, endTime)
    }
    
    /**
     * Obtiene estadísticas de uso en un rango de tiempo específico
     * @param startTime Tiempo de inicio en milisegundos
     * @param endTime Tiempo de fin en milisegundos
     * @return Lista de UsageStat filtrada y ordenada
     */
    fun getUsageStatsInRange(startTime: Long, endTime: Long): List<UsageStat> {
        // Query al sistema
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        if (usageStats == null || usageStats.isEmpty()) {
            return emptyList()
        }
        
        // Filtrar solo apps con tiempo > 0 y ordenar por tiempo descendente
        return usageStats
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .map { stats ->
                val appName = try {
                    val appInfo = packageManager.getApplicationInfo(stats.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    // Si no se encuentra, usar el packageName como fallback
                    stats.packageName
                }
                
                UsageStat(
                    packageName = stats.packageName,
                    appName = appName,
                    totalTimeInForeground = stats.totalTimeInForeground,
                    lastTimeUsed = stats.lastTimeUsed
                )
            }
    }
    
    /**
     * Obtiene el tiempo total de uso del día para una app específica
     * @param packageName Nombre del paquete de la app
     * @return Tiempo total en milisegundos
     */
    fun getTotalTimeForAppToday(packageName: String): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        return stats?.find { it.packageName == packageName }?.totalTimeInForeground ?: 0L
    }
    
    /**
     * Obtiene el tiempo total de uso de ayer para una app específica
     * @param packageName Nombre del paquete de la app
     * @return Tiempo total en milisegundos
     */
    fun getTotalTimeForAppYesterday(packageName: String): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val startTime = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endTime = calendar.timeInMillis
        
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        return stats?.find { it.packageName == packageName }?.totalTimeInForeground ?: 0L
    }
    
    /**
     * Verifica si hay permisos de uso de datos concedidos
     * @return true si hay al menos una app con datos, false si no
     */
    fun hasUsageStatsPermission(): Boolean {
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 1000 * 60 * 60 * 24, // Últimas 24 horas
            currentTime
        )
        return stats != null && stats.isNotEmpty()
    }
}