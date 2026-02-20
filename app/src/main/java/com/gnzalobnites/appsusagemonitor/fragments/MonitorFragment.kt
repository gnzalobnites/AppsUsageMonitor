package com.gnzalobnites.appsusagemonitor.fragments

import com.gnzalobnites.appsusagemonitor.SimpleAppsAdapter
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
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gnzalobnites.appsusagemonitor.*
import com.gnzalobnites.appsusagemonitor.banner.BannerManager

class MonitorFragment : Fragment() {

    private val TAG = "MonitorFragment"
    
    private lateinit var viewModel: MainViewModel
    private lateinit var bannerManager: BannerManager
    
    // UI Elements
    private var _swEnableBanners: Switch? = null
    private var _tvBannerStatus: TextView? = null
    private var _btnTestBanner: Button? = null
    private var _btnSelectApps: Button? = null
    private var _tvSelectedAppsCount: TextView? = null
    private var _recyclerViewApps: RecyclerView? = null
    private var _btnCheckPermissions: Button? = null
    private var _tvUsageStatsStatus: TextView? = null
    private var _swShowTikTokDemo: Switch? = null
    private var _tvTikTokDemoStatus: TextView? = null
    
    private lateinit var appsAdapter: SimpleAppsAdapter
    private var apps: List<ApplicationInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "🔵 onCreateView")
        return inflater.inflate(R.layout.fragment_monitor_enhanced, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "🔵 onViewCreated - INICIO")
        
        try {
            viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
            
            bannerManager = BannerManager(requireContext())
            val userPrefs = UserPreferences.getInstance(requireContext())
            val db = AppDatabase.getDatabase(requireContext())
            bannerManager.initialize(userPrefs, db)
            
            initViews(view)
            setupRecyclerView()
            setupListeners()
            loadCurrentSettings()
            loadInstalledApps()
            updateUsageStatsStatus()
            setupObservers()
            
            Log.d(TAG, "🎉 MonitorFragment configurado")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 ERROR: ${e.message}", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun initViews(view: View) {
        _swEnableBanners = view.findViewById(R.id.swEnableBanners)
        _tvBannerStatus = view.findViewById(R.id.tvBannerStatus)
        _btnTestBanner = view.findViewById(R.id.btnTestBanner)
        _btnSelectApps = view.findViewById(R.id.btnSelectApps)
        _tvSelectedAppsCount = view.findViewById(R.id.tvSelectedAppsCount)
        _recyclerViewApps = view.findViewById(R.id.recyclerViewApps)
        _btnCheckPermissions = view.findViewById(R.id.btnCheckPermissions)
        _tvUsageStatsStatus = view.findViewById(R.id.tvUsageStatsStatus)
        _swShowTikTokDemo = view.findViewById(R.id.swShowTikTokDemo)
        _tvTikTokDemoStatus = view.findViewById(R.id.tvTikTokDemoStatus)
    }
    
    private fun setupListeners() {
        _swEnableBanners?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateShowBanner(isChecked)
            _tvBannerStatus?.text = if (isChecked) "✅ Sí" else "⭕ No"
        }
        
        _swShowTikTokDemo?.setOnCheckedChangeListener { _, isChecked ->
            _tvTikTokDemoStatus?.text = if (isChecked) "✅ Sí" else "⭕ No"
        }
        
        _btnTestBanner?.setOnClickListener {
            testBannerNow()
        }
        
        _btnSelectApps?.setOnClickListener {
            showAppSelectionDialog()
        }
        
        _btnCheckPermissions?.setOnClickListener {
            showPermissionsDialog()
        }
    }
    
    private fun setupRecyclerView() {
        _recyclerViewApps?.layoutManager = LinearLayoutManager(requireContext())
    }
    
    private fun setupObservers() {
        viewModel.monitoredApps.observe(viewLifecycleOwner) { appsList ->
            appsList?.let {
                updateSelectedAppsCount()
                if (::appsAdapter.isInitialized) {
                    appsAdapter.updateSelectedApps(it.toSet())
                }
            }
        }
    }
    
    private fun loadCurrentSettings() {
        val showBanner = viewModel.showBanner.value ?: false
        _swEnableBanners?.isChecked = showBanner
        _tvBannerStatus?.text = if (showBanner) "✅ Sí" else "⭕ No"
    }
    
    private fun loadInstalledApps() {
        val pm = requireContext().packageManager
        apps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(pm).toString() }
        
        val selectedApps = viewModel.monitoredApps.value?.toSet() ?: emptySet()
        appsAdapter = SimpleAppsAdapter(requireContext(), apps, selectedApps) { packageName, isChecked ->
            if (isChecked) {
                viewModel.addMonitoredApp(packageName)
                Toast.makeText(requireContext(), "App agregada", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.removeMonitoredApp(packageName)
                Toast.makeText(requireContext(), "App removida", Toast.LENGTH_SHORT).show()
            }
        }
        _recyclerViewApps?.adapter = appsAdapter
        updateSelectedAppsCount()
    }
    
    private fun updateSelectedAppsCount() {
        val count = viewModel.monitoredApps.value?.size ?: 0
        _tvSelectedAppsCount?.text = "Seleccionadas: $count de ${apps.size}"
    }
    
    private fun updateUsageStatsStatus() {
        val hasPermission = bannerManager.hasUsageStatsPermission()
        _tvUsageStatsStatus?.text = if (hasPermission) 
            "✅ Datos de uso: ACTIVADOS" 
        else 
            "⚠️ Datos de uso: DESACTIVADOS"
    }
    
    private fun testBannerNow() {
        if (!Settings.canDrawOverlays(requireContext())) {
            Toast.makeText(requireContext(), "Se necesita permiso de overlay", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                Uri.parse("package:${requireContext().packageName}")))
            return
        }
        bannerManager.showTestBanner("🧪 Banner de prueba")
        Toast.makeText(requireContext(), "Banner mostrado", Toast.LENGTH_SHORT).show()
    }
    
    private fun showPermissionsDialog() {
        val overlayPerm = Settings.canDrawOverlays(requireContext())
        val accessibilityPerm = FocusAwareService.isServiceEnabled(requireContext())
        val usageStatsPerm = bannerManager.hasUsageStatsPermission()
        
        val message = "Overlay: ${if(overlayPerm) "✅" else "❌"}\n" +
                "Accesibilidad: ${if(accessibilityPerm) "✅" else "❌"}\n" +
                "Datos de Uso: ${if(usageStatsPerm) "✅" else "⚠️"}"
        
        AlertDialog.Builder(requireContext())
            .setTitle("Permisos")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showAppSelectionDialog() {
        // Por simplicidad, usamos el mismo adapter
        Toast.makeText(requireContext(), "Usa los checkboxes de la lista", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _swEnableBanners = null
        _tvBannerStatus = null
        _btnTestBanner = null
        _btnSelectApps = null
        _tvSelectedAppsCount = null
        _recyclerViewApps = null
        _btnCheckPermissions = null
        _tvUsageStatsStatus = null
        _swShowTikTokDemo = null
        _tvTikTokDemoStatus = null
    }
}