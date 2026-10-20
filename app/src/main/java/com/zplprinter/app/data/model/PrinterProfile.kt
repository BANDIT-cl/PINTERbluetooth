package com.zplprinter.app.data.model

data class PrinterProfile(
    val macAddress: String,
    val name: String,
    val lastConnectedAt: Long,
)
