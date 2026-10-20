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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zplprinter.app.ui.components.ZplTopAppBar

/** Conexión Bluetooth con la impresora y las dos pruebas de impresión. Equivalente a PrinterInfoActivity + DeviceListActivity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConnectionScreen(
    hasBluetoothPermission: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    viewModel: PrinterViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { ZplTopAppBar(title = "Impresora Bluetooth", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasBluetoothPermission) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Se necesita permiso de Bluetooth para buscar e imprimir.")
                        Button(onClick = onRequestPermission) { Text("Conceder permiso") }
                    }
                }
            }

            DeviceSection(
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

@Composable
private fun DeviceSection(
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
                Text("Dispositivo", style = MaterialTheme.typography.titleMedium)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Dispositivos nuevos:", style = MaterialTheme.typography.labelMedium)
                            if (uiState.isScanning) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
internal fun DeviceListColumn(devices: List<BluetoothDevice>, onSelectDevice: (BluetoothDevice) -> Unit) {
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

@SuppressLint("MissingPermission")
internal fun safeDeviceLabel(device: BluetoothDevice?): String {
    if (device == null) return "—"
    return try {
        device.name ?: device.address
    } catch (_: SecurityException) {
        device.address
    }
}
