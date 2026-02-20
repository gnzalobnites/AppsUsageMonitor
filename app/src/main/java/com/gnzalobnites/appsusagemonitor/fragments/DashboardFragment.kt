package com.gnzalobnites.appsusagemonitor.fragments

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
import androidx.lifecycle.ViewModelProvider
import com.gnzalobnites.appsusagemonitor.*
import com.gnzalobnites.appsusagemonitor.banner.BannerManager

class DashboardFragment : Fragment() {

    private val TAG = "DashboardFragment"
    
    // ViewModel
    private lateinit var viewModel: MainViewModel
    
    // UI Elements
    private var _tvServiceStatus: TextView? = null
    private var _tvMonitoredAppsCount: TextView? = null
    private var _btnToggleService: Button? = null
    private var _btnCheckPermissions: Button? = null
    private var _btnQuickSummary: Button? = null
    private var _btnGoToSettings: Button? = null
    private var _btnGoToStats: Button? = null
    private var _btnQuickMonitor: Button? = null
    private var _btnQuickBannerSettings: Button? = null
    private var _btnThemeToggle: ImageButton? = null
    private var _tvEmail: TextView? = null
    private var _btnBuyCoffee: TextView? = null
    
    // BannerManager
    private lateinit var bannerManager: BannerManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "🔵 onCreateView")
        return inflater.inflate(R.layout.fragment_dashboard_simplified, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "🔵 onViewCreated - INICIO")
        
        try {
            // 1. Inicializar ViewModel
            viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
            Log.d(TAG, "✅ ViewModel inicializado")
            
            // 2. Inicializar BannerManager
            bannerManager = BannerManager(requireContext())
            val userPrefs = UserPreferences.getInstance(requireContext())
            val db = AppDatabase.getDatabase(requireContext())
            bannerManager.initialize(userPrefs, db)
            Log.d(TAG, "✅ BannerManager inicializado")
            
            // 3. Inicializar vistas
            initViews(view)
            
            // 4. Configurar listeners
            setupListeners()
            
            // 5. Configurar UI inicial
            updateServiceStatusUI(isServiceRunning())
            updateMonitoredAppsCount()
            updateThemeButtonIcon()
            
            // 6. Configurar observadores
            setupObservers()
            
            Log.d(TAG, "🎉 DashboardFragment configurado COMPLETAMENTE")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 ERROR FATAL: ${e.message}", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun initViews(view: View) {
        Log.d(TAG, "🔵 initViews - Buscando vistas...")
        
        _tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        _tvMonitoredAppsCount = view.findViewById(R.id.tvMonitoredAppsCount)
        _btnToggleService = view.findViewById(R.id.btnToggleService)
        _btnCheckPermissions = view.findViewById(R.id.btnCheckPermissions)
        _btnQuickSummary = view.findViewById(R.id.btnQuickSummary)
        _btnGoToSettings = view.findViewById(R.id.btnGoToSettings)
        _btnGoToStats = view.findViewById(R.id.btnGoToStats)
        _btnQuickMonitor = view.findViewById(R.id.btnQuickMonitor)
        _btnQuickBannerSettings = view.findViewById(R.id.btnQuickBannerSettings)
        _btnThemeToggle = view.findViewById(R.id.btnThemeToggle)
        _tvEmail = view.findViewById(R.id.tvEmail)
        _btnBuyCoffee = view.findViewById(R.id.btnBuyCoffee)
        
        Log.d(TAG, "✅ Vistas inicializadas")
    }
    
    private fun setupListeners() {
        Log.d(TAG, "🔵 setupListeners - Configurando listeners...")
        
        _btnToggleService?.setOnClickListener {
            Log.d(TAG, "👆 Click en btnToggleService")
            toggleService()
        }
        
        _btnCheckPermissions?.setOnClickListener {
            Log.d(TAG, "👆 Click en btnCheckPermissions")
            showPermissionsDialog()
        }
        
        _btnQuickSummary?.setOnClickListener {
            Log.d(TAG, "👆 Click en btnQuickSummary")
            showQuickSummary()
        }
        
        _btnGoToSettings?.setOnClickListener {
            Log.d(TAG, "👆 Click en btnGoToSettings")
            navigateToSettings()
        }
        
        _btnGoToStats?.setOnClickListener {
            Log.d(TAG, "👆 Click en btnGoToStats")
            navigateToStats()
        }
        
        _btnQuickMonitor?.setOnClickListener {
            Log.d(TAG, "👆 Click en btnQuickMonitor")
            navigateToMonitor()
        }
        
        _btnQuickBannerSettings?.setOnClickListener {
            Log.d(TAG, "👆 Click en btnQuickBannerSettings")
            navigateToSettings()
        }
        
        _btnThemeToggle?.setOnClickListener {
            Log.d(TAG, "👆 Click en btnThemeToggle")
            toggleTheme()
        }
        
        _tvEmail?.setOnClickListener {
            Log.d(TAG, "👆 Click en tvEmail")
            sendEmail()
        }
        
        _btnBuyCoffee?.setOnClickListener {
            Log.d(TAG, "👆 Click en btnBuyCoffee")
            openBuyMeACoffeeLink()
        }
        
        Log.d(TAG, "✅ Listeners configurados")
    }
    
    private fun setupObservers() {
        Log.d(TAG, "🔵 setupObservers - Configurando observadores...")
        
        viewModel.monitoredApps.observe(viewLifecycleOwner) { apps ->
            apps?.let {
                Log.d(TAG, "📊 monitoredApps actualizado: ${it.size} apps")
                updateMonitoredAppsCount()
            }
        }
        
        viewModel.isDarkMode.observe(viewLifecycleOwner) { isDark ->
            isDark?.let {
                Log.d(TAG, "🎨 isDarkMode actualizado: $isDark")
                updateThemeButtonIcon()
            }
        }
        
        Log.d(TAG, "✅ Observadores configurados")
    }
    
    // ======================================================
    // MÉTODOS DE ACCIÓN
    // ======================================================
    
    private fun toggleService() {
        Log.d(TAG, "🔄 toggleService")
        
        try {
            val isRunning = isServiceRunning()
            
            if (isRunning) {
                stopService()
            } else {
                if (checkBasicPermissions()) {
                    startService()
                } else {
                    showPermissionsDialog()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en toggleService: ${e.message}", e)
        }
    }
    
    private fun startService() {
        Log.d(TAG, "▶️ Iniciando servicio...")
        try {
            val intent = Intent(requireContext(), FocusAwareService::class.java)
            requireContext().startService(intent)
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updateServiceStatusUI(true)
                Toast.makeText(requireContext(), "✅ Servicio iniciado", Toast.LENGTH_SHORT).show()
            }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando servicio: ${e.message}", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopService() {
        Log.d(TAG, "⏹️ Deteniendo servicio...")
        try {
            val intent = Intent(requireContext(), FocusAwareService::class.java)
            requireContext().stopService(intent)
            updateServiceStatusUI(false)
            Toast.makeText(requireContext(), "⏹️ Servicio detenido", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deteniendo servicio: ${e.message}", e)
        }
    }
    
    private fun checkBasicPermissions(): Boolean {
        val overlayPerm = Settings.canDrawOverlays(requireContext())
        val accessibilityPerm = FocusAwareService.isServiceEnabled(requireContext())
        Log.d(TAG, "🔐 Permisos - Overlay: $overlayPerm, Accessibility: $accessibilityPerm")
        return overlayPerm && accessibilityPerm
    }
    
    private fun isServiceRunning(): Boolean {
        return try {
            val manager = requireContext().getSystemService(android.app.ActivityManager::class.java)
            val services = manager.getRunningServices(Integer.MAX_VALUE)
            val isRunning = services.any { it.service.className == FocusAwareService::class.java.name }
            Log.d(TAG, "🔍 isServiceRunning: $isRunning")
            isRunning
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando servicio: ${e.message}")
            false
        }
    }
    
    private fun updateServiceStatusUI(isRunning: Boolean) {
        _tvServiceStatus?.let { tv ->
            if (isRunning) {
                tv.text = "✅ Servicio ACTIVO"
                tv.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
                _btnToggleService?.text = "⏹️ DETENER SERVICIO"
            } else {
                tv.text = "⭕ Servicio INACTIVO"
                tv.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                _btnToggleService?.text = "▶️ INICIAR SERVICIO"
            }
        }
    }
    
    private fun updateMonitoredAppsCount() {
        val count = viewModel.monitoredApps.value?.size ?: 0
        _tvMonitoredAppsCount?.text = "📱 Apps monitoreadas: $count"
        Log.d(TAG, "📊 Monitored apps count: $count")
    }
    
    private fun updateThemeButtonIcon() {
        val isDarkMode = viewModel.isDarkMode.value ?: false
        _btnThemeToggle?.setImageResource(
            if (isDarkMode) android.R.drawable.ic_menu_compass else android.R.drawable.ic_menu_compass
        )
    }
    
    private fun showPermissionsDialog() {
        Log.d(TAG, "📋 Mostrando diálogo de permisos")
        
        val overlayPerm = Settings.canDrawOverlays(requireContext())
        val accessibilityPerm = FocusAwareService.isServiceEnabled(requireContext())
        val usageStatsPerm = bannerManager.hasUsageStatsPermission()
        
        val message = StringBuilder().apply {
            append("📋 ESTADO DE PERMISOS:\n\n")
            append("• Overlay: ${if(overlayPerm) "✅ CONCEDIDO" else "❌ NO CONCEDIDO"}\n")
            append("• Accesibilidad: ${if(accessibilityPerm) "✅ CONCEDIDO" else "❌ NO CONCEDIDO"}\n")
            append("• Datos de Uso: ${if(usageStatsPerm) "✅ CONCEDIDO" else "⚠️ NO CONCEDIDO"}\n\n")
            
            if (!overlayPerm || !accessibilityPerm) {
                append("Para que la app funcione correctamente, necesitas conceder los permisos de Overlay y Accesibilidad.")
            } else {
                append("¡Todos los permisos necesarios están concedidos!")
            }
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Permisos")
            .setMessage(message.toString())
            .setPositiveButton("Entendido") { _, _ -> }
            .setNeutralButton("Configurar") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${requireContext().packageName}")))
            }
            .show()
    }
    
    private fun showQuickSummary() {
        Log.d(TAG, "📊 Mostrando resumen rápido")
        
        val monitoredApps = viewModel.monitoredApps.value ?: emptyList()
        val bannerEnabled = viewModel.showBanner.value ?: false
        val isRunning = isServiceRunning()
        
        val message = StringBuilder().apply {
            append("📊 RESUMEN RÁPIDO:\n\n")
            append("• Apps monitoreadas: ${monitoredApps.size}\n")
            append("• Banners: ${if (bannerEnabled) "ACTIVADOS" else "DESACTIVADOS"}\n")
            append("• Servicio: ${if (isRunning) "ACTIVO" else "INACTIVO"}\n\n")
            
            if (monitoredApps.isNotEmpty()) {
                append("Apps seleccionadas:\n")
                monitoredApps.take(5).forEachIndexed { index, pkg ->
                    val name = getAppName(pkg)
                    append("  ${index+1}. $name\n")
                }
                if (monitoredApps.size > 5) {
                    append("  ... y ${monitoredApps.size - 5} más\n")
                }
            }
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Resumen")
            .setMessage(message.toString())
            .setPositiveButton("OK", null)
            .show()
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
        (activity as? MainNavActivity)?.loadFragment(MonitorFragment(), "Monitor")
    }
    
    private fun navigateToSettings() {
        (activity as? MainNavActivity)?.loadFragment(SettingsFragment(), "Configuración")
    }
    
    private fun navigateToStats() {
        (activity as? MainNavActivity)?.loadFragment(StatsFragment(), "Estadísticas")
    }
    
    private fun toggleTheme() {
        val newIsDark = !(viewModel.isDarkMode.value ?: false)
        viewModel.updateDarkMode(newIsDark)
        (requireActivity().application as AppUsageMonitorApp).applyTheme(newIsDark)
        requireActivity().recreate()
    }
    
    private fun sendEmail() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:benitesgonzalogaston@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "Sugerencia sobre Apps Usage Monitor")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No hay app de email", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openBuyMeACoffeeLink() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, 
                Uri.parse("https://www.buymeacoffee.com/gnzbenitesh"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No se puede abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🟢 onResume")
        updateServiceStatusUI(isServiceRunning())
        updateMonitoredAppsCount()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "🔴 onDestroyView - Limpiando referencias")
        _tvServiceStatus = null
        _tvMonitoredAppsCount = null
        _btnToggleService = null
        _btnCheckPermissions = null
        _btnQuickSummary = null
        _btnGoToSettings = null
        _btnGoToStats = null
        _btnQuickMonitor = null
        _btnQuickBannerSettings = null
        _btnThemeToggle = null
        _tvEmail = null
        _btnBuyCoffee = null
    }
}