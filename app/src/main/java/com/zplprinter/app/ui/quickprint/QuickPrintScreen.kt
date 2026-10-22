package com.zplprinter.app.ui.quickprint

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.zplprinter.app.data.model.PrinterLanguage
import com.zplprinter.app.image.MonoBitmap
import com.zplprinter.app.ui.components.ZplTopAppBar
import com.zplprinter.app.ui.designer.PhotoCropDialog
import com.zplprinter.app.zpl.LabelCommandGenerator
import com.zplprinter.app.zpl.PrintSettings
import com.zplprinter.app.zpl.ZplConstants

private enum class QuickPrintTab(val label: String, val icon: ImageVector) {
    TEXT("Texto", Icons.Filled.TextFields),
    IMAGE("Imagen", Icons.Filled.ImageIcon),
    QR("QR", Icons.Filled.QrCode),
}

/**
 * Atajos de impresión directa (como los de PrinterL): texto, imagen o QR, listos para imprimir
 * al tamaño del papel cargado sin pasar por el diseñador completo de etiquetas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPrintScreen(
    widthMm: Float,
    heightMm: Float,
    dpi: Int,
    printSettings: PrintSettings,
    printerLanguage: PrinterLanguage,
    isPrinting: Boolean,
    lastPrintOk: Boolean?,
    printerErrorMessage: String?,
    onBack: () -> Unit,
    onPrint: (command: String, description: String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(QuickPrintTab.TEXT) }

    Scaffold(
        topBar = { ZplTopAppBar(title = "Impresión rápida", onBack = onBack) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                QuickPrintTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                    )
                }
            }

            when (selectedTab) {
                QuickPrintTab.TEXT -> TextQuickPrintTab(widthMm, heightMm, dpi, printSettings, printerLanguage, isPrinting, lastPrintOk, printerErrorMessage, onPrint)
                QuickPrintTab.IMAGE -> ImageQuickPrintTab(widthMm, heightMm, dpi, printSettings, printerLanguage, isPrinting, lastPrintOk, printerErrorMessage, onPrint)
                QuickPrintTab.QR -> QrQuickPrintTab(widthMm, heightMm, dpi, printSettings, printerLanguage, isPrinting, lastPrintOk, printerErrorMessage, onPrint)
            }
        }
    }
}

@Composable
private fun TextQuickPrintTab(
    widthMm: Float,
    heightMm: Float,
    dpi: Int,
    printSettings: PrintSettings,
    printerLanguage: PrinterLanguage,
    isPrinting: Boolean,
    lastPrintOk: Boolean?,
    printerErrorMessage: String?,
    onPrint: (String, String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var fontHeightMm by rememberSaveable { mutableFloatStateOf(8f) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Escribe el texto: se imprime ajustado al ancho del papel actual (${widthMm.toInt()}x${heightMm.toInt()}mm), sin abrir el diseñador.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Texto") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )
        Text("Tamaño de letra: ${fontHeightMm.toInt()} mm")
        Slider(value = fontHeightMm, onValueChange = { fontHeightMm = it }, valueRange = 3f..30f)

        Button(
            onClick = {
                val command = LabelCommandGenerator.buildTextLabel(text, widthMm, heightMm, dpi, fontHeightMm, printSettings, printerLanguage)
                onPrint(command, text.take(40).ifBlank { "Texto" })
            },
            enabled = !isPrinting && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isPrinting) "Imprimiendo…" else "Imprimir") }

        PrintResultText(lastPrintOk, printerErrorMessage)
    }
}

@Composable
private fun ImageQuickPrintTab(
    widthMm: Float,
    heightMm: Float,
    dpi: Int,
    printSettings: PrintSettings,
    printerLanguage: PrinterLanguage,
    isPrinting: Boolean,
    lastPrintOk: Boolean?,
    printerErrorMessage: String?,
    onPrint: (String, String) -> Unit,
) {
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var pendingZpl by remember { mutableStateOf<String?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingUri = uri
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Elige una foto: se recorta, se convierte a blanco y negro y se imprime ajustada al papel actual (${widthMm.toInt()}x${heightMm.toInt()}mm), sin abrir el diseñador.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { pickLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text("Elegir foto")
        }

        preview?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Vista previa",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(widthMm / heightMm)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
            )
        }

        Button(
            onClick = { pendingZpl?.let { onPrint(it, "Imagen") } },
            enabled = !isPrinting && pendingZpl != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isPrinting) "Imprimiendo…" else "Imprimir") }

        PrintResultText(lastPrintOk, printerErrorMessage)
    }

    pendingUri?.let { uri ->
        PhotoCropDialog(
            uri = uri,
            onConfirm = { cropped ->
                val targetWidthDots = ZplConstants.mmToDots(widthMm, dpi)
                val targetHeightDots = ZplConstants.mmToDots(heightMm, dpi)
                val fitted = MonoBitmap.fitOnWhiteCanvas(cropped, targetWidthDots, targetHeightDots)
                preview = fitted
                pendingZpl = LabelCommandGenerator.buildImageLabel(targetWidthDots, targetHeightDots, MonoBitmap.pack(fitted), printSettings, printerLanguage)
                pendingUri = null
            },
            onCancel = { pendingUri = null },
        )
    }
}

@Composable
private fun QrQuickPrintTab(
    widthMm: Float,
    heightMm: Float,
    dpi: Int,
    printSettings: PrintSettings,
    printerLanguage: PrinterLanguage,
    isPrinting: Boolean,
    lastPrintOk: Boolean?,
    printerErrorMessage: String?,
    onPrint: (String, String) -> Unit,
) {
    var data by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Escribe el contenido (link, texto, lo que sea): se imprime como QR al tamaño del papel actual, sin abrir el diseñador.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = data,
            onValueChange = { data = it },
            label = { Text("Contenido del QR") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (data.isNotBlank()) {
            val qrPreview = remember(data) { runCatching { generateQrPreview(data) }.getOrNull() }
            qrPreview?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Vista previa del QR",
                    modifier = Modifier.size(180.dp).align(Alignment.CenterHorizontally),
                )
            }
        }

        Button(
            onClick = {
                val command = LabelCommandGenerator.buildQrLabel(data, widthMm, heightMm, dpi, printSettings, printerLanguage)
                onPrint(command, data.take(40).ifBlank { "QR" })
            },
            enabled = !isPrinting && data.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isPrinting) "Imprimiendo…" else "Imprimir") }

        PrintResultText(lastPrintOk, printerErrorMessage)
    }
}

@Composable
private fun PrintResultText(lastPrintOk: Boolean?, printerErrorMessage: String?) {
    lastPrintOk?.let { ok ->
        Text(
            if (ok) "Impresión enviada." else (printerErrorMessage ?: "Error al imprimir"),
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

private fun generateQrPreview(data: String, sizePx: Int = 300): Bitmap {
    val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx)
    return BarcodeEncoder().createBitmap(matrix)
}
