package com.zplprinter.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LabelEntity::class, PrinterProfileEntity::class, PrintRecordEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun labelDao(): LabelDao
    abstract fun printerDao(): PrinterDao
    abstract fun printRecordDao(): PrintRecordDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Agrega la tabla de historial de impresión sin tocar las etiquetas ni impresoras ya guardadas. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `print_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `description` TEXT NOT NULL,
                        `printerName` TEXT,
                        `success` INTEGER NOT NULL,
                        `errorMessage` TEXT,
                        `printedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zpl_label_printer.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
