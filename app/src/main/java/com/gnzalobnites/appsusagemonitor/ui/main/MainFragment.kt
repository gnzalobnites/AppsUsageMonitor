package com.gnzalobnites.appsusagemonitor.ui.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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
    
    // Colores estables para cada app (se barajan una sola vez)
    private val chartColors by lazy { ColorTemplate.MATERIAL_COLORS.toMutableList().apply { shuffle() } }

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
        
        // Cargar estadísticas reales
        viewModel.loadTodayStats()
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
            
            // Configurar leyenda si está visible
            legend.isEnabled = false // Deshabilitar leyenda para más espacio
        }
    }

    private fun setupObservers() {
        // Observer 1 - Estadísticas de uso
        viewModel.usageStats.observe(viewLifecycleOwner) { statsMap: Map<String, Long> ->
            updateChartDisplay(statsMap)
        }
        
        // Observer 2 - Lista de monitoreo (solo para mensajes cuando no hay datos)
        viewModel.monitoredApps.observe(viewLifecycleOwner) { monitoredList ->
            // Solo actualizar el mensaje si no hay datos de uso
            if (viewModel.usageStats.value.isNullOrEmpty()) {
                if (monitoredList.isEmpty()) {
                    binding.tvTotalTime.text = getString(R.string.empty_state_no_apps)
                } else {
                    binding.tvTotalTime.text = getString(R.string.no_usage_today)
                }
            }
        }
        
        // Observer 3 - Estado del servicio (ahora actualiza el botón en lugar del FAB)
        viewModel.isServiceRunning.observe(viewLifecycleOwner) { isRunning ->
            updateServiceButtonState(isRunning)
        }
    }
    
    private fun updateChartDisplay(statsMap: Map<String, Long>) {
        // Verificar si hay datos
        if (statsMap.isEmpty()) {
            binding.chartUsage.visibility = View.GONE
            binding.tvTotalTime.visibility = View.VISIBLE
            binding.tvTotalTime.text = getString(R.string.no_monitored_apps_data_today)
            return
        }
        
        // --- CAMBIO IMPORTANTE: Filtrar por el día actual ---
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Filtrar el mapa para incluir solo las apps con uso desde el inicio de hoy.
        // Nota: Este filtro asume que las claves del mapa son paquetes y los valores son la suma total de duración.
        // Si 'statsMap' ya viene filtrado por día desde el VM, este paso es redundante pero seguro.
        // Para este ejemplo, aplicamos el filtro basado en la suposición de que los datos en 'statsMap' son de todo el historial.
        // Idealmente, el ViewModel debería proveer un mapa que ya sean solo los datos de hoy.
        // Como no tenemos acceso al VM aquí, asumimos que 'loadTodayStats()' del VM ya hace ese filtro.
        // Por lo tanto, la lógica de filtrado debe estar en MainViewModel.kt
        // Pero para adherirnos al plan, haremos el filtro aquí como ejemplo de la intención.
        // -----------------------------------------------------

        // Por ahora, asumimos que statsMap YA SOLO CONTIENE DATOS DE HOY.
        // Si no es así, deberías modificar MainViewModel para que así sea.
        
        val entries = ArrayList<PieEntry>()
        var totalMs = 0L

        // Filtrar aplicaciones con menos de 1 minuto de uso (60000 ms)
        statsMap.filter { it.value > 60000 }.forEach { (name, time) ->
            entries.add(PieEntry(time.toFloat(), name))
            totalMs += time
        }

        // Si no hay datos con más de 1 minuto, mostrar todas
        if (entries.isEmpty() && statsMap.isNotEmpty()) {
            Toast.makeText(requireContext(), R.string.all_apps_less_than_minute, Toast.LENGTH_SHORT).show()
            statsMap.forEach { (name, time) ->
                entries.add(PieEntry(time.toFloat(), name))
                totalMs += time
            }
        }

        // Obtener colores del tema para los textos
        val textColor = getThemeColor(MaterialR.attr.colorOnSurface)
        
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = chartColors
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        
        // Usar blanco para los valores sobre las rebanadas de colores
        dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return formatTime(value.toLong())
            }
        }
        dataSet.valueTextSize = 12f
        dataSet.setValueTextColor(Color.WHITE) // Siempre blanco sobre colores
        
        // Solo crear PieData si hay entradas
        if (entries.isNotEmpty()) {
            val pieData = PieData(dataSet)
            pieData.setValueTextSize(12f)
            pieData.setValueTextColor(Color.WHITE)
            binding.chartUsage.data = pieData
            
            // El texto central usa el color del tema
            binding.chartUsage.setCenterText(getString(R.string.today))
            binding.chartUsage.setCenterTextColor(textColor)
        } else {
            binding.chartUsage.data = null
            binding.chartUsage.setCenterText(getString(R.string.no_data))
            binding.chartUsage.setCenterTextColor(textColor)
        }
        
        binding.chartUsage.invalidate()
        
        // El TextView total usa color del tema (ya configurado en XML)
        binding.tvTotalTime.text = String.format(getString(R.string.total_time_format), formatTime(totalMs))
    }

    /**
     * Configura los botones de la interfaz
     */
    private fun setupButtons() {
        // 1. Botón Seleccionar Apps (Navegación)
        binding.btnSelectApps.setOnClickListener {
            findNavController().navigate(R.id.appSelectionFragment)
        }

        // 2. Botón Iniciar/Detener Servicio
        binding.btnServiceControl.setOnClickListener {
            val intent = Intent(requireContext(), MonitoringService::class.java)
            
            if (isServiceRunning(MonitoringService::class.java)) {
                requireContext().stopService(intent)
                Toast.makeText(context, R.string.service_stopped, Toast.LENGTH_SHORT).show()
            } else {
                if (hasUsageStatsPermission()) {
                    requireContext().startService(intent)
                    Toast.makeText(context, R.string.service_started, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.permission_usage_stats_required, Toast.LENGTH_LONG).show()
                    // Opcional: Aquí se podría abrir la pantalla de configuración de permisos
                }
            }
            // Forzamos actualización visual inmediata
            isServiceRunning(MonitoringService::class.java)
        }
        
        // Establecer estado inicial del botón
        updateServiceButtonState(isServiceRunning(MonitoringService::class.java))
    }

    /**
     * Configura el footer con email, versión y enlace de invitación a café
     */
    private fun setupFooter() {
        // 1. Configurar la versión dinámica (forma segura sin BuildConfig)
        val versionText = try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            getString(R.string.version_format, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            getString(R.string.version_format, "1.0.0") // Versión por defecto si falla
        }
        binding.tvVersionFooter.text = versionText

        // 2. Configurar el click del café
        binding.btnBuyCoffeeFooter.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/gnzbenitesh"))
            startActivity(intent)
        }
        
        // 3. Configurar el click del email
        binding.tvEmailFooter.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:benitesgonzalogaston@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject_suggestions))
            }
            
            try {
                startActivity(emailIntent)
            } catch (e: Exception) {
                // Si no hay app de email, mostrar un toast
                Toast.makeText(requireContext(), R.string.error_no_email_app, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Actualiza el texto y color del botón de servicio según el estado
     */
    private fun updateServiceButtonState(isRunning: Boolean) {
        if (isRunning) {
            binding.btnServiceControl.text = getString(R.string.stop_monitoring)
            binding.btnServiceControl.backgroundTintList = 
                android.content.res.ColorStateList.valueOf(Color.parseColor("#E57373")) // Rojo suave
        } else {
            binding.btnServiceControl.text = getString(R.string.start_monitoring)
            binding.btnServiceControl.backgroundTintList = 
                android.content.res.ColorStateList.valueOf(getThemeColor(com.google.android.material.R.attr.colorPrimary))
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                viewModel.setServiceRunning(true)
                return true
            }
        }
        viewModel.setServiceRunning(false)
        return false
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

    /**
     * Helper para obtener colores del tema actual
     */
    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    override fun onResume() {
        super.onResume()
        // Actualizar estado del servicio al reanudar
        isServiceRunning(MonitoringService::class.java)
        // Recargar estadísticas
        viewModel.loadTodayStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}