package com.zplprinter.app.zpl

/** Tamaño de etiqueta en milímetros. */
data class PaperSize(val label: String, val widthMm: Float, val heightMm: Float)

object ZplConstants {
    /** DPI nativo de la Xprinter XP-420B (8 puntos por mm). */
    const val DEFAULT_DPI = 203

    val PRESETS = listOf(
        PaperSize("40 x 30 mm", 40f, 30f),
        PaperSize("50 x 30 mm", 50f, 30f),
        PaperSize("58 x 40 mm", 58f, 40f),
        PaperSize("100 x 100 mm", 100f, 100f),
        PaperSize("100 x 150 mm", 100f, 150f),
        PaperSize("101 x 150 mm", 101f, 150f),
    )

    fun mmToDots(mm: Float, dpi: Int): Int = (mm * dpi / 25.4f).toInt()
}
