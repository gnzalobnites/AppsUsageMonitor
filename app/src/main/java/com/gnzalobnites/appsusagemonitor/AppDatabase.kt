package com.gnzalobnites.appsusagemonitor

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UsageSession::class],
    version = 2,  // Cambiado de 1 a 2
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usageDao(): UsageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migración de versión 1 a 2: agregar columna 'date'
        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Agregar columna 'date' 
                database.execSQL("""
                    ALTER TABLE usage_sessions 
                    ADD COLUMN date INTEGER NOT NULL DEFAULT 0
                """)
                
                // Actualizar registros existentes
                database.execSQL("""
                    UPDATE usage_sessions 
                    SET date = (
                        startTime - (startTime % (1000 * 60 * 60 * 24))
                    )
                """)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "usage_database"
                )
                .addMigrations(MIGRATION_1_2)  // Agregar migración
                .fallbackToDestructiveMigration()  // Para desarrollo
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}