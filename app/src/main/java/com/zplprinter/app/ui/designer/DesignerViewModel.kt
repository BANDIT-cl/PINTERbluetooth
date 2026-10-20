package com.zplprinter.app.ui.designer

import androidx.lifecycle.ViewModel
import com.zplprinter.app.data.model.BarcodeSymbology
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.model.LabelElement
import com.zplprinter.app.zpl.ZplConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Estado del lienzo del diseñador. No persiste a Room todavía (eso llega en
 * M5 junto con la pantalla Home); mientras tanto vive en memoria para poder
 * diseñar e imprimir directamente.
 */
class DesignerViewModel : ViewModel() {

    private val _label = MutableStateFlow(
        Label(name = "Nueva etiqueta", widthMm = 100f, heightMm = 100f, dpi = ZplConstants.DEFAULT_DPI)
    )
    val label: StateFlow<Label> = _label.asStateFlow()

    private val _selectedElementId = MutableStateFlow<String?>(null)
    val selectedElementId: StateFlow<String?> = _selectedElementId.asStateFlow()

    private var initialized = false

    /** Fija el tamaño de papel inicial (desde la última configuración usada) solo una vez. */
    fun initializeSizeIfNeeded(widthMm: Float, heightMm: Float, dpi: Int) {
        if (initialized) return
        initialized = true
        _label.update { it.copy(widthMm = widthMm, heightMm = heightMm, dpi = dpi) }
    }

    fun selectElement(id: String?) {
        _selectedElementId.value = id
    }

    fun addText() {
        val element = LabelElement.TextElement(
            id = newId(),
            xMm = 5f,
            yMm = 5f,
            text = "Texto",
        )
        addElement(element)
    }

    fun addBarcode() {
        val element = LabelElement.BarcodeElement(
            id = newId(),
            xMm = 5f,
            yMm = 5f,
            data = "123456789",
        )
        addElement(element)
    }

    fun addQr() {
        val element = LabelElement.QrElement(
            id = newId(),
            xMm = 5f,
            yMm = 5f,
            data = "https://",
        )
        addElement(element)
    }

    fun addLine() {
        val currentLabel = _label.value
        val element = LabelElement.ShapeElement(
            id = newId(),
            xMm = 5f,
            yMm = 5f,
            widthMm = (currentLabel.widthMm - 10f).coerceAtLeast(5f),
            heightMm = 0f,
        )
        addElement(element)
    }

    private fun addElement(element: LabelElement) {
        _label.update { it.copy(elements = it.elements + element, updatedAt = System.currentTimeMillis()) }
        _selectedElementId.value = element.id
    }

    fun moveElement(id: String, newXMm: Float, newYMm: Float) {
        updateElement(id) { element ->
            val clampedX = newXMm.coerceIn(0f, _label.value.widthMm)
            val clampedY = newYMm.coerceIn(0f, _label.value.heightMm)
            element.withPosition(clampedX, clampedY)
        }
    }

    /** Rota el elemento seleccionado 90° en cada toque; ZPL solo admite pasos de 90°. */
    fun rotateElement(id: String) {
        updateElement(id) { element ->
            element.withRotation((element.rotationDeg + 90f) % 360f)
        }
    }

    /**
     * Redimensiona el elemento seleccionado arrastrando desde su esquina en
     * el lienzo. `dWidthMm`/`dHeightMm` son el delta de ese frame de arrastre
     * (no el total), igual que [moveElement]. Cada tipo de elemento interpreta
     * el delta según cuál es su propiedad de "tamaño" real.
     */
    fun resizeElement(id: String, dWidthMm: Float, dHeightMm: Float) {
        updateElement(id) { element ->
            when (element) {
                is LabelElement.TextElement -> element.copy(
                    fontWidthMm = (element.fontWidthMm + dWidthMm).coerceIn(2f, 50f),
                    fontHeightMm = (element.fontHeightMm + dHeightMm).coerceIn(2f, 50f),
                )
                is LabelElement.BarcodeElement -> element.copy(
                    heightMm = (element.heightMm + dHeightMm).coerceIn(5f, 60f),
                    moduleWidthDots = (element.moduleWidthDots + dWidthMm / 3f).coerceIn(1f, 10f),
                )
                is LabelElement.QrElement -> element.copy(
                    magnification = (element.magnification + (dWidthMm + dHeightMm) / 8f).coerceIn(1f, 10f),
                )
                is LabelElement.ShapeElement -> element.copy(
                    widthMm = (element.widthMm + dWidthMm).coerceAtLeast(5f),
                    thicknessMm = (element.thicknessMm + dHeightMm / 10f).coerceIn(0.2f, 5f),
                )
            }
        }
    }

    fun updateBarcodeSymbology(id: String, symbology: BarcodeSymbology) {
        updateElement(id) { element ->
            (element as? LabelElement.BarcodeElement)?.copy(symbology = symbology) ?: element
        }
    }

    fun updateText(id: String, newText: String) {
        updateElement(id) { element ->
            (element as? LabelElement.TextElement)?.copy(text = newText) ?: element
        }
    }

    fun updateBarcodeData(id: String, newData: String) {
        updateElement(id) { element ->
            (element as? LabelElement.BarcodeElement)?.copy(data = newData) ?: element
        }
    }

    fun updateQrData(id: String, newData: String) {
        updateElement(id) { element ->
            (element as? LabelElement.QrElement)?.copy(data = newData) ?: element
        }
    }

    fun updateLineWidth(id: String, newWidthMm: Float) {
        updateElement(id) { element ->
            (element as? LabelElement.ShapeElement)?.copy(widthMm = newWidthMm.coerceAtLeast(1f)) ?: element
        }
    }

    fun deleteElement(id: String) {
        _label.update { it.copy(elements = it.elements.filterNot { el -> el.id == id }) }
        if (_selectedElementId.value == id) {
            _selectedElementId.value = null
        }
    }

    private fun updateElement(id: String, transform: (LabelElement) -> LabelElement) {
        _label.update { current ->
            current.copy(
                elements = current.elements.map { if (it.id == id) transform(it) else it },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
