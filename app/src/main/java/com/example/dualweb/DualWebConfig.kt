package com.example.dualweb

import android.content.Context
import android.view.KeyEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DualWebConstants {
    const val FLYFF_UNIVERSE_URL = "https://universe.flyff.com/play"
    const val ACCT2_PROCESS_SUFFIX = ":acct2"
    const val ACCT2_DATA_DIR_SUFFIX = "acct2"

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val KEY_SURFACE_PACKAGE = "surface_package"
    const val KEY_ATTACHED = "attached"
    const val KEY_ERROR = "error"

    const val STATUS_PROCESS_NAME = "process_name"
    const val STATUS_PID = "pid"
    const val STATUS_DATA_DIR_SUFFIX = "data_dir_suffix"
    const val STATUS_CURRENT_URL = "current_url"
    const val STATUS_PAGE_TITLE = "page_title"
    const val STATUS_DESKTOP_UA = "desktop_ua"
    const val STATUS_HOST_MODE = "host_mode"
    const val STATUS_DISPATCH_COUNT = "dispatch_count"
    const val STATUS_LAST_DISPATCH = "last_dispatch"
    const val STATUS_WEBVIEW_VISIBLE = "webview_visible"
}

enum class InputDispatchMode(val wireValue: Int, val shortLabel: String, val description: String) {
    NATIVE_EVENT(
        wireValue = 0,
        shortLabel = "Native (isTrusted)",
        description = "Calls webView.dispatchKeyEvent / dispatchTouchEvent directly on the target WebView (isTrusted: true, zero focus steal)."
    ),
    JS_DOM(
        wireValue = 1,
        shortLabel = "JS DOM Fallback",
        description = "Dispatches synthetic KeyboardEvent / PointerEvent via evaluateJavascript directly to the target WebView canvas/document."
    );

    companion object {
        fun fromWire(value: Int): InputDispatchMode =
            entries.firstOrNull { it.wireValue == value } ?: NATIVE_EVENT
    }
}

enum class DualLayoutMode(val label: String, val shortLabel: String) {
    SPLIT_50_50("50 / 50 Split", "50/50"),
    SINGLE_ACCT_1("Single: Account 1", "A1 Full"),
    SINGLE_ACCT_2("Single: Account 2", "A2 Full")
}

enum class DualWebMacroTarget(val label: String, val shortLabel: String, val description: String) {
    ACCOUNT_2(
        label = "Account 2 (:acct2)",
        shortLabel = "A2",
        description = "Routes macro taps, swipes, and keys directly to Account 2 while you play Account 1."
    ),
    ACCOUNT_1(
        label = "Account 1 (Main)",
        shortLabel = "A1",
        description = "Routes macro taps, swipes, and keys directly to Account 1."
    ),
    AUTO_PANE(
        label = "Auto (By Screen Pane)",
        shortLabel = "Auto",
        description = "Routes taps/swipes to whichever WebView pane contains the picked coordinates (keys target A2)."
    )
}

@Serializable
data class DualWebConfigState(
    val acct1DesktopUa: Boolean = true,
    val acct2DesktopUa: Boolean = true,
    val dispatchMode: InputDispatchMode = InputDispatchMode.NATIVE_EVENT,
    val layoutMode: DualLayoutMode = DualLayoutMode.SPLIT_50_50,
    val macroTarget: DualWebMacroTarget = DualWebMacroTarget.ACCOUNT_2,
    val showAccountBadges: Boolean = true,
    val selectedMacroId: String? = null
)

data class DomKeyDescriptor(
    val key: String,
    val code: String,
    val legacyKeyCode: Int
)

object DomKeyMapper {
    fun toDomDescriptor(androidKeyCode: Int): DomKeyDescriptor {
        if (androidKeyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val digit = androidKeyCode - KeyEvent.KEYCODE_0
            return DomKeyDescriptor(digit.toString(), "Digit$digit", 48 + digit)
        }
        if (androidKeyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val offset = androidKeyCode - KeyEvent.KEYCODE_A
            val upper = ('A'.code + offset).toChar()
            val lower = ('a'.code + offset).toChar()
            return DomKeyDescriptor(lower.toString(), "Key$upper", 65 + offset)
        }
        if (androidKeyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
            val fn = (androidKeyCode - KeyEvent.KEYCODE_F1) + 1
            return DomKeyDescriptor("F$fn", "F$fn", 111 + fn)
        }
        return when (androidKeyCode) {
            KeyEvent.KEYCODE_SPACE -> DomKeyDescriptor(" ", "Space", 32)
            KeyEvent.KEYCODE_TAB -> DomKeyDescriptor("Tab", "Tab", 9)
            KeyEvent.KEYCODE_ENTER -> DomKeyDescriptor("Enter", "Enter", 13)
            KeyEvent.KEYCODE_ESCAPE -> DomKeyDescriptor("Escape", "Escape", 27)
            KeyEvent.KEYCODE_DEL -> DomKeyDescriptor("Backspace", "Backspace", 8)
            KeyEvent.KEYCODE_DPAD_UP -> DomKeyDescriptor("ArrowUp", "ArrowUp", 38)
            KeyEvent.KEYCODE_DPAD_DOWN -> DomKeyDescriptor("ArrowDown", "ArrowDown", 40)
            KeyEvent.KEYCODE_DPAD_LEFT -> DomKeyDescriptor("ArrowLeft", "ArrowLeft", 37)
            KeyEvent.KEYCODE_DPAD_RIGHT -> DomKeyDescriptor("ArrowRight", "ArrowRight", 39)
            KeyEvent.KEYCODE_MINUS -> DomKeyDescriptor("-", "Minus", 189)
            KeyEvent.KEYCODE_EQUALS -> DomKeyDescriptor("=", "Equal", 187)
            KeyEvent.KEYCODE_SHIFT_LEFT -> DomKeyDescriptor("Shift", "ShiftLeft", 16)
            KeyEvent.KEYCODE_CTRL_LEFT -> DomKeyDescriptor("Control", "ControlLeft", 17)
            KeyEvent.KEYCODE_ALT_LEFT -> DomKeyDescriptor("Alt", "AltLeft", 18)
            else -> DomKeyDescriptor("Unidentified", "Unidentified", 0)
        }
    }
}

class DualWebPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("dual_web_flyff_prefs", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): DualWebConfigState {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return DualWebConfigState()
        return runCatching { json.decodeFromString<DualWebConfigState>(raw) }
            .getOrDefault(DualWebConfigState())
    }

    fun save(config: DualWebConfigState) {
        val encoded = runCatching { json.encodeToString(config) }.getOrNull() ?: return
        prefs.edit().putString(KEY_CONFIG_JSON, encoded).apply()
    }

    companion object {
        private const val KEY_CONFIG_JSON = "config_v2"
    }
}
