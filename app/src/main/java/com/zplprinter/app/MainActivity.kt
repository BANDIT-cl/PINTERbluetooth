package com.zplprinter.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zplprinter.app.ui.designer.DesignerScreen
import com.zplprinter.app.ui.home.HomeScreen
import com.zplprinter.app.ui.printer.ConnectionState
import com.zplprinter.app.ui.printer.MaintenanceScreen
import com.zplprinter.app.ui.printer.PrinterConnectionScreen
import com.zplprinter.app.ui.printer.PrinterViewModel
import com.zplprinter.app.ui.printer.PrintSettingsScreen
import com.zplprinter.app.ui.theme.ZplTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** Texto ZPL recibido por Intent.ACTION_SEND, con un id único para disparar el LaunchedEffect una sola vez por envío. */
    private val incomingZplState = mutableStateOf<Pair<String, Long>?>(null)

    private val requiredBluetoothPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            var hasPermission by mutableStateOf(hasBluetoothPermission())

            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                hasPermission = results.values.all { it }
            }

            val printerViewModel: PrinterViewModel = viewModel()
            val incoming by incomingZplState

            val navController = rememberNavController()

            LaunchedEffect(incoming) {
                incoming?.let { (zpl, _) ->
                    navController.navigate("printer")
                    printerViewModel.printSharedZpl(zpl)
                    incomingZplState.value = null
                }
            }

            ZplTheme {
                Surface {
                    val printerUiState by printerViewModel.uiState.collectAsState()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                connectionState = printerUiState.connectionState,
                                connectedDeviceLabel = printerUiState.rememberedPrinterName,
                                onOpenDesigner = { navController.navigate("designer") },
                                onOpenPrinter = { navController.navigate("printer") },
                                onOpenPrintSettings = { navController.navigate("print_settings") },
                                onOpenMaintenance = { navController.navigate("maintenance") },
                            )
                        }
                        composable("printer") {
                            PrinterConnectionScreen(
                                hasBluetoothPermission = hasPermission,
                                onRequestPermission = { permissionLauncher.launch(requiredBluetoothPermissions) },
                                onBack = { navController.popBackStack() },
                                viewModel = printerViewModel,
                            )
                        }
                        composable("print_settings") {
                            PrintSettingsScreen(
                                widthMm = printerUiState.widthMm,
                                heightMm = printerUiState.heightMm,
                                dpi = printerUiState.dpi,
                                mediaType = printerUiState.mediaType,
                                printSpeed = printerUiState.printSpeed,
                                darkness = printerUiState.darkness,
                                onBack = { navController.popBackStack() },
                                onSizeSelected = printerViewModel::selectPaperSize,
                                onPrintSettingsChange = printerViewModel::updatePrintSettings,
                            )
                        }
                        composable("maintenance") {
                            MaintenanceScreen(
                                connected = printerUiState.connectionState == ConnectionState.CONNECTED,
                                isCalibrating = printerUiState.isCalibrating,
                                isQueryingStatus = printerUiState.isQueryingStatus,
                                printerStatus = printerUiState.printerStatus,
                                errorMessage = printerUiState.errorMessage,
                                onBack = { navController.popBackStack() },
                                onCalibrate = printerViewModel::calibrateSensor,
                                onQueryStatus = printerViewModel::queryPrinterStatus,
                            )
                        }
                        composable("designer") {
                            DesignerScreen(
                                initialWidthMm = printerUiState.widthMm,
                                initialHeightMm = printerUiState.heightMm,
                                initialDpi = printerUiState.dpi,
                                mediaType = printerUiState.mediaType,
                                printSpeed = printerUiState.printSpeed,
                                darkness = printerUiState.darkness,
                                onBack = { navController.popBackStack() },
                                onPrint = { zpl -> printerViewModel.printSharedZpl(zpl) },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /** Extrae el ZPL de un Intent.ACTION_SEND (texto plano o archivo adjunto) y lo encola para imprimir. */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) return

        val directText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!directText.isNullOrBlank()) {
            incomingZplState.value = directText to System.nanoTime()
            return
        }

        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (uri != null) {
            readZplFromUri(uri)
        }
    }

    private fun readZplFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val text = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()

            if (!text.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    incomingZplState.value = text to System.nanoTime()
                }
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean =
        requiredBluetoothPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
}
