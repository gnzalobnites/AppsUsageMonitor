package com.gnzalobnites.appsusagemonitor.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
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
                // Obtener el nuevo código de idioma (ej. "es", "en")
                val langCode = sharedPreferences.getString(key, "es") ?: "es"
                // Aplicar el idioma usando la API oficial de AppCompat
                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(langCode)
                AppCompatDelegate.setApplicationLocales(appLocale)
                // Nota: No es necesario llamar a recreate() aquí.
                // AppCompatDelegate reiniciará la actividad automáticamente.
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
