package com.gnzalobnites.appsusagemonitor.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.Button
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.gnzalobnites.appsusagemonitor.MyApplication
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
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var currentGoalMinutes: Int = 5
    private var sessionDuration: Long = 0L
    private var goalReached = false
    private var isPreviewMode = false
    private var isExtraTimeActive = false
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isBubbleActive = false
    private var isForegroundStarted = false

    // Se guarda en un campo (no una lambda suelta): SharedPreferences guarda
    // los listeners como WeakReference, así que sin una referencia fuerte
    // externa el listener puede desaparecer solo.
    private var appearancePrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    
    private var updateJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Cache de vistas
    private var currentSessionTimeView: TextView? = null
    private var progressBarView: ProgressBar? = null
    private var goalTextView: TextView? = null
    private var totalTodayTimeView: TextView? = null
    private var bubbleContainerView: LinearLayout? = null
    private var closeButton: Button? = null
    private var collapseButton: Button? = null
    private var closeAppButton: Button? = null
    private var goalIconView: ImageView? = null
    private var bubbleProgressBar: ProgressBar? = null
    private var bubbleIconTimer: TextView? = null
    private var badgeTextView: TextView? = null

    // Nuevas vistas para tiempo extra
    private var extrasStatusView: TextView? = null
    private var extrasTotalTimeView: TextView? = null
    private var extrasBlocksView: TextView? = null
    private var btnAddExtras: Button? = null

    // ============================================================
    // ESTADO DEL TIEMPO EXTRA
    // ============================================================
    // 1. ACUMULADO (solo para mostrar estadísticas)
    private var totalExtrasBlocks = 0
    private var totalExtrasMinutes = 0
    
    // 2. BLOQUE ACTIVO (para el contador descendente)
    private var currentExtraMinutes = 0
    private var currentExtraStartTime: Long = 0L
    
    // 3. Control del job de tracking
    private var extrasJob: Job? = null
    private var extraTimeRemainingSeconds: Long = 0L
    
    private val EXTRAS_BLOCK_MINUTES = 5

    // Animaciones
    private var pulseAnimation: ObjectAnimator? = null
    private var goalPulseAnimation: ObjectAnimator? = null
    private var idleRunnable: Runnable? = null
    private val IDLE_ALPHA = 0.6f
    private val ACTIVE_ALPHA = 1.0f
    private val IDLE_DELAY_MS = 3000L

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
            y = displayMetrics.heightPixels / 4
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
        const val ACTION_RESUME_MONITORING = "com.gnzalobnites.appsusagemonitor.RESUME_MONITORING"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BubbleService created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        appRepository = MyApplication.appRepository
        usageRepository = MyApplication.usageRepository
        startForegroundService()
        registerAppearancePrefsListener()
    }

    private fun registerAppearancePrefsListener() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        appearancePrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == "bubble_size" || key == "bubble_opacity") {
                val size = sharedPrefs.getInt("bubble_size", 60)
                val opacity = sharedPrefs.getInt("bubble_opacity", 80)
                updateBubbleAppearance(size, opacity)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(appearancePrefsListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            Constants.ACTION_SHOW_BUBBLE -> {
                val packageName = intent.getStringExtra(Constants.EXTRA_PACKAGE_NAME)
                currentGoalMinutes = intent.getIntExtra(Constants.EXTRA_TIME_GOAL_MINUTES, 5)
                sessionStartTime = intent.getLongExtra(Constants.EXTRA_SESSION_START_TIME, System.currentTimeMillis())
                currentPackageName = packageName
                goalReached = false
                isPreviewMode = intent.getBooleanExtra("is_preview", false)
                isExtraTimeActive = false
                
                resetExtrasState()
                
                if (bubbleView == null) {
                    createBubbleView(packageName)
                    startTimeUpdater()
                } else {
                    updateBubbleContent()
                }
                isBubbleActive = true
            }
            Constants.ACTION_UPDATE_SESSION_TIME -> {
                sessionDuration = intent.getLongExtra(Constants.EXTRA_DURATION, 0L)
                updateTimeDisplay()
            }
            Constants.ACTION_UPDATE_PROGRESS -> {
                val duration = intent.getLongExtra(Constants.EXTRA_DURATION, 0L)
                val goal = intent.getIntExtra(Constants.EXTRA_TIME_GOAL_MINUTES, currentGoalMinutes)
                updateProgressBar(duration, goal)
            }
            Constants.ACTION_BUBBLE_GOAL_REACHED -> {
                val packageName = intent.getStringExtra(Constants.EXTRA_PACKAGE_NAME)
                val goal = intent.getIntExtra(Constants.EXTRA_TIME_GOAL_MINUTES, currentGoalMinutes)
                if (packageName == currentPackageName) {
                    goalReached = true
                    animateGoalReached()
                }
            }
            Constants.ACTION_HIDE_BUBBLE -> {
                if (isPreviewMode) {
                    mainHandler.postDelayed({
                        if (!isExpanded) {
                            hideAllViews()
                            isBubbleActive = false
                            stopTimeUpdater()
                        }
                    }, 2000)
                } else {
                    Log.d(TAG, "Hiding bubble")
                    hideAllViews()
                    isBubbleActive = false
                    stopTimeUpdater()
                }
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
            Log.d(TAG, "Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }

    private fun createBubbleView(packageName: String?) {
        if (packageName == null) return
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "No overlay permission")
            return
        }

        try {
            bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_view, null)
            setupBubbleTouchListener()
            cacheBubbleViews()

            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val savedSize = prefs.getInt("bubble_size", 60)
            val savedOpacity = prefs.getInt("bubble_opacity", 80)
            updateBubbleAppearance(savedSize, savedOpacity)

            bubbleView?.setOnClickListener {
                if (isExpanded) {
                    collapseBubble()
                } else {
                    expandBubble()
                }
            }

            windowManager.addView(bubbleView, bubbleParams)
            Log.d(TAG, "Bubble view added")

            animateBubbleIn()
            resetIdleTimer()

        } catch (e: Exception) {
            Log.e(TAG, "Error creating bubble view", e)
        }
    }

    private fun updateBubbleContent() {
        badgeTextView?.text = TimeFormatter.formatDurationCollapsed(sessionDuration)
    }

    private fun updateBubbleAppearance(size: Int, opacity: Int) {
        if (bubbleView == null || bubbleContainerView == null) return

        val alphaValue = opacity / 100f
        bubbleView?.alpha = alphaValue

        val density = resources.displayMetrics.density
        val sizeFraction = (size / 100f).coerceIn(0f, 1f)

        // IMPORTANTE: el contenedor NO se fuerza a un ancho/alto exacto.
        // bubble_view.xml le pone padding fijo + un icono + el texto del
        // tiempo en una fila horizontal; si el ancho forzado por el slider
        // queda por debajo de lo que ese contenido necesita, badge_text se
        // parte en dos lineas para entrar. Por eso el "tamano" del slider
        // ahora escala el CONTENIDO (texto e iconos) y el padding interno,
        // y el contenedor se deja en wrap_content: siempre se ajusta a lo
        // que realmente hace falta mostrar, sea cual sea el texto ("0:45",
        // "12:34" o "1:23:45" en sesiones largas).
        val params = bubbleContainerView?.layoutParams
        params?.width = ViewGroup.LayoutParams.WRAP_CONTENT
        params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        bubbleContainerView?.layoutParams = params

        val badgeTextSizeSp = 16f + sizeFraction * 10f
        val iconTextSizeSp = 12f + sizeFraction * 6f
        badgeTextView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, badgeTextSizeSp)
        badgeTextView?.maxLines = 1
        bubbleIconTimer?.setTextSize(TypedValue.COMPLEX_UNIT_SP, iconTextSizeSp)
        bubbleIconTimer?.maxLines = 1

        val paddingHorizontalPx = ((10 + sizeFraction * 10) * density).toInt()
        val paddingVerticalPx = ((6 + sizeFraction * 8) * density).toInt()
        bubbleContainerView?.setPadding(
            paddingHorizontalPx, paddingVerticalPx, paddingHorizontalPx, paddingVerticalPx
        )

        // minWidth (no width fijo): evita que la burbuja se vea demasiado
        // chica en size=0, pero nunca le impide crecer si el texto lo pide.
        val minWidthPx = ((60 + sizeFraction * 40) * density).toInt()
        bubbleContainerView?.minimumWidth = minWidthPx

        bubbleContainerView?.requestLayout()
        bubbleContainerView?.invalidate()
    }

    private fun cacheBubbleViews() {
        bubbleView?.let {
            bubbleContainerView = it.findViewById(R.id.bubble_container)
            badgeTextView = it.findViewById(R.id.badge_text)
            bubbleProgressBar = it.findViewById(R.id.bubble_progress)
            bubbleIconTimer = it.findViewById(R.id.bubble_icon_timer)
        }
    }

    private fun cacheExpandedViews() {
        expandedView?.let {
            currentSessionTimeView = it.findViewById(R.id.current_session_time)
            progressBarView = it.findViewById(R.id.session_progress_bar)
            goalTextView = it.findViewById(R.id.goal_text)
            totalTodayTimeView = it.findViewById(R.id.total_today_time)
            closeButton = it.findViewById(R.id.close_button)
            collapseButton = it.findViewById(R.id.btn_collapse)
            closeAppButton = it.findViewById(R.id.btn_close_app)
            goalIconView = it.findViewById(R.id.goal_icon)
            
            extrasStatusView = it.findViewById(R.id.extras_status)
            extrasTotalTimeView = it.findViewById(R.id.extras_total_time)
            extrasBlocksView = it.findViewById(R.id.extras_blocks)
            btnAddExtras = it.findViewById(R.id.btn_add_extras)
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

    private fun expandBubble() {
        try {
            if (expandedView == null) {
                createExpandedView()
                cacheExpandedViews()
            }

            expandedView?.visibility = View.VISIBLE
            bubbleContainerView?.visibility = View.GONE
            isExpanded = true

            if (goalReached) {
                if (isExtraTimeActive) {
                    showExtrasUIViews()
                    updateExtrasTotalTime()
                    updateExtrasBlocks()
                } else {
                    showGoalReachedUI()
                }
            } else {
                hideExtrasUI()
            }

            updateExpandedView()
            startBreathingAnimation()
            resetIdleTimer()
            vibrate(50)

            Log.d(TAG, "Bubble expanded")
        } catch (e: Exception) {
            Log.e(TAG, "Error expanding bubble", e)
        }
    }

    private fun collapseBubble() {
        try {
            expandedView?.visibility = View.GONE
            bubbleContainerView?.visibility = View.VISIBLE
            isExpanded = false
            stopBreathingAnimation()
            resetIdleTimer()
            
            if (!isPreviewMode) {
                notifyBubbleClosed()
            }
            
            Log.d(TAG, "Bubble collapsed")
        } catch (e: Exception) {
            Log.e(TAG, "Error collapsing bubble", e)
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

            expandedView?.findViewById<Button>(R.id.btn_collapse)?.setOnClickListener {
                collapseBubble()
            }

            expandedView?.findViewById<Button>(R.id.close_button)?.setOnClickListener {
                expandedView?.visibility = View.GONE
                bubbleContainerView?.visibility = View.VISIBLE
                isExpanded = false
                stopBreathingAnimation()
                resetIdleTimer()
                
                Log.d(TAG, "Bubble collapsed via close button")
            }

            windowManager.addView(expandedView, expandedParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating expanded view", e)
        }
    }

    private fun startTimeUpdater() {
        stopTimeUpdater()
        updateJob = serviceScope.launch {
            while (isActive && isBubbleActive) {
                updateExpandedView()
                delay(1000L)
            }
        }
    }

    private fun stopTimeUpdater() {
        updateJob?.cancel()
        updateJob = null
    }

    private fun updateTimeDisplay() {
        badgeTextView?.text = TimeFormatter.formatDurationCollapsed(sessionDuration)
        
        updateCollapsedProgress()
        
        if (isExpanded) {
            currentSessionTimeView?.text = TimeFormatter.formatDuration(sessionDuration)
            updateProgressBar(sessionDuration, currentGoalMinutes)
        }
    }

    private fun updateCollapsedProgress() {
        val goalMs = currentGoalMinutes * 60_000L
        val progress = if (goalMs > 0) {
            ((sessionDuration.toFloat() / goalMs) * 100).toInt().coerceIn(0, 100)
        } else 0
        
        bubbleProgressBar?.let { bar ->
            bar.progress = progress
            bar.visibility = if (progress > 0) View.VISIBLE else View.GONE
        }
    }

    private fun updateExpandedView() {
        if (!isExpanded || expandedView == null) return

        try {
            if (goalReached) {
                updateExtrasTotalTime()
                currentSessionTimeView?.text = TimeFormatter.formatDuration(sessionDuration)
            } else {
                currentSessionTimeView?.text = TimeFormatter.formatDuration(sessionDuration)
                val totalScreenTime = usageRepository.getExactScreenTimeToday()
                totalTodayTimeView?.text = TimeFormatter.formatDuration(totalScreenTime)
                updateProgressBar(sessionDuration, currentGoalMinutes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating expanded view", e)
        }
    }

    private fun updateProgressBar(duration: Long, goalMinutes: Int) {
        val goalMs = goalMinutes * 60_000L
        val progress = ((duration.toFloat() / goalMs) * 100).toInt().coerceAtMost(100)

        progressBarView?.let { progressBar ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                progressBar.setProgress(progress, true)
            } else {
                progressBar.progress = progress
            }
        }

        goalTextView?.text = getString(R.string.goal_progress_format, progress, goalMinutes)
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

    private fun animateGoalReached() {
        bubbleContainerView?.let { view ->
            view.setBackgroundColor(Color.parseColor("#FFD700"))
            
            // Cambiar color del icono y texto a negro para contraste
            bubbleIconTimer?.setTextColor(Color.BLACK)
            badgeTextView?.setTextColor(Color.BLACK)
            
            badgeTextView?.let { text ->
                text.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(300)
                    .withEndAction {
                        text.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(300)
                            .start()
                    }
                    .start()
            }
            
            goalPulseAnimation = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.05f, 1.0f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.05f, 1.0f)
            ).apply {
                duration = 600
                repeatCount = 2
                interpolator = OvershootInterpolator()
                start()
            }
        }

        mainHandler.postDelayed({
            // Restaurar fondo y colores
            bubbleContainerView?.setBackgroundResource(R.drawable.bg_bubble_digital)
            bubbleIconTimer?.setTextColor(Color.WHITE)
            badgeTextView?.setTextColor(Color.WHITE)
            
            if (isExpanded) {
                showGoalReachedUI()
            }
        }, 2000)

        vibrate(100)
    }

    private fun showGoalReachedUI() {
        if (isExpanded) {
            goalIconView?.visibility = View.VISIBLE
            progressBarView?.progressTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor("#FFD700")
            )
            showExtrasUI()
        }
    }

    private fun showExtrasUI() {
        if (isExpanded) {
            goalIconView?.visibility = View.VISIBLE
            progressBarView?.progressTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor("#FFD700")
            )
            val shouldStartTracking = !isExtraTimeActive || currentExtraMinutes == 0
            isExtraTimeActive = true
            showExtrasUIViews()
            if (shouldStartTracking) {
                if (currentExtraMinutes > 0) {
                    startExtrasTracking()
                }
            } else {
                updateExtrasTotalTime()
                updateExtrasBlocks()
            }
        }
    }

    private fun showExtrasUIViews() {
        extrasStatusView?.visibility = View.VISIBLE
        extrasStatusView?.text = getString(R.string.goal_reached_title)
        
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(currentPackageName ?: "", PackageManager.GET_META_DATA)
            ).toString()
        } catch (e: Exception) {
            "App"
        }
        val subtitle = getString(R.string.goal_reached_subtitle, appName, currentGoalMinutes)
        extrasStatusView?.append("\n$subtitle")
        
        extrasTotalTimeView?.visibility = View.VISIBLE
        updateExtrasTotalTime()
        
        extrasBlocksView?.visibility = View.VISIBLE
        updateExtrasBlocks()
        
        btnAddExtras?.visibility = View.VISIBLE
        btnAddExtras?.setOnClickListener {
            addExtraBlock()
        }
        
        closeAppButton?.visibility = View.VISIBLE
        closeAppButton?.setOnClickListener {
            closeMonitoredApp()
        }
        
        collapseButton?.visibility = View.VISIBLE
        closeButton?.visibility = View.GONE
    }

    private fun hideExtrasUI() {
        isExtraTimeActive = false
        extrasStatusView?.visibility = View.GONE
        extrasTotalTimeView?.visibility = View.GONE
        extrasBlocksView?.visibility = View.GONE
        btnAddExtras?.visibility = View.GONE
        closeAppButton?.visibility = View.GONE
        collapseButton?.visibility = View.VISIBLE
        closeButton?.visibility = View.GONE
    }

    // ============================================================
    // LÓGICA DE TIEMPO EXTRA CON BLOQUES INDEPENDIENTES
    // ============================================================

    private fun startExtrasTracking() {
        if (currentExtraMinutes <= 0) {
            Log.d(TAG, "No active extra block to track")
            return
        }
        
        if (extrasJob?.isActive == true) {
            return
        }
        
        extrasJob?.cancel()
        extrasJob = serviceScope.launch {
            while (isActive && isExtraTimeActive && currentExtraMinutes > 0) {
                updateExtrasTotalTime()
                
                val currentExtraMs = currentExtraMinutes * 60_000L
                val elapsedExtraMs = System.currentTimeMillis() - currentExtraStartTime
                
                if (elapsedExtraMs >= currentExtraMs) {
                    onExtraTimeCompleted()
                    break
                }
                
                delay(1000L)
            }
        }
    }

    private fun onExtraTimeCompleted() {
        isExtraTimeActive = false
        
        currentExtraMinutes = 0
        currentExtraStartTime = 0L
        extraTimeRemainingSeconds = 0L
        
        updateExtrasTotalTime()
        
        notifyExtraTimeCompleted()
        
        currentPackageName?.let { pkg ->
            val intent = Intent(this, MonitoringService::class.java).apply {
                action = Constants.ACTION_EXTRA_TIME_COMPLETED
                putExtra(Constants.EXTRA_PACKAGE_NAME, pkg)
            }
            startService(intent)
        }
        
        currentPackageName?.let { pkg ->
            val intent = Intent(this, BubbleService::class.java).apply {
                action = Constants.ACTION_BUBBLE_GOAL_REACHED
                putExtra(Constants.EXTRA_PACKAGE_NAME, pkg)
                putExtra(Constants.EXTRA_TIME_GOAL_MINUTES, currentGoalMinutes)
            }
            startService(intent)
        }
        
        mainHandler.postDelayed({
            if (isExpanded) {
                collapseBubble()
            }
        }, 1500)
    }

    private fun addExtraBlock() {
        totalExtrasBlocks++
        totalExtrasMinutes += EXTRAS_BLOCK_MINUTES
        
        currentExtraMinutes = EXTRAS_BLOCK_MINUTES
        currentExtraStartTime = System.currentTimeMillis()
        
        if (!isExtraTimeActive) {
            isExtraTimeActive = true
        }
        
        notifyExtraTimeAdded()
        
        updateExtrasTotalTime()
        updateExtrasBlocks()
        
        startExtrasTracking()
        
        btnAddExtras?.let { button ->
            button.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(150)
                .start()
            
            mainHandler.postDelayed({
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            }, 150)
        }
        
        vibrate(30)
        Log.d(TAG, "Added extra block: block=${EXTRAS_BLOCK_MINUTES}min, total=${totalExtrasMinutes}min, blocks=${totalExtrasBlocks}")
    }

    private fun updateExtrasTotalTime() {
        val formattedTime = TimeFormatter.formatDuration(sessionDuration)
        extrasTotalTimeView?.text = getString(R.string.goal_extras_total_time, formattedTime)
        
        when {
            currentExtraMinutes > 0 && isExtraTimeActive -> {
                val currentExtraMs = currentExtraMinutes * 60_000L
                val elapsedExtraMs = System.currentTimeMillis() - currentExtraStartTime
                val remainingMs = (currentExtraMs - elapsedExtraMs).coerceAtLeast(0)
                extraTimeRemainingSeconds = remainingMs / 1000
                
                if (remainingMs > 0) {
                    extrasStatusView?.text = "${getString(R.string.goal_reached_title)}\n${getString(R.string.goal_extras_remaining, TimeFormatter.formatDuration(remainingMs))}"
                } else {
                    extrasStatusView?.text = "${getString(R.string.goal_reached_title)}\n${getString(R.string.goal_extras_completed)}"
                }
                
                val extraProgress = (100 - (remainingMs * 100 / currentExtraMs).toInt()).coerceIn(0, 100)
                progressBarView?.let { bar ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        bar.setProgress(extraProgress, true)
                    } else {
                        bar.progress = extraProgress
                    }
                }
            }
            
            !isExtraTimeActive && totalExtrasMinutes > 0 -> {
                extrasStatusView?.text = "${getString(R.string.goal_reached_title)}\n${getString(R.string.goal_extras_completed)}"
                progressBarView?.let { bar ->
                    bar.progress = 100
                }
            }
            
            else -> {
                // No mostrar nada
            }
        }
    }

    private fun updateExtrasBlocks() {
        if (totalExtrasBlocks > 0) {
            val blocksText = getString(R.string.goal_extras_blocks_added, totalExtrasBlocks, totalExtrasMinutes)
            extrasBlocksView?.text = blocksText
            extrasBlocksView?.visibility = View.VISIBLE
        } else {
            extrasBlocksView?.text = getString(R.string.goal_extras_blocks, 0)
            extrasBlocksView?.visibility = View.VISIBLE
        }
    }

    private fun resetExtrasState() {
        if (isExtraTimeActive) {
            notifyExtraTimeStopped()
        }
        totalExtrasBlocks = 0
        totalExtrasMinutes = 0
        currentExtraMinutes = 0
        currentExtraStartTime = 0L
        isExtraTimeActive = false
        extraTimeRemainingSeconds = 0L
        extrasJob?.cancel()
        extrasJob = null
        Log.d(TAG, "Extras state reset")
    }

    // ============================================================
    // NOTIFICACIONES AL MONITORING SERVICE
    // ============================================================

    private fun notifyExtraTimeAdded() {
        try {
            val intent = Intent(this, MonitoringService::class.java).apply {
                action = Constants.ACTION_EXTRA_TIME_ADDED
                putExtra(Constants.EXTRA_EXTRA_BLOCKS, totalExtrasBlocks)
                putExtra(Constants.EXTRA_EXTRA_MINUTES, totalExtrasMinutes)
            }
            startService(intent)
            Log.d(TAG, "Notified: Extra time added - ${totalExtrasMinutes}min total, block ${currentExtraMinutes}min")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying extra time added", e)
        }
    }

    private fun notifyExtraTimeCompleted() {
        try {
            val intent = Intent(this, MonitoringService::class.java).apply {
                action = Constants.ACTION_EXTRA_TIME_COMPLETED
                putExtra(Constants.EXTRA_EXTRA_BLOCKS, totalExtrasBlocks)
                putExtra(Constants.EXTRA_EXTRA_MINUTES, totalExtrasMinutes)
            }
            startService(intent)
            Log.d(TAG, "Notified: Extra time completed - ${totalExtrasMinutes}min total")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying extra time completed", e)
        }
    }

    private fun notifyExtraTimeStopped() {
        try {
            val intent = Intent(this, MonitoringService::class.java).apply {
                action = Constants.ACTION_EXTRA_TIME_STOPPED
                putExtra(Constants.EXTRA_EXTRA_BLOCKS, totalExtrasBlocks)
                putExtra(Constants.EXTRA_EXTRA_MINUTES, totalExtrasMinutes)
            }
            startService(intent)
            Log.d(TAG, "Notified: Extra time stopped - ${totalExtrasMinutes}min total")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying extra time stopped", e)
        }
    }

    private fun closeMonitoredApp() {
        try {
            val intent = Intent(this, MonitoringService::class.java).apply {
                action = Constants.ACTION_CLOSE_MONITORED_APP
            }
            startService(intent)
            Log.d(TAG, "Requested closing monitored app")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting app close", e)
        }
    }

    private fun notifyBubbleClosed() {
        if (!isPreviewMode) {
            try {
                val intent = Intent(this, MonitoringService::class.java).apply {
                    action = Constants.ACTION_BUBBLE_CLOSED
                }
                startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying bubble closed", e)
            }
        }
    }

    private fun vibrate(duration: Long) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
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
        isExpanded = false
        
        currentSessionTimeView = null
        progressBarView = null
        goalTextView = null
        totalTodayTimeView = null
        bubbleContainerView = null
        closeButton = null
        collapseButton = null
        closeAppButton = null
        goalIconView = null
        bubbleProgressBar = null
        bubbleIconTimer = null
        badgeTextView = null
        
        extrasStatusView = null
        extrasTotalTimeView = null
        extrasBlocksView = null
        btnAddExtras = null
    }

    private fun hideAllViews() {
        try {
            Log.d(TAG, "Hiding all views")
            stopTimeUpdater()
            goalPulseAnimation?.cancel()
            extrasJob?.cancel()
            isExtraTimeActive = false
            idleRunnable?.let { mainHandler.removeCallbacks(it) }
            idleRunnable = null
            removeAllViews()
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
        stopTimeUpdater()
        goalPulseAnimation?.cancel()
        extrasJob?.cancel()
        isExtraTimeActive = false
        appearancePrefsListener?.let {
            PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(it)
        }
        appearancePrefsListener = null
        removeAllViews()
        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundStarted = false
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}