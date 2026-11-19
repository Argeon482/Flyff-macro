package com.example.dualweb

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DualWebMacroDelegate {
    fun dispatchKey(keyCode: Int, holdMs: Long, metaState: Int): Boolean
    fun dispatchTap(screenX: Float, screenY: Float, holdMs: Long, jitterPx: Int): Boolean
    fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        steps: Int
    ): Boolean
    fun describeTarget(): String
}

/**
 * Main-process bridge connecting MacroInterpreter (and Floating Builder macros)
 * directly to DualWebActivity's isolated Account 1 & Account 2 WebViews
 * without using AccessibilityService, InputManager, or ADB.
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

    fun targetDescription(): String = delegate?.describeTarget() ?: "System"

    fun dispatchKey(keyCode: Int, holdMs: Long, metaState: Int): Boolean {
        return delegate?.dispatchKey(keyCode, holdMs, metaState) ?: false
    }

    fun dispatchTap(screenX: Float, screenY: Float, holdMs: Long, jitterPx: Int): Boolean {
        return delegate?.dispatchTap(screenX, screenY, holdMs, jitterPx) ?: false
    }

    fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        steps: Int
    ): Boolean {
        return delegate?.dispatchSwipe(startX, startY, endX, endY, durationMs, steps) ?: false
    }
}
