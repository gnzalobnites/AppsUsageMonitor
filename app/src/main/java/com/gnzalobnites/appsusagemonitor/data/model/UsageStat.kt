package com.gnzalobnites.appsusagemonitor.data.model

/**
 * Modelo para representar estadísticas de uso de una app
 * Utilizado en el gráfico de MainFragment
 */
data class UsageStat(
    val packageName: String,
    val appName: String,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long
)