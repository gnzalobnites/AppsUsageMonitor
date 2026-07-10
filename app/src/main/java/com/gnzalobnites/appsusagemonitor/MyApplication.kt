package com.gnzalobnites.appsusagemonitor

import android.app.Application
import androidx.room.Room
import com.gnzalobnites.appsusagemonitor.data.database.AppDatabase
import com.gnzalobnites.appsusagemonitor.data.repository.AppRepository
import com.gnzalobnites.appsusagemonitor.data.repository.UsageRepository

class MyApplication : Application() {
    
    companion object {
        lateinit var database: AppDatabase
            private set
        
        // Exponemos las instancias únicas (Singletons)
        lateinit var appRepository: AppRepository
            private set
            
        lateinit var usageRepository: UsageRepository
            private set
    }

    override fun onCreate() {
        super.onCreate()
        
        // Inicializamos todo una sola vez al arrancar la app
        database = AppDatabase.getInstance(this)
        appRepository = AppRepository(this)
        usageRepository = UsageRepository(this)
    }
}