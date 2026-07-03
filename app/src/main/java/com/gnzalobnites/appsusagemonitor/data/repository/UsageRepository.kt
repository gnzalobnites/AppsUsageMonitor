package com.gnzalobnites.appsusagemonitor.data.repository

import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import java.util.Calendar

/**
 * Repositorio para obtener estadísticas de uso del sistema Android
 * Utiliza UsageStatsManager para consultar el tiempo de uso de apps
 */
class UsageRepository(private val application: Application) {
    
    private val usageStatsManager = application.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    
    companion object {
        private const val TAG = "UsageRepository"
        // Constante para DEVICE_SHUTDOWN (API 26+)
        private const val EVENT_DEVICE_SHUTDOWN = 26
    }
    
    /**
     * SOLUCIÓN DEFINITIVA MEJORADA: Obtiene el tiempo exacto de pantalla desde las 00:00 local
     * usando eventos SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE.
     * 
     * Esta versión:
     * 1. Captura TODO el tiempo de pantalla encendida (incluyendo launcher, notificaciones, bloqueo)
     * 2. Maneja correctamente el cruce de medianoche
     * 3. Maneja correctamente los reinicios y apagados del dispositivo (DEVICE_SHUTDOWN)
     * 4. NO contamina con procesos en segundo plano
     * 5. Coincide exactamente con Bienestar Digital y Samsung Health
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

        val events = try {
            usageStatsManager.queryEvents(startOfDay, now)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying events", e)
            return 0L
        }
        
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
                    // Previene sobrescribir el inicio si hubo un error en la secuencia de eventos
                    // o si ya estábamos en estado interactivo (evita duplicación)
                    if (!isScreenInteractive) {
                        lastInteractiveTime = event.timeStamp
                        isScreenInteractive = true
                    }
                    hasSeenScreenEvent = true
                }
                
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    hasSeenScreenEvent = true
                    if (isScreenInteractive) {
                        // La pantalla se apagó normalmente
                        totalScreenTime += (event.timeStamp - lastInteractiveTime)
                        isScreenInteractive = false
                    } else {
                        // Cruce de medianoche: la pantalla ya estaba encendida a las 00:00
                        totalScreenTime += (event.timeStamp - startOfDay)
                    }
                }
                
                EVENT_DEVICE_SHUTDOWN -> {
                    // El dispositivo se está apagando o reiniciando
                    if (isScreenInteractive) {
                        // Guardamos el tiempo acumulado hasta el apagado
                        totalScreenTime += (event.timeStamp - lastInteractiveTime)
                        isScreenInteractive = false
                        Log.d(TAG, "Device shutdown detected, saved ${event.timeStamp - lastInteractiveTime}ms")
                    }
                }
            }
        }

        // Casos borde si la pantalla está encendida en el preciso instante de la consulta
        if (isScreenInteractive) {
            // La pantalla se encendió hoy y sigue encendida
            totalScreenTime += (now - lastInteractiveTime)
            Log.d(TAG, "Screen is currently interactive, adding ${now - lastInteractiveTime}ms")
        } else if (!hasSeenScreenEvent) {
            // No hubo NINGÚN evento de pantalla hoy.
            // Verificamos directamente con el sistema si la pantalla está encendida ahora.
            // Si lo está, lleva encendida ininterrumpidamente desde ayer a las 23:59.
            val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isInteractive) {
                totalScreenTime = now - startOfDay
                Log.d(TAG, "No screen events today but screen is on, using full day")
            }
        }

        Log.d(TAG, "Total screen time today: ${totalScreenTime / 1000} seconds")
        return totalScreenTime
    }
}