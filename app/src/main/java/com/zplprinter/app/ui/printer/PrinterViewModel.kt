package com.zplprinter.app.ui.printer

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zplprinter.app.bluetooth.BluetoothPrinterManager
import com.zplprinter.app.data.db.AppDatabase
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.model.LabelElement
import com.zplprinter.app.data.prefs.AppPreferences
import com.zplprinter.app.data.repository.PrinterRepository
import com.zplprinter.app.zpl.MediaType
import com.zplprinter.app.zpl.PrintSettings
import com.zplprinter.app.zpl.ZplConstants
import com.zplprinter.app.zpl.ZplGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class PrinterUiState(
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val discoveredDevices: List<BluetoothDevice> = emptyList(),
    val isScanning: Boolean = false,
    val selectedDevice: BluetoothDevice? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val errorMessage: String? = null,
    val widthMm: Float = 100f,
    val heightMm: Float = 100f,
    val dpi: Int = ZplConstants.DEFAULT_DPI,
    val isPrinting: Boolean = false,
    val lastPrintOk: Boolean? = null,
    val rememberedPrinterName: String? = null,
    val mediaType: MediaType = MediaType.GAP,
    val printSpeed: Int = 4,
    val darkness: Int = 15,
    val isCalibrating: Boolean = false,
    val isQueryingStatus: Boolean = false,
    val printerStatus: String? = null,
)

class PrinterViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothManager = BluetoothPrinterManager(application)
    private val printerRepository = PrinterRepository(AppDatabase.getInstance(application).printerDao())
    private val appPreferences = AppPreferences(application)

    private val _uiState = MutableStateFlow(
        PrinterUiState(
            widthMm = appPreferences.getPaperWidthMm(),
            heightMm = appPreferences.getPaperHeightMm(),
            dpi = appPreferences.getDpi(),
            mediaType = appPreferences.getMediaType(),
            printSpeed = appPreferences.getPrintSpeed(),
            darkness = appPreferences.getDarkness(),
        )
    )
    val uiState: StateFlow<PrinterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val lastUsed = printerRepository.getLastUsed()
            _uiState.update { it.copy(rememberedPrinterName = lastUsed?.name) }
        }
    }

    fun refreshBondedDevices() {
        _uiState.update { it.copy(bondedDevices = bluetoothManager.bondedDevices()) }
    }

    /** Busca impresoras cercanas no emparejadas todavía (p. ej. recién encendidas). */
    fun startScan() {
        _uiState.update { it.copy(discoveredDevices = emptyList(), isScanning = true) }
        bluetoothManager.startDiscovery(
            onDeviceFound = { device ->
                _uiState.update { state ->
                    val alreadyKnown = state.discoveredDevices.any { it.address == device.address } ||
                        state.bondedDevices.any { it.address == device.address }
                    if (alreadyKnown) state else state.copy(discoveredDevices = state.discoveredDevices + device)
                }
            },
            onFinished = { _uiState.update { it.copy(isScanning = false) } }
        )
    }

    fun stopScan() {
        bluetoothManager.stopDiscovery()
        _uiState.update { it.copy(isScanning = false) }
    }

    /** Autocalibración del sensor de papel (~JC): corrige el desalineado típico tras cambiar el rollo. */
    fun calibrateSensor() {
        if (_uiState.value.connectionState != ConnectionState.CONNECTED) return
        _uiState.update { it.copy(isCalibrating = true, lastPrintOk = null) }
        viewModelScope.launch {
            val result = bluetoothManager.sendZpl(ZplGenerator.CALIBRATE_SENSOR_CMD)
            _uiState.update {
                it.copy(
                    isCalibrating = false,
                    lastPrintOk = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * Consulta el estado de la impresora (~HS) y muestra la respuesta cruda. Algunas impresoras
     * ZPL "compatibles" (no Zebra originales, como muchas Xprinter) no implementan esta consulta
     * y simplemente no responden; eso se reporta como error, no como estado vacío.
     */
    fun queryPrinterStatus() {
        if (_uiState.value.connectionState != ConnectionState.CONNECTED) return
        _uiState.update { it.copy(isQueryingStatus = true, printerStatus = null, errorMessage = null) }
        viewModelScope.launch {
            val result = bluetoothManager.queryStatus(ZplGenerator.QUERY_STATUS_CMD)
            _uiState.update {
                it.copy(
                    isQueryingStatus = false,
                    printerStatus = result.getOrNull(),
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else null
                )
            }
        }
    }

    fun selectPaperSize(widthMm: Float, heightMm: Float) {
        appPreferences.savePaperSize(widthMm, heightMm)
        _uiState.update { it.copy(widthMm = widthMm, heightMm = heightMm) }
    }

    fun updatePrintSettings(mediaType: MediaType, printSpeed: Int, darkness: Int) {
        appPreferences.savePrintSettings(mediaType, printSpeed, darkness)
        _uiState.update { it.copy(mediaType = mediaType, printSpeed = printSpeed, darkness = darkness) }
    }

    fun currentPrintSettings(copies: Int = 1): PrintSettings {
        val state = _uiState.value
        return PrintSettings(
            copies = copies,
            printSpeed = state.printSpeed,
            darkness = state.darkness,
            mediaType = state.mediaType,
        )
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        _uiState.update {
            it.copy(selectedDevice = device, connectionState = ConnectionState.CONNECTING, errorMessage = null)
        }
        viewModelScope.launch {
            val result = bluetoothManager.connect(device)
            if (result.isSuccess) {
                val name = bluetoothManager.nameOf(device)
                printerRepository.rememberConnected(device.address, name)
                _uiState.update {
                    it.copy(connectionState = ConnectionState.CONNECTED, rememberedPrinterName = name)
                }
            } else {
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.ERROR,
                        errorMessage = result.exceptionOrNull()?.message ?: "No se pudo conectar"
                    )
                }
            }
        }
    }

    fun disconnect() {
        bluetoothManager.disconnect()
        _uiState.update { it.copy(connectionState = ConnectionState.DISCONNECTED, selectedDevice = null) }
    }

    /**
     * Prueba temporal de M3: imprime un [Label] con los 4 tipos de elemento
     * (texto, código de barras, QR, línea) usando [ZplGenerator.generate],
     * para validar la traducción completa antes de construir el lienzo (M4).
     */
    fun printSampleElements() {
        val state = _uiState.value
        if (state.connectionState != ConnectionState.CONNECTED) return

        _uiState.update { it.copy(isPrinting = true, lastPrintOk = null) }
        viewModelScope.launch {
            val sampleLabel = Label(
                name = "Muestra M3",
                widthMm = state.widthMm,
                heightMm = state.heightMm,
                dpi = state.dpi,
                elements = listOf(
                    LabelElement.TextElement(
                        id = "title",
                        xMm = 5f,
                        yMm = 5f,
                        text = "MUESTRA M3",
                        fontHeightMm = 6f,
                        fontWidthMm = 6f,
                    ),
                    LabelElement.BarcodeElement(
                        id = "barcode",
                        xMm = 5f,
                        yMm = 18f,
                        data = "123456789",
                        heightMm = 15f,
                    ),
                    LabelElement.QrElement(
                        id = "qr",
                        xMm = 5f,
                        yMm = 42f,
                        data = "https://claude.ai",
                        magnification = 4f,
                    ),
                    LabelElement.ShapeElement(
                        id = "line",
                        xMm = 5f,
                        yMm = 80f,
                        widthMm = (state.widthMm - 10f).coerceAtLeast(1f),
                        heightMm = 0f,
                        thicknessMm = 0.5f,
                    ),
                ),
            )
            val zpl = ZplGenerator.generate(sampleLabel, currentPrintSettings())
            val result = bluetoothManager.sendZpl(zpl)
            _uiState.update {
                it.copy(
                    isPrinting = false,
                    lastPrintOk = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun printTestLabel() {
        val state = _uiState.value
        if (state.connectionState != ConnectionState.CONNECTED) return

        _uiState.update { it.copy(isPrinting = true, lastPrintOk = null) }
        viewModelScope.launch {
            val zpl = ZplGenerator.buildTestLabel(state.widthMm, state.heightMm, state.dpi)
            val result = bluetoothManager.sendZpl(zpl)
            _uiState.update {
                it.copy(
                    isPrinting = false,
                    lastPrintOk = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * Imprime ZPL recibido desde otra app (menú "Compartir"). Reconecta a la
     * última impresora recordada si es necesario; no requiere que el usuario
     * haya abierto antes la pantalla de configuración en esta sesión.
     */
    fun printSharedZpl(zpl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPrinting = true, lastPrintOk = null, errorMessage = null) }

            if (_uiState.value.connectionState != ConnectionState.CONNECTED) {
                val lastUsed = printerRepository.getLastUsed()
                if (lastUsed == null) {
                    _uiState.update {
                        it.copy(
                            isPrinting = false,
                            lastPrintOk = false,
                            errorMessage = "No hay ninguna impresora recordada. Abre la app, conecta tu Xprinter una vez y vuelve a intentarlo."
                        )
                    }
                    return@launch
                }
                val device = bluetoothManager.deviceForAddress(lastUsed.macAddress)
                if (device == null) {
                    _uiState.update {
                        it.copy(
                            isPrinting = false,
                            lastPrintOk = false,
                            errorMessage = "No se encontró la impresora '${lastUsed.name}'. Verifica que esté emparejada y encendida."
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(selectedDevice = device, connectionState = ConnectionState.CONNECTING)
                }
                val connectResult = bluetoothManager.connect(device)
                if (connectResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isPrinting = false,
                            connectionState = ConnectionState.ERROR,
                            lastPrintOk = false,
                            errorMessage = connectResult.exceptionOrNull()?.message
                                ?: "No se pudo conectar a '${lastUsed.name}'"
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(connectionState = ConnectionState.CONNECTED) }
            }

            val result = bluetoothManager.sendZpl(zpl)
            _uiState.update {
                it.copy(
                    isPrinting = false,
                    lastPrintOk = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    override fun onCleared() {
        bluetoothManager.disconnect()
        super.onCleared()
    }
}
