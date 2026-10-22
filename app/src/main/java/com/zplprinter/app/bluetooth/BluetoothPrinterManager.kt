package com.zplprinter.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Maneja la conexión con la impresora, Bluetooth clásico (perfil SPP) o Wi-Fi (socket TCP
 * directo, puerto 9100 habitual en impresoras térmicas), y el envío de comandos ZPL/TSPL
 * crudos por el transporte que esté activo. No solicita permisos: eso es responsabilidad
 * de la capa de UI antes de llamar a estos métodos.
 */
class BluetoothPrinterManager(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    private var socket: BluetoothSocket? = null
    private var wifiSocket: Socket? = null
    private var discoveryReceiver: BroadcastReceiver? = null

    /** Stream de salida del transporte activo (Bluetooth o Wi-Fi), el que esté conectado. */
    private val activeOutputStream: OutputStream?
        get() = socket?.takeIf { it.isConnected }?.outputStream
            ?: wifiSocket?.takeIf { it.isConnected }?.getOutputStream()

    private val activeInputStream: InputStream?
        get() = socket?.takeIf { it.isConnected }?.inputStream
            ?: wifiSocket?.takeIf { it.isConnected }?.getInputStream()

    val isBluetoothSupported: Boolean get() = adapter != null
    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> =
        try {
            adapter?.bondedDevices?.toList().orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }

    /** Resuelve un dispositivo ya emparejado por su dirección MAC, p. ej. para reconectar a la última impresora usada. */
    fun deviceForAddress(macAddress: String): BluetoothDevice? =
        try {
            adapter?.getRemoteDevice(macAddress)
        } catch (_: IllegalArgumentException) {
            null
        }

    /**
     * Escanea dispositivos Bluetooth cercanos no necesariamente emparejados (p. ej. una impresora
     * nueva recién encendida). [onDeviceFound] se llama por cada dispositivo detectado y
     * [onFinished] cuando el escaneo termina (tras ~12s, o si no se pudo iniciar por permisos).
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery(onDeviceFound: (BluetoothDevice) -> Unit, onFinished: () -> Unit) {
        stopDiscovery()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = IntentCompat.getParcelableExtra(
                            intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                        )
                        device?.let(onDeviceFound)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> onFinished()
                }
            }
        }
        discoveryReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        try {
            adapter?.cancelDiscovery()
            val started = adapter?.startDiscovery() == true
            if (!started) onFinished()
        } catch (_: SecurityException) {
            onFinished()
        }
    }

    /** Detiene un escaneo en curso. Seguro de llamar aunque no haya uno activo. */
    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            adapter?.cancelDiscovery()
        } catch (_: SecurityException) {
            // nada que cancelar
        }
        discoveryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // ya estaba desregistrado
            }
        }
        discoveryReceiver = null
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            closeQuietly()
            adapter?.cancelDiscovery()
            val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            newSocket.connect()
            socket = newSocket
            Result.success(Unit)
        } catch (e: IOException) {
            closeQuietly()
            Result.failure(e)
        } catch (e: SecurityException) {
            closeQuietly()
            Result.failure(e)
        }
    }

    /**
     * Conecta por Wi-Fi a una impresora que escucha en [ipAddress]:[port] (9100 es el puerto
     * "raw" estándar de impresión térmica/ZPL en red, soportado por la mayoría de las
     * impresoras con interfaz de red, a diferencia del Bluetooth SPP que no tiene puerto).
     */
    suspend fun connectWifi(ipAddress: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            closeQuietly()
            val newSocket = Socket()
            newSocket.connect(InetSocketAddress(ipAddress, port), WIFI_CONNECT_TIMEOUT_MS)
            wifiSocket = newSocket
            Result.success(Unit)
        } catch (e: IOException) {
            closeQuietly()
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            closeQuietly()
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    fun nameOf(device: BluetoothDevice): String =
        try {
            device.name ?: device.address
        } catch (_: SecurityException) {
            device.address
        }

    suspend fun sendZpl(zpl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val out = activeOutputStream
            ?: return@withContext Result.failure(IllegalStateException("No hay conexión con la impresora"))
        try {
            // La impresora interpreta ^FD como UTF-8 porque ZplGenerator antepone ^CI28;
            // sin ese comando, acentos y ñ llegarían corruptos.
            out.write(zpl.toByteArray(Charsets.UTF_8))
            out.flush()
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /**
     * Envía un comando TSPL (impresoras TSC). A diferencia de ZPL, TSPL no tiene un
     * ^CI28 que active UTF-8: la página de códigos se fija con CODEPAGE 1252 (que
     * [com.zplprinter.app.tspl.TsplGenerator] ya antepone), así que el texto debe viajar
     * en ISO-8859-1 (compatible en el rango de acentos/ñ del español). Además, BITMAP
     * embebe bytes crudos de imagen dentro del mismo String vía un round-trip Latin-1:
     * codificar con cualquier otro charset (p. ej. UTF-8) corrompería esos bytes.
     */
    suspend fun sendTspl(command: String): Result<Unit> = withContext(Dispatchers.IO) {
        val out = activeOutputStream
            ?: return@withContext Result.failure(IllegalStateException("No hay conexión con la impresora"))
        try {
            out.write(command.toByteArray(Charsets.ISO_8859_1))
            out.flush()
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /**
     * Envía un comando ZPL de consulta (p. ej. ~HS) y devuelve la respuesta cruda de la
     * impresora como texto. Espera hasta [timeoutMs] a que lleguen datos; algunas impresoras
     * ZPL "compatibles" (no Zebra originales) no implementan estas consultas y no responden nada.
     */
    suspend fun queryStatus(
        command: String,
        timeoutMs: Long = 1500,
    ): Result<String> = withContext(Dispatchers.IO) {
        val out = activeOutputStream
            ?: return@withContext Result.failure(IllegalStateException("No hay conexión con la impresora"))
        try {
            out.write(command.toByteArray(Charsets.US_ASCII))
            out.flush()

            val input = activeInputStream
                ?: return@withContext Result.failure(IllegalStateException("No hay conexión con la impresora"))
            val buffer = ByteArray(512)
            val response = StringBuilder()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    val read = input.read(buffer)
                    if (read > 0) response.append(String(buffer, 0, read, Charsets.US_ASCII))
                } else {
                    delay(50)
                }
            }
            if (response.isEmpty()) {
                Result.failure(IOException("La impresora no respondió a $command"))
            } else {
                Result.success(response.toString())
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    val isConnected: Boolean get() = socket?.isConnected == true || wifiSocket?.isConnected == true

    fun disconnect() {
        stopDiscovery()
        closeQuietly()
    }

    private fun closeQuietly() {
        try {
            socket?.close()
        } catch (_: IOException) {
            // ignorado: el socket ya puede estar cerrado por el otro extremo
        }
        socket = null
        try {
            wifiSocket?.close()
        } catch (_: IOException) {
            // ignorado: el socket ya puede estar cerrado por el otro extremo
        }
        wifiSocket = null
    }

    companion object {
        /** UUID estándar del perfil SPP (Serial Port Profile). */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** La mayoría de las impresoras de red escuchan "raw" en el puerto 9100. */
        const val DEFAULT_WIFI_PORT = 9100

        private const val WIFI_CONNECT_TIMEOUT_MS = 5000
    }
}
