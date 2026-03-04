package com.gnzalobnites.appsusagemonitor.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceFragmentCompat
import com.gnzalobnites.appsusagemonitor.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    // Listener para cambios en las preferencias
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            "theme_mode" -> {
                val theme = sharedPreferences.getString(key, "system")
                applyTheme(theme)
            }
            "language" -> {
                // Guardar el tema actual antes de cambiar idioma
                val currentTheme = sharedPreferences.getString("theme_mode", "system")
            
                val langCode = sharedPreferences.getString(key, "es") ?: "es"
                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(langCode)
            
                // Aplicar idioma (esto reiniciará la actividad)
                AppCompatDelegate.setApplicationLocales(appLocale)
            
                // Forzar re-aplicación del tema después del reinicio
                Handler(Looper.getMainLooper()).postDelayed({
                    applyTheme(currentTheme)
                }, 500)
            }
        }
    }

    private fun applyTheme(theme: String?) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    override fun onResume() {
        super.onResume()
        // Registrar el listener para estar atento a los cambios
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onPause() {
        super.onPause()
        // Desregistrar el listener para evitar fugas de memoria
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
