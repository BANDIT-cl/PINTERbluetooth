package com.zplprinter.app.ui.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zplprinter.app.zpl.ZplConstants

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsScreen(
    hasBluetoothPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenDesigner: () -> Unit,
    viewModel: PrinterViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Configuración de impresora") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onOpenDesigner, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir diseñador de etiquetas")
            }

            if (!hasBluetoothPermission) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Se necesita permiso de Bluetooth para buscar e imprimir en la Xprinter.")
                        Button(onClick = onRequestPermission) { Text("Conceder permiso") }
                    }
                }
            }

            PaperSizeSection(
                widthMm = uiState.widthMm,
                heightMm = uiState.heightMm,
                dpi = uiState.dpi,
                onSizeSelected = { width, height -> viewModel.selectPaperSize(width, height) }
            )

            PrintJobSettingsSection(
                mediaType = uiState.mediaType,
                printSpeed = uiState.printSpeed,
                darkness = uiState.darkness,
                onChange = { mediaType, speed, darkness ->
                    viewModel.updatePrintSettings(mediaType, speed, darkness)
                }
            )

            PrinterSection(
                hasPermission = hasBluetoothPermission,
                uiState = uiState,
                onRefresh = viewModel::refreshBondedDevices,
                onScan = viewModel::startScan,
                onStopScan = viewModel::stopScan,
                onSelectDevice = viewModel::connect,
                onDisconnect = viewModel::disconnect,
            )

            Button(
                onClick = viewModel::printTestLabel,
                enabled = uiState.connectionState == ConnectionState.CONNECTED && !uiState.isPrinting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isPrinting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text("Imprimir etiqueta de prueba")
                }
            }

            OutlinedButton(
                onClick = viewModel::printSampleElements,
                enabled = uiState.connectionState == ConnectionState.CONNECTED && !uiState.isPrinting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Imprimir muestra (texto + barras + QR + línea)")
            }

            MaintenanceSection(
                connected = uiState.connectionState == ConnectionState.CONNECTED,
                isCalibrating = uiState.isCalibrating,
                isQueryingStatus = uiState.isQueryingStatus,
                printerStatus = uiState.printerStatus,
                onCalibrate = viewModel::calibrateSensor,
                onQueryStatus = viewModel::queryPrinterStatus,
            )

            uiState.lastPrintOk?.let { ok ->
                Text(
                    if (ok) "Operación enviada correctamente." else "Error: ${uiState.errorMessage}",
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            uiState.errorMessage?.takeIf { uiState.connectionState == ConnectionState.ERROR }?.let {
                Text("Error de conexión: $it", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private const val MANUAL_OPTION_LABEL = "Personalizado..."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaperSizeSection(
    widthMm: Float,
    heightMm: Float,
    dpi: Int,
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tamaño de etiqueta (DPI: $dpi)", style = MaterialTheme.typography.titleMedium)

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = currentLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tamaño de papel") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                Button(
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintJobSettingsSection(
    mediaType: com.zplprinter.app.zpl.MediaType,
    printSpeed: Int,
    darkness: Int,
    onChange: (com.zplprinter.app.zpl.MediaType, Int, Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ajustes de impresión", style = MaterialTheme.typography.titleMedium)

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = mediaType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo de papel") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    com.zplprinter.app.zpl.MediaType.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                onChange(option, printSpeed, darkness)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Text("Velocidad de impresión: $printSpeed ips")
            Slider(
                value = printSpeed.toFloat(),
                onValueChange = { onChange(mediaType, it.toInt(), darkness) },
                valueRange = 1f..14f,
                steps = 12,
            )

            Text("Oscuridad: $darkness")
            Slider(
                value = darkness.toFloat(),
                onValueChange = { onChange(mediaType, printSpeed, it.toInt()) },
                valueRange = 0f..30f,
                steps = 29,
            )
        }
    }
}

@Composable
private fun PrinterSection(
    hasPermission: Boolean,
    uiState: PrinterUiState,
    onRefresh: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Impresora Bluetooth", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onRefresh, enabled = hasPermission) { Text("Actualizar") }
            }

            uiState.rememberedPrinterName?.let { name ->
                if (uiState.connectionState != ConnectionState.CONNECTED) {
                    Text(
                        "Impresora recordada: $name (se usará automáticamente al compartir una etiqueta ZPL)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            when (uiState.connectionState) {
                ConnectionState.CONNECTED -> {
                    Text("Conectado a: ${safeDeviceLabel(uiState.selectedDevice)}")
                    OutlinedButton(onClick = onDisconnect) { Text("Desconectar") }
                }
                ConnectionState.CONNECTING -> {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Conectando a ${safeDeviceLabel(uiState.selectedDevice)}...")
                    }
                }
                else -> {
                    if (!hasPermission) {
                        Text("Concede el permiso de Bluetooth para ver e imprimir dispositivos.")
                    } else {
                        if (uiState.bondedDevices.isEmpty()) {
                            Text("No hay dispositivos emparejados.")
                        } else {
                            Text("Emparejados:", style = MaterialTheme.typography.labelMedium)
                            DeviceListColumn(devices = uiState.bondedDevices, onSelectDevice = onSelectDevice)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text("Dispositivos nuevos:", style = MaterialTheme.typography.labelMedium)
                            if (uiState.isScanning) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.height(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = onStopScan) { Text("Detener") }
                                }
                            } else {
                                TextButton(onClick = onScan) { Text("Buscar") }
                            }
                        }

                        if (!uiState.isScanning && uiState.discoveredDevices.isEmpty()) {
                            Text(
                                "Pulsa \"Buscar\" para detectar impresoras cercanas no emparejadas todavía.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            DeviceListColumn(devices = uiState.discoveredDevices, onSelectDevice = onSelectDevice)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceListColumn(devices: List<BluetoothDevice>, onSelectDevice: (BluetoothDevice) -> Unit) {
    LazyColumn(modifier = Modifier.height((devices.size.coerceIn(1, 3) * 44).dp)) {
        items(devices) { device ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(safeDeviceLabel(device))
                TextButton(onClick = { onSelectDevice(device) }) { Text("Conectar") }
            }
        }
    }
}

@Composable
private fun MaintenanceSection(
    connected: Boolean,
    isCalibrating: Boolean,
    isQueryingStatus: Boolean,
    printerStatus: String?,
    onCalibrate: () -> Unit,
    onQueryStatus: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mantenimiento", style = MaterialTheme.typography.titleMedium)

            OutlinedButton(
                onClick = onCalibrate,
                enabled = connected && !isCalibrating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isCalibrating) "Calibrando..." else "Calibrar sensor de papel")
            }

            OutlinedButton(
                onClick = onQueryStatus,
                enabled = connected && !isQueryingStatus,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isQueryingStatus) "Consultando..." else "Consultar estado de la impresora")
            }

            printerStatus?.let {
                Text(
                    "Respuesta de la impresora:\n$it",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun safeDeviceLabel(device: BluetoothDevice?): String {
    if (device == null) return "—"
    return try {
        device.name ?: device.address
    } catch (_: SecurityException) {
        device.address
    }
}
