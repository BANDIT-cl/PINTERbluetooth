package com.zplprinter.app.printservice

import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrinterDiscoverySession
import com.zplprinter.app.data.prefs.AppPreferences

/**
 * Expone una única "impresora" virtual ante el framework de impresión de Android: no hay
 * descubrimiento real de red, porque la impresora ya es la que el usuario configuró dentro
 * de la app (conexión Bluetooth/Wi-Fi, tamaño de papel y dpi en [AppPreferences]). Declararla
 * con ese tamaño exacto como única opción de papel hace que la app de origen (Chrome, Gmail,
 * Word...) renderice su documento ya ajustado al papel cargado, sin que el usuario tenga que
 * elegir un tamaño por su cuenta.
 */
class ZplPrinterDiscoverySession(private val service: ZplPrintService) : PrinterDiscoverySession() {

    override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
        val prefs = AppPreferences(service)
        val printerId = service.generatePrinterId(PRINTER_LOCAL_ID)

        val mediaSize = PrintAttributes.MediaSize(
            PRINTER_LOCAL_ID,
            "${prefs.getPaperWidthMm().toInt()} x ${prefs.getPaperHeightMm().toInt()} mm",
            mmToMils(prefs.getPaperWidthMm()),
            mmToMils(prefs.getPaperHeightMm()),
        )
        val resolution = PrintAttributes.Resolution(PRINTER_LOCAL_ID, "${prefs.getDpi()} dpi", prefs.getDpi(), prefs.getDpi())

        val capabilities = PrinterCapabilitiesInfo.Builder(printerId)
            .addMediaSize(mediaSize, true)
            .addResolution(resolution, true)
            .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val printerInfo = PrinterInfo.Builder(printerId, "Etiquetas ZPL/TSPL", PrinterInfo.STATUS_IDLE)
            .setCapabilities(capabilities)
            .build()

        addPrinters(listOf(printerInfo))
    }

    override fun onStopPrinterDiscovery() = Unit
    override fun onValidatePrinters(printerIds: MutableList<PrinterId>) = Unit
    override fun onStartPrinterStateTracking(printerId: PrinterId) = Unit
    override fun onStopPrinterStateTracking(printerId: PrinterId) = Unit
    override fun onDestroy() = Unit

    private fun mmToMils(mm: Float): Int = (mm / 25.4f * 1000).toInt().coerceAtLeast(1)

    companion object {
        const val PRINTER_LOCAL_ID = "zpl_tspl_printer"
    }
}
