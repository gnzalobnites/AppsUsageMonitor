package com.gnzalobnites.appsusagemonitor.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log

data class UpdateInfo(val versionName: String, val downloadUrl: String)

class UpdateManager {

    companion object {
        private const val TAG = "UpdateManager"
    }

    /**
     * Comprueba en GitHub si hay una versión más reciente que la actual.
     * @param currentVersion Versión actual de la app (ej. "2.0.6")
     * @return UpdateInfo con los datos de la nueva versión si existe, null si ya está actualizada o hay error.
     */
     
     
     suspend fun checkForUpdates(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.github.com/repos/gnzalobnites/AppsUsageMonitor/releases/latest")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            // Verificar que el tag exista
            if (!json.has("tag_name")) {
                Log.e(TAG, "GitHub response missing tag_name")
                return@withContext null
            }
            
            val tagName = json.getString("tag_name")
            val cleanTagName = tagName.removePrefix("v")
            val cleanCurrent = currentVersion.removePrefix("v")

            if (isNewerVersion(cleanCurrent, cleanTagName)) {
                val assets = json.getJSONArray("assets")
                if (assets.length() > 0) {
                    val asset = assets.getJSONObject(0)
                    if (asset.has("browser_download_url")) {
                        val downloadUrl = asset.getString("browser_download_url")
                        return@withContext UpdateInfo(tagName, downloadUrl)
                    }
                }
            }
        } else {
            Log.e(TAG, "GitHub API error: ${connection.responseCode}")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error checking for updates", e)
    }
    return@withContext null
}
     
     
     
    /**
     * Compara dos versiones semánticas con manejo robusto de formatos.
     * @return true si fetched es más nueva que current.
     */
    private fun isNewerVersion(current: String, fetched: String): Boolean {
        // Normalizar versiones: eliminar sufijos como -beta, -alpha, etc.
        val normalizeVersion = { version: String ->
            version.split("-", "+").firstOrNull() ?: version
        }
        
        val currentNormalized = normalizeVersion(current)
        val fetchedNormalized = normalizeVersion(fetched)
        
        val currentParts = currentNormalized.split(".").map { 
            it.toIntOrNull() ?: 0 
        }
        val fetchedParts = fetchedNormalized.split(".").map { 
            it.toIntOrNull() ?: 0 
        }
        
        val maxLength = maxOf(currentParts.size, fetchedParts.size)
        for (i in 0 until maxLength) {
            val c = currentParts.getOrElse(i) { 0 }
            val f = fetchedParts.getOrElse(i) { 0 }
            if (f > c) return true
            if (f < c) return false
        }
        return false
    }
}