package com.gnzalobnites.appsusagemonitor.banner

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class BannerForegroundMonitor(
    private val context: Context,
    private val onAppExit: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    private val CHECK_INTERVAL_MS = 500L // Más frecuente para detectar salida rápido
    
    private lateinit var usageStatsManager: UsageStatsManager
    private var currentPackageName: String? = null
    private var wasInForeground = true
    
    fun initialize() {
        this.usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }
    
    fun startMonitoring(packageName: String) {
        stopMonitoring()
        currentPackageName = packageName
        wasInForeground = true
        
        checkRunnable = object : Runnable {
            override fun run() {
                val wasInForegroundBefore = wasInForeground
                wasInForeground = checkIfInForeground()
                
                // Si antes estaba en foreground y ahora no → SALIÓ DE LA APP
                if (wasInForegroundBefore && !wasInForeground) {
                    Log.d("ForegroundMonitor", "🚪 Usuario SALIÓ de: $packageName")
                    
                    // Enviar broadcast inmediato
                    val intent = Intent("APP_EXIT_DETECTED").apply {
                        putExtra("packageName", packageName)
                        putExtra("timestamp", System.currentTimeMillis())
                    }
                    context.sendBroadcast(intent)
                    
                    // Llamar al callback
                    onAppExit()
                    
                    // Detener monitoreo
                    stopMonitoring()
                } else {
                    // Seguir monitoreando
                    handler.postDelayed(this, CHECK_INTERVAL_MS)
                }
            }
        }
        
        handler.post(checkRunnable!!)
        Log.d("ForegroundMonitor", "👁️ Monitoreo iniciado para: $packageName")
    }
    
    fun stopMonitoring() {
        checkRunnable?.let { handler.removeCallbacks(it) }
        checkRunnable = null
        currentPackageName = null
    }
    
    private fun checkIfInForeground(): Boolean {
        val pkg = currentPackageName ?: return true
        
        if (!hasUsageStatsPermission()) {
            // Sin permiso, asumimos que sigue en foreground para no cerrar falsamente
            return true
        }
        
        return try {
            val currentTime = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                currentTime - 2000,
                currentTime
            )
            
            if (stats.isNullOrEmpty()) return true
            
            // Ordenar por último uso (más reciente primero)
            val sorted = stats.sortedByDescending { it.lastTimeUsed }
            val topApp = sorted.firstOrNull()
            
            val isInForeground = topApp?.packageName == pkg
            Log.d("ForegroundMonitor", "🔍 $pkg en foreground: $isInForeground (top: ${topApp?.packageName})")
            
            isInForeground
            
        } catch (e: Exception) {
            Log.e("ForegroundMonitor", "Error: ${e.message}")
            true
        }
    }
    
    fun isAppInForeground(packageName: String): Boolean {
        if (!hasUsageStatsPermission()) return true
        
        return try {
            val currentTime = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                currentTime - 1000,
                currentTime
            )
            
            if (stats.isNullOrEmpty()) return true
            
            val topApp = stats.sortedByDescending { it.lastTimeUsed }.firstOrNull()
            topApp?.packageName == packageName
            
        } catch (e: Exception) {
            Log.e("ForegroundMonitor", "Error: ${e.message}")
            true
        }
    }
    
    fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                AppOpsManager.MODE_DEFAULT
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }
}