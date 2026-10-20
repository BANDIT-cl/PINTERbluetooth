package com.zplprinter.app.ui.pdfimport

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zplprinter.app.pdf.PdfLabelConverter
import com.zplprinter.app.pdf.PdfLabelImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PdfImportUiState(
    val isLoading: Boolean = false,
    val images: List<PdfLabelImage> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Convierte un PDF (una o varias páginas; una etiqueta de envío suele venir con espacio en
 * blanco alrededor) en imágenes monocromas recortadas y redimensionadas al tamaño de papel
 * configurado. No imprime: la impresión la maneja la pantalla llamante reutilizando la
 * conexión Bluetooth ya activa de [com.zplprinter.app.ui.printer.PrinterViewModel], vía
 * [com.zplprinter.app.zpl.ZplGenerator.buildImageLabel].
 */
class PdfImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PdfImportUiState())
    val uiState: StateFlow<PdfImportUiState> = _uiState.asStateFlow()

    fun load(uri: Uri, widthMm: Float, heightMm: Float, dpi: Int) {
        _uiState.value = PdfImportUiState(isLoading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { PdfLabelConverter.convert(getApplication(), uri, widthMm, heightMm, dpi) }
            }
            _uiState.value = result.fold(
                onSuccess = { images -> PdfImportUiState(images = images) },
                onFailure = { error -> PdfImportUiState(errorMessage = error.message ?: "No se pudo leer el PDF") },
            )
        }
    }
}
