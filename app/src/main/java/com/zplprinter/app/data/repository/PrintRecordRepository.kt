package com.zplprinter.app.data.repository

import com.zplprinter.app.data.db.PrintRecordDao
import com.zplprinter.app.data.db.PrintRecordEntity
import com.zplprinter.app.data.model.PrintRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PrintRecordRepository(private val dao: PrintRecordDao) {

    fun observeRecords(): Flow<List<PrintRecord>> =
        dao.observeAll().map { entities -> entities.map(::toDomain) }

    suspend fun record(description: String, printerName: String?, success: Boolean, errorMessage: String?) {
        dao.insert(
            PrintRecordEntity(
                description = description,
                printerName = printerName,
                success = success,
                errorMessage = errorMessage,
                printedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun clear() = dao.clear()

    private fun toDomain(entity: PrintRecordEntity): PrintRecord = PrintRecord(
        id = entity.id,
        description = entity.description,
        printerName = entity.printerName,
        success = entity.success,
        errorMessage = entity.errorMessage,
        printedAt = entity.printedAt,
    )
}
