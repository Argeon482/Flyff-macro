package com.example.dualweb

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ShizuMacroTheme
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary

/**
 * Isolated Account 2 Activity running in `android:process=":acct2"`.
 * Serves two roles without reloading Account 2's live WebView session:
 * 1) Full native Android Split-Screen (`FLAG_ACTIVITY_LAUNCH_ADJACENT`) fullscreen window.
 * 2) Full IME / OAuth login helper when entering credentials on Account 2.
 */
class Account2Activity : ComponentActivity() {

    private var webContainer: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureFullscreenWindow()

        setContent {
            ShizuMacroTheme {
                Account2FullscreenScreen(
                    onBindContainer = { container ->
                        webContainer = container
                        Account2WebViewManager.attachToContainer(
                            hostContext = this@Account2Activity,
                            container = container,
                            modeLabel = "SPLIT_ACTIVITY"
                        )
                    },
                    onToggleUa = { desktop ->
                        Account2WebViewManager.setDesktopUserAgent(this@Account2Activity, desktop)
                    },
                    onForceKeyboard = { Account2WebViewManager.requestSoftKeyboard() },
                    onReload = { Account2WebViewManager.reload() },
                    onReturnToUnified = { finish() }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBarsIfImeHidden()
        }
    }

    override fun onDestroy() {
        webContainer?.let { container ->
            // Return WebView 2 back to SurfaceControlViewHost seamlessly without reload.
            Account2WebViewManager.detachFromContainer(container, notifySurfaceReattach = true)
        }
        webContainer = null
        super.onDestroy()
    }

    private fun configureFullscreenWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBarsIfImeHidden()
    }

    private fun hideSystemBarsIfImeHidden() {
        val imeVisible = ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        if (imeVisible) return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

private class FocusReclaimingFrameLayout(context: Context) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.actionMasked == MotionEvent.ACTION_DOWN) {
            val child = getChildAt(0)
            if (child != null) {
                if (!child.isFocusable) child.isFocusable = true
                if (!child.isFocusableInTouchMode) child.isFocusableInTouchMode = true
                if (!child.hasFocus()) {
                    child.requestFocusFromTouch()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

@Composable
private fun Account2FullscreenScreen(
    onBindContainer: (FrameLayout) -> Unit,
    onToggleUa: (Boolean) -> Unit,
    onForceKeyboard: () -> Unit,
    onReload: () -> Unit,
    onReturnToUnified: () -> Unit
) {
    var desktopUa by remember { mutableStateOf(Account2WebViewManager.isDesktopUserAgent()) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ObsidianBg
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FocusReclaimingFrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        onBindContainer(this)
                    }
                }
            )

            // Minimal translucent corner HUD so WebView 2 stays 100% fullscreen
            Surface(
                color = SlateSurface.copy(alpha = 0.84f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AmberLogic.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp)
                ) {
                    Text(
                        text = "A2 :acct2 • ${if (desktopUa) "DESK" else "MOB"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldRun
                    )
                    IconButton(
                        onClick = onForceKeyboard,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("acct2_activity_ime_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Show phone keyboard for Account 2",
                            tint = EmeraldRun,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            desktopUa = !desktopUa
                            onToggleUa(desktopUa)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("acct2_activity_ua_toggle")
                    ) {
                        Icon(
                            imageVector = if (desktopUa) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                            contentDescription = "Toggle Account 2 User-Agent",
                            tint = AmberLogic,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onReload,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("acct2_activity_reload")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload Account 2",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onReturnToUnified,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("acct2_activity_return")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Return Account 2 to unified screen",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
