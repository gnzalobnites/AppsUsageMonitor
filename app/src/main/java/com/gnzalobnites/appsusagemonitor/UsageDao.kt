package com.gnzalobnites.appsusagemonitor

import androidx.room.*

@Dao
interface UsageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: UsageSession): Long

    @Update
    suspend fun update(session: UsageSession)

    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(packageName: String): UsageSession?
    
    @Query("DELETE FROM usage_sessions WHERE startTime < :timestamp")
    suspend fun deleteOldRecords(timestamp: Long)
    
    @Query("DELETE FROM usage_sessions WHERE endTime IS NULL")
    suspend fun deleteIncompleteSessions()

    // Obtener tiempo de UNA app específica hoy - ACTUALIZADO para usar columna 'date'
    @Query("""
        SELECT COALESCE(SUM(
            CASE 
                WHEN endTime IS NOT NULL THEN 
                    (endTime - startTime)
                ELSE 
                    CASE 
                        WHEN (:now - startTime) < 24 * 60 * 60 * 1000 THEN 
                            (:now - startTime)  -- Sesión activa hoy
                        ELSE 
                            0  -- Sesión muy vieja, ignorar
                    END
            END
        ), 0) 
        FROM usage_sessions 
        WHERE packageName = :packageName 
        AND date = :dateMillis
    """)
    suspend fun getAppTimeToday(packageName: String, dateMillis: Long, now: Long): Long
    
    // Obtener tiempo de UNA app específica hoy - VERSIÓN ALTERNATIVA CON todayMidnight
    @Query("""
        SELECT COALESCE(SUM(
            CASE 
                WHEN endTime IS NOT NULL THEN 
                    (endTime - startTime)
                ELSE 
                    CASE 
                        WHEN startTime >= :todayMidnight THEN 
                            (:currentTime - startTime)  -- Sesión activa hoy
                        ELSE 
                            0  -- Sesión vieja sin cerrar
                    END
        END
        ), 0) 
        FROM usage_sessions 
        WHERE packageName = :packageName 
        AND date = :todayMidnight
    """)
    suspend fun getAppTimeTodayWithMidnight(
        packageName: String, 
        todayMidnight: Long, 
        currentTime: Long
    ): Long

    // REEMPLAZADO: Obtener tiempo de TODAS las apps monitoreadas hoy - VERSIÓN MEJORADA
    @Query("""
        SELECT COALESCE(SUM(
            CASE 
                WHEN endTime IS NOT NULL THEN 
                    (endTime - startTime)
                ELSE 
                    CASE 
                        WHEN startTime >= :todayMidnight THEN 
                            (:currentTime - startTime)  -- Sesión activa hoy
                        ELSE 
                            0  -- Sesión vieja sin cerrar (de otro día)
                    END
            END
        ), 0) 
        FROM usage_sessions 
        WHERE packageName IN (:monitoredPackages)
        AND date = :todayMidnight
    """)
    suspend fun getTotalMonitoredTimeToday(
        monitoredPackages: List<String>, 
        todayMidnight: Long, 
        currentTime: Long
    ): Long

    // ALIAS para getAppTimeToday (mantener compatibilidad con código existente)
    suspend fun getCurrentAppTimeToday(packageName: String, dateMillis: Long, now: Long): Long {
        return getAppTimeToday(packageName, dateMillis, now)
    }

    // Consultas básicas existentes - ACTUALIZADAS
    @Query("SELECT * FROM usage_sessions WHERE endTime IS NULL")
    suspend fun getActiveSessions(): List<UsageSession>
    
    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestSessionForApp(packageName: String): UsageSession?
    
    // Obtener sesiones de hoy por fecha exacta
    @Query("SELECT * FROM usage_sessions WHERE date = :currentDate ORDER BY startTime DESC")
    suspend fun getTodaySessions(currentDate: Long): List<UsageSession>
    
    // Obtener todas las sesiones de apps monitoreadas hoy - ACTUALIZADO
    @Query("""
        SELECT * FROM usage_sessions 
        WHERE packageName IN (:monitoredPackages)
        AND date = :dateMillis
        ORDER BY startTime DESC
    """)
    suspend fun getTodayMonitoredSessions(monitoredPackages: List<String>, dateMillis: Long): List<UsageSession>
    
    // Verificar si hay datos en la base de datos (para debugging) - ACTUALIZADO
    @Query("SELECT COUNT(*) FROM usage_sessions WHERE date = :dateMillis")
    suspend fun getTodaySessionCount(dateMillis: Long): Int

    // ==================== MÉTODOS PARA EL BOTÓN "TIEMPOS" ====================

    // 1. Obtener sesiones entre dos fechas
    @Query("""
        SELECT * FROM usage_sessions 
        WHERE startTime >= :startTime AND startTime <= :endTime 
        ORDER BY startTime DESC
    """)
    suspend fun getSessionsBetween(startTime: Long, endTime: Long): List<UsageSession>

    // 2. Obtener total de registros en la BD
    @Query("SELECT COUNT(*) FROM usage_sessions")
    suspend fun getTotalRecordsCount(): Int

    // 3. Obtener fecha del registro más antiguo
    @Query("SELECT MIN(startTime) FROM usage_sessions")
    suspend fun getOldestRecordDate(): Long?

    // 4. Obtener fecha del registro más reciente
    @Query("SELECT MAX(startTime) FROM usage_sessions")
    suspend fun getNewestRecordDate(): Long?

    // 5. Obtener estadísticas por app para un día específico - ACTUALIZADO
    @Query("""
        SELECT 
            packageName,
            COUNT(*) as sessionCount,
            SUM(
                CASE 
                    WHEN endTime IS NOT NULL THEN 
                        (endTime - startTime)
                    ELSE 
                        CASE 
                            WHEN (:now - startTime) < 24 * 60 * 60 * 1000 THEN 
                                (:now - startTime)
                            ELSE 
                                0
                        END
                END
            ) as totalTime
        FROM usage_sessions 
        WHERE date = :dateMillis
        GROUP BY packageName
        ORDER BY totalTime DESC
    """)
    suspend fun getDailyStatsByApp(dateMillis: Long, now: Long): List<DailyAppStats>
    
    // ==================== CONSULTAS PARA VERIFICACIÓN DE FECHAS ====================
    
    // Verificar que las fechas se almacenan correctamente
    @Query("SELECT DISTINCT date FROM usage_sessions ORDER BY date DESC LIMIT 5")
    suspend fun getRecentDates(): List<Long>
    
    // RENOMBRADO: Obtener sesiones en un rango de fechas (usando columna 'date')
    @Query("""
        SELECT * FROM usage_sessions 
        WHERE date >= :todayMidnight AND date < :tomorrowMidnight
        ORDER BY startTime DESC
    """)
    suspend fun getSessionsInDateRange(todayMidnight: Long, tomorrowMidnight: Long): List<UsageSession>
    
    // 6. Obtener estadísticas por app para un rango de fechas - NUEVO
    @Query("""
        SELECT 
            packageName,
            COUNT(*) as sessionCount,
            SUM(
                CASE 
                    WHEN endTime IS NOT NULL THEN 
                        (endTime - startTime)
                    ELSE 
                        0
                END
            ) as totalTime
        FROM usage_sessions 
        WHERE date >= :startDate AND date <= :endDate
        GROUP BY packageName
        ORDER BY totalTime DESC
    """)
    suspend fun getStatsByAppInRange(startDate: Long, endDate: Long): List<DailyAppStats>
}

// Clase de datos para estadísticas diarias por app
data class DailyAppStats(
    val packageName: String,
    val sessionCount: Int,
    val totalTime: Long
)