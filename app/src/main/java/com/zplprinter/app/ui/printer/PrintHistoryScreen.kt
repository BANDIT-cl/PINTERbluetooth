package com.zplprinter.app.ui.printer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zplprinter.app.data.model.PrintRecord
import com.zplprinter.app.ui.components.ZplTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cada intento de impresión (exitoso o no): diseñador, PDF importado, "Compartir" y las pruebas de Bluetooth. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintHistoryScreen(
    records: List<PrintRecord>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ZplTopAppBar(
                title = "Historial de impresión",
                onBack = onBack,
                actions = {
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Borrar historial")
                        }
                    }
                },
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Todavía no imprimiste ninguna etiqueta.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(records, key = { it.id }) { record -> PrintRecordRow(record) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Borrar historial") },
            text = { Text("¿Borrar todo el historial de impresión? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { onClear(); confirmClear = false }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun PrintRecordRow(record: PrintRecord) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (record.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = if (record.success) "Impresión exitosa" else "Error de impresión",
                    tint = if (record.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(record.description, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                buildString {
                    append(formatPrintedAt(record.printedAt))
                    record.printerName?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!record.success && record.errorMessage != null) {
                Text(
                    record.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun formatPrintedAt(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))
