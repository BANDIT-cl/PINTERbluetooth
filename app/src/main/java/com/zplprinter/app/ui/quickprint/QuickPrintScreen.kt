package com.zplprinter.app.ui.quickprint

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
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
    var selectedWidthMm by rememberSaveable { mutableFloatStateOf(widthMm) }
    var selectedHeightMm by rememberSaveable { mutableFloatStateOf(heightMm) }

    Scaffold(
        topBar = { ZplTopAppBar(title = "Impresión rápida", onBack = onBack) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp)) {
                QuickPrintSizePicker(
                    widthMm = selectedWidthMm,
                    heightMm = selectedHeightMm,
                    onSizeSelected = { w, h -> selectedWidthMm = w; selectedHeightMm = h },
                )
            }

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
                QuickPrintTab.TEXT -> TextQuickPrintTab(selectedWidthMm, selectedHeightMm, dpi, printSettings, printerLanguage, isPrinting, lastPrintOk, printerErrorMessage, onPrint)
                QuickPrintTab.IMAGE -> ImageQuickPrintTab(selectedWidthMm, selectedHeightMm, dpi, printSettings, printerLanguage, isPrinting, lastPrintOk, printerErrorMessage, onPrint)
                QuickPrintTab.QR -> QrQuickPrintTab(selectedWidthMm, selectedHeightMm, dpi, printSettings, printerLanguage, isPrinting, lastPrintOk, printerErrorMessage, onPrint)
            }
        }
    }
}

private const val QUICK_PRINT_MANUAL_OPTION_LABEL = "Personalizado..."

/** Selector de tamaño de etiqueta propio de Impresión rápida: parte del tamaño de papel configurado pero permite cambiarlo solo para esta impresión. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickPrintSizePicker(
    widthMm: Float,
    heightMm: Float,
    onSizeSelected: (Float, Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val matchingPreset = remember(widthMm, heightMm) {
        ZplConstants.PRESETS.firstOrNull { it.widthMm == widthMm && it.heightMm == heightMm }
    }
    var isManual by rememberSaveable { mutableStateOf(matchingPreset == null) }
    var manualWidthText by rememberSaveable { mutableStateOf(widthMm.toInt().toString()) }
    var manualHeightText by rememberSaveable { mutableStateOf(heightMm.toInt().toString()) }

    val currentLabel = matchingPreset?.label
        ?: if (isManual) QUICK_PRINT_MANUAL_OPTION_LABEL else "${widthMm.toInt()} x ${heightMm.toInt()} mm"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Tamaño de esta etiqueta") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ZplConstants.PRESETS.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.label) },
                        onClick = {
                            isManual = false
                            onSizeSelected(preset.widthMm, preset.heightMm)
                            expanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(QUICK_PRINT_MANUAL_OPTION_LABEL) },
                    onClick = {
                        isManual = true
                        expanded = false
                    }
                )
            }
        }

        if (isManual) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = manualWidthText,
                    onValueChange = { manualWidthText = it.filter(Char::isDigit) },
                    label = { Text("Ancho (mm)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = manualHeightText,
                    onValueChange = { manualHeightText = it.filter(Char::isDigit) },
                    label = { Text("Alto (mm)") },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedButton(
                onClick = {
                    val width = manualWidthText.toFloatOrNull()
                    val height = manualHeightText.toFloatOrNull()
                    if (width != null && width > 0 && height != null && height > 0) {
                        onSizeSelected(width, height)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Aplicar tamaño")
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
            "Escribe el texto: se imprime ajustado al tamaño elegido arriba (${widthMm.toInt()}x${heightMm.toInt()}mm), sin abrir el diseñador.",
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

        Text("Vista previa de la etiqueta", style = MaterialTheme.typography.labelMedium)
        LabelPreviewBox(widthMm = widthMm, heightMm = heightMm) {
            val fontSizeSp = with(LocalDensity.current) {
                (maxHeight * (fontHeightMm / heightMm).coerceIn(0.05f, 1f)).toSp()
            }
            Text(
                text = text.ifBlank { "Texto" },
                color = Color.Black,
                fontSize = fontSizeSp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp),
            )
        }

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
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageScale by rememberSaveable { mutableFloatStateOf(1f) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingUri = uri
    }

    val targetWidthDots = remember(widthMm, dpi) { ZplConstants.mmToDots(widthMm, dpi) }
    val targetHeightDots = remember(heightMm, dpi) { ZplConstants.mmToDots(heightMm, dpi) }
    val preview = remember(croppedBitmap, imageScale, targetWidthDots, targetHeightDots) {
        croppedBitmap?.let { MonoBitmap.fitOnWhiteCanvas(it, targetWidthDots, targetHeightDots, imageScale) }
    }
    val pendingZpl = remember(preview, printSettings, printerLanguage) {
        preview?.let { LabelCommandGenerator.buildImageLabel(targetWidthDots, targetHeightDots, MonoBitmap.pack(it), printSettings, printerLanguage) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Elige una foto: se recorta, se convierte a blanco y negro y se imprime ajustada al tamaño elegido arriba (${widthMm.toInt()}x${heightMm.toInt()}mm), sin abrir el diseñador.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { pickLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text("Elegir foto")
        }

        if (croppedBitmap != null) {
            Text("Tamaño de la imagen: ${(imageScale * 100).toInt()}%")
            Slider(value = imageScale, onValueChange = { imageScale = it }, valueRange = 0.2f..1f)
        }

        preview?.let { bitmap ->
            Text("Vista previa de la etiqueta", style = MaterialTheme.typography.labelMedium)
            LabelPreviewBox(widthMm = widthMm, heightMm = heightMm) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Vista previa",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                croppedBitmap = cropped
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
    var qrScale by rememberSaveable { mutableFloatStateOf(1f) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Escribe el contenido (link, texto, lo que sea): se imprime como QR al tamaño elegido arriba (${widthMm.toInt()}x${heightMm.toInt()}mm), sin abrir el diseñador.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = data,
            onValueChange = { data = it },
            label = { Text("Contenido del QR") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Tamaño del QR: ${(qrScale * 100).toInt()}%")
        Slider(value = qrScale, onValueChange = { qrScale = it }, valueRange = 0.2f..1f)

        Text("Vista previa de la etiqueta", style = MaterialTheme.typography.labelMedium)
        LabelPreviewBox(widthMm = widthMm, heightMm = heightMm) {
            if (data.isNotBlank()) {
                val qrPreview = remember(data) { runCatching { generateQrPreview(data) }.getOrNull() }
                qrPreview?.let { bitmap ->
                    val qrSize = minOf(maxWidth, maxHeight) * 0.7f * qrScale
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Vista previa del QR",
                        modifier = Modifier.size(qrSize),
                    )
                }
            }
        }

        Button(
            onClick = {
                val command = LabelCommandGenerator.buildQrLabel(data, widthMm, heightMm, dpi, printSettings, printerLanguage, qrScale)
                onPrint(command, data.take(40).ifBlank { "QR" })
            },
            enabled = !isPrinting && data.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isPrinting) "Imprimiendo…" else "Imprimir") }

        PrintResultText(lastPrintOk, printerErrorMessage)
    }
}

/** Simula el papel: rectángulo blanco con la proporción real de la etiqueta, para previsualizar antes de imprimir. */
@Composable
private fun LabelPreviewBox(
    widthMm: Float,
    heightMm: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(widthMm / heightMm)
            .clipToBounds()
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
        content = content,
    )
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
