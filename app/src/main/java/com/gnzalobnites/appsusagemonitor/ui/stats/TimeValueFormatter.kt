package com.gnzalobnites.appsusagemonitor.ui.stats

import com.github.mikephil.charting.formatter.ValueFormatter
import com.gnzalobnites.appsusagemonitor.R
import java.util.concurrent.TimeUnit

class TimeValueFormatter : ValueFormatter() {
    
    override fun getFormattedValue(value: Float): String {
        val minutes = value.toLong()
        return when {
            minutes < 60 -> String.format("%d%s", minutes, "m")
            minutes < 1440 -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                String.format("%d%s %d%s", hours, "h", remainingMinutes, "m")
            }
            else -> {
                val days = minutes / 1440
                val remainingHours = (minutes % 1440) / 60
                String.format("%d%s %d%s", days, "d", remainingHours, "h")
            }
        }
    }
}
