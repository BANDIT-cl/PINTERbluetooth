package com.zplprinter.app.ui.pdfimport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zplprinter.app.ui.components.ZplTopAppBar
import com.zplprinter.app.zpl.PrintSettings
import com.zplprinter.app.zpl.ZplGenerator

/**
 * Importa un PDF (p. ej. una etiqueta de envío de MercadoLibre, normalmente 100x150mm) y lo
 * recorta/redimensiona automáticamente al papel cargado en la impresora (p. ej. 100x100mm),
 * conservando la proporción para no deformar el código de barras ni el QR.
 */
@Composable
fun PdfImportScreen(
    initialUri: Uri?,
    onInitialUriConsumed: () -> Unit,
    widthMm: Float,
    heightMm: Float,
    dpi: Int,
    printSettings: PrintSettings,
    isPrinting: Boolean,
    lastPrintOk: Boolean?,
    printerErrorMessage: String?,
    onBack: () -> Unit,
    onPrint: (String) -> Unit,
    viewModel: PdfImportViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val pickPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.load(it, widthMm, heightMm, dpi) }
    }

    LaunchedEffect(initialUri) {
        initialUri?.let { uri ->
            viewModel.load(uri, widthMm, heightMm, dpi)
            onInitialUriConsumed()
        }
    }

    Scaffold(
        topBar = { ZplTopAppBar(title = "Importar etiqueta PDF", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Elegí el PDF de la etiqueta (p. ej. la de MercadoLibre). Se recorta y " +
                    "redimensiona automáticamente, manteniendo la proporción, a tu papel actual " +
                    "de ${widthMm.toInt()}x${heightMm.toInt()}mm.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedButton(
                onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Elegir PDF")
            }

            when {
                uiState.isLoading -> CircularProgressIndicator()

                uiState.errorMessage != null -> Text(
                    uiState.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                )

                uiState.images.isNotEmpty() -> {
                    val images = uiState.images
                    val first = images.first()

                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .aspectRatio(first.widthDots / first.heightDots.toFloat())
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Image(
                            bitmap = first.preview.asImageBitmap(),
                            contentDescription = "Vista previa de la etiqueta",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    if (images.size > 1) {
                        Text(
                            "El PDF tiene ${images.size} páginas: se van a imprimir ${images.size} etiquetas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = {
                            val zpl = images.joinToString(separator = "") { image ->
                                ZplGenerator.buildImageLabel(
                                    image.widthDots,
                                    image.heightDots,
                                    image.packedMono,
                                    printSettings,
                                )
                            }
                            onPrint(zpl)
                        },
                        enabled = !isPrinting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isPrinting) "Imprimiendo…" else "Imprimir")
                    }

                    lastPrintOk?.let { ok ->
                        Text(
                            if (ok) "Impresión enviada" else (printerErrorMessage ?: "Error al imprimir"),
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
