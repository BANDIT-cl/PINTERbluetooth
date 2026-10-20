package com.zplprinter.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zplprinter.app.ui.components.NavRow
import com.zplprinter.app.ui.components.ZplTopAppBar
import com.zplprinter.app.ui.printer.ConnectionState

/**
 * Pantalla principal: lista de navegación hacia las secciones de la app, al estilo
 * de la Home de com.print.label (tarjetas de ancho completo con ícono de acento).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    connectionState: ConnectionState,
    connectedDeviceLabel: String?,
    onOpenDesigner: () -> Unit,
    onOpenPrinter: () -> Unit,
    onOpenPrintSettings: () -> Unit,
    onOpenMaintenance: () -> Unit,
) {
    Scaffold(
        topBar = { ZplTopAppBar(title = "Impresora de etiquetas ZPL") }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavRow(
                title = "Diseñador de etiquetas",
                subtitle = "Crear o editar una etiqueta",
                icon = Icons.Filled.Edit,
                onClick = onOpenDesigner,
            )
            NavRow(
                title = "Impresora Bluetooth",
                subtitle = connectionSubtitle(connectionState, connectedDeviceLabel),
                icon = Icons.Filled.Bluetooth,
                iconTint = if (connectionState == ConnectionState.CONNECTED) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                onClick = onOpenPrinter,
            )
            NavRow(
                title = "Ajustes de impresión",
                subtitle = "Tamaño de papel, tipo, velocidad, oscuridad",
                icon = Icons.Filled.Settings,
                onClick = onOpenPrintSettings,
            )
            NavRow(
                title = "Mantenimiento",
                subtitle = "Calibrar sensor, consultar estado de la impresora",
                icon = Icons.Filled.Build,
                onClick = onOpenMaintenance,
            )
        }
    }
}

private fun connectionSubtitle(state: ConnectionState, deviceLabel: String?): String = when (state) {
    ConnectionState.CONNECTED -> "Conectado: ${deviceLabel ?: "impresora"}"
    ConnectionState.CONNECTING -> "Conectando…"
    ConnectionState.ERROR -> "Error de conexión"
    ConnectionState.DISCONNECTED -> "Sin conectar"
}
