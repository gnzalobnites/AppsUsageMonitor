package com.gnzalobnites.appsusagemonitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class UsageRepository(private val database: AppDatabase) {
    
    companion object {
        @Volatile
        private var INSTANCE: UsageRepository? = null
        
        fun getInstance(database: AppDatabase): UsageRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UsageRepository(database).also { INSTANCE = it }
            }
        }
    }
    
    // Obtener sesiones por rango de tiempo
    suspend fun getSessionsBetween(startTime: Long, endTime: Long): List<UsageSession> {
        return withContext(Dispatchers.IO) {
            database.usageDao().getSessionsBetween(startTime, endTime)
        }
    }
    
    // Obtener estadísticas diarias
    suspend fun getDailyStats(): Map<String, Long> {
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            
            val sessions = database.usageDao().getSessionsBetween(startOfDay, System.currentTimeMillis())
            
            val stats = mutableMapOf<String, Long>()
            sessions.groupBy { it.packageName }.forEach { (packageName, sessionList) ->
                val totalDuration = sessionList.map { session ->
                    val end = session.endTime ?: System.currentTimeMillis()
                    end - session.startTime
                }.sum()
                stats[packageName] = totalDuration
            }
            stats
        }
    }
    
    // Método alternativo más eficiente - CORREGIDO
    suspend fun getDailyStatsOptimized(): Map<String, Long> {
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayDate = calendar.timeInMillis
            val now = System.currentTimeMillis()  // ← AGREGADO
            
            val dailyStats = database.usageDao().getDailyStatsByApp(todayDate, now)  // ← AHORA CON 2 PARÁMETROS
            
            dailyStats.associate { it.packageName to it.totalTime }
        }
    }
    
    // Obtener total de registros
    suspend fun getTotalRecords(): Int {
        return withContext(Dispatchers.IO) {
            database.usageDao().getTotalRecordsCount()
        }
    }
    
    // Insertar nueva sesión
    suspend fun insertSession(session: UsageSession) {
        withContext(Dispatchers.IO) {
            database.usageDao().insert(session)
        }
    }
    
    // Actualizar sesión existente
    suspend fun updateSession(session: UsageSession) {
        withContext(Dispatchers.IO) {
            database.usageDao().update(session)
        }
    }
    
    // Obtener sesión activa
    suspend fun getActiveSession(packageName: String): UsageSession? {
        return withContext(Dispatchers.IO) {
            database.usageDao().getActiveSession(packageName)
        }
    }
    
    // Obtener sesiones activas
    suspend fun getActiveSessions(): List<UsageSession> {
        return withContext(Dispatchers.IO) {
            database.usageDao().getActiveSessions()
        }
    }
    
    // Limpiar registros antiguos (más de 30 días)
    suspend fun deleteOldRecords() {
        withContext(Dispatchers.IO) {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            database.usageDao().deleteOldRecords(thirtyDaysAgo)
        }
    }
    
    // Limpiar sesiones incompletas
    suspend fun deleteIncompleteSessions() {
        withContext(Dispatchers.IO) {
            database.usageDao().deleteIncompleteSessions()
        }
    }
    
    // Obtener sesiones de hoy - YA CORREGIDO (solo un parámetro)
    suspend fun getTodaySessions(): List<UsageSession> {
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayDate = calendar.timeInMillis
            
            database.usageDao().getTodaySessions(todayDate) // ← Correcto: solo un parámetro
        }
    }
    
    // Obtener estadísticas de BD para debugging
    suspend fun getDatabaseStats(): DatabaseStats {
        return withContext(Dispatchers.IO) {
            val totalRecords = database.usageDao().getTotalRecordsCount()
            val oldestRecord = database.usageDao().getOldestRecordDate()
            val newestRecord = database.usageDao().getNewestRecordDate()
            
            DatabaseStats(totalRecords, oldestRecord, newestRecord)
        }
    }
	
	// En UsageRepository, agregar estos métodos:

// 1. Método para tiempo total de apps monitoreadas hoy
suspend fun getTotalMonitoredTimeToday(monitoredPackages: List<String>): Long {
    return withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayMidnight = calendar.timeInMillis
        val currentTime = System.currentTimeMillis()
        
        database.usageDao().getTotalMonitoredTimeToday(monitoredPackages, todayMidnight, currentTime)
    }
}

// 2. Método para tiempo de una app específica hoy
suspend fun getAppTimeToday(packageName: String): Long {
    return withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayDate = calendar.timeInMillis
        val now = System.currentTimeMillis()
        
        database.usageDao().getAppTimeToday(packageName, todayDate, now)
    }
}

// 3. Método para sesiones monitoreadas hoy
suspend fun getTodayMonitoredSessions(monitoredPackages: List<String>): List<UsageSession> {
    return withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayDate = calendar.timeInMillis
        
        database.usageDao().getTodayMonitoredSessions(monitoredPackages, todayDate)
    }
}
	
	
}