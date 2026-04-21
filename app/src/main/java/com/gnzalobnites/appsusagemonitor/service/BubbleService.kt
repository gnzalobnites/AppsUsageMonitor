package com.gnzalobnites.appsusagemonitor.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.TextView
import android.widget.ProgressBar
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.data.repository.AppRepository
import com.gnzalobnites.appsusagemonitor.utils.Constants
import kotlinx.coroutines.*
import java.util.*
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator

class BubbleService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var appRepository: AppRepository
    private var bubbleView: View? = null
    private var expandedView: View? = null
    private var isExpanded = false
    private var currentPackageName: String? = null
    private var currentBadgeCount: Int = 0
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var currentInterval: Long = Constants.INTERVAL_1_MINUTE
    private val mainHandler = Handler(Looper.getMainLooper())
    private var updateExpandedViewRunnable: Runnable? = null
    private var isBubbleActive = false
    private var isPersistent = false

    private var updateHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var updateJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var cachedTotalToday: Long = 0L
    private var lastCacheUpdate: Long = 0L
    private val CACHE_DURATION = 5000L

    // Animaciones y estados
    private var pulseAnimation: ObjectAnimator? = null
    // --- NUEVAS VARIABLES PARA OPAICDAD Y ARRASTRE ---
    private var idleRunnable: Runnable? = null
    private val IDLE_ALPHA = 0.6f
    private val ACTIVE_ALPHA = 1.0f
    private val IDLE_DELAY_MS = 3000L // 3 segundos antes de atenuarse

    private val bubbleParams: WindowManager.LayoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END

            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            y = screenHeight / 4

            x = 0
        }
    }

    private val expandedParams: WindowManager.LayoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
    }

    companion object {
        private const val TAG = "BubbleService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BubbleService created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        appRepository = AppRepository(this)
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            Constants.ACTION_SHOW_BUBBLE -> {
                val packageName = intent.getStringExtra(Constants.EXTRA_PACKAGE_NAME)
                val badgeCount = intent.getIntExtra(Constants.EXTRA_BADGE_COUNT, 1)
                val interval = intent.getLongExtra(Constants.EXTRA_INTERVAL, Constants.INTERVAL_1_MINUTE)
                val sessionStart = intent.getLongExtra(Constants.EXTRA_SESSION_START_TIME, System.currentTimeMillis())
                isPersistent = intent.getBooleanExtra(Constants.EXTRA_BUBBLE_PERSISTENT, false)

                currentInterval = interval
                sessionStartTime = sessionStart

                if (packageName != null) {
                    currentPackageName = packageName
                    refreshTotalTodayCache(packageName)
                }

                showBubble(packageName, badgeCount)
            }
            Constants.ACTION_HIDE_BUBBLE -> {
                Log.d(TAG, "Hiding bubble")
                hideAllViews()
                isBubbleActive = false
                stopUpdatingTime()
            }
        }

        return START_STICKY
    }

    private fun refreshTotalTodayCache(packageName: String) {
        serviceScope.launch {
            try {
                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val endOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

                cachedTotalToday = appRepository.getTotalUsageForDay(
                    packageName,
                    startOfDay,
                    endOfDay
                )
                lastCacheUpdate = System.currentTimeMillis()
                Log.d(TAG, "Cache refreshed: totalToday=$cachedTotalToday ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing cache", e)
            }
        }
    }

    private fun startForegroundService() {
        val channelId = "bubble_service_channel"
        val notificationId = Constants.NOTIFICATION_ID

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_description)
                    setShowBadge(false)
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_content))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(notificationId, notification)
            }
            Log.d(TAG, "Foreground service started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            try {
                startForeground(notificationId, NotificationCompat.Builder(this, channelId).build())
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback also failed", e2)
            }
        }
    }

    private fun showBubble(packageName: String?, badgeCount: Int) {
        if (packageName == null) {
            Log.e(TAG, "PackageName is null")
            return
        }

        Log.d(TAG, "Attempting to show bubble for: $packageName, count: $badgeCount, persistent: $isPersistent")

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "No overlay permission")
            return
        }

        if (isBubbleActive && bubbleView != null) {
            updateBubbleContent(packageName, badgeCount)
            return
        }

        currentPackageName = packageName
        currentBadgeCount = badgeCount

        try {
            createBubbleView(packageName, badgeCount)
            isBubbleActive = true
            
            // NUEVO: Iniciar la actualización en vivo en cuanto la burbuja nace
            startUpdatingTime()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing bubble", e)
        }
    }

    // --- NUEVA FUNCIÓN PARA EL ARRASTRE Y LA OPAICDAD ---
    private fun setupBubbleTouchListener() {
        bubbleView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private var isDragging: Boolean = false
            private val CLICK_THRESHOLD = 15f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = bubbleParams.x
                        initialY = bubbleParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false

                        resetIdleTimer()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY

                        if (Math.abs(deltaX) > CLICK_THRESHOLD || Math.abs(deltaY) > CLICK_THRESHOLD) {
                            isDragging = true
                        }

                        if (isDragging) {
                            bubbleParams.x = initialX + deltaX.toInt()
                            bubbleParams.y = initialY + deltaY.toInt()
                            windowManager.updateViewLayout(bubbleView, bubbleParams)

                            resetIdleTimer()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        resetIdleTimer()

                        if (!isDragging) {
                            view.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    // --- NUEVA FUNCIÓN DE ANIMACIÓN DE ENTRADA ---
    private fun animateBubbleIn() {
        bubbleView?.findViewById<View>(R.id.bubble_container)?.apply {
            scaleX = 0f
            scaleY = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(600)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }

    // --- NUEVA FUNCIÓN PARA CAMBIAR LA OPAICDAD ---
    private fun setBubbleIdle(isIdle: Boolean) {
        bubbleView?.findViewById<View>(R.id.bubble_container)?.animate()?.apply {
            alpha(if (isIdle) IDLE_ALPHA else ACTIVE_ALPHA)
            duration = 400
            start()
        }
    }

    // --- NUEVA FUNCIÓN PARA REINICIAR EL TEMPORIZADOR DE REPOSO ---
    private fun resetIdleTimer() {
        setBubbleIdle(false)
        idleRunnable?.let { mainHandler.removeCallbacks(it) }

        idleRunnable = Runnable {
            if (!isExpanded) {
                setBubbleIdle(true)
            }
        }
        mainHandler.postDelayed(idleRunnable!!, IDLE_DELAY_MS)
    }

    private fun createBubbleView(packageName: String, badgeCount: Int) {
        try {
            bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_view, null)

            setupBubbleContent(packageName, badgeCount)

            // --- CONFIGURAR EL LISTENER DE TOQUE PARA ARRASTRAR ---
            setupBubbleTouchListener()

            // --- EL CLICK LISTENER ORIGINAL ---
            bubbleView?.setOnClickListener {
                Log.d(TAG, "Bubble clicked")
                resetIdleTimer()
                if (isExpanded) {
                    hideExpandedView()
                } else {
                    showExpandedView()
                }
            }

            windowManager.addView(bubbleView, bubbleParams)
            Log.d(TAG, "Bubble view added to window manager at position y=${bubbleParams.y}")

            // --- LLAMAR A LAS NUEVAS FUNCIONES ---
            animateBubbleIn()
            resetIdleTimer()

        } catch (e: Exception) {
            Log.e(TAG, "Error creating bubble view", e)
        }
    }

    private fun setupBubbleContent(packageName: String, badgeCount: Int) {
        try {
            val bubbleIcon = bubbleView?.findViewById<View>(R.id.bubble_icon)
            val badgeText = bubbleView?.findViewById<TextView>(R.id.badge_text)
            val bubbleContainer = bubbleView?.findViewById<View>(R.id.bubble_container)

            if (bubbleIcon == null || badgeText == null || bubbleContainer == null) {
                Log.e(TAG, "Bubble views not found")
                return
            }

            val pm = packageManager
            val appIcon = pm.getApplicationIcon(packageName)

            val bitmap = drawableToBitmap(appIcon)
            val roundedBitmap = getRoundedBitmap(bitmap)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bubbleIcon.background = BitmapDrawable(resources, roundedBitmap)
            } else {
                bubbleIcon.setBackgroundDrawable(BitmapDrawable(resources, roundedBitmap))
            }

            // Establecer texto inicial en el badge
            badgeText.text = "0"
            badgeText.visibility = View.VISIBLE
            bubbleContainer.visibility = View.VISIBLE

            Log.d(TAG, "Bubble content setup complete")

        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, getString(R.string.error_app_icon_not_found), e)
            bubbleView?.findViewById<View>(R.id.bubble_icon)?.setBackgroundColor(Color.GRAY)
            bubbleView?.findViewById<TextView>(R.id.badge_text)?.text = "0"
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up bubble content", e)
        }
    }

    // <<<--- MÉTODO CORREGIDO: Elimina la interferencia del badgeCount --->>>
    private fun updateBubbleContent(packageName: String, badgeCount: Int) {
        try {
            // Ya no actualizamos el texto aquí usando badgeCount
            // Esto evita el parpadeo entre intervalos y minutos en vivo
            Log.d(TAG, "Bubble content update triggered. Interval count: $badgeCount")

            // Forzamos la actualización inmediata usando nuestra nueva lógica unificada
            updateExpandedViewTimes()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating bubble content", e)
        }
    }

    private fun showExpandedView() {
        try {
            if (expandedView == null) {
                createExpandedView()
            }

            expandedView?.visibility = View.VISIBLE
            bubbleView?.findViewById<View>(R.id.bubble_container)?.visibility = View.GONE
            isExpanded = true

            updateExpandedViewTimes()
            // startUpdatingTime()  <-- ELIMINADA
            startBreathingAnimation()
            resetIdleTimer() // Despertar al expandir

            Log.d(TAG, "Expanded view shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing expanded view", e)
        }
    }

    private fun hideExpandedView() {
        try {
            expandedView?.visibility = View.GONE
            bubbleView?.findViewById<View>(R.id.bubble_container)?.visibility = View.VISIBLE
            isExpanded = false

            // stopUpdatingTime()  <-- ELIMINADA
            stopBreathingAnimation()
            resetIdleTimer() // Reiniciar el temporizador de reposo al colapsar

            Log.d(TAG, "Expanded view hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding expanded view", e)
        }
    }

    private fun createExpandedView() {
        try {
            val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_AppsUsageMonitor)
            expandedView = LayoutInflater.from(contextThemeWrapper).inflate(R.layout.bubble_expanded_view, null)

            expandedParams.gravity = Gravity.CENTER
            expandedParams.x = 0
            expandedParams.y = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                expandedView?.setBackgroundResource(R.drawable.bg_elegant_bubble)
            }

            expandedView?.findViewById<Button>(R.id.close_button)?.setOnClickListener {
                closeCartel()
            }

            windowManager.addView(expandedView, expandedParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating expanded view", e)
        }
    }

    private fun startUpdatingTime() {
        stopUpdatingTime()
        updateJob = serviceScope.launch {
            while (isActive) {
                updateExpandedViewTimes()
                delay(1000)
            }
        }
        Log.d(TAG, "Time updates started")
    }

    private fun stopUpdatingTime() {
        updateJob?.cancel()
        updateJob = null
        Log.d(TAG, "Time updates stopped")
    }

    // <<<--- MÉTODO MODIFICADO: Actualización unificada --->>>
    private fun updateExpandedViewTimes() {
        if (currentPackageName == null) return

        val now = System.currentTimeMillis()
        val sessionDuration = now - sessionStartTime

        // 1. NUEVO: Actualizar siempre el contador de la burbuja minimizada (en minutos)
        val minutesPassed = (sessionDuration / 60000).toInt()
        bubbleView?.findViewById<TextView>(R.id.badge_text)?.text = minutesPassed.toString()

        // 2. Si la burbuja NO está expandida, terminamos la ejecución aquí
        if (!isExpanded || expandedView == null) return

        // 3. Si la burbuja ESTÁ expandida, actualizamos los datos del cartel
        if (now - lastCacheUpdate > CACHE_DURATION) {
            refreshTotalTodayCache(currentPackageName!!)
        }

        val totalToday = cachedTotalToday + sessionDuration

        try {
            expandedView?.apply {
                findViewById<TextView>(R.id.current_session_time)?.text = formatDuration(sessionDuration)
                findViewById<TextView>(R.id.total_today_time)?.text = formatDuration(totalToday)

                val progressPercent = if (currentInterval > 0) {
                    ((sessionDuration % currentInterval).toFloat() / currentInterval.toFloat() * 100).toInt()
                } else {
                    0
                }

                val progressBar = findViewById<ProgressBar>(R.id.session_progress_bar)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    progressBar?.setProgress(progressPercent, true)
                } else {
                    progressBar?.progress = progressPercent
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating expanded view times", e)
        }
    }

    private fun startBreathingAnimation() {
        expandedView?.let { view ->
            pulseAnimation = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.02f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.02f)
            ).apply {
                duration = 2500
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun stopBreathingAnimation() {
        pulseAnimation?.cancel()
        pulseAnimation = null
        expandedView?.scaleX = 1.0f
        expandedView?.scaleY = 1.0f
    }

    private fun closeCartel() {
        stopUpdatingTime()
        hideAllViews()
        isExpanded = false
        isBubbleActive = false

        notifyBubbleClosed()
    }

    private fun updateExpandedView(packageName: String) {
        updateExpandedViewTimes()
    }

    private fun notifyBubbleClosed() {
        try {
            val intent = Intent(this, MonitoringService::class.java).apply {
                action = Constants.ACTION_BUBBLE_CLOSED
            }
            startService(intent)
            Log.d(TAG, "Bubble closed notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying bubble closed", e)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getRoundedBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        canvas.drawCircle(
            (bitmap.width / 2).toFloat(),
            (bitmap.height / 2).toFloat(),
            (bitmap.width / 2).toFloat(),
            paint
        )

        return output
    }

    private fun formatDuration(duration: Long): String {
        val seconds = (duration / 1000) % 60
        val minutes = (duration / (1000 * 60)) % 60
        val hours = (duration / (1000 * 60 * 60))

        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%02d:%02d", minutes, seconds)
            else -> String.format("0:%02d", seconds)
        }
    }

    private fun hideAllViews() {
        try {
            Log.d(TAG, "Hiding all views")

            stopUpdatingTime()
            updateExpandedViewRunnable?.let { mainHandler.removeCallbacks(it) }
            updateExpandedViewRunnable = null
            // --- LIMPIAR EL RUNNABLE DE REPOSO ---
            idleRunnable?.let { mainHandler.removeCallbacks(it) }
            idleRunnable = null

            if (bubbleView != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        if (bubbleView!!.isAttachedToWindow) {
                            windowManager.removeView(bubbleView)
                        }
                    } else {
                        windowManager.removeView(bubbleView)
                    }
                } catch (e: IllegalArgumentException) {
                    Log.d(TAG, "Bubble view already removed")
                }
                bubbleView = null
            }

            if (expandedView != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        if (expandedView!!.isAttachedToWindow) {
                            windowManager.removeView(expandedView)
                        }
                    } else {
                        windowManager.removeView(expandedView)
                    }
                } catch (e: IllegalArgumentException) {
                    Log.d(TAG, "Expanded view already removed")
                }
                expandedView = null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error hiding all views", e)
        } finally {
            isBubbleActive = false
            isExpanded = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BubbleService destroyed")
        stopUpdatingTime()
        stopBreathingAnimation()
        hideAllViews()
        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        updateHandler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}