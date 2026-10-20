# PINTERbluetooth

App Android (Kotlin + Jetpack Compose) para diseñar etiquetas y enviarlas por Bluetooth a una impresora térmica compatible con ZPL (Zebra Programming Language), como la Xprinter XP-420B.

## Funcionalidad

- **Diseñador de etiquetas**: lienzo visual para componer texto, código de barras (Code128, Code39, EAN-13, UPC-A, ITF), QR y líneas/cajas, con arrastre, redimensionado y rotación en pasos de 90°.
- **Conexión Bluetooth clásica (SPP)**: búsqueda de dispositivos emparejados, escaneo de dispositivos nuevos cercanos, conexión/desconexión y recuerdo de la última impresora usada.
- **Generación de ZPL**: traduce el modelo de la etiqueta a comandos ZPL (`^FO`, `^A0`, `^BC`/`^B3`/`^BE`/`^BU`/`^B2`, `^BQ`, `^GB`, etc.), con codificación UTF-8 (`^CI28`) para que tildes y `ñ` se impriman bien.
- **Mantenimiento de impresora**: calibración del sensor de papel (`~JC`) y consulta de estado (`~HS`).
- **Integración con "Compartir" de Android**: recibe ZPL como texto o archivo desde otras apps (`Intent.ACTION_SEND`) y lo imprime directamente en la última impresora recordada.
- **Persistencia**: ajustes de papel/impresión en `SharedPreferences`, perfiles de impresora y etiquetas guardadas en una base de datos Room.

## Estructura

```
app/src/main/java/com/zplprinter/app/
├─ bluetooth/        BluetoothPrinterManager: conexión SPP, descubrimiento, envío/consulta ZPL
├─ data/
│  ├─ db/            Entidades y DAOs Room (etiquetas, perfiles de impresora)
│  ├─ model/         Label, LabelElement, PrinterProfile
│  ├─ prefs/         AppPreferences (ajustes persistentes)
│  └─ repository/    LabelRepository, PrinterRepository
├─ ui/
│  ├─ designer/       DesignerScreen + DesignerViewModel (lienzo de edición)
│  └─ printer/        PrinterSettingsScreen + PrinterViewModel (conexión, ajustes, mantenimiento)
├─ zpl/               ZplGenerator, ZplConstants, PrintSettings
└─ MainActivity.kt    Navegación y manejo de Intent.ACTION_SEND
```

## Build

```
./gradlew assembleDebug
```

Requiere Android SDK (minSdk 24, targetSdk 34) y JDK 17.

## Origen de las decisiones de protocolo

Las plantillas ZPL y el manejo de Bluetooth se validaron comparándolas, vía ingeniería inversa (REA/JADX), con una app comercial de impresión de etiquetas (`com.print.label`) para confirmar compatibilidad de comandos y detectar diferencias relevantes (p. ej. codificación de caracteres, autocalibración de sensor).
