package com.zplprinter.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Paleta extraída por muestreo de píxeles de capturas reales de com.print.label
 * (app de referencia analizada por ingeniería inversa): AppBar azul, status bar
 * verde oscuro, fondo gris claro, tarjetas blancas.
 */
val PrintLabelBlue = Color(0xFF419DE4)
val PrintLabelStatusBar = Color(0xFF00574B)
val PrintLabelBackground = Color(0xFFEBEBEB)
val PrintLabelSurface = Color(0xFFFFFFFF)
val PrintLabelOnSurfaceVariant = Color(0xFF6B6B6B)

private val ZplColorScheme = lightColorScheme(
    primary = PrintLabelBlue,
    onPrimary = Color.White,
    secondary = PrintLabelBlue,
    onSecondary = Color.White,
    background = PrintLabelBackground,
    onBackground = Color(0xFF212121),
    surface = PrintLabelSurface,
    onSurface = Color(0xFF212121),
    surfaceVariant = PrintLabelBackground,
    onSurfaceVariant = PrintLabelOnSurfaceVariant,
    outlineVariant = Color(0xFFE0E0E0),
)

@Composable
fun ZplTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        val window = (context as? android.app.Activity)?.window
        androidx.compose.runtime.SideEffect {
            window?.statusBarColor = PrintLabelStatusBar.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = ZplColorScheme,
        content = content,
    )
}
