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

    fun downloadAndInstall(url: String, fileName: String = "AppsUsageMonitor-update.apk") {
        // Limpiar cualquier receiver anterior
        cleanup()
        
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(context.getString(R.string.update_download_title))
            setDescription(context.getString(R.string.update_download_description))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        downloadCompleteReceiver = onDownloadComplete(fileName)
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(downloadCompleteReceiver, filter)
            }
            isReceiverRegistered = true
            Toast.makeText(appContext, R.string.update_download_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error registering receiver", e)
            downloadCompleteReceiver = null
            isReceiverRegistered = false
        }
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

    fun cleanup() {
        if (isReceiverRegistered) {
            downloadCompleteReceiver?.let {
                try {
                    appContext.unregisterReceiver(it)
                    Log.d(TAG, "Receiver unregistered successfully")
                } catch (e: IllegalArgumentException) {
                    Log.d(TAG, "Receiver was already unregistered")
                }
                downloadCompleteReceiver = null
                isReceiverRegistered = false
            }
        }
    }
}