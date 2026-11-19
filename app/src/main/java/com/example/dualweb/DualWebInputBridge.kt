package com.example.dualweb

import com.example.model.WebViewTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DualWebMacroDelegate {
    fun dispatchKey(
        keyCode: Int,
        holdMs: Long,
        metaState: Int,
        targetOverride: WebViewTarget = WebViewTarget.DEFAULT
    ): Boolean

    fun dispatchTap(
        screenX: Float,
        screenY: Float,
        holdMs: Long,
        jitterPx: Int,
        targetOverride: WebViewTarget = WebViewTarget.DEFAULT
    ): Boolean

    fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        steps: Int,
        targetOverride: WebViewTarget = WebViewTarget.DEFAULT
    ): Boolean

    fun describeTarget(targetOverride: WebViewTarget = WebViewTarget.DEFAULT): String

    fun detectTargetForScreenPoint(screenX: Float, screenY: Float): WebViewTarget = WebViewTarget.DEFAULT
}

/**
 * Main-process bridge connecting MacroInterpreter directly to DualWebActivity's
 * isolated Account 1 & Account 2 WebViews without AccessibilityService, InputManager, or ADB.
 */
class DualWebInputBridge {

    @Volatile
    private var delegate: DualWebMacroDelegate? = null

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun attach(delegate: DualWebMacroDelegate) {
        this.delegate = delegate
        _isActive.value = true
    }

    fun detach(delegate: DualWebMacroDelegate) {
        if (this.delegate === delegate) {
            this.delegate = null
            _isActive.value = false
        }
    }

    fun isRoutingActive(): Boolean = delegate != null

    fun targetDescription(targetOverride: WebViewTarget = WebViewTarget.DEFAULT): String =
        delegate?.describeTarget(targetOverride) ?: "System"

    fun detectTargetForScreenPoint(screenX: Float, screenY: Float): WebViewTarget =
        delegate?.detectTargetForScreenPoint(screenX, screenY) ?: WebViewTarget.DEFAULT

    fun dispatchKey(
        keyCode: Int,
        holdMs: Long,
        metaState: Int,
        targetOverride: WebViewTarget = WebViewTarget.DEFAULT
    ): Boolean {
        return delegate?.dispatchKey(keyCode, holdMs, metaState, targetOverride) ?: false
    }

    fun dispatchTap(
        screenX: Float,
        screenY: Float,
        holdMs: Long,
        jitterPx: Int,
        targetOverride: WebViewTarget = WebViewTarget.DEFAULT
    ): Boolean {
        return delegate?.dispatchTap(screenX, screenY, holdMs, jitterPx, targetOverride) ?: false
    }

    fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        steps: Int,
        targetOverride: WebViewTarget = WebViewTarget.DEFAULT
    ): Boolean {
        return delegate?.dispatchSwipe(startX, startY, endX, endY, durationMs, steps, targetOverride) ?: false
    }
}
