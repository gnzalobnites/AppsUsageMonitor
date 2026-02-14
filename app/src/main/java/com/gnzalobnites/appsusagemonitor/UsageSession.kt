// UsageSession.kt
package com.gnzalobnites.appsusagemonitor

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

/**
 * Representa una sesión de uso de una aplicación.
 *
 * - startTime: momento en que el usuario entró a la app (en milisegundos desde Epoch).
 * - endTime: momento en que salió (null si la sesión está activa).
 * - packageName: identificador único de la app (ej. "com.instagram.android").
 * - date: fecha del día (solo fecha, sin hora) para facilitar consultas diarias.
 */
@Entity(tableName = "usage_sessions")
data class UsageSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val date: Long // Se calculará en los constructores
) {
    companion object {
        /**
         * Calcula la fecha (sin hora) a partir de un timestamp
         */
        fun calculateDateFromTimestamp(timestamp: Long): Long {
            return Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
    
    /**
     * Constructor principal que calcula la fecha basada en startTime
     */
    constructor(
        packageName: String,
        startTime: Long,
        endTime: Long? = null
    ) : this(
        id = 0,
        packageName = packageName,
        startTime = startTime,
        endTime = endTime,
        date = calculateDateFromTimestamp(startTime)
    )
    
    /**
     * Constructor para migración de datos existentes
     */
    constructor(
        id: Long,
        packageName: String,
        startTime: Long,
        endTime: Long? = null
    ) : this(
        id = id,
        packageName = packageName,
        startTime = startTime,
        endTime = endTime,
        date = calculateDateFromTimestamp(startTime)
    )
}
