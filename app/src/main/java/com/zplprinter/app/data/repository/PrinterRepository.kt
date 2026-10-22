package com.zplprinter.app.data.repository

import com.zplprinter.app.data.db.PrinterDao
import com.zplprinter.app.data.db.PrinterProfileEntity
import com.zplprinter.app.data.model.ConnectionType
import com.zplprinter.app.data.model.PrinterProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PrinterRepository(private val dao: PrinterDao) {

    suspend fun getLastUsed(): PrinterProfile? = dao.getLastUsed()?.toModel()

    /** Todas las impresoras a las que el usuario se conectó alguna vez, de la más reciente a la más antigua. */
    fun observeProfiles(): Flow<List<PrinterProfile>> =
        dao.observeAll().map { entities -> entities.map { it.toModel() } }

    suspend fun rememberConnected(address: String, name: String, connectionType: ConnectionType) {
        dao.upsert(PrinterProfileEntity(address, name, System.currentTimeMillis(), connectionType.name))
    }

    suspend fun forget(profile: PrinterProfile) {
        dao.delete(PrinterProfileEntity(profile.macAddress, profile.name, profile.lastConnectedAt, profile.connectionType.name))
    }

    private fun PrinterProfileEntity.toModel() = PrinterProfile(
        macAddress = macAddress,
        name = name,
        lastConnectedAt = lastConnectedAt,
        connectionType = runCatching { ConnectionType.valueOf(connectionType) }.getOrDefault(ConnectionType.BLUETOOTH),
    )
}
