package com.gnzalobnites.appsusagemonitor.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
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
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.data.repository.AppRepository
import com.gnzalobnites.appsusagemonitor.utils.Constants
import kotlinx.coroutines.*
import java.util.*

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
    
    // Cache para tiempos de hoy
    private var cachedTotalToday: Long = 0L
    private var lastCacheUpdate: Long = 0L
    private val CACHE_DURATION = 5000L // Actualizar cada 5 segundos
    
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
            // MODIFICADO: Cambiar gravedad a TOP para controlar posición vertical desde arriba
            gravity = Gravity.TOP or Gravity.END
            
            // MODIFICADO: Calcular posición vertical a 1/4 de la pantalla desde arriba
            // (punto medio entre el borde superior y el centro)
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
                
                // Inicializar caché
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
    
    // Refrescar el caché del total de hoy desde la BD local
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
        } catch (e: Exception) {
            Log.e(TAG, "Error showing bubble", e)
        }
    }

    private fun createBubbleView(packageName: String, badgeCount: Int) {
        try {
            bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_view, null)
            
            setupBubbleContent(packageName, badgeCount)
            
            bubbleView?.setOnClickListener {
                Log.d(TAG, "Bubble clicked")
                if (isExpanded) {
                    hideExpandedView()
                } else {
                    showExpandedView()
                }
            }

            windowManager.addView(bubbleView, bubbleParams)
            Log.d(TAG, "Bubble view added to window manager at position y=${bubbleParams.y}")
            
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
            
            badgeText.text = badgeCount.toString()
            badgeText.visibility = View.VISIBLE
            bubbleContainer.visibility = View.VISIBLE
            
            Log.d(TAG, "Bubble content setup complete")
            
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, getString(R.string.error_app_icon_not_found), e)
            bubbleView?.findViewById<View>(R.id.bubble_icon)?.setBackgroundColor(Color.GRAY)
            bubbleView?.findViewById<TextView>(R.id.badge_text)?.text = badgeCount.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up bubble content", e)
        }
    }

    private fun updateBubbleContent(packageName: String, badgeCount: Int) {
        try {
            val badgeText = bubbleView?.findViewById<TextView>(R.id.badge_text)
            badgeText?.text = badgeCount.toString()
            Log.d(TAG, "Bubble content updated to count: $badgeCount")
            
            if (isExpanded && expandedView != null) {
                updateExpandedViewTimes()
            }
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
            
            // Actualizar inmediatamente al expandir
            updateExpandedViewTimes()
            
            // Iniciar actualizaciones en tiempo real
            startUpdatingTime()
            
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
            
            stopUpdatingTime()
            
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
                expandedView?.setBackgroundResource(R.drawable.glass_morphism_background)
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
                delay(1000) // Actualizar cada segundo
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
        if (!isExpanded || expandedView == null || currentPackageName == null) return

        val now = System.currentTimeMillis()
        val sessionDuration = now - sessionStartTime
        
        // Refrescar caché periódicamente
        if (now - lastCacheUpdate > CACHE_DURATION) {
            refreshTotalTodayCache(currentPackageName!!)
        }
        
        // Total es el caché + la sesión actual (para incluir lo que llevamos ahora)
        val totalToday = cachedTotalToday + sessionDuration

        try {
            expandedView?.apply {
                findViewById<TextView>(R.id.current_session_time)?.text = formatDuration(sessionDuration)
                findViewById<TextView>(R.id.total_today_time)?.text = formatDuration(totalToday)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating expanded view times", e)
        }
    }

    private fun closeCartel() {
        stopUpdatingTime()
        hideAllViews()
        isExpanded = false
        isBubbleActive = false
        
        notifyBubbleClosed()
    }

    private fun updateExpandedView(packageName: String) {
        // Este método se mantiene por compatibilidad
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