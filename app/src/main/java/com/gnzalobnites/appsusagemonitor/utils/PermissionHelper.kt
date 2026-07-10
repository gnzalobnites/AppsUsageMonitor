package com.gnzalobnites.appsusagemonitor.utils

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.gnzalobnites.appsusagemonitor.R
import java.util.Calendar

/**
 * Helper para gestionar permisos de forma secuencial con diálogos explicativos.
 * Evita lanzar múltiples intents de configuración simultáneamente.
 */
object PermissionHelper {

    const val REQUEST_CODE_NOTIFICATIONS = 1001

    // ---------- Checks individuales ----------

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

    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasAllPermissions(context: Context): Boolean =
        hasUsageStatsPermission(context) &&
                hasOverlayPermission(context) &&
                hasNotificationPermission(context)

    // ---------- Máquina de estados secuencial ----------

    private enum class PermissionStep { USAGE_STATS, OVERLAY, NOTIFICATIONS }

    private var awaitingStep: PermissionStep? = null
    private var onFlowFinished: (() -> Unit)? = null

    /**
     * Punto de entrada único para pedir permisos, con explicación previa a cada uno.
     * Se puede llamar tanto desde MainActivity (onCreate) como desde el botón
     * "Configurar permisos" de cualquier Fragment (con `requireActivity()`).
     *
     * @param onFinished callback opcional que se dispara cuando la cadena termina
     *                   (todos otorgados o el usuario cerró el último diálogo).
     */
    fun requestAllPermissions(activity: FragmentActivity, onFinished: (() -> Unit)? = null) {
        onFlowFinished = onFinished
        advance(activity)
    }

    /**
     * Debe llamarse desde MainActivity.onResume(). Si estábamos esperando que el
     * usuario resolviera un permiso en Ajustes, revisa si ya lo otorgó y recién
     * ahí sigue con el próximo paso. Si no lo otorgó, no insiste solo: espera a
     * que el usuario vuelva a intentarlo manualmente.
     */
    fun onActivityResumed(activity: FragmentActivity) {
        val step = awaitingStep ?: return
        if (isStepGranted(activity, step)) {
            awaitingStep = null
            advance(activity)
        }
    }

    /** Debe llamarse desde MainActivity.onRequestPermissionsResult(). */
    fun onRequestPermissionsResult(requestCode: Int) {
        if (requestCode == REQUEST_CODE_NOTIFICATIONS) {
            awaitingStep = null
            finishFlow()
        }
    }

    private fun isStepGranted(context: Context, step: PermissionStep): Boolean = when (step) {
        PermissionStep.USAGE_STATS -> hasUsageStatsPermission(context)
        PermissionStep.OVERLAY -> hasOverlayPermission(context)
        PermissionStep.NOTIFICATIONS -> hasNotificationPermission(context)
    }

    private fun advance(activity: FragmentActivity) {
        when {
            !hasUsageStatsPermission(activity) -> requestUsageStats(activity)
            !hasOverlayPermission(activity) -> requestOverlay(activity)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(activity) ->
                requestNotifications(activity)
            else -> finishFlow()
        }
    }

    private fun finishFlow() {
        onFlowFinished?.invoke()
        onFlowFinished = null
    }

    private fun requestUsageStats(activity: FragmentActivity) {
        awaitingStep = PermissionStep.USAGE_STATS
        AlertDialog.Builder(activity)
            .setTitle(R.string.permission_usage_stats_required)
            .setMessage(
                activity.getString(
                    R.string.permission_usage_stats_instructions,
                    activity.getString(R.string.app_name)
                )
            )
            .setCancelable(false)
            .setPositiveButton(R.string.configure_permissions) { _, _ ->
                activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton(R.string.update_dialog_later) { _, _ ->
                awaitingStep = null
                advance(activity) // este queda pendiente, seguimos con el resto
            }
            .show()
    }

    private fun requestOverlay(activity: FragmentActivity) {
        awaitingStep = PermissionStep.OVERLAY
        AlertDialog.Builder(activity)
            .setTitle(R.string.permission_overlay_required)
            .setMessage(R.string.permission_overlay_instructions)
            .setCancelable(false)
            .setPositiveButton(R.string.configure_permissions) { _, _ ->
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
            }
            .setNegativeButton(R.string.update_dialog_later) { _, _ ->
                awaitingStep = null
                advance(activity)
            }
            .show()
    }

    private fun requestNotifications(activity: FragmentActivity) {
        awaitingStep = PermissionStep.NOTIFICATIONS
        AlertDialog.Builder(activity)
            .setTitle(R.string.permission_notifications_required)
            .setMessage(R.string.permission_notifications_instructions)
            .setPositiveButton(R.string.configure_permissions) { _, _ ->
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATIONS
                )
            }
            .setNegativeButton(R.string.update_dialog_later) { _, _ ->
                awaitingStep = null
                finishFlow()
            }
            .show()
    }

    // ---------- Funciones auxiliares (sin cambios) ----------

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
        return stats?.find { it.packageName == packageName }?.totalTimeInForeground ?: 0L
    }
}