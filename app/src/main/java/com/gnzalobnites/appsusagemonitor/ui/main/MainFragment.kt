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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.databinding.FragmentMainBinding
import com.gnzalobnites.appsusagemonitor.service.MonitoringService
import com.gnzalobnites.appsusagemonitor.utils.AccessibilityHelper
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.R as MaterialR
import java.util.*

class MainFragment : Fragment() {
    
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels()
    
    private val chartColors by lazy { ColorTemplate.MATERIAL_COLORS.toMutableList().apply { shuffle() } }
    
    // Handler para el observer de cambio de día
    private val dayChangeHandler = Handler(Looper.getMainLooper())
    
    // Variable para tracking del estado anterior del servicio
    private var lastServiceState = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        setupObservers()
        setupButtons()
        setupFooter()
        
        // Verificar estado inicial del servicio de accesibilidad
        viewModel.checkAccessibilityServiceState()
        
        // Cargar estadísticas iniciales
        viewModel.loadTodayStats()
        
        // Detectar cambio de día y recargar datos automáticamente
        observeDayChange()
        
        // CORREGIDO: Observer para actualizar el botón automáticamente
        viewModel.isAccessibilityServiceEnabled.observe(viewLifecycleOwner) { isEnabled ->
            updateServiceButtonState(isEnabled)
        }
    }

    private fun setupChart() {
        val textColor = getThemeColor(MaterialR.attr.colorOnSurface)
        
        binding.chartUsage.apply {
            description.isEnabled = false
            setUsePercentValues(false)
            setDrawEntryLabels(true)
            holeRadius = 52f
            transparentCircleRadius = 57f
            setHoleColor(Color.TRANSPARENT)
            animateY(1000)
            setEntryLabelColor(textColor)
            setEntryLabelTextSize(12f)
            centerText = getString(R.string.today)
            setCenterTextSize(18f)
            setCenterTextColor(textColor)
            
            legend.isEnabled = false
        }
    }

    private fun setupObservers() {
        viewModel.usageStats.observe(viewLifecycleOwner) { statsMap: Map<String, Long> ->
            updateChartDisplay(statsMap)
        }
        
        viewModel.monitoredApps.observe(viewLifecycleOwner) { monitoredList ->
            if (viewModel.usageStats.value.isNullOrEmpty()) {
                if (monitoredList.isEmpty()) {
                    binding.tvTotalTime.text = getString(R.string.empty_state_no_apps)
                } else {
                    binding.tvTotalTime.text = getString(R.string.no_usage_today)
                }
            }
        }
        
        // Mantenemos el observer anterior para compatibilidad
        viewModel.isServiceRunning.observe(viewLifecycleOwner) { isRunning ->
            // No hacemos nada aquí para evitar duplicación, usamos el nuevo observer
        }
    }
    
    private fun updateChartDisplay(statsMap: Map<String, Long>) {
        if (statsMap.isEmpty()) {
            binding.chartUsage.visibility = View.GONE
            binding.tvTotalTime.visibility = View.VISIBLE
            binding.tvTotalTime.text = getString(R.string.no_monitored_apps_data_today)
            return
        }
        
        val entries = ArrayList<PieEntry>()
        var totalMs = 0L

        statsMap.filter { it.value > 60000 }.forEach { (name, time) ->
            entries.add(PieEntry(time.toFloat(), name))
            totalMs += time
        }

        if (entries.isEmpty() && statsMap.isNotEmpty()) {
            Toast.makeText(requireContext(), R.string.all_apps_less_than_minute, Toast.LENGTH_SHORT).show()
            statsMap.forEach { (name, time) ->
                entries.add(PieEntry(time.toFloat(), name))
                totalMs += time
            }
        }

        val textColor = getThemeColor(MaterialR.attr.colorOnSurface)
        
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = chartColors
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        
        dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return formatTime(value.toLong())
            }
        }
        dataSet.valueTextSize = 12f
        dataSet.setValueTextColor(Color.WHITE)
        
        if (entries.isNotEmpty()) {
            val pieData = PieData(dataSet)
            pieData.setValueTextSize(12f)
            pieData.setValueTextColor(Color.WHITE)
            binding.chartUsage.data = pieData
            
            binding.chartUsage.setCenterText(getString(R.string.today))
            binding.chartUsage.setCenterTextColor(textColor)
        } else {
            binding.chartUsage.data = null
            binding.chartUsage.setCenterText(getString(R.string.no_data))
            binding.chartUsage.setCenterTextColor(textColor)
        }
        
        binding.chartUsage.invalidate()
        
        binding.tvTotalTime.text = String.format(getString(R.string.total_time_format), formatTime(totalMs))
    }

    private fun setupButtons() {
        binding.btnSelectApps.setOnClickListener {
            findNavController().navigate(R.id.appSelectionFragment)
        }

        binding.btnServiceControl.setOnClickListener {
            val isEnabled = viewModel.isAccessibilityServiceEnabled.value ?: false
            
            if (isEnabled) {
                // Si está habilitado, mostramos diálogo para desactivar
                showAccessibilityDisableDialog()
            } else {
                // Si no está habilitado, verificamos permiso de uso de datos primero
                if (!hasUsageStatsPermission()) {
                    Toast.makeText(
                        requireContext(), 
                        R.string.permission_usage_stats_required, 
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // Guiamos al usuario a activar el servicio de accesibilidad
                    showAccessibilityEnableDialog()
                }
            }
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

    private fun setupFooter() {
        val versionText = try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            getString(R.string.version_format, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            getString(R.string.version_format_placeholder)
        }
        binding.tvVersionFooter.text = versionText

        binding.btnBuyCoffeeFooter.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/gnzbenitesh"))
            startActivity(intent)
        }
        
        binding.tvEmailFooter.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${getString(R.string.developer_email)}")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject_suggestions))
            }
            
            try {
                startActivity(emailIntent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.error_no_email_app, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // CORREGIDO: Actualiza el botón según el estado real y muestra toast solo cuando cambia de OFF a ON
    private fun updateServiceButtonState(isEnabled: Boolean) {
        // Mostrar toast solo cuando cambia de desactivado a activado
        if (isEnabled && !lastServiceState) {
            Toast.makeText(requireContext(), R.string.service_running, Toast.LENGTH_SHORT).show()
        }
        
        // Actualizar el estado anterior
        lastServiceState = isEnabled
        
        // Actualizar UI del botón
        if (isEnabled) {
            binding.btnServiceControl.text = getString(R.string.stop_monitoring)
            binding.btnServiceControl.backgroundTintList = 
                ColorStateList.valueOf(Color.parseColor("#E57373"))
        } else {
            binding.btnServiceControl.text = getString(R.string.start_monitoring)
            binding.btnServiceControl.backgroundTintList = 
                ColorStateList.valueOf(getThemeColor(MaterialR.attr.colorPrimary))
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        return AccessibilityHelper.isAccessibilityServiceEnabled(requireContext(), serviceClass)
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            requireContext().packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun formatTime(millis: Long): String {
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000
        
        return when {
            hours > 0 -> String.format(getString(R.string.hours_minutes_format), hours, minutes)
            minutes > 0 -> String.format(getString(R.string.minutes_seconds_format), minutes, seconds)
            else -> String.format(getString(R.string.seconds_format), seconds)
        }
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    /**
     * Observa el cambio de día y recarga los datos automáticamente a medianoche
     */
    private fun observeDayChange() {
        val calendar = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val timeUntilMidnight = midnight.timeInMillis - System.currentTimeMillis()
        
        dayChangeHandler.postDelayed({
            // Recargar datos a la medianoche
            viewModel.loadTodayStats()
            // Repetir el observer para el próximo día
            observeDayChange()
        }, timeUntilMidnight)
    }

    override fun onResume() {
        super.onResume()
        // CORREGIDO: Verificación directa del estado del servicio
        val enabled = AccessibilityHelper.isAccessibilityServiceEnabled(
            requireContext(),
            MonitoringService::class.java
        )
        // Actualizamos el ViewModel y la UI
        viewModel.checkAccessibilityServiceState()
        viewModel.loadTodayStats()
        // Forzamos actualización de UI por si el LiveData no ha cambiado
        updateServiceButtonState(enabled)
    }

    override fun onPause() {
        super.onPause()
        // Limpiar callbacks pendientes para evitar memory leaks
        dayChangeHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dayChangeHandler.removeCallbacksAndMessages(null)
        _binding = null
    }
}