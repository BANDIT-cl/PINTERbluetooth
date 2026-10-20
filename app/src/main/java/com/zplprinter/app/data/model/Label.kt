package com.zplprinter.app.data.model

import com.zplprinter.app.zpl.ZplConstants
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Label(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val widthMm: Float,
    val heightMm: Float,
    val dpi: Int = ZplConstants.DEFAULT_DPI,
    val elements: List<LabelElement> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
