package com.gnzalobnites.appsusagemonitor

import com.gnzalobnites.appsusagemonitor.banner.BannerManager
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import java.util.*

class FocusAwareService : AccessibilityService() {

    private lateinit var userPreferences: UserPreferences
    private lateinit var database: AppDatabase
    private lateinit var bannerManager: BannerManager
    private var activeSession: UsageSession? = null
    
    // Variables para debounce
    private var lastPackageName: String? = null
    private var lastEventTime = 0L
    private val DEBOUNCE_DELAY_MS = 500L
    private val handler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    
    // Temporizador para evitar múltiples terminaciones rápidas
    private var lastSessionEndTime = 0L
    private val MIN_SESSION_END_INTERVAL_MS = 1000L
    
    // Job para corrutinas
    private var serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    // ViewModel simplificado
    private lateinit var serviceViewModel: SimpleServiceViewModel
    
    // Broadcast Receiver para comunicación con BannerManager
    private val bannerExitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "APP_EXIT_DETECTED" -> {
                    val packageName = intent.getStringExtra("packageName")
                    val timestamp = intent.getLongExtra("timestamp", 0)
                    Log.d(TAG, "📡 BannerManager detectó salida de app: $packageName en $timestamp")
                    
                    // Asegurar que la sesión termina
                    if (activeSession?.packageName == packageName) {
                        Log.d(TAG, "🛑 Terminando sesión por salida detectada")
                        endCurrentSession()
                    }
                }
                "BANNER_HIDDEN" -> {
                    Log.d(TAG, "📡 Banner ocultado completamente")
                }
            }
        }
    }
    
    companion object {
        const val TAG = "FocusAwareService"
        
        fun isServiceEnabled(context: Context): Boolean {
            val serviceName = ComponentName(context, FocusAwareService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            val isEnabled = enabledServices.contains(serviceName.flattenToString())
            Log.d(TAG, "Servicio habilitado: $isEnabled")
            return isEnabled
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceViewModel = SimpleServiceViewModel()
        Log.d(TAG, "✅ SimpleServiceViewModel creado")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Servicio de accesibilidad CONECTADO")
        
        try {
            userPreferences = UserPreferences.getInstance(this)
            database = AppDatabase.getDatabase(this)
            bannerManager = BannerManager(this)
            bannerManager.initialize(userPreferences, database)
            
            // Registrar receiver para comunicación bidireccional
            registerBannerReceiver()
            
            // Notificar que el servicio está listo
            sendServiceStateBroadcast(true)
            
            // Verificar permiso de UsageStats
            checkUsageStatsPermission()
            
            Log.d(TAG, "✅ Servicio completamente inicializado")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en onServiceConnected: ${e.message}", e)
        }
    }

    private fun registerBannerReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction("APP_EXIT_DETECTED")
                addAction("FORCE_HIDE_BANNER")
            }
            registerReceiver(bannerExitReceiver, filter)
            Log.d(TAG, "✅ Receptor de banner registrado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registrando receptor: ${e.message}")
        }
    }

    private fun checkUsageStatsPermission() {
        if (!bannerManager.hasUsageStatsPermission()) {
            Log.d(TAG, "⚠️ Permiso UsageStats no concedido - se recomienda para mayor precisión")
            // No hacemos nada, el BannerManager usará fallback
        } else {
            Log.d(TAG, "✅ Permiso UsageStats concedido - máxima precisión")
        }
    }

    private fun sendServiceStateBroadcast(isRunning: Boolean) {
        val intent = Intent("SERVICE_STATE_CHANGED").apply {
            putExtra("isRunning", isRunning)
        }
        sendBroadcast(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        
        val currentTime = System.currentTimeMillis()
        
        // Debounce: ignorar eventos muy seguidos del mismo paquete
        if (packageName == lastPackageName && (currentTime - lastEventTime) < DEBOUNCE_DELAY_MS) {
            Log.d(TAG, "⏳ Debounce: ignorando evento rápido para $packageName")
            return
        }
        
        lastPackageName = packageName
        lastEventTime = currentTime
        
        // Cancelar debounce anterior
        debounceRunnable?.let { handler.removeCallbacks(it) }
        
        // Usar debounce para procesar después de un pequeño delay
        debounceRunnable = Runnable {
            processAppChange(packageName)
        }
        handler.postDelayed(debounceRunnable!!, 300L)
    }

    private fun processAppChange(packageName: String) {
        Log.d(TAG, "🔍 Procesando cambio a app: $packageName")
        
        // Filtrar apps del sistema
        if (isSystemApp(packageName)) {
            Log.d(TAG, "⚙️ App del sistema detectada: $packageName")
            
            if (shouldKeepSessionActive(packageName)) {
                Log.d(TAG, "🛡️ App de sistema seguro - NO terminar sesión")
                return
            } else if (activeSession != null) {
                Log.d(TAG, "📱 App del sistema relevante → terminar sesión")
                endCurrentSession()
            }
            return
        }

        Log.d(TAG, "📱 App de usuario detectada: $packageName")

        // Verificar si esta app está monitoreada
        val isMonitored = userPreferences.showBanner && userPreferences.isMonitored(packageName)
        
        // Manejar cambios de app
        handleAppTransition(packageName, isMonitored)
        
        // Actualizar ViewModel
        updateViewModel(packageName)
    }

    private fun isSystemApp(packageName: String): Boolean {
        val systemAppsToFilter = listOf(
            "android",
            "com.android.systemui",
            this.packageName,
            "com.google.android.inputmethod.latin",
            "com.google.android.inputmethod",
            "com.sec.android.inputmethod",
            "com.samsung.android.honeyboard",
            "com.samsung.android.svoiceime",
            "com.samsung.android.clipboardsaveservice",
            "com.swiftkey.swiftkeyconfigurator",
            "com.touchtype.swiftkey",
            "com.gboard",
            "com.samsung.android.sidegesturepad",
            "com.samsung.android.app.gestureservice",
            "com.samsung.android.onehandedmode",
            "com.samsung.android.edgepanel",
            "com.samsung.android.edge.feature",
            "com.samsung.android.service.edge",
            "com.samsung.android.service.peoplestripe",
            "com.samsung.android.service.gesture",
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.easysetup",
            "com.android.systemui.quickpanel",
            "com.android.systemui.qspanel",
            "com.android.systemui.statusbar",
            "com.android.systemui.notification",
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.google.android.providers.media.module",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.samsung.android.app.aodservice",
            "com.samsung.android.bixby.agent",
            "com.samsung.android.bixby.wakeup",
            "com.samsung.android.visionintelligence",
            "com.samsung.android.app.settings.bixby",
            "com.samsung.android.app.routines",
            "com.samsung.android.app.reminder",
            "com.android.systemui.keyguard",
            "com.samsung.android.kidsinstaller",
            "com.samsung.android.app.screenrecorder",
            "com.samsung.android.service.livedrawing",
            "com.samsung.android.service.airview",
            "com.samsung.android.app.cameraedge",
            "com.samsung.android.service.aircommand"
        )
        
        return systemAppsToFilter.contains(packageName)
    }

    private fun shouldKeepSessionActive(packageName: String): Boolean {
        val systemAppsThatDontEndSession = listOf(
            "com.google.android.inputmethod",
            "com.sec.android.inputmethod",
            "com.samsung.android.honeyboard",
            "com.samsung.android.svoiceime",
            "com.samsung.android.sidegesturepad",
            "com.samsung.android.app.gestureservice",
            "com.samsung.android.edgepanel",
            "com.android.systemui",
            "com.android.systemui.statusbar",
            "com.android.systemui.notification",
            "com.android.systemui.quickpanel",
            "com.android.systemui.qspanel",
            "com.android.systemui.keyguard",
            this.packageName
        )
        
        return systemAppsThatDontEndSession.any { packageName.contains(it) }
    }

    private fun handleAppTransition(packageName: String, isMonitored: Boolean) {
        // 1. Siempre terminar sesión anterior si existe Y la nueva app es DIFERENTE
        if (activeSession != null && activeSession?.packageName != packageName) {
            Log.d(TAG, "🔄 Cambiando de app: ${activeSession?.packageName} → $packageName")
            endCurrentSession()
            
            // Pequeña pausa para asegurar que la sesión anterior se termina
            handler.postDelayed({
                // 2. Si la nueva app está monitoreada, iniciar nueva sesión
                if (isMonitored) {
                    Log.d(TAG, "✅ App monitoreada → INICIAR nueva sesión: $packageName")
                    startNewSession(packageName)
                } else {
                    Log.d(TAG, "📌 App NO monitoreada: $packageName (sin nueva sesión)")
                }
            }, 100)
        } 
        // 3. Si NO hay sesión activa y la app está monitoreada, iniciar sesión
        else if (activeSession == null && isMonitored) {
            Log.d(TAG, "✅ App monitoreada → INICIAR primera sesión: $packageName")
            startNewSession(packageName)
        }
        // 4. Si es la MISMA app y está monitoreada, solo continuar
        else if (activeSession?.packageName == packageName && isMonitored) {
            Log.d(TAG, "📌 Continuando sesión para: $packageName")
            
            // Verificar si la sesión es muy vieja (más de 1 hora sin banner)
            val sessionAge = System.currentTimeMillis() - activeSession!!.startTime
            if (sessionAge > TimeUnit.HOURS.toMillis(1)) {
                Log.d(TAG, "🔄 Sesión muy vieja (${sessionAge/60000}min), reiniciando...")
                restartSession(packageName)
            }
        }
    }

    private fun startNewSession(packageName: String) {
        val currentTime = System.currentTimeMillis()
        
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val todayDate = calendar.timeInMillis
        
        val newSession = UsageSession(
            packageName = packageName,
            startTime = currentTime,
            endTime = null,
            date = todayDate
        )

        serviceScope.launch {
            try {
                Log.d(TAG, "💾 Guardando sesión para: $packageName")
                
                val sessionId = withContext(Dispatchers.IO) {
                    database.usageDao().insert(newSession)
                }
                
                activeSession = newSession.copy(id = sessionId)
                bannerManager.startSession(packageName)
                
                Log.d(TAG, "🎬 Sesión INICIADA con ID: $sessionId")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error iniciando sesión: ${e.message}", e)
            }
        }
    }

    private fun restartSession(packageName: String) {
        serviceScope.launch {
            try {
                endCurrentSession()
                
                delay(500) // Pequeña pausa
                
                startNewSession(packageName)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error reiniciando sesión: ${e.message}", e)
            }
        }
    }

    // En FocusAwareService.kt, asegúrate de que endCurrentSession() sea robusto:

private fun endCurrentSession() {
    val currentTime = System.currentTimeMillis()
    
    // Evitar múltiples terminaciones rápidas
    if ((currentTime - lastSessionEndTime) < MIN_SESSION_END_INTERVAL_MS) {
        Log.d(TAG, "⏱️ Evitando terminación rápida consecutiva")
        return
    }
    
    activeSession?.let { session ->
        if (session.endTime == null) {
            val duration = currentTime - session.startTime
            val updatedSession = session.copy(endTime = currentTime)
            
            serviceScope.launch {
                try {
                    Log.d(TAG, "⏹️ Finalizando sesión para: ${session.packageName} (duración: ${duration}ms)")
                    
                    withContext(Dispatchers.IO) {
                        database.usageDao().update(updatedSession)
                    }
                    
                    activeSession = null
                    lastSessionEndTime = currentTime
                    
                    // IMPORTANTE: Llamar a endSession() del BannerManager
                    bannerManager.endSession()
                    
                    Log.d(TAG, "✅ Sesión FINALIZADA y guardada")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error finalizando sesión: ${e.message}", e)
                }
            }
        }
    }
}

    private fun updateViewModel(packageName: String) {
        val currentSessionDuration = if (activeSession != null) {
            System.currentTimeMillis() - activeSession!!.startTime
        } else {
            0L
        }
        
        serviceViewModel.updateFromService(
            currentPackage = packageName,
            duration = currentSessionDuration,
            bannerVisible = activeSession != null
        )
    }

    // ======================================================
    // MÉTODOS PARA BANNERS DE PRUEBA (compatibilidad)
    // ======================================================

    fun showTestBanner() {
        handler.post {
            try {
                Log.d(TAG, "🧪 Mostrando banner de prueba")
                bannerManager.showTestBanner()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en banner de prueba: ${e.message}")
            }
        }
    }

    fun showCustomBanner(message: String, duration: Int) {
        handler.post {
            try {
                Log.d(TAG, "📨 Mostrando banner personalizado: '$message'")
                bannerManager.showTestBanner(message) // Usamos showTestBanner con mensaje
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en banner personalizado: ${e.message}")
            }
        }
    }

    // ======================================================
    // CICLO DE VIDA DEL SERVICIO
    // ======================================================

    override fun onInterrupt() {
        Log.d(TAG, "⚠️ Servicio interrumpido")
        endCurrentSession()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "🔌 Servicio desvinculado")
        
        try {
            endCurrentSession()
            bannerManager.cleanup()
            
            // Cancelar todas las corrutinas
            serviceScope.cancel()
            
            // Desregistrar receiver
            try {
                unregisterReceiver(bannerExitReceiver)
                Log.d(TAG, "✅ Receptor desregistrado")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error al desregistrar receptor: ${e.message}")
            }
            
            // Limpiar handlers
            debounceRunnable?.let { handler.removeCallbacks(it) }
            handler.removeCallbacksAndMessages(null)
            
            // Notificar que el servicio se detuvo
            sendServiceStateBroadcast(false)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en onUnbind: ${e.message}", e)
        }
        
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "💥 Servicio destruido")
        
        try {
            serviceScope.cancel()
            bannerManager.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error en onDestroy: ${e.message}")
        }
    }

    // ======================================================
    // CLASE VIEWMODEL SIMPLIFICADA
    // ======================================================

    class SimpleServiceViewModel {
        private var currentPackage: String? = null
        private var currentDuration: Long = 0L
        private var bannerVisible: Boolean = false
        
        fun updateFromService(
            currentPackage: String? = null,
            duration: Long = 0L,
            bannerVisible: Boolean = false,
            totalTime: String = "0m"
        ) {
            currentPackage?.let { this.currentPackage = it }
            if (duration > 0) this.currentDuration = duration
            this.bannerVisible = bannerVisible
            Log.d("SimpleServiceViewModel", "Actualizado: $currentPackage, $currentDuration ms")
        }
        
        fun getCurrentPackage(): String? = currentPackage
        fun getCurrentDuration(): Long = currentDuration
        fun isBannerVisible(): Boolean = bannerVisible
    }
}