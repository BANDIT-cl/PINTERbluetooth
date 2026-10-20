package com.zplprinter.app.ui.labels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zplprinter.app.data.db.AppDatabase
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.repository.LabelRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Lista de etiquetas guardadas en Room, con duplicar y borrar. Abrir una etiqueta lo maneja la pantalla llamante (navega al diseñador con su id). */
class SavedLabelsViewModel(application: Application) : AndroidViewModel(application) {

    private val labelRepository = LabelRepository(AppDatabase.getInstance(application).labelDao())

    val labels: StateFlow<List<Label>> = labelRepository.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Copia la etiqueta con un id nuevo, para no pisar la original al editarla. */
    fun duplicate(label: Label) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            labelRepository.saveLabel(
                label.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${label.name} (copia)",
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    fun delete(label: Label) {
        viewModelScope.launch { labelRepository.deleteLabel(label) }
    }
}
