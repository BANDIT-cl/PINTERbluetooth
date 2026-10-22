package com.zplprinter.app.ui.batchimport

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zplprinter.app.data.model.PrinterLanguage
import com.zplprinter.app.pdf.PdfLabelConverter
import com.zplprinter.app.ui.printer.BatchPrintItem
import com.zplprinter.app.zpl.LabelCommandGenerator
import com.zplprinter.app.zpl.PrintSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class BatchItemKind { PDF, RAW_ZPL, UNSUPPORTED }
enum class BatchItemStatus { PENDING, OK, ERROR }

data class BatchImportItem(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val kind: BatchItemKind,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val errorMessage: String? = null,
)

data class BatchImportUiState(
    val items: List<BatchImportItem> = emptyList(),
    val isLoading: Boolean = true,
    val isPrinting: Boolean = false,
    val allDone: Boolean = false,
)

/**
 * Prepara e imprime varios archivos recibidos a la vez (p. ej. varias etiquetas PDF de
 * MercadoLibre seleccionadas juntas en el gestor de archivos). Cada item se clasifica por su
 * tipo real consultado por Uri -no por el tipo genérico del Intent.ACTION_SEND_MULTIPLE, que
 * suele ser "*​/​*" cuando los archivos son de tipos distintos- y se puede sacar de la lista
 * antes de imprimir. Un PDF se convierte a imagen igual que "Importar etiqueta PDF"; cualquier
 * otro archivo (.zpl, .prn, .txt) se envía tal cual como ZPL crudo, igual que al compartir un
 * solo archivo hoy.
 */
class BatchImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BatchImportUiState())
    val uiState: StateFlow<BatchImportUiState> = _uiState.asStateFlow()

    fun load(uris: List<Uri>) {
        _uiState.value = BatchImportUiState(isLoading = true)
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { uris.map(::classify) }
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun removeItem(id: String) {
        _uiState.update { it.copy(items = it.items.filterNot { item -> item.id == id }) }
    }

    /**
     * Convierte cada item restante (ver [prepare]) y los imprime en secuencia vía [onPrintBatch]
     * -en la práctica, [com.zplprinter.app.ui.printer.PrinterViewModel.printBatch]-. La
     * conversión de PDF ocurre acá (puede tardar un poco); el envío y la reconexión a la
     * impresora quedan a cargo del PrinterViewModel, dueño de la única conexión activa.
     */
    fun printAll(
        widthMm: Float,
        heightMm: Float,
        dpi: Int,
        printSettings: PrintSettings,
        language: PrinterLanguage,
        onPrintBatch: (List<BatchPrintItem>, (Int, Boolean) -> Unit) -> Unit,
    ) {
        val pending = _uiState.value.items.filter { it.kind != BatchItemKind.UNSUPPORTED }
        if (pending.isEmpty()) return

        _uiState.update { it.copy(isPrinting = true) }

        viewModelScope.launch {
            val prepared: List<Pair<BatchImportItem, BatchPrintItem?>> = withContext(Dispatchers.Default) {
                pending.map { item -> item to prepare(item, widthMm, heightMm, dpi, printSettings, language) }
            }

            // Los que no se pudieron leer/convertir quedan en error sin pasar a imprimir.
            _uiState.update { state ->
                state.copy(items = state.items.map { item ->
                    val match = prepared.firstOrNull { it.first.id == item.id }
                    if (match != null && match.second == null) {
                        item.copy(status = BatchItemStatus.ERROR, errorMessage = "No se pudo leer el archivo")
                    } else item
                })
            }

            val printable = prepared.mapNotNull { (item, printItem) -> printItem?.let { item to it } }
            if (printable.isEmpty()) {
                _uiState.update { it.copy(isPrinting = false, allDone = true) }
                return@launch
            }

            onPrintBatch(printable.map { it.second }) { index, ok ->
                val resolvedItem = printable[index].first
                _uiState.update { state ->
                    state.copy(items = state.items.map { current ->
                        if (current.id == resolvedItem.id) {
                            current.copy(status = if (ok) BatchItemStatus.OK else BatchItemStatus.ERROR)
                        } else current
                    })
                }
                if (index == printable.lastIndex) {
                    _uiState.update { it.copy(isPrinting = false, allDone = true) }
                }
            }
        }
    }

    private fun prepare(
        item: BatchImportItem,
        widthMm: Float,
        heightMm: Float,
        dpi: Int,
        printSettings: PrintSettings,
        language: PrinterLanguage,
    ): BatchPrintItem? = when (item.kind) {
        BatchItemKind.PDF -> runCatching {
            val images = PdfLabelConverter.convert(getApplication(), item.uri, widthMm, heightMm, dpi)
            val command = images.joinToString(separator = "") { image ->
                LabelCommandGenerator.buildImageLabel(image.widthDots, image.heightDots, image.packedMono, printSettings, language)
            }
            BatchPrintItem.GeneratedCommand(command, item.displayName)
        }.getOrNull()
        BatchItemKind.RAW_ZPL -> runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(item.uri)?.bufferedReader()?.use { it.readText() }
            text?.takeIf { it.isNotBlank() }?.let { BatchPrintItem.RawZpl(it, item.displayName) }
        }.getOrNull()
        BatchItemKind.UNSUPPORTED -> null
    }

    private fun classify(uri: Uri): BatchImportItem {
        val context = getApplication<Application>()
        val name = displayNameFor(uri) ?: uri.lastPathSegment ?: "Archivo"
        val kind = if (context.contentResolver.getType(uri) == "application/pdf") BatchItemKind.PDF else BatchItemKind.RAW_ZPL
        return BatchImportItem(id = UUID.randomUUID().toString(), uri = uri, displayName = name, kind = kind)
    }

    private fun displayNameFor(uri: Uri): String? {
        val context = getApplication<Application>()
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }
}
