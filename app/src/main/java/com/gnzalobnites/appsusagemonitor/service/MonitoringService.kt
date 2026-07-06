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
import android.os.SystemClock
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
// import kotlinx.coroutines.channels.trySend

class MonitoringService : AccessibilityService() {

    private lateinit var repository: AppRepository
    private lateinit var usageRepository: UsageRepository
    
    // ✅ Scope con IO para trabajo pesado y Main para UI
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // ✅ Canales
    private val inputChannel = Channel<MonitorEvent>(INPUT_CHANNEL_CAPACITY)
    private val eventChannel = Channel<MonitorEvent>(EVENT_CHANNEL_CAPACITY)
    
    private var screenTimeNotificationJob: Job? = null
    private var loadAppJob: Job? = null
    private var bubbleJob: Job? = null
    private var departureJob: Job? = null

    // ✅ Estado inmutable
    private data class MonitorState(
        val currentAppPackage: String? = null,
        val currentMonitoredApp: MonitoredApp? = null,
        val sessionStartTime: Long = 0L,
        val isBubbleVisible: Boolean = false,
        val bubbleCloseTime: Long = 0L,
        val bubbleGeneration: Long = 0L,
        val departureGeneration: Long = 0L,
        val lastBubbleShowTime: Long = 0L,
        val loadGeneration: Long = 0L
    )

    private var state = MonitorState()

    // ✅ Filtro para TYPE_WINDOW_CONTENT_CHANGED
    private var lastStillActivePackage: String? = null
    private var lastStillActiveTime: Long = 0L

    // ✅ Eventos
    private sealed interface MonitorEvent {
        data class AppChanged(
            val packageName: String,
            val timestamp: Long
        ) : MonitorEvent
        
        data class AppStillActive(
            val packageName: String,
            val timestamp: Long
        ) : MonitorEvent
        
        data class BubbleClosed(
            val timestamp: Long
        ) : MonitorEvent
        
        data class BubbleTimeout(
            val generation: Long,
            val timestamp: Long
        ) : MonitorEvent
        
        data class DepartureTimeout(
            val generation: Long,
            val timestamp: Long
        ) : MonitorEvent
        
        data object CheckScreenTime : MonitorEvent
        
        data class AppLoaded(
            val generation: Long,
            val packageName: String,
            val monitoredApp: MonitoredApp?,
            val appChangedTimestamp: Long
        ) : MonitorEvent
    }

    companion object {
        private const val TAG = "MonitoringService"
        private const val DEBUG_TAG = "AppMonitor_Debug"
        private const val DEPARTURE_DELAY_MS = 500L
        private const val EVENT_CHANNEL_CAPACITY = 64
        private const val INPUT_CHANNEL_CAPACITY = 128
        private const val SCREEN_TIME_UPDATE_INTERVAL = 60000L
        private const val STILL_ACTIVE_THROTTLE_MS = 250L
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        repository = AppRepository(application)
        usageRepository = UsageRepository(application)
        createNotificationChannel()
        startEventLoop()
        startInputForwarder()
        startBubbleService()
        startScreenTimeNotification()
    }

    private fun startInputForwarder() {
        serviceScope.launch {
            for (event in inputChannel) {
                eventChannel.send(event)
            }
        }
    }

    private fun startEventLoop() {
        serviceScope.launch {
            for (event in eventChannel) {
                try {
                    Log.d(TAG, "Processing event: $event")
                    val (newState, effects) = reduce(state, event)
                    state = newState
                    executeEffects(effects)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing event: $event", e)
                }
            }
            Log.d(TAG, "Event loop finished")
        }
    }

    // ✅ Reductor puro
    private fun reduce(
        state: MonitorState,
        event: MonitorEvent
    ): Pair<MonitorState, List<Effect>> {
        return when (event) {
            is MonitorEvent.AppChanged -> handleAppChanged(state, event)
            is MonitorEvent.AppLoaded -> handleAppLoaded(state, event)
            is MonitorEvent.AppStillActive -> handleAppStillActive(state, event)
            is MonitorEvent.BubbleTimeout -> handleBubbleTimeout(state, event)
            is MonitorEvent.DepartureTimeout -> handleDepartureTimeout(state, event)
            is MonitorEvent.BubbleClosed -> handleBubbleClosed(state, event)
            MonitorEvent.CheckScreenTime -> handleScreenTimeCheck(state)
        }
    }

    // ✅ Handlers
    private fun handleAppChanged(
        state: MonitorState,
        event: MonitorEvent.AppChanged
    ): Pair<MonitorState, List<Effect>> {
        Log.d(DEBUG_TAG, ">>> App changed to: ${event.packageName} at ${event.timestamp}")

        if (event.packageName == state.currentAppPackage) {
            val newGeneration = state.departureGeneration + 1
            return state.copy(
                departureGeneration = newGeneration
            ) to emptyList()
        }

        val newLoadGen = state.loadGeneration + 1
        val effect = Effect.LoadMonitoredApp(
            packageName = event.packageName,
            generation = newLoadGen,
            appChangedTimestamp = event.timestamp
        )
        
        return state.copy(
            loadGeneration = newLoadGen
        ) to listOf(effect)
    }

    private fun handleAppLoaded(
        state: MonitorState,
        event: MonitorEvent.AppLoaded
    ): Pair<MonitorState, List<Effect>> {
        if (event.generation != state.loadGeneration) {
            Log.d(TAG, "AppLoaded obsoleto (gen: ${event.generation}, actual: ${state.loadGeneration})")
            return state to emptyList()
        }

        return if (event.monitoredApp?.isMonitoring == true) {
            val newBubbleGen = state.bubbleGeneration + 1
            val newDepartureGen = state.departureGeneration + 1
            
            val effects = listOf(
                Effect.ScheduleBubble(
                    interval = event.monitoredApp.selectedInterval,
                    generation = newBubbleGen
                ),
                Effect.HideBubble
            )
            
            state.copy(
                currentAppPackage = event.packageName,
                currentMonitoredApp = event.monitoredApp,
                sessionStartTime = event.appChangedTimestamp,
                isBubbleVisible = false,
                bubbleCloseTime = 0L,
                bubbleGeneration = newBubbleGen,
                departureGeneration = newDepartureGen,
                lastBubbleShowTime = 0L
            ) to effects
        } else {
            if (isPackageExempt(event.packageName)) {
                Log.d(DEBUG_TAG, "Paquete exento: ${event.packageName}")
                state to emptyList()
            } else {
                val newGeneration = state.departureGeneration + 1
                val effects = listOf(
                    Effect.ScheduleDeparture(
                        delay = DEPARTURE_DELAY_MS,
                        generation = newGeneration
                    )
                )
                state.copy(
                    departureGeneration = newGeneration
                ) to effects
            }
        }
    }

    private fun handleAppStillActive(
        state: MonitorState,
        event: MonitorEvent.AppStillActive
    ): Pair<MonitorState, List<Effect>> {
        if (event.packageName == state.currentAppPackage) {
            val newGeneration = state.departureGeneration + 1
            Log.d(TAG, "App still active: ${event.packageName}, new departure gen: $newGeneration")
            return state.copy(
                departureGeneration = newGeneration
            ) to emptyList()
        }
        return state to emptyList()
    }

    private fun handleBubbleTimeout(
        state: MonitorState,
        event: MonitorEvent.BubbleTimeout
    ): Pair<MonitorState, List<Effect>> {
        if (event.generation != state.bubbleGeneration) {
            Log.d(TAG, "Bubble timeout obsoleto (gen: ${event.generation}, actual: ${state.bubbleGeneration})")
            return state to emptyList()
        }

        if (state.currentAppPackage == null || state.isBubbleVisible) {
            return state to emptyList()
        }

        val intervalsPassed = if (state.sessionStartTime > 0 && state.currentMonitoredApp != null) {
            ((event.timestamp - state.sessionStartTime) / state.currentMonitoredApp!!.selectedInterval).toInt()
        } else {
            1
        }

        val effects = listOf(
            Effect.ShowBubble(
                packageName = state.currentAppPackage!!,
                badgeCount = intervalsPassed,
                interval = state.currentMonitoredApp?.selectedInterval ?: 0L,
                sessionStartTime = state.sessionStartTime
            )
        )

        return state.copy(
            isBubbleVisible = true,
            lastBubbleShowTime = event.timestamp
        ) to effects
    }

    private fun handleDepartureTimeout(
        state: MonitorState,
        event: MonitorEvent.DepartureTimeout
    ): Pair<MonitorState, List<Effect>> {
        if (event.generation != state.departureGeneration) {
            Log.d(TAG, "Departure timeout obsoleto (gen: ${event.generation}, actual: ${state.departureGeneration})")
            return state to emptyList()
        }

        if (state.currentAppPackage != null) {
            Log.w(DEBUG_TAG, "Cerrando sesión tras $DEPARTURE_DELAY_MS ms")
            val effects = listOf(Effect.HideBubble)
            return state.copy(
                currentAppPackage = null,
                currentMonitoredApp = null,
                sessionStartTime = 0L,
                isBubbleVisible = false,
                bubbleCloseTime = 0L,
                lastBubbleShowTime = 0L
            ) to effects
        }

        return state to emptyList()
    }

    private fun handleBubbleClosed(
        state: MonitorState,
        event: MonitorEvent.BubbleClosed
    ): Pair<MonitorState, List<Effect>> {
        Log.d(TAG, "Burbuja cerrada por usuario")
        
        return if (state.currentMonitoredApp != null) {
            val newGeneration = state.bubbleGeneration + 1
            val effects = listOf(
                Effect.ScheduleBubble(
                    interval = state.currentMonitoredApp.selectedInterval,
                    generation = newGeneration
                )
            )
            state.copy(
                isBubbleVisible = false,
                bubbleCloseTime = event.timestamp,
                bubbleGeneration = newGeneration
            ) to effects
        } else {
            state.copy(isBubbleVisible = false) to emptyList()
        }
    }

    private fun handleScreenTimeCheck(
        state: MonitorState
    ): Pair<MonitorState, List<Effect>> {
        return state to listOf(Effect.UpdateScreenTimeNotification)
    }

    // ✅ Efectos
    private sealed interface Effect {
        data class ShowBubble(
            val packageName: String,
            val badgeCount: Int,
            val interval: Long,
            val sessionStartTime: Long
        ) : Effect
        
        data object HideBubble : Effect
        
        data class ScheduleBubble(
            val interval: Long,
            val generation: Long
        ) : Effect
        
        data class ScheduleDeparture(
            val delay: Long,
            val generation: Long
        ) : Effect
        
        data object UpdateScreenTimeNotification : Effect
        
        data class LoadMonitoredApp(
            val packageName: String,
            val generation: Long,
            val appChangedTimestamp: Long
        ) : Effect
    }

    // ✅ Ejecutor de efectos - con dispatcher Main para UI
    private fun executeEffects(effects: List<Effect>) {
        // ✅ Los efectos que tocan el framework de Android se ejecutan en Main
        mainScope.launch {
            for (effect in effects) {
                when (effect) {
                    is Effect.ShowBubble -> {
                        showBubble(
                            packageName = effect.packageName,
                            badgeCount = effect.badgeCount,
                            interval = effect.interval,
                            sessionStartTime = effect.sessionStartTime
                        )
                    }
                    Effect.HideBubble -> hideBubble()
                    is Effect.ScheduleBubble -> {
                        // Schedule usa delay, se ejecuta en IO
                        serviceScope.launch {
                            scheduleBubble(effect.interval, effect.generation)
                        }
                    }
                    is Effect.ScheduleDeparture -> {
                        serviceScope.launch {
                            scheduleDeparture(effect.delay, effect.generation)
                        }
                    }
                    Effect.UpdateScreenTimeNotification -> updateScreenTimeNotification()
                    is Effect.LoadMonitoredApp -> {
                        serviceScope.launch {
                            loadMonitoredApp(
                                packageName = effect.packageName,
                                generation = effect.generation,
                                appChangedTimestamp = effect.appChangedTimestamp
                            )
                        }
                    }
                }
            }
        }
    }

    // ✅ Carga asíncrona
    private suspend fun loadMonitoredApp(
        packageName: String,
        generation: Long,
        appChangedTimestamp: Long
    ) {
        loadAppJob?.cancel()
        loadAppJob = serviceScope.launch {
            try {
                Log.d(TAG, "Loading app: $packageName (gen: $generation)")
                val monitoredApp = repository.getAppByPackage(packageName)
                
                eventChannel.send(
                    MonitorEvent.AppLoaded(
                        generation = generation,
                        packageName = packageName,
                        monitoredApp = monitoredApp,
                        appChangedTimestamp = appChangedTimestamp
                    )
                )
            } catch (e: CancellationException) {
                Log.d(TAG, "Load cancelled for $packageName (gen: $generation)")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading monitored app: $packageName", e)
                eventChannel.send(
                    MonitorEvent.AppLoaded(
                        generation = generation,
                        packageName = packageName,
                        monitoredApp = null,
                        appChangedTimestamp = appChangedTimestamp
                    )
                )
            }
        }
    }

    // ✅ Programadores
    private suspend fun scheduleBubble(interval: Long, generation: Long) {
        bubbleJob?.cancel()
        bubbleJob = serviceScope.launch {
            Log.d(TAG, "Programando burbuja en ${interval}ms (gen: $generation)")
            delay(interval)
            val timestamp = System.currentTimeMillis()
            eventChannel.send(MonitorEvent.BubbleTimeout(generation, timestamp))
        }
    }

    private suspend fun scheduleDeparture(delay: Long, generation: Long) {
        departureJob?.cancel()
        departureJob = serviceScope.launch {
            delay(delay)
            val timestamp = System.currentTimeMillis()
            eventChannel.send(MonitorEvent.DepartureTimeout(generation, timestamp))
        }
    }

    // ✅ Funciones de UI - todas en Main implícitamente
    private fun showBubble(
        packageName: String,
        badgeCount: Int,
        interval: Long,
        sessionStartTime: Long
    ) {
        try {
            Log.d(TAG, "Mostrando burbuja para: $packageName, contador: $badgeCount")
            
            val intent = Intent(this, BubbleService::class.java).apply {
                action = Constants.ACTION_SHOW_BUBBLE
                putExtra(Constants.EXTRA_PACKAGE_NAME, packageName)
                putExtra(Constants.EXTRA_BADGE_COUNT, badgeCount)
                putExtra(Constants.EXTRA_INTERVAL, interval)
                putExtra(Constants.EXTRA_SESSION_START_TIME, sessionStartTime)
                putExtra(Constants.EXTRA_BUBBLE_PERSISTENT, true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando burbuja", e)
        }
    }

    private fun hideBubble() {
        try {
            val intent = Intent(this, BubbleService::class.java).apply {
                action = Constants.ACTION_HIDE_BUBBLE
            }
            startService(intent)
            Log.d(TAG, "Burbuja ocultada")
        } catch (e: Exception) {
            Log.e(TAG, "Error ocultando burbuja", e)
        }
    }

    private fun startBubbleService() {
        try {
            val intent = Intent(this, BubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando BubbleService", e)
        }
    }

    // ✅ Notificaciones
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
        Log.d(TAG, "Loop de notificación iniciado")
    }

    private fun updateScreenTimeNotification() {
        try {
            val totalMillis = usageRepository.getExactScreenTimeToday()
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
            notificationManager.notify(Constants.NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando notificación", e)
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
            else -> false
        }
    }

    // ✅ Overrides
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

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                inputChannel.trySend(
                    MonitorEvent.AppChanged(packageName, System.currentTimeMillis())
                ).onFailure { Log.w(TAG, "AppChanged descartado para $packageName") }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = SystemClock.uptimeMillis()
                if (packageName != lastStillActivePackage || 
                    now - lastStillActiveTime > STILL_ACTIVE_THROTTLE_MS) {
                    lastStillActivePackage = packageName
                    lastStillActiveTime = now
                    inputChannel.trySend(
                        MonitorEvent.AppStillActive(packageName, System.currentTimeMillis())
                    ).onFailure { Log.w(TAG, "AppStillActive descartado para $packageName") }
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
                inputChannel.trySend(
                    MonitorEvent.BubbleClosed(System.currentTimeMillis())
                ).onFailure { Log.w(TAG, "BubbleClosed descartado") }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroying")
        
        loadAppJob?.cancel()
        bubbleJob?.cancel()
        departureJob?.cancel()
        
        hideBubble()
        
        inputChannel.close()
        eventChannel.close()
        screenTimeNotificationJob?.cancel()
        serviceScope.cancel()
        mainScope.cancel()
        
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
}