package com.gnzalobnites.appsusagemonitor

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.navigation.NavigationView
import com.gnzalobnites.appsusagemonitor.fragments.DashboardFragment
import com.gnzalobnites.appsusagemonitor.fragments.StatsFragment
import com.gnzalobnites.appsusagemonitor.fragments.MonitorFragment
import com.gnzalobnites.appsusagemonitor.fragments.SettingsFragment

class MainNavActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "MainNavActivity"
    
    // Hacer públicos para que los fragments puedan acceder
    lateinit var navView: NavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate - Iniciando MainNavActivity...")
        
        try {
            setContentView(R.layout.activity_main_nav)
            Log.d(TAG, "Layout cargado exitosamente")
            
            // ===========================================
            // INICIALIZAR TOOLBAR Y HAMBURGUESA
            // ===========================================
            toolbar = findViewById(R.id.toolbar)
            setSupportActionBar(toolbar)
            
            // Configurar botón de hamburguesa
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setHomeButtonEnabled(true)
                // El icono ya está definido en el layout con app:navigationIcon
            }
            
            // ===========================================
            // ESTABLECER TÍTULO INICIAL DE LA APP
            // ===========================================
            toolbar.title = getString(R.string.app_name)
            
            // ===========================================
            
            // Inicializar vistas con verificación
            drawerLayout = findViewById(R.id.drawer_layout) 
                ?: throw IllegalStateException("DrawerLayout no encontrado en el layout")
            navView = findViewById(R.id.nav_view)
                ?: throw IllegalStateException("NavigationView no encontrado en el layout")
            
            navView.setNavigationItemSelectedListener(this)
            
            // Cargar fragment inicial
            if (savedInstanceState == null) {
                loadDashboard()
                navView.setCheckedItem(R.id.nav_dashboard)
            }
            
            Log.d(TAG, "✅ MainNavActivity creada y configurada exitosamente")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR CRÍTICO en onCreate: ${e.message}", e)
            showErrorAndRecover(
                "Error al iniciar la aplicación: ${e.localizedMessage ?: "Error desconocido"}"
            )
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Manejar clic en el ícono de hamburguesa (home)
        when (item.itemId) {
            android.R.id.home -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    drawerLayout.openDrawer(GravityCompat.START)
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "Item seleccionado: ${item.title}")
        
        try {
            // Cerrar drawer primero
            drawerLayout.closeDrawer(GravityCompat.START)
            
            // Pequeño delay para que se cierre el drawer
            Handler(Looper.getMainLooper()).postDelayed({
                when (item.itemId) {
                    R.id.nav_dashboard -> {
                        loadDashboard()
                        navView.setCheckedItem(R.id.nav_dashboard)
                    }
                    R.id.nav_stats -> {
                        loadFragment(StatsFragment(), getString(R.string.stats_title))
                        navView.setCheckedItem(R.id.nav_stats)
                    }
                    R.id.nav_monitor -> {
                        loadFragment(MonitorFragment(), getString(R.string.monitor_title))
                        navView.setCheckedItem(R.id.nav_monitor)
                    }
                    R.id.nav_settings -> {
                        navigateToSettings()
                    }
                    R.id.nav_tools -> {
                        loadSimpleFragment("Herramientas")
                        navView.setCheckedItem(R.id.nav_tools)
                    }
                    R.id.nav_permissions -> {
                        Toast.makeText(this, "Los permisos se configuran desde Dashboard", 
                            Toast.LENGTH_SHORT).show()
                        loadDashboard()
                    }
                    R.id.nav_test -> {
                        Toast.makeText(this, "Test banner - en desarrollo", Toast.LENGTH_SHORT).show()
                    }
                    R.id.nav_about -> {
                        Toast.makeText(this, "Apps Usage Monitor v1.0", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Log.w(TAG, "Item de navegación no manejado: ${item.itemId}")
                    }
                }
            }, 200)
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en navegación: ${e.message}", e)
            Toast.makeText(this, "Error al navegar", Toast.LENGTH_SHORT).show()
            return false
        }
    }
    
    fun loadDashboard() {
        try {
            Log.d(TAG, "Cargando Dashboard simplificado...")
            
            // Limpiar back stack completamente
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            
            // Cargar Dashboard sin añadir al back stack
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, DashboardFragment())
                .commitAllowingStateLoss()
                
            toolbar.title = getString(R.string.app_name)
            navView.setCheckedItem(R.id.nav_dashboard)
            Log.d(TAG, "✅ Dashboard simplificado cargado")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando dashboard: ${e.message}", e)
            Toast.makeText(this, "Error cargando dashboard", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Método principal que acepta String
    fun loadSimpleFragment(title: String) {
        try {
            Log.d(TAG, "Cargando fragment simple: $title")
            val fragment = SimpleFragment.newInstance(title)
            
            // Cargar fragment añadiendo al back stack
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack("simple_$title")
                .commitAllowingStateLoss()
                
            toolbar.title = title
            Log.d(TAG, "✅ Fragment '$title' cargado")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando fragment simple: ${e.message}", e)
            Toast.makeText(this, "Error cargando contenido", Toast.LENGTH_SHORT).show()
            showErrorAndRecover("No se pudo cargar el contenido solicitado")
        }
    }
    
    // Método sobrecargado que acepta resource ID
    fun loadSimpleFragment(titleResId: Int) {
        loadSimpleFragment(getString(titleResId))
    }
    
    // Método para cambiar fragment desde cualquier lugar (String)
    fun loadFragment(fragment: Fragment, title: String) {
        try {
            Log.d(TAG, "Cargando fragment personalizado: $title")
            
            // Verificar si ya hay fragments en el contenedor
            val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            
            if (currentFragment != null && currentFragment::class.java == fragment::class.java) {
                Log.d(TAG, "⚠️ Fragment ya está visible: $title")
                return
            }
            
            // Cargar fragment añadiendo al back stack
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack("fragment_${title.replace(" ", "_")}")
                .commitAllowingStateLoss()
                
            toolbar.title = title
            Log.d(TAG, "✅ Fragment personalizado '$title' cargado")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando fragment personalizado: ${e.message}", e)
            Toast.makeText(this, "Error al cambiar de vista", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Método sobrecargado para resource ID
    fun loadFragment(fragment: Fragment, titleResId: Int) {
        loadFragment(fragment, getString(titleResId))
    }
    
    private fun navigateToSettings() {
        try {
            // Cargar SettingsFragment añadiendo al back stack
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, SettingsFragment())
                .addToBackStack("settings")
                .commitAllowingStateLoss()
                
            toolbar.title = getString(R.string.settings_title)
            navView.setCheckedItem(R.id.nav_settings)
            Log.d(TAG, "✅ SettingsFragment cargado")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error navegando a settings: ${e.message}", e)
            Toast.makeText(this, "Error abriendo configuración", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onBackPressed() {
        try {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
                return
            }
            
            // Verificar si estamos en el Dashboard
            val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            
            if (currentFragment is DashboardFragment) {
                // Si ya estamos en Dashboard, mostrar diálogo de salida
                showExitConfirmationDialog()
            } else {
                // Si no estamos en Dashboard, volver al Dashboard
                loadDashboard()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en onBackPressed: ${e.message}")
            super.onBackPressed()
        }
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Salir")
            .setMessage("¿Estás seguro de que quieres salir de la aplicación?")
            .setPositiveButton("Sí", object : android.content.DialogInterface.OnClickListener {
                override fun onClick(dialog: android.content.DialogInterface, which: Int) {
                    // Cerrar la aplicación
                    finishAffinity()
                }
            })
            .setNegativeButton("No", null)
            .show()
    }
    
    fun clearBackStack() {
        try {
            // Limpiar todo el back stack
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            Log.d(TAG, "✅ Back stack limpiado")
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando back stack: ${e.message}")
        }
    }
    
    private fun showErrorAndRecover(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            
            try {
                loadDashboard()
                
                // Opcional: mostrar diálogo informativo
                AlertDialog.Builder(this)
                    .setTitle("Información")
                    .setMessage("Se ha recuperado de un error. Se ha cargado el Dashboard.")
                    .setPositiveButton("OK", null)
                    .show()
                    
            } catch (e: Exception) {
                Log.e(TAG, "Error crítico - No se pudo recuperar: ${e.message}", e)
                
                // Último recurso: intentar reiniciar la activity
                try {
                    val intent = Intent(this, MainNavActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                } catch (e2: Exception) {
                    Log.e(TAG, "Error fatal - No se pudo reiniciar la app", e2)
                }
            }
        }
    }
}