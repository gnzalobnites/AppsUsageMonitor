package com.gnzalobnites.appsusagemonitor.data.database

import android.app.Application
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp

@Database(
    entities = [MonitoredApp::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Obtiene la instancia de la base de datos usando el ApplicationContext
         * @param context Contexto de la aplicación (puede ser Activity, Service o Application)
         * @return Instancia de AppDatabase
         * @throws IllegalStateException si el ApplicationContext es null
         */
        fun getInstance(context: Context): AppDatabase {
            // Obtener applicationContext con null safety
            val appContext = context.applicationContext
                ?: throw IllegalStateException("ApplicationContext is null. " +
                    "Make sure you're passing a valid Context")

            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "app_usage_database"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Método alternativo para usar desde Application directamente
         * @param application Instancia de Application
         * @return Instancia de AppDatabase
         */
        fun getInstance(application: Application): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    application,
                    AppDatabase::class.java,
                    "app_usage_database"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Limpia la instancia de la base de datos (útil para pruebas)
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}