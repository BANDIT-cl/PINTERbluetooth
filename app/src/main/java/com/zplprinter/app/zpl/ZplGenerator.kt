package com.zplprinter.app.zpl

import android.graphics.Bitmap
import android.graphics.Matrix
import com.zplprinter.app.data.model.BarcodeSymbology
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.model.LabelElement
import com.zplprinter.app.data.model.TextDataType
import com.zplprinter.app.image.MonoBitmap
import kotlin.math.roundToInt

/**
 * Generador de ZPL. `buildTestLabel` es la prueba fija de conectividad de M1;
 * `generate(Label)` es la traducción completa del modelo del diseñador a ZPL.
 */
object ZplGenerator {

    /** Autocalibración del sensor de gap/marca negra (~JC). Corrige el desalineado típico tras cambiar el rollo. */
    const val CALIBRATE_SENSOR_CMD = "~JC"

    /** Host Status Return: la impresora responde con 3 líneas de estado (papel, pausa, errores). */
    const val QUERY_STATUS_CMD = "~HS"

    fun buildTestLabel(widthMm: Float, heightMm: Float, dpi: Int): String {
        val widthDots = ZplConstants.mmToDots(widthMm, dpi)
        val heightDots = ZplConstants.mmToDots(heightMm, dpi)

        return buildString {
            append("^XA")
            append("^CI28")
            append("^PW$widthDots")
            append("^LL$heightDots")
            append("^FO20,20^A0N,40,40^FDPRUEBA DE IMPRESION^FS")
            append("^FO20,70^A0N,28,28^FD${widthMm.toInt()}x${heightMm.toInt()} mm @ ${dpi}dpi^FS")
            append("^FO20,110^GB${widthDots - 40},0,3^FS")
            append("^XZ")
        }
    }

    /**
     * Traduce una [Label] completa (texto, código de barras, QR, líneas/cajas) a ZPL.
     *
     * Si ningún [LabelElement.TextElement] usa un contador, genera un solo bloque
     * `^XA...^XZ` y deja que la propia impresora repita la copia con `^PQ` (más rápido:
     * un solo envío por Bluetooth). Si hay un contador, cada copia necesita texto distinto,
     * así que se genera un bloque `^XA...^XZ` por copia, cada uno con el valor incrementado.
     */
    fun generate(label: Label, printSettings: PrintSettings = PrintSettings()): String {
        val copies = printSettings.copies.coerceAtLeast(1)
        val hasCounter = label.elements.any {
            it is LabelElement.TextElement && it.dataType == TextDataType.COUNTER
        }
        return if (hasCounter) {
            buildString {
                repeat(copies) { copyIndex -> append(buildLabelBlock(label, printSettings, copyIndex, copiesInBlock = 1)) }
            }
        } else {
            buildLabelBlock(label, printSettings, copyIndex = 0, copiesInBlock = copies)
        }
    }

    private fun buildLabelBlock(label: Label, printSettings: PrintSettings, copyIndex: Int, copiesInBlock: Int): String {
        val widthDots = ZplConstants.mmToDots(label.widthMm, label.dpi)
        val heightDots = ZplConstants.mmToDots(label.heightMm, label.dpi)

        return buildString {
            append("^XA")
            append("^CI28")
            append("^MN${printSettings.mediaType.zplChar}")
            printSettings.printSpeed?.let { append("^PR$it") }
            printSettings.darkness?.let { append("^MD$it") }
            append("^PW$widthDots")
            append("^LL$heightDots")
            label.elements.forEach { element -> append(renderElement(element, label.dpi, copyIndex)) }
            append("^PQ$copiesInBlock")
            append("^XZ")
        }
    }

    private fun renderElement(element: LabelElement, dpi: Int, copyIndex: Int): String = when (element) {
        is LabelElement.TextElement -> renderText(element, dpi, copyIndex)
        is LabelElement.BarcodeElement -> renderBarcode(element, dpi)
        is LabelElement.QrElement -> renderQr(element, dpi)
        is LabelElement.ShapeElement -> renderShape(element, dpi)
        is LabelElement.ImageElement -> renderImage(element, dpi)
    }

    private fun renderText(e: LabelElement.TextElement, dpi: Int, copyIndex: Int): String {
        val x = ZplConstants.mmToDots(e.xMm, dpi)
        val y = ZplConstants.mmToDots(e.yMm, dpi)
        val h = ZplConstants.mmToDots(e.fontHeightMm, dpi)
        val w = ZplConstants.mmToDots(e.fontWidthMm, dpi)
        val orientation = orientationChar(e.rotationDeg)
        return "^FO$x,$y^A0$orientation,$h,$w^FD${escape(e.resolvedText(copyIndex))}^FS"
    }

    private fun renderBarcode(e: LabelElement.BarcodeElement, dpi: Int): String {
        val x = ZplConstants.mmToDots(e.xMm, dpi)
        val y = ZplConstants.mmToDots(e.yMm, dpi)
        val h = ZplConstants.mmToDots(e.heightMm, dpi)
        val orientation = orientationChar(e.rotationDeg)
        val interp = if (e.humanReadable) "Y" else "N"
        val barcodeCommand = when (e.symbology) {
            BarcodeSymbology.CODE128 -> "^BC$orientation,$h,$interp,N,N"
            BarcodeSymbology.CODE39 -> "^B3$orientation,N,$h,$interp,N"
            BarcodeSymbology.EAN13 -> "^BE$orientation,$h,$interp,N"
            BarcodeSymbology.UPC_A -> "^BU$orientation,$h,$interp,N"
            BarcodeSymbology.ITF -> "^B2$orientation,$h,$interp,N,N"
        }
        val moduleWidth = e.moduleWidthDots.roundToInt().coerceIn(1, 10)
        return "^FO$x,$y^BY$moduleWidth,2,$h$barcodeCommand^FD${escape(e.data)}^FS"
    }

    private fun renderQr(e: LabelElement.QrElement, dpi: Int): String {
        val x = ZplConstants.mmToDots(e.xMm, dpi)
        val y = ZplConstants.mmToDots(e.yMm, dpi)
        val orientation = orientationChar(e.rotationDeg)
        val magnification = e.magnification.roundToInt().coerceIn(1, 10)
        return "^FO$x,$y^BQ$orientation,2,$magnification^FDQA,${escape(e.data)}^FS"
    }

    /**
     * ZPL no admite escalar un ^GFA al imprimir (a diferencia de texto/barras, que escalan por
     * parámetro), así que acá se reescala el bitmap fuente en memoria según el tamaño actual del
     * elemento en mm, y se re-rota (solo pasos de 90°, como el resto de los elementos) antes de
     * volver a empaquetarlo.
     */
    private fun renderImage(e: LabelElement.ImageElement, dpi: Int): String {
        val x = ZplConstants.mmToDots(e.xMm, dpi)
        val y = ZplConstants.mmToDots(e.yMm, dpi)
        val targetWidthDots = ZplConstants.mmToDots(e.widthMm, dpi).coerceAtLeast(1)
        val targetHeightDots = ZplConstants.mmToDots(e.heightMm, dpi).coerceAtLeast(1)

        val source = MonoBitmap.unpack(MonoBitmap.fromBase64(e.sourcePackedMonoBase64), e.sourceWidthDots, e.sourceHeightDots)
        val scaled = if (targetWidthDots == source.width && targetHeightDots == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, targetWidthDots, targetHeightDots, true)
        }
        val rotated = if (e.rotationDeg % 360f == 0f) scaled else rotateBitmap(scaled, e.rotationDeg)

        val packed = MonoBitmap.pack(rotated)
        val bytesPerRow = (rotated.width + 7) / 8
        val totalBytes = bytesPerRow * rotated.height
        return "^FO$x,$y^GFA,$totalBytes,$totalBytes,$bytesPerRow,${packed.toZplHex()}^FS"
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun renderShape(e: LabelElement.ShapeElement, dpi: Int): String {
        val x = ZplConstants.mmToDots(e.xMm, dpi)
        val y = ZplConstants.mmToDots(e.yMm, dpi)
        val w = ZplConstants.mmToDots(e.widthMm, dpi)
        val h = ZplConstants.mmToDots(e.heightMm, dpi)
        val thickness = ZplConstants.mmToDots(e.thicknessMm, dpi).coerceAtLeast(1)
        return "^FO$x,$y^GB$w,$h,$thickness^FS"
    }

    /**
     * Construye una etiqueta ^XA...^XZ con una sola imagen monocroma a pantalla completa (^GFA),
     * del tamaño exacto [widthDots]x[heightDots]. La usa la importación de PDF (p. ej. etiquetas
     * de MercadoLibre ya recortadas y redimensionadas por [com.zplprinter.app.pdf.PdfLabelConverter])
     * para imprimir directamente, sin pasar por el modelo [Label] de elementos editables.
     */
    fun buildImageLabel(
        widthDots: Int,
        heightDots: Int,
        packedMono: ByteArray,
        printSettings: PrintSettings = PrintSettings(),
    ): String {
        val bytesPerRow = (widthDots + 7) / 8
        val totalBytes = bytesPerRow * heightDots
        return buildString {
            append("^XA")
            append("^MN${printSettings.mediaType.zplChar}")
            printSettings.printSpeed?.let { append("^PR$it") }
            printSettings.darkness?.let { append("^MD$it") }
            append("^PW$widthDots")
            append("^LL$heightDots")
            append("^FO0,0")
            append("^GFA,$totalBytes,$totalBytes,$bytesPerRow,")
            append(packedMono.toZplHex())
            append("^FS")
            append("^PQ${printSettings.copies.coerceAtLeast(1)}")
            append("^XZ")
        }
    }

    /**
     * Etiqueta de solo texto libre ("Imprimir texto"), sin pasar por el diseñador: el texto se
     * ajusta solo dentro del ancho del papel con ^FB (salto de línea automático de ZPL).
     */
    fun buildTextLabel(
        text: String,
        widthMm: Float,
        heightMm: Float,
        dpi: Int,
        fontHeightMm: Float = 8f,
        printSettings: PrintSettings = PrintSettings(),
    ): String {
        val widthDots = ZplConstants.mmToDots(widthMm, dpi)
        val heightDots = ZplConstants.mmToDots(heightMm, dpi)
        val marginDots = ZplConstants.mmToDots(4f, dpi)
        val fontDots = ZplConstants.mmToDots(fontHeightMm, dpi).coerceAtLeast(10)
        val blockWidthDots = (widthDots - 2 * marginDots).coerceAtLeast(fontDots)
        val lineHeightDots = (fontDots * 1.2f).roundToInt().coerceAtLeast(1)
        val maxLines = ((heightDots - 2 * marginDots) / lineHeightDots).coerceAtLeast(1)

        return buildString {
            append("^XA")
            append("^CI28")
            append("^MN${printSettings.mediaType.zplChar}")
            printSettings.printSpeed?.let { append("^PR$it") }
            printSettings.darkness?.let { append("^MD$it") }
            append("^PW$widthDots")
            append("^LL$heightDots")
            append("^FO$marginDots,$marginDots")
            append("^A0N,$fontDots,$fontDots")
            append("^FB$blockWidthDots,$maxLines,0,L,0")
            append("^FD${escapeTextBlock(text)}^FS")
            append("^PQ${printSettings.copies.coerceAtLeast(1)}")
            append("^XZ")
        }
    }

    /** Etiqueta de solo QR ("Imprimir QR"), sin pasar por el diseñador. El tamaño se estima a partir del papel cargado. */
    fun buildQrLabel(
        data: String,
        widthMm: Float,
        heightMm: Float,
        dpi: Int,
        printSettings: PrintSettings = PrintSettings(),
    ): String {
        val widthDots = ZplConstants.mmToDots(widthMm, dpi)
        val heightDots = ZplConstants.mmToDots(heightMm, dpi)
        val marginDots = ZplConstants.mmToDots(5f, dpi)
        val magnification = (minOf(widthMm, heightMm) / 25f).roundToInt().coerceIn(1, 10)

        return buildString {
            append("^XA")
            append("^CI28")
            append("^MN${printSettings.mediaType.zplChar}")
            printSettings.printSpeed?.let { append("^PR$it") }
            printSettings.darkness?.let { append("^MD$it") }
            append("^PW$widthDots")
            append("^LL$heightDots")
            append("^FO$marginDots,$marginDots^BQN,2,$magnification")
            append("^FDQA,${escape(data)}^FS")
            append("^PQ${printSettings.copies.coerceAtLeast(1)}")
            append("^XZ")
        }
    }

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    /** Conversión a hex ASCII (formato esperado por ^GFA) con tabla precalculada: evitar String.format por byte importa con etiquetas de cientos de KB. */
    private fun ByteArray.toZplHex(): String {
        val chars = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            chars[i * 2] = HEX_CHARS[v ushr 4]
            chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(chars)
    }

    /** ZPL usa ^ y ~ como prefijos de control; se eliminan del contenido del usuario para no romper el stream. */
    private fun escape(text: String): String = text.replace("^", "").replace("~", "")

    /** Dentro de un bloque ^FB, un salto de línea real del usuario tiene que forzarse con \&; un \n suelto no hace nada en ZPL. */
    private fun escapeTextBlock(text: String): String = escape(text).replace("\n", "\\&")

    /** ZPL solo admite rotaciones en pasos de 90°: N=0°, R=90°, I=180°, B=270°. */
    private fun orientationChar(rotationDeg: Float): Char {
        val normalized = ((rotationDeg % 360) + 360) % 360
        return when {
            normalized < 45f || normalized >= 315f -> 'N'
            normalized < 135f -> 'R'
            normalized < 225f -> 'I'
            else -> 'B'
        }
    }
}
