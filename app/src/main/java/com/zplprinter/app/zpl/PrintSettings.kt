package com.zplprinter.app.zpl

/** Modo de sensor de medio (^MN): cómo detecta la impresora el borde de cada etiqueta. */
enum class MediaType(val displayName: String, val zplChar: Char) {
    GAP("Con gap (etiquetas troqueladas)", 'Y'),
    CONTINUOUS("Papel continuo", 'N'),
    BLACK_MARK("Con marca negra", 'M'),
}

/**
 * Ajustes de trabajo de impresión (no son parte del diseño de la etiqueta):
 * copias, velocidad, oscuridad y tipo de papel. Nulo en velocidad/oscuridad
 * significa "usar el valor que ya tenga la impresora", sin emitir el comando.
 */
data class PrintSettings(
    val copies: Int = 1,
    val printSpeed: Int? = null,
    val darkness: Int? = null,
    val mediaType: MediaType = MediaType.GAP,
)
