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
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import com.gnzalobnites.appsusagemonitor.data.repository.AppRepository
import com.gnzalobnites.appsusagemonitor.data.repository.UsageRepository
import com.gnzalobnites.appsusagemonitor.utils.Constants
import kotlinx.coroutines.*

class MonitoringService : AccessibilityService() {
    
    private lateinit var repository: AppRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentAppPackage: String? = null
    private var currentMonitoredApp: MonitoredApp? = null
    private var sessionStartTime: Long = 0L
    private var lastBubbleShowTime: Long = 0L
    private var isBubbleVisible = false
    private var nextBubbleJob: Job? = null
    private var bubbleCloseTime: Long = 0L
    private var isFirstBubbleScheduled = false
    private var departureJob: Job? = null

    private var screenTimeNotificationJob: Job? = null
    // ELIMINADA: private val SCREEN_TIME_NOTIFICATION_ID = 2026

    companion object {
        private const val TAG = "MonitoringService"
        private const val DEBUG_TAG = "AppMonitor_Debug"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        repository = AppRepository(this)
        createNotificationChannel()
    }

    /**
     * MODIFICADO: Usa el canal unificado desde Constants
     */
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
        
        startBubbleService()
        startRealTimeScreenNotification()
    }

    private fun startRealTimeScreenNotification() {
        screenTimeNotificationJob = serviceScope.launch {
            val usageRepo = UsageRepository(applicationContext as android.app.Application)
            
            while (isActive) {
                updateScreenTimeNotification(usageRepo)
                delay(60000)
            }
        }
        Log.d(TAG, "Screen time notification loop started")
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

    /**
     * MODIFICADO: Usa el ID unificado de Constants.NOTIFICATION_ID
     */
    private fun updateScreenTimeNotification(usageRepo: UsageRepository) {
        try {
            val totalMillis = usageRepo.getExactScreenTimeToday()
            val hours = totalMillis / (1000 * 60 * 60)
            val minutes = (totalMillis / (1000 * 60)) % 60

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
            // Usar el MISMO ID que BubbleService
            notificationManager.notify(Constants.NOTIFICATION_ID, notification)

            Log.d(TAG, "Notification updated: $fullTimeText (exact: $totalMillis ms)")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating screen time notification", e)
        }
    }

    private fun stopScreenTimeNotification() {
        screenTimeNotificationJob?.cancel()
        screenTimeNotificationJob = null

        // ELIMINADO: notificationManager.cancel(SCREEN_TIME_NOTIFICATION_ID)
        // La notificación es administrada por el estado Foreground del BubbleService.
        
        Log.d(TAG, "Screen time notification loop stopped")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(DEBUG_TAG, ">>> Ventana activa: $packageName")
                departureJob?.cancel()
                handleAppChange(packageName)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (packageName == currentAppPackage) {
                    departureJob?.cancel()
                }
            }
        }
    }

    private fun handleAppChange(packageName: String) {
        serviceScope.launch {
            val monitoredApp = repository.getAppByPackage(packageName)

            if (monitoredApp?.isMonitoring == true) {
                departureJob?.cancel()
                if (currentAppPackage == packageName) return@launch

                if (currentAppPackage != null) {
                    // La sesión anterior termina automáticamente al cambiar de app
                }
                startNewSession(packageName, monitoredApp)
            } else {
                if (isPackageExempt(packageName)) {
                    Log.d(DEBUG_TAG, "Paquete exento detectado: $packageName. Manteniendo sesión.")
                    departureJob?.cancel()
                    return@launch
                }

                departureJob?.cancel()
                departureJob = serviceScope.launch {
                    delay(500)
                    if (currentAppPackage != null) {
                        Log.w(DEBUG_TAG, "Cerrando sesión tras 500ms. App destino: $packageName")
                        cleanupCurrentState()
                    }
                }
            }
        }
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
            else -> false
        }
    }

    private suspend fun startNewSession(packageName: String, monitoredApp: MonitoredApp) {
        sessionStartTime = System.currentTimeMillis()
        currentMonitoredApp = monitoredApp
        currentAppPackage = packageName
        isBubbleVisible = false
        bubbleCloseTime = 0L
        isFirstBubbleScheduled = false
        lastBubbleShowTime = 0L

        Log.d(TAG, "Session started for $packageName at $sessionStartTime")

        scheduleNextBubble(monitoredApp.selectedInterval)
    }

    private fun scheduleNextBubble(interval: Long) {
        nextBubbleJob?.cancel()
        nextBubbleJob = serviceScope.launch {
            Log.d(TAG, "Scheduling next bubble in $interval ms")
            delay(interval)

            if (currentAppPackage != null && !isBubbleVisible) {
                showBubble()
            } else {
                Log.d(TAG, "Conditions changed, not showing bubble. currentApp: $currentAppPackage, isVisible: $isBubbleVisible")
            }
        }
    }

    private fun showBubble() {
        if (isBubbleVisible) {
            Log.d(TAG, "Bubble already visible, not showing another")
            return
        }

        try {
            Log.d(TAG, "Showing bubble for: ${currentMonitoredApp?.packageName}")

            val now = System.currentTimeMillis()
            val intervalsPassed = if (sessionStartTime > 0 && currentMonitoredApp != null) {
                ((now - sessionStartTime) / currentMonitoredApp!!.selectedInterval).toInt()
            } else {
                1
            }

            val intent = Intent(this, BubbleService::class.java).apply {
                action = Constants.ACTION_SHOW_BUBBLE
                putExtra(Constants.EXTRA_PACKAGE_NAME, currentAppPackage)
                putExtra(Constants.EXTRA_BADGE_COUNT, intervalsPassed)
                putExtra(Constants.EXTRA_INTERVAL, currentMonitoredApp?.selectedInterval ?: 0L)
                putExtra(Constants.EXTRA_SESSION_START_TIME, sessionStartTime)
                putExtra(Constants.EXTRA_BUBBLE_PERSISTENT, true)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            lastBubbleShowTime = now
            isBubbleVisible = true
            bubbleCloseTime = 0L

            Log.d(TAG, "Bubble shown at $lastBubbleShowTime")

        } catch (e: Exception) {
            Log.e(TAG, "Error showing bubble", e)
        }
    }

    private fun hideBubble() {
        try {
            Log.d(TAG, "Hiding bubble")
            val intent = Intent(this, BubbleService::class.java).apply {
                action = Constants.ACTION_HIDE_BUBBLE
            }
            startService(intent)
            isBubbleVisible = false
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding bubble", e)
        }
    }

    private fun cleanupCurrentState() {
        nextBubbleJob?.cancel()
        nextBubbleJob = null

        if (isBubbleVisible) {
            hideBubble()
        }

        currentAppPackage = null
        currentMonitoredApp = null
        sessionStartTime = 0L
        isBubbleVisible = false
        bubbleCloseTime = 0L
        isFirstBubbleScheduled = false
        lastBubbleShowTime = 0L
    }

    fun onBubbleClosed() {
        serviceScope.launch {
            Log.d(TAG, "Bubble closed by user")
            bubbleCloseTime = System.currentTimeMillis()
            isBubbleVisible = false

            currentMonitoredApp?.let { app ->
                scheduleNextBubble(app.selectedInterval)
            }
        }
    }

    private fun startBubbleService() {
        try {
            Log.d(TAG, "Starting BubbleService")
            val intent = Intent(this, BubbleService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BubbleService", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_BUBBLE_CLOSED -> {
                onBubbleClosed()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        nextBubbleJob?.cancel()
        departureJob?.cancel()
        screenTimeNotificationJob?.cancel()
        serviceScope.cancel()
        hideBubble()

        // ELIMINADO: notificationManager.cancel(...) en el destroy
        // Cuando BubbleService ejecute stopForeground(true), la notificación se destruirá automáticamente.
    }
}