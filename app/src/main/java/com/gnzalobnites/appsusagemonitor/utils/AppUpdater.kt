package com.gnzalobnites.appsusagemonitor.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.gnzalobnites.appsusagemonitor.R
import java.io.File

class AppUpdater(private val context: Context) {

    private val appContext = context.applicationContext
    private var downloadId: Long = -1
    private var downloadCompleteReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    companion object {
        private const val TAG = "AppUpdater"
    }

    /**
     * Inicia la descarga del APK desde la URL proporcionada.
     * @param url URL de descarga del APK.
     * @param fileName Nombre con el que se guardará el archivo.
     */
    fun downloadAndInstall(url: String, fileName: String = "AppsUsageMonitor-update.apk") {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(context.getString(R.string.update_download_title))
            setDescription(context.getString(R.string.update_download_description))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        // Registrar el receiver usando appContext para evitar memory leaks
        if (!isReceiverRegistered) {
            downloadCompleteReceiver = onDownloadComplete(fileName)
            appContext.registerReceiver(
                downloadCompleteReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        }
        
        Toast.makeText(appContext, R.string.update_download_started, Toast.LENGTH_SHORT).show()
    }

    private fun onDownloadComplete(fileName: String) = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == id) {
                installApk(appContext, fileName)
                cleanup()
            }
        }
    }

    private fun installApk(context: Context, fileName: String) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (!file.exists()) {
            Toast.makeText(context, R.string.update_file_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        if (installIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(installIntent)
        } else {
            Toast.makeText(context, R.string.update_no_installer, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Limpia el receiver si la actividad/fragmento se destruye antes de que termine la descarga.
     */
    fun cleanup() {
        if (isReceiverRegistered) {
            downloadCompleteReceiver?.let {
                try {
                    appContext.unregisterReceiver(it)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Receiver already unregistered", e)
                }
                downloadCompleteReceiver = null
                isReceiverRegistered = false
            }
        }
    }
}