package com.gnzalobnites.appsusagemonitor.banner

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
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
    
    // Corrutinas
    private var updateJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Broadcast receiver
    private val appExitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "FORCE_HIDE_BANNER") {
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
        
        val windowManager = ContextCompat.getSystemService(context, WindowManager::class.java)!!
        
        // Inicializar componentes
        uiController = BannerUIController(context)
        scheduler = BannerScheduler(userPreferences) { showBannerWaiting() }
        foregroundMonitor = BannerForegroundMonitor(context) { handleAppExit() }
        testUtils = BannerTestUtils(context).apply { initialize(windowManager) }
        
        foregroundMonitor.initialize()
        
        // Registrar receiver
        context.registerReceiver(appExitReceiver, IntentFilter("FORCE_HIDE_BANNER"))
        
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
        hideBannerImmediately()
        
        currentSession = null
        bannerState = BannerState.HIDDEN
    }

    // ======================================================
    // MOSTRAR/OCULTAR BANNER
    // ======================================================

    private fun showBannerWaiting() {
        val session = currentSession ?: return
        
        if (!foregroundMonitor.isAppInForeground(session.packageName)) {
            Log.d("BannerManager", "⏭️ App no en foreground")
            return
        }
        
        if (bannerState != BannerState.HIDDEN) return
        
        try {
            uiController.createBannerView()
            uiController.setupWaitingUI(session) { onBannerClicked() }
            
            val windowManager = ContextCompat.getSystemService(context, WindowManager::class.java)
            val params = createWindowParams()
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
            
            uiController.expandBanner(timeStats, session.appName)
            bannerState = BannerState.VISIBLE_EXPANDED
        }
    }

    private fun closeBannerAndScheduleNext() {
        uiController.hideWithAnimation {
            isBannerShowing = false
            bannerState = BannerState.HIDDEN
            scheduler.scheduleNextBanner(bannerState, currentSession != null)
        }
        
        stopLiveUpdates()
    }

    private fun hideBannerImmediately() {
        stopLiveUpdates()
        foregroundMonitor.stopMonitoring()
        
        uiController.bannerView?.let { view ->
            try {
                ContextCompat.getSystemService(context, WindowManager::class.java)?.removeView(view)
            } catch (e: Exception) {}
        }
        
        isBannerShowing = false
        bannerState = BannerState.HIDDEN
    }

    private fun handleAppExit() {
        Log.d("BannerManager", "👋 Usuario salió de la app")
        hideBannerImmediately()
        endSession()
    }

    // ======================================================
    // ACTUALIZACIONES EN VIVO (CORREGIDO CON CORRUTINAS)
    // ======================================================

    private val UPDATE_INTERVAL_MS = 1000L

    private fun startLiveUpdates() {
        stopLiveUpdates()
        
        updateJob = managerScope.launch {
            while (bannerState != BannerState.HIDDEN) {
                try {
                    val session = currentSession
                    if (session != null) {
                        val timeStats = getCurrentTimeStats() // ¡Ahora es suspend!
                        
                        if (bannerState == BannerState.VISIBLE_EXPANDED) {
                            uiController.updateExpandedContent(timeStats, session.appName)
                        } else {
                            uiController.updateMinimizedContent(timeStats, session.appName)
                        }
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
    // UTILIDADES (CORREGIDAS)
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

    private fun createWindowParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = 100
        }
    }

    // ======================================================
    // PERMISOS (DELEGADOS)
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
        hideBannerImmediately()
        testUtils.hideTestBanner()
        managerScope.cancel()
    }

    fun setNinjaMode(enabled: Boolean) {
        Log.d("BannerManager", "🎨 Modo premium ${if (enabled) "activado" else "desactivado"}")
    }
}