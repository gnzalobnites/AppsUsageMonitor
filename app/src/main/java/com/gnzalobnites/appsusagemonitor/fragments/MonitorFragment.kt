package com.gnzalobnites.appsusagemonitor.fragments

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gnzalobnites.appsusagemonitor.*
import com.gnzalobnites.appsusagemonitor.banner.BannerManager
import java.util.*

class MonitorFragment : Fragment() {

    private val TAG = "MonitorFragment"
    private lateinit var viewModel: MainViewModel
    private lateinit var bannerManager: BannerManager
    
    // UI Elements
    private lateinit var swEnableBanners: Switch
    private lateinit var tvBannerStatus: TextView
    private lateinit var btnTestBanner: Button
    private lateinit var btnSelectApps: Button
    private lateinit var tvSelectedAppsCount: TextView
    private lateinit var recyclerViewApps: RecyclerView
    private lateinit var btnCheckPermissions: Button
    private lateinit var tvUsageStatsStatus: TextView
    private lateinit var swShowTikTokDemo: Switch
    private lateinit var tvTikTokDemoStatus: TextView
    
    // Para selección de apps
    private var apps: List<ApplicationInfo> = emptyList()
    private lateinit var appsAdapter: SimpleAppsAdapter
    
    // Para el diálogo de selección
    private var dialogAdapter: SimpleAppsAdapter? = null
    private var selectionDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_monitor_enhanced, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d(TAG, "onViewCreated - MonitorFragment")
        
        try {
            // Inicializar ViewModel
            viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
            
            // Inicializar BannerManager
            bannerManager = BannerManager(requireContext())
            val userPrefs = UserPreferences.getInstance(requireContext())
            val db = AppDatabase.getDatabase(requireContext())
            bannerManager.initialize(userPrefs, db)
            
            // Inicializar vistas
            initViews(view)
            
            // Configurar UI inicial
            setupUI()
            
            // Configurar listeners
            setupListeners()
            
            // Configurar observadores
            setupObservers()
            
            // Cargar apps instaladas
            loadInstalledApps()
            
            Log.d(TAG, "✅ MonitorFragment configurado")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en onViewCreated: ${e.message}", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun initViews(view: View) {
        swEnableBanners = view.findViewById(R.id.swEnableBanners)
        tvBannerStatus = view.findViewById(R.id.tvBannerStatus)
        btnTestBanner = view.findViewById(R.id.btnTestBanner)
        btnSelectApps = view.findViewById(R.id.btnSelectApps)
        tvSelectedAppsCount = view.findViewById(R.id.tvSelectedAppsCount)
        recyclerViewApps = view.findViewById(R.id.recyclerViewApps)
        btnCheckPermissions = view.findViewById(R.id.btnCheckPermissions)
        tvUsageStatsStatus = view.findViewById(R.id.tvUsageStatsStatus)
        swShowTikTokDemo = view.findViewById(R.id.swShowTikTokDemo)
        tvTikTokDemoStatus = view.findViewById(R.id.tvTikTokDemoStatus)
    }
    
    private fun setupUI() {
        // Cargar configuraciones actuales
        loadCurrentSettings()
        
        // Configurar RecyclerView
        setupRecyclerView()
        
        // Actualizar contador
        updateSelectedAppsCount()
        
        // Actualizar estado de UsageStats
        updateUsageStatsStatus()
    }
    
    private fun loadCurrentSettings() {
        val showBanner = viewModel.showBanner.value ?: false
        swEnableBanners.isChecked = showBanner
        tvBannerStatus.text = if (showBanner) "✅ Banners activados" else "⭕ Banners desactivados"
    }
    
    private fun setupRecyclerView() {
        recyclerViewApps.layoutManager = LinearLayoutManager(requireContext())
        val selectedApps = viewModel.monitoredApps.value?.toSet() ?: emptySet()
        appsAdapter = SimpleAppsAdapter(requireContext(), apps, selectedApps) { packageName, isChecked ->
            onAppChecked(packageName, isChecked)
        }
        recyclerViewApps.adapter = appsAdapter
    }
    
    private fun setupListeners() {
        swEnableBanners.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateShowBanner(isChecked)
            tvBannerStatus.text = if (isChecked) "✅ Banners activados" else "⭕ Banners desactivados"
            Log.d(TAG, "Banners ${if (isChecked) "activados" else "desactivados"}")
        }
        
        swShowTikTokDemo.setOnCheckedChangeListener { _, isChecked ->
            tvTikTokDemoStatus.text = if (isChecked) "✅ Demo activada" else "⭕ Demo desactivada"
            Log.d(TAG, "Demo TikTok ${if (isChecked) "activada" else "desactivada"}")
        }
        
        btnTestBanner.setOnClickListener {
            testBannerNow()
        }
        
        btnSelectApps.setOnClickListener {
            showAppSelectionDialog()
        }
        
        btnCheckPermissions.setOnClickListener {
            showPermissionsDialog()
        }
    }
    
    private fun setupObservers() {
        viewModel.monitoredApps.observe(viewLifecycleOwner, Observer { appsList ->
            appsList?.let {
                updateSelectedAppsCount()
                if (::appsAdapter.isInitialized) {
                    appsAdapter.updateSelectedApps(it.toSet())
                }
            }
        })
    }
    
    private fun updateUsageStatsStatus() {
        val hasPermission = bannerManager.hasUsageStatsPermission()
        tvUsageStatsStatus.text = if (hasPermission) {
            "✅ Modo preciso activado (recomendado)"
        } else {
            "⚠️ Modo preciso desactivado - Actívalo para mayor precisión"
        }
        
        if (hasPermission) {
            tvUsageStatsStatus.setTextColor(
                resources.getColor(android.R.color.holo_green_dark, requireContext().theme)
            )
        } else {
            tvUsageStatsStatus.setTextColor(
                resources.getColor(android.R.color.holo_orange_dark, requireContext().theme)
            )
        }
    }
    
    private fun showPermissionsDialog() {
        val overlayPerm = Settings.canDrawOverlays(requireContext())
        val accessibilityPerm = FocusAwareService.isServiceEnabled(requireContext())
        val usageStatsPerm = bannerManager.hasUsageStatsPermission()
        
        val message = StringBuilder().apply {
            append("📋 ESTADO DE PERMISOS:\n\n")
            append("• Overlay: ${if(overlayPerm) "✅" else "❌"}\n")
            append("• Accesibilidad: ${if(accessibilityPerm) "✅" else "❌"}\n")
            append("• Datos de Uso: ${if(usageStatsPerm) "✅" else "⚠️"}\n\n")
            
            if (!usageStatsPerm) {
                append("RECOMENDACIÓN: Activa 'Datos de Uso' para:\n")
                append("✓ Detectar con precisión cuándo sales de las apps\n")
                append("✓ Mayor eficiencia energética\n")
                append("✓ Estadísticas más exactas\n\n")
                append("Sin este permiso, usaremos el método estándar (menos preciso).")
            } else {
                append("✓ Todos los permisos recomendados están activados.")
            }
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Permisos del Sistema")
            .setMessage(message.toString())
            .setPositiveButton("Configurar Datos de Uso") { _, _ ->
                bannerManager.requestUsageStatsPermission(requireActivity())
            }
            .setNeutralButton("Verificar Accesibilidad") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
    
    private fun loadInstalledApps() {
        val packageManager = requireContext().packageManager
        apps = packageManager.getInstalledApplications(0)
            .filter { 
                val launchIntent = packageManager.getLaunchIntentForPackage(it.packageName)
                launchIntent != null 
            }
            .sortedBy { app ->
                try {
                    packageManager.getApplicationLabel(app).toString()
                        .toLowerCase(Locale.getDefault())
                } catch (e: Exception) {
                    app.packageName
                }
            }

        val selectedApps = viewModel.monitoredApps.value?.toSet() ?: emptySet()
        appsAdapter = SimpleAppsAdapter(requireContext(), apps, selectedApps) { packageName, isChecked ->
            onAppChecked(packageName, isChecked)
        }
        recyclerViewApps.adapter = appsAdapter
        
        Log.d(TAG, "Cargadas ${apps.size} apps con launcher")
    }
    
    private fun onAppChecked(packageName: String, isChecked: Boolean) {
        val appName = try {
            val pm = requireContext().packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
        
        if (isChecked) {
            viewModel.addMonitoredApp(packageName)
            Toast.makeText(requireContext(), "✓ $appName monitoreada", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.removeMonitoredApp(packageName)
            Toast.makeText(requireContext(), "✗ $appName removida", Toast.LENGTH_SHORT).show()
        }
        updateSelectedAppsCount()
    }
    
    private fun updateSelectedAppsCount() {
        val count = viewModel.monitoredApps.value?.size ?: 0
        val total = apps.size
        tvSelectedAppsCount.text = "$count/$total apps seleccionadas"
    }
    
    private fun showAppSelectionDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_app_selection, null)
        
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerViewDialogApps)
        val btnSelectAll = dialogView.findViewById<Button>(R.id.btnSelectAll)
        val btnDeselectAll = dialogView.findViewById<Button>(R.id.btnDeselectAll)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmSelection)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelSelection)
        val tvSelectedCount = dialogView.findViewById<TextView>(R.id.tvSelectedCount)
        
        val currentMonitoredApps = viewModel.monitoredApps.value?.toSet() ?: emptySet()
        
        dialogAdapter = SimpleAppsAdapter(
            requireContext(),
            apps,
            currentMonitoredApps.toSet(),
            { packageName, isChecked ->
                if (isChecked) {
                    viewModel.addMonitoredApp(packageName)
                } else {
                    viewModel.removeMonitoredApp(packageName)
                }
                
                dialogAdapter?.let { adapter ->
                    updateSelectedCountDialog(tvSelectedCount, adapter)
                }
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = dialogAdapter
        
        dialogAdapter?.let { adapter ->
            updateSelectedCountDialog(tvSelectedCount, adapter)
        }
        
        btnSelectAll.setOnClickListener {
            val allPackages = apps.map { it.packageName }.toSet()
            allPackages.forEach { packageName ->
                viewModel.addMonitoredApp(packageName)
            }
            dialogAdapter?.updateSelectedApps(allPackages)
        }
        
        btnDeselectAll.setOnClickListener {
            val currentSelected = dialogAdapter?.getSelectedApps() ?: emptySet()
            currentSelected.forEach { packageName ->
                viewModel.removeMonitoredApp(packageName)
            }
            dialogAdapter?.updateSelectedApps(emptySet())
        }
        
        btnConfirm.setOnClickListener {
            selectionDialog?.dismiss()
            selectionDialog = null
            dialogAdapter = null
            updateSelectedAppsCount()
            Toast.makeText(requireContext(), "✅ Selección guardada", Toast.LENGTH_SHORT).show()
        }
        
        btnCancel.setOnClickListener {
            val originalApps = viewModel.monitoredApps.value?.toSet() ?: emptySet()
            dialogAdapter?.updateSelectedApps(originalApps)
            
            selectionDialog?.dismiss()
            selectionDialog = null
            dialogAdapter = null
        }
        
        selectionDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        selectionDialog?.show()
    }
    
    private fun updateSelectedCountDialog(textView: TextView, adapter: SimpleAppsAdapter) {
        val count = adapter.getSelectedCount()
        textView.text = "$count apps seleccionadas"
    }
    
    private fun testBannerNow() {
        Log.d(TAG, "=== PRUEBA DE BANNER ===")
        
        if (!Settings.canDrawOverlays(requireContext())) {
            Toast.makeText(requireContext(), 
                "❌ Necesitas permiso de overlay", 
                Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }
        
        val testMessage = "🧪 BANNER DE PRUEBA\n\n" +
                "• Temporal: 15 segundos\n" +
                "• Toca para cerrar antes\n" +
                "• No afecta monitoreo real"
        
        bannerManager.showTestBanner(testMessage)
        
        Toast.makeText(requireContext(), 
            "✅ Banner mostrado (15s)", 
            Toast.LENGTH_SHORT).show()
    }
    
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${requireContext().packageName}")
        )
        startActivity(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        selectionDialog?.dismiss()
        selectionDialog = null
        dialogAdapter = null
    }
}

// ======================================================
// CLASE ADAPTER - AL FINAL DEL ARCHIVO (FUERA DE LA CLASE)
// ======================================================
class SimpleAppsAdapter(
    private val context: Context,
    private val apps: List<ApplicationInfo>,
    initialSelectedApps: Set<String>,
    private val onAppChecked: (String, Boolean) -> Unit
) : RecyclerView.Adapter<SimpleAppsAdapter.ViewHolder>() {
    
    private val packageManager = context.packageManager
    private val selectedApps = initialSelectedApps.toMutableSet()
    private var isUpdating = false
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxApp)
        val appName: TextView = itemView.findViewById(R.id.textAppName)
        val appIcon: ImageView = itemView.findViewById(R.id.imageAppIcon)
        var currentPackageName: String? = null
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_simple_app, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val packageName = app.packageName
        
        holder.currentPackageName = packageName
        
        try {
            holder.appIcon.setImageDrawable(app.loadIcon(packageManager))
            holder.appName.text = app.loadLabel(packageManager).toString()
            
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = selectedApps.contains(packageName)
            
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                val currentPkg = holder.currentPackageName
                
                if (currentPkg == packageName && !isUpdating) {
                    isUpdating = true
                    
                    if (isChecked) {
                        selectedApps.add(packageName)
                    } else {
                        selectedApps.remove(packageName)
                    }
                    
                    onAppChecked(packageName, isChecked)
                    
                    isUpdating = false
                }
            }
            
            holder.itemView.setOnClickListener {
                val currentPkg = holder.currentPackageName
                if (currentPkg == packageName) {
                    holder.checkBox.isChecked = !holder.checkBox.isChecked
                }
            }
            
        } catch (e: Exception) {
            holder.appName.text = packageName
        }
    }
    
    override fun getItemCount(): Int = apps.size
    
    fun updateSelectedApps(newSelectedApps: Set<String>) {
        isUpdating = true
        selectedApps.clear()
        selectedApps.addAll(newSelectedApps)
        notifyDataSetChanged()
        isUpdating = false
    }
    
    fun getSelectedCount(): Int = selectedApps.size
    
    fun getSelectedApps(): Set<String> = selectedApps.toSet()
}