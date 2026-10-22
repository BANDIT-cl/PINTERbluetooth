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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zplprinter.app.bluetooth.BluetoothPrinterManager
import com.zplprinter.app.data.model.ConnectionType
import com.zplprinter.app.data.model.PrinterProfile
import com.zplprinter.app.ui.components.ZplTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Conexión con la impresora (Bluetooth o Wi-Fi) y las dos pruebas de impresión. Equivalente a PrinterInfoActivity + DeviceListActivity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConnectionScreen(
    hasBluetoothPermission: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    viewModel: PrinterViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val savedProfiles by viewModel.savedProfiles.collectAsState()
    var pendingForget by remember { mutableStateOf<PrinterProfile?>(null) }

    Scaffold(
        topBar = { ZplTopAppBar(title = "Impresora", onBack = onBack) }
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

            if (savedProfiles.isNotEmpty()) {
                SavedProfilesSection(
                    profiles = savedProfiles,
                    connectedAddress = when (uiState.connectionType) {
                        ConnectionType.BLUETOOTH -> uiState.selectedDevice?.takeIf { uiState.connectionState == ConnectionState.CONNECTED }?.address
                        ConnectionType.WIFI -> uiState.wifiTarget?.takeIf { uiState.connectionState == ConnectionState.CONNECTED }
                    },
                    connecting = uiState.connectionState == ConnectionState.CONNECTING,
                    onConnect = viewModel::connectToSavedProfile,
                    onForget = { pendingForget = it },
                )
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

            WifiSection(
                uiState = uiState,
                onConnect = viewModel::connectWifi,
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

    pendingForget?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingForget = null },
            title = { Text("Olvidar impresora") },
            text = { Text("¿Olvidar \"${profile.name}\"? Dejará de usarse automáticamente al compartir o imprimir.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.forgetProfile(profile)
                    pendingForget = null
                }) { Text("Olvidar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingForget = null }) { Text("Cancelar") }
            },
        )
    }
}

/**
 * Impresoras a las que el usuario ya se conectó alguna vez (no solo la última), para poder
 * elegir entre varias sin tener que escanear/emparejar de nuevo. La más reciente es la que
 * se usa automáticamente al imprimir desde "Compartir" o desde la importación de PDF.
 */
@Composable
private fun SavedProfilesSection(
    profiles: List<PrinterProfile>,
    connectedAddress: String?,
    connecting: Boolean,
    onConnect: (PrinterProfile) -> Unit,
    onForget: (PrinterProfile) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Impresoras guardadas", style = MaterialTheme.typography.titleMedium)
            profiles.forEachIndexed { index, profile ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.name + if (profile.connectionType == ConnectionType.WIFI) " (Wi-Fi)" else "")
                        Text(
                            if (profile.macAddress == connectedAddress) {
                                "Conectada ahora"
                            } else if (index == 0) {
                                "Se usa automáticamente · ${formatLastConnected(profile.lastConnectedAt)}"
                            } else {
                                formatLastConnected(profile.lastConnectedAt)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (profile.macAddress != connectedAddress) {
                        TextButton(onClick = { onConnect(profile) }, enabled = !connecting) { Text("Conectar") }
                    }
                    IconButton(onClick = { onForget(profile) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Olvidar \"${profile.name}\"")
                    }
                }
            }
        }
    }
}

private fun formatLastConnected(epochMs: Long): String =
    "Último uso: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))

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

            val isBluetoothTransport = uiState.connectionType == ConnectionType.BLUETOOTH
            when {
                uiState.connectionState == ConnectionState.CONNECTED && isBluetoothTransport -> {
                    Text("Conectado a: ${safeDeviceLabel(uiState.selectedDevice)}")
                    OutlinedButton(onClick = onDisconnect) { Text("Desconectar") }
                }
                uiState.connectionState == ConnectionState.CONNECTING && isBluetoothTransport -> {
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

/**
 * Conexión por Wi-Fi: la mayoría de las impresoras de red no se "descubren" (no hay un
 * equivalente simple al escaneo Bluetooth), así que se pide la IP directamente, como hace
 * com.print.label. El puerto 9100 ("raw"/JetDirect) es el valor por defecto que usan casi
 * todas las impresoras térmicas con interfaz de red.
 */
@Composable
private fun WifiSection(
    uiState: PrinterUiState,
    onConnect: (ipAddress: String, port: Int, name: String) -> Unit,
    onDisconnect: () -> Unit,
) {
    var ipAddress by rememberSaveable { mutableStateOf("") }
    var portText by rememberSaveable { mutableStateOf(BluetoothPrinterManager.DEFAULT_WIFI_PORT.toString()) }
    val connectedViaWifi = uiState.connectionType == ConnectionType.WIFI && uiState.connectionState == ConnectionState.CONNECTED
    val connectingViaWifi = uiState.connectionType == ConnectionType.WIFI && uiState.connectionState == ConnectionState.CONNECTING

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Impresora Wi-Fi", style = MaterialTheme.typography.titleMedium)

            if (connectedViaWifi) {
                Text("Conectado a: ${uiState.wifiTarget}")
                OutlinedButton(onClick = onDisconnect) { Text("Desconectar") }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("Dirección IP") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit) },
                        label = { Text("Puerto") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        val port = portText.toIntOrNull() ?: BluetoothPrinterManager.DEFAULT_WIFI_PORT
                        onConnect(ipAddress.trim(), port, ipAddress.trim())
                    },
                    enabled = ipAddress.isNotBlank() && !connectingViaWifi,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (connectingViaWifi) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    } else {
                        Text("Conectar por Wi-Fi")
                    }
                }
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
