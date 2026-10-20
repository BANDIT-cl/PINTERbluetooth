package com.zplprinter.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Simbologías de código de barras soportadas; cada una usa un comando ZPL distinto (^BC, ^B3, ^BE, ^BU, ^B2). */
enum class BarcodeSymbology(val displayName: String) {
    CODE128("Code 128"),
    CODE39("Code 39"),
    EAN13("EAN-13"),
    UPC_A("UPC-A"),
    ITF("Interleaved 2 of 5"),
}

/** Origen del contenido de un [LabelElement.TextElement]: texto fijo, un contador autoincremental o la fecha actual. */
enum class TextDataType { STATIC, COUNTER, DATE }

/**
 * Un elemento dibujado en el lienzo del diseñador. Las coordenadas y
 * dimensiones están siempre en milímetros, relativas a la esquina
 * superior izquierda de la etiqueta; ZplGenerator las convierte a dots.
 */
@Serializable
sealed class LabelElement {
    abstract val id: String
    abstract val xMm: Float
    abstract val yMm: Float
    abstract val rotationDeg: Float
    /** Si está bloqueado, el lienzo ignora arrastres de mover/redimensionar y los botones Rotar/Ampliar/Reducir. */
    abstract val locked: Boolean

    abstract fun withPosition(newXMm: Float, newYMm: Float): LabelElement
    abstract fun withRotation(newRotationDeg: Float): LabelElement
    abstract fun withLocked(locked: Boolean): LabelElement

    @Serializable
    @SerialName("text")
    data class TextElement(
        override val id: String,
        override val xMm: Float,
        override val yMm: Float,
        override val rotationDeg: Float = 0f,
        override val locked: Boolean = false,
        val text: String,
        val fontHeightMm: Float = 5f,
        val fontWidthMm: Float = 5f,
        val dataType: TextDataType = TextDataType.STATIC,
        /** Valor del contador en la primera copia impresa. Solo aplica si [dataType] es COUNTER. */
        val counterStart: Int = 1,
        /** Cuánto aumenta el contador entre una copia y la siguiente. */
        val counterStep: Int = 1,
        /** Cantidad de dígitos del contador, rellenando con ceros a la izquierda (p. ej. 3 → "007"). */
        val counterDigits: Int = 1,
    ) : LabelElement() {
        override fun withPosition(newXMm: Float, newYMm: Float) = copy(xMm = newXMm, yMm = newYMm)
        override fun withRotation(newRotationDeg: Float) = copy(rotationDeg = newRotationDeg)
        override fun withLocked(locked: Boolean) = copy(locked = locked)

        /** El texto a imprimir en la copia [copyIndex] (0-based): fijo, el valor del contador en ese paso, o la fecha de hoy. */
        fun resolvedText(copyIndex: Int): String = when (dataType) {
            TextDataType.STATIC -> text
            TextDataType.COUNTER -> (counterStart + copyIndex * counterStep)
                .toString()
                .padStart(counterDigits, '0')
            TextDataType.DATE -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        }
    }

    @Serializable
    @SerialName("barcode")
    data class BarcodeElement(
        override val id: String,
        override val xMm: Float,
        override val yMm: Float,
        override val rotationDeg: Float = 0f,
        override val locked: Boolean = false,
        val data: String,
        val heightMm: Float = 15f,
        val moduleWidthDots: Float = 2f,
        val humanReadable: Boolean = true,
        val symbology: BarcodeSymbology = BarcodeSymbology.CODE128,
    ) : LabelElement() {
        override fun withPosition(newXMm: Float, newYMm: Float) = copy(xMm = newXMm, yMm = newYMm)
        override fun withRotation(newRotationDeg: Float) = copy(rotationDeg = newRotationDeg)
        override fun withLocked(locked: Boolean) = copy(locked = locked)
    }

    @Serializable
    @SerialName("qr")
    data class QrElement(
        override val id: String,
        override val xMm: Float,
        override val yMm: Float,
        override val rotationDeg: Float = 0f,
        override val locked: Boolean = false,
        val data: String,
        val magnification: Float = 5f,
    ) : LabelElement() {
        override fun withPosition(newXMm: Float, newYMm: Float) = copy(xMm = newXMm, yMm = newYMm)
        override fun withRotation(newRotationDeg: Float) = copy(rotationDeg = newRotationDeg)
        override fun withLocked(locked: Boolean) = copy(locked = locked)
    }

    @Serializable
    @SerialName("shape")
    data class ShapeElement(
        override val id: String,
        override val xMm: Float,
        override val yMm: Float,
        override val rotationDeg: Float = 0f,
        override val locked: Boolean = false,
        val widthMm: Float,
        val heightMm: Float,
        val thicknessMm: Float = 0.5f,
    ) : LabelElement() {
        override fun withPosition(newXMm: Float, newYMm: Float) = copy(xMm = newXMm, yMm = newYMm)
        override fun withRotation(newRotationDeg: Float) = copy(rotationDeg = newRotationDeg)
        override fun withLocked(locked: Boolean) = copy(locked = locked)
    }
}
