package com.gnzalobnites.appsusagemonitor.banner

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.gnzalobnites.appsusagemonitor.R

class BannerTestUtils(private val context: Context) {
    
    private var windowManager: WindowManager? = null
    private var testBannerView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    
    fun initialize(windowManager: WindowManager) {
        this.windowManager = windowManager
    }
    
    fun showTestBanner(
        testMessage: String,
        onBannerClosed: () -> Unit
    ) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Sin permiso de overlay", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("BannerTest", "🧪 Mostrando banner de prueba")
        
        try {
            hideTestBanner()
            
            val inflater = LayoutInflater.from(context)
            testBannerView = inflater.inflate(R.layout.banner_overlay, null)
            
            testBannerView?.apply {
                findViewById<TextView>(R.id.sessionTimeText)?.text = "🧪 MODO PRUEBA"
                findViewById<TextView>(R.id.todayTotalText)?.visibility = View.GONE
                findViewById<TextView>(R.id.motivationalMessage)?.visibility = View.VISIBLE
                findViewById<TextView>(R.id.motivationalMessage)?.text = testMessage
                findViewById<TextView>(R.id.appNameLabel)?.visibility = View.GONE
                findViewById<ImageView>(R.id.ninjaIcon)?.visibility = View.VISIBLE
                
                setOnClickListener {
                    Log.d("BannerTest", "🖱️ Banner cerrado por usuario")
                    hideTestBanner()
                    onBannerClosed()
                }
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 300
            }
            
            windowManager?.addView(testBannerView, params)
            
            // Auto-ocultar después de 15 segundos
            handler.postDelayed({
                if (testBannerView != null) {
                    Log.d("BannerTest", "⏱️ Auto-ocultando")
                    hideTestBanner()
                    onBannerClosed()
                }
            }, 15000)
            
        } catch (e: Exception) {
            Log.e("BannerTest", "Error: ${e.message}")
        }
    }
    
    fun hideTestBanner() {
        try {
            testBannerView?.let { view ->
                windowManager?.removeView(view)
                testBannerView = null
            }
        } catch (e: Exception) {
            // Ignorar
        }
    }
}