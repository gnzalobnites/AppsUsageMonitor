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
        
        // Garantizar que no hay listeners que consuman eventos
        bannerView?.setOnTouchListener(null)
        
        return bannerView!!
    }
    
    /**
     * Configurar UI para modo minimizado
     */
    fun setupWaitingUI(sessionInfo: SessionInfo, onBannerClick: () -> Unit) {
        bannerView?.apply {
            // Tiempo de sesión (siempre blanco)
            findViewById<TextView>(R.id.sessionTimeText)?.let {
                it.text = TimeStats.formatTime(sessionInfo.getDuration())
                it.setTextColor(android.graphics.Color.WHITE)
                it.visibility = View.VISIBLE
            }
            
            // Nombre de app (siempre blanco)
            findViewById<TextView>(R.id.appNameLabel)?.let {
                it.text = " ${sessionInfo.appName}"
                it.setTextColor(android.graphics.Color.WHITE)
                it.visibility = View.VISIBLE
            }
            
            // Icono - color basado en tiempo
            findViewById<ImageView>(R.id.ninjaIcon)?.let {
                it.visibility = View.VISIBLE
                applyIconColorBasedOnTime(it, sessionInfo.getDuration())
            }
            
            // Asegurar que el texto de hoy (si aparece) sea blanco
            findViewById<TextView>(R.id.todayTotalText)?.setTextColor(android.graphics.Color.WHITE)
            
            // Ocultar elementos expandidos inicialmente
            findViewById<View>(R.id.expandedContent)?.visibility = View.GONE
            findViewById<TextView>(R.id.todayTotalText)?.visibility = View.GONE
            findViewById<TextView>(R.id.motivationalMessage)?.visibility = View.GONE
            
            // ¡CORREGIDO! Click listener para AMBOS estados (minimizado y expandido)
            // El banner SIEMPRE debe detectar clicks cuando está visible
            setOnClickListener { onBannerClick() }
            
            // Estilo base
            applyBaseStyling()
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
            // Tiempo de sesión (blanco)
            findViewById<TextView>(R.id.sessionTimeText)?.apply {
                text = timeStats.formattedSessionTime
                setTextColor(android.graphics.Color.WHITE)
            }
            
            // Nombre de app (blanco)
            findViewById<TextView>(R.id.appNameLabel)?.apply {
                text = " $appName"
                setTextColor(android.graphics.Color.WHITE)
            }
            
            // Total hoy (blanco)
            findViewById<TextView>(R.id.todayTotalText)?.apply {
                text = "Hoy: ${timeStats.formattedTodayTotal}"
                setTextColor(android.graphics.Color.WHITE)
            }
            
            // Mensaje motivacional (blanco)
            findViewById<TextView>(R.id.motivationalMessage)?.apply {
                if (java.util.Random().nextInt(10) == 0) {
                    text = getRandomMotivationalMessage()
                }
                setTextColor(android.graphics.Color.WHITE)
            }
            
            // Icono - color basado en tiempo
            findViewById<ImageView>(R.id.ninjaIcon)?.let {
                applyIconColorBasedOnTime(it, timeStats.sessionTime)
            }
        }
    }
    
    /**
     * Actualizar solo elementos minimizados
     */
    fun updateMinimizedContent(timeStats: TimeStats, appName: String) {
        bannerView?.apply {
            // Tiempo de sesión (blanco)
            findViewById<TextView>(R.id.sessionTimeText)?.apply {
                text = timeStats.formattedSessionTime
                setTextColor(android.graphics.Color.WHITE)
            }
            
            // Nombre de app (blanco)
            findViewById<TextView>(R.id.appNameLabel)?.apply {
                text = " $appName"
                setTextColor(android.graphics.Color.WHITE)
            }
            
            // Icono - color basado en tiempo
            findViewById<ImageView>(R.id.ninjaIcon)?.let {
                applyIconColorBasedOnTime(it, timeStats.sessionTime)
            }
        }
    }
    
    /**
     * Aplica color al ícono basado en el tiempo de uso
     */
    private fun applyIconColorBasedOnTime(imageView: ImageView, duration: Long) {
        val minutes = duration / 60000
        val color = when {
            minutes > 30 -> android.graphics.Color.parseColor("#FF6B6B")  // Rojo coral para >30 min
            minutes > 15 -> android.graphics.Color.parseColor("#FFB347")  // Naranja para 15-30 min
            minutes > 5  -> android.graphics.Color.parseColor("#4ECDC4")  // Turquesa para 5-15 min
            else -> android.graphics.Color.parseColor("#95E1D3")          // Verde agua para <5 min
        }
        imageView.setColorFilter(color)
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
    
    private fun applyBaseStyling() {
        bannerView?.findViewById<CardView>(R.id.bannerRootCard)?.apply {
            cardElevation = 8f
            radius = 24f
            alpha = 0.95f
            // Mantener fondo oscuro para mejor contraste con texto blanco
            setCardBackgroundColor(0xCC1A1A1A.toInt())
        }
    }
    
    private fun getRandomMotivationalMessage(): String {
        messageIndex = (messageIndex + 1) % MotivationalMessages.messages.size
        return MotivationalMessages.messages[messageIndex]
    }
}