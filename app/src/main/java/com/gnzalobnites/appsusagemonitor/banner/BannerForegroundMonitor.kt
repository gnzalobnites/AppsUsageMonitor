package com.gnzalobnites.appsusagemonitor.banner

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
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
    private val CHECK_INTERVAL_MS = 1000L
    
    private lateinit var usageStatsManager: UsageStatsManager
    private var currentPackageName: String? = null
    
    fun initialize() {
        this.usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }
    
    fun startMonitoring(packageName: String) {
        stopMonitoring()
        currentPackageName = packageName
        
        checkRunnable = object : Runnable {
            override fun run() {
                checkIfInForeground()
                handler.postDelayed(this, CHECK_INTERVAL_MS)
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
    
    private fun checkIfInForeground() {
        val pkg = currentPackageName ?: return
        
        if (!isAppInForeground(pkg)) {
            Log.d("ForegroundMonitor", "🚪 Usuario salió de: $pkg")
            onAppExit()
        }
    }
    
    fun isAppInForeground(packageName: String): Boolean {
        if (!hasUsageStatsPermission()) return true
        
        return try {
            val currentTime = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                currentTime - 2000,
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