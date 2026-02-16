package com.gnzalobnites.appsusagemonitor.banner

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.UserPreferences
import com.gnzalobnites.appsusagemonitor.AppDatabase
import kotlinx.coroutines.*
import java.util.*

class BannerManager(private val context: Context) {

    // Componentes
    private lateinit var uiController: BannerUIController
    private lateinit var scheduler: BannerScheduler
    private lateinit var foregroundMonitor: BannerForegroundMonitor
    private lateinit var testUtils: BannerTestUtils
    
    // Dependencias
    private lateinit var userPreferences: UserPreferences
    private lateinit var database: AppDatabase
    
    // Estado
    private var bannerState = BannerState.HIDDEN
    private var currentSession: SessionInfo? = null
    private var isBannerShowing = false
    
    // WindowManager
    private var windowManager: WindowManager? = null
    
    // Handlers para postDelayed (compatible con Kotlin 1.3.72)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var removeBannerRunnable: Runnable? = null
    
    // Corrutinas
    private var updateJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Broadcast receiver
    private val appExitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "APP_EXIT_DETECTED") {
                handleAppExit()
            }
        }
    }

    // ======================================================
    // INICIALIZACIÓN
    // ======================================================

    fun initialize(userPrefs: UserPreferences, db: AppDatabase) {
        this.userPreferences = userPrefs
        this.database = db
        
        this.windowManager = ContextCompat.getSystemService(context, WindowManager::class.java)
        
        // Inicializar componentes
        uiController = BannerUIController(context)
        scheduler = BannerScheduler(userPreferences) { showBanner() }
        foregroundMonitor = BannerForegroundMonitor(context) { handleAppExit() }
        testUtils = BannerTestUtils(context).apply { initialize(windowManager!!) }
        
        foregroundMonitor.initialize()
        
        // Registrar receiver
        context.registerReceiver(appExitReceiver, IntentFilter("APP_EXIT_DETECTED"))
        
        Log.d("BannerManager", "✅ Inicializado con componentes separados")
    }

    // ======================================================
    // GESTIÓN DE SESIÓN
    // ======================================================

    fun startSession(packageName: String) {
        Log.d("BannerManager", "🚀 INICIANDO SESIÓN para: $packageName")
        
        val appName = getAppName(packageName)
        currentSession = SessionInfo(packageName, System.currentTimeMillis(), appName)
        
        bannerState = BannerState.HIDDEN
        foregroundMonitor.startMonitoring(packageName)
        
        if (userPreferences.showBanner) {
            scheduler.scheduleNextBanner(bannerState, true)
        }
    }

    fun endSession() {
        Log.d("BannerManager", "⏹️ FINALIZANDO SESIÓN")
        
        foregroundMonitor.stopMonitoring()
        scheduler.cancelAll()
        
        // ¡CRÍTICO! Eliminar el banner COMPLETAMENTE y asegurar que no queden residuos
        forceRemoveBanner()
        
        currentSession = null
        bannerState = BannerState.HIDDEN
    }

    // ======================================================
    // MOSTRAR/OCULTAR BANNER
    // ======================================================

    private fun showBanner() {
        val session = currentSession ?: return
        
        if (!foregroundMonitor.isAppInForeground(session.packageName)) {
            Log.d("BannerManager", "⏭️ App no en foreground")
            return
        }
        
        if (bannerState != BannerState.HIDDEN) return
        
        try {
            uiController.createBannerView()
            
            // Configurar con listener de clicks
            uiController.setupWaitingUI(session) { 
                onBannerClicked() 
            }
            
            // El banner minimizado DEBE recibir clicks para expandirse
            val params = createBannerParams(isInteractive = true)
            windowManager?.addView(uiController.bannerView, params)
            
            bannerState = BannerState.VISIBLE_WAITING
            isBannerShowing = true
            scheduler.bannerShown()
            
            startLiveUpdates()
            
        } catch (e: Exception) {
            Log.e("BannerManager", "Error: ${e.message}")
            bannerState = BannerState.HIDDEN
        }
    }

    private fun onBannerClicked() {
        Log.d("BannerManager", "👆 Banner clickeado en estado: $bannerState")
        
        when (bannerState) {
            BannerState.VISIBLE_WAITING -> expandBanner()
            BannerState.VISIBLE_EXPANDED -> closeBannerAndScheduleNext()
            else -> {}
        }
    }

    private fun expandBanner() {
        managerScope.launch {
            val session = currentSession ?: return@launch
            val timeStats = getCurrentTimeStats()
            
            // Guardar referencia antes de eliminar
            val currentView = uiController.bannerView
            
            if (currentView != null && windowManager != null) {
                try {
                    // PASO 1: Eliminar completamente el view actual
                    windowManager?.removeView(currentView)
                    
                    // Pequeña pausa para asegurar que el sistema procesa la eliminación
                    delay(50)
                    
                    // PASO 2: Actualizar UI al estado expandido
                    uiController.expandBanner(timeStats, session.appName)
                    
                    // PASO 3: Crear NUEVOS parámetros para el estado expandido
                    val expandedParams = createBannerParams(isInteractive = true)
                    
                    // PASO 4: Volver a agregar el view con los nuevos parámetros
                    windowManager?.addView(uiController.bannerView, expandedParams)
                    
                    bannerState = BannerState.VISIBLE_EXPANDED
                    
                    Log.d("BannerManager", "🔄 Banner RECREADO para modo expandido")
                    
                } catch (e: Exception) {
                    Log.e("BannerManager", "Error en expansión: ${e.message}")
                    // En caso de error, intentar recuperar
                    bannerState = BannerState.HIDDEN
                }
            }
        }
    }

    private fun closeBannerAndScheduleNext() {
        // Animación de cierre
        uiController.hideWithAnimation {
            // Eliminar completamente después de la animación
            forceRemoveBanner()
            
            isBannerShowing = false
            bannerState = BannerState.HIDDEN
            scheduler.scheduleNextBanner(bannerState, currentSession != null)
        }
        
        stopLiveUpdates()
    }

    /**
     * Elimina el banner de forma forzada y garantiza que no queden residuos
     */
    private fun forceRemoveBanner() {
        try {
            uiController.bannerView?.let { view ->
                // Cancelar cualquier Runnable pendiente
                removeBannerRunnable?.let { mainHandler.removeCallbacks(it) }
                
                // Eliminar del WindowManager
                windowManager?.removeView(view)
                
                // Limpiar referencias
                uiController.bannerView = null
                
                Log.d("BannerManager", "🗑️ Banner eliminado completamente")
            }
        } catch (e: Exception) {
            Log.e("BannerManager", "Error al eliminar banner: ${e.message}")
            // Asegurar que la referencia se limpia incluso si hay error
            uiController.bannerView = null
        }
        
        isBannerShowing = false
    }

    private fun handleAppExit() {
        Log.d("BannerManager", "👋 Usuario salió de la app - ELIMINANDO BANNER")
        
        // ¡CRÍTICO! Al salir de la app monitoreada, el banner debe DESAPARECER COMPLETAMENTE
        forceRemoveBanner()
        endSession()
        
        // Enviar broadcast para notificar que el banner ya no está
        val intent = Intent("BANNER_HIDDEN").apply {
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
    }

    // ======================================================
    // WINDOW PARAMS - UNIFICADO
    // ======================================================

    /**
     * Parámetros unificados para banner
     * @param isInteractive true para recibir clicks, false para modo solo visual
     */
    private fun createBannerParams(isInteractive: Boolean): WindowManager.LayoutParams {
        val flags = if (isInteractive) {
            // Modo interactivo: recibe clicks pero NO bloquea fuera del área
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            // Modo no interactivo: no recibe clicks, no bloquea nada
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            flags,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = 100
        }
    }

    // ======================================================
    // ACTUALIZACIONES EN VIVO
    // ======================================================

    private val UPDATE_INTERVAL_MS = 1000L

    private fun startLiveUpdates() {
        stopLiveUpdates()
        
        updateJob = managerScope.launch {
            while (bannerState != BannerState.HIDDEN && currentSession != null) {
                try {
                    val session = currentSession ?: break
                    val timeStats = getCurrentTimeStats()
                    
                    if (bannerState == BannerState.VISIBLE_EXPANDED) {
                        uiController.updateExpandedContent(timeStats, session.appName)
                    } else {
                        uiController.updateMinimizedContent(timeStats, session.appName)
                    }
                    delay(UPDATE_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e("BannerManager", "Error en live updates: ${e.message}")
                    break
                }
            }
        }
    }

    private fun stopLiveUpdates() {
        updateJob?.cancel()
        updateJob = null
    }

    // ======================================================
    // UTILIDADES
    // ======================================================

    private suspend fun getCurrentTimeStats(): TimeStats {
        val session = currentSession ?: return TimeStats(0, 0)
        val todayTotal = getTodayTotal(session.packageName)
        return TimeStats(session.getDuration(), todayTotal)
    }

    private suspend fun getTodayTotal(packageName: String): Long {
        return try {
            val now = System.currentTimeMillis()
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayMidnight = calendar.timeInMillis
            
            withContext(Dispatchers.IO) {
                database.usageDao().getAppTimeToday(
                    packageName,
                    todayMidnight,
                    now
                )
            }
        } catch (e: Exception) {
            Log.e("BannerManager", "Error obteniendo tiempo total: ${e.message}")
            0L
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    // ======================================================
    // PERMISOS
    // ======================================================

    fun hasUsageStatsPermission(): Boolean = foregroundMonitor.hasUsageStatsPermission()

    fun requestUsageStatsPermission(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            Toast.makeText(context, 
                "🔍 Busca '${activity.getString(R.string.app_name)}' en la lista", 
                Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(context, "No se pudo abrir configuración", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ======================================================
    // MÉTODOS DE PRUEBA
    // ======================================================

    fun showTestBanner(testMessage: String = "🧪 BANNER DE PRUEBA") {
        testUtils.showTestBanner(testMessage) {
            // Banner cerrado
        }
    }

    fun showTikTokBanner(message: String, durationSeconds: Int) {
        showTestBanner(message)
    }

    fun forceStopTestBanner() {
        testUtils.hideTestBanner()
    }

    // ======================================================
    // LIMPIEZA
    // ======================================================

    fun cleanup() {
        try {
            context.unregisterReceiver(appExitReceiver)
        } catch (e: Exception) {}
        
        foregroundMonitor.stopMonitoring()
        scheduler.cancelAll()
        stopLiveUpdates()
        forceRemoveBanner()
        testUtils.hideTestBanner()
        managerScope.cancel()
    }

    fun setNinjaMode(enabled: Boolean) {
        Log.d("BannerManager", "🎨 Modo premium ${if (enabled) "activado" else "desactivado"}")
    }
}