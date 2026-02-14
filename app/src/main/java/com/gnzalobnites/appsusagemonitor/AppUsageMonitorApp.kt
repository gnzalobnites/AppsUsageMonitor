package com.gnzalobnites.appsusagemonitor

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import android.util.Log

class AppUsageMonitorApp : Application() {
    
    companion object {
        lateinit var instance: AppUsageMonitorApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Inicializar tema ANTES de cualquier actividad
        initializeTheme()
    }
    
    fun applyTheme(isDarkMode: Boolean) {
        Log.d("AppUsageMonitorApp", "🎨 Aplicando tema: ${if (isDarkMode) "oscuro" else "claro"}")
        
        try {
            // Guardar preferencia
            val prefs = UserPreferences.getInstance(this)
            prefs.isDarkMode = isDarkMode
            
            // Aplicar tema GLOBAL
            val mode = if (isDarkMode) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            
            AppCompatDelegate.setDefaultNightMode(mode)
            Log.d("AppUsageMonitorApp", "✅ Tema aplicado: ${if (isDarkMode) "MODE_NIGHT_YES" else "MODE_NIGHT_NO"}")
        } catch (e: Exception) {
            Log.e("AppUsageMonitorApp", "Error aplicando tema: ${e.message}")
        }
    }
    
    private fun initializeTheme() {
        try {
            val prefs = UserPreferences.getInstance(this)
            val isDarkMode = prefs.isDarkMode
            
            val mode = if (isDarkMode) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            
            // IMPORTANTE: Debe llamarse ANTES de que se cree cualquier actividad
            AppCompatDelegate.setDefaultNightMode(mode)
            Log.d("AppUsageMonitorApp", "Tema inicial: ${if (isDarkMode) "oscuro" else "claro"}")
        } catch (e: Exception) {
            Log.e("AppUsageMonitorApp", "Error inicializando tema: ${e.message}")
            // Fallback a tema claro
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}