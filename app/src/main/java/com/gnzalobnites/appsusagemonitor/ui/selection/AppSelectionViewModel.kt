package com.gnzalobnites.appsusagemonitor.ui.selection

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import com.gnzalobnites.appsusagemonitor.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?,
    var isSelected: Boolean = false
)

class AppSelectionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = AppRepository(application)
    private val packageManager = application.packageManager
    
    private val _installedApps = MutableLiveData<List<AppInfo>>()
    val installedApps: LiveData<List<AppInfo>> = _installedApps
    
    private val _filteredApps = MutableLiveData<List<AppInfo>>()
    val filteredApps: LiveData<List<AppInfo>> = _filteredApps
    
    private val _selectedApps = MutableLiveData<Int>(0)
    val selectedApps: LiveData<Int> = _selectedApps
    
    val monitoredApps: LiveData<List<MonitoredApp>> = repository.getMonitoredApps().asLiveData()
    
    private var allApps: List<AppInfo> = listOf()
    
    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = packageManager.queryIntentActivities(intent, 0)
                
                resolveInfos.map { resolveInfo ->
                    AppInfo(
                        packageName = resolveInfo.activityInfo.packageName,
                        appName = resolveInfo.loadLabel(packageManager).toString(),
                        icon = resolveInfo.loadIcon(packageManager)
                    )
                }.sortedBy { it.appName }
            }
            allApps = apps
            _installedApps.value = apps
            _filteredApps.value = apps
        }
    }
    
    fun filterApps(query: String) {
        if (query.isEmpty()) {
            _filteredApps.value = allApps
        } else {
            val filtered = allApps.filter { 
                it.appName.contains(query, ignoreCase = true) 
            }
            _filteredApps.value = filtered
        }
    }
    
    fun toggleAppSelection(appInfo: AppInfo) {
        val index = allApps.indexOfFirst { it.packageName == appInfo.packageName }
        if (index != -1) {
            allApps[index].isSelected = !allApps[index].isSelected
            
            val filteredList = _filteredApps.value?.toMutableList()
            filteredList?.find { it.packageName == appInfo.packageName }?.isSelected = allApps[index].isSelected
            
            _filteredApps.value = filteredList ?: emptyList()
            
            val selectedCount = allApps.count { it.isSelected }
            _selectedApps.value = selectedCount
        }
    }
    
    fun addSelectedAppsToMonitor(interval: Long) {
        viewModelScope.launch {
            allApps.filter { it.isSelected }.forEach { appInfo ->
                repository.addAppToMonitor(appInfo.packageName, interval)
            }
            clearSelection()
        }
    }
    
    private fun clearSelection() {
        allApps.forEach { it.isSelected = false }
        _filteredApps.value = allApps
        _selectedApps.value = 0
    }
    
    fun removeMonitoredApp(app: MonitoredApp) {
        viewModelScope.launch {
            repository.deleteMonitoredApp(app)
        }
    }
    
    fun formatInterval(interval: Long, context: android.content.Context): String {
        return when (interval) {
            com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_10_SECONDS -> 
                context.getString(com.gnzalobnites.appsusagemonitor.R.string.interval_every_10_seconds)
            com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_1_MINUTE -> 
                context.getString(com.gnzalobnites.appsusagemonitor.R.string.interval_every_1_minute)
            com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_5_MINUTES -> 
                context.getString(com.gnzalobnites.appsusagemonitor.R.string.interval_every_5_minutes)
            com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_15_MINUTES -> 
                context.getString(com.gnzalobnites.appsusagemonitor.R.string.interval_every_15_minutes)
            com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_30_MINUTES -> 
                context.getString(com.gnzalobnites.appsusagemonitor.R.string.interval_every_30_minutes)
            com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_1_HOUR -> 
                context.getString(com.gnzalobnites.appsusagemonitor.R.string.interval_every_1_hour)
            else -> context.getString(com.gnzalobnites.appsusagemonitor.R.string.interval_custom)
        }
    }
}