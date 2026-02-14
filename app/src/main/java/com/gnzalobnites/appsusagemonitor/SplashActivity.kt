package com.gnzalobnites.appsusagemonitor

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    
    companion object {
        private const val SPLASH_DELAY_MS = 1500L // 1.5 segundos
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configurar pantalla completa (sin barra de estado)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setContentView(R.layout.activity_splash)
        
        // Ocultar action bar si existe
        supportActionBar?.hide()
                
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToMainActivity()
        }, SPLASH_DELAY_MS)
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainNavActivity::class.java)
        startActivity(intent)
        
        // Finalizar esta actividad para que no se pueda volver atrás
        finish()
        
        // Transición suave
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
    
    // Evitar que el usuario pueda presionar Back durante el splash
    override fun onBackPressed() {
        // No hacer nada durante el splash
    }
}
