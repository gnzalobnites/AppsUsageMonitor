package com.gnzalobnites.appsusagemonitor

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    
    companion object {
        private const val SPLASH_DELAY_MS = 1500L
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var splashRunnable: Runnable? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ Si la actividad ya fue creada, finalizar para evitar duplicados
        if (savedInstanceState != null) {
            finish()
            return
        }
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setContentView(R.layout.activity_splash)
        supportActionBar?.hide()
        
        splashRunnable = Runnable {
            // ✅ Verificar estado de la actividad antes de navegar
            if (!isFinishing && !isDestroyed) {
                navigateToMainActivity()
            }
        }
        
        splashRunnable?.let {
            handler.postDelayed(it, SPLASH_DELAY_MS)
        }
    }
    
    private fun navigateToMainActivity() {
        // ✅ Verificación adicional de seguridad
        if (isFinishing || isDestroyed) return
        
        val intent = Intent(this, MainActivity::class.java).apply {
            // ✅ Limpiar la pila de actividades para evitar volver al splash
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
    
    override fun onBackPressed() {
        // ✅ Deshabilitar el botón atrás durante el splash
        // No hacer nada
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // ✅ Limpiar correctamente el Handler y el Runnable
        splashRunnable?.let { handler.removeCallbacks(it) }
        splashRunnable = null
    }
}