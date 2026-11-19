package com.example.dualweb

import android.app.Application
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.example.model.KeyboardKeyCatalog
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lives strictly inside the isolated `:acct2` process.
 * Owns the single live WebView 2 instance so it can move seamlessly between
 * SurfaceControlViewHost (unified single-screen dual mode) and Account2Activity
 * (native Android split-screen / full IME login mode) without reloading the WebGL game.
 */
object Account2WebViewManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dispatchCounter = AtomicInteger(0)

    private var contextWrapper: MutableContextWrapper? = null
    private var webView: WebView? = null
    private var desktopUserAgent: Boolean = true
    private var currentUrl: String = DualWebConstants.FLYFF_UNIVERSE_URL
    private var pageTitle: String = ""
    private var hostMode: String = "IDLE"
    private var lastDispatchSummary: String = "Ready"
    private var onReattachSurfaceRequested: (() -> Unit)? = null

    fun setSurfaceReattachCallback(callback: (() -> Unit)?) {
        onReattachSurfaceRequested = callback
    }

    fun getOrCreateWebView(hostContext: Context): WebView {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Account2WebViewManager.getOrCreateWebView must run on main thread"
        }
        val existing = webView
        if (existing != null) {
            contextWrapper?.baseContext = hostContext
            existing.visibility = View.VISIBLE
            return existing
        }

        val wrapper = MutableContextWrapper(hostContext)
        contextWrapper = wrapper
        val created = WebViewFactory.createGameWebView(
            context = wrapper,
            desktopUserAgent = desktopUserAgent,
            onPageStateChanged = { url, title ->
                if (url.isNotBlank()) currentUrl = url
                if (title.isNotBlank()) pageTitle = title
            }
        )
        created.loadUrl(DualWebConstants.FLYFF_UNIVERSE_URL)
        webView = created
        return created
    }

    fun attachToContainer(
        hostContext: Context,
        container: ViewGroup,
        modeLabel: String
    ): WebView {
        val wv = getOrCreateWebView(hostContext)
        detachFromCurrentParent(wv)
        wv.visibility = View.VISIBLE
        container.removeAllViews()
        container.addView(
            wv,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        hostMode = modeLabel
        // Never allow Chromium to throttle rAF or pause WebGL timers.
        wv.onResume()
        wv.resumeTimers()
        return wv
    }

    fun detachFromContainer(container: ViewGroup, notifySurfaceReattach: Boolean) {
        val wv = webView ?: return
        if (wv.parent === container) {
            container.removeView(wv)
            contextWrapper?.baseContext = container.context.applicationContext
            hostMode = "DETACHED"
            if (notifySurfaceReattach) {
                mainHandler.post { onReattachSurfaceRequested?.invoke() }
            }
        }
    }

    private fun detachFromCurrentParent(wv: WebView) {
        val parent = wv.parent
        if (parent is ViewGroup) {
            parent.removeView(wv)
        }
    }

    fun loadUrl(url: String) {
        mainHandler.post {
            val wv = webView ?: return@post
            val target = url.trim().ifBlank { DualWebConstants.FLYFF_UNIVERSE_URL }
            currentUrl = target
            wv.loadUrl(target)
        }
    }

    fun reload() {
        mainHandler.post {
            webView?.reload()
        }
    }

    fun goBack() {
        mainHandler.post {
            val wv = webView ?: return@post
            if (wv.canGoBack()) wv.goBack()
        }
    }

    fun setDesktopUserAgent(context: Context, desktop: Boolean) {
        mainHandler.post {
            val changed = desktopUserAgent != desktop
            desktopUserAgent = desktop
            val wv = webView ?: return@post
            WebViewFactory.applyUserAgent(context.applicationContext, wv, desktop)
            if (changed) {
                wv.reload()
            }
        }
    }

    fun isDesktopUserAgent(): Boolean = desktopUserAgent

    fun dispatchKey(
        keyCode: Int,
        holdMs: Long,
        metaState: Int,
        mode: InputDispatchMode
    ): Boolean {
        val wv = webView ?: return false
        val count = dispatchCounter.incrementAndGet()
        val keyLabel = KeyboardKeyCatalog.labelForKeyCode(keyCode)
        lastDispatchSummary = "${if (mode == InputDispatchMode.NATIVE_EVENT) "NATIVE" else "JS"} KEY $keyLabel -> A2 #$count"

        return when (mode) {
            InputDispatchMode.NATIVE_EVENT ->
                WebViewFactory.dispatchKeyNative(wv, keyCode, holdMs, metaState, mainHandler)
            InputDispatchMode.JS_DOM ->
                WebViewFactory.dispatchKeyJs(wv, keyCode, holdMs, metaState)
        }
    }

    fun dispatchRawMotionEvent(event: android.view.MotionEvent) {
        val copy = android.view.MotionEvent.obtain(event)
        mainHandler.post {
            try {
                val wv = webView ?: return@post
                if (wv.visibility != View.VISIBLE) {
                    wv.visibility = View.VISIBLE
                }
                wv.dispatchTouchEvent(copy)
            } finally {
                copy.recycle()
            }
        }
    }

    fun dispatchCanvasTap(
        normX: Float,
        normY: Float,
        holdMs: Long,
        mode: InputDispatchMode
    ): Boolean {
        val wv = webView ?: return false
        val count = dispatchCounter.incrementAndGet()
        val pctX = (normX.coerceIn(0f, 1f) * 100f).toInt()
        val pctY = (normY.coerceIn(0f, 1f) * 100f).toInt()
        lastDispatchSummary = "${if (mode == InputDispatchMode.NATIVE_EVENT) "NATIVE" else "JS"} TAP ($pctX%,$pctY%) -> A2 #$count"

        return when (mode) {
            InputDispatchMode.NATIVE_EVENT ->
                WebViewFactory.dispatchCanvasTapNative(wv, normX, normY, holdMs, mainHandler)
            InputDispatchMode.JS_DOM ->
                WebViewFactory.dispatchCanvasTapJs(wv, normX, normY, holdMs)
        }
    }

    fun dispatchCanvasSwipe(
        startNormX: Float,
        startNormY: Float,
        endNormX: Float,
        endNormY: Float,
        durationMs: Long,
        steps: Int,
        mode: InputDispatchMode
    ): Boolean {
        val wv = webView ?: return false
        val count = dispatchCounter.incrementAndGet()
        lastDispatchSummary = "${if (mode == InputDispatchMode.NATIVE_EVENT) "NATIVE" else "JS"} SWIPE -> A2 #$count"

        return when (mode) {
            InputDispatchMode.NATIVE_EVENT ->
                WebViewFactory.dispatchCanvasSwipeNative(
                    wv,
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
                    wv,
                    startNormX,
                    startNormY,
                    endNormX,
                    endNormY,
                    durationMs,
                    steps
                )
        }
    }

    fun clearSession() {
        mainHandler.post {
            val wv = webView ?: return@post
            WebViewFactory.clearSessionData(wv)
            wv.loadUrl(DualWebConstants.FLYFF_UNIVERSE_URL)
            lastDispatchSummary = "Cleared Account 2 cookies/storage"
        }
    }

    fun buildStatusBundle(): Bundle = Bundle().apply {
        putString(DualWebConstants.STATUS_PROCESS_NAME, resolveCurrentProcessName())
        putInt(DualWebConstants.STATUS_PID, Process.myPid())
        putString(DualWebConstants.STATUS_DATA_DIR_SUFFIX, DualWebConstants.ACCT2_DATA_DIR_SUFFIX)
        putString(DualWebConstants.STATUS_CURRENT_URL, webView?.url ?: currentUrl)
        putString(DualWebConstants.STATUS_PAGE_TITLE, webView?.title ?: pageTitle)
        putBoolean(DualWebConstants.STATUS_DESKTOP_UA, desktopUserAgent)
        putString(DualWebConstants.STATUS_HOST_MODE, hostMode)
        putInt(DualWebConstants.STATUS_DISPATCH_COUNT, dispatchCounter.get())
        putString(DualWebConstants.STATUS_LAST_DISPATCH, lastDispatchSummary)
        putBoolean(DualWebConstants.STATUS_WEBVIEW_VISIBLE, webView?.visibility == View.VISIBLE)
    }

    fun resolveCurrentProcessName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        return runCatching {
            File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n', '\r')
        }.getOrDefault("unknown")
    }
}
