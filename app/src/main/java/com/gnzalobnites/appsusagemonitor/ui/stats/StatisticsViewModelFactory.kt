package com.gnzalobnites.appsusagemonitor.ui.stats

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gnzalobnites.appsusagemonitor.data.database.UsageSessionDao

class StatisticsViewModelFactory(
    private val application: Application,
    private val usageSessionDao: UsageSessionDao
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
            return StatisticsViewModel(application, usageSessionDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}