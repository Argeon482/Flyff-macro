package com.example.dualweb

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ShizuMacroApp
import com.example.model.MacroDefinition
import com.example.model.WebViewTarget
import com.example.service.MacroOverlayService
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ShizuMacroTheme
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Immersive Fullscreen Dual-Account WebView Workspace for Flyff Universe.
 *
 * - Supports interactive user touches on EITHER WebView in 50/50 Split AND Single WebView views
 *   (A1 Full or A2 Full), while keeping BOTH WebViews `View.VISIBLE` so Chromium never throttles
 *   `requestAnimationFrame` or pauses WebGL.
 * - Account 2 (`WebView 2`) lives in isolated `android:process=":acct2"` (`setDataDirectorySuffix("acct2")`).
 *   Live user touches landing on Account 2's SurfaceView are forwarded via `oneway` AIDL directly into
 *   `webView2.dispatchTouchEvent(...)` so tapping/dragging works reliably on all Android 11–15 OEMs.
 * - Registers into `ShizuMacroApp.dualWebInputBridge` so macros built in the Floating Builder run
 *   directly on Account 1 or Account 2 via Start / Stop without Accessibility, InputManager, or ADB.
 */
class DualWebActivity : ComponentActivity() {

    private lateinit var app: ShizuMacroApp
    private lateinit var controller: Account2ClientController
    private val mainHandler = Handler(Looper.getMainLooper())

    private var account1Container: TouchGatedFrameLayout? = null
    private var account1WebView: WebView? = null
    private var account2SurfaceBridge: Account2SurfaceBridge? = null
    private var account2SurfaceView: SurfaceView? = null

    @Volatile
    private var lastActiveAcct1Rect: Rect = Rect()
    @Volatile
    private var lastActiveAcct2Rect: Rect = Rect()

    private val macroDelegate = object : DualWebMacroDelegate {
        override fun dispatchKey(
            keyCode: Int,
            holdMs: Long,
            metaState: Int,
            targetOverride: WebViewTarget
        ): Boolean {
            val cfg = controller.config.value
            val resolvedAcct = when (targetOverride) {
                WebViewTarget.ACCOUNT_1 -> 1
                WebViewTarget.ACCOUNT_2 -> 2
                WebViewTarget.DEFAULT -> when (cfg.macroTarget) {
                    DualWebMacroTarget.ACCOUNT_1 -> 1
                    DualWebMacroTarget.ACCOUNT_2,
                    DualWebMacroTarget.AUTO_PANE -> 2
                }
            }
            return if (resolvedAcct == 1) {
                dispatchKeyToAcct1(keyCode, holdMs, metaState, cfg.dispatchMode)
            } else {
                controller.dispatchKeyDirect(keyCode, holdMs, metaState)
            }
        }

        override fun dispatchTap(
            screenX: Float,
            screenY: Float,
            holdMs: Long,
            jitterPx: Int,
            targetOverride: WebViewTarget
        ): Boolean {
            val cfg = controller.config.value
            val jx = if (jitterPx > 0) Random.nextInt(-jitterPx, jitterPx + 1).toFloat() else 0f
            val jy = if (jitterPx > 0) Random.nextInt(-jitterPx, jitterPx + 1).toFloat() else 0f
            val px = screenX + jx
            val py = screenY + jy

            val resolvedAcct = resolveTargetAccountForPoint(targetOverride, cfg.macroTarget, px, py)
            val (normX, normY) = mapScreenPointToNormalized(resolvedAcct, px, py)
            return if (resolvedAcct == 1) {
                dispatchTapToAcct1(normX, normY, holdMs, cfg.dispatchMode)
            } else {
                controller.dispatchTapDirect(normX, normY, holdMs)
            }
        }

        override fun dispatchSwipe(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Long,
            steps: Int,
            targetOverride: WebViewTarget
        ): Boolean {
            val cfg = controller.config.value
            val resolvedAcct = resolveTargetAccountForPoint(targetOverride, cfg.macroTarget, startX, startY)
            val (snx, sny) = mapScreenPointToNormalized(resolvedAcct, startX, startY)
            val (enx, eny) = mapScreenPointToNormalized(resolvedAcct, endX, endY)
            return if (resolvedAcct == 1) {
                dispatchSwipeToAcct1(snx, sny, enx, eny, durationMs, steps, cfg.dispatchMode)
            } else {
                controller.dispatchSwipeDirect(snx, sny, enx, eny, durationMs, steps)
            }
        }

        override fun describeTarget(targetOverride: WebViewTarget): String {
            val cfg = controller.config.value
            val targetLabel = when (targetOverride) {
                WebViewTarget.ACCOUNT_1 -> "A1"
                WebViewTarget.ACCOUNT_2 -> "A2"
                WebViewTarget.DEFAULT -> cfg.macroTarget.shortLabel
            }
            return "$targetLabel/${if (cfg.dispatchMode == InputDispatchMode.NATIVE_EVENT) "Native" else "JS"}"
        }

        override fun detectTargetForScreenPoint(screenX: Float, screenY: Float): WebViewTarget {
            refreshLiveScreenRects()
            val layout = controller.config.value.layoutMode
            if (layout == DualLayoutMode.SINGLE_ACCT_1) return WebViewTarget.ACCOUNT_1
            if (layout == DualLayoutMode.SINGLE_ACCT_2) return WebViewTarget.ACCOUNT_2
            val ix = screenX.roundToInt()
            val iy = screenY.roundToInt()
            return when {
                lastActiveAcct1Rect.width() > 20 && lastActiveAcct1Rect.contains(ix, iy) -> WebViewTarget.ACCOUNT_1
                lastActiveAcct2Rect.width() > 20 && lastActiveAcct2Rect.contains(ix, iy) -> WebViewTarget.ACCOUNT_2
                else -> WebViewTarget.DEFAULT
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureFullscreenWindow()

        app = application as ShizuMacroApp
        controller = Account2ClientController(this)
        val startAdjacentSplit = intent.getBooleanExtra(EXTRA_START_ADJACENT_SPLIT, false)

        val bridge = Account2SurfaceBridge(controller)
        account2SurfaceBridge = bridge
        app.dualWebInputBridge.attach(macroDelegate)

        controller.bind {
            bridge.tryAttachOrResize()
            if (startAdjacentSplit) {
                controller.launchAccount2Activity(adjacentSplitScreen = true)
            }
        }

        setContent {
            ShizuMacroTheme {
                DualWebFullscreenWorkspace(
                    app = app,
                    controller = controller,
                    surfaceBridge = bridge,
                    onWebView1Bound = { container, wv ->
                        account1Container = container
                        account1WebView = wv
                    },
                    onSurfaceView2Bound = { sv ->
                        account2SurfaceView = sv
                    },
                    onReloadAcct1 = { account1WebView?.reload() },
                    onClearAcct1Session = {
                        account1WebView?.let { wv ->
                            WebViewFactory.clearSessionData(wv)
                            wv.loadUrl(DualWebConstants.FLYFF_UNIVERSE_URL)
                        }
                    },
                    onToggleAcct1Ua = { desktop ->
                        controller.setAcct1DesktopUa(desktop)
                        account1WebView?.let { wv ->
                            WebViewFactory.applyUserAgent(this@DualWebActivity, wv, desktop)
                            wv.reload()
                        }
                    },
                    onOpenFloatingDock = { selectedMacro ->
                        openFloatingDockOverlay(selectedMacro)
                    },
                    onBackPress = {
                        val wv1 = account1WebView
                        if (wv1 != null && controller.config.value.layoutMode != DualLayoutMode.SINGLE_ACCT_2 && wv1.canGoBack()) {
                            wv1.goBack()
                        } else {
                            finish()
                        }
                    },
                    onExit = { finish() }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        account1WebView?.onResume()
        account1WebView?.resumeTimers()
        controller.refreshRemoteStatus()
        account2SurfaceBridge?.tryAttachOrResize()
    }

    override fun onDestroy() {
        app.dualWebInputBridge.detach(macroDelegate)
        account2SurfaceBridge?.release()
        account2SurfaceBridge = null
        account2SurfaceView = null
        controller.unbindAndRelease()
        account1WebView?.let { wv ->
            wv.stopLoading()
            wv.destroy()
        }
        account1WebView = null
        account1Container = null
        super.onDestroy()
    }

    private fun openFloatingDockOverlay(selectedMacro: MacroDefinition?) {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }
        if (selectedMacro != null) {
            app.overlayCoordinator.setLoadedMacro(selectedMacro.id, selectedMacro.name)
        }
        app.overlayCoordinator.setOverlayStudioOpen(false)
        val svcIntent = Intent(this, MacroOverlayService::class.java).apply {
            action = MacroOverlayService.ACTION_SHOW_DOCK
            if (selectedMacro != null) {
                putExtra(MacroOverlayService.EXTRA_MACRO_ID, selectedMacro.id)
                putExtra(MacroOverlayService.EXTRA_MACRO_NAME, selectedMacro.name)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svcIntent)
        } else {
            startService(svcIntent)
        }
    }

    private fun refreshLiveScreenRects() {
        account1WebView?.let { wv1 ->
            if (wv1.width > 20 && wv1.height > 20) {
                val loc = IntArray(2)
                wv1.getLocationOnScreen(loc)
                lastActiveAcct1Rect = Rect(loc[0], loc[1], loc[0] + wv1.width, loc[1] + wv1.height)
            }
        }
        account2SurfaceView?.let { sv2 ->
            if (sv2.width > 20 && sv2.height > 20) {
                val loc = IntArray(2)
                sv2.getLocationOnScreen(loc)
                lastActiveAcct2Rect = Rect(loc[0], loc[1], loc[0] + sv2.width, loc[1] + sv2.height)
            }
        }
    }

    private fun resolveTargetAccountForPoint(
        targetOverride: WebViewTarget,
        defaultTarget: DualWebMacroTarget,
        screenX: Float,
        screenY: Float
    ): Int {
        when (targetOverride) {
            WebViewTarget.ACCOUNT_1 -> return 1
            WebViewTarget.ACCOUNT_2 -> return 2
            WebViewTarget.DEFAULT -> Unit
        }
        refreshLiveScreenRects()
        return when (defaultTarget) {
            DualWebMacroTarget.ACCOUNT_1 -> 1
            DualWebMacroTarget.ACCOUNT_2 -> 2
            DualWebMacroTarget.AUTO_PANE -> {
                val layout = controller.config.value.layoutMode
                if (layout == DualLayoutMode.SINGLE_ACCT_1) return 1
                if (layout == DualLayoutMode.SINGLE_ACCT_2) return 2
                val ix = screenX.roundToInt()
                val iy = screenY.roundToInt()
                when {
                    lastActiveAcct2Rect.contains(ix, iy) -> 2
                    lastActiveAcct1Rect.contains(ix, iy) -> 1
                    else -> 2
                }
            }
        }
    }

    private fun mapScreenPointToNormalized(account: Int, screenX: Float, screenY: Float): Pair<Float, Float> {
        refreshLiveScreenRects()
        val rect = if (account == 1) lastActiveAcct1Rect else lastActiveAcct2Rect
        val otherRect = if (account == 1) lastActiveAcct2Rect else lastActiveAcct1Rect
        val ix = screenX.roundToInt()
        val iy = screenY.roundToInt()
        val rootW = window.decorView.width.coerceAtLeast(1).toFloat()
        val rootH = window.decorView.height.coerceAtLeast(1).toFloat()

        return when {
            rect.width() > 20 && rect.height() > 20 && rect.contains(ix, iy) -> {
                val nx = ((screenX - rect.left) / rect.width().toFloat()).coerceIn(0f, 1f)
                val ny = ((screenY - rect.top) / rect.height().toFloat()).coerceIn(0f, 1f)
                nx to ny
            }
            otherRect.width() > 20 && otherRect.height() > 20 && otherRect.contains(ix, iy) -> {
                val nx = ((screenX - otherRect.left) / otherRect.width().toFloat()).coerceIn(0f, 1f)
                val ny = ((screenY - otherRect.top) / otherRect.height().toFloat()).coerceIn(0f, 1f)
                nx to ny
            }
            else -> {
                // Fallback: map proportionally across the full screen so coordinates picked in single view still hit the right spot in split view.
                (screenX / rootW).coerceIn(0f, 1f) to (screenY / rootH).coerceIn(0f, 1f)
            }
        }
    }

    private fun dispatchKeyToAcct1(keyCode: Int, holdMs: Long, metaState: Int, mode: InputDispatchMode): Boolean {
        val wv1 = account1WebView ?: return false
        mainHandler.post {
            when (mode) {
                InputDispatchMode.NATIVE_EVENT ->
                    WebViewFactory.dispatchKeyNative(wv1, keyCode, holdMs, metaState, mainHandler)
                InputDispatchMode.JS_DOM ->
                    WebViewFactory.dispatchKeyJs(wv1, keyCode, holdMs, metaState)
            }
        }
        return true
    }

    private fun dispatchTapToAcct1(normX: Float, normY: Float, holdMs: Long, mode: InputDispatchMode): Boolean {
        val wv1 = account1WebView ?: return false
        mainHandler.post {
            when (mode) {
                InputDispatchMode.NATIVE_EVENT ->
                    WebViewFactory.dispatchCanvasTapNative(wv1, normX, normY, holdMs, mainHandler)
                InputDispatchMode.JS_DOM ->
                    WebViewFactory.dispatchCanvasTapJs(wv1, normX, normY, holdMs)
            }
        }
        return true
    }

    private fun dispatchSwipeToAcct1(
        startNormX: Float,
        startNormY: Float,
        endNormX: Float,
        endNormY: Float,
        durationMs: Long,
        steps: Int,
        mode: InputDispatchMode
    ): Boolean {
        val wv1 = account1WebView ?: return false
        mainHandler.post {
            when (mode) {
                InputDispatchMode.NATIVE_EVENT ->
                    WebViewFactory.dispatchCanvasSwipeNative(
                        wv1,
                        startNormX,
                        startNormY,
                        endNormX,
                        endNormY,
                        durationMs,
                        steps,
                        mainHandler
                    )
                InputDispatchMode.JS_DOM ->
                    WebViewFactory.dispatchCanvasSwipeJs(
                        wv1,
                        startNormX,
                        startNormY,
                        endNormX,
                        endNormY,
                        durationMs,
                        steps
                    )
            }
        }
        return true
    }

    private fun configureFullscreenWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        const val EXTRA_START_ADJACENT_SPLIT = "extra_start_adjacent_split"

        fun createIntent(context: Context, startAdjacentSplit: Boolean = false): Intent =
            Intent(context, DualWebActivity::class.java).apply {
                putExtra(EXTRA_START_ADJACENT_SPLIT, startAdjacentSplit)
            }
    }
}

/**
 * Wraps WebView 1 so that in `Single: Account 2` view, WebView 1 stays `View.VISIBLE`
 * at full viewport resolution (preventing Chromium rAF/WebGL throttling) while never
 * intercepting or consuming user touches meant for Account 2's SurfaceView.
 */
private class TouchGatedFrameLayout(context: Context) : FrameLayout(context) {
    var allowUserTouch: Boolean = true

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (!allowUserTouch) return false
        return super.dispatchTouchEvent(ev)
    }
}

private class Account2SurfaceBridge(
    private val controller: Account2ClientController
) : SurfaceHolder.Callback {

    private var surfaceView: SurfaceView? = null
    private var currentWidth: Int = 0
    private var currentHeight: Int = 0
    private var lastFullWidth: Int = 0
    private var lastFullHeight: Int = 0
    private var isSurfaceReady: Boolean = false
    private var isAttached: Boolean = false
    var allowUserTouchForwarding: Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    fun bindSurfaceView(view: SurfaceView) {
        surfaceView = view
        view.holder.addCallback(this)
        view.setOnTouchListener { _, event ->
            if (!allowUserTouchForwarding) return@setOnTouchListener false
            controller.dispatchUserMotionEvent(event)
            true
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceReady = true
        isAttached = false
        surfaceView?.post { tryAttachOrResize() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        currentWidth = width
        currentHeight = height
        if (width > 24 && height > 24) {
            lastFullWidth = width
            lastFullHeight = height
        }
        isSurfaceReady = width > 0 && height > 0
        tryAttachOrResize()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isSurfaceReady = false
        isAttached = false
        controller.detachSurface()
    }

    fun tryAttachOrResize() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val sv = surfaceView ?: return
        if (!isSurfaceReady || currentWidth <= 0 || currentHeight <= 0) return

        val token = sv.hostToken
        if (token == null) {
            sv.post { tryAttachOrResize() }
            return
        }

        // Preserve full logical viewport size when tucked at 2x2dp behind Single Account 1 view
        // so Flyff Universe's WebGL canvas never shrinks or pauses while hidden behind A1.
        val dm = sv.resources.displayMetrics
        val effectiveW = when {
            currentWidth > 24 -> currentWidth
            lastFullWidth > 24 -> lastFullWidth
            else -> dm.widthPixels.coerceAtLeast(1)
        }
        val effectiveH = when {
            currentHeight > 24 -> currentHeight
            lastFullHeight > 24 -> lastFullHeight
            else -> dm.heightPixels.coerceAtLeast(1)
        }

        if (isAttached) {
            if (currentWidth > 24 && currentHeight > 24) {
                controller.resizeSurface(effectiveW, effectiveH)
            }
            return
        }

        val displayId = sv.display?.displayId ?: Display.DEFAULT_DISPLAY
        val surfacePackage = controller.attachSurface(token, displayId, effectiveW, effectiveH)
        if (surfacePackage != null) {
            sv.setChildSurfacePackage(surfacePackage)
            isAttached = true
        }
    }

    fun release() {
        surfaceView?.setOnTouchListener(null)
        surfaceView?.holder?.removeCallback(this)
        surfaceView = null
        isSurfaceReady = false
        isAttached = false
    }
}

@Composable
private fun DualWebFullscreenWorkspace(
    app: ShizuMacroApp,
    controller: Account2ClientController,
    surfaceBridge: Account2SurfaceBridge,
    onWebView1Bound: (TouchGatedFrameLayout, WebView) -> Unit,
    onSurfaceView2Bound: (SurfaceView) -> Unit,
    onReloadAcct1: () -> Unit,
    onClearAcct1Session: () -> Unit,
    onToggleAcct1Ua: (Boolean) -> Unit,
    onOpenFloatingDock: (MacroDefinition?) -> Unit,
    onBackPress: () -> Unit,
    onExit: () -> Unit
) {
    val config by controller.config.collectAsState()
    val remoteStatus by controller.remoteStatus.collectAsState()
    val macros by app.repository.macros.collectAsState(initial = emptyList())
    val executionState by app.interpreter.state.collectAsState()
    val overlayState by app.overlayCoordinator.overlayState.collectAsState()

    // Keep selected macro synced with Floating Builder's active macro
    LaunchedEffect(overlayState.activeMacroId) {
        val overlayMacroId = overlayState.activeMacroId
        if (!overlayMacroId.isNullOrBlank() && overlayMacroId != config.selectedMacroId) {
            controller.setSelectedMacroId(overlayMacroId)
        }
    }

    val selectedMacro: MacroDefinition? = remember(macros, config.selectedMacroId, overlayState.activeMacroId) {
        macros.firstOrNull { it.id == config.selectedMacroId }
            ?: macros.firstOrNull { it.id == overlayState.activeMacroId }
            ?: macros.firstOrNull()
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showMacroPicker by remember { mutableStateOf(false) }
    var barCollapsed by remember { mutableStateOf(false) }
    var barOffsetX by remember { mutableFloatStateOf(0f) }
    var barOffsetY by remember { mutableFloatStateOf(0f) }

    BackHandler {
        when {
            showMacroPicker -> showMacroPicker = false
            showSettingsSheet -> showSettingsSheet = false
            else -> onBackPress()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ObsidianBg
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalW = maxWidth
            val totalH = maxHeight
            val isLandscape = totalW >= totalH
            val density = LocalDensity.current
            val totalWPx = with(density) { totalW.toPx() }
            val totalHPx = with(density) { totalH.toPx() }

            val supportsEmbeddedSurface = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            val acct2InExternalSplit = remoteStatus.hostMode == "SPLIT_ACTIVITY"

            val acct1Bounds: PaneBounds
            val acct2Bounds: PaneBounds
            val acct1Z: Float
            val acct2Z: Float
            val acct1Interactive: Boolean
            val acct2Interactive: Boolean

            if (!supportsEmbeddedSurface || acct2InExternalSplit) {
                acct1Bounds = PaneBounds(0.dp, 0.dp, totalW, totalH)
                acct2Bounds = PaneBounds(0.dp, 0.dp, 2.dp, 2.dp)
                acct1Z = 2f
                acct2Z = 0f
                acct1Interactive = true
                acct2Interactive = false
            } else {
                when (config.layoutMode) {
                    DualLayoutMode.SPLIT_50_50 -> {
                        if (isLandscape) {
                            val halfW = totalW / 2
                            acct2Bounds = PaneBounds(0.dp, 0.dp, halfW, totalH)
                            acct1Bounds = PaneBounds(halfW, 0.dp, totalW - halfW, totalH)
                        } else {
                            val halfH = totalH / 2
                            acct2Bounds = PaneBounds(0.dp, 0.dp, totalW, halfH)
                            acct1Bounds = PaneBounds(0.dp, halfH, totalW, totalH - halfH)
                        }
                        acct1Z = 1f
                        acct2Z = 1f
                        acct1Interactive = true
                        acct2Interactive = true
                    }
                    DualLayoutMode.SINGLE_ACCT_1 -> {
                        acct1Bounds = PaneBounds(0.dp, 0.dp, totalW, totalH)
                        acct2Bounds = PaneBounds(0.dp, 0.dp, 2.dp, 2.dp)
                        acct1Z = 2f
                        acct2Z = 0f
                        acct1Interactive = true
                        acct2Interactive = false
                    }
                    DualLayoutMode.SINGLE_ACCT_2 -> {
                        acct1Bounds = PaneBounds(0.dp, 0.dp, totalW, totalH)
                        acct2Bounds = PaneBounds(0.dp, 0.dp, totalW, totalH)
                        acct1Z = 0f
                        acct2Z = 2f
                        acct1Interactive = false
                        acct2Interactive = true
                    }
                }
            }

            // Keep SurfaceView 2 touch forwarding synced with layout mode
            surfaceBridge.allowUserTouchForwarding = acct2Interactive

            // ACCOUNT 1: Main-process persistent WebView (always View.VISIBLE; touch-gated when A2 Full)
            Box(
                modifier = Modifier
                    .offset(x = acct1Bounds.x, y = acct1Bounds.y)
                    .size(width = acct1Bounds.w, height = acct1Bounds.h)
                    .zIndex(acct1Z)
                    .border(
                        width = if (config.layoutMode == DualLayoutMode.SPLIT_50_50) 0.5.dp else 0.dp,
                        color = ElectricCyan.copy(alpha = 0.45f)
                    )
                    .testTag("webview_account1_pane")
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val wv = WebViewFactory.createGameWebView(
                            context = ctx,
                            desktopUserAgent = config.acct1DesktopUa
                        ).apply {
                            loadUrl(DualWebConstants.FLYFF_UNIVERSE_URL)
                        }
                        TouchGatedFrameLayout(ctx).apply {
                            allowUserTouch = acct1Interactive
                            addView(
                                wv,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            onWebView1Bound(this, wv)
                        }
                    },
                    update = { container ->
                        container.allowUserTouch = acct1Interactive
                        val child = container.getChildAt(0)
                        if (child != null && child.visibility != View.VISIBLE) {
                            child.visibility = View.VISIBLE
                        }
                    }
                )

                if (config.showAccountBadges && acct1Interactive) {
                    CompactAccountCornerBadge(
                        label = "A1",
                        subLabel = if (config.acct1DesktopUa) "DESK" else "MOB",
                        accent = ElectricCyan,
                        onToggleUa = { onToggleAcct1Ua(!config.acct1DesktopUa) },
                        onReload = onReloadAcct1,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    )
                }
            }

            // ACCOUNT 2: Isolated `:acct2` SurfaceControlViewHost SurfaceView (always View.VISIBLE; forwards user touches via oneway AIDL)
            Box(
                modifier = Modifier
                    .offset(x = acct2Bounds.x, y = acct2Bounds.y)
                    .size(width = acct2Bounds.w, height = acct2Bounds.h)
                    .zIndex(acct2Z)
                    .border(
                        width = if (config.layoutMode == DualLayoutMode.SPLIT_50_50) 0.5.dp else 0.dp,
                        color = EmeraldRun.copy(alpha = 0.55f)
                    )
                    .testTag("webview_account2_pane")
            ) {
                if (supportsEmbeddedSurface) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                visibility = View.VISIBLE
                                setZOrderOnTop(false)
                                surfaceBridge.bindSurfaceView(this)
                                onSurfaceView2Bound(this)
                            }
                        },
                        update = { sv ->
                            if (sv.visibility != View.VISIBLE) {
                                sv.visibility = View.VISIBLE
                            }
                        }
                    )
                }

                if (config.showAccountBadges && acct2Interactive && !acct2InExternalSplit && supportsEmbeddedSurface) {
                    CompactAccountCornerBadge(
                        label = "A2",
                        subLabel = if (config.acct2DesktopUa) ":acct2 DESK" else ":acct2 MOB",
                        accent = EmeraldRun,
                        onToggleUa = { controller.setAcct2DesktopUa(!config.acct2DesktopUa) },
                        onReload = { controller.reloadAccount2() },
                        onOpenImeLogin = { controller.launchAccount2Activity(adjacentSplitScreen = false) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    )
                }
            }

            if (!supportsEmbeddedSurface) {
                Surface(
                    color = SlateSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AmberLogic),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .zIndex(5f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Android <11: Open Account 2 in Split-Screen",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                        Button(
                            onClick = { controller.launchAccount2Activity(adjacentSplitScreen = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.Splitscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open A2", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Floating Selected Macro Start/Stop Bar
            val maxDragX = (totalWPx * 0.42f).coerceAtLeast(80f)
            val maxDragY = (totalHPx * 0.44f).coerceAtLeast(80f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp, start = 8.dp, end = 8.dp)
                    .offset {
                        IntOffset(
                            x = barOffsetX.coerceIn(-maxDragX, maxDragX).roundToInt(),
                            y = barOffsetY.coerceIn(-maxDragY * 1.8f, 16f).roundToInt()
                        )
                    }
                    .zIndex(10f)
            ) {
                FloatingSelectedMacroBar(
                    selectedMacro = selectedMacro,
                    executionState = executionState,
                    config = config,
                    remoteStatus = remoteStatus,
                    collapsed = barCollapsed,
                    onToggleCollapsed = { barCollapsed = !barCollapsed },
                    onDragBar = { dx, dy ->
                        barOffsetX = (barOffsetX + dx).coerceIn(-maxDragX, maxDragX)
                        barOffsetY = (barOffsetY + dy).coerceIn(-maxDragY * 1.8f, 16f)
                    },
                    onStartMacro = {
                        val target = selectedMacro
                        if (target != null) {
                            app.overlayCoordinator.setLoadedMacro(target.id, target.name)
                            controller.setSelectedMacroId(target.id)
                            app.interpreter.start(target)
                        } else {
                            showMacroPicker = true
                        }
                    },
                    onStopMacro = { app.interpreter.stop() },
                    onOpenMacroPicker = { showMacroPicker = true },
                    onOpenFloatingDock = { onOpenFloatingDock(selectedMacro) },
                    onCycleLayout = { controller.cycleLayoutMode() },
                    onCycleTarget = { controller.cycleMacroTarget() },
                    onOpenSettings = { showSettingsSheet = true }
                )
            }
        }
    }

    if (showMacroPicker) {
        SelectMacroDialog(
            macros = macros,
            selectedMacroId = selectedMacro?.id,
            onSelectMacro = { chosen ->
                controller.setSelectedMacroId(chosen.id)
                app.overlayCoordinator.setLoadedMacro(chosen.id, chosen.name)
                showMacroPicker = false
            },
            onOpenFloatingDock = { onOpenFloatingDock(selectedMacro) },
            onDismiss = { showMacroPicker = false }
        )
    }

    if (showSettingsSheet) {
        DualWebSettingsSheet(
            config = config,
            remoteStatus = remoteStatus,
            executionState = executionState,
            mainProcessName = controller.mainProcessName,
            mainProcessPid = controller.mainProcessPid,
            onDismiss = { showSettingsSheet = false },
            onSetLayoutMode = { controller.setLayoutMode(it) },
            onSetMacroTarget = { controller.setMacroTarget(it) },
            onSetDispatchMode = { controller.setDispatchMode(it) },
            onToggleAcct1Ua = onToggleAcct1Ua,
            onToggleAcct2Ua = { controller.setAcct2DesktopUa(it) },
            onReloadAcct1 = onReloadAcct1,
            onReloadAcct2 = { controller.reloadAccount2() },
            onClearAcct1Session = onClearAcct1Session,
            onClearAcct2Session = { controller.clearAccount2Session() },
            onOpenAcct2SplitActivity = { adjacent ->
                showSettingsSheet = false
                controller.launchAccount2Activity(adjacentSplitScreen = adjacent)
            },
            onOpenFloatingDock = { onOpenFloatingDock(selectedMacro) },
            onToggleBadges = { controller.setShowAccountBadges(it) },
            onExitDualClient = {
                showSettingsSheet = false
                onExit()
            }
        )
    }
}

private data class PaneBounds(
    val x: androidx.compose.ui.unit.Dp,
    val y: androidx.compose.ui.unit.Dp,
    val w: androidx.compose.ui.unit.Dp,
    val h: androidx.compose.ui.unit.Dp
)

@Composable
private fun CompactAccountCornerBadge(
    label: String,
    subLabel: String,
    accent: Color,
    onToggleUa: () -> Unit,
    onReload: () -> Unit,
    onOpenImeLogin: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateSurface.copy(alpha = 0.78f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(start = 8.dp, end = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$label • $subLabel",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleUa)
                    .testTag("badge_${label.lowercase()}_ua")
            ) {
                Icon(
                    imageVector = if (subLabel.contains("DESK")) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                    contentDescription = "Toggle Desktop or Mobile User-Agent for $label",
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onReload)
                    .testTag("badge_${label.lowercase()}_reload")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reload $label",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (onOpenImeLogin != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenImeLogin)
                        .testTag("badge_${label.lowercase()}_ime")
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Open Account 2 full IME login window",
                        tint = AmberLogic,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
