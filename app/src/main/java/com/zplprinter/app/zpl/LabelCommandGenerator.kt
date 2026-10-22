package com.zplprinter.app.zpl

import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.model.PrinterLanguage
import com.zplprinter.app.tspl.TsplGenerator

/**
 * Fachada que elige entre [ZplGenerator] y [TsplGenerator] según el lenguaje configurado,
 * para que las pantallas (diseñador, importación de PDF, impresión rápida) no necesiten
 * saber qué protocolo habla la impresora conectada.
 */
object LabelCommandGenerator {

    fun generate(label: Label, printSettings: PrintSettings, language: PrinterLanguage): String =
        when (language) {
            PrinterLanguage.ZPL -> ZplGenerator.generate(label, printSettings)
            PrinterLanguage.TSPL -> TsplGenerator.generate(label, printSettings)
        }

    fun buildImageLabel(
        widthDots: Int,
        heightDots: Int,
        packedMono: ByteArray,
        printSettings: PrintSettings,
        language: PrinterLanguage,
    ): String = when (language) {
        PrinterLanguage.ZPL -> ZplGenerator.buildImageLabel(widthDots, heightDots, packedMono, printSettings)
        PrinterLanguage.TSPL -> TsplGenerator.buildImageLabel(widthDots, heightDots, packedMono, printSettings)
    }

    fun buildTextLabel(
        text: String,
        widthMm: Float,
        heightMm: Float,
        dpi: Int,
        fontHeightMm: Float,
        printSettings: PrintSettings,
        language: PrinterLanguage,
    ): String = when (language) {
        PrinterLanguage.ZPL -> ZplGenerator.buildTextLabel(text, widthMm, heightMm, dpi, fontHeightMm, printSettings)
        PrinterLanguage.TSPL -> TsplGenerator.buildTextLabel(text, widthMm, heightMm, dpi, fontHeightMm, printSettings)
    }

    fun buildQrLabel(
        data: String,
        widthMm: Float,
        heightMm: Float,
        dpi: Int,
        printSettings: PrintSettings,
        language: PrinterLanguage,
    ): String = when (language) {
        PrinterLanguage.ZPL -> ZplGenerator.buildQrLabel(data, widthMm, heightMm, dpi, printSettings)
        PrinterLanguage.TSPL -> TsplGenerator.buildQrLabel(data, widthMm, heightMm, dpi, printSettings)
    }
}
