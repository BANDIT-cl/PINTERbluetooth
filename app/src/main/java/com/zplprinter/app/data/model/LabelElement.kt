package com.zplprinter.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Simbologías de código de barras soportadas; cada una usa un comando ZPL distinto (^BC, ^B3, ^BE, ^BU, ^B2). */
enum class BarcodeSymbology(val displayName: String) {
    CODE128("Code 128"),
    CODE39("Code 39"),
    EAN13("EAN-13"),
    UPC_A("UPC-A"),
    ITF("Interleaved 2 of 5"),
}

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

    abstract fun withPosition(newXMm: Float, newYMm: Float): LabelElement
    abstract fun withRotation(newRotationDeg: Float): LabelElement

    @Serializable
    @SerialName("text")
    data class TextElement(
        override val id: String,
        override val xMm: Float,
        override val yMm: Float,
        override val rotationDeg: Float = 0f,
        val text: String,
        val fontHeightMm: Float = 5f,
        val fontWidthMm: Float = 5f,
    ) : LabelElement() {
        override fun withPosition(newXMm: Float, newYMm: Float) = copy(xMm = newXMm, yMm = newYMm)
        override fun withRotation(newRotationDeg: Float) = copy(rotationDeg = newRotationDeg)
    }

    @Serializable
    @SerialName("barcode")
    data class BarcodeElement(
        override val id: String,
        override val xMm: Float,
        override val yMm: Float,
        override val rotationDeg: Float = 0f,
        val data: String,
        val heightMm: Float = 15f,
        val moduleWidthDots: Float = 2f,
        val humanReadable: Boolean = true,
        val symbology: BarcodeSymbology = BarcodeSymbology.CODE128,
    ) : LabelElement() {
        override fun withPosition(newXMm: Float, newYMm: Float) = copy(xMm = newXMm, yMm = newYMm)
        override fun withRotation(newRotationDeg: Float) = copy(rotationDeg = newRotationDeg)
    }

    @Serializable
    @SerialName("qr")
    data class QrElement(
        override val id: String,
        override val xMm: Float,
        override val yMm: Float,
        override val rotationDeg: Float = 0f,
        val data: String,
        val magnification: Float = 5f,
    ) : LabelElement() {
        override fun withPosition(newXMm: Float, newYMm: Float) = copy(xMm = newXMm, yMm = newYMm)
        override fun withRotation(newRotationDeg: Float) = copy(rotationDeg = newRotationDeg)
    }

    @Serializable
    @SerialName("shape")
    data class ShapeElement(
        override val id: String,
        override val xMm: Float,
        override val yMm: Float,
        override val rotationDeg: Float = 0f,
        val widthMm: Float,
        val heightMm: Float,
        val thicknessMm: Float = 0.5f,
    ) : LabelElement() {
        override fun withPosition(newXMm: Float, newYMm: Float) = copy(xMm = newXMm, yMm = newYMm)
        override fun withRotation(newRotationDeg: Float) = copy(rotationDeg = newRotationDeg)
    }
}
