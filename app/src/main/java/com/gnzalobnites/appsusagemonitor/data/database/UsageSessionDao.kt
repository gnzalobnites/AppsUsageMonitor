package com.gnzalobnites.appsusagemonitor.data.database

import androidx.room.*
import com.gnzalobnites.appsusagemonitor.data.entities.UsageSession
import kotlinx.coroutines.flow.Flow

// Modelo de datos para estadísticas diarias
data class DailyUsageStats(
    val dayTimestamp: Long, // El timestamp del inicio del día
    val totalDuration: Long // Suma de duración en milisegundos
)

@Dao
interface UsageSessionDao {
    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName ORDER BY startTime DESC")
    fun getSessionsForApp(packageName: String): Flow<List<UsageSession>>

    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName AND startTime >= :startOfDay AND startTime < :endOfDay ORDER BY startTime DESC")
    suspend fun getSessionsForDay(packageName: String, startOfDay: Long, endOfDay: Long): List<UsageSession>

    @Query("SELECT * FROM usage_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): UsageSession?

    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName AND startTime >= :startOfDay")
    suspend fun getTodaySessions(packageName: String, startOfDay: Long): List<UsageSession>

    @Query("SELECT SUM(duration) FROM usage_sessions WHERE packageName = :packageName AND startTime >= :startOfDay")
    suspend fun getTotalUsageToday(packageName: String, startOfDay: Long): Long?

    @Insert
    suspend fun insert(session: UsageSession): Long

    @Update
    suspend fun update(session: UsageSession)

    // Versión original - CORREGIDA: requiere duration como parámetro
    @Query("UPDATE usage_sessions SET endTime = :endTime, duration = :duration WHERE id = :sessionId")
    suspend fun updateSessionEnd(sessionId: Long, endTime: Long, duration: Long)

    // Método mejorado que incluye sesiones que comenzaron antes pero terminaron durante el día
    @Query("""
        SELECT COALESCE(SUM(duration), 0) FROM usage_sessions 
        WHERE packageName = :packageName 
        AND (
            (startTime >= :startOfDay AND startTime < :endOfDay) OR
            (endTime >= :startOfDay AND endTime < :endOfDay) OR
            (startTime < :startOfDay AND endTime >= :endOfDay)
        )
    """)
    suspend fun getTotalUsageForDay(packageName: String, startOfDay: Long, endOfDay: Long): Long

    // Método adicional para limpiar sesiones antiguas (opcional, útil para mantenimiento)
    @Query("DELETE FROM usage_sessions WHERE endTime < :cutoffTime AND endTime IS NOT NULL")
    suspend fun deleteOldSessions(cutoffTime: Long)

    /**
     * Obtiene el uso diario de una app específica en un rango de tiempo.
     * La lógica de SQL divide el timestamp por los milisegundos de un día (86400000)
     * para agrupar todas las sesiones que pertenecen al mismo día.
     */
    @Query("""
        SELECT 
            (startTime / 86400000) * 86400000 AS dayTimestamp, 
            SUM(duration) AS totalDuration 
        FROM usage_sessions 
        WHERE packageName = :packageName 
          AND startTime >= :startTimeLimit 
          AND endTime IS NOT NULL
        GROUP BY dayTimestamp 
        ORDER BY dayTimestamp ASC
    """)
    fun getWeeklyStatsByApp(packageName: String, startTimeLimit: Long): Flow<List<DailyUsageStats>>

    /**
     * Obtiene una lista de los nombres de paquetes de apps que tienen registros
     * Útil para llenar el filtro/spinner de aplicaciones en la pantalla de estadísticas.
     */
    @Query("SELECT DISTINCT packageName FROM usage_sessions")
    suspend fun getAppsWithHistory(): List<String>

    /**
     * Obtiene las estadísticas de los últimos 7 días para una aplicación específica.
     * Similar a getWeeklyStatsByApp pero con límite de 7 días y como función suspend.
     */
    @Query("""
        SELECT 
            (startTime / 86400000) * 86400000 AS dayTimestamp, 
            SUM(duration) AS totalDuration 
        FROM usage_sessions 
        WHERE packageName = :packageName 
          AND startTime >= :startTimeLimit 
          AND endTime IS NOT NULL
        GROUP BY dayTimestamp 
        ORDER BY dayTimestamp ASC
        LIMIT 7
    """)
    suspend fun getLast7DaysStats(packageName: String, startTimeLimit: Long): List<DailyUsageStats>
}