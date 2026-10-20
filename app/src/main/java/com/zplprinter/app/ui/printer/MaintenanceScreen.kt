package com.zplprinter.app.ui.printer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zplprinter.app.ui.components.ActionRow
import com.zplprinter.app.ui.components.ZplTopAppBar

/** Calibración de sensor y consulta de estado, como lista de acciones. Equivalente a parte de PagerSettingActivity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    connected: Boolean,
    isCalibrating: Boolean,
    isQueryingStatus: Boolean,
    printerStatus: String?,
    errorMessage: String?,
    onBack: () -> Unit,
    onCalibrate: () -> Unit,
    onQueryStatus: () -> Unit,
) {
    Scaffold(
        topBar = { ZplTopAppBar(title = "Mantenimiento", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (!connected) {
                Text(
                    "Conecta una impresora primero para calibrar o consultar su estado.",
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            ActionRow(
                title = if (isCalibrating) "Calibrando…" else "Calibrar sensor de papel",
                subtitle = "Autocalibración de gap/marca negra (~JC)",
                icon = Icons.Filled.Build,
                enabled = connected && !isCalibrating,
                onClick = onCalibrate,
            )

            ActionRow(
                title = if (isQueryingStatus) "Consultando…" else "Consultar estado de la impresora",
                subtitle = "Host Status Return (~HS)",
                icon = Icons.Filled.Info,
                enabled = connected && !isQueryingStatus,
                onClick = onQueryStatus,
            )

            printerStatus?.let {
                Text(
                    "Respuesta de la impresora:\n$it",
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            errorMessage?.let {
                Text(
                    "Error: $it",
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
