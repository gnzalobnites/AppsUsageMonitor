package com.gnzalobnites.appsusagemonitor.fragments

import com.gnzalobnites.appsusagemonitor.banner.BannerManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.gnzalobnites.appsusagemonitor.*
import com.gnzalobnites.appsusagemonitor.AppUsageMonitorApp

class DashboardFragment : Fragment() {

    private val TAG = "DASHBOARD_FRAGMENT"
    
    // UI Elements
    private lateinit var viewModel: MainViewModel
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvMonitoredAppsCount: TextView
    private lateinit var btnToggleService: Button
    private lateinit var btnCheckPermissions: Button
    private lateinit var btnQuickSummary: Button
    private lateinit var btnGoToSettings: Button
    private lateinit var btnGoToStats: Button
    private lateinit var btnQuickMonitor: Button
    private lateinit var btnQuickBannerSettings: Button
    private lateinit var btnThemeToggle: ImageButton
    private lateinit var tvEmail: TextView
    private lateinit var btnBuyCoffee: TextView
    
    // BannerManager para pruebas de permisos
    private lateinit var bannerManager: BannerManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "🔵 onCreateView - INICIO")
        try {
            val view = inflater.inflate(R.layout.fragment_dashboard_simplified, container, false)
            Log.d(TAG, "✅ Layout inflado exitosamente")
            return view
        } catch (e: Exception) {
            Log.e(TAG, "💥 ERROR FATAL en onCreateView: ${e.message}", e)
            throw e
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "🔵 onViewCreated - INICIO")
        
        try {
            // Inicializar ViewModel
            viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
            
            // Inicializar BannerManager para permisos
            bannerManager = BannerManager(requireContext())
            val userPrefs = UserPreferences.getInstance(requireContext())
            val db = AppDatabase.getDatabase(requireContext())
            bannerManager.initialize(userPrefs, db)
            
            // Inicializar vistas
            initViews(view)
            
            // Configurar UI
            setupUI()
            
            // Configurar listeners
            setupListeners()
            
            // Configurar observadores
            setupObservers()
            
            // Verificar estado inicial
            checkServiceStatus()
            
            Log.d(TAG, "🎉 onViewCreated COMPLETADO EXITOSAMENTE")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 ERROR FATAL en onViewCreated: ${e.message}", e)
        }
    }
    
    private fun initViews(view: View) {
        Log.d(TAG, "🔵 initViews - INICIO")
        
        try {
            tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
            tvMonitoredAppsCount = view.findViewById(R.id.tvMonitoredAppsCount)
            btnToggleService = view.findViewById(R.id.btnToggleService)
            btnCheckPermissions = view.findViewById(R.id.btnCheckPermissions)
            btnQuickSummary = view.findViewById(R.id.btnQuickSummary)
            btnGoToSettings = view.findViewById(R.id.btnGoToSettings)
            btnGoToStats = view.findViewById(R.id.btnGoToStats)
            btnQuickMonitor = view.findViewById(R.id.btnQuickMonitor)
            btnQuickBannerSettings = view.findViewById(R.id.btnQuickBannerSettings)
            btnThemeToggle = view.findViewById(R.id.btnThemeToggle)
            tvEmail = view.findViewById(R.id.tvEmail)
            btnBuyCoffee = view.findViewById(R.id.btnBuyCoffee)
            
            Log.d(TAG, "✅ Todas las vistas encontradas")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 initViews ERROR: ${e.message}", e)
            throw e
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🟢 onResume - INICIO")
        
        try {
            checkServiceStatus()
            updateMonitoredAppsCount()
            updateThemeButtonIcon()
        } catch (e: Exception) {
            Log.e(TAG, "💥 onResume ERROR: ${e.message}", e)
        }
    }
    
    private fun setupUI() {
        Log.d(TAG, "🔵 setupUI - INICIO")
        
        try {
            updateServiceStatusUI(false)
            updateMonitoredAppsCount()
            updateThemeButtonIcon()
        } catch (e: Exception) {
            Log.e(TAG, "💥 setupUI ERROR: ${e.message}", e)
            throw e
        }
    }
    
    private fun setupListeners() {
        Log.d(TAG, "🔵 setupListeners - INICIO")
        
        try {
            btnToggleService.setOnClickListener { toggleService() }
            btnCheckPermissions.setOnClickListener { showPermissionsDialog() }
            btnQuickSummary.setOnClickListener { showQuickSummary() }
            btnQuickMonitor.setOnClickListener { navigateToMonitor() }
            btnQuickBannerSettings.setOnClickListener { navigateToSettings() }
            btnThemeToggle.setOnClickListener { toggleTheme() }
            btnGoToSettings.setOnClickListener { navigateToSettings() }
            btnGoToStats.setOnClickListener { navigateToStats() }
            tvEmail.setOnClickListener { sendEmail() }
            btnBuyCoffee.setOnClickListener { openBuyMeACoffeeLink() }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 setupListeners ERROR: ${e.message}", e)
            throw e
        }
    }
    
    private fun setupObservers() {
        Log.d(TAG, "🔵 setupObservers - INICIO")
        
        try {
            viewModel.monitoredApps.observe(viewLifecycleOwner, Observer { apps ->
                apps?.let { updateMonitoredAppsCount() }
            })
            
            viewModel.isDarkMode.observe(viewLifecycleOwner, Observer { isDark ->
                isDark?.let { updateThemeButtonIcon() }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 setupObservers ERROR: ${e.message}", e)
            throw e
        }
    }
    
    private fun toggleTheme() {
        Log.d(TAG, "🎨 toggleTheme - INICIO")
        
        try {
            val currentIsDark = viewModel.isDarkMode.value ?: false
            val newIsDark = !currentIsDark
            
            viewModel.updateDarkMode(newIsDark)
            
            val app = requireActivity().application as AppUsageMonitorApp
            app.applyTheme(newIsDark)
            
            updateThemeButtonIcon()
            
            val themeMessage = if (newIsDark) 
                getString(R.string.settings_theme_dark) 
            else 
                getString(R.string.settings_theme_light)
            Toast.makeText(requireContext(), themeMessage, Toast.LENGTH_SHORT).show()
            
            requireActivity().recreate()
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 toggleTheme ERROR: ${e.message}", e)
            Toast.makeText(requireContext(), getString(R.string.error_occurred), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateThemeButtonIcon() {
        try {
            val isDarkMode = viewModel.isDarkMode.value ?: false
            
            if (isDarkMode) {
                btnThemeToggle.setImageResource(R.drawable.ic_light_mode)
                btnThemeToggle.contentDescription = getString(R.string.settings_theme_light)
            } else {
                btnThemeToggle.setImageResource(R.drawable.ic_dark_mode)
                btnThemeToggle.contentDescription = getString(R.string.settings_theme_dark)
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 updateThemeButtonIcon ERROR: ${e.message}", e)
        }
    }
    
    private fun toggleService() {
        Log.d(TAG, "🔄 toggleService - INICIO")
        
        try {
            val isServiceRunning = isServiceRunning()
            
            if (isServiceRunning) {
                stopService()
            } else {
                if (checkBasicPermissions()) {
                    startService()
                } else {
                    showPermissionsDialog()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 toggleService ERROR: ${e.message}", e)
        }
    }
    
    private fun checkBasicPermissions(): Boolean {
        Log.d(TAG, "🔐 checkBasicPermissions - INICIO")
        
        try {
            val overlayPerm = Settings.canDrawOverlays(requireContext())
            val accessibilityPerm = FocusAwareService.isServiceEnabled(requireContext())
            
            return overlayPerm && accessibilityPerm
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 checkBasicPermissions ERROR: ${e.message}", e)
            return false
        }
    }
    
    private fun showPermissionsDialog() {
        Log.d(TAG, "📋 showPermissionsDialog - INICIO")
        
        try {
            val overlayPerm = Settings.canDrawOverlays(requireContext())
            val accessibilityPerm = FocusAwareService.isServiceEnabled(requireContext())
            val usageStatsPerm = bannerManager.hasUsageStatsPermission()
            
            val message = StringBuilder().apply {
                append(getString(R.string.permission_dialog_title) + ":\n\n")
                append("• Permiso Overlay: ")
                append(if(overlayPerm) "✅ " + getString(R.string.dialog_yes) else "❌ " + getString(R.string.dialog_no))
                append("\n")
                append("• Servicio Accesibilidad: ")
                append(if(accessibilityPerm) "✅ " + getString(R.string.dialog_yes) else "❌ " + getString(R.string.dialog_no))
                append("\n")
                append("• Datos de Uso (precisión): ")
                append(if(usageStatsPerm) "✅ " + getString(R.string.dialog_yes) else "⚠️ " + getString(R.string.dialog_no))
                append("\n\n")
                
                when {
                    !overlayPerm && !accessibilityPerm -> 
                        append(getString(R.string.error_overlay_permission))
                    !overlayPerm -> 
                        append("❌ " + getString(R.string.error_overlay_permission))
                    !accessibilityPerm -> 
                        append(getString(R.string.permission_required))
                    else -> 
                        append(getString(R.string.monitor_usage_stats_off))
                }
            }
            
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.permission_dialog_title))
                .setMessage(message.toString())
                .setPositiveButton(getString(R.string.settings_save)) { _, _ ->
                    showPermissionOptionsDialog(overlayPerm, accessibilityPerm, usageStatsPerm)
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
                
        } catch (e: Exception) {
            Log.e(TAG, "💥 showPermissionsDialog ERROR: ${e.message}", e)
        }
    }
    
    private fun showPermissionOptionsDialog(
        overlayPerm: Boolean, 
        accessibilityPerm: Boolean,
        usageStatsPerm: Boolean
    ) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        
        if (!overlayPerm) {
            options.add("🔲 " + getString(R.string.permission_dialog_title))
            actions.add { requestOverlayPermission() }
        }
        
        if (!accessibilityPerm) {
            options.add("♿ " + getString(R.string.permissions_title))
            actions.add { requestAccessibilityPermission() }
        }
        
        if (!usageStatsPerm) {
            options.add("📊 " + getString(R.string.monitor_usage_stats_off))
            actions.add { requestUsageStatsPermission() }
        }
        
        if (options.isEmpty()) {
            Toast.makeText(requireContext(), "✅ " + getString(R.string.success), Toast.LENGTH_SHORT).show()
            return
        }
        
        val items = options.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.permissions_title))
            .setItems(items) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }
    
    private fun requestOverlayPermission() {
        Log.d(TAG, "🔐 Solicitando permiso overlay")
        
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            startActivity(intent)
            Toast.makeText(requireContext(), 
                getString(R.string.error_overlay_permission), 
                Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "💥 requestOverlayPermission ERROR: ${e.message}", e)
        }
    }
    
    private fun requestAccessibilityPermission() {
        Log.d(TAG, "🔐 Solicitando permiso accesibilidad")
        
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(requireContext(), 
                getString(R.string.permission_usage_stats_instructions, "Apps Usage Monitor"), 
                Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "💥 requestAccessibilityPermission ERROR: ${e.message}", e)
        }
    }
    
    private fun requestUsageStatsPermission() {
        Log.d(TAG, "🔐 Solicitando permiso Datos de Uso")
        
        try {
            bannerManager.requestUsageStatsPermission(requireActivity())
        } catch (e: Exception) {
            Log.e(TAG, "💥 requestUsageStatsPermission ERROR: ${e.message}", e)
        }
    }
    
    private fun startService() {
        Log.d(TAG, "▶️ startService - INICIO")
        
        try {
            val intent = Intent(requireContext(), FocusAwareService::class.java)
            requireContext().startService(intent)
            
            handler.postDelayed({
                try {
                    checkServiceStatus()
                    Toast.makeText(requireContext(), getString(R.string.service_started), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error en callback de inicio: ${e.message}")
                }
            }, 1000)
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 startService ERROR: ${e.message}", e)
            Toast.makeText(requireContext(), "❌ ${getString(R.string.error_occurred)}: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopService() {
        Log.d(TAG, "⏹️ stopService - INICIO")
        
        try {
            val intent = Intent(requireContext(), FocusAwareService::class.java)
            requireContext().stopService(intent)
            
            updateServiceStatusUI(false)
            Toast.makeText(requireContext(), getString(R.string.service_inactive), Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 stopService ERROR: ${e.message}", e)
            Toast.makeText(requireContext(), "❌ ${getString(R.string.error_occurred)}: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkServiceStatus() {
        Log.d(TAG, "🔍 checkServiceStatus - INICIO")
        
        try {
            val isRunning = isServiceRunning()
            updateServiceStatusUI(isRunning)
        } catch (e: Exception) {
            Log.e(TAG, "💥 checkServiceStatus ERROR: ${e.message}", e)
            updateServiceStatusUI(false)
        }
    }
    
    private fun updateServiceStatusUI(isRunning: Boolean) {
        Log.d(TAG, "🖥️ updateServiceStatusUI - isRunning = $isRunning")
        
        try {
            val context = requireContext()
            val successGreen = context.resources.getColor(R.color.success_green, context.theme)
            val errorRed = context.resources.getColor(R.color.error_red, context.theme)
            
            if (isRunning) {
                tvServiceStatus.text = getString(R.string.dashboard_service_active)
                tvServiceStatus.setTextColor(successGreen)
                btnToggleService.text = getString(R.string.dashboard_stop_service)
                btnToggleService.setBackgroundColor(errorRed)
            } else {
                tvServiceStatus.text = getString(R.string.dashboard_service_stopped)
                tvServiceStatus.setTextColor(errorRed)
                btnToggleService.text = getString(R.string.dashboard_start_service)
                btnToggleService.setBackgroundColor(successGreen)
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 updateServiceStatusUI ERROR: ${e.message}", e)
        }
    }
    
    private fun updateMonitoredAppsCount() {
        try {
            val count = viewModel.monitoredApps.value?.size ?: 0
            tvMonitoredAppsCount.text = getString(R.string.dashboard_monitored_count, count)
        } catch (e: Exception) {
            Log.e(TAG, "💥 updateMonitoredAppsCount ERROR: ${e.message}", e)
        }
    }
    
    private fun showQuickSummary() {
        Log.d(TAG, "📊 showQuickSummary - INICIO")
        
        try {
            val monitoredApps = viewModel.monitoredApps.value ?: emptyList()
            val bannerEnabled = viewModel.showBanner.value ?: false
            val usageStatsPerm = bannerManager.hasUsageStatsPermission()
            
            val summary = StringBuilder().apply {
                append(getString(R.string.summary_dialog_title) + "\n\n")
                append("• " + getString(R.string.dashboard_monitored_count, monitoredApps.size) + "\n")
                append("• " + getString(R.string.monitor_enable_banners) + ": ")
                append(if (bannerEnabled) "✅ " + getString(R.string.dialog_yes) else "⭕ " + getString(R.string.dialog_no))
                append("\n")
                append("• " + getString(R.string.service_active) + ": ")
                append(if (isServiceRunning()) "✅ " + getString(R.string.service_active) else "⭕ " + getString(R.string.service_inactive))
                append("\n")
                append("• " + getString(R.string.monitor_usage_stats_on) + ": ")
                append(if (usageStatsPerm) "✅ " + getString(R.string.dialog_yes) else "⚠️ " + getString(R.string.dialog_no))
                append("\n\n")
                
                if (monitoredApps.isEmpty()) {
                    append("⚠️ " + getString(R.string.no_apps_selected) + "\n")
                    append(getString(R.string.select_apps))
                } else {
                    append(getString(R.string.monitor_selected_apps_label) + "\n")
                    var count = 0
                    val iterator = monitoredApps.iterator()
                    while (iterator.hasNext() && count < 5) {
                        val app = iterator.next()
                        val appName = getAppName(app)
                        append("  • $appName\n")
                        count++
                    }
                    if (monitoredApps.size > 5) {
                        append(getString(R.string.dialog_apps_selected_count, monitoredApps.size - 5))
                    }
                }
            }
            
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.summary_dialog_title))
                .setMessage(summary.toString())
                .setPositiveButton(getString(R.string.dialog_yes), null)
                .setNeutralButton(getString(R.string.monitor_title)) { _, _ ->
                    navigateToMonitor()
                }
                .show()
                
        } catch (e: Exception) {
            Log.e(TAG, "💥 showQuickSummary ERROR: ${e.message}", e)
        }
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val pm = requireContext().packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
    
    private fun navigateToMonitor() {
        try {
            val activity = activity
            if (activity is com.gnzalobnites.appsusagemonitor.MainNavActivity) {
                activity.loadFragment(MonitorFragment(), getString(R.string.monitor_title))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 navigateToMonitor ERROR: ${e.message}", e)
        }
    }
    
    private fun navigateToSettings() {
        try {
            val activity = activity
            if (activity is com.gnzalobnites.appsusagemonitor.MainNavActivity) {
                activity.loadFragment(SettingsFragment(), getString(R.string.settings_title))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 navigateToSettings ERROR: ${e.message}", e)
        }
    }
    
    private fun navigateToStats() {
        try {
            val activity = activity
            if (activity is com.gnzalobnites.appsusagemonitor.MainNavActivity) {
                activity.loadFragment(StatsFragment(), getString(R.string.stats_title))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 navigateToStats ERROR: ${e.message}", e)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        try {
            val activityManager = requireContext().getSystemService(android.app.ActivityManager::class.java)
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)
            
            val targetClassName = FocusAwareService::class.java.name
            
            for (service in services) {
                if (service.service.className == targetClassName) {
                    return true
                }
            }
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 isServiceRunning ERROR: ${e.message}", e)
            return false
        }
    }
    
    private fun sendEmail() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:benitesgonzalogaston@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.dashboard_footer_suggestions))
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando email: ${e.message}")
            Toast.makeText(requireContext(), getString(R.string.error_open_link), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openBuyMeACoffeeLink() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, 
                Uri.parse("https://www.buymeacoffee.com/gnzbenitesh"))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo enlace: ${e.message}")
            Toast.makeText(requireContext(), getString(R.string.error_open_link), Toast.LENGTH_SHORT).show()
        }
    }
    
    companion object {
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    }
}