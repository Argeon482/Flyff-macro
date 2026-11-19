package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ShizuMacroApp
import com.example.data.ExecutionLogEntity
import com.example.data.TemplateAssetEntity
import com.example.engine.RuntimeExecutionState
import com.example.model.DelayStep
import com.example.model.IfColorStep
import com.example.model.IfImageStep
import com.example.model.KeyPressStep
import com.example.model.LoopEndStep
import com.example.model.LoopStartStep
import com.example.model.MacroDefinition
import com.example.model.MacroStep
import com.example.model.SwipeStep
import com.example.model.TapStep
import com.example.model.WebViewTarget
import com.example.service.MacroOverlayService
import com.example.service.OverlayUiState
import com.example.service.PickerMode
import com.example.service.PickerRequest
import com.example.service.PickerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface UiNotice {
    data class Message(val text: String, val isError: Boolean = false) : UiNotice
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ShizuMacroApp
    private val repository = app.repository
    val screenDetector = app.screenDetector
    val interpreter = app.interpreter
    val overlayCoordinator = app.overlayCoordinator
    val scriptClipboard = app.scriptClipboard

    val macros: StateFlow<List<MacroDefinition>> = repository.macros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<TemplateAssetEntity>> = repository.templates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ExecutionLogEntity>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val screenCaptureActive: StateFlow<Boolean> = screenDetector.isActive
    val executionState: StateFlow<RuntimeExecutionState> = interpreter.state
    val overlayState: StateFlow<OverlayUiState> = overlayCoordinator.overlayState
    val clipboardItem = scriptClipboard.current

    private val _hasOverlayPermission = MutableStateFlow(canDrawOverlays())
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    private val _notices = MutableSharedFlow<UiNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<UiNotice> = _notices.asSharedFlow()

    fun notifyMessage(text: String, isError: Boolean = false) {
        _notices.tryEmit(UiNotice.Message(text, isError))
    }

    init {
        viewModelScope.launch {
            overlayCoordinator.pickerResults.collect { result ->
                applyPickerResult(result)
            }
        }
    }

    fun refreshDiagnostics() {
        _hasOverlayPermission.value = canDrawOverlays()
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(app)

    fun createNewMacro(name: String, targetApp: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val cleanName = name.trim().ifBlank { "Untitled Macro" }
            val macro = MacroDefinition(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                targetAppLabel = targetApp.trim().ifBlank { "Dual Flyff" },
                steps = listOf(
                    KeyPressStep(
                        label = "Key 1 (A2)",
                        customKeyCode = KeyEvent.KEYCODE_1,
                        keyLabel = "1",
                        holdMs = 45L,
                        targetWebView = WebViewTarget.ACCOUNT_2
                    ),
                    DelayStep(label = "Cooldown", minMs = 450L, maxMs = 650L)
                )
            )
            repository.saveMacro(macro)
            onCreated(macro.id)
        }
    }

    fun createDualFlyffDemoMacro(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val loopId = UUID.randomUUID().toString()
            val macro = MacroDefinition(
                id = UUID.randomUUID().toString(),
                name = "Dual Flyff A2 Buff + A1 Action",
                description = "Alternates inputs between Account 2 (Top) and Account 1 Main (Bottom) in a single loop.",
                targetAppLabel = "Dual Flyff",
                loopCount = 1,
                pollingIntervalMs = 200L,
                steps = listOf(
                    LoopStartStep(id = loopId, label = "Repeat 3x", iterations = 3),
                    KeyPressStep(
                        label = "A2 Buff Key 1",
                        customKeyCode = KeyEvent.KEYCODE_1,
                        keyLabel = "1",
                        holdMs = 45L,
                        targetWebView = WebViewTarget.ACCOUNT_2
                    ),
                    DelayStep(label = "Cast Delay", minMs = 400L, maxMs = 550L),
                    KeyPressStep(
                        label = "A1 Main Key Space",
                        customKeyCode = KeyEvent.KEYCODE_SPACE,
                        keyLabel = "Space",
                        holdMs = 45L,
                        targetWebView = WebViewTarget.ACCOUNT_1
                    ),
                    DelayStep(label = "Pace Interval", minMs = 350L, maxMs = 500L),
                    LoopEndStep(label = "End Loop", loopStartId = loopId)
                )
            )
            repository.saveMacro(macro)
            _notices.tryEmit(UiNotice.Message("Created demo flow '${macro.name}'"))
            onCreated(macro.id)
        }
    }

    fun observeMacro(macroId: String) = repository.observeMacro(macroId)

    fun saveMacro(macro: MacroDefinition) {
        viewModelScope.launch {
            repository.saveMacro(macro)
        }
    }

    fun duplicateMacro(macroId: String) {
        viewModelScope.launch {
            val copy = repository.duplicateMacro(macroId)
            if (copy != null) {
                _notices.tryEmit(UiNotice.Message("Duplicated '${copy.name}'"))
            }
        }
    }

    fun deleteMacro(macroId: String) {
        viewModelScope.launch {
            if (executionState.value.activeMacroId == macroId) {
                interpreter.stop()
            }
            repository.deleteMacro(macroId)
            _notices.tryEmit(UiNotice.Message("Macro deleted"))
        }
    }

    fun runMacroDirectly(macro: MacroDefinition) {
        overlayCoordinator.setLoadedMacro(macro.id, macro.name)
        interpreter.start(macro)
    }

    fun launchFloatingDock(macro: MacroDefinition) {
        if (!canDrawOverlays()) {
            _notices.tryEmit(UiNotice.Message("Grant 'Display over other apps' first.", isError = true))
            return
        }
        overlayCoordinator.setLoadedMacro(macro.id, macro.name)
        val intent = Intent(app, MacroOverlayService::class.java).apply {
            action = MacroOverlayService.ACTION_SHOW_DOCK
            putExtra(MacroOverlayService.EXTRA_MACRO_ID, macro.id)
            putExtra(MacroOverlayService.EXTRA_MACRO_NAME, macro.name)
        }
        startForegroundServiceCompat(intent)
    }

    fun startMediaProjectionService(resultCode: Int, data: Intent) {
        val intent = Intent(app, MacroOverlayService::class.java).apply {
            action = MacroOverlayService.ACTION_START_PROJECTION
            putExtra(MacroOverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MacroOverlayService.EXTRA_RESULT_DATA, data)
        }
        startForegroundServiceCompat(intent)
        _notices.tryEmit(UiNotice.Message("Screen detection capture active"))
    }

    fun stopMediaProjection() {
        screenDetector.stop()
        _notices.tryEmit(UiNotice.Message("Screen capture stopped"))
    }

    fun startScreenPicker(
        macro: MacroDefinition,
        step: MacroStep,
        modeOverride: PickerMode? = null,
        functionId: String? = null
    ) {
        if (!canDrawOverlays()) {
            _notices.tryEmit(UiNotice.Message("Grant 'Display over other apps' to use the visual picker.", isError = true))
            return
        }
        val request = when (step) {
            is TapStep -> PickerRequest(
                macroId = macro.id,
                stepId = step.id,
                mode = modeOverride ?: PickerMode.PICK_TAP,
                functionId = functionId,
                initialX = step.x,
                initialY = step.y,
                returnToAppOnComplete = true
            )
            is SwipeStep -> PickerRequest(
                macroId = macro.id,
                stepId = step.id,
                mode = modeOverride ?: PickerMode.PICK_SWIPE,
                functionId = functionId,
                initialX = step.startX,
                initialY = step.startY,
                initialEndX = step.endX,
                initialEndY = step.endY,
                returnToAppOnComplete = true
            )
            is IfColorStep -> PickerRequest(
                macroId = macro.id,
                stepId = step.id,
                mode = modeOverride ?: PickerMode.PICK_COLOR,
                functionId = functionId,
                initialX = step.x.toFloat(),
                initialY = step.y.toFloat(),
                returnToAppOnComplete = true
            )
            is IfImageStep -> PickerRequest(
                macroId = macro.id,
                stepId = step.id,
                mode = modeOverride ?: PickerMode.PICK_TEMPLATE_CROP,
                functionId = functionId,
                initialLeft = step.searchLeft,
                initialTop = step.searchTop,
                initialRight = step.searchRight,
                initialBottom = step.searchBottom,
                returnToAppOnComplete = true
            )
            else -> return
        }
        overlayCoordinator.setLoadedMacro(macro.id, macro.name)
        overlayCoordinator.startPicker(request)
        val intent = Intent(app, MacroOverlayService::class.java).apply {
            action = MacroOverlayService.ACTION_START_PICKER
        }
        startForegroundServiceCompat(intent)
    }

    fun exportMacrosToUri(uri: Uri, macroIds: Set<String>? = null) {
        viewModelScope.launch {
            runCatching {
                val payload = repository.exportMacrosToJson(macroIds)
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open destination file")
                }
            }.onSuccess {
                _notices.tryEmit(UiNotice.Message("Exported macro bundle to JSON"))
            }.onFailure { err ->
                _notices.tryEmit(UiNotice.Message("Export failed: ${err.message}", isError = true))
            }
        }
    }

    fun importMacrosFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to read selected JSON file")
                }
                repository.importMacrosFromJson(raw).getOrThrow()
            }.onSuccess { count ->
                _notices.tryEmit(UiNotice.Message("Imported $count macro(s) from JSON"))
            }.onFailure { err ->
                _notices.tryEmit(UiNotice.Message("Invalid macro JSON: ${err.message}", isError = true))
            }
        }
    }

    fun cropTemplate(
        templateId: String,
        trimLeft: Int,
        trimTop: Int,
        trimRight: Int,
        trimBottom: Int,
        saveAsNew: Boolean,
        onCropped: (TemplateAssetEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.cropTemplate(
                templateId = templateId,
                trimLeft = trimLeft,
                trimTop = trimTop,
                trimRight = trimRight,
                trimBottom = trimBottom,
                saveAsNew = saveAsNew
            )
            if (result != null) {
                onCropped(result)
                val action = if (saveAsNew) "Saved cropped copy" else "Cropped"
                _notices.tryEmit(UiNotice.Message("$action '${result.name}' (${result.width}×${result.height} px)"))
            } else {
                _notices.tryEmit(UiNotice.Message("Unable to crop template image", isError = true))
            }
        }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch {
            repository.deleteTemplate(id)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    private fun applyPickerResult(result: PickerResult) {
        val msg = when (result) {
            is PickerResult.TapPicked -> if (result.stepId.isBlank()) {
                "Added tap at (${result.x.toInt()}, ${result.y.toInt()})"
            } else {
                "Updated tap to (${result.x.toInt()}, ${result.y.toInt()})"
            }
            is PickerResult.SwipePicked -> "Updated swipe gesture from screen picker"
            is PickerResult.ColorPicked -> "Sampled ${result.hexColor} at (${result.x}, ${result.y})"
            is PickerResult.TemplatePicked -> "Captured template '${result.templateName}'"
            is PickerResult.RoiPicked -> "Updated search area (${result.region.left},${result.region.top})–(${result.region.right},${result.region.bottom})"
        }
        _notices.tryEmit(UiNotice.Message(msg))
    }

    private fun startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }
}
