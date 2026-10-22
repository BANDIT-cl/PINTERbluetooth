package com.zplprinter.app.ui.printer

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zplprinter.app.bluetooth.BluetoothPrinterManager
import com.zplprinter.app.data.db.AppDatabase
import com.zplprinter.app.data.model.ConnectionType
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.model.LabelElement
import com.zplprinter.app.data.model.PrintRecord
import com.zplprinter.app.data.model.PrinterLanguage
import com.zplprinter.app.data.model.PrinterProfile
import com.zplprinter.app.data.prefs.AppPreferences
import com.zplprinter.app.data.repository.PrintRecordRepository
import com.zplprinter.app.data.repository.PrinterRepository
import com.zplprinter.app.tspl.TsplGenerator
import com.zplprinter.app.zpl.MediaType
import com.zplprinter.app.zpl.PrintSettings
import com.zplprinter.app.zpl.ZplConstants
import com.zplprinter.app.zpl.ZplGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class PrinterUiState(
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val discoveredDevices: List<BluetoothDevice> = emptyList(),
    val isScanning: Boolean = false,
    val selectedDevice: BluetoothDevice? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    /** A qué transporte corresponde [connectionState]/[selectedDevice]; solo significativo si no está DISCONNECTED. */
    val connectionType: ConnectionType = ConnectionType.BLUETOOTH,
    /** Etiqueta del extremo Wi-Fi activo o en curso (p. ej. "192.168.1.100:9100"), para mostrar en la UI sin exponer BluetoothDevice. */
    val wifiTarget: String? = null,
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
    val printerLanguage: PrinterLanguage = PrinterLanguage.ZPL,
)

class PrinterViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothManager = BluetoothPrinterManager(application)
    private val printerRepository = PrinterRepository(AppDatabase.getInstance(application).printerDao())
    private val printRecordRepository = PrintRecordRepository(AppDatabase.getInstance(application).printRecordDao())
    private val appPreferences = AppPreferences(application)

    private val _uiState = MutableStateFlow(
        PrinterUiState(
            widthMm = appPreferences.getPaperWidthMm(),
            heightMm = appPreferences.getPaperHeightMm(),
            dpi = appPreferences.getDpi(),
            mediaType = appPreferences.getMediaType(),
            printSpeed = appPreferences.getPrintSpeed(),
            darkness = appPreferences.getDarkness(),
            printerLanguage = appPreferences.getPrinterLanguage(),
        )
    )
    val uiState: StateFlow<PrinterUiState> = _uiState.asStateFlow()

    /** Impresoras a las que el usuario se conectó alguna vez, para elegir entre varias (no solo "la última usada"). */
    val savedProfiles: StateFlow<List<PrinterProfile>> = printerRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cada intento de impresión (exitoso o no), más reciente primero. */
    val printHistory: StateFlow<List<PrintRecord>> = printRecordRepository.observeRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearPrintHistory() {
        viewModelScope.launch { printRecordRepository.clear() }
    }

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

    /**
     * Autocalibración del sensor de papel: ~JC en ZPL, o GAPDETECT/BLINEDETECT en TSPL
     * según el tipo de papel configurado (TSPL distingue el sensor a calibrar).
     */
    fun calibrateSensor() {
        val state = _uiState.value
        if (state.connectionState != ConnectionState.CONNECTED) return
        _uiState.update { it.copy(isCalibrating = true, lastPrintOk = null) }
        viewModelScope.launch {
            val result = when (state.printerLanguage) {
                PrinterLanguage.ZPL -> bluetoothManager.sendZpl(ZplGenerator.CALIBRATE_SENSOR_CMD)
                PrinterLanguage.TSPL -> {
                    val cmd = if (state.mediaType == MediaType.BLACK_MARK) TsplGenerator.CALIBRATE_BLINE_CMD else TsplGenerator.CALIBRATE_GAP_CMD
                    bluetoothManager.sendTspl(cmd)
                }
            }
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
     * y simplemente no responden; eso se reporta como error, no como estado vacío. No disponible
     * para TSPL: no hay un comando de consulta de estado confirmado en el manual de TSC.
     */
    fun queryPrinterStatus() {
        val state = _uiState.value
        if (state.connectionState != ConnectionState.CONNECTED) return
        if (state.printerLanguage == PrinterLanguage.TSPL) {
            _uiState.update { it.copy(errorMessage = "Consultar estado no está disponible para impresoras TSPL") }
            return
        }
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

    fun updatePrinterLanguage(language: PrinterLanguage) {
        appPreferences.savePrinterLanguage(language)
        _uiState.update { it.copy(printerLanguage = language) }
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
            it.copy(
                selectedDevice = device,
                connectionType = ConnectionType.BLUETOOTH,
                wifiTarget = null,
                connectionState = ConnectionState.CONNECTING,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val result = bluetoothManager.connect(device)
            if (result.isSuccess) {
                val name = bluetoothManager.nameOf(device)
                printerRepository.rememberConnected(device.address, name, ConnectionType.BLUETOOTH)
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

    /** Conecta por Wi-Fi a [ipAddress]:[port] y la recuerda con [name] para futuras reconexiones automáticas. */
    fun connectWifi(ipAddress: String, port: Int, name: String) {
        stopScan()
        val target = "$ipAddress:$port"
        _uiState.update {
            it.copy(
                selectedDevice = null,
                connectionType = ConnectionType.WIFI,
                wifiTarget = target,
                connectionState = ConnectionState.CONNECTING,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val result = bluetoothManager.connectWifi(ipAddress, port)
            if (result.isSuccess) {
                val resolvedName = name.ifBlank { target }
                printerRepository.rememberConnected(target, resolvedName, ConnectionType.WIFI)
                _uiState.update {
                    it.copy(connectionState = ConnectionState.CONNECTED, rememberedPrinterName = resolvedName)
                }
            } else {
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.ERROR,
                        errorMessage = result.exceptionOrNull()?.message ?: "No se pudo conectar a $target"
                    )
                }
            }
        }
    }

    /** Conecta a una impresora guardada (lista "Impresoras guardadas"), por Bluetooth (buscándola por MAC) o Wi-Fi (reabriendo el socket a su ip:puerto). */
    fun connectToSavedProfile(profile: PrinterProfile) {
        when (profile.connectionType) {
            ConnectionType.WIFI -> {
                val (ip, port) = parseWifiAddress(profile.macAddress)
                if (ip == null) {
                    _uiState.update {
                        it.copy(connectionState = ConnectionState.ERROR, errorMessage = "Dirección Wi-Fi inválida para \"${profile.name}\".")
                    }
                    return
                }
                connectWifi(ip, port, profile.name)
            }
            ConnectionType.BLUETOOTH -> {
                val device = bluetoothManager.deviceForAddress(profile.macAddress)
                if (device == null) {
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.ERROR,
                            errorMessage = "No se encontró \"${profile.name}\". Verifica que esté emparejada y encendida."
                        )
                    }
                    return
                }
                connect(device)
            }
        }
    }

    /** Olvida una impresora guardada (p. ej. si ya no se usa); no afecta el emparejamiento Bluetooth del sistema. */
    fun forgetProfile(profile: PrinterProfile) {
        viewModelScope.launch { printerRepository.forget(profile) }
    }

    fun disconnect() {
        bluetoothManager.disconnect()
        _uiState.update { it.copy(connectionState = ConnectionState.DISCONNECTED, selectedDevice = null, wifiTarget = null) }
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
            printRecordRepository.record(
                "Muestra de prueba",
                _uiState.value.rememberedPrinterName,
                result.isSuccess,
                result.exceptionOrNull()?.message,
            )
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
            printRecordRepository.record(
                "Etiqueta de prueba",
                _uiState.value.rememberedPrinterName,
                result.isSuccess,
                result.exceptionOrNull()?.message,
            )
        }
    }

    /**
     * Si no hay una conexión activa, reconecta a la última impresora recordada (Bluetooth o
     * Wi-Fi, según [PrinterProfile.connectionType]) y actualiza el estado de conexión. Devuelve
     * un mensaje de error si falló, o `null` si ya había conexión o se logró reconectar.
     */
    private suspend fun ensureConnectedToLastUsed(description: String): String? {
        if (_uiState.value.connectionState == ConnectionState.CONNECTED) return null

        val lastUsed = printerRepository.getLastUsed()
            ?: return "No hay ninguna impresora recordada. Abre la app, conecta tu impresora una vez y vuelve a intentarlo."

        val connectResult = when (lastUsed.connectionType) {
            ConnectionType.WIFI -> {
                val (ip, port) = parseWifiAddress(lastUsed.macAddress)
                if (ip == null) return "Dirección Wi-Fi inválida para '${lastUsed.name}'."
                _uiState.update { it.copy(connectionType = ConnectionType.WIFI, wifiTarget = lastUsed.macAddress, connectionState = ConnectionState.CONNECTING) }
                bluetoothManager.connectWifi(ip, port)
            }
            ConnectionType.BLUETOOTH -> {
                val device = bluetoothManager.deviceForAddress(lastUsed.macAddress)
                    ?: return "No se encontró la impresora '${lastUsed.name}'. Verifica que esté emparejada y encendida."
                _uiState.update { it.copy(selectedDevice = device, connectionType = ConnectionType.BLUETOOTH, connectionState = ConnectionState.CONNECTING) }
                bluetoothManager.connect(device)
            }
        }

        return if (connectResult.isFailure) {
            _uiState.update { it.copy(connectionState = ConnectionState.ERROR) }
            connectResult.exceptionOrNull()?.message ?: "No se pudo conectar a '${lastUsed.name}'"
        } else {
            _uiState.update { it.copy(connectionState = ConnectionState.CONNECTED, rememberedPrinterName = lastUsed.name) }
            null
        }
    }

    /**
     * Imprime ZPL recibido desde otra app (menú "Compartir"), desde el diseñador o desde la
     * importación de PDF. Reconecta a la última impresora recordada si es necesario; no
     * requiere que el usuario haya abierto antes la pantalla de configuración en esta sesión.
     * Cada intento (exitoso o no) queda en el historial de impresión.
     */
    fun printSharedZpl(zpl: String, description: String = "Etiqueta compartida") {
        viewModelScope.launch {
            _uiState.update { it.copy(isPrinting = true, lastPrintOk = null, errorMessage = null) }

            val connectError = ensureConnectedToLastUsed(description)
            if (connectError != null) {
                _uiState.update { it.copy(isPrinting = false, lastPrintOk = false, errorMessage = connectError) }
                printRecordRepository.record(description, null, false, connectError)
                return@launch
            }

            val printerName = _uiState.value.rememberedPrinterName
            val result = bluetoothManager.sendZpl(zpl)
            _uiState.update {
                it.copy(
                    isPrinting = false,
                    lastPrintOk = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
            printRecordRepository.record(description, printerName, result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    /**
     * Imprime un comando generado por la propia app (diseñador, importación de PDF, impresión
     * rápida) en el protocolo que corresponda según [PrinterUiState.printerLanguage]. A diferencia
     * de [printSharedZpl] (que siempre es ZPL, porque el protocolo lo decide la app externa que
     * comparte el archivo), aquí el comando ya viene en el lenguaje correcto: solo falta elegir
     * con qué método enviarlo. Reconecta a la última impresora recordada igual que [printSharedZpl].
     */
    fun printGeneratedCommand(command: String, description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPrinting = true, lastPrintOk = null, errorMessage = null) }

            val connectError = ensureConnectedToLastUsed(description)
            if (connectError != null) {
                _uiState.update { it.copy(isPrinting = false, lastPrintOk = false, errorMessage = connectError) }
                printRecordRepository.record(description, null, false, connectError)
                return@launch
            }

            val printerName = _uiState.value.rememberedPrinterName
            val result = when (_uiState.value.printerLanguage) {
                PrinterLanguage.ZPL -> bluetoothManager.sendZpl(command)
                PrinterLanguage.TSPL -> bluetoothManager.sendTspl(command)
            }
            _uiState.update {
                it.copy(
                    isPrinting = false,
                    lastPrintOk = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
            printRecordRepository.record(description, printerName, result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    override fun onCleared() {
        bluetoothManager.disconnect()
        super.onCleared()
    }

    private companion object {
        /** Separa "ip:puerto" (formato guardado en [PrinterProfile.macAddress] para Wi-Fi); null si no matchea. */
        fun parseWifiAddress(target: String): Pair<String?, Int> {
            val parts = target.split(":")
            val ip = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
            val port = parts.getOrNull(1)?.toIntOrNull() ?: BluetoothPrinterManager.DEFAULT_WIFI_PORT
            return ip to port
        }
    }
}
