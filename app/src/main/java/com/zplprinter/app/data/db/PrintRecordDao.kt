package com.zplprinter.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintRecordDao {
    @Query("SELECT * FROM print_records ORDER BY printedAt DESC")
    fun observeAll(): Flow<List<PrintRecordEntity>>

    @Insert
    suspend fun insert(entity: PrintRecordEntity)

    @Query("DELETE FROM print_records")
    suspend fun clear()
}
