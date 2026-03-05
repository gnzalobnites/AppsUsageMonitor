package com.gnzalobnites.appsusagemonitor.ui.main

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import com.gnzalobnites.appsusagemonitor.data.repository.AppRepository
import com.gnzalobnites.appsusagemonitor.data.repository.UsageRepository
import com.gnzalobnites.appsusagemonitor.data.model.UsageStat
import com.gnzalobnites.appsusagemonitor.service.MonitoringService
import com.gnzalobnites.appsusagemonitor.utils.AccessibilityHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    private val repository = AppRepository(application)
    private val usageRepository = UsageRepository(application)
    private val packageManager = application.packageManager
    
    // Funcionalidad existente: apps monitoreadas
    val monitoredApps: LiveData<List<MonitoredApp>> = repository.getMonitoredApps().asLiveData()
    
    // Funcionalidad existente: estado del servicio (ahora refleja el estado real de accesibilidad)
    private val _isServiceRunning = MutableLiveData(false)
    val isServiceRunning: LiveData<Boolean> = _isServiceRunning
    
    // NUEVO: LiveData específico para el estado del servicio de accesibilidad
    private val _isAccessibilityServiceEnabled = MutableLiveData(false)
    val isAccessibilityServiceEnabled: LiveData<Boolean> = _isAccessibilityServiceEnabled
    
    // LiveData que el Fragment observará para el gráfico
    private val _usageStats = MutableLiveData<Map<String, Long>>()
    val usageStats: LiveData<Map<String, Long>> = _usageStats
    
    // NUEVA funcionalidad: estadísticas de uso diario (versión detallada)
    private val _todayUsageStats = MutableLiveData<List<UsageStat>>()
    val todayUsageStats: LiveData<List<UsageStat>> = _todayUsageStats
    
    // Caché para nombres de apps
    private val appNamesCache = mutableMapOf<String, String>()
    
    // MODIFICADO: Actualiza el estado basado en el estado real del servicio de accesibilidad
    fun updateServiceState(isEnabled: Boolean) {
        _isAccessibilityServiceEnabled.value = isEnabled
        _isServiceRunning.value = isEnabled // Mantener compatibilidad
    }
    
    // NUEVO: Verificar y actualizar el estado del servicio de accesibilidad
    fun checkAccessibilityServiceState() {
        viewModelScope.launch(Dispatchers.IO) {
            val isEnabled = AccessibilityHelper.isAccessibilityServiceEnabled(
                getApplication(),
                MonitoringService::class.java
            )
            _isAccessibilityServiceEnabled.postValue(isEnabled)
            _isServiceRunning.postValue(isEnabled)
        }
    }
    
    // NUEVO: Abrir configuración de accesibilidad
    fun requestAccessibilityPermission() {
        AccessibilityHelper.openAccessibilitySettings(getApplication())
    }
    
    // Mantenemos setServiceRunning para compatibilidad pero ahora actualiza ambos LiveData
    fun setServiceRunning(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
        _isAccessibilityServiceEnabled.value = isRunning
    }
    
    fun stopMonitoringApp(app: MonitoredApp) {
        viewModelScope.launch {
            repository.removeAppFromMonitor(app)
        }
    }
    
    // Versión CORREGIDA para el gráfico - usa el método mejorado getTotalUsageToday()
    fun loadTodayStats() {
        viewModelScope.launch {
            try {
                // 1. Obtener apps monitoreadas desde la DB
                val monitoredAppsList = withContext(Dispatchers.IO) {
                    repository.getMonitoredAppsSync()
                }
                
                // Si no hay apps monitoreadas, mostrar mapa vacío
                if (monitoredAppsList.isEmpty()) {
                    _usageStats.postValue(emptyMap())
                    _todayUsageStats.postValue(emptyList())
                    return@launch
                }
                
                // 2. Usar la hora actual como límite superior para evitar datos del día anterior
                val now = System.currentTimeMillis()
                
                // 3. Construir mapas de resultados
                val usageMap = mutableMapOf<String, Long>()
                val detailedList = mutableListOf<UsageStat>()
                
                // 4. Para cada app monitoreada, obtener el total de uso de HOY desde la BD
                monitoredAppsList.forEach { monitoredApp ->
                    // Usar el método mejorado que solo obtiene datos desde las 00:00 hasta ahora
                    val totalToday = repository.getTotalUsageToday(monitoredApp.packageName)
                    
                    if (totalToday > 0) {
                        val appName = monitoredApp.appName
                        usageMap[appName] = totalToday
                        
                        detailedList.add(
                            UsageStat(
                                packageName = monitoredApp.packageName,
                                appName = appName,
                                totalTimeInForeground = totalToday,
                                lastTimeUsed = now
                            )
                        )
                        
                        // Guardar en caché por si se necesita después
                        appNamesCache[monitoredApp.packageName] = appName
                    }
                }
                
                // 5. Publicar resultados
                _usageStats.postValue(usageMap)
                _todayUsageStats.postValue(detailedList)
                
            } catch (e: SecurityException) {
                // Permiso no concedido
                _usageStats.postValue(emptyMap())
                _todayUsageStats.postValue(emptyList())
            } catch (e: Exception) {
                // Manejar otros errores
                _usageStats.postValue(emptyMap())
                _todayUsageStats.postValue(emptyList())
            }
        }
    }
    
    // NUEVO: Cargar estadísticas reales de hoy (versión detallada)
    fun loadTodayUsageStats() {
        // Reutilizamos loadTodayStats que ya hace lo mismo
        loadTodayStats()
    }
    
    // NUEVO: Obtener nombre de app del caché
    fun getAppName(packageName: String): String? {
        return appNamesCache[packageName]
    }
    
    // NUEVO: Método auxiliar para obtener nombre de app
    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // Fallback al packageName si no se encuentra
        }
    }
    
    // NUEVO: Verificar si hay apps monitoreadas
    fun hasMonitoredApps(): Boolean {
        return monitoredApps.value?.isNotEmpty() == true
    }
}