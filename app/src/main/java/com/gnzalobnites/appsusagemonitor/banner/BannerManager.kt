package com.gnzalobnites.appsusagemonitor.banner

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gnzalobnites.appsusagemonitor.AppDatabase
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.UserPreferences
import kotlinx.coroutines.*

class BannerManager(private val context: Context) {

    private lateinit var userPreferences: UserPreferences
    private lateinit var database: AppDatabase
    private lateinit var uiController: BannerUIController
    private lateinit var scheduler: BannerScheduler
    private lateinit var foregroundMonitor: BannerForegroundMonitor
    private lateinit var testUtils: BannerTestUtils

    private var bannerState = BannerState.HIDDEN
    private var currentSession: SessionInfo? = null
    private var windowManager: WindowManager? = null
    private var isBannerAddedToWindow = false

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updateJob: Job? = null

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

        uiController = BannerUIController(context).apply {
            createBannerView()
        }

        scheduler = BannerScheduler(userPreferences) { showBanner() }
        foregroundMonitor = BannerForegroundMonitor(context) { handleAppExit() }.also { it.initialize() }
        testUtils = BannerTestUtils(context).apply { initialize(windowManager!!) }

        // Registrar receiver
        try {
            val intentFilter = IntentFilter("APP_EXIT_DETECTED")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.registerReceiver(
                    appExitReceiver,
                    intentFilter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(appExitReceiver, intentFilter)
            }
            Log.d("BannerManager", "✅ BroadcastReceiver registrado")
        } catch (e: Exception) {
            Log.e("BannerManager", "❌ Error registrando receiver: ${e.message}")
        }

        Log.d("BannerManager", "✅ Inicializado.")
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
        stopLiveUpdates()
        removeBannerFromWindow()
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
            if (!isBannerAddedToWindow) {
                val params = createBannerParams()
                windowManager?.addView(uiController.bannerView, params)
                isBannerAddedToWindow = true
            }

            // Configurar en modo minimizado
            uiController.setupWaitingUI(session) { onBannerClicked() }
            bannerState = BannerState.VISIBLE_WAITING
            scheduler.bannerShown()
            startLiveUpdates()
            
            Log.d("BannerManager", "✅ Banner mostrado en modo minimizado")

        } catch (e: Exception) {
            Log.e("BannerManager", "❌ Error mostrando banner: ${e.message}")
            bannerState = BannerState.HIDDEN
        }
    }

    private fun onBannerClicked() {
        Log.d("BannerManager", "👆 Banner clickeado - Estado actual: $bannerState")
        
        // Si está en animación, ignorar clics
        if (uiController.isAnimating) {
            Log.d("BannerManager", "⏳ En animación, ignorando click")
            return
        }
        
        when (bannerState) {
            BannerState.VISIBLE_WAITING -> {
                expandBanner()
            }
            BannerState.VISIBLE_EXPANDED -> {
                closeBannerAndScheduleNext()
            }
            else -> {
                Log.d("BannerManager", "⚠️ Click en estado inesperado: $bannerState")
            }
        }
    }

    private fun expandBanner() {
        managerScope.launch {
            val session = currentSession ?: return@launch
            val timeStats = getCurrentTimeStats()
            
            uiController.expandBanner(timeStats, session.appName)
            bannerState = BannerState.VISIBLE_EXPANDED
            
            Log.d("BannerManager", "🔄 Banner expandido")
        }
    }

    private fun closeBannerAndScheduleNext() {
        Log.d("BannerManager", "🔴 Cerrando banner y programando siguiente")
        
        // 1. DETENER ACTUALIZACIONES INMEDIATAMENTE
        stopLiveUpdates()
        
        // 2. Cambiar estado ANTES de la animación
        bannerState = BannerState.HIDDEN
        
        // 3. Animar cierre
        uiController.hideWithAnimation {
            // 4. DESPUÉS de la animación, remover de la ventana
            removeBannerFromWindow()
            
            // 5. Programar el próximo banner si la sesión sigue activa
            if (currentSession != null) {
                scheduler.scheduleNextBanner(bannerState, true)
            }
            
            Log.d("BannerManager", "✅ Banner cerrado completamente")
        }
    }

    private fun removeBannerFromWindow() {
        try {
            if (isBannerAddedToWindow) {
                uiController.bannerView?.let { view ->
                    // Asegurar que no hay animaciones pendientes
                    view.animate().cancel()
                    windowManager?.removeView(view)
                    isBannerAddedToWindow = false
                    Log.d("BannerManager", "🗑️ Banner quitado del window manager")
                }
            }
        } catch (e: Exception) {
            Log.e("BannerManager", "Error al quitar banner: ${e.message}")
            isBannerAddedToWindow = false
        }
    }

    private fun handleAppExit() {
        Log.d("BannerManager", "👋 Usuario salió - OCULTANDO BANNER")
        removeBannerFromWindow()
        endSession()
        context.sendBroadcast(Intent("BANNER_HIDDEN"))
    }

    // ======================================================
    // WINDOW PARAMS
    // ======================================================
    private fun createBannerParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
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
                    
                    when (bannerState) {
                        BannerState.VISIBLE_EXPANDED -> {
                            uiController.updateExpandedContent(timeStats, session.appName)
                        }
                        BannerState.VISIBLE_WAITING -> {
                            uiController.updateMinimizedContent(timeStats, session.appName)
                        }
                        else -> {}
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
        return withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val todayMidnight = calendar.timeInMillis
                database.usageDao().getAppTimeToday(packageName, todayMidnight, now)
            } catch (e: Exception) {
                Log.e("BannerManager", "Error DB: ${e.message}")
                0L
            }
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
    // PERMISOS Y PRUEBAS
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
                context.getString(R.string.permission_usage_stats_instructions,
                    context.getString(R.string.app_name)),
                Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(context, context.getString(R.string.error_open_link), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showTestBanner(testMessage: String = context.getString(R.string.log_test_banner)) {
        testUtils.showTestBanner(testMessage) { /* Banner cerrado */ }
    }
    
    fun showTikTokBanner(message: String, durationSeconds: Int) = showTestBanner(message)
    fun forceStopTestBanner() = testUtils.hideTestBanner()
    fun setNinjaMode(enabled: Boolean) { 
        Log.d("BannerManager", "Modo premium ${if (enabled) "activado" else "desactivado"}") 
    }

    // ======================================================
    // LIMPIEZA
    // ======================================================
    fun cleanup() {
        try {
            context.unregisterReceiver(appExitReceiver)
        } catch (e: Exception) {
            Log.e("BannerManager", "Error unregistering receiver: ${e.message}")
        }
        
        foregroundMonitor.stopMonitoring()
        scheduler.cancelAll()
        stopLiveUpdates()
        removeBannerFromWindow()
        testUtils.hideTestBanner()
        managerScope.cancel()
    }
}