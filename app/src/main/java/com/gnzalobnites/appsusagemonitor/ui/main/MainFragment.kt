package com.gnzalobnites.appsusagemonitor.ui.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.databinding.FragmentMainBinding
import com.gnzalobnites.appsusagemonitor.service.BubbleService
import com.gnzalobnites.appsusagemonitor.service.MonitoringService
import com.gnzalobnites.appsusagemonitor.utils.AccessibilityHelper
import com.gnzalobnites.appsusagemonitor.utils.Constants
import com.gnzalobnites.appsusagemonitor.utils.TimeFormatter
import com.google.android.material.R as MaterialR
import kotlinx.coroutines.launch
import java.util.*

class MainFragment : Fragment() {
    
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels()
    
    // Handler con referencias manejadas correctamente
    private val dayChangeHandler = Handler(Looper.getMainLooper())
    private val previewHandler = Handler(Looper.getMainLooper())
    
    // Runnables con ciclo de vida controlado
    private var dayChangeRunnable: Runnable? = null
    private var previewHideRunnable: Runnable? = null
    private var lastServiceState = false
    private var isPreviewActive = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupButtons()
        setupControls()
        
        viewModel.checkAccessibilityServiceState()
        viewModel.loadTodayStats()
        viewModel.loadMonitoredAppsCount()
        observeDayChange()
        
        // ✅ CORRECCIÓN: Usar viewLifecycleOwner.lifecycleScope en lugar de lifecycleScope
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isAccessibilityServiceEnabled.collect { isEnabled ->
                updateServiceButtonState(isEnabled)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.monitoredAppsCount.collect { count ->
                binding.monitoredAppsCount.text = count.toString()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentInterval.collect { interval ->
                binding.currentIntervalValue.text = formatInterval(interval)
            }
        }
    }

    private fun setupObservers() {
        // ✅ CORRECCIÓN: Usar viewLifecycleOwner.lifecycleScope en lugar de lifecycleScope
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalScreenTime.collect { totalMs ->
                if (totalMs > 0) {
                    // ✅ CORRECCIÓN: Se reemplaza formatTime por formatDuration para unificar el formato
                    binding.todayScreenTimeValue.text = TimeFormatter.formatDuration(totalMs)
                } else {
                    // Mantenemos "0m" para consistencia con el comportamiento anterior
                    binding.todayScreenTimeValue.text = "0m"
                }
            }
        }

        viewModel.preferences.observe(viewLifecycleOwner) { prefs ->
            prefs?.let {
                binding.bubbleSizeSeekbar.progress = it.bubbleSize
                binding.bubbleOpacitySeekbar.progress = it.bubbleOpacity
                binding.switchHapticFeedback.isChecked = it.hapticFeedback
                binding.switchAutoHide.isChecked = it.autoHide
                binding.switchShowScreenTime.isChecked = it.showScreenTime
                binding.switchSoundNotifications.isChecked = it.soundNotifications
            }
        }
    }

    private fun setupButtons() {
        binding.btnSelectApps.setOnClickListener {
            findNavController().navigate(R.id.appSelectionFragment)
        }

        binding.btnServiceControl.setOnClickListener {
            val isEnabled = viewModel.isAccessibilityServiceEnabled.value
            
            if (isEnabled) {
                showAccessibilityDisableDialog()
            } else {
                if (!hasUsageStatsPermission()) {
                    Toast.makeText(
                        requireContext(), 
                        R.string.permission_usage_stats_required, 
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    showAccessibilityEnableDialog()
                }
            }
        }
    }

    private fun setupControls() {
        // Configurar SeekBars con correcciones
        binding.bubbleSizeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.setBubbleSize(progress)
                    // updateBubblePreview(size = progress) // Deshabilitado
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                startBubblePreview()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Cancelar Runnable anterior antes de crear uno nuevo
                previewHideRunnable?.let { previewHandler.removeCallbacks(it) }
                previewHideRunnable = Runnable { hideBubblePreview() }
                previewHandler.postDelayed(previewHideRunnable!!, 1500)
            }
        })

        binding.bubbleOpacitySeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.setBubbleOpacity(progress)
                    // updateBubblePreview(opacity = progress) // Deshabilitado
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                startBubblePreview()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Cancelar Runnable anterior antes de crear uno nuevo
                previewHideRunnable?.let { previewHandler.removeCallbacks(it) }
                previewHideRunnable = Runnable { hideBubblePreview() }
                previewHandler.postDelayed(previewHideRunnable!!, 1500)
            }
        })

        // Configurar Switches
        binding.switchHapticFeedback.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setHapticFeedback(isChecked)
        }

        binding.switchAutoHide.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoHide(isChecked)
        }

        binding.switchShowScreenTime.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setShowScreenTime(isChecked)
        }

        binding.switchSoundNotifications.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSoundNotifications(isChecked)
        }
    }

    /**
     * Inicia la burbuja en modo previsualización
     * Ahora envía tamaño y opacidad inmediatamente para evitar el salto
     */
    private fun startBubblePreview() {
        // Cancelar cualquier orden previa de ocultamiento
        previewHandler.removeCallbacksAndMessages(null)
        
        if (isPreviewActive) return
        isPreviewActive = true
        
        // ✅ CORRECCIÓN: Verificar que el Fragment está adjunto
        if (!isAdded || context == null) {
            isPreviewActive = false
            return
        }
        
        val ctx = requireContext()
        
        try {
            val intent = Intent(ctx, BubbleService::class.java).apply {
                action = Constants.ACTION_SHOW_BUBBLE
                putExtra(Constants.EXTRA_PACKAGE_NAME, ctx.packageName)
                putExtra(Constants.EXTRA_SESSION_START_TIME, System.currentTimeMillis())
                putExtra(Constants.EXTRA_TIME_GOAL_MINUTES, 5) // Meta de 5 minutos para preview
                putExtra(Constants.EXTRA_BUBBLE_PERSISTENT, true)
                putExtra("is_preview", true) // Flag para identificar que es preview
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            
            
            Log.d("MainFragment", "Bubble preview started")
        } catch (e: Exception) {
            Log.e("MainFragment", "Error starting bubble preview", e)
            isPreviewActive = false
        }
    }

    /**
     * Actualiza la previsualización de la burbuja en tiempo real
     */
    private fun updateBubblePreview(size: Int? = null, opacity: Int? = null) {
        // ✅ CORRECCIÓN: Verificar que el Fragment está adjunto
        if (!isAdded || context == null) {
            return
        }
        
        try {
            val intent = Intent(requireContext(), BubbleService::class.java).apply {
                // action = Constants.ACTION_UPDATE_PREVIEW // Eliminado - ya no se usa
                // size?.let { putExtra(Constants.EXTRA_PREVIEW_SIZE, it) } // Eliminado
                // opacity?.let { putExtra(Constants.EXTRA_PREVIEW_OPACITY, it) } // Eliminado
            }
            requireContext().startService(intent)
            Log.d("MainFragment", "Bubble preview updated: size=$size, opacity=$opacity")
        } catch (e: Exception) {
            Log.e("MainFragment", "Error updating bubble preview", e)
        }
    }

    /**
     * Oculta la burbuja de previsualización
     */
    private fun hideBubblePreview() {
        if (!isPreviewActive) return
        isPreviewActive = false
        
        // ✅ CORRECCIÓN: Verificar que el Fragment está adjunto
        if (!isAdded || context == null) {
            return
        }
        
        try {
            val intent = Intent(requireContext(), BubbleService::class.java).apply {
                action = Constants.ACTION_HIDE_BUBBLE
            }
            requireContext().startService(intent)
            Log.d("MainFragment", "Bubble preview hidden")
        } catch (e: Exception) {
            Log.e("MainFragment", "Error hiding bubble preview", e)
        }
    }

    private fun showAccessibilityEnableDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.accessibility_permission_title)
            .setMessage(R.string.accessibility_permission_message)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                viewModel.requestAccessibilityPermission()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showAccessibilityDisableDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.stop_monitoring_title)
            .setMessage(R.string.stop_monitoring_message)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                viewModel.requestAccessibilityPermission()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Actualiza el estado del botón de servicio con verificación de ciclo de vida
     */
    private fun updateServiceButtonState(isEnabled: Boolean) {
        // ✅ CORRECCIÓN: Verificar que el Fragment está adjunto
        if (!isAdded || context == null) {
            return
        }
        
        val ctx = requireContext()
        
        if (isEnabled && !lastServiceState) {
            Toast.makeText(ctx, R.string.service_running, Toast.LENGTH_SHORT).show()
        }
        
        lastServiceState = isEnabled
        
        if (isEnabled) {
            binding.serviceStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_active)
            binding.serviceStatusText.text = getString(R.string.service_status_active)
            binding.serviceStatusText.setTextColor(ContextCompat.getColor(ctx, R.color.green_500))
            binding.btnServiceControl.text = getString(R.string.stop_monitoring)
            binding.btnServiceControl.backgroundTintList = 
                ColorStateList.valueOf(Color.parseColor("#E57373"))
        } else {
            binding.serviceStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_inactive)
            binding.serviceStatusText.text = getString(R.string.service_status_inactive)
            binding.serviceStatusText.setTextColor(ContextCompat.getColor(ctx, android.R.color.darker_gray))
            binding.btnServiceControl.text = getString(R.string.start_monitoring)
            binding.btnServiceControl.backgroundTintList = 
                ColorStateList.valueOf(getThemeColor(MaterialR.attr.colorPrimary))
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                requireContext().packageName
            ) == android.app.AppOpsManager.MODE_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                requireContext().packageName
            ) == android.app.AppOpsManager.MODE_ALLOWED
        }
    }

    private fun formatInterval(interval: Long): String {
        return when (interval) {
            10000L -> "10s"
            60000L -> "1 min"
            300000L -> "5 min"
            900000L -> "15 min"
            1800000L -> "30 min"
            3600000L -> "1 h"
            else -> "Custom"
        }
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    /**
     * Observa el cambio de día con manejo adecuado de ciclo de vida
     */
    private fun observeDayChange() {
        // Usar un Runnable que se pueda cancelar limpiamente
        dayChangeRunnable = Runnable {
            // Verificar que el fragmento sigue activo
            if (isAdded && !isDetached) {
                viewModel.loadTodayStats()
                observeDayChange()
            }
        }
        
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val timeUntilMidnight = midnight.timeInMillis - System.currentTimeMillis()
        
        // Postear el Runnable con verificación de ciclo de vida
        dayChangeRunnable?.let { 
            dayChangeHandler.postDelayed(it, timeUntilMidnight)
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = AccessibilityHelper.isAccessibilityServiceEnabled(
            requireContext(),
            MonitoringService::class.java
        )
        viewModel.checkAccessibilityServiceState()
        viewModel.loadTodayStats()
        viewModel.loadMonitoredAppsCount()
        updateServiceButtonState(enabled)
    }
    
    override fun onPause() {
        super.onPause()
        // Cancelar TODOS los callbacks y mensajes
        dayChangeHandler.removeCallbacksAndMessages(null)
        previewHandler.removeCallbacksAndMessages(null)
        
        // Limpiar referencias a Runnables
        dayChangeRunnable = null
        previewHideRunnable = null
        
        hideBubblePreview()
    }

    override fun onStop() {
        super.onStop()
        // Cancelar handlers también en onStop para mayor seguridad
        dayChangeHandler.removeCallbacksAndMessages(null)
        previewHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Asegurar limpieza completa
        dayChangeHandler.removeCallbacksAndMessages(null)
        previewHandler.removeCallbacksAndMessages(null)
        
        dayChangeRunnable = null
        previewHideRunnable = null
        
        hideBubblePreview()
        _binding = null
    }
}