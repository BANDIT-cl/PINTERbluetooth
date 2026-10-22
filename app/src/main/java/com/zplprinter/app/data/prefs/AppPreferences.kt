package com.zplprinter.app.data.prefs

import android.content.Context
import com.zplprinter.app.data.model.PrinterLanguage
import com.zplprinter.app.zpl.MediaType
import com.zplprinter.app.zpl.ZplConstants

/**
 * Preferencias simples que no requieren una tabla relacional: el último
 * tamaño/tipo de papel y los ajustes de impresión (se recuerdan entre
 * impresiones y al abrir la app, igual que la última impresora).
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("zpl_app_prefs", Context.MODE_PRIVATE)

    fun getPaperWidthMm(): Float = prefs.getFloat(KEY_WIDTH_MM, DEFAULT_SIZE_MM)
    fun getPaperHeightMm(): Float = prefs.getFloat(KEY_HEIGHT_MM, DEFAULT_SIZE_MM)
    fun getDpi(): Int = prefs.getInt(KEY_DPI, ZplConstants.DEFAULT_DPI)

    fun savePaperSize(widthMm: Float, heightMm: Float, dpi: Int = getDpi()) {
        prefs.edit()
            .putFloat(KEY_WIDTH_MM, widthMm)
            .putFloat(KEY_HEIGHT_MM, heightMm)
            .putInt(KEY_DPI, dpi)
            .apply()
    }

    fun getMediaType(): MediaType =
        runCatching { MediaType.valueOf(prefs.getString(KEY_MEDIA_TYPE, MediaType.GAP.name)!!) }
            .getOrDefault(MediaType.GAP)

    fun getPrintSpeed(): Int = prefs.getInt(KEY_PRINT_SPEED, DEFAULT_PRINT_SPEED)
    fun getDarkness(): Int = prefs.getInt(KEY_DARKNESS, DEFAULT_DARKNESS)

    fun savePrintSettings(mediaType: MediaType, printSpeed: Int, darkness: Int) {
        prefs.edit()
            .putString(KEY_MEDIA_TYPE, mediaType.name)
            .putInt(KEY_PRINT_SPEED, printSpeed)
            .putInt(KEY_DARKNESS, darkness)
            .apply()
    }

    fun getPrinterLanguage(): PrinterLanguage =
        runCatching { PrinterLanguage.valueOf(prefs.getString(KEY_PRINTER_LANGUAGE, PrinterLanguage.ZPL.name)!!) }
            .getOrDefault(PrinterLanguage.ZPL)

    fun savePrinterLanguage(language: PrinterLanguage) {
        prefs.edit().putString(KEY_PRINTER_LANGUAGE, language.name).apply()
    }

    private companion object {
        const val KEY_WIDTH_MM = "paper_width_mm"
        const val KEY_HEIGHT_MM = "paper_height_mm"
        const val KEY_DPI = "paper_dpi"
        const val KEY_MEDIA_TYPE = "media_type"
        const val KEY_PRINT_SPEED = "print_speed"
        const val KEY_DARKNESS = "darkness"
        const val KEY_PRINTER_LANGUAGE = "printer_language"
        const val DEFAULT_SIZE_MM = 100f
        const val DEFAULT_PRINT_SPEED = 4
        const val DEFAULT_DARKNESS = 15
    }
}
