package com.example.dualweb

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ShizuMacroTheme
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Immersive Fullscreen Dual-Account WebView Workspace for Flyff Universe.
 *
 * - Account 1 (`WebView 1`) runs in the main process (default WebView data directory).
 * - Account 2 (`WebView 2`) runs in the isolated `:acct2` process (`WebView.setDataDirectorySuffix("acct2")`)
 *   and is rendered hardware-accelerated into this screen via Android 11-15 `SurfaceControlViewHost`,
 *   with seamless 1-tap handoff to `Account2Activity` for Android Split-Screen (`FLAG_ACTIVITY_LAUNCH_ADJACENT`)
 *   or full IME/OAuth login without reloading.
 * - Both WebViews stay `View.VISIBLE` at all times so Chromium never throttles `requestAnimationFrame`
 *   or pauses WebGL rendering.
 */
class DualWebActivity : ComponentActivity() {

    private lateinit var controller: Account2ClientController
    private var account1WebView: WebView? = null
    private var account2SurfaceBridge: Account2SurfaceBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureFullscreenWindow()

        controller = Account2ClientController(this)
        val startAdjacentSplit = intent.getBooleanExtra(EXTRA_START_ADJACENT_SPLIT, false)

        val bridge = Account2SurfaceBridge(controller)
        account2SurfaceBridge = bridge
        controller.bind {
            bridge.tryAttachOrResize()
            if (startAdjacentSplit) {
                controller.launchAccount2Activity(adjacentSplitScreen = true)
            }
        }

        setContent {
            ShizuMacroTheme {
                DualWebFullscreenWorkspace(
                    controller = controller,
                    surfaceBridge = bridge,
                    onWebView1Created = { wv -> account1WebView = wv },
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
                    onBackPress = {
                        val wv1 = account1WebView
                        if (wv1 != null && wv1.canGoBack()) {
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
        account2SurfaceBridge?.release()
        account2SurfaceBridge = null
        controller.unbindAndRelease()
        account1WebView?.let { wv ->
            wv.stopLoading()
            wv.destroy()
        }
        account1WebView = null
        super.onDestroy()
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

private class Account2SurfaceBridge(
    private val controller: Account2ClientController
) : SurfaceHolder.Callback {

    private var surfaceView: SurfaceView? = null
    private var currentWidth: Int = 0
    private var currentHeight: Int = 0
    private var isSurfaceReady: Boolean = false
    private var isAttached: Boolean = false

    fun bindSurfaceView(view: SurfaceView) {
        surfaceView = view
        view.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceReady = true
        isAttached = false
        surfaceView?.post { tryAttachOrResize() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        currentWidth = width
        currentHeight = height
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

        if (isAttached) {
            controller.resizeSurface(currentWidth, currentHeight)
            return
        }

        val displayId = sv.display?.displayId ?: Display.DEFAULT_DISPLAY
        val surfacePackage = controller.attachSurface(token, displayId, currentWidth, currentHeight)
        if (surfacePackage != null) {
            sv.setChildSurfacePackage(surfacePackage)
            isAttached = true
        }
    }

    fun release() {
        surfaceView?.holder?.removeCallback(this)
        surfaceView = null
        isSurfaceReady = false
        isAttached = false
    }
}

@Composable
private fun DualWebFullscreenWorkspace(
    controller: Account2ClientController,
    surfaceBridge: Account2SurfaceBridge,
    onWebView1Created: (WebView) -> Unit,
    onReloadAcct1: () -> Unit,
    onClearAcct1Session: () -> Unit,
    onToggleAcct1Ua: (Boolean) -> Unit,
    onBackPress: () -> Unit,
    onExit: () -> Unit
) {
    val config by controller.config.collectAsState()
    val remoteStatus by controller.remoteStatus.collectAsState()
    val activeLoopIds by controller.activeLoopIds.collectAsState()
    val lastFiredButtonId by controller.lastFiredButtonId.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var barCollapsed by remember { mutableStateOf(false) }
    var barOffsetX by remember { mutableFloatStateOf(0f) }
    var barOffsetY by remember { mutableFloatStateOf(0f) }

    BackHandler {
        if (showSettingsSheet) {
            showSettingsSheet = false
        } else {
            onBackPress()
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

            // Compute non-destructive geometry for BOTH persistent WebViews.
            // Both stay View.VISIBLE at all times so Chromium never throttles rAF or WebGL.
            val pipW = if (isLandscape) 186.dp else 156.dp
            val pipH = if (isLandscape) 114.dp else 104.dp
            val pipMargin = 10.dp

            val acct1Bounds: PaneBounds
            val acct2Bounds: PaneBounds
            val acct1Z: Float
            val acct2Z: Float

            if (!supportsEmbeddedSurface || acct2InExternalSplit) {
                // When Account 2 is open in its own Android Split-Screen Activity, Account 1 takes 100% fullscreen here.
                acct1Bounds = PaneBounds(0.dp, 0.dp, totalW, totalH)
                acct2Bounds = PaneBounds(totalW - 2.dp, totalH - 2.dp, 2.dp, 2.dp)
                acct1Z = 1f
                acct2Z = 0f
            } else {
                when (config.layoutMode) {
                    DualLayoutMode.SPLIT_50_50 -> {
                        if (isLandscape) {
                            val halfW = totalW / 2
                            acct1Bounds = PaneBounds(0.dp, 0.dp, halfW, totalH)
                            acct2Bounds = PaneBounds(halfW, 0.dp, totalW - halfW, totalH)
                        } else {
                            val halfH = totalH / 2
                            acct1Bounds = PaneBounds(0.dp, 0.dp, totalW, halfH)
                            acct2Bounds = PaneBounds(0.dp, halfH, totalW, totalH - halfH)
                        }
                        acct1Z = 0f
                        acct2Z = 0f
                    }
                    DualLayoutMode.FOCUS_70_30 -> {
                        if (isLandscape) {
                            val a1W = totalW * 0.68f
                            acct1Bounds = PaneBounds(0.dp, 0.dp, a1W, totalH)
                            acct2Bounds = PaneBounds(a1W, 0.dp, totalW - a1W, totalH)
                        } else {
                            val a1H = totalH * 0.68f
                            acct1Bounds = PaneBounds(0.dp, 0.dp, totalW, a1H)
                            acct2Bounds = PaneBounds(0.dp, a1H, totalW, totalH - a1H)
                        }
                        acct1Z = 0f
                        acct2Z = 0f
                    }
                    DualLayoutMode.ACCT1_FULL_PIP -> {
                        acct1Bounds = PaneBounds(0.dp, 0.dp, totalW, totalH)
                        acct2Bounds = PaneBounds(
                            x = (totalW - pipW - pipMargin).coerceAtLeast(0.dp),
                            y = pipMargin,
                            w = pipW,
                            h = pipH
                        )
                        acct1Z = 0f
                        acct2Z = 2f
                    }
                    DualLayoutMode.ACCT2_FULL_PIP -> {
                        acct2Bounds = PaneBounds(0.dp, 0.dp, totalW, totalH)
                        acct1Bounds = PaneBounds(
                            x = (totalW - pipW - pipMargin).coerceAtLeast(0.dp),
                            y = pipMargin,
                            w = pipW,
                            h = pipH
                        )
                        acct2Z = 0f
                        acct1Z = 2f
                    }
                }
            }

            // ACCOUNT 1: Main-process persistent WebView (default cookie/storage dir, always View.VISIBLE)
            Box(
                modifier = Modifier
                    .offset(x = acct1Bounds.x, y = acct1Bounds.y)
                    .size(width = acct1Bounds.w, height = acct1Bounds.h)
                    .zIndex(acct1Z)
                    .border(
                        width = if (config.layoutMode == DualLayoutMode.ACCT2_FULL_PIP) 1.5.dp else 0.5.dp,
                        color = ElectricCyan.copy(alpha = 0.45f)
                    )
                    .testTag("webview_account1_pane")
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebViewFactory.createGameWebView(
                            context = ctx,
                            desktopUserAgent = config.acct1DesktopUa
                        ).also { wv ->
                            wv.loadUrl(DualWebConstants.FLYFF_UNIVERSE_URL)
                            onWebView1Created(wv)
                        }
                    },
                    update = { wv ->
                        if (wv.visibility != View.VISIBLE) {
                            wv.visibility = View.VISIBLE
                        }
                    }
                )

                if (config.showAccountBadges) {
                    CompactAccountCornerBadge(
                        label = "A1",
                        subLabel = if (config.acct1DesktopUa) "DESK" else "MOB",
                        accent = ElectricCyan,
                        isPiP = config.layoutMode == DualLayoutMode.ACCT2_FULL_PIP,
                        onSwapFocus = { controller.setLayoutMode(DualLayoutMode.ACCT1_FULL_PIP) },
                        onToggleUa = { onToggleAcct1Ua(!config.acct1DesktopUa) },
                        onReload = onReloadAcct1,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    )
                }
            }

            // ACCOUNT 2: Isolated `:acct2` process SurfaceControlViewHost SurfaceView (always View.VISIBLE)
            Box(
                modifier = Modifier
                    .offset(x = acct2Bounds.x, y = acct2Bounds.y)
                    .size(width = acct2Bounds.w, height = acct2Bounds.h)
                    .zIndex(acct2Z)
                    .border(
                        width = if (config.layoutMode == DualLayoutMode.ACCT1_FULL_PIP) 1.5.dp else 0.5.dp,
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
                                // Punch through behind Compose overlays so the floating Macro Bar renders cleanly on top.
                                setZOrderOnTop(false)
                                surfaceBridge.bindSurfaceView(this)
                            }
                        },
                        update = { sv ->
                            if (sv.visibility != View.VISIBLE) {
                                sv.visibility = View.VISIBLE
                            }
                        }
                    )
                }

                if (config.showAccountBadges && !acct2InExternalSplit && supportsEmbeddedSurface) {
                    CompactAccountCornerBadge(
                        label = "A2",
                        subLabel = if (config.acct2DesktopUa) ":acct2 DESK" else ":acct2 MOB",
                        accent = EmeraldRun,
                        isPiP = config.layoutMode == DualLayoutMode.ACCT1_FULL_PIP,
                        onSwapFocus = { controller.setLayoutMode(DualLayoutMode.ACCT2_FULL_PIP) },
                        onToggleUa = { controller.setAcct2DesktopUa(!config.acct2DesktopUa) },
                        onReload = { controller.reloadAccount2() },
                        onOpenImeLogin = { controller.launchAccount2Activity(adjacentSplitScreen = false) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    )
                }
            }

            // Fallback banner if Android < 11 (SurfaceControlViewHost unavailable)
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
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Splitscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open A2", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Floating / Draggable Non-Colliding Macro Bar targeting Account 2
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
                FloatingAccount2MacroBar(
                    config = config,
                    remoteStatus = remoteStatus,
                    activeLoopIds = activeLoopIds,
                    lastFiredButtonId = lastFiredButtonId,
                    collapsed = barCollapsed,
                    onToggleCollapsed = { barCollapsed = !barCollapsed },
                    onDragBar = { dx, dy ->
                        barOffsetX = (barOffsetX + dx).coerceIn(-maxDragX, maxDragX)
                        barOffsetY = (barOffsetY + dy).coerceIn(-maxDragY * 1.8f, 16f)
                    },
                    onFireButton = { btn -> controller.fireButtonOnce(btn) },
                    onToggleLoop = { id -> controller.toggleButtonLoop(id) },
                    onStopAllLoops = { controller.stopAllLoops() },
                    onCycleLayout = { controller.cycleLayoutMode() },
                    onOpenSettings = { showSettingsSheet = true }
                )
            }
        }
    }

    if (showSettingsSheet) {
        DualWebSettingsSheet(
            config = config,
            remoteStatus = remoteStatus,
            activeLoopIds = activeLoopIds,
            mainProcessName = controller.mainProcessName,
            mainProcessPid = controller.mainProcessPid,
            onDismiss = { showSettingsSheet = false },
            onSetLayoutMode = { controller.setLayoutMode(it) },
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
            onToggleBadges = { controller.setShowAccountBadges(it) },
            onToggleLoop = { controller.toggleButtonLoop(it) },
            onStopAllLoops = { controller.stopAllLoops() },
            onUpsertButton = { controller.upsertMacroButton(it) },
            onDeleteButton = { controller.deleteMacroButton(it) },
            onResetDefaultButtons = { controller.resetMacroButtonsToDefault() },
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
    isPiP: Boolean,
    onSwapFocus: () -> Unit,
    onToggleUa: () -> Unit,
    onReload: () -> Unit,
    onOpenImeLogin: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateSurface.copy(alpha = 0.76f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = if (isPiP) label else "$label • $subLabel",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            if (isPiP) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onSwapFocus)
                        .testTag("badge_${label.lowercase()}_swap")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Expand $label to fullscreen",
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
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
}
