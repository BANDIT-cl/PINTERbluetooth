package com.zplprinter.app.data.model

/** Cómo se llega a la impresora: Bluetooth clásico (SPP) o Wi-Fi (socket TCP directo, puerto 9100 habitual). */
enum class ConnectionType { BLUETOOTH, WIFI }

/**
 * [macAddress] es la dirección MAC para [ConnectionType.BLUETOOTH], o "ip:puerto"
 * (p. ej. "192.168.1.100:9100") para [ConnectionType.WIFI]. El nombre del campo se
 * mantiene por compatibilidad con la base de datos existente.
 */
data class PrinterProfile(
    val macAddress: String,
    val name: String,
    val lastConnectedAt: Long,
    val connectionType: ConnectionType = ConnectionType.BLUETOOTH,
)
