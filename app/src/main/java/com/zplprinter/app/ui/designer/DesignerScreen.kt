package com.zplprinter.app.ui.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zplprinter.app.ui.components.ZplTopAppBar
import com.zplprinter.app.data.model.BarcodeSymbology
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.model.LabelElement
import com.zplprinter.app.data.model.TextDataType
import com.zplprinter.app.zpl.MediaType
import com.zplprinter.app.zpl.PrintSettings
import com.zplprinter.app.zpl.ZplGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignerScreen(
    initialWidthMm: Float,
    initialHeightMm: Float,
    initialDpi: Int,
    mediaType: MediaType,
    printSpeed: Int,
    darkness: Int,
    onBack: () -> Unit,
    onPrint: (String) -> Unit,
    viewModel: DesignerViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.initializeSizeIfNeeded(initialWidthMm, initialHeightMm, initialDpi) }

    val label by viewModel.label.collectAsState()
    val selectedId by viewModel.selectedElementId.collectAsState()
    val selectedElement = label.elements.firstOrNull { it.id == selectedId }
    var copies by rememberSaveable { mutableStateOf(1) }

    Scaffold(
        topBar = {
            ZplTopAppBar(
                title = "Diseñador de etiquetas",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            val settings = PrintSettings(
                                copies = copies,
                                printSpeed = printSpeed,
                                darkness = darkness,
                                mediaType = mediaType,
                            )
                            onPrint(ZplGenerator.generate(label, settings))
                        }
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = "Imprimir")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ToolbarRow(
                onAddText = viewModel::addText,
                onAddBarcode = viewModel::addBarcode,
                onAddQr = viewModel::addQr,
                onAddLine = viewModel::addLine,
            )

            CopiesRow(copies = copies, onCopiesChange = { copies = it.coerceIn(1, 99) })

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                LabelCanvas(
                    label = label,
                    selectedId = selectedId,
                    onSelect = viewModel::selectElement,
                    onMove = viewModel::moveElement,
                    onResize = viewModel::resizeElement,
                )
            }

            if (selectedElement != null) {
                PropertyPanel(element = selectedElement, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ToolbarRow(
    onAddText: () -> Unit,
    onAddBarcode: () -> Unit,
    onAddQr: () -> Unit,
    onAddLine: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onAddText) { Text("+ Texto") }
        OutlinedButton(onClick = onAddBarcode) { Text("+ Código") }
        OutlinedButton(onClick = onAddQr) { Text("+ QR") }
        OutlinedButton(onClick = onAddLine) { Text("+ Línea") }
    }
}

@Composable
private fun CopiesRow(copies: Int, onCopiesChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Copias:", modifier = Modifier.padding(top = 12.dp))
        IconButton(onClick = { onCopiesChange(copies - 1) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Menos copias")
        }
        Text(copies.toString(), modifier = Modifier.padding(top = 12.dp))
        IconButton(onClick = { onCopiesChange(copies + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Más copias")
        }
    }
}

@Composable
private fun LabelCanvas(
    label: Label,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, Float, Float) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val scalePxPerMm = maxWidthPx / label.widthMm
        val canvasHeightDp = with(density) { (scalePxPerMm * label.heightMm).toDp() }

        Box(
            modifier = Modifier
                .width(maxWidth)
                .height(canvasHeightDp)
                .background(Color.White)
                .border(1.dp, Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onSelect(null) })
                }
        ) {
            label.elements.forEach { element ->
                ElementView(
                    element = element,
                    scalePxPerMm = scalePxPerMm,
                    isSelected = element.id == selectedId,
                    onSelect = { onSelect(element.id) },
                    onMove = { dxMm, dyMm -> onMove(element.id, element.xMm + dxMm, element.yMm + dyMm) },
                    onResize = { dWidthMm, dHeightMm -> onResize(element.id, dWidthMm, dHeightMm) },
                )
            }
        }
    }
}

/** Tamaño visual aproximado (mm) de un elemento, usado para dibujar su caja y ubicar el handle de resize. */
private fun elementSizeMm(element: LabelElement): Pair<Float, Float> = when (element) {
    is LabelElement.TextElement ->
        (element.fontWidthMm * element.text.length.coerceAtLeast(1) * 0.6f) to element.fontHeightMm
    is LabelElement.BarcodeElement ->
        (element.data.length.coerceAtLeast(4) * element.moduleWidthDots) to element.heightMm
    is LabelElement.QrElement ->
        (element.magnification * 5f) to (element.magnification * 5f)
    is LabelElement.ShapeElement ->
        element.widthMm to element.thicknessMm.coerceAtLeast(1f)
}

@Composable
private fun ElementView(
    element: LabelElement,
    scalePxPerMm: Float,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val xDp = with(density) { (element.xMm * scalePxPerMm).toDp() }
    val yDp = with(density) { (element.yMm * scalePxPerMm).toDp() }
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    val (widthMm, heightMm) = elementSizeMm(element)
    val widthDp = with(density) { (widthMm * scalePxPerMm).toDp() }
    val heightDp = with(density) { (heightMm * scalePxPerMm).toDp() }.coerceAtLeast(4.dp)

    Box(
        modifier = Modifier
            .offset(xDp, yDp)
            .size(widthDp, heightDp)
            .border(2.dp, borderColor)
            .pointerInput(element.id) {
                detectDragGestures(onDragStart = { onSelect() }) { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x / scalePxPerMm, dragAmount.y / scalePxPerMm)
                }
            }
    ) {
        when (element) {
            is LabelElement.TextElement -> {
                val fontSizeSp = with(density) { (element.fontHeightMm * scalePxPerMm).toDp().toSp() }
                Text(element.text, fontSize = fontSizeSp, maxLines = 1)
            }
            is LabelElement.BarcodeElement -> {
                Text(
                    "|||| ${element.data}",
                    modifier = Modifier.fillMaxSize().background(Color.LightGray).padding(4.dp),
                    maxLines = 1,
                )
            }
            is LabelElement.QrElement -> {
                Text(
                    "QR: ${element.data}",
                    modifier = Modifier.fillMaxSize().background(Color.LightGray).padding(4.dp),
                    maxLines = 2,
                )
            }
            is LabelElement.ShapeElement -> {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(element.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onResize(dragAmount.x / scalePxPerMm, dragAmount.y / scalePxPerMm)
                        }
                    }
            )
        }
    }
}

/** Pestañas del panel de propiedades, al estilo Contenido/Estilo/Alinear de apps de etiquetado comerciales. */
private enum class PropertyTab(val label: String) { CONTENT("Contenido"), STYLE("Estilo"), ALIGN("Alinear") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertyPanel(element: LabelElement, viewModel: DesignerViewModel) {
    var selectedTab by rememberSaveable(element.id) { mutableStateOf(PropertyTab.CONTENT) }

    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                PropertyTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (selectedTab) {
                PropertyTab.CONTENT -> ContentTab(element, viewModel)
                PropertyTab.STYLE -> StyleTab(element, viewModel)
                PropertyTab.ALIGN -> AlignTab(element, viewModel)
            }

            OutlinedButton(onClick = { viewModel.deleteElement(element.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("Eliminar elemento")
            }
        }
    }
}

@Composable
private fun ContentTab(element: LabelElement, viewModel: DesignerViewModel) {
    when (element) {
        is LabelElement.TextElement -> TextContentTab(element, viewModel)
        is LabelElement.BarcodeElement -> OutlinedTextField(
            value = element.data,
            onValueChange = { viewModel.updateBarcodeData(element.id, it) },
            label = { Text("Datos del código de barras") },
            modifier = Modifier.fillMaxWidth(),
        )
        is LabelElement.QrElement -> OutlinedTextField(
            value = element.data,
            onValueChange = { viewModel.updateQrData(element.id, it) },
            label = { Text("Contenido del QR") },
            modifier = Modifier.fillMaxWidth(),
        )
        is LabelElement.ShapeElement -> {
            var widthText by rememberSaveable(element.id) { mutableStateOf(element.widthMm.toInt().toString()) }
            OutlinedTextField(
                value = widthText,
                onValueChange = { text ->
                    widthText = text.filter(Char::isDigit)
                    widthText.toFloatOrNull()?.let { viewModel.updateLineWidth(element.id, it) }
                },
                label = { Text("Ancho de la línea (mm)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TextContentTab(element: LabelElement.TextElement, viewModel: DesignerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tipo de datos", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentedOption(
                label = "Texto",
                selected = element.dataType == TextDataType.STATIC,
                onClick = { viewModel.updateTextDataType(element.id, TextDataType.STATIC) },
                modifier = Modifier.weight(1f),
            )
            SegmentedOption(
                label = "Contador",
                selected = element.dataType == TextDataType.COUNTER,
                onClick = { viewModel.updateTextDataType(element.id, TextDataType.COUNTER) },
                modifier = Modifier.weight(1f),
            )
        }

        when (element.dataType) {
            TextDataType.STATIC -> OutlinedTextField(
                value = element.text,
                onValueChange = { viewModel.updateText(element.id, it) },
                label = { Text("Texto") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextDataType.COUNTER -> CounterFields(element, viewModel)
        }
    }
}

/**
 * Campos del contador autoincremental: cada copia impresa sustituye este texto por
 * `inicio + copia*incremento`, rellenado a `dígitos` posiciones. Requiere imprimir N
 * bloques ZPL separados (ver [com.zplprinter.app.zpl.ZplGenerator.generate]), no una
 * sola etiqueta repetida con `^PQ`.
 */
@Composable
private fun CounterFields(element: LabelElement.TextElement, viewModel: DesignerViewModel) {
    var startText by rememberSaveable(element.id) { mutableStateOf(element.counterStart.toString()) }
    var stepText by rememberSaveable(element.id) { mutableStateOf(element.counterStep.toString()) }
    var digitsText by rememberSaveable(element.id) { mutableStateOf(element.counterDigits.toString()) }

    fun commit(start: String = startText, step: String = stepText, digits: String = digitsText) {
        viewModel.updateCounterConfig(
            element.id,
            start = start.toIntOrNull() ?: element.counterStart,
            step = step.toIntOrNull() ?: element.counterStep,
            digits = digits.toIntOrNull() ?: element.counterDigits,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = startText,
            onValueChange = { startText = it.filter { c -> c.isDigit() }; commit(start = startText) },
            label = { Text("Inicio") },
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = stepText,
            onValueChange = { stepText = it.filter { c -> c.isDigit() }; commit(step = stepText) },
            label = { Text("Incremento") },
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = digitsText,
            onValueChange = { digitsText = it.filter(Char::isDigit); commit(digits = digitsText) },
            label = { Text("Dígitos") },
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        "Vista previa: ${element.resolvedText(0)}, ${element.resolvedText(1)}, ${element.resolvedText(2)}…",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun StyleTab(element: LabelElement, viewModel: DesignerViewModel) {
    when (element) {
        is LabelElement.TextElement -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Alto de letra: ${element.fontHeightMm.toInt()} mm")
            Slider(
                value = element.fontHeightMm,
                onValueChange = { viewModel.updateFontSize(element.id, it, element.fontWidthMm) },
                valueRange = 2f..50f,
            )
            Text("Ancho de letra: ${element.fontWidthMm.toInt()} mm")
            Slider(
                value = element.fontWidthMm,
                onValueChange = { viewModel.updateFontSize(element.id, element.fontHeightMm, it) },
                valueRange = 2f..50f,
            )
        }
        is LabelElement.BarcodeElement -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BarcodeSymbologyDropdown(
                selected = element.symbology,
                onSelected = { viewModel.updateBarcodeSymbology(element.id, it) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Mostrar texto legible")
                Switch(
                    checked = element.humanReadable,
                    onCheckedChange = { viewModel.updateBarcodeHumanReadable(element.id, it) },
                )
            }
        }
        is LabelElement.QrElement -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Magnificación: ${element.magnification.toInt()}")
            Slider(
                value = element.magnification,
                onValueChange = { viewModel.updateQrMagnification(element.id, it) },
                valueRange = 1f..10f,
            )
        }
        is LabelElement.ShapeElement -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Grosor: ${"%.1f".format(element.thicknessMm)} mm")
            Slider(
                value = element.thicknessMm,
                onValueChange = { viewModel.updateLineThickness(element.id, it) },
                valueRange = 0.2f..5f,
            )
        }
    }
}

@Composable
private fun AlignTab(element: LabelElement, viewModel: DesignerViewModel) {
    OutlinedButton(
        onClick = { viewModel.rotateElement(element.id) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Filled.RotateRight, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
        Text("Rotar 90° (actual: ${element.rotationDeg.toInt()}°)")
    }
}

@Composable
private fun SegmentedOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarcodeSymbologyDropdown(selected: BarcodeSymbology, onSelected: (BarcodeSymbology) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo de código de barras") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BarcodeSymbology.entries.forEach { option ->
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
