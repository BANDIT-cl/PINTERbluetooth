package com.zplprinter.app.zpl

import com.zplprinter.app.data.model.BarcodeSymbology
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.model.LabelElement
import com.zplprinter.app.data.model.TextDataType
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

    private fun renderShape(e: LabelElement.ShapeElement, dpi: Int): String {
        val x = ZplConstants.mmToDots(e.xMm, dpi)
        val y = ZplConstants.mmToDots(e.yMm, dpi)
        val w = ZplConstants.mmToDots(e.widthMm, dpi)
        val h = ZplConstants.mmToDots(e.heightMm, dpi)
        val thickness = ZplConstants.mmToDots(e.thicknessMm, dpi).coerceAtLeast(1)
        return "^FO$x,$y^GB$w,$h,$thickness^FS"
    }

    /** ZPL usa ^ y ~ como prefijos de control; se eliminan del contenido del usuario para no romper el stream. */
    private fun escape(text: String): String = text.replace("^", "").replace("~", "")

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
