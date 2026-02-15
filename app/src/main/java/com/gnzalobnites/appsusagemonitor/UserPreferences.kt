package com.gnzalobnites.appsusagemonitor

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.TimeUnit

class UserPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "user_preferences",
        Context.MODE_PRIVATE
    )

    var showBanner: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BANNER, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_BANNER, value).apply()
            Log.d("UserPreferences", "showBanner establecido a: $value")
        }

    var bannerIntervalMinutes: Int
        get() = prefs.getInt(KEY_BANNER_INTERVAL_MIN, 5)
        set(value) = prefs.edit().putInt(KEY_BANNER_INTERVAL_MIN, value).apply()

    var bannerDurationSeconds: Int
        get() = prefs.getInt(KEY_BANNER_DURATION_SEC, 5)
        set(value) = prefs.edit().putInt(KEY_BANNER_DURATION_SEC, value).apply()

    var monitoredApps: Set<String>
        get() = prefs.getStringSet(KEY_MONITORED_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_MONITORED_APPS, value).apply()
        
    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
            Log.d("UserPreferences", "Modo oscuro establecido a: $value")
        }

    // MODIFICADO: Soporte para 10 segundos (DEMO) - eliminado 1 segundo
    fun getBannerIntervalMs(): Long {
        val minutes = bannerIntervalMinutes
        
        return when {
            minutes == -1 -> {
                Log.d("UserPreferences", "⚙️ Intervalo: 10 segundos (DEMO) → 10000ms")
                10000L
            }
            else -> {
                val ms = TimeUnit.MINUTES.toMillis(minutes.toLong())
                Log.d("UserPreferences", "⚙️ Intervalo: $minutes min → ${ms}ms")
                ms
            }
        }
    }
    
    // MODIFICADO: Texto correcto para 10 segundos (DEMO)
    fun getBannerIntervalDisplayText(): String {
        return when (bannerIntervalMinutes) {
            -1 -> "10 segundos (DEMO)"
            else -> "$bannerIntervalMinutes minutos"
        }
    }

    fun clearMonitoredApps() {
        prefs.edit().remove(KEY_MONITORED_APPS).apply()
        Log.d("UserPreferences", "Apps monitoreadas limpiadas")
    }
    
    fun isMonitored(packageName: String): Boolean {
        return monitoredApps.contains(packageName)
    }

    fun addMonitoredApp(packageName: String) {
        val newSet = monitoredApps.toMutableSet().apply { add(packageName) }
        monitoredApps = newSet
        Log.d("UserPreferences", "App agregada: $packageName (total: ${newSet.size})")
    }

    fun removeMonitoredApp(packageName: String) {
        val newSet = monitoredApps.toMutableSet().apply { remove(packageName) }
        monitoredApps = newSet
        Log.d("UserPreferences", "App removida: $packageName (total: ${newSet.size})")
    }
    
    fun clearAll() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
        Log.d("UserPreferences", "Todas las preferencias han sido eliminadas")
    }

    companion object {
        private const val KEY_SHOW_BANNER = "show_banner"
        private const val KEY_BANNER_INTERVAL_MIN = "banner_interval_min"
        private const val KEY_BANNER_DURATION_SEC = "banner_duration_sec"
        private const val KEY_MONITORED_APPS = "monitored_apps"
        private const val KEY_DARK_MODE = "dark_mode"

        @Volatile
        private var INSTANCE: UserPreferences? = null

        fun getInstance(context: Context): UserPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = UserPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}