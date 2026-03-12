package com.gnzalobnites.appsusagemonitor.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionName: String, val downloadUrl: String)

class UpdateManager {

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

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val tagName = json.getString("tag_name") // ej: "v2.0.6"
                
                // Limpiamos la "v" para comparar
                val cleanTagName = tagName.removePrefix("v")
                val cleanCurrent = currentVersion.removePrefix("v")

                if (isNewerVersion(cleanCurrent, cleanTagName)) {
                    val assets = json.getJSONArray("assets")
                    if (assets.length() > 0) {
                        // Tomamos el primer asset (que debería ser el .apk)
                        val downloadUrl = assets.getJSONObject(0).getString("browser_download_url")
                        return@withContext UpdateInfo(tagName, downloadUrl)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * Compara dos versiones semánticas.
     * @return true si fetched es más nueva que current.
     */
    private fun isNewerVersion(current: String, fetched: String): Boolean {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val fetchedParts = fetched.split(".").map { it.toIntOrNull() ?: 0 }
        
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
