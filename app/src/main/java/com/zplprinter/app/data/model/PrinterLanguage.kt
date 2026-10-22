package com.zplprinter.app.data.model

/** Lenguaje de comandos que entiende la impresora conectada. */
enum class PrinterLanguage(val displayName: String) {
    ZPL("ZPL (Zebra, Xprinter y compatibles)"),
    TSPL("TSPL (impresoras TSC)"),
}
