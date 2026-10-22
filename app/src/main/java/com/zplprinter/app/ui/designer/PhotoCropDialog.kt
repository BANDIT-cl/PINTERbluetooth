package com.zplprinter.app.ui.designer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zplprinter.app.ui.components.ZplTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val HANDLE_SIZE_DP = 24f
private const val MIN_CROP_SIZE_DP = 32f
private const val MAX_DECODE_DIMENSION_PX = 1400

/**
 * Importa una foto de la galería y deja elegir qué parte usar arrastrando las dos esquinas
 * del recorte, antes de insertarla como [com.zplprinter.app.data.model.LabelElement.ImageElement].
 * El resultado ([onConfirm]) es la foto recortada a color; [DesignerViewModel.addImage] la
 * convierte a monocromo, que es lo que realmente se imprime.
 */
@Composable
fun PhotoCropDialog(uri: Uri, onConfirm: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var sourceBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        sourceBitmap = withContext(Dispatchers.IO) { loadRotatedBitmap(context, uri) }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val bitmap = sourceBitmap
            // Un Dialog de Compose abre una ventana Android separada de la de MainActivity, que
            // no siempre recibe los insets de la barra de navegacion de forma confiable (una
            // limitacion conocida): navigationBarsPadding() solo no alcanzaba para garantizar que
            // "Cancelar"/"Listo" quedaran visibles en todas las pantallas (p. ej. con una foto en
            // vertical, que ya de por si necesita mas alto). verticalScroll() es la garantia real.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
            ) {
                ZplTopAppBar(title = "Recortar imagen", onBack = onCancel)

                if (bitmap == null) {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    var boxSizePx by remember(bitmap) { mutableStateOf(IntSize.Zero) }
                    var cropLeft by remember(bitmap) { mutableFloatStateOf(0f) }
                    var cropTop by remember(bitmap) { mutableFloatStateOf(0f) }
                    var cropRight by remember(bitmap) { mutableFloatStateOf(0f) }
                    var cropBottom by remember(bitmap) { mutableFloatStateOf(0f) }
                    var initialized by remember(bitmap) { mutableStateOf(false) }

                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                .onSizeChanged { size ->
                                    boxSizePx = size
                                    if (!initialized && size.width > 0 && size.height > 0) {
                                        cropLeft = size.width * 0.1f
                                        cropTop = size.height * 0.1f
                                        cropRight = size.width * 0.9f
                                        cropBottom = size.height * 0.9f
                                        initialized = true
                                    }
                                }
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )

                            if (initialized) {
                                val handlePx = with(density) { HANDLE_SIZE_DP.dp.toPx() }
                                val minSizePx = with(density) { MIN_CROP_SIZE_DP.dp.toPx() }

                                Box(
                                    modifier = Modifier
                                        .offset(
                                            with(density) { cropLeft.toDp() },
                                            with(density) { cropTop.toDp() },
                                        )
                                        .size(
                                            with(density) { (cropRight - cropLeft).toDp() },
                                            with(density) { (cropBottom - cropTop).toDp() },
                                        )
                                        .border(2.dp, MaterialTheme.colorScheme.primary)
                                )

                                CropHandle(
                                    xPx = cropLeft,
                                    yPx = cropTop,
                                    handlePx = handlePx,
                                    onDrag = { dx, dy ->
                                        cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minSizePx)
                                        cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minSizePx)
                                    },
                                )
                                CropHandle(
                                    xPx = cropRight - handlePx,
                                    yPx = cropBottom - handlePx,
                                    handlePx = handlePx,
                                    onDrag = { dx, dy ->
                                        cropRight = (cropRight + dx).coerceIn(cropLeft + minSizePx, boxSizePx.width.toFloat())
                                        cropBottom = (cropBottom + dy).coerceIn(cropTop + minSizePx, boxSizePx.height.toFloat())
                                    },
                                )
                            }
                        }
                    }

                    Text(
                        "Arrastra las esquinas celestes para elegir qué parte de la foto usar.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                        Button(
                            onClick = {
                                val scaleX = bitmap.width / boxSizePx.width.toFloat()
                                val scaleY = bitmap.height / boxSizePx.height.toFloat()
                                onConfirm(
                                    cropBitmap(
                                        bitmap,
                                        cropLeft * scaleX,
                                        cropTop * scaleY,
                                        cropRight * scaleX,
                                        cropBottom * scaleY,
                                    )
                                )
                            },
                            enabled = initialized,
                            modifier = Modifier.weight(1f),
                        ) { Text("Listo") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropHandle(xPx: Float, yPx: Float, handlePx: Float, onDrag: (Float, Float) -> Unit) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset(with(density) { xPx.toDp() }, with(density) { yPx.toDp() })
            .size(with(density) { handlePx.toDp() })
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            }
    )
}

private fun cropBitmap(source: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
    val l = left.toInt().coerceIn(0, source.width - 1)
    val t = top.toInt().coerceIn(0, source.height - 1)
    val r = right.toInt().coerceIn(l + 1, source.width)
    val b = bottom.toInt().coerceIn(t + 1, source.height)
    return Bitmap.createBitmap(source, l, t, r - l, b - t)
}

/** Decodifica la foto reducida a un tamaño manejable y la rota según su orientación EXIF (las fotos de cámara vienen "acostadas" en los bytes). */
private fun loadRotatedBitmap(context: Context, uri: Uri): Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

    var sampleSize = 1
    while (boundsOptions.outWidth / sampleSize > MAX_DECODE_DIMENSION_PX ||
        boundsOptions.outHeight / sampleSize > MAX_DECODE_DIMENSION_PX
    ) {
        sampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = context.contentResolver.openInputStream(uri)
        ?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        ?: return null

    val rotationDegrees = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)

    if (rotationDegrees == 0f) return decoded
    val matrix = Matrix().apply { postRotate(rotationDegrees) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}
