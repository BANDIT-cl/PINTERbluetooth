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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zplprinter.app.ui.batchimport.BatchImportScreen
import com.zplprinter.app.ui.designer.DesignerScreen
import com.zplprinter.app.ui.home.HomeScreen
import com.zplprinter.app.ui.labels.SavedLabelsScreen
import com.zplprinter.app.ui.pdfimport.PdfImportScreen
import com.zplprinter.app.ui.printer.ConnectionState
import com.zplprinter.app.ui.printer.MaintenanceScreen
import com.zplprinter.app.ui.printer.PrintHistoryScreen
import com.zplprinter.app.ui.printer.PrinterConnectionScreen
import com.zplprinter.app.ui.printer.PrinterViewModel
import com.zplprinter.app.ui.printer.PrintSettingsScreen
import com.zplprinter.app.ui.quickprint.QuickPrintScreen
import com.zplprinter.app.ui.theme.ZplTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** Texto ZPL recibido por Intent.ACTION_SEND, con un id único para disparar el LaunchedEffect una sola vez por envío. */
    private val incomingZplState = mutableStateOf<Pair<String, Long>?>(null)

    /** PDF (p. ej. etiqueta de MercadoLibre) recibido por Intent.ACTION_SEND, pendiente de abrir en PdfImportScreen. */
    private val incomingPdfState = mutableStateOf<Pair<Uri, Long>?>(null)

    /** Varios archivos recibidos a la vez por Intent.ACTION_SEND_MULTIPLE, pendientes de revisar en BatchImportScreen. */
    private val incomingBatchState = mutableStateOf<Pair<List<Uri>, Long>?>(null)

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
            val incomingPdf by incomingPdfState
            val incomingBatch by incomingBatchState

            val navController = rememberNavController()

            LaunchedEffect(incoming) {
                incoming?.let { (zpl, _) ->
                    navController.navigate("printer")
                    printerViewModel.printSharedZpl(zpl, description = "Compartido desde otra app")
                    incomingZplState.value = null
                }
            }

            LaunchedEffect(incomingPdf) {
                incomingPdf?.let {
                    navController.navigate("pdf_import")
                }
            }

            LaunchedEffect(incomingBatch) {
                incomingBatch?.let {
                    navController.navigate("batch_import")
                }
            }

            ZplTheme {
                Surface {
                    val printerUiState by printerViewModel.uiState.collectAsState()
                    val printHistory by printerViewModel.printHistory.collectAsState()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                connectionState = printerUiState.connectionState,
                                connectedDeviceLabel = printerUiState.rememberedPrinterName,
                                onOpenDesigner = { navController.navigate("designer") },
                                onOpenPrinter = { navController.navigate("printer") },
                                onOpenPrintSettings = { navController.navigate("print_settings") },
                                onOpenMaintenance = { navController.navigate("maintenance") },
                                onOpenPdfImport = { navController.navigate("pdf_import") },
                                onOpenSavedLabels = { navController.navigate("saved_labels") },
                                onOpenPrintHistory = { navController.navigate("print_history") },
                                onOpenQuickPrint = { navController.navigate("quick_print") },
                            )
                        }
                        composable("print_history") {
                            PrintHistoryScreen(
                                records = printHistory,
                                onBack = { navController.popBackStack() },
                                onClear = printerViewModel::clearPrintHistory,
                            )
                        }
                        composable("quick_print") {
                            QuickPrintScreen(
                                widthMm = printerUiState.widthMm,
                                heightMm = printerUiState.heightMm,
                                dpi = printerUiState.dpi,
                                printSettings = printerViewModel.currentPrintSettings(),
                                printerLanguage = printerUiState.printerLanguage,
                                isPrinting = printerUiState.isPrinting,
                                lastPrintOk = printerUiState.lastPrintOk,
                                printerErrorMessage = printerUiState.errorMessage,
                                onBack = { navController.popBackStack() },
                                onPrint = { command, description -> printerViewModel.printGeneratedCommand(command, description) },
                            )
                        }
                        composable("saved_labels") {
                            SavedLabelsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenLabel = { id -> navController.navigate("designer?labelId=$id") },
                                onCreateNew = { navController.navigate("designer") },
                            )
                        }
                        composable("pdf_import") {
                            PdfImportScreen(
                                initialUri = incomingPdf?.first,
                                onInitialUriConsumed = { incomingPdfState.value = null },
                                widthMm = printerUiState.widthMm,
                                heightMm = printerUiState.heightMm,
                                dpi = printerUiState.dpi,
                                printSettings = printerViewModel.currentPrintSettings(),
                                printerLanguage = printerUiState.printerLanguage,
                                isPrinting = printerUiState.isPrinting,
                                lastPrintOk = printerUiState.lastPrintOk,
                                printerErrorMessage = printerUiState.errorMessage,
                                onBack = { navController.popBackStack() },
                                onPrint = { command -> printerViewModel.printGeneratedCommand(command, description = "Etiqueta PDF") },
                            )
                        }
                        composable("batch_import") {
                            BatchImportScreen(
                                initialUris = incomingBatch?.first.orEmpty(),
                                onInitialUrisConsumed = { incomingBatchState.value = null },
                                widthMm = printerUiState.widthMm,
                                heightMm = printerUiState.heightMm,
                                dpi = printerUiState.dpi,
                                printSettings = printerViewModel.currentPrintSettings(),
                                printerLanguage = printerUiState.printerLanguage,
                                onBack = { navController.popBackStack() },
                                onPrintBatch = { items, onItemResult -> printerViewModel.printBatch(items, onItemResult) },
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
                                printerLanguage = printerUiState.printerLanguage,
                                onBack = { navController.popBackStack() },
                                onSizeSelected = printerViewModel::selectPaperSize,
                                onPrintSettingsChange = printerViewModel::updatePrintSettings,
                                onPrinterLanguageSelected = printerViewModel::updatePrinterLanguage,
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
                        composable(
                            route = "designer?labelId={labelId}",
                            arguments = listOf(
                                navArgument("labelId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            ),
                        ) { backStackEntry ->
                            DesignerScreen(
                                initialLabelId = backStackEntry.arguments?.getString("labelId"),
                                initialWidthMm = printerUiState.widthMm,
                                initialHeightMm = printerUiState.heightMm,
                                initialDpi = printerUiState.dpi,
                                mediaType = printerUiState.mediaType,
                                printSpeed = printerUiState.printSpeed,
                                darkness = printerUiState.darkness,
                                printerLanguage = printerUiState.printerLanguage,
                                onBack = { navController.popBackStack() },
                                onOpenSettings = { navController.navigate("print_settings") },
                                onPrint = { command, description -> printerViewModel.printGeneratedCommand(command, description) },
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

    /** Extrae el ZPL (o PDF) de un Intent.ACTION_SEND/ACTION_SEND_MULTIPLE/ACTION_VIEW y lo encola para imprimir o importar. */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // "Abrir con" desde MercadoLibre, el navegador o el gestor de descargas dispara ACTION_VIEW
        // en vez de ACTION_SEND, y la Uri viaja en intent.data en lugar de EXTRA_STREAM.
        if (intent.action == Intent.ACTION_VIEW && intent.type == "application/pdf") {
            intent.data?.let { uri ->
                incomingPdfState.value = uri to System.nanoTime()
            }
            return
        }

        // Seleccionar varios archivos a la vez en el gestor de archivos y compartirlos dispara
        // ACTION_SEND_MULTIPLE (EXTRA_STREAM como ArrayList<Uri>), no ACTION_SEND.
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (!uris.isNullOrEmpty()) {
                incomingBatchState.value = uris.toList() to System.nanoTime()
            }
            return
        }

        if (intent.action != Intent.ACTION_SEND) return

        if (intent.type == "application/pdf") {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (uri != null) {
                incomingPdfState.value = uri to System.nanoTime()
            }
            return
        }

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
