package com.zplprinter.app.data.repository

import com.zplprinter.app.data.db.LabelDao
import com.zplprinter.app.data.db.LabelEntity
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.model.LabelElement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LabelRepository(private val dao: LabelDao) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observeLabels(): Flow<List<Label>> =
        dao.observeAll().map { entities -> entities.map(::toDomain) }

    suspend fun getLabel(id: String): Label? = dao.getById(id)?.let(::toDomain)

    suspend fun saveLabel(label: Label) = dao.upsert(toEntity(label))

    suspend fun deleteLabel(label: Label) = dao.delete(toEntity(label))

    private fun toDomain(entity: LabelEntity): Label = Label(
        id = entity.id,
        name = entity.name,
        widthMm = entity.widthMm,
        heightMm = entity.heightMm,
        dpi = entity.dpi,
        elements = json.decodeFromString<List<LabelElement>>(entity.elementsJson),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    private fun toEntity(label: Label): LabelEntity = LabelEntity(
        id = label.id,
        name = label.name,
        widthMm = label.widthMm,
        heightMm = label.heightMm,
        dpi = label.dpi,
        elementsJson = json.encodeToString(label.elements),
        createdAt = label.createdAt,
        updatedAt = label.updatedAt,
    )
}
