package com.zplprinter.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val widthMm: Float,
    val heightMm: Float,
    val dpi: Int,
    val elementsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)
