package com.zplprinter.app.data.model

/** Un intento de impresión (exitoso o no), para la pantalla "Historial de impresión". */
data class PrintRecord(
    val id: Long,
    val description: String,
    val printerName: String?,
    val success: Boolean,
    val errorMessage: String?,
    val printedAt: Long,
)
