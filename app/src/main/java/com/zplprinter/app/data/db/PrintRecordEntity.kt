package com.zplprinter.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "print_records")
data class PrintRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val printerName: String?,
    val success: Boolean,
    val errorMessage: String?,
    val printedAt: Long,
)
