package com.zplprinter.app.ui.labels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.ui.components.ZplTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lista de etiquetas guardadas en Room: abrir, duplicar o borrar. La "Nueva" va directo al diseñador en blanco. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedLabelsScreen(
    onBack: () -> Unit,
    onOpenLabel: (String) -> Unit,
    onCreateNew: () -> Unit,
    viewModel: SavedLabelsViewModel = viewModel(),
) {
    val labels by viewModel.labels.collectAsState()
    var pendingDelete by remember { mutableStateOf<Label?>(null) }

    Scaffold(
        topBar = {
            ZplTopAppBar(
                title = "Mis etiquetas",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onCreateNew) {
                        Icon(Icons.Filled.Add, contentDescription = "Nueva etiqueta")
                    }
                },
            )
        }
    ) { padding ->
        if (labels.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "No tienes etiquetas guardadas todavía.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onCreateNew) { Text("Crear etiqueta") }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(labels, key = { it.id }) { label ->
                    SavedLabelRow(
                        label = label,
                        onClick = { onOpenLabel(label.id) },
                        onDuplicate = { viewModel.duplicate(label) },
                        onDelete = { pendingDelete = label },
                    )
                }
            }
        }
    }

    pendingDelete?.let { label ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Borrar etiqueta") },
            text = { Text("¿Borrar \"${label.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(label)
                    pendingDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun SavedLabelRow(label: Label, onClick: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label.name.ifBlank { "Etiqueta" }, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${label.widthMm.toInt()} x ${label.heightMm.toInt()} mm · ${formatUpdatedAt(label.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDuplicate) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicar \"${label.name}\"")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Borrar \"${label.name}\"", tint = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun formatUpdatedAt(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))
