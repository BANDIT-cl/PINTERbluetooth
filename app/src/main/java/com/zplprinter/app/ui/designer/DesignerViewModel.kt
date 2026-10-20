package com.zplprinter.app.ui.designer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zplprinter.app.data.db.AppDatabase
import com.zplprinter.app.data.model.BarcodeSymbology
import com.zplprinter.app.data.model.Label
import com.zplprinter.app.data.model.LabelElement
import com.zplprinter.app.data.model.TextDataType
import com.zplprinter.app.data.repository.LabelRepository
import com.zplprinter.app.zpl.ZplConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val MAX_HISTORY = 50
private const val SCALE_STEP_MM = 2f

/** Estado del lienzo del diseñador, con historial de deshacer/rehacer y guardado en Room. */
class DesignerViewModel(application: Application) : AndroidViewModel(application) {

    private val labelRepository = LabelRepository(AppDatabase.getInstance(application).labelDao())

    private val _label = MutableStateFlow(
        Label(name = "Nueva etiqueta", widthMm = 100f, heightMm = 100f, dpi = ZplConstants.DEFAULT_DPI)
    )
    val label: StateFlow<Label> = _label.asStateFlow()

    private val _selectedElementId = MutableStateFlow<String?>(null)
    val selectedElementId: StateFlow<String?> = _selectedElementId.asStateFlow()

    private val undoStack = ArrayDeque<Label>()
    private val redoStack = ArrayDeque<Label>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _lastSaveOk = MutableStateFlow<Boolean?>(null)
    val lastSaveOk: StateFlow<Boolean?> = _lastSaveOk.asStateFlow()

    private var initialized = false

    /** Fija el tamaño de papel inicial (desde la última configuración usada) solo una vez. */
    fun initializeSizeIfNeeded(widthMm: Float, heightMm: Float, dpi: Int) {
        if (initialized) return
        initialized = true
        _label.update { it.copy(widthMm = widthMm, heightMm = heightMm, dpi = dpi) }
    }

    /** Carga una etiqueta guardada por id (p. ej. al abrirla desde "Mis etiquetas"), una sola vez por pantalla. */
    fun loadLabel(id: String) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            labelRepository.getLabel(id)?.let { loaded -> _label.value = loaded }
        }
    }

    fun updateName(newName: String) {
        _label.update { it.copy(name = newName, updatedAt = System.currentTimeMillis()) }
    }

    fun selectElement(id: String?) {
        _selectedElementId.value = id
    }

    fun addText() {
        addElement(LabelElement.TextElement(id = newId(), xMm = 5f, yMm = 5f, text = "Texto"))
    }

    fun addBarcode() {
        addElement(LabelElement.BarcodeElement(id = newId(), xMm = 5f, yMm = 5f, data = "123456789"))
    }

    fun addQr() {
        addElement(LabelElement.QrElement(id = newId(), xMm = 5f, yMm = 5f, data = "https://"))
    }

    fun addLine() {
        val currentLabel = _label.value
        addElement(
            LabelElement.ShapeElement(
                id = newId(),
                xMm = 5f,
                yMm = 5f,
                widthMm = (currentLabel.widthMm - 10f).coerceAtLeast(5f),
                heightMm = 0f,
            )
        )
    }

    /** Herramienta "Forma": un rectángulo con alto, a diferencia de "Línea" (alto 0). */
    fun addShape() {
        addElement(LabelElement.ShapeElement(id = newId(), xMm = 5f, yMm = 5f, widthMm = 20f, heightMm = 20f))
    }

    /** Herramienta "Fecha": texto que siempre resuelve a la fecha de hoy al imprimir. */
    fun addDate() {
        addElement(
            LabelElement.TextElement(
                id = newId(),
                xMm = 5f,
                yMm = 5f,
                text = "",
                dataType = TextDataType.DATE,
            )
        )
    }

    private fun addElement(element: LabelElement) {
        pushUndoSnapshot()
        _label.update { it.copy(elements = it.elements + element, updatedAt = System.currentTimeMillis()) }
        _selectedElementId.value = element.id
    }

    /** Duplica el elemento seleccionado con un pequeño desplazamiento, y selecciona la copia. */
    fun duplicateElement(id: String) {
        val original = _label.value.elements.find { it.id == id } ?: return
        pushUndoSnapshot()
        val copyId = newId()
        val duplicate = when (original) {
            is LabelElement.TextElement -> original.copy(id = copyId)
            is LabelElement.BarcodeElement -> original.copy(id = copyId)
            is LabelElement.QrElement -> original.copy(id = copyId)
            is LabelElement.ShapeElement -> original.copy(id = copyId)
        }.withPosition(original.xMm + 5f, original.yMm + 5f)
        _label.update { it.copy(elements = it.elements + duplicate, updatedAt = System.currentTimeMillis()) }
        _selectedElementId.value = duplicate.id
    }

    /** Alterna el bloqueo del elemento: mientras está bloqueado, el lienzo ignora arrastres y Rotar/Ampliar/Reducir. */
    fun toggleLock(id: String) {
        pushUndoSnapshot()
        updateElement(id) { element -> element.withLocked(!element.locked) }
    }

    /**
     * Mueve el elemento seleccionado arrastrando en el lienzo. `dXMm`/`dYMm` son el delta
     * de ese frame de arrastre (no la posición absoluta): el gesto en Compose no vuelve a
     * componer `ElementView` en cada frame, así que la posición "actual" solo es confiable
     * leída aquí desde `_label.value` (la fuente de verdad), no desde el closure capturado
     * por el `pointerInput` del lienzo. Calcular la posición nueva ahí (como se hacía antes)
     * usaba una posición base obsoleta y el elemento terminaba sin moverse bien o fuera
     * de los límites de la etiqueta.
     */
    fun moveElement(id: String, dXMm: Float, dYMm: Float) {
        updateElement(id) { element ->
            if (element.locked) {
                element
            } else {
                val clampedX = (element.xMm + dXMm).coerceIn(0f, _label.value.widthMm)
                val clampedY = (element.yMm + dYMm).coerceIn(0f, _label.value.heightMm)
                element.withPosition(clampedX, clampedY)
            }
        }
    }

    /** Rota el elemento seleccionado 90° en cada toque; ZPL solo admite pasos de 90°. */
    fun rotateElement(id: String) {
        pushUndoSnapshot()
        updateElement(id) { element ->
            if (element.locked) element else element.withRotation((element.rotationDeg + 90f) % 360f)
        }
    }

    /**
     * Redimensiona el elemento seleccionado arrastrando desde su esquina en
     * el lienzo. `dWidthMm`/`dHeightMm` son el delta de ese frame de arrastre
     * (no el total), igual que [moveElement].
     */
    fun resizeElement(id: String, dWidthMm: Float, dHeightMm: Float) {
        updateElement(id) { element -> applyResize(element, dWidthMm, dHeightMm) }
    }

    /** Botones Ampliar/Reducir: un paso fijo de escala sobre el elemento seleccionado, como un solo paso de deshacer. */
    fun scaleElement(id: String, grow: Boolean) {
        pushUndoSnapshot()
        val delta = if (grow) SCALE_STEP_MM else -SCALE_STEP_MM
        updateElement(id) { element -> applyResize(element, delta, delta) }
    }

    /** Cada tipo de elemento interpreta el delta según cuál es su propiedad de "tamaño" real. Ignorado si está bloqueado. */
    private fun applyResize(element: LabelElement, dWidthMm: Float, dHeightMm: Float): LabelElement {
        if (element.locked) return element
        return when (element) {
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

    fun updateBarcodeSymbology(id: String, symbology: BarcodeSymbology) {
        updateElement(id) { element ->
            (element as? LabelElement.BarcodeElement)?.copy(symbology = symbology) ?: element
        }
    }

    fun updateBarcodeHumanReadable(id: String, humanReadable: Boolean) {
        updateElement(id) { element ->
            (element as? LabelElement.BarcodeElement)?.copy(humanReadable = humanReadable) ?: element
        }
    }

    fun updateQrMagnification(id: String, magnification: Float) {
        updateElement(id) { element ->
            (element as? LabelElement.QrElement)?.copy(magnification = magnification.coerceIn(1f, 10f)) ?: element
        }
    }

    fun updateLineThickness(id: String, thicknessMm: Float) {
        updateElement(id) { element ->
            (element as? LabelElement.ShapeElement)?.copy(thicknessMm = thicknessMm.coerceIn(0.2f, 5f)) ?: element
        }
    }

    fun updateText(id: String, newText: String) {
        updateElement(id) { element ->
            (element as? LabelElement.TextElement)?.copy(text = newText) ?: element
        }
    }

    fun updateTextDataType(id: String, dataType: TextDataType) {
        updateElement(id) { element ->
            (element as? LabelElement.TextElement)?.copy(dataType = dataType) ?: element
        }
    }

    /** Configura el contador autoincremental de un [LabelElement.TextElement]. No cambia [dataType]. */
    fun updateCounterConfig(id: String, start: Int, step: Int, digits: Int) {
        updateElement(id) { element ->
            (element as? LabelElement.TextElement)?.copy(
                counterStart = start,
                counterStep = step,
                counterDigits = digits.coerceIn(1, 10),
            ) ?: element
        }
    }

    fun updateFontSize(id: String, fontHeightMm: Float, fontWidthMm: Float) {
        updateElement(id) { element ->
            (element as? LabelElement.TextElement)?.copy(
                fontHeightMm = fontHeightMm.coerceIn(2f, 50f),
                fontWidthMm = fontWidthMm.coerceIn(2f, 50f),
            ) ?: element
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
        pushUndoSnapshot()
        _label.update { it.copy(elements = it.elements.filterNot { el -> el.id == id }) }
        if (_selectedElementId.value == id) {
            _selectedElementId.value = null
        }
    }

    /** Guarda la etiqueta actual en Room (tabla "Mi etiqueta"). */
    fun saveLabel() {
        _isSaving.value = true
        viewModelScope.launch {
            val result = runCatching { labelRepository.saveLabel(_label.value) }
            _isSaving.value = false
            _lastSaveOk.value = result.isSuccess
        }
    }

    /**
     * Marca el inicio de un gesto de arrastre (mover o redimensionar con el handle del lienzo):
     * un solo paso de deshacer para todo el gesto, no uno por cada frame de arrastre.
     */
    fun beginCanvasTransform() {
        pushUndoSnapshot()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_label.value)
        _label.value = previous
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = true
        _selectedElementId.value = null
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_label.value)
        _label.value = next
        _canUndo.value = true
        _canRedo.value = redoStack.isNotEmpty()
        _selectedElementId.value = null
    }

    private fun pushUndoSnapshot() {
        undoStack.addLast(_label.value)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        _canUndo.value = true
        _canRedo.value = false
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
