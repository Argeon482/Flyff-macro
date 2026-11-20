package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.KeyEvent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.MacroRepository
import com.example.dualweb.DualLayoutMode
import com.example.dualweb.DualWebInputBridge
import com.example.dualweb.DualWebMacroDelegate
import com.example.dualweb.WebViewTextInputHelper
import com.example.engine.ExecutionStatus
import com.example.engine.MacroInterpreter
import com.example.model.BranchAction
import com.example.model.CallFunctionStep
import com.example.model.IfImageStep
import com.example.model.IfVariableStep
import com.example.model.InputInjectionMode
import com.example.model.JumpStep
import com.example.model.KeyPressStep
import com.example.model.LoopEndStep
import com.example.model.LoopStartStep
import com.example.model.MacroCloner
import com.example.model.MacroDefinition
import com.example.model.MacroFunction
import com.example.model.ScriptClipboard
import com.example.model.ScriptClipboardItem
import com.example.model.SetVariableStep
import com.example.model.StopStep
import com.example.model.TapStep
import com.example.model.TouchGuardPolicy
import com.example.model.VarComparator
import com.example.model.VarOperation
import com.example.model.WebViewTarget
import com.example.service.OverlayCoordinator
import com.example.service.PickerMode
import com.example.service.PickerRequest
import com.example.service.PickerResult
import com.example.ui.components.computeTemplateCropRect
import com.example.vision.ScreenDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appName_isShizuMacro() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ShizuMacro", appName)
    }

    @Test
    fun repository_saveExportAndImportRoundTrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())

        val loopId = "loop-1"
        val original = MacroDefinition(
            id = "macro-1",
            name = "Test Macro",
            targetAppLabel = "Sandbox",
            loopCount = 2,
            steps = listOf(
                SetVariableStep(variableName = "count", operation = VarOperation.SET, valueExpression = "1"),
                LoopStartStep(id = loopId, iterations = 3),
                SetVariableStep(variableName = "count", operation = VarOperation.INCREMENT, valueExpression = "2"),
                LoopEndStep(loopStartId = loopId)
            )
        )
        repo.saveMacro(original)

        val jsonPayload = repo.exportMacrosToJson(setOf(original.id))
        repo.deleteMacro(original.id)
        assertTrue(repo.macros.first().isEmpty())

        val imported = repo.importMacrosFromJson(jsonPayload)
        assertEquals(1, imported.getOrThrow())

        val saved = repo.macros.first()
        assertEquals(1, saved.size)
        assertEquals("Test Macro", saved.first().name)
        assertEquals(4, saved.first().steps.size)
        db.close()
    }

    @Test
    fun screenDetector_matchesColorAndTemplate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = ScreenDetector(context)

        val screen = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(screen)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply { color = Color.rgb(16, 185, 129) }
        canvas.drawRect(Rect(40, 50, 80, 90), paint)

        val colorMatch = detector.matchColor(screen, 60, 70, "#10B981", tolerancePct = 5)
        assertTrue(colorMatch.matched)
        assertEquals("#10B981", colorMatch.sampledHex)

        val template = Bitmap.createBitmap(screen, 44, 54, 24, 24)
        val imgMatch = detector.matchTemplate(screen, template, null, minConfidencePct = 90)
        assertTrue(imgMatch.matched)
        assertTrue(imgMatch.confidencePct >= 95)

        template.recycle()
        screen.recycle()
    }

    @Test
    fun interpreter_executesLoopVariablesAndBranching() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())
        val detector = ScreenDetector(context)
        val coordinator = OverlayCoordinator()
        val interpreter = MacroInterpreter(repo, detector, coordinator)

        val loopId = "l1"
        val macro = MacroDefinition(
            id = "logic-test",
            name = "Logic Engine Test",
            loopCount = 1,
            steps = listOf(
                SetVariableStep(variableName = "score", operation = VarOperation.SET, valueExpression = "0"),
                LoopStartStep(id = loopId, iterations = 4),
                SetVariableStep(variableName = "score", operation = VarOperation.INCREMENT, valueExpression = "5"),
                LoopEndStep(loopStartId = loopId),
                IfVariableStep(
                    variableName = "score",
                    comparator = VarComparator.EQUALS,
                    compareValue = "20",
                    thenAction = BranchAction.CONTINUE_NEXT,
                    elseAction = BranchAction.STOP_MACRO
                ),
                SetVariableStep(variableName = "verified", operation = VarOperation.SET, valueExpression = "true")
            )
        )

        interpreter.start(macro)
        var attempts = 0
        while (interpreter.state.value.status == ExecutionStatus.RUNNING && attempts < 60) {
            delay(50)
            attempts++
        }

        val finalState = interpreter.state.value
        assertEquals(ExecutionStatus.IDLE, finalState.status)
        assertEquals("20", finalState.variables["score"])
        assertEquals("true", finalState.variables["verified"])
        db.close()
    }

    @Test
    fun overlayCoordinator_preservesPickerDraftAndRoiState() = runBlocking {
        val coordinator = OverlayCoordinator()
        coordinator.setOverlayShowing(true, "m1", "Macro 1")
        coordinator.setOverlayStudioOpen(true, editingStepId = "step-img")
        assertTrue(coordinator.overlayState.value.showOverlayStudio)
        assertEquals("step-img", coordinator.overlayState.value.studioEditingStepId)

        val initialReq = PickerRequest(
            macroId = "m1",
            stepId = "step-img",
            mode = PickerMode.PICK_SEARCH_ROI,
            initialLeft = 100,
            initialTop = 200,
            initialRight = 500,
            initialBottom = 700
        )
        coordinator.startPicker(initialReq)
        assertEquals(PickerMode.PICK_SEARCH_ROI, coordinator.overlayState.value.pickerMode)
        assertEquals(false, coordinator.overlayState.value.showOverlayStudio)

        // Simulate user dragging handles then tapping Switch App
        val draggedReq = initialReq.copy(initialLeft = 140, initialTop = 260, initialRight = 620, initialBottom = 880)
        coordinator.updatePendingPickerRequest(draggedReq)
        coordinator.setPickerPassThrough(true)
        assertTrue(coordinator.overlayState.value.isPickerPassThrough)
        assertEquals(620, coordinator.overlayState.value.pendingPickerRequest?.initialRight)

        // Returning to Place Target preserves dragged bounds
        coordinator.setPickerPassThrough(false)
        assertEquals(140, coordinator.overlayState.value.pendingPickerRequest?.initialLeft)

        coordinator.publishPickerResult(PickerResult.RoiPicked("m1", "step-img", Rect(140, 260, 620, 880)))
        assertEquals(PickerMode.NONE, coordinator.overlayState.value.pickerMode)

        // Verify IfImageStep ROI survives repository save/load
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())
        repo.saveMacro(
            MacroDefinition(
                id = "m1",
                name = "Vision Macro",
                steps = listOf(
                    IfImageStep(
                        id = "step-img",
                        templateId = "tpl-1",
                        templateName = "Button",
                        searchLeft = 140,
                        searchTop = 260,
                        searchRight = 620,
                        searchBottom = 880
                    )
                )
            )
        )
        val loaded = repo.getMacro("m1")!!.steps.first() as IfImageStep
        assertEquals(140, loaded.searchLeft)
        assertEquals(880, loaded.searchBottom)
        db.close()
    }

    @Test
    fun macro_injectionMode_persistsAndDefaultsCleanly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())

        val stylusMacro = MacroDefinition(
            id = "stylus-1",
            name = "Game Keyboard & Touch Flow",
            injectionMode = InputInjectionMode.CO_TOUCH_MULTIPLEX,
            touchGuardPolicy = TouchGuardPolicy.YIELD_350MS,
            steps = listOf(
                KeyPressStep(
                    customKeyCode = KeyEvent.KEYCODE_Q,
                    keyLabel = "Q",
                    holdMs = 50L,
                    metaState = KeyEvent.META_SHIFT_ON
                )
            )
        )
        repo.saveMacro(stylusMacro)

        val loaded = repo.getMacro("stylus-1")!!
        assertEquals(InputInjectionMode.CO_TOUCH_MULTIPLEX, loaded.injectionMode)
        assertEquals(TouchGuardPolicy.YIELD_350MS, loaded.touchGuardPolicy)
        val loadedKeyStep = loaded.steps.first() as KeyPressStep
        assertEquals(KeyEvent.KEYCODE_Q, loadedKeyStep.resolvedKeyCode())
        assertEquals(50L, loadedKeyStep.holdMs)
        assertEquals(KeyEvent.META_SHIFT_ON, loadedKeyStep.metaState)
        assertEquals("Shift+Q", loadedKeyStep.formattedBinding())

        val legacyBundleJson = """
            {
              "schemaVersion": 1,
              "exportedAt": 1700000000000,
              "macros": [
                {
                  "id": "legacy-1",
                  "name": "Legacy Flow",
                  "steps": []
                }
              ]
            }
        """.trimIndent()
        val count = repo.importMacrosFromJson(legacyBundleJson).getOrThrow()
        assertEquals(1, count)
        val importedLegacy = repo.macros.first().first { it.name == "Legacy Flow" }
        assertEquals(InputInjectionMode.TOUCHSCREEN, importedLegacy.injectionMode)
        assertEquals(TouchGuardPolicy.OFF, importedLegacy.touchGuardPolicy)
        db.close()
    }

    @Test
    fun interpreter_dispatchesPerStepAccount2AndAccount1ViaDualWebBridge() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())
        val detector = ScreenDetector(context)
        val coordinator = OverlayCoordinator()
        val dualWebBridge = DualWebInputBridge()

        val recorded = mutableListOf<String>()
        val delegate = object : DualWebMacroDelegate {
            override fun dispatchKey(
                keyCode: Int,
                holdMs: Long,
                metaState: Int,
                targetOverride: WebViewTarget
            ): Boolean {
                recorded += "KEY:$keyCode:${targetOverride.name}"
                return true
            }

            override fun dispatchTap(
                screenX: Float,
                screenY: Float,
                holdMs: Long,
                jitterPx: Int,
                targetOverride: WebViewTarget
            ): Boolean {
                recorded += "TAP:${screenX.toInt()},${screenY.toInt()}:${targetOverride.name}"
                return true
            }

            override fun dispatchSwipe(
                startX: Float,
                startY: Float,
                endX: Float,
                endY: Float,
                durationMs: Long,
                steps: Int,
                targetOverride: WebViewTarget
            ): Boolean = true

            override fun describeTarget(targetOverride: WebViewTarget): String = targetOverride.name
        }
        dualWebBridge.attach(delegate)

        val interpreter = MacroInterpreter(repo, detector, coordinator, dualWebBridge)
        val macro = MacroDefinition(
            id = "dual-web-routing-test",
            name = "Dual Web Routing Test",
            loopCount = 1,
            steps = listOf(
                KeyPressStep(
                    label = "Buff A2",
                    customKeyCode = KeyEvent.KEYCODE_1,
                    keyLabel = "1",
                    targetWebView = WebViewTarget.ACCOUNT_2
                ),
                TapStep(
                    label = "Tap A1 Main",
                    x = 320f,
                    y = 1400f,
                    targetWebView = WebViewTarget.ACCOUNT_1
                ),
                KeyPressStep(
                    label = "Action A1 Main",
                    customKeyCode = KeyEvent.KEYCODE_SPACE,
                    keyLabel = "Space",
                    targetWebView = WebViewTarget.ACCOUNT_1
                )
            )
        )

        interpreter.start(macro)
        var attempts = 0
        while (interpreter.state.value.status == ExecutionStatus.RUNNING && attempts < 80) {
            delay(40)
            attempts++
        }

        assertEquals(ExecutionStatus.IDLE, interpreter.state.value.status)
        assertEquals(
            listOf(
                "KEY:${KeyEvent.KEYCODE_1}:ACCOUNT_2",
                "TAP:320,1400:ACCOUNT_1",
                "KEY:${KeyEvent.KEYCODE_SPACE}:ACCOUNT_1"
            ),
            recorded
        )

        // Detaching Dual WebView should fail preflight cleanly with clear Dual Flyff message
        dualWebBridge.detach(delegate)
        interpreter.start(macro)
        assertEquals(ExecutionStatus.ERROR, interpreter.state.value.status)
        assertTrue(interpreter.state.value.errorMessage?.contains("Dual Flyff") == true)
        db.close()
    }

    @Test
    fun interpreter_executesFunctionsNestedCallsAndRecursionGuard() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())
        val detector = ScreenDetector(context)
        val coordinator = OverlayCoordinator()
        val interpreter = MacroInterpreter(repo, detector, coordinator)

        val fnInner = MacroFunction(
            id = "fn-inner",
            name = "AddThree",
            steps = listOf(
                SetVariableStep(variableName = "total", operation = VarOperation.INCREMENT, valueExpression = "3")
            )
        )
        val fnOuter = MacroFunction(
            id = "fn-outer",
            name = "Combo",
            steps = listOf(
                CallFunctionStep(label = "Call Inner", functionId = fnInner.id, functionName = fnInner.name, repeatCount = 2),
                SetVariableStep(variableName = "total", operation = VarOperation.INCREMENT, valueExpression = "1")
            )
        )
        val fnRecursive = MacroFunction(
            id = "fn-self",
            name = "SelfCall",
            steps = listOf(
                SetVariableStep(variableName = "depthHits", operation = VarOperation.INCREMENT, valueExpression = "1"),
                CallFunctionStep(label = "Recurse", functionId = "fn-self", functionName = "SelfCall", repeatCount = 1)
            )
        )
        val fnStop = MacroFunction(
            id = "fn-stop",
            name = "StopEarly",
            steps = listOf(
                SetVariableStep(variableName = "stoppedInFn", operation = VarOperation.SET, valueExpression = "yes"),
                StopStep(label = "Abort")
            )
        )

        val macro = MacroDefinition(
            id = "fn-engine-test",
            name = "Functions Engine Test",
            loopCount = 1,
            functions = listOf(fnInner, fnOuter, fnStop),
            steps = listOf(
                SetVariableStep(variableName = "total", operation = VarOperation.SET, valueExpression = "0"),
                // Combo runs twice -> each run calls AddThree(2x = +6) + 1 = +7 => total = 14
                CallFunctionStep(label = "Run Combo", functionId = fnOuter.id, functionName = fnOuter.name, repeatCount = 2),
                // Stop inside function must abort before afterStop is set
                CallFunctionStep(label = "Stop Now", functionId = fnStop.id, functionName = fnStop.name, repeatCount = 1),
                SetVariableStep(variableName = "afterStop", operation = VarOperation.SET, valueExpression = "should_not_run")
            )
        )

        interpreter.start(macro)
        var attempts = 0
        while (interpreter.state.value.status == ExecutionStatus.RUNNING && attempts < 80) {
            delay(40)
            attempts++
        }

        val finalState = interpreter.state.value
        assertEquals(ExecutionStatus.IDLE, finalState.status)
        assertEquals("14", finalState.variables["total"])
        assertEquals("yes", finalState.variables["stoppedInFn"])
        assertNull(finalState.variables["afterStop"])

        // Self-recursive function must trigger MAX_CALL_DEPTH error cleanly without hanging
        val recursiveMacro = MacroDefinition(
            id = "fn-recursion-test",
            name = "Recursion Guard Test",
            loopCount = 1,
            functions = listOf(fnRecursive),
            steps = listOf(
                SetVariableStep(variableName = "depthHits", operation = VarOperation.SET, valueExpression = "0"),
                CallFunctionStep(label = "Test Recursion", functionId = fnRecursive.id, functionName = fnRecursive.name, repeatCount = 1)
            )
        )
        interpreter.start(recursiveMacro)
        attempts = 0
        while (interpreter.state.value.status == ExecutionStatus.RUNNING && attempts < 80) {
            delay(40)
            attempts++
        }
        val recState = interpreter.state.value
        assertEquals(ExecutionStatus.ERROR, recState.status)
        assertEquals("16", recState.variables["depthHits"])
        assertTrue(recState.lastTelemetry.contains("Max function call depth"))
        db.close()
    }

    @Test
    fun scriptClipboardAndCloner_clonesStepsAndFunctionsWithRemappedIds() {
        val clipboard = ScriptClipboard()
        val loopStart = LoopStartStep(id = "loop-start-1", label = "Loop 2x", iterations = 2)
        val body = SetVariableStep(id = "set-1", label = "Bump", variableName = "x", operation = VarOperation.INCREMENT, valueExpression = "1")
        val branch = IfVariableStep(
            id = "if-1",
            variableName = "x",
            comparator = VarComparator.EQUALS,
            compareValue = "2",
            thenAction = BranchAction.JUMP_TO_STEP,
            thenJumpStepId = body.id,
            elseAction = BranchAction.CONTINUE_NEXT
        )
        val jump = JumpStep(id = "jump-1", targetStepId = loopStart.id)
        val loopEnd = LoopEndStep(id = "loop-end-1", loopStartId = loopStart.id)

        val fn = MacroFunction(
            id = "fn-orig",
            name = "FarmLoop",
            steps = listOf(loopStart, body, branch, jump, loopEnd)
        )

        clipboard.copyStep(body)
        val copiedStepItem = clipboard.current.value as ScriptClipboardItem.SingleStep
        val clonedSingle = MacroCloner.cloneStep(copiedStepItem.step) as SetVariableStep
        assertNotEquals(body.id, clonedSingle.id)
        assertEquals(body.variableName, clonedSingle.variableName)

        clipboard.copyFunction(fn)
        val copiedFnItem = clipboard.current.value as ScriptClipboardItem.FunctionClip
        val clonedFn1 = MacroCloner.cloneFunction(copiedFnItem.function, existingNames = setOf("FarmLoop"))
        val clonedFn2 = MacroCloner.cloneFunction(copiedFnItem.function, existingNames = setOf("FarmLoop", "FarmLoop (Copy)"))

        assertNotEquals(fn.id, clonedFn1.id)
        assertEquals("FarmLoop (Copy)", clonedFn1.name)
        assertEquals("FarmLoop (2)", clonedFn2.name)
        assertEquals(5, clonedFn1.steps.size)

        val clonedLoopStart = clonedFn1.steps[0] as LoopStartStep
        val clonedBody = clonedFn1.steps[1] as SetVariableStep
        val clonedBranch = clonedFn1.steps[2] as IfVariableStep
        val clonedJump = clonedFn1.steps[3] as JumpStep
        val clonedLoopEnd = clonedFn1.steps[4] as LoopEndStep

        assertNotEquals(loopStart.id, clonedLoopStart.id)
        assertNotEquals(body.id, clonedBody.id)
        assertEquals(clonedLoopStart.id, clonedLoopEnd.loopStartId)
        assertEquals(clonedBody.id, clonedBranch.thenJumpStepId)
        assertEquals(clonedLoopStart.id, clonedJump.targetStepId)

        clipboard.clear()
        assertNull(clipboard.current.value)
    }

    @Test
    fun repository_persistsAndExportsMacroFunctionsWithTemplates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())

        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.CYAN)
        val template = repo.saveTemplateBitmap("LootIcon", bmp)
        bmp.recycle()

        val fn = MacroFunction(
            id = "fn-loot",
            name = "CheckLoot",
            steps = listOf(
                IfImageStep(
                    id = "img-in-fn",
                    templateId = template.id,
                    templateName = template.name,
                    minConfidencePct = 85
                )
            )
        )
        val macro = MacroDefinition(
            id = "macro-fn-1",
            name = "Modular Run",
            functions = listOf(fn),
            steps = listOf(
                CallFunctionStep(label = "Call CheckLoot", functionId = fn.id, functionName = fn.name, repeatCount = 3)
            )
        )
        repo.saveMacro(macro)

        val reloaded = repo.getMacro(macro.id)!!
        assertEquals(1, reloaded.functions.size)
        assertEquals("CheckLoot", reloaded.functions.first().name)
        assertEquals(1, reloaded.functions.first().steps.size)

        val exportedJson = repo.exportMacrosToJson(setOf(macro.id))
        repo.deleteMacro(macro.id)
        repo.deleteTemplate(template.id)

        val importedCount = repo.importMacrosFromJson(exportedJson).getOrThrow()
        assertEquals(1, importedCount)

        val importedMacro = repo.macros.first().first()
        assertEquals(1, importedMacro.functions.size)
        val importedImgStep = importedMacro.functions.first().steps.first() as IfImageStep
        assertNotEquals(template.id, importedImgStep.templateId)
        val restoredBitmap = repo.loadTemplateBitmap(importedImgStep.templateId)
        assertTrue(restoredBitmap != null)
        restoredBitmap?.recycle()
        db.close()
    }

    @Test
    fun templateCropEditor_trimsBy1PxPerSideAndPersistsCopyOrOverwrite() = runBlocking {
        // Verify pure 4-side crop bounds math + min-size clamping
        val rect = computeTemplateCropRect(
            sourceWidth = 20,
            sourceHeight = 16,
            trimLeft = 1,
            trimTop = 2,
            trimRight = 3,
            trimBottom = 4,
            minSize = 4
        )
        assertEquals(1, rect.left)
        assertEquals(2, rect.top)
        assertEquals(16, rect.width)
        assertEquals(10, rect.height)

        val clamped = computeTemplateCropRect(
            sourceWidth = 10,
            sourceHeight = 10,
            trimLeft = 5,
            trimTop = 5,
            trimRight = 9,
            trimBottom = 9,
            minSize = 4
        )
        assertEquals(4, clamped.width)
        assertEquals(4, clamped.height)

        // Verify repository cropTemplate saves exact trimmed pixels for both Copy and Overwrite
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())

        // 20x16 bitmap with RED outer border and GREEN inner rectangle [2..17) x [1..13)
        val bmp = Bitmap.createBitmap(20, 16, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.RED)
        val canvas = Canvas(bmp)
        val innerPaint = Paint().apply { color = Color.GREEN }
        canvas.drawRect(Rect(2, 1, 17, 13), innerPaint)

        val original = repo.saveTemplateBitmap("TargetBtn", bmp)
        bmp.recycle()

        // Save Copy trimmed by L=2, T=1, R=3, B=3 -> 15x12 pure GREEN
        val copy = repo.cropTemplate(
            templateId = original.id,
            trimLeft = 2,
            trimTop = 1,
            trimRight = 3,
            trimBottom = 3,
            saveAsNew = true
        )!!
        assertNotEquals(original.id, copy.id)
        assertEquals("TargetBtn (Crop)", copy.name)
        assertEquals(15, copy.width)
        assertEquals(12, copy.height)
        val copyBmp = repo.loadTemplateBitmap(copy.id)!!
        assertEquals(15, copyBmp.width)
        assertEquals(12, copyBmp.height)
        assertEquals(Color.GREEN, copyBmp.getPixel(0, 0))
        assertEquals(Color.GREEN, copyBmp.getPixel(14, 11))
        copyBmp.recycle()

        // Original should remain untouched at 20x16 with RED corner
        val origStillBmp = repo.loadTemplateBitmap(original.id)!!
        assertEquals(20, origStillBmp.width)
        assertEquals(16, origStillBmp.height)
        assertEquals(Color.RED, origStillBmp.getPixel(0, 0))
        origStillBmp.recycle()

        // Overwrite original by trimming 1px on each side -> 18x14
        val overwritten = repo.cropTemplate(
            templateId = original.id,
            trimLeft = 1,
            trimTop = 1,
            trimRight = 1,
            trimBottom = 1,
            saveAsNew = false
        )!!
        assertEquals(original.id, overwritten.id)
        assertEquals(18, overwritten.width)
        assertEquals(14, overwritten.height)
        val overwrittenBmp = repo.loadTemplateBitmap(original.id)!!
        assertEquals(18, overwrittenBmp.width)
        assertEquals(14, overwrittenBmp.height)
        // Pixel (1,0) of trimmed bitmap was (2,1) of original -> GREEN
        assertEquals(Color.GREEN, overwrittenBmp.getPixel(1, 0))
        overwrittenBmp.recycle()

        repo.deleteTemplate(original.id)
        repo.deleteTemplate(copy.id)
        db.close()
    }

    @Test
    fun webViewTextInputHelper_resolvesQuickTypeTargetAndEscapesJsPayload() {
        assertEquals(1, WebViewTextInputHelper.resolveQuickTypeInitialAccount(DualLayoutMode.SINGLE_ACCT_1, 2))
        assertEquals(2, WebViewTextInputHelper.resolveQuickTypeInitialAccount(DualLayoutMode.SINGLE_ACCT_2, 1))
        assertEquals(1, WebViewTextInputHelper.resolveQuickTypeInitialAccount(DualLayoutMode.SPLIT_50_50, 1))
        assertEquals(2, WebViewTextInputHelper.resolveQuickTypeInitialAccount(DualLayoutMode.SPLIT_50_50, 2))

        val sampleText = "/say \"Hello\" \\ world"
        val jsWithEnter = WebViewTextInputHelper.buildJsTextInjectionScript(
            text = sampleText,
            pressEnter = true
        )
        assertTrue(jsWithEnter.contains("var str = ${org.json.JSONObject.quote(sampleText)};"))
        assertTrue(jsWithEnter.contains("""\"Hello\""""))
        assertTrue(jsWithEnter.contains("""\\"""))
        assertTrue(jsWithEnter.contains("if (true)"))
        assertTrue(jsWithEnter.contains("key: 'Enter'"))

        val jsNoEnter = WebViewTextInputHelper.buildJsTextInjectionScript(
            text = "125000",
            pressEnter = false
        )
        assertTrue(jsNoEnter.contains("""var str = "125000";"""))
        assertTrue(jsNoEnter.contains("if (false)"))
    }
}
