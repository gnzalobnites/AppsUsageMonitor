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
    
    fun createBannerView(): View {
        val inflater = LayoutInflater.from(context)
        bannerView = inflater.inflate(R.layout.banner_overlay, null)
        bannerView?.setOnTouchListener(null)
        return bannerView!!
    }
    
    fun setupWaitingUI(sessionInfo: SessionInfo, onBannerClick: () -> Unit) {
        bannerView?.apply {
            findViewById<TextView>(R.id.sessionTimeText)?.let {
                it.text = TimeStats.formatTime(sessionInfo.getDuration())
                it.setTextColor(android.graphics.Color.WHITE)
            }
            
            findViewById<TextView>(R.id.appNameLabel)?.let {
                it.text = " ${sessionInfo.appName}"
                it.setTextColor(android.graphics.Color.WHITE)
            }
            
            // Icono estilo Notification Badge
            findViewById<ImageView>(R.id.ninjaIcon)?.let {
                it.visibility = View.VISIBLE
                it.alpha = 1.0f // Máxima opacidad para el globo
                it.clearColorFilter() // Asegura colores originales del XML
            }
            
            findViewById<View>(R.id.expandedContent)?.visibility = View.GONE
            setOnClickListener { onBannerClick() }
            applyBaseStyling()
        }
    }
    
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
            
            updateExpandedContent(timeStats, appName)
        }
    }
    
    fun updateExpandedContent(timeStats: TimeStats, appName: String) {
        bannerView?.apply {
            findViewById<TextView>(R.id.sessionTimeText)?.text = timeStats.formattedSessionTime
            findViewById<TextView>(R.id.appNameLabel)?.text = " $appName"
            findViewById<TextView>(R.id.todayTotalText)?.text = "Hoy: ${timeStats.formattedTodayTotal}"
            
            findViewById<TextView>(R.id.motivationalMessage)?.apply {
                if (java.util.Random().nextInt(10) == 0) {
                    text = getRandomMotivationalMessage()
                }
                setTextColor(android.graphics.Color.WHITE)
            }

            findViewById<ImageView>(R.id.ninjaIcon)?.let {
                it.alpha = 1.0f
                it.clearColorFilter()
            }
        }
    }

    fun updateMinimizedContent(timeStats: TimeStats, appName: String) {
        bannerView?.apply {
            findViewById<TextView>(R.id.sessionTimeText)?.text = timeStats.formattedSessionTime
            findViewById<TextView>(R.id.appNameLabel)?.text = " $appName"
            
            findViewById<ImageView>(R.id.ninjaIcon)?.let {
                it.alpha = 1.0f
                it.clearColorFilter()
            }
        }
    }
    
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
    
    private fun applyBaseStyling() {
        bannerView?.findViewById<CardView>(R.id.bannerRootCard)?.apply {
            cardElevation = 8f
            radius = 24f
            setCardBackgroundColor(0xCC1A1A1A.toInt())
        }
    }
    
    private fun getRandomMotivationalMessage(): String {
        messageIndex = (messageIndex + 1) % MotivationalMessages.messages.size
        return MotivationalMessages.messages[messageIndex]
    }
}
