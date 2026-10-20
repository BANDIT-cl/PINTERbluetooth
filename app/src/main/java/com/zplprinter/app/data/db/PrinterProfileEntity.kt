package com.zplprinter.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "printer_profiles")
data class PrinterProfileEntity(
    @PrimaryKey val macAddress: String,
    val name: String,
    val lastConnectedAt: Long,
)
