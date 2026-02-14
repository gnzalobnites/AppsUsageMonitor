package com.gnzalobnites.appsusagemonitor.banner

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.gnzalobnites.appsusagemonitor.R
import java.util.*

class BannerUIController(private val context: Context) {
    
    var bannerView: View? = null
    private var messageIndex = 0
    
    /**
     * Crear vista del banner
     */
    fun createBannerView(): View {
        val inflater = LayoutInflater.from(context)
        bannerView = inflater.inflate(R.layout.banner_overlay, null)
        return bannerView!!
    }
    
    /**
     * Configurar UI para modo minimizado
     */
    fun setupWaitingUI(sessionInfo: SessionInfo, onBannerClick: () -> Unit) {
        bannerView?.apply {
            // Tiempo de sesión
            findViewById<TextView>(R.id.sessionTimeText)?.let {
                it.text = TimeStats.formatTime(sessionInfo.getDuration())
                it.visibility = View.VISIBLE
            }
            
            // Nombre de app
            findViewById<TextView>(R.id.appNameLabel)?.let {
                it.text = " ${sessionInfo.appName}"
                it.visibility = View.VISIBLE
            }
            
            // Icono
            findViewById<ImageView>(R.id.ninjaIcon)?.let {
                it.visibility = View.VISIBLE
                applyIconColor(it, sessionInfo.getDuration())
            }
            
            // Ocultar elementos expandidos
            findViewById<View>(R.id.expandedContent)?.visibility = View.GONE
            findViewById<TextView>(R.id.todayTotalText)?.visibility = View.GONE
            findViewById<TextView>(R.id.motivationalMessage)?.visibility = View.GONE
            
            // Click listener
            setOnClickListener { onBannerClick() }
            
            // Estilo base
            applyBaseStyling()
            applyMinimizedColorScheme(sessionInfo.getDuration())
        }
    }
    
    /**
     * Expandir banner con animación
     */
    fun expandBanner(timeStats: TimeStats, appName: String) {
        bannerView?.apply {
            val expandedContent = findViewById<View>(R.id.expandedContent)
            
            expandedContent.visibility = View.VISIBLE
            expandedContent.alpha = 0f
            expandedContent.scaleY = 0.8f
            
            expandedContent.animate()
                ?.alpha(1f)
                ?.scaleY(1f)
                ?.setDuration(300)
                ?.setInterpolator(OvershootInterpolator(1.2f))
                ?.start()
            
            findViewById<TextView>(R.id.todayTotalText)?.visibility = View.VISIBLE
            findViewById<TextView>(R.id.motivationalMessage)?.visibility = View.VISIBLE
            
            updateExpandedContent(timeStats, appName)
        }
    }
    
    /**
     * Actualizar contenido expandido
     */
    fun updateExpandedContent(timeStats: TimeStats, appName: String) {
        bannerView?.apply {
            findViewById<TextView>(R.id.sessionTimeText)?.text = timeStats.formattedSessionTime
            findViewById<TextView>(R.id.appNameLabel)?.text = " $appName"
            findViewById<TextView>(R.id.todayTotalText)?.text = "Hoy: ${timeStats.formattedTodayTotal}"
            
            // Mensaje motivacional aleatorio
            if (java.util.Random().nextInt(10) == 0) {
                findViewById<TextView>(R.id.motivationalMessage)?.text = 
                    getRandomMotivationalMessage()
            }
            
            applyColorSchemeBasedOnTime(timeStats.sessionTime)
        }
    }
    
    /**
     * Actualizar solo elementos minimizados
     */
    fun updateMinimizedContent(timeStats: TimeStats, appName: String) {
        bannerView?.apply {
            findViewById<TextView>(R.id.sessionTimeText)?.text = timeStats.formattedSessionTime
            findViewById<TextView>(R.id.appNameLabel)?.text = " $appName"
            findViewById<ImageView>(R.id.ninjaIcon)?.let { 
                applyIconColor(it, timeStats.sessionTime)
            }
            applyMinimizedColorScheme(timeStats.sessionTime)
        }
    }
    
    /**
     * Ocultar banner con animación
     */
    fun hideWithAnimation(onComplete: () -> Unit) {
        bannerView?.animate()
            ?.alpha(0f)
            ?.scaleX(0.8f)
            ?.scaleY(0.8f)
            ?.setDuration(300)
            ?.withEndAction {
                onComplete()
                bannerView = null
            }
            ?.start()
    }
    
    // ========== MÉTODOS DE ESTILO ==========
    
    private fun applyIconColor(imageView: ImageView, duration: Long) {
        val minutes = duration / 60000
        val color = when {
            minutes > 30 -> 0xFFFF8B94.toInt()
            minutes > 15 -> 0xFFFFD3B6.toInt()
            else -> 0xFFA8E6CF.toInt()
        }
        imageView.setColorFilter(color)
    }
    
    private fun applyMinimizedColorScheme(duration: Long) {
        val minutes = duration / 60000
        bannerView?.apply {
            val sessionTimeText = findViewById<TextView>(R.id.sessionTimeText)
            val appNameLabel = findViewById<TextView>(R.id.appNameLabel)
            val ninjaIcon = findViewById<ImageView>(R.id.ninjaIcon)
            
            val accentColor = when {
                minutes > 30 -> 0xFFFF8B94.toInt()
                minutes > 15 -> 0xFFFFD3B6.toInt()
                else -> 0xFFA8E6CF.toInt()
            }
            
            sessionTimeText?.setTextColor(accentColor)
            appNameLabel?.setTextColor(accentColor)
            ninjaIcon?.setColorFilter(accentColor)
        }
    }
    
    private fun applyColorSchemeBasedOnTime(duration: Long) {
        val minutes = duration / 60000
        bannerView?.apply {
            val sessionTimeText = findViewById<TextView>(R.id.sessionTimeText)
            val todayTotalText = findViewById<TextView>(R.id.todayTotalText)
            val ninjaIcon = findViewById<ImageView>(R.id.ninjaIcon)
            val appNameLabel = findViewById<TextView>(R.id.appNameLabel)
            val motivationalMessage = findViewById<TextView>(R.id.motivationalMessage)
            
            when {
                minutes > 30 -> {
                    val accentColor = 0xFFFF8B94.toInt()
                    sessionTimeText?.setTextColor(accentColor)
                    ninjaIcon?.setColorFilter(accentColor)
                    appNameLabel?.setTextColor(accentColor)
                    todayTotalText?.setTextColor(0xFFD4A5A5.toInt())
                    motivationalMessage?.setTextColor(0xFFE8D6D6.toInt())
                    findViewById<CardView>(R.id.bannerRootCard)?.setCardBackgroundColor(0xCC1A1A1A.toInt())
                }
                minutes > 15 -> {
                    val accentColor = 0xFFFFD3B6.toInt()
                    sessionTimeText?.setTextColor(accentColor)
                    ninjaIcon?.setColorFilter(accentColor)
                    appNameLabel?.setTextColor(accentColor)
                    todayTotalText?.setTextColor(0xFFE6C4A8.toInt())
                    motivationalMessage?.setTextColor(0xFFF5E6D6.toInt())
                }
                else -> {
                    val accentColor = 0xFFA8E6CF.toInt()
                    sessionTimeText?.setTextColor(accentColor)
                    ninjaIcon?.setColorFilter(accentColor)
                    appNameLabel?.setTextColor(accentColor)
                    todayTotalText?.setTextColor(0xFFC6E6D6.toInt())
                    motivationalMessage?.setTextColor(0xFFE6F5EF.toInt())
                }
            }
        }
    }
    
    private fun applyBaseStyling() {
        bannerView?.findViewById<CardView>(R.id.bannerRootCard)?.apply {
            cardElevation = 8f
            radius = 24f
            alpha = 0.95f
        }
    }
    
    private fun getRandomMotivationalMessage(): String {
        messageIndex = (messageIndex + 1) % MotivationalMessages.messages.size
        return MotivationalMessages.messages[messageIndex]
    }
}