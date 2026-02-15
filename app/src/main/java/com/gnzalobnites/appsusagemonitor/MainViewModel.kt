package com.gnzalobnites.appsusagemonitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import java.util.*
import android.util.Log

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    // Usar SupervisorJob para manejar errores en coroutines
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Agregar Repository
    private val repository: UsageRepository
    
    // LiveData para la configuración
    private val _showBanner = MutableLiveData<Boolean>()
    val showBanner: LiveData<Boolean> = _showBanner
    
    private val _bannerInterval = MutableLiveData<Int>()
    val bannerInterval: LiveData<Int> = _bannerInterval
    
    private val _bannerDuration = MutableLiveData<Int>()
    val bannerDuration: LiveData<Int> = _bannerDuration
    
    private val _monitoredApps = MutableLiveData<List<String>>()
    val monitoredApps: LiveData<List<String>> = _monitoredApps
    
    private val _isDarkMode = MutableLiveData<Boolean>()
    val isDarkMode: LiveData<Boolean> = _isDarkMode
    
    // LiveData para el estado del servicio
    private val _serviceStatus = MutableLiveData<ServiceStatus>()
    val serviceStatus: LiveData<ServiceStatus> = _serviceStatus
    
    // LiveData para el resumen diario
    private val _dailySummary = MutableLiveData<String>()
    val dailySummary: LiveData<String> = _dailySummary
    
    // LiveData para el estado de permisos
    private val _permissionsStatus = MutableLiveData<PermissionsStatus>()
    val permissionsStatus: LiveData<PermissionsStatus> = _permissionsStatus
    
    private val prefs: UserPreferences
    
    init {
        // Inicializar Repository
        val database = AppDatabase.getDatabase(application)
        repository = UsageRepository.getInstance(database)
        
        prefs = UserPreferences.getInstance(application)
        
        // Inicializar todos los LiveData
        _showBanner.value = prefs.showBanner
        _bannerInterval.value = prefs.bannerIntervalMinutes
        _bannerDuration.value = prefs.bannerDurationSeconds
        _monitoredApps.value = prefs.monitoredApps.toList()
        _isDarkMode.value = prefs.isDarkMode
        
        _serviceStatus.value = ServiceStatus(
            isRunning = false,
            overlayPermission = false,
            accessibilityPermission = false
        )
        
        _dailySummary.value = ""
        
        _permissionsStatus.value = PermissionsStatus(
            overlayPermission = false,
            accessibilityPermission = false,
            allPermissionsGranted = false
        )
        
        Log.d("MainViewModel", "✅ TODOS los LiveData inicializados")
    }
    
    fun saveAllSettings() {
        prefs.showBanner = _showBanner.value ?: false
        prefs.bannerIntervalMinutes = _bannerInterval.value ?: 1
        prefs.bannerDurationSeconds = _bannerDuration.value ?: 5
        prefs.isDarkMode = _isDarkMode.value ?: false
        
        val currentApps = _monitoredApps.value ?: emptyList()
        prefs.clearMonitoredApps()
        currentApps.forEach { packageName ->
            prefs.addMonitoredApp(packageName)
        }
        
        Log.d("MainViewModel", "✅ Configuración guardada")
    }
    
    fun updateShowBanner(show: Boolean) {
        prefs.showBanner = show
        _showBanner.value = show
        Log.d("MainViewModel", "• Mostrar banner actualizado: $show")
    }
    
    // MODIFICADO: Permitir -1 (10 segundos) - eliminado 0
    fun updateBannerInterval(interval: Int) {
        prefs.bannerIntervalMinutes = interval
        _bannerInterval.value = interval
        Log.d("MainViewModel", "• Intervalo del banner actualizado: $interval")
    }
    
    fun updateBannerDuration(duration: Int) {
        val safeDuration = if (duration < 5) 5 else duration
        prefs.bannerDurationSeconds = safeDuration
        _bannerDuration.value = safeDuration
        Log.d("MainViewModel", "• Duración del banner actualizada: $safeDuration seg")
    }
    
    fun addMonitoredApp(packageName: String) {
        prefs.addMonitoredApp(packageName)
        _monitoredApps.value = prefs.monitoredApps.toList()
        Log.d("MainViewModel", "• App agregada: $packageName")
    }
    
    fun removeMonitoredApp(packageName: String) {
        prefs.removeMonitoredApp(packageName)
        _monitoredApps.value = prefs.monitoredApps.toList()
        Log.d("MainViewModel", "• App removida: $packageName")
    }
    
    fun updateDarkMode(isDark: Boolean) {
        prefs.isDarkMode = isDark
        _isDarkMode.value = isDark
        Log.d("MainViewModel", "• Modo oscuro actualizado: $isDark")
    }
    
    fun clearAllSettings() {
        prefs.clearAll()
        _showBanner.value = false
        _bannerInterval.value = 5 // Valor por defecto: 5 minutos
        _bannerDuration.value = 5
        _monitoredApps.value = emptyList()
        _isDarkMode.value = false
        
        Log.d("MainViewModel", "✅ TODA la configuración fue limpiada")
    }
    
    fun loadDailyTimeSummary() {
        viewModelScope.launch {
            try {
                _dailySummary.value = "Cargando..."
                
                val summary = withContext(Dispatchers.IO) {
                    getDailyTimeSummaryInternal()
                }
                
                _dailySummary.value = summary
                Log.d("MainViewModel", "✅ Resumen diario cargado")
                
            } catch (e: Exception) {
                _dailySummary.value = "Error al cargar resumen: ${e.message}"
                Log.e("MainViewModel", "❌ Error al cargar resumen diario", e)
            }
        }
    }
    
    private suspend fun getDailyTimeSummaryInternal(): String {
        return withContext(Dispatchers.IO) {
            try {
                // Obtener apps monitoreadas
                val monitoredPackages = prefs.monitoredApps.toList()
                
                // Obtener sesiones monitoreadas hoy
                val sessions = repository.getTodayMonitoredSessions(monitoredPackages)
                Log.d("MainViewModel", "• Sesiones monitoreadas hoy: ${sessions.size}")
                
                // Obtener tiempo total monitoreado hoy
                val totalMonitoredTime = repository.getTotalMonitoredTimeToday(monitoredPackages)
                
                // Obtener estadísticas por app
                val dailyStats = try {
                    repository.getDailyStatsOptimized()
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Usando fallback para estadísticas diarias: ${e.message}")
                    repository.getDailyStats()
                }
                
                // Filtrar solo apps monitoreadas
                val monitoredStats = dailyStats.filter { it.key in monitoredPackages }
                
                buildString {
                    append("📊 RESUMEN DIARIO\n")
                    append("Fecha: ${Date()}\n")
                    append("─".repeat(30))
                    append("\n\n")
                    
                    append("📈 ESTADÍSTICAS GENERALES\n")
                    append("• Apps monitoreadas: ${monitoredPackages.size}\n")
                    append("• Sesiones registradas: ${sessions.size}\n")
                    append("• Tiempo total monitoreado: ${formatTimeForDisplay(totalMonitoredTime)}\n\n")
                    
                    if (sessions.isNotEmpty() && monitoredStats.isNotEmpty()) {
                        append("⏱️ TIEMPO POR APLICACIÓN MONITOREADA\n")
                        
                        val sortedApps = monitoredStats.entries.sortedByDescending { it.value }
                        
                        sortedApps.forEachIndexed { index, (packageName, appTime) ->
                            val sessionCount = sessions.count { it.packageName == packageName }
                            
                            val appName = try {
                                val pm = getApplication<Application>().packageManager
                                val appInfo = pm.getApplicationInfo(packageName, 0)
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                packageName
                            }
                            
                            val percentage = if (totalMonitoredTime > 0) {
                                String.format("%.1f", (appTime.toDouble() / totalMonitoredTime * 100))
                            } else "0.0"
                            
                            append("${index + 1}. $appName\n")
                            append("   ▸ Tiempo: ${formatTimeForDisplay(appTime)} ($percentage%)\n")
                            append("   ▸ Sesiones: $sessionCount\n")
                        }
                    } else {
                        append("⚠️ No hay datos de uso para apps monitoreadas hoy.\n")
                        
                        // Mostrar las apps monitoreadas sin datos
                        if (monitoredPackages.isNotEmpty()) {
                            append("\n📱 Apps monitoreadas:\n")
                            monitoredPackages.forEachIndexed { index, packageName ->
                                val appName = try {
                                    val pm = getApplication<Application>().packageManager
                                    val appInfo = pm.getApplicationInfo(packageName, 0)
                                    pm.getApplicationLabel(appInfo).toString()
                                } catch (e: Exception) {
                                    packageName
                                }
                                append("${index + 1}. $appName\n")
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e("MainViewModel", "❌ Error interno al obtener resumen", e)
                "Error al obtener resumen: ${e.message}"
            }
        }
    }
    
    suspend fun getDatabaseStats(): DatabaseStats {
        return withContext(Dispatchers.IO) {
            try {
                repository.getDatabaseStats()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error al obtener estadísticas de BD", e)
                DatabaseStats(0, null, null)
            }
        }
    }
    
    fun cleanOldRecords() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteOldRecords()
                    Log.d("MainViewModel", "✅ Registros antiguos limpiados")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "❌ Error al limpiar registros antiguos", e)
            }
        }
    }
    
    fun cleanIncompleteSessions() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteIncompleteSessions()
                    Log.d("MainViewModel", "✅ Sesiones incompletas limpiadas")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "❌ Error al limpiar sesiones incompletas", e)
            }
        }
    }
    
    suspend fun getActiveSession(packageName: String): UsageSession? {
        return withContext(Dispatchers.IO) {
            try {
                repository.getActiveSession(packageName)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error al obtener sesión activa", e)
                null
            }
        }
    }
    
    private fun formatTimeForDisplay(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d h %02d m %02d s", hours, minutes, seconds)
        } else if (minutes > 0) {
            String.format("%d m %02d s", minutes, seconds)
        } else {
            String.format("%d s", seconds)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
        saveAllSettings()
        Log.d("MainViewModel", "🔄 ViewModel limpiado - configuración guardada")
    }
    
    // Data classes para estados
    data class ServiceStatus(
        val isRunning: Boolean,
        val overlayPermission: Boolean,
        val accessibilityPermission: Boolean
    )
    
    data class PermissionsStatus(
        val overlayPermission: Boolean,
        val accessibilityPermission: Boolean,
        val allPermissionsGranted: Boolean
    )
}

// 🔴 IMPORTANTE: Esta definición debe estar FUERA de la clase
data class DatabaseStats(
    val totalRecords: Int,
    val oldestRecord: Long?,
    val newestRecord: Long?
)