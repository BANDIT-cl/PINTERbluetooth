package com.zplprinter.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PrinterDao {
    @Query("SELECT * FROM printer_profiles ORDER BY lastConnectedAt DESC LIMIT 1")
    suspend fun getLastUsed(): PrinterProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PrinterProfileEntity)
}
