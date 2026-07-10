package com.gnzalobnites.appsusagemonitor.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.gnzalobnites.appsusagemonitor.MyApplication
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import com.gnzalobnites.appsusagemonitor.data.repository.AppRepository
import com.gnzalobnites.appsusagemonitor.data.repository.UsageRepository
import com.gnzalobnites.appsusagemonitor.utils.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure

class MonitoringService : AccessibilityService() {

    private lateinit var repository: AppRepository
    private lateinit var usageRepository: UsageRepository
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val eventChannel = Channel<MonitorEvent>(64)
    
    private var sessionUpdateJob: Job? = null
    private var goalCheckJob: Job? = null
    private var screenTimeNotificationJob: Job? = null
    private var bubbleActive = false
    private var currentPackageName: String? = null
    private var currentMonitoredApp: MonitoredApp? = null
    private var sessionStartTime: Long = 0L
    
    private var lastNotifiedHours: Long = -1L
    private var lastNotifiedMinutes: Long = -1L
    
    private var lastStillActivePackage: String? = null
    private var lastStillActiveTime: Long = 0L
    private var lastAppChangeTime: Long = 0L

    // Tiempo extra activo: mientras sea true, se suprimen las notificaciones de meta alcanzada
    private var isExtraTimeActive = false

    companion object {
        private const val TAG = "MonitoringService"
        private const val STILL_ACTIVE_THROTTLE_MS = 800L
        private const val SCREEN_TIME_UPDATE_INTERVAL = 60000L
        private const val GOAL_CHECK_INTERVAL = 5000L
        private const val APP_CHANGE_DEBOUNCE_MS = 500L
    }

    private sealed interface MonitorEvent {
        data class AppChanged(val packageName: String, val timestamp: Long) : MonitorEvent
        data object CheckScreenTime : MonitorEvent
        data class BubbleClosed(val timestamp: Long) : MonitorEvent
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        repository = MyApplication.appRepository
        usageRepository = MyApplication.usageRepository
        
        createNotificationChannel()
        startEventLoop()
        startScreenTimeNotification()
    }

    private fun startEventLoop() {
        serviceScope.launch {
            for (event in eventChannel) {
                try {
                    processEvent(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing event: $event", e)
                }
            }
        }
    }

    private suspend fun processEvent(event: MonitorEvent) {
        when (event) {
            is MonitorEvent.AppChanged -> handleAppChanged(event)
            MonitorEvent.CheckScreenTime -> updateScreenTimeNotification()
            is MonitorEvent.BubbleClosed -> handleBubbleClosed(event)
        }
    }

    // Filtrar ventanas irrelevantes
    private fun isRelevantWindow(event: AccessibilityEvent): Boolean {
        val packageName = event.packageName?.toString() ?: return false
        val className = event.className?.toString() ?: return false
        
        // 1. Ignorar paquetes exentos
        if (isPackageExempt(packageName)) {
            Log.d(TAG, "Ignoring exempt package: $packageName")
            return false
        }
        
        // 2. Ignorar ventanas de sistema/UI que causan falsos cambios
        val ignoredClassPatterns = listOf(
            "PopupWindow",
            "Dialog",
            "Toast",
            "AlertController",
            "ChooserActivity",
            "ResolverActivity",
            "PermissionController",
            "MenuPopupWindow",
            "BottomSheetDialog",
            "InputMethod",
            "MultiWindowToggle",
            "PanelBar",
            "NotificationPanelView",
            "StatusBar",
            "NavigationBar",
            "RecentTasksView",
            "RecentsActivity",
            "ScreenPinningRequest",
            "Keyguard",
            "Launcher"
        )
        
        if (ignoredClassPatterns.any { className.contains(it) }) {
            Log.d(TAG, "Ignoring system UI/dialog: $className")
            return false
        }
        
        // 3. Verificar que sea una ventana principal (Activity)
        val isActivity = className.contains("Activity") || 
                         className.contains("Fragment") ||
                         className.contains("ViewGroup") ||
                         packageName.contains("launcher") ||
                         packageName.contains("home")
        
        if ((packageName.contains("android") || packageName.contains("system")) && !isActivity) {
            Log.d(TAG, "Ignoring system window without activity: $className")
            return false
        }
        
        // 4. Verificar que el evento tenga contenido significativo
        if (event.text.isEmpty() && event.contentDescription == null) {
            if (!packageName.contains("launcher") && !packageName.contains("home")) {
                Log.d(TAG, "Window without content: $packageName, $className")
            }
        }
        
        return true
    }

    private suspend fun handleAppChanged(event: MonitorEvent.AppChanged) {
        if (event.timestamp - lastAppChangeTime < APP_CHANGE_DEBOUNCE_MS) {
            Log.d(TAG, "Debounced: ${event.packageName}")
            return
        }
        lastAppChangeTime = event.timestamp

        Log.d(TAG, "App changed to: ${event.packageName}")

        // Si la app es exenta, ocultar burbuja
        if (isPackageExempt(event.packageName)) {
            Log.d(TAG, "Exempt package, hiding bubble")
            hideBubble()
            stopSessionMonitoring()
            bubbleActive = false
            currentPackageName = null
            currentMonitoredApp = null
            sessionStartTime = 0L
            return
        }

        // Si es el mismo paquete que ya estamos monitoreando, no hacer nada
        if (event.packageName == currentPackageName && bubbleActive) {
            Log.d(TAG, "Same package already monitored: ${event.packageName}")
            return
        }

        // Buscar si la app está monitoreada
        val app = withContext(Dispatchers.IO) {
            repository.getAppByPackage(event.packageName)
        }

        if (app?.isMonitoring == true) {
            // App monitoreada - mostrar burbuja
            Log.d(TAG, "Starting monitoring for: ${app.appName}")
            
            // Resetear lastGoalNotified y lastGoalNotifiedTime al abrir la app
            // para que pueda notificar nuevamente en esta sesión
            val resetApp = app.copy(
                lastGoalNotified = false,
                lastGoalNotifiedTime = 0L
            )
            withContext(Dispatchers.IO) {
                repository.updateAppMonitoring(resetApp)
            }
            
            currentPackageName = event.packageName
            currentMonitoredApp = resetApp
            sessionStartTime = event.timestamp
            
            mainScope.launch {
                showBubble(resetApp, event.timestamp)
            }
            
            startSessionMonitoring(resetApp)
            bubbleActive = true
        } else {
            // App no monitoreada - ocultar burbuja
            Log.d(TAG, "App not monitored: ${event.packageName}, hiding bubble")
            hideBubble()
            stopSessionMonitoring()
            bubbleActive = false
            currentPackageName = null
            currentMonitoredApp = null
            sessionStartTime = 0L
        }
    }

    private fun startSessionMonitoring(app: MonitoredApp) {
        sessionUpdateJob?.cancel()
        goalCheckJob?.cancel()
        
        sessionUpdateJob = serviceScope.launch {
            while (isActive && currentPackageName != null) {
                val currentTime = System.currentTimeMillis()
                val duration = currentTime - sessionStartTime
                
                mainScope.launch {
                    updateBubbleTime(duration)
                }
                
                updateBubbleProgress(duration, app.timeGoalMinutes)
                
                delay(1000L)
            }
        }
        
        goalCheckJob = serviceScope.launch {
            while (isActive && currentPackageName != null) {
                val currentTime = System.currentTimeMillis()
                val duration = currentTime - sessionStartTime
                
                // Usar el estado vivo (currentMonitoredApp), no el snapshot inicial:
                // MonitoredApp es inmutable, y checkAndNotifyGoal actualiza
                // currentMonitoredApp cada vez que notifica. Si seguimos pasando
                // el `app` original, lastGoalNotifiedTime nunca avanza y el guard
                // de isExtraTimeActive queda como unico freno (spam cada 5s en
                // cuanto ese flag vuelve a false).
                val liveApp = currentMonitoredApp ?: app
                checkAndNotifyGoal(liveApp, duration)
                
                delay(GOAL_CHECK_INTERVAL)
            }
        }
    }

    /**
     * Verifica si se ha alcanzado la meta y notifica cada vez que se completa un múltiplo.
     * 
     * Comportamiento: Si la meta es 5 minutos, notifica a los 5, 10, 15, 20... minutos.
     * 
     * @param app La app monitoreada
     * @param duration Duración actual de la sesión en milisegundos
     */
    private suspend fun checkAndNotifyGoal(app: MonitoredApp, duration: Long) {
        val goalMs = app.timeGoalMinutes * 60_000L
        
        // Si el tiempo extra está activo, no notificar hasta que se cumpla o se detenga
        if (isExtraTimeActive) return
        
        // Si no ha pasado suficiente tiempo, salir
        if (duration < goalMs) return
        
        // Modo alarma: mientras la meta esté superada y no haya tiempo extra
        // activo, se notifica en CADA verificación (cada GOAL_CHECK_INTERVAL).
        // Se detiene únicamente cuando el usuario agrega tiempo extra
        // (isExtraTimeActive corta el check más arriba) o cierra la app
        // monitoreada (termina la sesión y este job se cancela).
        val completedGoals = duration / goalMs
        Log.d(TAG, "Goal reached for ${app.appName} (x${completedGoals}): alarm notification tick")
        
        // Notificar a la burbuja
        mainScope.launch {
            notifyGoalReached(app)
        }
        
        // Mostrar/actualizar notificación en barra de estado
        showGoalNotification(app, completedGoals)
        
        // Registrar la primera vez que se alcanzó la meta (una sola vez, informativo)
        if (!app.lastGoalNotified) {
            val updatedApp = app.copy(
                lastGoalNotified = true,
                lastGoalNotifiedTime = duration
            )
            withContext(Dispatchers.IO) {
                repository.updateAppMonitoring(updatedApp)
            }
            currentMonitoredApp = updatedApp
        }
    }

    private fun stopSessionMonitoring() {
        sessionUpdateJob?.cancel()
        sessionUpdateJob = null
        goalCheckJob?.cancel()
        goalCheckJob = null
        // Red de seguridad: nunca dejar el freno de alarma trabado en `true`
        // para la próxima sesión, incluso si BubbleService no llegó a avisar
        // que el tiempo extra terminó o se detuvo.
        isExtraTimeActive = false
        Log.d(TAG, "Session monitoring stopped")
    }

    private fun showBubble(app: MonitoredApp, sessionStart: Long) {
        try {
            val intent = Intent(this, BubbleService::class.java).apply {
                action = Constants.ACTION_SHOW_BUBBLE
                putExtra(Constants.EXTRA_PACKAGE_NAME, app.packageName)
                putExtra(Constants.EXTRA_TIME_GOAL_MINUTES, app.timeGoalMinutes)
                putExtra(Constants.EXTRA_SESSION_START_TIME, sessionStart)
                putExtra(Constants.EXTRA_BUBBLE_PERSISTENT, true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "Bubble shown for: ${app.appName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing bubble", e)
        }
    }

    private fun updateBubbleTime(duration: Long) {
        try {
            val intent = Intent(this, BubbleService::class.java).apply {
                action = Constants.ACTION_UPDATE_SESSION_TIME
                putExtra(Constants.EXTRA_DURATION, duration)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating bubble time", e)
        }
    }

    private fun updateBubbleProgress(duration: Long, goalMinutes: Int) {
        try {
            val intent = Intent(this, BubbleService::class.java).apply {
                action = Constants.ACTION_UPDATE_PROGRESS
                putExtra(Constants.EXTRA_DURATION, duration)
                putExtra(Constants.EXTRA_TIME_GOAL_MINUTES, goalMinutes)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating bubble progress", e)
        }
    }

    private fun notifyGoalReached(app: MonitoredApp) {
        try {
            val intent = Intent(this, BubbleService::class.java).apply {
                action = Constants.ACTION_BUBBLE_GOAL_REACHED
                putExtra(Constants.EXTRA_PACKAGE_NAME, app.packageName)
                putExtra(Constants.EXTRA_TIME_GOAL_MINUTES, app.timeGoalMinutes)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying goal reached", e)
        }
    }

    private fun hideBubble() {
        try {
            val intent = Intent(this, BubbleService::class.java).apply {
                action = Constants.ACTION_HIDE_BUBBLE
            }
            startService(intent)
            Log.d(TAG, "Bubble hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding bubble", e)
        }
    }

    private fun handleBubbleClosed(event: MonitorEvent.BubbleClosed) {
        Log.d(TAG, "Bubble closed by user, keeping monitoring active")
    }

    /**
     * Muestra una notificación cuando se alcanza la meta.
     * Incluye el número de meta completada (ej: "¡Meta 3 alcanzada!").
     */
    private fun showGoalNotification(app: MonitoredApp, goalsCompleted: Long) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    Constants.GOAL_NOTIFICATION_CHANNEL_ID,
                    getString(R.string.goal_notification_title),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificaciones cuando alcanzas una meta de tiempo"
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notificationTitle = if (goalsCompleted > 1) {
                getString(R.string.goal_reached_multiple_title, goalsCompleted)
            } else {
                getString(R.string.goal_reached_title)
            }
            
            val notificationText = getString(R.string.goal_notification_text, app.appName, app.timeGoalMinutes)

            val notification = NotificationCompat.Builder(this, Constants.GOAL_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .build()

            // Modo alarma: se reusa el mismo ID para que cada tick actualice
            // y re-alerte (vibre/suene) la misma notificación en vez de
            // apilar una nueva cada 5 segundos en la bandeja.
            notificationManager.notify(Constants.GOAL_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing goal notification", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_screen_time),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_screen_time_description)
                setShowBadge(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startScreenTimeNotification() {
        screenTimeNotificationJob = serviceScope.launch {
            while (isActive) {
                eventChannel.send(MonitorEvent.CheckScreenTime)
                delay(SCREEN_TIME_UPDATE_INTERVAL)
            }
        }
    }

    private fun updateScreenTimeNotification() {
        try {
            val totalMillis = usageRepository.getExactScreenTimeToday()
            val hours = totalMillis / (1000 * 60 * 60)
            val minutes = (totalMillis / (1000 * 60)) % 60

            if (hours == lastNotifiedHours && minutes == lastNotifiedMinutes) {
                return
            }
            lastNotifiedHours = hours
            lastNotifiedMinutes = minutes

            val fullTimeText = String.format("%dh %02dm", hours, minutes)
            val bitmapIcon = createStackedTimeBitmap(hours, minutes)

            val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(IconCompat.createWithBitmap(bitmapIcon))
                .setContentTitle(getString(R.string.notification_screen_time_title))
                .setContentText(getString(R.string.notification_screen_time_text, fullTimeText))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(Constants.NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    private fun createStackedTimeBitmap(hours: Long, minutes: Long): Bitmap {
        val scale = resources.displayMetrics.density
        val size = (24 * scale).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val xPos = size / 2f

        if (hours > 0) {
            paint.textSize = 8.5f * scale
            val topLineY = size * 0.42f
            val bottomLineY = size * 0.88f
            canvas.drawText("${hours}h", xPos, topLineY, paint)
            canvas.drawText("${minutes}m", xPos, bottomLineY, paint)
        } else {
            paint.textSize = 11.5f * scale
            val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText("${minutes}m", xPos, yPos, paint)
        }

        return bitmap
    }

    private fun isPackageExempt(packageName: String): Boolean {
        return when {
            packageName == "com.android.systemui" -> true
            packageName == "android" -> true
            packageName == this.packageName -> true
            packageName.contains("inputmethod") || packageName.contains("keyboard") -> true
            packageName.contains("samsung.android.sidegesture") -> true
            packageName.contains("samsung.android.app.smartcapture") -> true
            packageName.contains("samsung.android.honeyboard") -> true
            packageName.contains("com.android.launcher") -> true
            packageName.contains("com.google.android.apps.nexuslauncher") -> true
            packageName.contains("com.samsung.android.launcher") -> true
            packageName.contains("com.google.android.googlequicksearchbox") -> true
            else -> false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service CONNECTED")
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""
        
        Log.d(TAG, "Event: type=${event.eventType}, package=$packageName, class=$className, window=${event.windowId}")
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (packageName == currentPackageName) {
                    Log.d(TAG, "Same package, ignoring: $packageName")
                    return
                }
                
                if (isRelevantWindow(event)) {
                    Log.d(TAG, "Processing window change: $packageName")
                    eventChannel.trySend(
                        MonitorEvent.AppChanged(packageName, System.currentTimeMillis())
                    ).onFailure { Log.w(TAG, "AppChanged descartado para $packageName") }
                } else {
                    Log.d(TAG, "Ignoring irrelevant window: $packageName, $className")
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = System.currentTimeMillis()
                if (packageName != lastStillActivePackage || 
                    now - lastStillActiveTime > STILL_ACTIVE_THROTTLE_MS) {
                    lastStillActivePackage = packageName
                    lastStillActiveTime = now
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_BUBBLE_CLOSED -> {
                eventChannel.trySend(
                    MonitorEvent.BubbleClosed(System.currentTimeMillis())
                ).onFailure { Log.w(TAG, "BubbleClosed descartado") }
            }
            Constants.ACTION_EXTRA_TIME_ADDED -> {
                isExtraTimeActive = true
                Log.d(TAG, "Extra time added, suppressing goal notifications")
            }
            Constants.ACTION_EXTRA_TIME_COMPLETED -> {
                isExtraTimeActive = false
                Log.d(TAG, "Extra time completed, resuming goal notifications")
                serviceScope.launch {
                    val duration = System.currentTimeMillis() - sessionStartTime
                    val app = currentMonitoredApp
                    if (app != null && duration >= app.timeGoalMinutes * 60_000L) {
                        mainScope.launch {
                            notifyGoalReached(app)
                        }
                        showGoalNotification(app, duration / (app.timeGoalMinutes * 60_000L))
                    }
                }
            }
            Constants.ACTION_EXTRA_TIME_STOPPED -> {
                isExtraTimeActive = false
                Log.d(TAG, "Extra time stopped, resuming goal notifications")
                serviceScope.launch {
                    val duration = System.currentTimeMillis() - sessionStartTime
                    val app = currentMonitoredApp
                    if (app != null && duration >= app.timeGoalMinutes * 60_000L) {
                        mainScope.launch {
                            notifyGoalReached(app)
                        }
                        showGoalNotification(app, duration / (app.timeGoalMinutes * 60_000L))
                    }
                }
            }
            Constants.ACTION_CLOSE_MONITORED_APP -> {
                Log.d(TAG, "Closing monitored app: sending user home")
                performGlobalAction(GLOBAL_ACTION_HOME)
                isExtraTimeActive = false
                hideBubble()
                stopSessionMonitoring()
                bubbleActive = false
                currentPackageName = null
                currentMonitoredApp = null
                sessionStartTime = 0L
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroying")
        
        hideBubble()
        stopSessionMonitoring()
        screenTimeNotificationJob?.cancel()
        eventChannel.close()
        serviceScope.cancel()
        mainScope.cancel()
        
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
}