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
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.data.repository.AppRepository
import com.gnzalobnites.appsusagemonitor.data.repository.UsageRepository
import com.gnzalobnites.appsusagemonitor.utils.Constants
import com.gnzalobnites.appsusagemonitor.utils.TimeFormatter
import kotlinx.coroutines.*
import java.util.*
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.os.VibrationEffect
import android.os.Vibrator

class BubbleService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var appRepository: AppRepository
    private lateinit var usageRepository: UsageRepository
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
    private var isDestroying = false
    private var isForegroundStarted = false
    private var isPreviewMode = false

    private var updateHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var updateJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Cache de vistas para mejorar rendimiento
    private var currentSessionTimeView: TextView? = null
    private var progressBarView: ProgressBar? = null
    private var badgeTextView: TextView? = null
    private var totalTodayTimeView: TextView? = null
    private var bubbleContainerView: LinearLayout? = null
    private var closeButton: Button? = null
    private var bubbleIconView: View? = null

    // Animaciones y estados
    private var pulseAnimation: ObjectAnimator? = null
    private var idleRunnable: Runnable? = null
    private val IDLE_ALPHA = 0.6f
    private val ACTIVE_ALPHA = 1.0f
    private val IDLE_DELAY_MS = 3000L

    // CORREGIDO: Siempre usar WRAP_CONTENT para que el padding y sombras no se recorten
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
        private const val PREVIEW_PACKAGE = "com.gnzalobnites.appsusagemonitor.preview"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BubbleService created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // ✅ CORREGIDO: Usar application en lugar de this para evitar fugas
        appRepository = AppRepository(application)
        usageRepository = UsageRepository(application)
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
                
                isPreviewMode = packageName == PREVIEW_PACKAGE || packageName == this.packageName

                currentInterval = interval
                sessionStartTime = sessionStart

                if (packageName != null) {
                    currentPackageName = packageName
                }

                showBubble(packageName, badgeCount)
            }
            Constants.ACTION_HIDE_BUBBLE -> {
                Log.d(TAG, "Hiding bubble")
                hideAllViews()
                isBubbleActive = false
                isPreviewMode = false
                stopUpdatingTime()
            }
            Constants.ACTION_UPDATE_PREVIEW -> {
                val size = intent.getIntExtra(Constants.EXTRA_PREVIEW_SIZE, -1)
                val opacity = intent.getIntExtra(Constants.EXTRA_PREVIEW_OPACITY, -1)
                updateBubbleAppearanceLive(size, opacity)
            }
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        if (isForegroundStarted) return
        isForegroundStarted = true

        val channelId = Constants.NOTIFICATION_CHANNEL_ID
        val notificationId = Constants.NOTIFICATION_ID

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    getString(R.string.notification_channel_screen_time),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_screen_time_description)
                    setShowBadge(false)
                    setSound(null, null)
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.notification_screen_time_title))
                .setContentText(getString(R.string.notification_screen_time_initializing))
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setSilent(true)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(notificationId, notification)
            }
            Log.d(TAG, "Foreground service started with base notification")

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
            startUpdatingTime()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing bubble", e)
        }
    }

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

    private fun animateBubbleIn() {
        bubbleContainerView?.apply {
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

    private fun setBubbleIdle(isIdle: Boolean) {
        bubbleContainerView?.animate()?.apply {
            alpha(if (isIdle) IDLE_ALPHA else ACTIVE_ALPHA)
            duration = 400
            start()
        }
    }

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
            setupBubbleTouchListener()
            cacheBubbleViews()

            // Aplicar preferencias guardadas
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val savedSize = prefs.getInt("bubble_size", 60)
            val savedOpacity = prefs.getInt("bubble_opacity", 80)
            updateBubbleAppearanceLive(savedSize, savedOpacity)

            bubbleView?.setOnClickListener {
                Log.d(TAG, "Bubble clicked")
                resetIdleTimer()
                vibrate(30)
                if (isExpanded) {
                    hideExpandedView()
                } else {
                    showExpandedView()
                }
            }

            windowManager.addView(bubbleView, bubbleParams)
            Log.d(TAG, "Bubble view added to window manager at position y=${bubbleParams.y}")

            animateBubbleIn()
            resetIdleTimer()

        } catch (e: Exception) {
            Log.e(TAG, "Error creating bubble view", e)
        }
    }

    private fun cacheBubbleViews() {
        bubbleView?.let {
            // CORREGIDO: Obtener views correctamente
            bubbleContainerView = it.findViewById(R.id.bubble_container)
            badgeTextView = it.findViewById(R.id.badge_text)
            bubbleIconView = it.findViewById(R.id.bubble_icon)
        }
    }

    private fun cacheExpandedViews() {
        expandedView?.let {
            currentSessionTimeView = it.findViewById(R.id.current_session_time)
            progressBarView = it.findViewById(R.id.session_progress_bar)
            totalTodayTimeView = it.findViewById(R.id.total_today_time)
            closeButton = it.findViewById(R.id.close_button)
        }
    }

    private fun setupBubbleContent(packageName: String, badgeCount: Int) {
        try {
            val bubbleIcon = bubbleView?.findViewById<View>(R.id.bubble_icon)
            val badgeText = bubbleView?.findViewById<TextView>(R.id.badge_text)
            val bubbleContainer = bubbleView?.findViewById<LinearLayout>(R.id.bubble_container)

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

    private fun updateBubbleContent(packageName: String, badgeCount: Int) {
        try {
            Log.d(TAG, "Bubble content update triggered. Interval count: $badgeCount")
            updateExpandedViewTimes()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating bubble content", e)
        }
    }

    private fun showExpandedView() {
        try {
            if (expandedView == null) {
                createExpandedView()
                cacheExpandedViews()
            }

            expandedView?.visibility = View.VISIBLE
            bubbleContainerView?.visibility = View.GONE
            isExpanded = true

            updateExpandedViewTimes()
            startBreathingAnimation()
            resetIdleTimer()
            vibrate(50)

            Log.d(TAG, "Expanded view shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing expanded view", e)
        }
    }

    private fun hideExpandedView() {
        try {
            expandedView?.visibility = View.GONE
            bubbleContainerView?.visibility = View.VISIBLE
            isExpanded = false
            stopBreathingAnimation()
            resetIdleTimer()
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

    private fun updateExpandedViewTimes() {
        if (currentPackageName == null) return

        val now = System.currentTimeMillis()
        val sessionDuration = now - sessionStartTime

        if (isPreviewMode) {
            badgeTextView?.text = "👁"
        } else {
            badgeTextView?.text = TimeFormatter.formatDuration(sessionDuration)
        }

        if (!isExpanded || expandedView == null) return

        try {
            currentSessionTimeView?.text = if (isPreviewMode) "Preview" else TimeFormatter.formatDuration(sessionDuration)

            val totalScreenTimeToday = usageRepository.getExactScreenTimeToday()
            totalTodayTimeView?.text = if (isPreviewMode) "Configuración" else TimeFormatter.formatDuration(totalScreenTimeToday)
            totalTodayTimeView?.visibility = View.VISIBLE

            val progressPercent = if (currentInterval > 0) {
                ((sessionDuration % currentInterval).toFloat() / currentInterval.toFloat() * 100).toInt()
            } else {
                0
            }

            progressBarView?.let { progressBar ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    progressBar.setProgress(progressPercent, true)
                } else {
                    progressBar.progress = progressPercent
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating expanded view times", e)
        }
    }

    /**
     * CORREGIDO: Actualiza la apariencia de la burbuja en tiempo real
     * - Ya no modifica bubbleParams (siempre WRAP_CONTENT)
     * - Aplica opacidad a la vista raíz completa
     * - Usa 60% del tamaño para el icono
     * - Forza requestLayout e invalidate
     */
    private fun updateBubbleAppearanceLive(size: Int, opacity: Int) {
        if (bubbleView == null || bubbleContainerView == null) return

        // CORRECCIÓN: Aplicar opacidad a la vista raíz completa
        if (opacity != -1) {
            val alphaValue = opacity / 100f
            bubbleView?.alpha = alphaValue
            Log.d(TAG, "Preview opacity updated: $opacity% -> alpha: $alphaValue")
        }

        // CORRECCIÓN: Tamaño - solo modificar el contenedor, no bubbleParams
        if (size != -1) {
            val scale = resources.displayMetrics.density
            val baseSizeDp = 40
            val maxSizeDp = 100
            val additionalDp = ((size / 100f) * (maxSizeDp - baseSizeDp))
            val finalSizeDp = baseSizeDp + additionalDp
            val finalSizePx = (finalSizeDp * scale).toInt()

            // CORRECCIÓN: Solo modificar el contenedor, no la ventana
            val params = bubbleContainerView?.layoutParams
            params?.width = finalSizePx
            params?.height = finalSizePx
            bubbleContainerView?.layoutParams = params

            // Icono al 60% del tamaño del contenedor (más proporcionado)
            val iconParams = bubbleIconView?.layoutParams
            val iconSize = (finalSizePx * 0.60f).toInt()
            iconParams?.width = iconSize
            iconParams?.height = iconSize
            bubbleIconView?.layoutParams = iconParams

            // Forzar redibujado completo
            bubbleContainerView?.requestLayout()
            bubbleContainerView?.invalidate()
            bubbleIconView?.requestLayout()
            bubbleIconView?.invalidate()
            bubbleView?.requestLayout()
            bubbleView?.invalidate()

            // Actualizar la ventana (solo posición, no tamaño)
            try {
                windowManager.updateViewLayout(bubbleView, bubbleParams)
                Log.d(TAG, "Preview size updated: ${size}% -> ${finalSizeDp}dp (${finalSizePx}px)")
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando el layout en tiempo real", e)
            }
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
        // Detener actualizaciones primero
        stopUpdatingTime()
        stopBreathingAnimation()
        hideAllViews()
        isExpanded = false
        isBubbleActive = false
        isPreviewMode = false
        notifyBubbleClosed()
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

        val width = drawable.intrinsicWidth.coerceAtLeast(100)
        val height = drawable.intrinsicHeight.coerceAtLeast(100)
        val size = Math.max(width, height)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getRoundedBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val dx = (size - bitmap.width) / 2f
        val dy = (size - bitmap.height) / 2f
        if (dx != 0f || dy != 0f) {
            val matrix = android.graphics.Matrix()
            matrix.setTranslate(dx, dy)
            shader.setLocalMatrix(matrix)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }

        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        return output
    }

    private fun vibrate(duration: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating", e)
        }
    }

    private fun removeAllViews() {
        listOf(bubbleView to bubbleParams, expandedView to expandedParams).forEach { (view, params) ->
            view?.let {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && it.isAttachedToWindow) {
                        windowManager.removeView(it)
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        windowManager.removeView(it)
                    }
                } catch (e: IllegalArgumentException) {
                    Log.d(TAG, "View already removed")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing view", e)
                }
            }
        }
        bubbleView = null
        expandedView = null
        isBubbleActive = false
        isExpanded = false
        isPreviewMode = false
        
        currentSessionTimeView = null
        progressBarView = null
        badgeTextView = null
        totalTodayTimeView = null
        bubbleContainerView = null
        closeButton = null
        bubbleIconView = null
    }

    private fun hideAllViews() {
        try {
            Log.d(TAG, "Hiding all views")
            stopUpdatingTime()
            updateExpandedViewRunnable?.let { mainHandler.removeCallbacks(it) }
            updateExpandedViewRunnable = null
            idleRunnable?.let { mainHandler.removeCallbacks(it) }
            idleRunnable = null
            removeAllViews()
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding all views", e)
        } finally {
            isBubbleActive = false
            isExpanded = false
            isPreviewMode = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroying = true
        Log.d(TAG, "BubbleService destroyed")
        stopUpdatingTime()
        stopBreathingAnimation()
        removeAllViews()
        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        updateHandler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundStarted = false
        isPreviewMode = false
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}