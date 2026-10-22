package com.zplprinter.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.zplprinter.app.image.MonoBitmap
import com.zplprinter.app.zpl.ZplConstants
import kotlin.math.roundToInt

/** Una página de PDF ya convertida a mapa de bits monocromo del tamaño exacto de etiqueta, lista para ^GFA. */
data class PdfLabelImage(
    val widthDots: Int,
    val heightDots: Int,
    val packedMono: ByteArray,
    val preview: Bitmap,
)

/**
 * Convierte cada página de un PDF (p. ej. una etiqueta de envío de MercadoLibre, a menudo
 * embebida dentro de una hoja A4 junto con mucho espacio en blanco) en un mapa de bits
 * monocromo del tamaño de papel físico cargado en la impresora.
 *
 * El contenido real de la etiqueta suele ocupar solo una región de la página, así que primero
 * se recorta automáticamente al rectángulo que contiene tinta, y luego se escala manteniendo
 * la proporción (sin estirar el código de barras/QR) para que entre completo en el tamaño de
 * papel configurado -p. ej. 100x100mm aunque el PDF sea de 100x150mm-, centrado sobre blanco.
 */
object PdfLabelConverter {

    private const val RENDER_DPI = 300
    private const val AUTOCROP_MARGIN_MM = 2f

    /** Luminancia (0-255) bajo la cual un píxel cuenta como "tinta" al buscar el recorte. */
    private const val CONTENT_THRESHOLD = 250

    fun convert(
        context: Context,
        uri: Uri,
        targetWidthMm: Float,
        targetHeightMm: Float,
        dpi: Int,
    ): List<PdfLabelImage> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("No se pudo abrir el archivo PDF")
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                check(renderer.pageCount > 0) { "El PDF no tiene páginas" }
                val targetWidthDots = ZplConstants.mmToDots(targetWidthMm, dpi)
                val targetHeightDots = ZplConstants.mmToDots(targetHeightMm, dpi)

                return (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val full = renderPageToBitmap(page)
                        val cropped = autocrop(full)
                        if (cropped !== full) full.recycle()

                        val fitted = MonoBitmap.fitOnWhiteCanvas(cropped, targetWidthDots, targetHeightDots)
                        if (fitted !== cropped) cropped.recycle()

                        PdfLabelImage(targetWidthDots, targetHeightDots, MonoBitmap.pack(fitted), fitted)
                    }
                }
            }
        }
    }

    private fun renderPageToBitmap(page: PdfRenderer.Page): Bitmap {
        val scale = RENDER_DPI / 72f
        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val matrix = Matrix().apply { setScale(scale, scale) }
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    /** Recorta al rectángulo que contiene tinta (más un margen); si la página está en blanco, la deja intacta. */
    private fun autocrop(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val rowBase = y * width
            for (x in 0 until width) {
                if (luminance(pixels[rowBase + x]) < CONTENT_THRESHOLD) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return bitmap

        val marginPx = (AUTOCROP_MARGIN_MM * RENDER_DPI / 25.4f).roundToInt()
        val bbox = Rect(
            (minX - marginPx).coerceAtLeast(0),
            (minY - marginPx).coerceAtLeast(0),
            (maxX + 1 + marginPx).coerceAtMost(width),
            (maxY + 1 + marginPx).coerceAtMost(height),
        )
        return Bitmap.createBitmap(bitmap, bbox.left, bbox.top, bbox.width(), bbox.height())
    }

    private fun luminance(pixel: Int): Int {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha == 0) return 255
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
