package com.zplprinter.app.ui.printer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zplprinter.app.data.model.PrinterLanguage
import com.zplprinter.app.ui.components.ValueRow
import com.zplprinter.app.ui.components.ZplTopAppBar
import com.zplprinter.app.zpl.MediaType
import com.zplprinter.app.zpl.ZplConstants

/** Ajustes de impresión como lista plana de filas expandibles, al estilo de InterfaceSettingActivity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSettingsScreen(
    widthMm: Float,
    heightMm: Float,
    dpi: Int,
    mediaType: MediaType,
    printSpeed: Int,
    darkness: Int,
    printerLanguage: PrinterLanguage,
    onBack: () -> Unit,
    onSizeSelected: (Float, Float) -> Unit,
    onPrintSettingsChange: (MediaType, Int, Int) -> Unit,
    onPrinterLanguageSelected: (PrinterLanguage) -> Unit,
) {
    var expandedRow by rememberSaveable { mutableStateOf<String?>(null) }
    fun toggle(key: String) {
        expandedRow = if (expandedRow == key) null else key
    }

    Scaffold(
        topBar = { ZplTopAppBar(title = "Ajustes de impresión", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ValueRow(
                title = "Tamaño de papel",
                value = "${widthMm.toInt()} x ${heightMm.toInt()} mm ($dpi dpi)",
                expanded = expandedRow == "size",
                onClick = { toggle("size") },
            ) {
                PaperSizeContent(widthMm = widthMm, heightMm = heightMm, onSizeSelected = onSizeSelected)
            }

            ValueRow(
                title = "Tipo de papel",
                value = mediaType.displayName,
                expanded = expandedRow == "media",
                onClick = { toggle("media") },
            ) {
                MediaTypeContent(
                    selected = mediaType,
                    onSelected = { onPrintSettingsChange(it, printSpeed, darkness) },
                )
            }

            ValueRow(
                title = "Lenguaje de impresora",
                value = printerLanguage.displayName,
                expanded = expandedRow == "language",
                onClick = { toggle("language") },
            ) {
                PrinterLanguageContent(selected = printerLanguage, onSelected = onPrinterLanguageSelected)
            }

            ValueRow(
                title = "Velocidad de impresión",
                value = "$printSpeed ips",
                expanded = expandedRow == "speed",
                onClick = { toggle("speed") },
            ) {
                Slider(
                    value = printSpeed.toFloat(),
                    onValueChange = { onPrintSettingsChange(mediaType, it.toInt(), darkness) },
                    valueRange = 1f..14f,
                    steps = 12,
                )
            }

            ValueRow(
                title = "Oscuridad",
                value = "$darkness",
                expanded = expandedRow == "darkness",
                onClick = { toggle("darkness") },
            ) {
                Slider(
                    value = darkness.toFloat(),
                    onValueChange = { onPrintSettingsChange(mediaType, printSpeed, it.toInt()) },
                    valueRange = 0f..30f,
                    steps = 29,
                )
            }
        }
    }
}

private const val MANUAL_OPTION_LABEL = "Personalizado..."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaperSizeContent(
    widthMm: Float,
    heightMm: Float,
    onSizeSelected: (Float, Float) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val matchingPreset = remember(widthMm, heightMm) {
        ZplConstants.PRESETS.firstOrNull { it.widthMm == widthMm && it.heightMm == heightMm }
    }
    var isManual by rememberSaveable { mutableStateOf(matchingPreset == null) }
    var manualWidthText by rememberSaveable { mutableStateOf(widthMm.toInt().toString()) }
    var manualHeightText by rememberSaveable { mutableStateOf(heightMm.toInt().toString()) }

    val currentLabel = matchingPreset?.label
        ?: if (isManual) MANUAL_OPTION_LABEL else "${widthMm.toInt()} x ${heightMm.toInt()} mm"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Tamaño de papel") },
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
                    text = { Text(MANUAL_OPTION_LABEL) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterLanguageContent(selected: PrinterLanguage, onSelected: (PrinterLanguage) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Lenguaje de impresora") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PrinterLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
    if (selected == PrinterLanguage.TSPL) {
        Text(
            "TSPL es experimental: no se probó todavía contra una impresora TSC real.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaTypeContent(selected: MediaType, onSelected: (MediaType) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo de papel") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MediaType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
