package com.example.dualweb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

object WebViewFactory {

    @SuppressLint("SetJavaScriptEnabled")
    fun createGameWebView(
        context: Context,
        desktopUserAgent: Boolean,
        onPageStateChanged: (url: String, title: String) -> Unit = { _, _ -> }
    ): WebView {
        val webView = WebView(context).apply {
            visibility = View.VISIBLE
            setBackgroundColor(Color.parseColor("#0B0E14"))
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFocusable = true
            isFocusableInTouchMode = true
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        configureSettings(context, webView, desktopUserAgent)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Keep all auth redirects and game navigations inside this isolated WebView.
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageStateChanged(url.orEmpty(), view?.title.orEmpty())
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                onPageStateChanged(view?.url.orEmpty(), title.orEmpty())
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                // Grant protected media / audio permissions if requested by the WebGL game.
                request?.grant(request.resources)
            }
        }

        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configureSettings(context: Context, webView: WebView, desktopUserAgent: Boolean) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        applyUserAgent(context, webView, desktopUserAgent)
    }

    fun applyUserAgent(context: Context, webView: WebView, desktopUserAgent: Boolean) {
        val ua = if (desktopUserAgent) {
            DualWebConstants.DESKTOP_USER_AGENT
        } else {
            WebSettings.getDefaultUserAgent(context)
        }
        webView.settings.userAgentString = ua
    }

    fun clearSessionData(webView: WebView) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView.clearCache(true)
        webView.clearHistory()
    }

    /**
     * Primary non-colliding key dispatch directly to a target WebView instance.
     * Produces genuine isTrusted: true Chromium events without touching InputManager,
     * AccessibilityService, ADB, or Account 1's active touch stream.
     */
    fun dispatchKeyNative(
        webView: WebView,
        keyCode: Int,
        holdMs: Long,
        metaState: Int,
        mainHandler: Handler = Handler(Looper.getMainLooper())
    ): Boolean {
        val clampedHold = holdMs.coerceIn(15L, 2000L)
        if (webView.visibility != View.VISIBLE) {
            webView.visibility = View.VISIBLE
        }
        // Local focus inside Account 2's own window/SurfaceControlViewHost does not steal
        // OS window focus or cancel touch gestures in Account 1's process.
        if (!webView.hasFocus()) {
            webView.requestFocus()
        }

        val downTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(
            downTime,
            downTime,
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            0,
            InputDevice.SOURCE_KEYBOARD
        )
        webView.dispatchKeyEvent(downEvent)

        mainHandler.postDelayed({
            val upEvent = KeyEvent(
                downTime,
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP,
                keyCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD
            )
            webView.dispatchKeyEvent(upEvent)
        }, clampedHold)
        return true
    }

    /**
     * Fallback JS DOM synthetic KeyboardEvent dispatch via evaluateJavascript.
     */
    fun dispatchKeyJs(
        webView: WebView,
        keyCode: Int,
        holdMs: Long,
        metaState: Int
    ): Boolean {
        val clampedHold = holdMs.coerceIn(15L, 2000L)
        val desc = DomKeyMapper.toDomDescriptor(keyCode)
        val keyJson = JSONObject.quote(desc.key)
        val codeJson = JSONObject.quote(desc.code)
        val shift = (metaState and KeyEvent.META_SHIFT_ON) != 0
        val ctrl = (metaState and KeyEvent.META_CTRL_ON) != 0
        val alt = (metaState and KeyEvent.META_ALT_ON) != 0

        val js = """
            (function() {
              try {
                var target = document.querySelector('canvas') || document.activeElement || document.body || document;
                var init = {
                  key: $keyJson,
                  code: $codeJson,
                  keyCode: ${desc.legacyKeyCode},
                  which: ${desc.legacyKeyCode},
                  shiftKey: $shift,
                  ctrlKey: $ctrl,
                  altKey: $alt,
                  bubbles: true,
                  cancelable: true,
                  composed: true
                };
                var downEv = new KeyboardEvent('keydown', init);
                target.dispatchEvent(downEv);
                setTimeout(function() {
                  var upEv = new KeyboardEvent('keyup', init);
                  target.dispatchEvent(upEv);
                }, $clampedHold);
              } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
        return true
    }

    /**
     * Primary non-colliding direct MotionEvent canvas tap on a target WebView instance.
     */
    fun dispatchCanvasTapNative(
        webView: WebView,
        normX: Float,
        normY: Float,
        holdMs: Long,
        mainHandler: Handler = Handler(Looper.getMainLooper())
    ): Boolean {
        val w = webView.width
        val h = webView.height
        if (w <= 0 || h <= 0) return false

        val px = normX.coerceIn(0f, 1f) * w.toFloat()
        val py = normY.coerceIn(0f, 1f) * h.toFloat()
        val clampedHold = holdMs.coerceIn(20L, 1000L)

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, px, py, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(down)
        down.recycle()

        mainHandler.postDelayed({
            val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, px, py, 0)
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            webView.dispatchTouchEvent(up)
            up.recycle()
        }, clampedHold)
        return true
    }

    /**
     * Fallback JS DOM synthetic pointer/mouse tap at normalized canvas coordinates.
     */
    fun dispatchCanvasTapJs(
        webView: WebView,
        normX: Float,
        normY: Float,
        holdMs: Long
    ): Boolean {
        val nx = normX.coerceIn(0f, 1f)
        val ny = normY.coerceIn(0f, 1f)
        val clampedHold = holdMs.coerceIn(20L, 1000L)
        val js = """
            (function() {
              try {
                var vw = window.innerWidth || document.documentElement.clientWidth || 1;
                var vh = window.innerHeight || document.documentElement.clientHeight || 1;
                var cx = Math.round(vw * $nx);
                var cy = Math.round(vh * $ny);
                var el = document.elementFromPoint(cx, cy) || document.querySelector('canvas') || document.body;
                if (!el) return;
                var opts = { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy, button: 0, buttons: 1 };
                if (typeof PointerEvent !== 'undefined') {
                  el.dispatchEvent(new PointerEvent('pointerdown', Object.assign({ pointerId: 1, pointerType: 'touch', isPrimary: true }, opts)));
                }
                el.dispatchEvent(new MouseEvent('mousedown', opts));
                setTimeout(function() {
                  var upOpts = { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy, button: 0, buttons: 0 };
                  if (typeof PointerEvent !== 'undefined') {
                    el.dispatchEvent(new PointerEvent('pointerup', Object.assign({ pointerId: 1, pointerType: 'touch', isPrimary: true }, upOpts)));
                  }
                  el.dispatchEvent(new MouseEvent('mouseup', upOpts));
                  el.dispatchEvent(new MouseEvent('click', upOpts));
                }, $clampedHold);
              } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
        return true
    }
}
