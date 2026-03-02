package com.gnzalobnites.appsusagemonitor.ui.stats

import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.concurrent.TimeUnit

class TimeValueFormatter : ValueFormatter() {
    
    override fun getFormattedValue(value: Float): String {
        // Convertir minutos a formato legible
        val minutes = value.toLong()
        return when {
            minutes < 60 -> "${minutes}m"
            minutes < 1440 -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                "${hours}h ${remainingMinutes}m"
            }
            else -> {
                val days = minutes / 1440
                val remainingHours = (minutes % 1440) / 60
                "${days}d ${remainingHours}h"
            }
        }
    }
}
