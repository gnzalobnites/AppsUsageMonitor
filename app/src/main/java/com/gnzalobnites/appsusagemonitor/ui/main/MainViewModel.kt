package com.gnzalobnites.appsusagemonitor.ui.main

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import com.gnzalobnites.appsusagemonitor.data.repository.AppRepository
import com.gnzalobnites.appsusagemonitor.data.repository.UsageRepository
import com.gnzalobnites.appsusagemonitor.service.MonitoringService
import com.gnzalobnites.appsusagemonitor.utils.AccessibilityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BubblePreferences(
    val bubbleSize: Int = 60,
    val bubbleOpacity: Int = 80,
    val hapticFeedback: Boolean = true,
    val autoHide: Boolean = true,
    val showScreenTime: Boolean = true,
    val soundNotifications: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    private val repository = AppRepository(application)
    private val usageRepository = UsageRepository(application)
    private val packageManager = application.packageManager
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    
    val monitoredApps = repository.getMonitoredApps().asLiveData()
    
    // Estado del servicio
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    
    private val _isAccessibilityServiceEnabled = MutableStateFlow(false)
    val isAccessibilityServiceEnabled: StateFlow<Boolean> = _isAccessibilityServiceEnabled.asStateFlow()
    
    // Tiempo de pantalla
    private val _totalScreenTime = MutableStateFlow(0L)
    val totalScreenTime: StateFlow<Long> = _totalScreenTime.asStateFlow()
    
    // Contador de apps monitoreadas
    private val _monitoredAppsCount = MutableStateFlow(0)
    val monitoredAppsCount: StateFlow<Int> = _monitoredAppsCount.asStateFlow()
    
    // Intervalo actual
    private val _currentInterval = MutableStateFlow(60000L)
    val currentInterval: StateFlow<Long> = _currentInterval.asStateFlow()
    
    // Preferencias de la burbuja
    private val _preferences = MutableLiveData<BubblePreferences>()
    val preferences: LiveData<BubblePreferences> = _preferences
    
    init {
        loadPreferences()
        loadCurrentInterval()
    }
    
    fun updateServiceState(isEnabled: Boolean) {
        _isAccessibilityServiceEnabled.value = isEnabled
        _isServiceRunning.value = isEnabled
    }
    
    fun checkAccessibilityServiceState() {
        viewModelScope.launch(Dispatchers.IO) {
            val isEnabled = AccessibilityHelper.isAccessibilityServiceEnabled(
                getApplication(),
                MonitoringService::class.java
            )
            _isAccessibilityServiceEnabled.value = isEnabled
            _isServiceRunning.value = isEnabled
        }
    }
    
    fun requestAccessibilityPermission() {
        AccessibilityHelper.openAccessibilitySettings(getApplication())
    }
    
    fun setServiceRunning(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
        _isAccessibilityServiceEnabled.value = isRunning
    }
    
    fun stopMonitoringApp(app: MonitoredApp) {
        viewModelScope.launch {
            repository.removeAppFromMonitor(app)
            loadMonitoredAppsCount()
        }
    }
    
    fun loadTodayStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val totalTime = usageRepository.getExactScreenTimeToday()
                _totalScreenTime.value = totalTime
            } catch (e: Exception) {
                _totalScreenTime.value = 0L
            }
        }
    }
    
    fun loadMonitoredAppsCount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = repository.getMonitoredAppsSync().size
                _monitoredAppsCount.value = count
            } catch (e: Exception) {
                _monitoredAppsCount.value = 0
            }
        }
    }
    
    fun loadCurrentInterval() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apps = repository.getMonitoredAppsSync()
                if (apps.isNotEmpty()) {
                    // Tomar el intervalo de la primera app monitoreada
                    _currentInterval.value = apps.first().selectedInterval
                }
            } catch (e: Exception) {
                _currentInterval.value = 60000L
            }
        }
    }
    
    fun loadPreferences() {
        val size = prefs.getInt("bubble_size", 60)
        val opacity = prefs.getInt("bubble_opacity", 80)
        val haptic = prefs.getBoolean("haptic_feedback", true)
        val autoHide = prefs.getBoolean("auto_hide", true)
        val showTime = prefs.getBoolean("show_screen_time", true)
        val sound = prefs.getBoolean("sound_notifications", false)
        
        _preferences.value = BubblePreferences(size, opacity, haptic, autoHide, showTime, sound)
    }
    
    fun setBubbleSize(size: Int) {
        prefs.edit().putInt("bubble_size", size).apply()
        _preferences.value = _preferences.value?.copy(bubbleSize = size)
    }
    
    fun setBubbleOpacity(opacity: Int) {
        prefs.edit().putInt("bubble_opacity", opacity).apply()
        _preferences.value = _preferences.value?.copy(bubbleOpacity = opacity)
    }
    
    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean("haptic_feedback", enabled).apply()
        _preferences.value = _preferences.value?.copy(hapticFeedback = enabled)
    }
    
    fun setAutoHide(enabled: Boolean) {
        prefs.edit().putBoolean("auto_hide", enabled).apply()
        _preferences.value = _preferences.value?.copy(autoHide = enabled)
    }
    
    fun setShowScreenTime(enabled: Boolean) {
        prefs.edit().putBoolean("show_screen_time", enabled).apply()
        _preferences.value = _preferences.value?.copy(showScreenTime = enabled)
    }
    
    fun setSoundNotifications(enabled: Boolean) {
        prefs.edit().putBoolean("sound_notifications", enabled).apply()
        _preferences.value = _preferences.value?.copy(soundNotifications = enabled)
    }
    
    fun hasMonitoredApps(): Boolean {
        return monitoredApps.value?.isNotEmpty() == true
    }
}