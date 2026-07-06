package com.gnzalobnites.appsusagemonitor

import android.widget.TextView
import android.view.View
import android.os.Build
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.gnzalobnites.appsusagemonitor.databinding.ActivityMainBinding
import com.gnzalobnites.appsusagemonitor.utils.PermissionHelper
import com.gnzalobnites.appsusagemonitor.utils.UpdateInfo
import com.gnzalobnites.appsusagemonitor.utils.UpdateManager
import com.gnzalobnites.appsusagemonitor.utils.AppUpdater
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AlertDialog
import android.util.Log

class MainActivity : AppCompatActivity() {
    private var appUpdater: AppUpdater? = null
    private var isUpdateCheckRunning = false
    private var updateCheckJob: Job? = null

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarConfiguration: AppBarConfiguration

    companion object {
        private const val TAG = "MainActivity"
        private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        val headerView = binding.navView.getHeaderView(0)
        val tvVersion = headerView.findViewById<TextView>(R.id.nav_header_version)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = getString(R.string.nav_header_version_format, packageInfo.versionName)
        } catch (e: Exception) {
            tvVersion.text = getString(R.string.nav_header_version_placeholder)
        }

        setContentView(binding.root)

        drawerLayout = binding.drawerLayout

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.appSelectionFragment,
                R.id.settingsFragment,
                R.id.aboutFragment
            ),
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDarkMode = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

            window.decorView.systemUiVisibility = if (!isDarkMode) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                0
            }
        }

        checkAndRequestPermissions()
        checkUpdatesSilently()
    }

    private fun applyTheme(theme: String?) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun checkAndRequestPermissions() {
        if (!PermissionHelper.hasUsageStatsPermission(this)) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
        }

        if (!PermissionHelper.hasOverlayPermission(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PermissionHelper.REQUEST_CODE_NOTIFICATIONS
                )
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return androidx.navigation.ui.NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private fun checkUpdatesSilently() {
        if (isUpdateCheckRunning) return
        
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val autoUpdateEnabled = prefs.getBoolean("auto_check_updates", true)

        if (!autoUpdateEnabled) return

        val lastCheckTime = prefs.getLong("last_update_check", 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastCheckTime < TWENTY_FOUR_HOURS_MS) return

        isUpdateCheckRunning = true

        updateCheckJob = lifecycleScope.launch {
            try {
                prefs.edit().putLong("last_update_check", currentTime).apply()

                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVersion = packageInfo.versionName

                val updateManager = UpdateManager()
                val updateInfo = updateManager.checkForUpdates(currentVersion)

                if (updateInfo != null && !isFinishing && !isDestroyed) {
                    showUpdateAvailableDialog(updateInfo)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking updates silently", e)
            } finally {
                isUpdateCheckRunning = false
                updateCheckJob = null
            }
        }
    }

    private fun showUpdateAvailableDialog(updateInfo: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_dialog_title)
            .setMessage(getString(R.string.update_dialog_message, updateInfo.versionName))
            .setPositiveButton(R.string.update_dialog_download) { _, _ ->
                appUpdater = AppUpdater(this)
                appUpdater?.downloadAndInstall(updateInfo.downloadUrl)
            }
            .setNegativeButton(R.string.update_dialog_later, null)
            .setOnCancelListener {
                isUpdateCheckRunning = false
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateCheckJob?.cancel()
        updateCheckJob = null
        appUpdater?.cleanup()
        appUpdater = null
    }
}