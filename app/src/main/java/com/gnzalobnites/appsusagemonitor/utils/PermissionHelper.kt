package com.gnzalobnites.appsusagemonitor.utils

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import java.util.Calendar  // <--- IMPORTANTE: Esta línea soluciona el error

object PermissionHelper {
    
    const val REQUEST_CODE_NOTIFICATIONS = 1001
    const val REQUEST_CODE_OVERLAY = 1002
    
    fun hasUsageStatsPermission(context: Context): Boolean {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 1000 * 60 * 60 * 24,
            currentTime
        )
        return stats != null && stats.isNotEmpty()
    }
    
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    fun hasAllPermissions(context: Context): Boolean {
        return hasUsageStatsPermission(context) && 
               hasOverlayPermission(context) && 
               hasNotificationPermission(context)
    }
    
    fun requestUsageStatsPermission(fragment: Fragment) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        fragment.startActivity(intent)
    }
    
    fun requestOverlayPermission(fragment: Fragment) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${fragment.requireContext().packageName}")
        )
        fragment.startActivityForResult(intent, REQUEST_CODE_OVERLAY)
    }
    
    fun requestNotificationPermission(activity: FragmentActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATIONS
                )
            }
        }
    }
    
    fun requestAllPermissions(fragment: Fragment) {
        if (!hasUsageStatsPermission(fragment.requireContext())) {
            requestUsageStatsPermission(fragment)
        }
        if (!hasOverlayPermission(fragment.requireContext())) {
            requestOverlayPermission(fragment)
        }
        if (!hasNotificationPermission(fragment.requireContext())) {
            fragment.activity?.let { requestNotificationPermission(it) }
        }
    }
    
    // Función para banners - Obtiene el uso total de hoy desde el sistema
    fun getTotalUsageTodayFromSystem(context: Context, packageName: String): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val start = calendar.timeInMillis
        val end = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        return stats.find { it.packageName == packageName }?.totalTimeInForeground ?: 0L
    }
}
