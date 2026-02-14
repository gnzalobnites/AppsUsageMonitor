package com.gnzalobnites.appsusagemonitor.fragments

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.gnzalobnites.appsusagemonitor.MainViewModel
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.UsageRepository
import com.gnzalobnites.appsusagemonitor.AppDatabase
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.coroutines.*

class StatsFragment : Fragment() {

    private val TAG = "StatsFragment"
    private lateinit var viewModel: MainViewModel
    private lateinit var repository: UsageRepository
    private lateinit var database: AppDatabase

    // UI Elements
    private lateinit var tvTotalTime: TextView
    private lateinit var tvMonitoredAppCount: TextView
    private lateinit var tvTimeRangeTitle: TextView
    private lateinit var btnRefreshStats: Button
    private lateinit var btnExportStats: Button
    private lateinit var btnViewDetails: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var pieChart: PieChart
    private lateinit var layoutAppLegend: LinearLayout
    
    // Job para corrutinas
    private var loadJob: Job? = null
    
    // Data class para uso de apps
    data class AppUsageData(
        val packageName: String,
        val appName: String,
        val totalTime: Long,
        var percentage: Float = 0f,
        val sessionCount: Int = 0
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_stats_enhanced, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "📊 StatsFragment - Inicializando")
        
        try {
            // Inicializar ViewModel
            viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
            
            // Inicializar Database y Repository
            database = AppDatabase.getDatabase(requireContext())
            repository = UsageRepository.getInstance(database)

            // Inicializar vistas
            initViews(view)
            
            // Configurar gráfico
            setupPieChart()
            
            // Configurar listeners
            setupListeners()
            
            // Cargar datos iniciales
            loadStats()
            
            Log.d(TAG, "✅ StatsFragment configurado correctamente")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en onViewCreated: ${e.message}", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun initViews(view: View) {
        tvTotalTime = view.findViewById(R.id.tvTotalTime)
        tvMonitoredAppCount = view.findViewById(R.id.tvMonitoredAppCount)
        tvTimeRangeTitle = view.findViewById(R.id.tvTimeRangeTitle)
        btnRefreshStats = view.findViewById(R.id.btnRefreshStats)
        btnExportStats = view.findViewById(R.id.btnExportStats)
        btnViewDetails = view.findViewById(R.id.btnViewDetails)
        progressBar = view.findViewById(R.id.progressBar)
        pieChart = view.findViewById(R.id.pieChart)
        layoutAppLegend = view.findViewById(R.id.layoutAppLegend)
        
        // Configurar texto inicial
        tvTimeRangeTitle.text = "📅 Estadísticas - Hoy"
        tvTotalTime.text = "0m"
        tvMonitoredAppCount.text = "0"
        progressBar.visibility = View.GONE
    }
    
    private fun setupPieChart() {
        try {
            pieChart.setUsePercentValues(true)
            pieChart.description.isEnabled = false
            pieChart.legend.isEnabled = false
            pieChart.isDrawHoleEnabled = true
            pieChart.setHoleColor(Color.TRANSPARENT)
            pieChart.setTransparentCircleColor(Color.WHITE)
            pieChart.setTransparentCircleAlpha(110)
            pieChart.holeRadius = 58f
            pieChart.transparentCircleRadius = 61f
            pieChart.setDrawCenterText(true)
            pieChart.setCenterTextSize(14f)
            pieChart.setEntryLabelColor(Color.BLACK)
            pieChart.setEntryLabelTextSize(12f)
            pieChart.setCenterText("Sin datos")
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando pieChart: ${e.message}")
        }
    }
    
    private fun setupListeners() {
        btnRefreshStats.setOnClickListener { loadStats() }
        
        btnExportStats.setOnClickListener { 
            showExportDialog()
        }
        
        btnViewDetails.setOnClickListener {
            showDetailsDialog()
        }
    }
    
    private fun loadStats() {
        Log.d(TAG, "loadStats - Cargando estadísticas...")
        
        // Cancelar job anterior
        loadJob?.cancel()
        
        // Mostrar loading
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        tvTotalTime.text = "Calculando..."
        pieChart.clear()
        pieChart.setCenterText("Cargando...")
        layoutAppLegend.removeAllViews()
        layoutAppLegend.visibility = View.GONE

        val monitoredPackages = viewModel.monitoredApps.value ?: emptyList()
        Log.d(TAG, "Apps monitoreadas: ${monitoredPackages.size}")
        
        if (monitoredPackages.isEmpty()) {
            showNoDataMessage("No hay apps monitoreadas")
            return
        }

        tvMonitoredAppCount.text = monitoredPackages.size.toString()

        // Iniciar carga en segundo plano
        loadJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // Actualizar barra de progreso
                progressBar.progress = 30
                
                // Cargar datos en segundo plano
                val (usageData, totalTime) = withContext(Dispatchers.IO) {
                    loadStatsFromDatabase(monitoredPackages)
                }
                
                progressBar.progress = 80
                
                // Actualizar UI en hilo principal
                if (usageData.isEmpty()) {
                    showNoDataMessage("No hay datos de uso hoy")
                } else {
                    tvTotalTime.text = formatTime(totalTime)
                    showPieChart(usageData, totalTime)
                    showLegend(usageData)
                }
                
                progressBar.progress = 100
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en loadStats: ${e.message}", e)
                showNoDataMessage("Error al cargar datos")
            } finally {
                // Ocultar progreso después de un momento
                delay(500)
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private suspend fun loadStatsFromDatabase(monitoredPackages: List<String>): Pair<List<AppUsageData>, Long> {
        val usageData = mutableListOf<AppUsageData>()
        var totalTime = 0L
        
        Log.d(TAG, "Cargando estadísticas para ${monitoredPackages.size} apps")
        
        for (packageName in monitoredPackages) {
            try {
                // Obtener tiempo total de hoy para esta app
                val time = repository.getAppTimeToday(packageName)
                
                // Obtener conteo de sesiones (opcional)
                val sessions = repository.getTodaySessions()
                val sessionCount = sessions.count { it.packageName == packageName }
                
                if (time > 0) {
                    totalTime += time
                    val appName = getAppName(packageName)
                    usageData.add(AppUsageData(
                        packageName = packageName,
                        appName = appName,
                        totalTime = time,
                        sessionCount = sessionCount
                    ))
                    Log.d(TAG, "  • $appName: ${formatTime(time)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error con $packageName: ${e.message}")
            }
        }
        
        // Ordenar por tiempo (mayor a menor)
        usageData.sortByDescending { it.totalTime }
        
        // Calcular porcentajes
        if (totalTime > 0) {
            usageData.forEach { data ->
                data.percentage = (data.totalTime.toFloat() / totalTime) * 100
            }
        }
        
        Log.d(TAG, "Total tiempo hoy: ${formatTime(totalTime)}")
        
        return Pair(usageData, totalTime)
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val pm = requireContext().packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            // Si no se puede obtener el nombre, usar el packageName formateado
            packageName.substringAfterLast('.').capitalize()
        }
    }
    
    private fun showPieChart(data: List<AppUsageData>, totalTime: Long) {
        try {
            if (data.isEmpty()) {
                pieChart.setCenterText("Sin datos")
                pieChart.invalidate()
                return
            }
            
            // Crear entradas para el gráfico
            val entries = data.mapIndexed { index, appData ->
                PieEntry(appData.percentage, index)
            }
            
            // Colores para el gráfico
            val colors = getChartColors(data.size)
            
            // Configurar dataset
            val dataSet = PieDataSet(entries, "").apply {
                this.colors = colors
                valueTextSize = 12f
                valueFormatter = PercentFormatter(pieChart)
                setValueTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary_light))
                sliceSpace = 2f
                selectionShift = 5f
            }
            
            // Configurar datos
            val pieData = PieData(dataSet).apply {
                setValueFormatter(PercentFormatter(pieChart))
            }
            
            // Texto central
            val totalTimeFormatted = formatTimeShort(totalTime)
            pieChart.setCenterText("$totalTimeFormatted\nTotal")
            
            // Aplicar y actualizar
            pieChart.data = pieData
            pieChart.invalidate() // Refrescar gráfico
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en showPieChart: ${e.message}")
        }
    }
    
    private fun showLegend(data: List<AppUsageData>) {
        try {
            layoutAppLegend.removeAllViews()
            layoutAppLegend.visibility = View.VISIBLE
            
            val colors = getChartColors(data.size)
            
            data.forEachIndexed { index, appData ->
                val itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_stat_legend, layoutAppLegend, false)
                
                val colorView = itemView.findViewById<View>(R.id.viewColor)
                val tvAppName = itemView.findViewById<TextView>(R.id.tvLegendAppName)
                val tvAppTime = itemView.findViewById<TextView>(R.id.tvLegendAppTime)
                val tvAppPercentage = itemView.findViewById<TextView>(R.id.tvLegendAppPercentage)
                
                // Limitar nombre de app a 20 caracteres
                val displayName = if (appData.appName.length > 20) {
                    appData.appName.take(18) + "..."
                } else {
                    appData.appName
                }
                
                colorView.setBackgroundColor(colors[index % colors.size])
                tvAppName.text = displayName
                tvAppTime.text = formatTimeShort(appData.totalTime)
                tvAppPercentage.text = String.format("%.1f%%", appData.percentage)
                
                layoutAppLegend.addView(itemView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en showLegend: ${e.message}")
        }
    }
    
    private fun getChartColors(count: Int): List<Int> {
        val baseColors = ColorTemplate.MATERIAL_COLORS.toList() + 
                         ColorTemplate.VORDIPLOM_COLORS.toList() +
                         listOf(
                            Color.rgb(193, 37, 82),
                            Color.rgb(255, 102, 0),
                            Color.rgb(245, 199, 0),
                            Color.rgb(106, 150, 31),
                            Color.rgb(179, 100, 53)
                         )
        
        return (0 until count).map { index ->
            baseColors[index % baseColors.size]
        }
    }
    
    private fun showNoDataMessage(message: String) {
        tvTotalTime.text = "0m"
        tvMonitoredAppCount.text = (viewModel.monitoredApps.value?.size ?: 0).toString()
        
        pieChart.clear()
        pieChart.setCenterText(message)
        pieChart.invalidate()
        
        layoutAppLegend.removeAllViews()
        layoutAppLegend.visibility = View.GONE
        progressBar.visibility = View.GONE
    }
    
    private fun formatTime(milliseconds: Long): String {
        if (milliseconds <= 0) return "0m"
        
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return when {
            hours > 0 -> String.format("%dh %02dm", hours, minutes)
            minutes > 0 -> String.format("%dm", minutes)
            else -> String.format("%ds", seconds)
        }
    }
    
    private fun formatTimeShort(milliseconds: Long): String {
        if (milliseconds <= 0) return "0m"
        
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        
        return when {
            hours > 0 -> String.format("%dh %02dm", hours, minutes)
            minutes > 0 -> String.format("%dm", minutes)
            else -> "< 1m"
        }
    }
    
    private fun showExportDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("📤 Exportar Estadísticas")
            .setMessage("¿Qué formato deseas exportar?")
            .setPositiveButton("CSV") { _, _ ->
                Toast.makeText(requireContext(), "Exportando a CSV...", Toast.LENGTH_SHORT).show()
                // Aquí iría la lógica real de exportación
            }
            .setNeutralButton("JSON") { _, _ ->
                Toast.makeText(requireContext(), "Exportando a JSON...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun showDetailsDialog() {
        val monitoredApps = viewModel.monitoredApps.value ?: emptyList()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val stats = withContext(Dispatchers.IO) {
                    loadStatsFromDatabase(monitoredApps)
                }
                
                val (usageData, totalTime) = stats
                
                if (usageData.isEmpty()) {
                    Toast.makeText(requireContext(), "No hay datos para mostrar", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val message = StringBuilder().apply {
                    append("📊 DETALLE POR APP\n\n")
                    append("Total: ${formatTime(totalTime)}\n")
                    append("Apps activas: ${usageData.size}\n")
                    append("─".repeat(30) + "\n\n")
                    
                    usageData.take(10).forEachIndexed { index, data ->
                        append("${index + 1}. ${data.appName}\n")
                        append("   ⏱️ ${formatTime(data.totalTime)} (${String.format("%.1f", data.percentage)}%)\n")
                        append("   📊 Sesiones: ${data.sessionCount}\n\n")
                    }
                    
                    if (usageData.size > 10) {
                        append("... y ${usageData.size - 10} apps más")
                    }
                }
                
                AlertDialog.Builder(requireContext())
                    .setTitle("Detalles de Uso")
                    .setMessage(message.toString())
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Recargar") { _, _ ->
                        loadStats()
                    }
                    .show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error mostrando detalles: ${e.message}")
                Toast.makeText(requireContext(), "Error cargando detalles", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Recargar datos al volver al fragment
        loadStats()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
    }
}