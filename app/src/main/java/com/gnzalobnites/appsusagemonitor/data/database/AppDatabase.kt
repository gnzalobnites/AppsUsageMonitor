package com.gnzalobnites.appsusagemonitor.data.database

import android.app.Application
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp

@Database(
    entities = [MonitoredApp::class],
    version = 3, // Incrementar versión
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migración de versión 2 a 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Agregar nuevas columnas
                database.execSQL("ALTER TABLE monitored_apps ADD COLUMN timeGoalMinutes INTEGER NOT NULL DEFAULT 5")
                database.execSQL("ALTER TABLE monitored_apps ADD COLUMN currentSessionStart INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE monitored_apps ADD COLUMN currentSessionDuration INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE monitored_apps ADD COLUMN lastGoalNotified INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE monitored_apps ADD COLUMN lastGoalNotifiedTime INTEGER NOT NULL DEFAULT 0")
                
                // Migrar datos existentes: selectedInterval -> timeGoalMinutes
                database.execSQL("""
                    UPDATE monitored_apps 
                    SET timeGoalMinutes = CASE 
                        WHEN selectedInterval = 10000 THEN 1
                        WHEN selectedInterval = 60000 THEN 1
                        WHEN selectedInterval = 300000 THEN 5
                        WHEN selectedInterval = 900000 THEN 15
                        WHEN selectedInterval = 1800000 THEN 30
                        WHEN selectedInterval = 3600000 THEN 60
                        ELSE 5
                    END
                """)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            val appContext = context.applicationContext
                ?: throw IllegalStateException("ApplicationContext is null")

            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "app_usage_database"
                ).addMigrations(MIGRATION_2_3)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }

        fun getInstance(application: Application): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    application,
                    AppDatabase::class.java,
                    "app_usage_database"
                ).addMigrations(MIGRATION_2_3)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }
}