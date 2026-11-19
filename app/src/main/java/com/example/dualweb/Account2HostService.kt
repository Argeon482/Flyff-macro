package com.example.dualweb

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import android.view.SurfaceControlViewHost
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask

/**
 * Runs in `android:process=":acct2"` with isolated WebView data directory suffix `"acct2"`.
 * Embeds Account 2's WebView into a cross-process SurfaceControlViewHost (Android 11-15)
 * and executes non-colliding key/tap dispatches directly on WebView 2.
 */
class Account2HostService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var surfaceHost: SurfaceControlViewHost? = null
    private var hostContainer: FrameLayout? = null
    private var hostDisplayContext: Context? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    override fun onCreate() {
        super.onCreate()
        Account2WebViewManager.setSurfaceReattachCallback {
            reattachWebViewToSurfaceIfPossible()
        }
    }

    override fun onDestroy() {
        Account2WebViewManager.setSurfaceReattachCallback(null)
        runOnMainSync {
            releaseSurfaceHostLocked()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : IAccount2WebViewService.Stub() {
        override fun attachSurface(
            hostToken: IBinder?,
            displayId: Int,
            width: Int,
            height: Int
        ): Bundle {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return Bundle().apply {
                    putBoolean(DualWebConstants.KEY_ATTACHED, false)
                    putString(
                        DualWebConstants.KEY_ERROR,
                        "SurfaceControlViewHost requires Android 11+ (use Native Split-Screen mode)."
                    )
                }
            }
            return runOnMainSync {
                attachSurfaceOnMain(hostToken, displayId, width, height)
            }
        }

        override fun resizeSurface(width: Int, height: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            mainHandler.post {
                val w = width.coerceAtLeast(1)
                val h = height.coerceAtLeast(1)
                lastWidth = w
                lastHeight = h
                surfaceHost?.relayout(w, h)
                hostContainer?.let { container ->
                    container.visibility = View.VISIBLE
                    container.requestLayout()
                }
            }
        }

        override fun detachSurface() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            mainHandler.post {
                releaseSurfaceHostLocked()
            }
        }

        override fun loadUrl(url: String?) {
            Account2WebViewManager.loadUrl(url ?: DualWebConstants.FLYFF_UNIVERSE_URL)
        }

        override fun reload() {
            Account2WebViewManager.reload()
        }

        override fun goBack() {
            Account2WebViewManager.goBack()
        }

        override fun setDesktopUserAgent(desktop: Boolean) {
            Account2WebViewManager.setDesktopUserAgent(applicationContext, desktop)
        }

        override fun dispatchKey(
            keyCode: Int,
            holdMs: Long,
            metaState: Int,
            dispatchMode: Int
        ): Boolean {
            val mode = InputDispatchMode.fromWire(dispatchMode)
            return runOnMainSync {
                Account2WebViewManager.dispatchKey(keyCode, holdMs, metaState, mode)
            }
        }

        override fun dispatchCanvasTap(
            normX: Float,
            normY: Float,
            holdMs: Long,
            dispatchMode: Int
        ): Boolean {
            val mode = InputDispatchMode.fromWire(dispatchMode)
            return runOnMainSync {
                Account2WebViewManager.dispatchCanvasTap(normX, normY, holdMs, mode)
            }
        }

        override fun clearAccount2Session() {
            Account2WebViewManager.clearSession()
        }

        override fun getStatus(): Bundle = runOnMainSync {
            Account2WebViewManager.buildStatusBundle()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun attachSurfaceOnMain(
        hostToken: IBinder?,
        displayId: Int,
        width: Int,
        height: Int
    ): Bundle {
        val result = Bundle()
        try {
            releaseSurfaceHostLocked()

            val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(displayId)
                ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                ?: run {
                    result.putBoolean(DualWebConstants.KEY_ATTACHED, false)
                    result.putString(DualWebConstants.KEY_ERROR, "Display $displayId unavailable")
                    return result
                }

            val displayContext = createDisplayContext(display)
            val safeW = width.coerceAtLeast(1)
            val safeH = height.coerceAtLeast(1)
            lastWidth = safeW
            lastHeight = safeH
            hostDisplayContext = displayContext

            val container = FrameLayout(displayContext).apply {
                visibility = View.VISIBLE
            }
            Account2WebViewManager.attachToContainer(
                hostContext = displayContext,
                container = container,
                modeLabel = "SURFACE_HOST"
            )

            @Suppress("DEPRECATION")
            val host = SurfaceControlViewHost(displayContext, display, hostToken)
            host.setView(container, safeW, safeH)

            surfaceHost = host
            hostContainer = container

            result.putBoolean(DualWebConstants.KEY_ATTACHED, true)
            result.putParcelable(DualWebConstants.KEY_SURFACE_PACKAGE, host.surfacePackage)
        } catch (t: Throwable) {
            result.putBoolean(DualWebConstants.KEY_ATTACHED, false)
            result.putString(DualWebConstants.KEY_ERROR, t.message ?: t.javaClass.simpleName)
        }
        return result
    }

    private fun reattachWebViewToSurfaceIfPossible() {
        val container = hostContainer ?: return
        val displayCtx = hostDisplayContext ?: applicationContext
        Account2WebViewManager.attachToContainer(
            hostContext = displayCtx,
            container = container,
            modeLabel = "SURFACE_HOST"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lastWidth > 0 && lastHeight > 0) {
            surfaceHost?.relayout(lastWidth, lastHeight)
        }
    }

    private fun releaseSurfaceHostLocked() {
        hostContainer?.let { container ->
            Account2WebViewManager.detachFromContainer(container, notifySurfaceReattach = false)
        }
        hostContainer = null
        hostDisplayContext = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { surfaceHost?.release() }
        }
        surfaceHost = null
    }

    private fun <T> runOnMainSync(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }
        val future = FutureTask(Callable { block() })
        mainHandler.post(future)
        return future.get()
    }
}
