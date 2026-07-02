package com.gnzalobnites.appsusagemonitor.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp

@Database(
    entities = [MonitoredApp::class],
    version = 2,  // Incrementamos la versión para que Room elimine las tablas viejas
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_usage_database"
                ).fallbackToDestructiveMigration() // Esto elimina las tablas viejas y crea las nuevas
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}