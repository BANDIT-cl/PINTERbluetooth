package com.zplprinter.app.printservice

import android.os.ParcelFileDescriptor
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import com.zplprinter.app.bluetooth.BluetoothPrinterManager
import com.zplprinter.app.data.db.AppDatabase
import com.zplprinter.app.data.model.ConnectionType
import com.zplprinter.app.data.model.PrinterLanguage
import com.zplprinter.app.data.prefs.AppPreferences
import com.zplprinter.app.data.repository.PrinterRepository
import com.zplprinter.app.pdf.PdfLabelConverter
import com.zplprinter.app.tspl.TsplGenerator
import com.zplprinter.app.zpl.PrintSettings
import com.zplprinter.app.zpl.ZplGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Permite imprimir desde el diálogo nativo "Imprimir" de cualquier app (Chrome, Gmail, Word...)
 * directo a la impresora ZPL/TSPL configurada, sin pasar por "Compartir". El usuario debe
 * habilitarlo una vez en Ajustes > Conexión > Impresión (el framework de Android exige esa
 * activación manual; la app no puede auto-habilitarse). Expone una única impresora virtual
 * ([ZplPrinterDiscoverySession]) con el papel/dpi configurados, renderiza cada página recibida
 * como imagen monocroma -igual que la importación de PDF de MercadoLibre, vía
 * [PdfLabelConverter]- y la envía por el mismo transporte (Bluetooth o Wi-Fi) que la última
 * impresora recordada.
 */
class ZplPrintService : PrintService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession = ZplPrinterDiscoverySession(this)

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJob.cancel()
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        printJob.start()
        serviceScope.launch {
            val result = runCatching { printDocument(printJob) }
            result.onSuccess { printJob.complete() }
            result.onFailure { printJob.fail(it.message ?: "No se pudo imprimir") }
        }
    }

    private suspend fun printDocument(printJob: PrintJob) {
        val prefs = AppPreferences(this)
        val pfd: ParcelFileDescriptor = printJob.document.data ?: error("El documento no tiene datos")

        // El tamaño de papel/dpi ya se le pidió al emisor del documento vía
        // ZplPrinterDiscoverySession, así que el PDF recibido ya debería venir ajustado;
        // PdfLabelConverter igual recorta y reencuadra por si el emisor no respetó el tamaño.
        val images = PdfLabelConverter.convert(pfd, prefs.getPaperWidthMm(), prefs.getPaperHeightMm(), prefs.getDpi())

        val printSettings = PrintSettings(
            copies = printJob.info.copies.coerceAtLeast(1),
            printSpeed = prefs.getPrintSpeed(),
            darkness = prefs.getDarkness(),
            mediaType = prefs.getMediaType(),
        )
        val language = prefs.getPrinterLanguage()

        val bluetoothManager = BluetoothPrinterManager(this)
        val printerRepository = PrinterRepository(AppDatabase.getInstance(this).printerDao())
        try {
            connectToLastUsed(bluetoothManager, printerRepository)
            images.forEach { image ->
                val result = when (language) {
                    PrinterLanguage.ZPL -> bluetoothManager.sendZpl(
                        ZplGenerator.buildImageLabel(image.widthDots, image.heightDots, image.packedMono, printSettings)
                    )
                    PrinterLanguage.TSPL -> bluetoothManager.sendTspl(
                        TsplGenerator.buildImageLabel(image.widthDots, image.heightDots, image.packedMono, printSettings)
                    )
                }
                result.getOrThrow()
            }
        } finally {
            bluetoothManager.disconnect()
        }
    }

    /**
     * Reconecta a la última impresora recordada (Bluetooth o Wi-Fi). Duplica la pieza
     * equivalente de [com.zplprinter.app.ui.printer.PrinterViewModel.ensureConnectedToLastUsed]
     * a propósito: este servicio vive en su propio ciclo de vida de componente, sin Activity ni
     * StateFlow de UI a los que engancharse, así que no puede reusar ese código directamente.
     */
    private suspend fun connectToLastUsed(bluetoothManager: BluetoothPrinterManager, printerRepository: PrinterRepository) {
        val lastUsed = printerRepository.getLastUsed()
            ?: error("No hay ninguna impresora recordada. Abre la app y conecta tu impresora una vez.")

        val result = when (lastUsed.connectionType) {
            ConnectionType.WIFI -> {
                val parts = lastUsed.macAddress.split(":")
                val ip = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
                    ?: error("Dirección Wi-Fi inválida para '${lastUsed.name}'.")
                val port = parts.getOrNull(1)?.toIntOrNull() ?: BluetoothPrinterManager.DEFAULT_WIFI_PORT
                bluetoothManager.connectWifi(ip, port)
            }
            ConnectionType.BLUETOOTH -> {
                val device = bluetoothManager.deviceForAddress(lastUsed.macAddress)
                    ?: error("No se encontró la impresora '${lastUsed.name}'. Verifica que esté emparejada y encendida.")
                bluetoothManager.connect(device)
            }
        }
        result.getOrThrow()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
