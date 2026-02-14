package com.gnzalobnites.appsusagemonitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import java.util.Calendar

class ServiceViewModel(application: Application) : AndroidViewModel(application) {
    
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _currentApp = MutableLiveData<String>()
    val currentApp: LiveData<String> = _currentApp
    
    private val _sessionDuration = MutableLiveData<Long>()
    val sessionDuration: LiveData<Long> = _sessionDuration
    
    private val _todayUsage = MutableLiveData<Map<String, Long>>()
    val todayUsage: LiveData<Map<String, Long>> = _todayUsage
    
    private val _bannerVisible = MutableLiveData<Boolean>()
    val bannerVisible: LiveData<Boolean> = _bannerVisible
    
    private val _lastBannerTime = MutableLiveData<Long>()
    val lastBannerTime: LiveData<Long> = _lastBannerTime
    
    private val _serviceStatus = MutableLiveData<String>()
    val serviceStatus: LiveData<String> = _serviceStatus
    
    // Método para actualizar desde el servicio
    fun updateFromService(
        currentPackage: String? = null,
        duration: Long? = null,
        bannerVisible: Boolean? = null
    ) {
        currentPackage?.let { _currentApp.value = it }
        duration?.let { _sessionDuration.value = it }
        bannerVisible?.let { _bannerVisible.value = it }
        
        _lastBannerTime.value = System.currentTimeMillis()
    }
    
    // Función para actualizar la app actual
    fun updateCurrentApp(packageName: String) {
        _currentApp.value = packageName
    }
    
    // Función para actualizar duración de sesión
    fun updateSessionDuration(duration: Long) {
        _sessionDuration.value = duration
    }
    
    // Función para mostrar/ocultar banner
    fun setBannerVisible(visible: Boolean) {
        _bannerVisible.value = visible
        if (visible) {
            _lastBannerTime.value = System.currentTimeMillis()
        }
    }
    
    // Método para cargar estadísticas del día
    fun loadTodayUsageStats() {
        viewModelScope.launch {
            try {
                val stats = withContext(Dispatchers.IO) {
                    loadUsageStatsFromDatabase()
                }
                _todayUsage.value = stats
                _serviceStatus.value = "Estadísticas cargadas: ${stats.size} apps"
            } catch (e: Exception) {
                _serviceStatus.value = "Error cargando estadísticas: ${e.message}"
            }
        }
    }
    
    // Función para cargar uso diario (alias para mantener compatibilidad)
    fun loadTodayUsage() {
        loadTodayUsageStats()
    }
    
    private suspend fun loadUsageStatsFromDatabase(): Map<String, Long> {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(getApplication())
            val dao = database.usageDao()
            
            // Calcular inicio del día
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            
            // Obtener sesiones del día
            val sessions = dao.getSessionsBetween(startOfDay, System.currentTimeMillis())
            
            // Agrupar por app y sumar tiempos
            sessions.groupBy { it.packageName }
                .mapValues { entry ->
                    entry.value.sumOf { session ->
                        val end = session.endTime ?: System.currentTimeMillis()
                        end - session.startTime
                    }
                }
        }
    }
    
    // Función helper para compatibilidad con versiones anteriores de Kotlin
    private fun <T> Iterable<T>.sumOf(selector: (T) -> Long): Long {
        var sum = 0L
        for (element in this) {
            sum += selector(element)
        }
        return sum
    }
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }
}