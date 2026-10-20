package com.zplprinter.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterDao {
    @Query("SELECT * FROM printer_profiles ORDER BY lastConnectedAt DESC LIMIT 1")
    suspend fun getLastUsed(): PrinterProfileEntity?

    @Query("SELECT * FROM printer_profiles ORDER BY lastConnectedAt DESC")
    fun observeAll(): Flow<List<PrinterProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PrinterProfileEntity)

    @Delete
    suspend fun delete(entity: PrinterProfileEntity)
}
