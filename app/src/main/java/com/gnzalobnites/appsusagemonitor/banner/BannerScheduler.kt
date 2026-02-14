package com.gnzalobnites.appsusagemonitor.banner

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gnzalobnites.appsusagemonitor.UserPreferences

class BannerScheduler(
    private val userPreferences: UserPreferences,
    private val onShowBanner: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var showBannerRunnable: Runnable? = null
    private var nextBannerScheduled = false
    
    fun scheduleNextBanner(currentState: BannerState, hasActiveSession: Boolean): Boolean {
        if (!userPreferences.showBanner) {
            Log.d("BannerScheduler", "❌ Banner desactivado")
            return false
        }
        
        if (!hasActiveSession) {
            Log.d("BannerScheduler", "❌ No hay sesión activa")
            return false
        }
        
        if (currentState != BannerState.HIDDEN) {
            Log.d("BannerScheduler", "⚠️ Estado actual: $currentState, no se programa")
            return false
        }
        
        if (nextBannerScheduled) {
            Log.d("BannerScheduler", "⚠️ Ya hay banner programado")
            return false
        }
        
        val intervalMs = userPreferences.getBannerIntervalMs()
        val safeIntervalMs = if (intervalMs < 1000) 5000L else intervalMs
        
        Log.d("BannerScheduler", "📅 Programando banner en ${safeIntervalMs/1000} seg")
        
        showBannerRunnable?.let { handler.removeCallbacks(it) }
        showBannerRunnable = Runnable {
            Log.d("BannerScheduler", "🔔 EJECUTANDO banner")
            onShowBanner()
        }
        
        handler.postDelayed(showBannerRunnable!!, safeIntervalMs)
        nextBannerScheduled = true
        return true
    }
    
    fun bannerShown() {
        nextBannerScheduled = false
    }
    
    fun cancelAll() {
        showBannerRunnable?.let { handler.removeCallbacks(it) }
        showBannerRunnable = null
        nextBannerScheduled = false
    }
}