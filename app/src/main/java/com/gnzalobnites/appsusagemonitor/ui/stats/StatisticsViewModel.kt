package com.gnzalobnites.appsusagemonitor.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gnzalobnites.appsusagemonitor.data.database.DailyUsageStats
import com.gnzalobnites.appsusagemonitor.data.database.UsageSessionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class StatisticsViewModel(
    application: Application,
    private val usageSessionDao: UsageSessionDao
) : AndroidViewModel(application) {

    private val _selectedPackage = MutableStateFlow<String?>(null)
    private val _availableApps = MutableStateFlow<List<String>>(emptyList())
    val availableApps: StateFlow<List<String>> = _availableApps

    val weeklyStats: Flow<List<DailyUsageStats>> = _selectedPackage
        .filterNotNull()
        .flatMapLatest { packageName ->
            flow {
                emit(getStatsFromDatabase(packageName))
            }.flowOn(Dispatchers.IO)
        }

    init {
        loadAvailableApps()
    }

    private fun loadAvailableApps() {
        viewModelScope.launch {
            _availableApps.value = usageSessionDao.getAppsWithHistory()
            if (_selectedPackage.value == null && _availableApps.value.isNotEmpty()) {
                _selectedPackage.value = _availableApps.value[0]
            }
        }
    }

    fun selectApp(packageName: String) {
        _selectedPackage.value = packageName
    }

    private suspend fun getStatsFromDatabase(packageName: String): List<DailyUsageStats> {
        val result = mutableListOf<DailyUsageStats>()
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        for (i in 6 downTo 0) {
            val dayCalendar = Calendar.getInstance().apply {
                timeInMillis = calendar.timeInMillis
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val dayStart = dayCalendar.timeInMillis
            val dayEnd = dayStart + (24 * 60 * 60 * 1000)
            
            val totalForDay = try {
                usageSessionDao.getTotalUsageForDay(packageName, dayStart, dayEnd)
            } catch (e: Exception) {
                0L
            }
            
            result.add(DailyUsageStats(dayStart, totalForDay))
        }
        
        return result
    }

    val weekRangeLabel: Flow<String> = flow {
        val endDate = Date()
        
        val startCalendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate = Date(startCalendar.timeInMillis)

        val sdf = java.text.SimpleDateFormat(getApplication<Application>().getString(com.gnzalobnites.appsusagemonitor.R.string.week_range_format), Locale.getDefault())
        emit("${sdf.format(startDate)} - ${sdf.format(endDate)}")
    }
}