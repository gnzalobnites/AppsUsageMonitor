package com.gnzalobnites.appsusagemonitor.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.gnzalobnites.appsusagemonitor.AppUsageMonitorApp
import com.gnzalobnites.appsusagemonitor.MainViewModel
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.UserPreferences
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private val TAG = "SettingsFragment"
    private lateinit var viewModel: MainViewModel
    
    // UI Elements
    private lateinit var swDarkMode: Switch
    private lateinit var swAutoStart: Switch
    private lateinit var swBatteryOptimization: Switch
    private lateinit var swVibrateNotification: Switch
    private lateinit var swSoundNotification: Switch
    private lateinit var swExportDailyReports: Switch
    
    private lateinit var tvThemeStatus: TextView
    private lateinit var tvAutoStartStatus: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvVibrateStatus: TextView
    private lateinit var tvSoundStatus: TextView
    private lateinit var tvExportStatus: TextView
    private lateinit var tvSettingsStatus: TextView
    
    // Spinner de intervalo (solo una opción)
    private lateinit var spBannersPerDay: Spinner
    private lateinit var tvBannersPerDayValue: TextView
    
    private lateinit var btnSaveSettings: Button
    private lateinit var btnResetSettings: Button
    
    // Valores de configuración - ELIMINADO el valor 0 (1 segundo)
    private val bannerIntervalValues = arrayOf(-1, 1, 2, 3, 5, 10, 15, 20, 30, 45, 60)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings_enhanced, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d(TAG, "onViewCreated - SettingsFragment")
        
        try {
            viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
            
            initViews(view)
            setupSpinners()
            loadCurrentSettings()
            setupListeners()
            updateAllStatusTexts()
            updateSettingsStatus()
            
            observeDarkModeChanges()
            
            Log.d(TAG, "✅ SettingsFragment configurado")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en onViewCreated: ${e.message}", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun initViews(view: View) {
        swDarkMode = view.findViewById(R.id.swDarkMode)
        swAutoStart = view.findViewById(R.id.swAutoStart)
        swBatteryOptimization = view.findViewById(R.id.swBatteryOptimization)
        swVibrateNotification = view.findViewById(R.id.swVibrateNotification)
        swSoundNotification = view.findViewById(R.id.swSoundNotification)
        swExportDailyReports = view.findViewById(R.id.swExportDailyReports)
        
        tvThemeStatus = view.findViewById(R.id.tvThemeStatus)
        tvAutoStartStatus = view.findViewById(R.id.tvAutoStartStatus)
        tvBatteryStatus = view.findViewById(R.id.tvBatteryStatus)
        tvVibrateStatus = view.findViewById(R.id.tvVibrateStatus)
        tvSoundStatus = view.findViewById(R.id.tvSoundStatus)
        tvExportStatus = view.findViewById(R.id.tvExportStatus)
        tvSettingsStatus = view.findViewById(R.id.tvSettingsStatus)
        
        spBannersPerDay = view.findViewById(R.id.spBannersPerDay)
        tvBannersPerDayValue = view.findViewById(R.id.tvBannersPerDayValue)
        
        btnSaveSettings = view.findViewById(R.id.btnSaveSettings)
        btnResetSettings = view.findViewById(R.id.btnResetSettings)
    }
    
    private fun setupSpinners() {
        try {
            // SOLO spinner para intervalo de banners
            // MODIFICADO: Eliminado "1 segundo (PRUEBAS)" y renombrado -1 como "10 segundos (DEMO)"
            val intervalLabels = bannerIntervalValues.map { value ->
                when (value) {
                    -1 -> "10 segundos"
                    1 -> "1 minuto"
                    2 -> "2 minutos"
                    3 -> "3 minutos"
                    5 -> "5 minutos"
                    10 -> "10 minutos"
                    15 -> "15 minutos"
                    20 -> "20 minutos"
                    30 -> "30 minutos"
                    45 -> "45 minutos"
                    60 -> "60 minutos"
                    else -> "$value minutos"
                }
            }.toTypedArray()
            
            val intervalAdapter = ArrayAdapter(
                requireContext(), 
                android.R.layout.simple_spinner_item, 
                intervalLabels
            )
            intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spBannersPerDay.adapter = intervalAdapter
            
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando spinner: ${e.message}")
        }
    }
    
    private fun loadCurrentSettings() {
        // Remover listener temporalmente
        swDarkMode.setOnCheckedChangeListener(null)
        
        try {
            // Tema
            val isDarkMode = viewModel.isDarkMode.value ?: false
            swDarkMode.isChecked = isDarkMode
            tvThemeStatus.text = if (isDarkMode) "🌙 Tema oscuro" else "☀️ Tema claro"
            
            // Intervalo (única opción)
            val interval = viewModel.bannerInterval.value ?: 5
            // Buscar el índice correspondiente (si no existe, usar 5 minutos que es índice 4)
            val intervalIndex = bannerIntervalValues.indexOf(interval).takeIf { it >= 0 } ?: 4
            spBannersPerDay.setSelection(intervalIndex)
            
            val prefs = UserPreferences.getInstance(requireContext())
            tvBannersPerDayValue.text = getBannerIntervalDisplayText(interval)
            
            // Valores por defecto para otras opciones
            swAutoStart.isChecked = false
            swBatteryOptimization.isChecked = true
            swVibrateNotification.isChecked = true
            swSoundNotification.isChecked = false
            swExportDailyReports.isChecked = false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando configuración: ${e.message}")
        }
    }
    
    // NUEVO: Función helper para obtener texto de visualización
    private fun getBannerIntervalDisplayText(interval: Int): String {
        return when (interval) {
            -1 -> "10 segundos (DEMO)"
            1 -> "1 minuto"
            2 -> "2 minutos"
            3 -> "3 minutos"
            5 -> "5 minutos"
            10 -> "10 minutos"
            15 -> "15 minutos"
            20 -> "20 minutos"
            30 -> "30 minutos"
            45 -> "45 minutos"
            60 -> "60 minutos"
            else -> "$interval minutos"
        }
    }
    
    private fun setupListeners() {
        swDarkMode.setOnCheckedChangeListener { _, isChecked ->
            tvThemeStatus.text = if (isChecked) "🌙 Tema oscuro" else "☀️ Tema claro"
            viewModel.updateDarkMode(isChecked)
            applyThemeChange(isChecked)
        }
        
        spBannersPerDay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val value = bannerIntervalValues[position]
                viewModel.updateBannerInterval(value)
                
                tvBannersPerDayValue.text = getBannerIntervalDisplayText(value)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        swAutoStart.setOnCheckedChangeListener { _, isChecked ->
            tvAutoStartStatus.text = if (isChecked) "✅ Inicio automático" else "⭕ Inicio manual"
        }
        
        swBatteryOptimization.setOnCheckedChangeListener { _, isChecked ->
            tvBatteryStatus.text = if (isChecked) "✅ Optimización activa" else "⚠️ Sin optimización"
        }
        
        swVibrateNotification.setOnCheckedChangeListener { _, isChecked ->
            tvVibrateStatus.text = if (isChecked) "✅ Vibración activa" else "🔇 Sin vibración"
        }
        
        swSoundNotification.setOnCheckedChangeListener { _, isChecked ->
            tvSoundStatus.text = if (isChecked) "🔔 Sonido activo" else "🔇 Silencio"
        }
        
        swExportDailyReports.setOnCheckedChangeListener { _, isChecked ->
            tvExportStatus.text = if (isChecked) "✅ Exportación automática" else "⭕ Exportación manual"
        }
        
        btnSaveSettings.setOnClickListener { saveSettings() }
        btnResetSettings.setOnClickListener { resetSettings() }
    }
    
    private fun observeDarkModeChanges() {
        viewModel.isDarkMode.observe(viewLifecycleOwner, Observer<Boolean> { isDarkMode ->
            swDarkMode.setOnCheckedChangeListener(null)
            if (swDarkMode.isChecked != isDarkMode) {
                swDarkMode.isChecked = isDarkMode
            }
            tvThemeStatus.text = if (isDarkMode) "🌙 Tema oscuro" else "☀️ Tema claro"
            
            swDarkMode.setOnCheckedChangeListener { _, checked ->
                tvThemeStatus.text = if (checked) "🌙 Tema oscuro" else "☀️ Tema claro"
                viewModel.updateDarkMode(checked)
                applyThemeChange(checked)
            }
        })
    }
    
    private fun updateAllStatusTexts() {
        tvThemeStatus.text = if (swDarkMode.isChecked) "🌙 Tema oscuro" else "☀️ Tema claro"
        tvAutoStartStatus.text = if (swAutoStart.isChecked) "✅ Inicio automático" else "⭕ Inicio manual"
        tvBatteryStatus.text = if (swBatteryOptimization.isChecked) "✅ Optimización activa" else "⚠️ Sin optimización"
        tvVibrateStatus.text = if (swVibrateNotification.isChecked) "✅ Vibración activa" else "🔇 Sin vibración"
        tvSoundStatus.text = if (swSoundNotification.isChecked) "🔔 Sonido activo" else "🔇 Silencio"
        tvExportStatus.text = if (swExportDailyReports.isChecked) "✅ Exportación automática" else "⭕ Exportación manual"
    }
    
    private fun updateSettingsStatus() {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        tvSettingsStatus.text = "Última actualización: $currentTime"
    }
    
    private fun applyThemeChange(isDarkMode: Boolean) {
        try {
            viewModel.updateDarkMode(isDarkMode)
            
            val app = requireActivity().application as AppUsageMonitorApp
            app.applyTheme(isDarkMode)
            
            requireActivity().recreate()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando tema: ${e.message}")
            Toast.makeText(requireContext(), "Error al cambiar tema", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveSettings() {
        viewModel.saveAllSettings()
        updateSettingsStatus()
        Toast.makeText(requireContext(), "✅ Configuraciones guardadas", Toast.LENGTH_SHORT).show()
        showSaveConfirmation()
    }
    
    private fun showSaveConfirmation() {
        val interval = bannerIntervalValues[spBannersPerDay.selectedItemPosition]
        
        val summary = StringBuilder().apply {
            append("⚙️ CONFIGURACIONES GUARDADAS:\n\n")
            append("• Tema: ${if (swDarkMode.isChecked) "Oscuro" else "Claro"}\n")
            append("• Intervalo: ${getBannerIntervalDisplayText(interval)}\n")
            append("• Inicio automático: ${if (swAutoStart.isChecked) "Sí" else "No"}\n")
            append("• Optimización batería: ${if (swBatteryOptimization.isChecked) "Sí" else "No"}\n")
            append("• Vibración: ${if (swVibrateNotification.isChecked) "Sí" else "No"}\n")
            append("• Sonido: ${if (swSoundNotification.isChecked) "Sí" else "No"}\n")
            append("• Exportación automática: ${if (swExportDailyReports.isChecked) "Sí" else "No"}\n\n")
            append("✅ El banner permanece visible hasta que lo cierres manualmente")
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Configuraciones Guardadas")
            .setMessage(summary.toString())
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun resetSettings() {
        AlertDialog.Builder(requireContext())
            .setTitle("Restablecer Configuraciones")
            .setMessage("¿Estás seguro de querer restablecer todas las configuraciones?")
            .setPositiveButton("Sí, restablecer") { _, _ ->
                performReset()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun performReset() {
        swDarkMode.setOnCheckedChangeListener(null)
        
        swDarkMode.isChecked = false
        swAutoStart.isChecked = false
        swBatteryOptimization.isChecked = true
        swVibrateNotification.isChecked = true
        swSoundNotification.isChecked = false
        swExportDailyReports.isChecked = false
        
        spBannersPerDay.setSelection(4) // 5 minutos por defecto
        
        updateAllStatusTexts()
        
        tvBannersPerDayValue.text = "5 minutos"
        
        viewModel.clearAllSettings()
        
        swDarkMode.setOnCheckedChangeListener { _, isChecked ->
            tvThemeStatus.text = if (isChecked) "🌙 Tema oscuro" else "☀️ Tema claro"
            viewModel.updateDarkMode(isChecked)
            applyThemeChange(isChecked)
        }
        
        applyThemeChange(false)
        
        Toast.makeText(requireContext(), "✅ Configuraciones restablecidas", Toast.LENGTH_SHORT).show()
        tvSettingsStatus.text = "Configuraciones restablecidas"
    }
}