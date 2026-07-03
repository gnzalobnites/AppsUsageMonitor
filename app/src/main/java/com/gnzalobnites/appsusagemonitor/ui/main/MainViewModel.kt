package com.gnzalobnites.appsusagemonitor.ui.main

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import com.gnzalobnites.appsusagemonitor.data.repository.AppRepository
import com.gnzalobnites.appsusagemonitor.data.repository.UsageRepository
import com.gnzalobnites.appsusagemonitor.service.MonitoringService
import com.gnzalobnites.appsusagemonitor.utils.AccessibilityHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    private val repository = AppRepository(application)
    private val usageRepository = UsageRepository(application)
    private val packageManager = application.packageManager
    
    val monitoredApps = repository.getMonitoredApps().asLiveData()
    
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    
    private val _isAccessibilityServiceEnabled = MutableStateFlow(false)
    val isAccessibilityServiceEnabled: StateFlow<Boolean> = _isAccessibilityServiceEnabled.asStateFlow()
    
    // SIMPLIFICADO: Solo el tiempo total de pantalla con StateFlow
    private val _totalScreenTime = MutableStateFlow(0L)
    val totalScreenTime: StateFlow<Long> = _totalScreenTime.asStateFlow()
    
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
        }
    }
    
    // SIMPLIFICADO: Solo obtiene el tiempo total de pantalla
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
    
    fun hasMonitoredApps(): Boolean {
        return monitoredApps.value?.isNotEmpty() == true
    }
}