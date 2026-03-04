package com.gnzalobnites.appsusagemonitor.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import com.gnzalobnites.appsusagemonitor.data.repository.AppRepository
import com.gnzalobnites.appsusagemonitor.utils.Constants
import kotlinx.coroutines.*

class MonitoringService : AccessibilityService() {
    
    private lateinit var repository: AppRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentAppPackage: String? = null
    private var currentSessionId: Long? = null
    private var sessionStartTime: Long? = null
    private var currentMonitoredApp: MonitoredApp? = null
    private var lastBubbleShowTime: Long = 0L
    private var isBubbleVisible = false
    private var nextBubbleJob: Job? = null
    private var bubbleCloseTime: Long = 0L
    private var isFirstBubbleScheduled = false
    private var departureJob: Job? = null

    companion object {
        private const val TAG = "MonitoringService"
        private const val DEBUG_TAG = "AppMonitor_Debug"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        repository = AppRepository(this)
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
                
                if (currentAppPackage != null) endCurrentSession()
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
                        endCurrentSession()
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
        currentSessionId = repository.startSession(packageName, sessionStartTime!!)
        currentMonitoredApp = monitoredApp
        currentAppPackage = packageName
        isBubbleVisible = false
        bubbleCloseTime = 0L
        isFirstBubbleScheduled = false
        lastBubbleShowTime = 0L
        
        Log.d(TAG, "Session started for $packageName at ${sessionStartTime}")
        
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
            val intervalsPassed = if (sessionStartTime != null && currentMonitoredApp != null) {
                ((now - sessionStartTime!!) / currentMonitoredApp!!.selectedInterval).toInt()
            } else {
                1
            }
            
            val intent = Intent(this, BubbleService::class.java).apply {
                action = Constants.ACTION_SHOW_BUBBLE
                putExtra(Constants.EXTRA_PACKAGE_NAME, currentAppPackage)
                putExtra(Constants.EXTRA_BADGE_COUNT, intervalsPassed)
                putExtra(Constants.EXTRA_INTERVAL, currentMonitoredApp?.selectedInterval ?: 0L)
                putExtra(Constants.EXTRA_SESSION_START_TIME, sessionStartTime ?: System.currentTimeMillis())
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
        currentSessionId = null
        sessionStartTime = null
        isBubbleVisible = false
        bubbleCloseTime = 0L
        isFirstBubbleScheduled = false
        lastBubbleShowTime = 0L
    }

    private suspend fun endCurrentSession() {
        currentSessionId?.let { sessionId ->
            Log.d(TAG, "Ending session: $sessionId")
            repository.endSession(sessionId)
            currentSessionId = null
            sessionStartTime = null
        }
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
        serviceScope.cancel()
        hideBubble()
    }
}