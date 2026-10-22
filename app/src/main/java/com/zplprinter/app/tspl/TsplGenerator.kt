package com.zplprinter.app.tspl

import android.graphics.Bitmap
import android.graphics.Matrix
import com.zplprinter.app.data.model.BarcodeSymbology
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.model.LabelElement
import com.zplprinter.app.data.model.TextDataType
import com.zplprinter.app.image.MonoBitmap
import com.zplprinter.app.zpl.MediaType
import com.zplprinter.app.zpl.PrintSettings
import com.zplprinter.app.zpl.ZplConstants
import kotlin.math.roundToInt

/**
 * Generador de TSPL (TSC Printer Language), construido siguiendo el manual oficial
 * "TSC TSPL/TSPL2 Programming Manual". No probado contra una impresora TSC real (el
 * usuario solo tiene una Xprinter ZPL); los comandos se verificaron uno por uno contra
 * el manual, pero valores por defecto como DIRECTION/REFERENCE o el tamaño de gap pueden
 * necesitar ajuste fino en hardware real.
 *
 * A diferencia de ZPL (que usa ^GFA con datos hex-ASCII), BITMAP de TSPL requiere los
 * bytes crudos de la imagen embebidos directamente en el comando. Por eso esta clase
 * devuelve String usando [toRawLatin1String] (round-trip exacto byte<->char vía ISO-8859-1
 * para códigos 0-255) y [com.zplprinter.app.bluetooth.BluetoothPrinterManager.sendTspl]
 * debe codificar el String final con Charsets.ISO_8859_1, nunca UTF-8.
 */
object TsplGenerator {

    /** Autocalibración por sensor de gap, sin parámetros (detección automática). */
    const val CALIBRATE_GAP_CMD = "GAPDETECT\r\n"

    /** Autocalibración por sensor de marca negra, sin parámetros (detección automática). */
    const val CALIBRATE_BLINE_CMD = "BLINEDETECT\r\n"

    fun buildTestLabel(widthMm: Float, heightMm: Float, dpi: Int): String {
        val widthDots = ZplConstants.mmToDots(widthMm, dpi)
        return buildString {
            append(header(widthMm, heightMm, PrintSettings()))
            append("TEXT 20,20,\"3\",0,1,2,\"PRUEBA DE IMPRESION\"\r\n")
            append("TEXT 20,70,\"3\",0,1,1,\"${widthMm.toInt()}x${heightMm.toInt()} mm @ ${dpi}dpi\"\r\n")
            append("BOX 20,110,${widthDots - 20},113,3\r\n")
            append("PRINT 1,1\r\n")
        }
    }

    /** Traduce una [Label] completa (texto, código de barras, QR, líneas/cajas, imagen) a TSPL. */
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
        return buildString {
            append(header(label.widthMm, label.heightMm, printSettings))
            label.elements.forEach { element -> append(renderElement(element, label.dpi, copyIndex)) }
            append("PRINT $copiesInBlock,1\r\n")
        }
    }

    /**
     * Cabecera común: tamaño, sensor de medio, velocidad/oscuridad, dirección/origen,
     * página de códigos (CODEPAGE 1252, necesaria para que ñ/acentos no lleguen corruptos)
     * y limpieza del buffer (CLS, que debe ir después de SIZE).
     */
    private fun header(widthMm: Float, heightMm: Float, printSettings: PrintSettings): String = buildString {
        append("SIZE ${trimZero(widthMm)} mm,${trimZero(heightMm)} mm\r\n")
        append(mediaTypeCommand(printSettings.mediaType))
        printSettings.printSpeed?.let { append("SPEED $it\r\n") }
        printSettings.darkness?.let { append("DENSITY ${toTsplDensity(it)}\r\n") }
        append("DIRECTION 1,0\r\n")
        append("REFERENCE 0,0\r\n")
        append("CODEPAGE 1252\r\n")
        append("CLS\r\n")
    }

    /** GAP/BLINE requieren un largo de separación explícito, a diferencia de ^MN en ZPL (que solo cambia el modo del sensor); se usa un valor por defecto razonable ya que la app no pide calibrar el largo exacto al usuario. */
    private fun mediaTypeCommand(mediaType: MediaType): String = when (mediaType) {
        MediaType.GAP -> "GAP 3 mm,0\r\n"
        MediaType.CONTINUOUS -> "GAP 0,0\r\n"
        MediaType.BLACK_MARK -> "BLINE 3 mm,0\r\n"
    }

    /** ZPL's ^MD va de 0-30; DENSITY de TSPL va de 0-15. */
    private fun toTsplDensity(zplStyleDarkness: Int): Int = (zplStyleDarkness / 2f).roundToInt().coerceIn(0, 15)

    private fun trimZero(mm: Float): String =
        if (mm == mm.toInt().toFloat()) mm.toInt().toString() else mm.toString()

    private fun renderElement(element: LabelElement, dpi: Int, copyIndex: Int): String = when (element) {
        is LabelElement.TextElement -> renderText(element, dpi, copyIndex)
        is LabelElement.BarcodeElement -> renderBarcode(element, dpi)
        is LabelElement.QrElement -> renderQr(element, dpi)
        is LabelElement.ShapeElement -> renderShape(element, dpi)
        is LabelElement.ImageElement -> renderImage(element, dpi)
    }

    /** Ancho/alto base (en dots, a 1x) de la fuente fija "3" (16 x 24) usada como equivalente a ^A0 escalable de ZPL. */
    private const val BASE_FONT_WIDTH_DOTS = 16
    private const val BASE_FONT_HEIGHT_DOTS = 24

    private fun renderText(e: LabelElement.TextElement, dpi: Int, copyIndex: Int): String {
        val x = ZplConstants.mmToDots(e.xMm, dpi)
        val y = ZplConstants.mmToDots(e.yMm, dpi)
        val rotation = rotationDegrees(e.rotationDeg)
        val xMult = (ZplConstants.mmToDots(e.fontWidthMm, dpi) / BASE_FONT_WIDTH_DOTS.toFloat()).roundToInt().coerceIn(1, 10)
        val yMult = (ZplConstants.mmToDots(e.fontHeightMm, dpi) / BASE_FONT_HEIGHT_DOTS.toFloat()).roundToInt().coerceIn(1, 10)
        return "TEXT $x,$y,\"3\",$rotation,$xMult,$yMult,\"${escapeQuoted(e.resolvedText(copyIndex))}\"\r\n"
    }

    private fun renderBarcode(e: LabelElement.BarcodeElement, dpi: Int): String {
        val x = ZplConstants.mmToDots(e.xMm, dpi)
        val y = ZplConstants.mmToDots(e.yMm, dpi)
        val h = ZplConstants.mmToDots(e.heightMm, dpi)
        val rotation = rotationDegrees(e.rotationDeg)
        val readable = if (e.humanReadable) 1 else 0
        val narrow = e.moduleWidthDots.roundToInt().coerceIn(1, 10)
        val wide = (narrow * 2).coerceIn(1, 10)
        val codeType = when (e.symbology) {
            BarcodeSymbology.CODE128 -> "128"
            BarcodeSymbology.CODE39 -> "39"
            BarcodeSymbology.EAN13 -> "EAN13"
            BarcodeSymbology.UPC_A -> "UPCA"
            BarcodeSymbology.ITF -> "25"
        }
        return "BARCODE $x,$y,\"$codeType\",$h,$readable,$rotation,$narrow,$wide,\"${escapeQuoted(e.data)}\"\r\n"
    }

    private fun renderQr(e: LabelElement.QrElement, dpi: Int): String {
        val x = ZplConstants.mmToDots(e.xMm, dpi)
        val y = ZplConstants.mmToDots(e.yMm, dpi)
        val rotation = rotationDegrees(e.rotationDeg)
        val cellWidth = e.magnification.roundToInt().coerceIn(1, 10)
        return "QRCODE $x,$y,M,$cellWidth,A,$rotation,\"${escapeQuoted(e.data)}\"\r\n"
    }

    private fun renderShape(e: LabelElement.ShapeElement, dpi: Int): String {
        val x1 = ZplConstants.mmToDots(e.xMm, dpi)
        val y1 = ZplConstants.mmToDots(e.yMm, dpi)
        val thickness = ZplConstants.mmToDots(e.thicknessMm, dpi).coerceAtLeast(1)
        val w = ZplConstants.mmToDots(e.widthMm, dpi).coerceAtLeast(thickness)
        val h = ZplConstants.mmToDots(e.heightMm, dpi).coerceAtLeast(thickness)
        return "BOX $x1,$y1,${x1 + w},${y1 + h},$thickness\r\n"
    }

    /** A diferencia de ^GFA de ZPL (hex-ASCII), BITMAP de TSPL necesita los bytes crudos; se empaquetan igual que en MonoBitmap (1=negro, MSB primero) y se insertan vía round-trip Latin-1. */
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

        return buildBitmapCommand(x, y, rotated)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Construye una etiqueta con una sola imagen monocroma a pantalla completa (BITMAP),
     * del tamaño exacto [widthDots]x[heightDots]. La usa la importación de PDF y la
     * impresión rápida de imagen, igual que [com.zplprinter.app.zpl.ZplGenerator.buildImageLabel].
     */
    fun buildImageLabel(
        widthDots: Int,
        heightDots: Int,
        packedMono: ByteArray,
        printSettings: PrintSettings = PrintSettings(),
    ): String {
        val bytesPerRow = (widthDots + 7) / 8
        val widthMm = widthDots * 25.4f / ZplConstants.DEFAULT_DPI
        val heightMm = heightDots * 25.4f / ZplConstants.DEFAULT_DPI
        return buildString {
            append(header(widthMm, heightMm, printSettings))
            append("BITMAP 0,0,$bytesPerRow,$heightDots,0,")
            append(String(packedMono, Charsets.ISO_8859_1))
            append("\r\n")
            append("PRINT ${printSettings.copies.coerceAtLeast(1)},1\r\n")
        }
    }

    private fun buildBitmapCommand(x: Int, y: Int, bitmap: Bitmap): String {
        val packed = MonoBitmap.pack(bitmap)
        val bytesPerRow = (bitmap.width + 7) / 8
        return buildString {
            append("BITMAP $x,$y,$bytesPerRow,${bitmap.height},0,")
            append(String(packed, Charsets.ISO_8859_1))
            append("\r\n")
        }
    }

    /**
     * Etiqueta de solo texto libre ("Imprimir texto"), sin pasar por el diseñador. TSPL no
     * tiene un equivalente a ^FB (ajuste automático de línea de ZPL), así que el ajuste de
     * línea se calcula a mano según el ancho de carácter de la fuente fija elegida.
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
        val mult = (ZplConstants.mmToDots(fontHeightMm, dpi) / BASE_FONT_HEIGHT_DOTS.toFloat()).roundToInt().coerceIn(1, 10)
        val charWidthDots = BASE_FONT_WIDTH_DOTS * mult
        val lineHeightDots = (BASE_FONT_HEIGHT_DOTS * mult * 1.2f).roundToInt().coerceAtLeast(1)
        val blockWidthDots = (widthDots - 2 * marginDots).coerceAtLeast(charWidthDots)
        val maxCharsPerLine = (blockWidthDots / charWidthDots).coerceAtLeast(1)
        val maxLines = ((heightDots - 2 * marginDots) / lineHeightDots).coerceAtLeast(1)

        val lines = wrapText(text, maxCharsPerLine).take(maxLines)

        return buildString {
            append(header(widthMm, heightMm, printSettings))
            lines.forEachIndexed { index, line ->
                val y = marginDots + index * lineHeightDots
                append("TEXT $marginDots,$y,\"3\",0,$mult,$mult,\"${escapeQuoted(line)}\"\r\n")
            }
            append("PRINT ${printSettings.copies.coerceAtLeast(1)},1\r\n")
        }
    }

    /** Ajuste de línea simple por palabras; si una palabra sola supera el ancho, se corta igual (no hay guionado). */
    private fun wrapText(text: String, maxCharsPerLine: Int): List<String> {
        val lines = mutableListOf<String>()
        text.split("\n").forEach { paragraph ->
            if (paragraph.isEmpty()) {
                lines.add("")
                return@forEach
            }
            var current = StringBuilder()
            paragraph.split(" ").forEach { word ->
                val candidate = if (current.isEmpty()) word else "${current} $word"
                if (candidate.length > maxCharsPerLine) {
                    if (current.isNotEmpty()) lines.add(current.toString())
                    current = StringBuilder(word.take(maxCharsPerLine))
                } else {
                    current = StringBuilder(candidate)
                }
            }
            if (current.isNotEmpty()) lines.add(current.toString())
        }
        return lines
    }

    /**
     * Etiqueta de solo QR ("Imprimir QR"), sin pasar por el diseñador. El tamaño base se estima a partir
     * del papel cargado; [sizeScale] (0-1] permite achicarlo respecto a ese tamaño base.
     */
    fun buildQrLabel(
        data: String,
        widthMm: Float,
        heightMm: Float,
        dpi: Int,
        printSettings: PrintSettings = PrintSettings(),
        sizeScale: Float = 1f,
    ): String {
        val marginDots = ZplConstants.mmToDots(5f, dpi)
        val cellWidth = ((minOf(widthMm, heightMm) / 25f) * sizeScale.coerceIn(0.05f, 1f)).roundToInt().coerceIn(1, 10)

        return buildString {
            append(header(widthMm, heightMm, printSettings))
            append("QRCODE $marginDots,$marginDots,M,$cellWidth,A,0,\"${escapeQuoted(data)}\"\r\n")
            append("PRINT ${printSettings.copies.coerceAtLeast(1)},1\r\n")
        }
    }

    /** TSPL solo admite rotaciones en pasos de 90°: 0, 90, 180, 270 grados (sentido horario). */
    private fun rotationDegrees(rotationDeg: Float): Int {
        val normalized = ((rotationDeg % 360) + 360) % 360
        return when {
            normalized < 45f || normalized >= 315f -> 0
            normalized < 135f -> 90
            normalized < 225f -> 180
            else -> 270
        }
    }

    /** Dentro de "..." de TSPL, las comillas y barras invertidas del contenido del usuario deben escaparse para no romper el comando. */
    private fun escapeQuoted(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"")
}
