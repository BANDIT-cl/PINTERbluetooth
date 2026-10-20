package com.zplprinter.app.data.repository

import com.zplprinter.app.data.db.PrinterDao
import com.zplprinter.app.data.db.PrinterProfileEntity
import com.zplprinter.app.data.model.PrinterProfile

class PrinterRepository(private val dao: PrinterDao) {

    suspend fun getLastUsed(): PrinterProfile? = dao.getLastUsed()?.let {
        PrinterProfile(it.macAddress, it.name, it.lastConnectedAt)
    }

    suspend fun rememberConnected(macAddress: String, name: String) {
        dao.upsert(PrinterProfileEntity(macAddress, name, System.currentTimeMillis()))
    }
}
