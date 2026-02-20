package com.gnzalobnites.appsusagemonitor.banner

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.gnzalobnites.appsusagemonitor.R
import java.util.*

class BannerUIController(private val context: Context) {

    var bannerView: View? = null
    private var messageIndex = 0
    var isAnimating: Boolean = false
        private set

    fun createBannerView(): View {
        val inflater = LayoutInflater.from(context)
        bannerView = inflater.inflate(R.layout.banner_overlay, null).apply {
            tag = BannerViewReferences(
                clickableArea = findViewById(R.id.clickableArea),
                sessionTimeText = findViewById(R.id.sessionTimeText),
                appNameLabel = findViewById(R.id.appNameLabel),
                todayTotalText = findViewById(R.id.todayTotalText),
                motivationalMessage = findViewById(R.id.motivationalMessage),
                ninjaIcon = findViewById(R.id.ninjaIcon),
                expandedContent = findViewById(R.id.expandedContent),
                bannerRootCard = findViewById(R.id.bannerRootCard)
            )
        }
        applyBaseStyling()
        return bannerView!!
    }

    fun setupWaitingUI(sessionInfo: SessionInfo, onBannerClick: () -> Unit) {
        bannerView?.apply {
            val refs = tag as BannerViewReferences
            
            // Cancelar cualquier animación previa
            animate().cancel()
            
            // Configurar textos
            refs.sessionTimeText?.text = TimeStats.formatTime(sessionInfo.getDuration())
            refs.appNameLabel?.text = " ${sessionInfo.appName}"
            
            // Asegurar que el contenido expandido esté OCULTO
            refs.expandedContent?.visibility = View.GONE
            refs.todayTotalText?.visibility = View.GONE
            refs.motivationalMessage?.visibility = View.GONE
            
            refs.ninjaIcon?.visibility = View.VISIBLE
            refs.ninjaIcon?.alpha = 1.0f
            
            // Asegurar que el banner sea completamente visible
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            
            // Configurar click listener
            refs.clickableArea?.setOnClickListener {
                if (!isAnimating) {
                    onBannerClick()
                }
            }
        }
    }

    fun expandBanner(timeStats: TimeStats, appName: String) {
        if (isAnimating) return
        isAnimating = true
        
        bannerView?.apply {
            val refs = tag as BannerViewReferences
            
            // Cancelar animación previa
            animate().cancel()
            
            // Mostrar el contenido expandido con animación
            refs.expandedContent?.let { expanded ->
                expanded.visibility = View.VISIBLE
                expanded.alpha = 0f
                expanded.scaleY = 0.8f
                
                expanded.animate()
                    ?.alpha(1f)
                    ?.scaleY(1f)
                    ?.setDuration(300)
                    ?.setInterpolator(OvershootInterpolator(1.2f))
                    ?.withEndAction {
                        isAnimating = false
                    }
                    ?.start()
            }
            
            updateExpandedContent(timeStats, appName)
        }
    }

    fun updateExpandedContent(timeStats: TimeStats, appName: String) {
        bannerView?.apply {
            val refs = tag as BannerViewReferences
            
            refs.sessionTimeText?.text = timeStats.formattedSessionTime
            refs.appNameLabel?.text = " $appName"
            
            refs.todayTotalText?.apply {
                visibility = View.VISIBLE
                text = "Hoy: ${timeStats.formattedTodayTotal}"
            }
            
            refs.motivationalMessage?.apply {
                visibility = View.VISIBLE
                if (Random().nextInt(10) == 0) {
                    text = getRandomMotivationalMessage()
                }
            }
            
            refs.ninjaIcon?.visibility = View.VISIBLE
        }
    }

    fun updateMinimizedContent(timeStats: TimeStats, appName: String) {
        bannerView?.apply {
            val refs = tag as BannerViewReferences
            
            refs.sessionTimeText?.text = timeStats.formattedSessionTime
            refs.appNameLabel?.text = " $appName"
            
            // Asegurar que el contenido expandido esté OCULTO
            refs.expandedContent?.visibility = View.GONE
            refs.todayTotalText?.visibility = View.GONE
            refs.motivationalMessage?.visibility = View.GONE
        }
    }

    fun hideWithAnimation(onComplete: () -> Unit) {
        if (isAnimating) return
        isAnimating = true
        
        bannerView?.apply {
            // Cancelar cualquier animación previa
            animate().cancel()
            
            // Animar
            animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(300)
                .withEndAction {
                    // Restauramos propiedades para la próxima vez
                    alpha = 1f
                    scaleX = 1f
                    scaleY = 1f
                    isAnimating = false
                    onComplete()
                }
                .start()
        }
    }

    private fun applyBaseStyling() {
        bannerView?.findViewById<CardView>(R.id.bannerRootCard)?.apply {
            cardElevation = 8f
            radius = 24f
        }
    }

    private fun getRandomMotivationalMessage(): String {
        messageIndex = (messageIndex + 1) % MotivationalMessages.messages.size
        return MotivationalMessages.messages[messageIndex]
    }

    private data class BannerViewReferences(
        val clickableArea: LinearLayout?,
        val sessionTimeText: TextView?,
        val appNameLabel: TextView?,
        val todayTotalText: TextView?,
        val motivationalMessage: TextView?,
        val ninjaIcon: ImageView?,
        val expandedContent: View?,
        val bannerRootCard: CardView?
    )
}