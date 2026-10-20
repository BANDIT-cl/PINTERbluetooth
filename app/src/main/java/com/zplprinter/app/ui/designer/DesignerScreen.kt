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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.zplprinter.app.data.model.BarcodeSymbology
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.model.LabelElement
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
            TopAppBar(
                title = { Text("Diseñador de etiquetas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertyPanel(element: LabelElement, viewModel: DesignerViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (element) {
                is LabelElement.TextElement -> OutlinedTextField(
                    value = element.text,
                    onValueChange = { viewModel.updateText(element.id, it) },
                    label = { Text("Texto") },
                    modifier = Modifier.fillMaxWidth(),
                )
                is LabelElement.BarcodeElement -> {
                    OutlinedTextField(
                        value = element.data,
                        onValueChange = { viewModel.updateBarcodeData(element.id, it) },
                        label = { Text("Datos del código de barras") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BarcodeSymbologyDropdown(
                        selected = element.symbology,
                        onSelected = { viewModel.updateBarcodeSymbology(element.id, it) },
                    )
                }
                is LabelElement.QrElement -> OutlinedTextField(
                    value = element.data,
                    onValueChange = { viewModel.updateQrData(element.id, it) },
                    label = { Text("Contenido del QR") },
                    modifier = Modifier.fillMaxWidth(),
                )
                is LabelElement.ShapeElement -> Text("Línea (ancho ${element.widthMm.toInt()} mm)")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.rotateElement(element.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.RotateRight, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Rotar 90° (${element.rotationDeg.toInt()}°)")
                }
                OutlinedButton(
                    onClick = { viewModel.deleteElement(element.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Eliminar")
                }
            }
        }
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
