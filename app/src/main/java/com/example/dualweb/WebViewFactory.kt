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
import android.view.inputmethod.InputMethodManager
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
            ): Boolean = false

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
                target.dispatchEvent(new KeyboardEvent('keydown', init));
                setTimeout(function() {
                  target.dispatchEvent(new KeyboardEvent('keyup', init));
                }, $clampedHold);
              } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
        return true
    }

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
        val clampedHold = holdMs.coerceIn(20L, 1500L)

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

    fun dispatchCanvasTapJs(
        webView: WebView,
        normX: Float,
        normY: Float,
        holdMs: Long
    ): Boolean {
        val nx = normX.coerceIn(0f, 1f)
        val ny = normY.coerceIn(0f, 1f)
        val clampedHold = holdMs.coerceIn(20L, 1500L)
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

    fun dispatchCanvasSwipeNative(
        webView: WebView,
        startNormX: Float,
        startNormY: Float,
        endNormX: Float,
        endNormY: Float,
        durationMs: Long,
        steps: Int,
        mainHandler: Handler = Handler(Looper.getMainLooper())
    ): Boolean {
        val w = webView.width
        val h = webView.height
        if (w <= 0 || h <= 0) return false

        val sx = startNormX.coerceIn(0f, 1f) * w.toFloat()
        val sy = startNormY.coerceIn(0f, 1f) * h.toFloat()
        val ex = endNormX.coerceIn(0f, 1f) * w.toFloat()
        val ey = endNormY.coerceIn(0f, 1f) * h.toFloat()
        val clampedDuration = durationMs.coerceIn(40L, 4000L)
        val clampedSteps = steps.coerceIn(2, 48)

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, sx, sy, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(down)
        down.recycle()

        for (i in 1..clampedSteps) {
            val frac = i.toFloat() / clampedSteps.toFloat()
            val delayMs = (clampedDuration * frac).toLong()
            val cx = sx + (ex - sx) * frac
            val cy = sy + (ey - sy) * frac
            mainHandler.postDelayed({
                val move = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, cx, cy, 0)
                move.source = InputDevice.SOURCE_TOUCHSCREEN
                webView.dispatchTouchEvent(move)
                move.recycle()
            }, delayMs)
        }

        mainHandler.postDelayed({
            val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, ex, ey, 0)
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            webView.dispatchTouchEvent(up)
            up.recycle()
        }, clampedDuration + 10L)
        return true
    }

    fun dispatchCanvasSwipeJs(
        webView: WebView,
        startNormX: Float,
        startNormY: Float,
        endNormX: Float,
        endNormY: Float,
        durationMs: Long,
        steps: Int
    ): Boolean {
        val sx = startNormX.coerceIn(0f, 1f)
        val sy = startNormY.coerceIn(0f, 1f)
        val ex = endNormX.coerceIn(0f, 1f)
        val ey = endNormY.coerceIn(0f, 1f)
        val clampedDuration = durationMs.coerceIn(40L, 4000L)
        val clampedSteps = steps.coerceIn(2, 30)
        val js = """
            (function() {
              try {
                var vw = window.innerWidth || document.documentElement.clientWidth || 1;
                var vh = window.innerHeight || document.documentElement.clientHeight || 1;
                var x0 = vw * $sx, y0 = vh * $sy;
                var x1 = vw * $ex, y1 = vh * $ey;
                var el = document.elementFromPoint(Math.round(x0), Math.round(y0)) || document.querySelector('canvas') || document.body;
                if (!el) return;
                function fire(type, x, y, btns) {
                  var opts = { bubbles: true, cancelable: true, view: window, clientX: Math.round(x), clientY: Math.round(y), button: 0, buttons: btns };
                  if (typeof PointerEvent !== 'undefined') {
                    var pType = type === 'mousedown' ? 'pointerdown' : (type === 'mousemove' ? 'pointermove' : 'pointerup');
                    el.dispatchEvent(new PointerEvent(pType, Object.assign({ pointerId: 1, pointerType: 'touch', isPrimary: true }, opts)));
                  }
                  el.dispatchEvent(new MouseEvent(type, opts));
                }
                fire('mousedown', x0, y0, 1);
                var n = $clampedSteps;
                var dur = $clampedDuration;
                for (var i = 1; i <= n; i++) {
                  (function(idx) {
                    setTimeout(function() {
                      var f = idx / n;
                      fire('mousemove', x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, 1);
                      if (idx === n) fire('mouseup', x1, y1, 0);
                    }, Math.round(dur * (idx / n)));
                  })(i);
                }
              } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
        return true
    }

    /**
     * Streams a string of characters (and optional Enter key) directly to the WebView via
     * native `KeyEvent`s (`isTrusted: true`). Characters not mapped by `VIRTUAL_KEYBOARD`
     * fall back cleanly to JS text insertion so Unicode/symbols never silently drop.
     */
    fun dispatchTextNative(
        webView: WebView,
        text: String,
        pressEnter: Boolean,
        mainHandler: Handler = Handler(Looper.getMainLooper())
    ): Boolean {
        if (text.isEmpty() && !pressEnter) return false
        if (webView.visibility != View.VISIBLE) {
            webView.visibility = View.VISIBLE
        }
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        if (!webView.hasFocus()) {
            webView.requestFocus()
        }

        val kcm = runCatching { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }.getOrNull()
        var delayMs = 0L
        val stepDelayMs = 10L

        text.forEach { ch ->
            val mappedEvents = runCatching { kcm?.getEvents(charArrayOf(ch)) }.getOrNull()
            if (mappedEvents != null && mappedEvents.isNotEmpty()) {
                val scheduledDelay = delayMs
                mainHandler.postDelayed({
                    val baseTime = SystemClock.uptimeMillis()
                    mappedEvents.forEachIndexed { idx, raw ->
                        val timed = KeyEvent(
                            baseTime,
                            baseTime + idx,
                            raw.action,
                            raw.keyCode,
                            raw.repeatCount,
                            raw.metaState,
                            KeyCharacterMap.VIRTUAL_KEYBOARD,
                            raw.scanCode,
                            raw.flags,
                            InputDevice.SOURCE_KEYBOARD
                        )
                        webView.dispatchKeyEvent(timed)
                    }
                }, scheduledDelay)
            } else {
                val fallbackChar = ch.toString()
                val scheduledDelay = delayMs
                mainHandler.postDelayed({
                    dispatchTextJs(webView, fallbackChar, pressEnter = false)
                }, scheduledDelay)
            }
            delayMs += stepDelayMs
        }

        if (pressEnter) {
            val enterDelay = delayMs + 12L
            mainHandler.postDelayed({
                dispatchKeyNative(
                    webView = webView,
                    keyCode = KeyEvent.KEYCODE_ENTER,
                    holdMs = 35L,
                    metaState = 0,
                    mainHandler = mainHandler
                )
            }, enterDelay)
        }
        return true
    }

    /**
     * JS DOM text-input fallback: inserts text into any focused HTML input/textarea/contenteditable
     * AND fires synthetic KeyboardEvents for canvas-driven chat/login UIs (e.g. Flyff Universe).
     */
    fun dispatchTextJs(
        webView: WebView,
        text: String,
        pressEnter: Boolean
    ): Boolean {
        if (text.isEmpty() && !pressEnter) return false
        val js = WebViewTextInputHelper.buildJsTextInjectionScript(text, pressEnter)
        webView.evaluateJavascript(js, null)
        return true
    }

    /**
     * Explicitly reclaims native Android View focus for the WebView and asks InputMethodManager
     * to open the soft keyboard, nudging any active editable DOM element at the same time.
     */
    fun forceShowSoftKeyboard(
        webView: WebView,
        mainHandler: Handler = Handler(Looper.getMainLooper())
    ) {
        webView.visibility = View.VISIBLE
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        webView.requestFocusFromTouch()

        val focusJs = """
            (function() {
              try {
                var el = document.activeElement;
                if (el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable)) {
                  el.focus();
                } else {
                  var candidate = document.querySelector('input:not([type="hidden"]):not([disabled]), textarea:not([disabled]), [contenteditable="true"]');
                  if (candidate) candidate.focus();
                }
              } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(focusJs, null)

        mainHandler.postDelayed({
            if (!webView.hasFocus()) {
                webView.requestFocusFromTouch()
            }
            val imm = webView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
        }, 60L)
    }
}

object WebViewTextInputHelper {

    /**
     * Chooses the initial Quick Type target account (1 or 2) based on the active layout mode
     * and whichever WebView pane the user touched most recently.
     */
    fun resolveQuickTypeInitialAccount(
        layoutMode: DualLayoutMode,
        lastTouchedAccount: Int
    ): Int = when (layoutMode) {
        DualLayoutMode.SINGLE_ACCT_1 -> 1
        DualLayoutMode.SINGLE_ACCT_2 -> 2
        DualLayoutMode.SPLIT_50_50 -> if (lastTouchedAccount == 2) 2 else 1
    }

    /**
     * Generates a self-contained JS snippet that inserts text into focused DOM inputs or
     * dispatches per-character KeyboardEvents to WebGL canvases, followed by Enter if requested.
     */
    fun buildJsTextInjectionScript(text: String, pressEnter: Boolean): String {
        val quotedText = JSONObject.quote(text)
        return """
            (function() {
              try {
                var str = $quotedText;
                var el = document.activeElement;
                var isEditable = el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable);
                if (isEditable && str.length > 0) {
                  if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
                    var start = typeof el.selectionStart === 'number' ? el.selectionStart : el.value.length;
                    var end = typeof el.selectionEnd === 'number' ? el.selectionEnd : el.value.length;
                    var nextVal = el.value.slice(0, start) + str + el.value.slice(end);
                    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
                    if (desc && desc.set) {
                      desc.set.call(el, nextVal);
                    } else {
                      el.value = nextVal;
                    }
                    if (typeof el.setSelectionRange === 'function') {
                      var caret = start + str.length;
                      el.setSelectionRange(caret, caret);
                    }
                    el.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, data: str, inputType: 'insertText' }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                  } else {
                    document.execCommand('insertText', false, str);
                  }
                } else if (str.length > 0) {
                  var target = document.querySelector('canvas') || el || document.body || document;
                  for (var i = 0; i < str.length; i++) {
                    var ch = str.charAt(i);
                    var codePoint = ch.charCodeAt(0);
                    var init = { key: ch, keyCode: codePoint, which: codePoint, charCode: codePoint, bubbles: true, cancelable: true, composed: true };
                    target.dispatchEvent(new KeyboardEvent('keydown', init));
                    target.dispatchEvent(new KeyboardEvent('keypress', init));
                    target.dispatchEvent(new KeyboardEvent('keyup', init));
                  }
                }
                if ($pressEnter) {
                  var enterTarget = isEditable ? el : (document.querySelector('canvas') || el || document.body || document);
                  var enterInit = { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true, composed: true };
                  enterTarget.dispatchEvent(new KeyboardEvent('keydown', enterInit));
                  enterTarget.dispatchEvent(new KeyboardEvent('keypress', enterInit));
                  enterTarget.dispatchEvent(new KeyboardEvent('keyup', enterInit));
                }
              } catch (e) {}
            })();
        """.trimIndent()
    }
}

