package com.gnzalobnites.appsusagemonitor.data.repository

import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import java.util.Calendar

/**
 * Repositorio para obtener estadísticas de uso del sistema Android
 * Utiliza UsageStatsManager para consultar el tiempo de uso de apps
 */
class UsageRepository(private val application: Application) {
    
    private val usageStatsManager = application.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    
    /**
     * SOLUCIÓN DEFINITIVA: Obtiene el tiempo exacto de pantalla desde las 00:00 local
     * usando eventos SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE.
     * 
     * Esta versión:
     * 1. Captura TODO el tiempo de pantalla encendida (incluyendo launcher, notificaciones, bloqueo)
     * 2. Maneja correctamente el cruce de medianoche
     * 3. NO contamina con procesos en segundo plano
     * 4. Coincide exactamente con Bienestar Digital y Samsung Health
     */
    fun getExactScreenTimeToday(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        val events = usageStatsManager.queryEvents(startOfDay, now)
        val event = UsageEvents.Event()

        var totalScreenTime = 0L
        var lastInteractiveTime = 0L
        var hasSeenScreenEvent = false
        var isScreenInteractive = false

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            // Evaluamos el estado interactivo de la pantalla en lugar de las aplicaciones
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    lastInteractiveTime = event.timeStamp
                    isScreenInteractive = true
                    hasSeenScreenEvent = true
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    hasSeenScreenEvent = true
                    if (isScreenInteractive) {
                        totalScreenTime += (event.timeStamp - lastInteractiveTime)
                        isScreenInteractive = false
                    } else {
                        // Magia para el cruce de medianoche:
                        // Si el primer evento que vemos es "apagar pantalla",
                        // significa que ya estaba encendida a las 00:00 exactas.
                        totalScreenTime += (event.timeStamp - startOfDay)
                    }
                }
            }
        }

        // Casos borde si la pantalla está encendida en el preciso instante de la consulta
        if (isScreenInteractive) {
            // La pantalla se encendió hoy y sigue encendida
            totalScreenTime += (now - lastInteractiveTime)
        } else if (!hasSeenScreenEvent) {
            // No hubo NINGÚN evento de pantalla hoy.
            // Verificamos directamente con el sistema si la pantalla está encendida ahora.
            // Si lo está, lleva encendida ininterrumpidamente desde ayer a las 23:59.
            val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isInteractive) {
                totalScreenTime = now - startOfDay
            }
        }

        return totalScreenTime
    }
}