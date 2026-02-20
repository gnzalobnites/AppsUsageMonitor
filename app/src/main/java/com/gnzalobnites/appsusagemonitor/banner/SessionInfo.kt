package com.gnzalobnites.appsusagemonitor.banner

/**
 * Información de la sesión actual
 */
data class SessionInfo(
    val packageName: String,
    val startTime: Long,
    val appName: String = ""
) {
    fun getDuration(): Long = System.currentTimeMillis() - startTime
}

/**
 * Estadísticas de tiempo
 */
data class TimeStats(
    val sessionTime: Long,
    val todayTotal: Long,
    val formattedSessionTime: String = formatTime(sessionTime),
    val formattedTodayTotal: String = formatTime(todayTotal)
) {
    companion object {
        fun formatTime(milliseconds: Long): String {
            val totalSeconds = milliseconds / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}