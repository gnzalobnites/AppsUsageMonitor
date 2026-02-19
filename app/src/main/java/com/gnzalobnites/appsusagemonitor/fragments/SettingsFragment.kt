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
    
    // Array de strings para el spinner
    private lateinit var intervalLabels: Array<String>

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
            
            // Cargar el array de strings del XML
            intervalLabels = resources.getStringArray(R.array.banner_interval_labels)
            
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
            Toast.makeText(requireContext(), getString(R.string.error_occurred) + ": ${e.message}", Toast.LENGTH_SHORT).show()
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
            // Usar el array de strings del XML directamente
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
            tvThemeStatus.text = if (isDarkMode) 
                getString(R.string.settings_theme_dark) 
            else 
                getString(R.string.settings_theme_light)
            
            // Intervalo (única opción)
            val interval = viewModel.bannerInterval.value ?: 5
            // Buscar el índice correspondiente (si no existe, usar 5 minutos que es índice 4)
            val intervalIndex = bannerIntervalValues.indexOf(interval).takeIf { it >= 0 } ?: 4
            spBannersPerDay.setSelection(intervalIndex)
            
            val prefs = UserPreferences.getInstance(requireContext())
            tvBannersPerDayValue.text = intervalLabels[intervalIndex]
            
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
    
    private fun setupListeners() {
        swDarkMode.setOnCheckedChangeListener { _, isChecked ->
            tvThemeStatus.text = if (isChecked) 
                getString(R.string.settings_theme_dark) 
            else 
                getString(R.string.settings_theme_light)
            viewModel.updateDarkMode(isChecked)
            applyThemeChange(isChecked)
        }
        
        spBannersPerDay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val value = bannerIntervalValues[position]
                viewModel.updateBannerInterval(value)
                
                tvBannersPerDayValue.text = intervalLabels[position]
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        swAutoStart.setOnCheckedChangeListener { _, isChecked ->
            tvAutoStartStatus.text = if (isChecked) 
                getString(R.string.settings_auto_start_auto) 
            else 
                getString(R.string.settings_auto_start_manual)
        }
        
        swBatteryOptimization.setOnCheckedChangeListener { _, isChecked ->
            tvBatteryStatus.text = if (isChecked) 
                getString(R.string.settings_battery_on) 
            else 
                getString(R.string.settings_battery_off)
        }
        
        swVibrateNotification.setOnCheckedChangeListener { _, isChecked ->
            tvVibrateStatus.text = if (isChecked) 
                getString(R.string.settings_vibration_on) 
            else 
                getString(R.string.settings_vibration_off)
        }
        
        swSoundNotification.setOnCheckedChangeListener { _, isChecked ->
            tvSoundStatus.text = if (isChecked) 
                getString(R.string.settings_sound_on) 
            else 
                getString(R.string.settings_sound_off)
        }
        
        swExportDailyReports.setOnCheckedChangeListener { _, isChecked ->
            tvExportStatus.text = if (isChecked) 
                getString(R.string.settings_export_auto) 
            else 
                getString(R.string.settings_export_manual)
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
            tvThemeStatus.text = if (isDarkMode) 
                getString(R.string.settings_theme_dark) 
            else 
                getString(R.string.settings_theme_light)
            
            swDarkMode.setOnCheckedChangeListener { _, checked ->
                tvThemeStatus.text = if (checked) 
                    getString(R.string.settings_theme_dark) 
                else 
                    getString(R.string.settings_theme_light)
                viewModel.updateDarkMode(checked)
                applyThemeChange(checked)
            }
        })
    }
    
    private fun updateAllStatusTexts() {
        tvThemeStatus.text = if (swDarkMode.isChecked) 
            getString(R.string.settings_theme_dark) 
        else 
            getString(R.string.settings_theme_light)
        tvAutoStartStatus.text = if (swAutoStart.isChecked) 
            getString(R.string.settings_auto_start_auto) 
        else 
            getString(R.string.settings_auto_start_manual)
        tvBatteryStatus.text = if (swBatteryOptimization.isChecked) 
            getString(R.string.settings_battery_on) 
        else 
            getString(R.string.settings_battery_off)
        tvVibrateStatus.text = if (swVibrateNotification.isChecked) 
            getString(R.string.settings_vibration_on) 
        else 
            getString(R.string.settings_vibration_off)
        tvSoundStatus.text = if (swSoundNotification.isChecked) 
            getString(R.string.settings_sound_on) 
        else 
            getString(R.string.settings_sound_off)
        tvExportStatus.text = if (swExportDailyReports.isChecked) 
            getString(R.string.settings_export_auto) 
        else 
            getString(R.string.settings_export_manual)
    }
    
    private fun updateSettingsStatus() {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        tvSettingsStatus.text = getString(R.string.settings_last_update, currentTime)
    }
    
    private fun applyThemeChange(isDarkMode: Boolean) {
        try {
            viewModel.updateDarkMode(isDarkMode)
            
            val app = requireActivity().application as AppUsageMonitorApp
            app.applyTheme(isDarkMode)
            
            requireActivity().recreate()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando tema: ${e.message}")
            Toast.makeText(requireContext(), getString(R.string.error_occurred), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveSettings() {
        viewModel.saveAllSettings()
        updateSettingsStatus()
        Toast.makeText(requireContext(), getString(R.string.settings_saved_dialog_title), Toast.LENGTH_SHORT).show()
        showSaveConfirmation()
    }
    
    private fun showSaveConfirmation() {
        val interval = bannerIntervalValues[spBannersPerDay.selectedItemPosition]
        
        val summary = StringBuilder().apply {
            append(getString(R.string.settings_saved_summary, 
                if (swDarkMode.isChecked) 
                    getString(R.string.settings_theme_dark) 
                else 
                    getString(R.string.settings_theme_light),
                intervalLabels[spBannersPerDay.selectedItemPosition],
                if (swAutoStart.isChecked) 
                    getString(R.string.dialog_yes) 
                else 
                    getString(R.string.dialog_no),
                if (swBatteryOptimization.isChecked) 
                    getString(R.string.dialog_yes) 
                else 
                    getString(R.string.dialog_no),
                if (swVibrateNotification.isChecked) 
                    getString(R.string.dialog_yes) 
                else 
                    getString(R.string.dialog_no),
                if (swSoundNotification.isChecked) 
                    getString(R.string.dialog_yes) 
                else 
                    getString(R.string.dialog_no),
                if (swExportDailyReports.isChecked) 
                    getString(R.string.dialog_yes) 
                else 
                    getString(R.string.dialog_no)
            ))
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_saved_dialog_title))
            .setMessage(summary.toString())
            .setPositiveButton(getString(R.string.dialog_yes), null)
            .show()
    }
    
    private fun resetSettings() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_reset_dialog_title))
            .setMessage(getString(R.string.settings_reset_dialog_message))
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                performReset()
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
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
        
        tvBannersPerDayValue.text = intervalLabels[4]
        
        viewModel.clearAllSettings()
        
        swDarkMode.setOnCheckedChangeListener { _, isChecked ->
            tvThemeStatus.text = if (isChecked) 
                getString(R.string.settings_theme_dark) 
            else 
                getString(R.string.settings_theme_light)
            viewModel.updateDarkMode(isChecked)
            applyThemeChange(isChecked)
        }
        
        applyThemeChange(false)
        
        Toast.makeText(requireContext(), getString(R.string.settings_reset), Toast.LENGTH_SHORT).show()
        tvSettingsStatus.text = getString(R.string.settings_reset)
    }
}