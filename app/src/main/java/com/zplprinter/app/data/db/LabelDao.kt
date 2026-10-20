package com.zplprinter.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels WHERE id = :id")
    suspend fun getById(id: String): LabelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LabelEntity)

    @Delete
    suspend fun delete(entity: LabelEntity)
}
