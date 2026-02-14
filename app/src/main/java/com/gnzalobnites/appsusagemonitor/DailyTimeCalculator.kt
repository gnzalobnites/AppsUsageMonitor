package com.gnzalobnites.appsusagemonitor

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailyTimeCalculator(private val context: Context) {
    
    companion object {
        const val TAG = "DailyTimeCalculator"
    }
    
    private val database = AppDatabase.getDatabase(context)
    private val userPreferences = UserPreferences.getInstance(context)
    
    /**
     * Calcular y mostrar el tiempo total de apps monitoreadas hoy
     */
    fun calculateAndLogDailyTime() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val monitoredApps = userPreferences.monitoredApps.toList()
                
                if (monitoredApps.isEmpty()) {
                    Log.d(TAG, "📊 No hay apps monitoreadas")
                    return@launch
                }
                
                // Calcular tiempo total
                val totalTime = database.usageDao().getTotalMonitoredTimeToday(
                    monitoredApps,
                    now,
                    now
                )
                
                // Calcular por cada app para detalle
                val appDetails = mutableListOf<String>()
                monitoredApps.forEach { packageName ->
                    val appTime = database.usageDao().getAppTimeToday(
                        packageName,
                        now,
                        now
                    )
                    if (appTime > 0) {
                        appDetails.add("$packageName: ${formatTimeSimple(appTime)}")
                    }
                }
                
                // Log detallado
                Log.d(TAG, "=".repeat(60))
                Log.d(TAG, "📅 RESUMEN DIARIO - Apps monitoreadas: ${monitoredApps.size}")
                Log.d(TAG, "⏱️ TIEMPO TOTAL HOY: ${formatTimeSimple(totalTime)}")
                Log.d(TAG, "")
                Log.d(TAG, "📱 DESGLOSE POR APP:")
                appDetails.forEach { detail ->
                    Log.d(TAG, "  • $detail")
                }
                Log.d(TAG, "=".repeat(60))
                
            } catch (e: Exception) {
                Log.e(TAG, "Error calculando tiempo diario: ${e.message}")
            }
        }
    }
    
    /**
     * Obtener el tiempo total de hoy para mostrar en el banner
     */
    suspend fun getTotalTimeForBanner(): BannerTimeInfo {
        return try {
            val now = System.currentTimeMillis()
            val monitoredApps = userPreferences.monitoredApps.toList()
            
            val totalTime = if (monitoredApps.isNotEmpty()) {
                database.usageDao().getTotalMonitoredTimeToday(monitoredApps, now, now)
            } else {
                0L
            }
            
            BannerTimeInfo(
                totalTimeToday = totalTime,
                monitoredAppCount = monitoredApps.size,
                timestamp = now
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo tiempo para banner: ${e.message}")
            BannerTimeInfo()
        }
    }
    
    private fun formatTimeSimple(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}

data class BannerTimeInfo(
    val totalTimeToday: Long = 0L,
    val monitoredAppCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)