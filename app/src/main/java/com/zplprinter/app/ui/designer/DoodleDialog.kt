package com.zplprinter.app.ui.designer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zplprinter.app.ui.components.ZplTopAppBar

private const val STROKE_WIDTH_DP = 6f

/**
 * Lienzo de dibujo libre a mano ("Graffiti"): cada trazo es una lista de puntos, dibujados en
 * un [Canvas] de Compose mientras se arrastra el dedo. Al confirmar, se vuelven a trazar sobre
 * un [android.graphics.Bitmap] nativo con el tamaño real del lienzo, que es lo que
 * [DesignerViewModel.addImage] convierte a monocromo e inserta en la etiqueta.
 */
@Composable
fun DoodleDialog(onConfirm: (android.graphics.Bitmap) -> Unit, onCancel: () -> Unit) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { STROKE_WIDTH_DP.dp.toPx() }

    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }
    var canvasSizePx by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ZplTopAppBar(title = "Dibujo libre", onBack = onCancel)

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color.White)
                            .border(1.dp, Color.Black)
                            .onSizeChanged { canvasSizePx = it }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset -> strokes.add(mutableListOf(offset)) },
                                ) { change, _ ->
                                    change.consume()
                                    strokes.lastOrNull()?.add(change.position)
                                }
                            }
                    ) {
                        strokes.forEach { points ->
                            if (points.size < 2) return@forEach
                            val path = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                            }
                            drawPath(
                                path,
                                color = Color.Black,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                        enabled = strokes.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Deshacer trazo") }
                    OutlinedButton(
                        onClick = { strokes.clear() },
                        enabled = strokes.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Borrar todo") }
                }

                Text(
                    "Dibuja con el dedo sobre el lienzo blanco.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(
                        onClick = { onConfirm(renderStrokesToBitmap(strokes, canvasSizePx, strokeWidthPx)) },
                        enabled = strokes.isNotEmpty() && canvasSizePx.width > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("Listo") }
                }
            }
        }
    }
}

private fun renderStrokesToBitmap(
    strokes: List<List<Offset>>,
    sizePx: IntSize,
    strokeWidthPx: Float,
): android.graphics.Bitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx.width, sizePx.height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }

    strokes.forEach { points ->
        if (points.size < 2) return@forEach
        val path = android.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}
