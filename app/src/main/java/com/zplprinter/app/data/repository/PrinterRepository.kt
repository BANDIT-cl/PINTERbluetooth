package com.zplprinter.app.data.repository

import com.zplprinter.app.data.db.PrinterDao
import com.zplprinter.app.data.db.PrinterProfileEntity
import com.zplprinter.app.data.model.PrinterProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PrinterRepository(private val dao: PrinterDao) {

    suspend fun getLastUsed(): PrinterProfile? = dao.getLastUsed()?.let {
        PrinterProfile(it.macAddress, it.name, it.lastConnectedAt)
    }

    /** Todas las impresoras a las que el usuario se conectó alguna vez, de la más reciente a la más antigua. */
    fun observeProfiles(): Flow<List<PrinterProfile>> =
        dao.observeAll().map { entities -> entities.map { PrinterProfile(it.macAddress, it.name, it.lastConnectedAt) } }

    suspend fun rememberConnected(macAddress: String, name: String) {
        dao.upsert(PrinterProfileEntity(macAddress, name, System.currentTimeMillis()))
    }

    suspend fun forget(profile: PrinterProfile) {
        dao.delete(PrinterProfileEntity(profile.macAddress, profile.name, profile.lastConnectedAt))
    }
}
