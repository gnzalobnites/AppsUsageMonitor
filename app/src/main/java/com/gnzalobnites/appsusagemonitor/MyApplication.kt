package com.gnzalobnites.appsusagemonitor

import android.app.Application
import androidx.room.Room
import com.gnzalobnites.appsusagemonitor.data.database.AppDatabase

class MyApplication : Application() {
    
    companion object {
        lateinit var database: AppDatabase
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
    }
}
