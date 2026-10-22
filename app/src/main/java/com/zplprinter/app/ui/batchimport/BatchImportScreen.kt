package com.zplprinter.app.ui.batchimport

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zplprinter.app.data.model.PrinterLanguage
import com.zplprinter.app.ui.components.ZplTopAppBar
import com.zplprinter.app.ui.printer.BatchPrintItem
import com.zplprinter.app.zpl.PrintSettings

/**
 * Revisa los archivos recibidos de golpe (p. ej. varias etiquetas PDF seleccionadas juntas en
 * el gestor de archivos y compartidas a la app) antes de imprimirlos todos en secuencia.
 */
@Composable
fun BatchImportScreen(
    initialUris: List<Uri>,
    onInitialUrisConsumed: () -> Unit,
    widthMm: Float,
    heightMm: Float,
    dpi: Int,
    printSettings: PrintSettings,
    printerLanguage: PrinterLanguage,
    onBack: () -> Unit,
    onPrintBatch: (List<BatchPrintItem>, (Int, Boolean) -> Unit) -> Unit,
    viewModel: BatchImportViewModel = viewModel(),
) {
    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty()) {
            viewModel.load(initialUris)
            onInitialUrisConsumed()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val printableCount = uiState.items.count { it.kind != BatchItemKind.UNSUPPORTED }

    Scaffold(
        topBar = { ZplTopAppBar(title = "Imprimir varios archivos", onBack = onBack) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.items.isEmpty() -> Text("No hay archivos para imprimir.")
                else -> {
                    Text(
                        "${uiState.items.size} archivo(s). Se imprimen en el orden de la lista.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(uiState.items, key = { it.id }) { item ->
                            BatchItemRow(item = item, enabled = !uiState.isPrinting, onRemove = { viewModel.removeItem(item.id) })
                        }
                    }
                    Button(
                        onClick = { viewModel.printAll(widthMm, heightMm, dpi, printSettings, printerLanguage, onPrintBatch) },
                        enabled = !uiState.isPrinting && printableCount > 0,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        if (uiState.isPrinting) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        } else {
                            Text("Imprimir todo ($printableCount)")
                        }
                    }
                    if (uiState.allDone) {
                        val okCount = uiState.items.count { it.status == BatchItemStatus.OK }
                        Text(
                            "$okCount de ${uiState.items.size} impresas correctamente.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchItemRow(item: BatchImportItem, enabled: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = when (item.kind) {
                BatchItemKind.PDF -> Icons.Filled.PictureAsPdf
                BatchItemKind.RAW_ZPL -> Icons.Filled.Description
                BatchItemKind.UNSUPPORTED -> Icons.Filled.Error
            },
            contentDescription = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(item.displayName, maxLines = 1)
            Text(
                text = statusLabel(item),
                style = MaterialTheme.typography.bodySmall,
                color = when (item.status) {
                    BatchItemStatus.OK -> MaterialTheme.colorScheme.primary
                    BatchItemStatus.ERROR -> MaterialTheme.colorScheme.error
                    BatchItemStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        when (item.status) {
            BatchItemStatus.OK -> Icon(Icons.Filled.CheckCircle, contentDescription = "Impresa", tint = MaterialTheme.colorScheme.primary)
            BatchItemStatus.ERROR -> Icon(Icons.Filled.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
            BatchItemStatus.PENDING -> IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Filled.Close, contentDescription = "Quitar \"${item.displayName}\" de la lista")
            }
        }
    }
}

private fun statusLabel(item: BatchImportItem): String = when (item.status) {
    BatchItemStatus.PENDING -> if (item.kind == BatchItemKind.UNSUPPORTED) "Tipo no soportado, se omitirá" else "Pendiente"
    BatchItemStatus.OK -> "Impresa"
    BatchItemStatus.ERROR -> item.errorMessage ?: "Error al imprimir"
}
