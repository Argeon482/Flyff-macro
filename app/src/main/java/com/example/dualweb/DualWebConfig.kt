package com.example.dualweb

import android.content.Context
import android.view.KeyEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

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
        description = "Calls webView2.dispatchKeyEvent / dispatchTouchEvent directly on Account 2 (isTrusted: true, zero focus steal)."
    ),
    JS_DOM(
        wireValue = 1,
        shortLabel = "JS DOM Fallback",
        description = "Dispatches synthetic KeyboardEvent / PointerEvent via evaluateJavascript directly to Account 2's canvas/document."
    );

    companion object {
        fun fromWire(value: Int): InputDispatchMode =
            entries.firstOrNull { it.wireValue == value } ?: NATIVE_EVENT
    }
}

enum class DualLayoutMode(val label: String, val shortLabel: String) {
    SPLIT_50_50("Split 50 / 50", "50/50"),
    FOCUS_70_30("70% A1 / 30% A2", "70/30"),
    ACCT1_FULL_PIP("A1 Full + A2 PiP", "A1 Full"),
    ACCT2_FULL_PIP("A2 Full + A1 PiP", "A2 Full")
}

enum class MacroActionKind {
    KEY,
    CANVAS_TAP
}

@Serializable
data class DualMacroButton(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val actionKind: MacroActionKind = MacroActionKind.KEY,
    val keyCode: Int = KeyEvent.KEYCODE_1,
    val holdMs: Long = 35L,
    val loopEnabled: Boolean = false,
    val intervalMs: Long = 1500L,
    val tapNormX: Float = 0.5f,
    val tapNormY: Float = 0.5f
)

@Serializable
data class DualWebConfigState(
    val acct1DesktopUa: Boolean = true,
    val acct2DesktopUa: Boolean = true,
    val dispatchMode: InputDispatchMode = InputDispatchMode.NATIVE_EVENT,
    val layoutMode: DualLayoutMode = DualLayoutMode.SPLIT_50_50,
    val showAccountBadges: Boolean = true,
    val macroButtons: List<DualMacroButton> = defaultMacroButtons()
)

fun defaultMacroButtons(): List<DualMacroButton> = listOf(
    DualMacroButton(id = "btn_1", label = "1", keyCode = KeyEvent.KEYCODE_1, intervalMs = 1200L),
    DualMacroButton(id = "btn_2", label = "2", keyCode = KeyEvent.KEYCODE_2, intervalMs = 1500L),
    DualMacroButton(id = "btn_3", label = "3", keyCode = KeyEvent.KEYCODE_3, intervalMs = 2000L),
    DualMacroButton(id = "btn_4", label = "4", keyCode = KeyEvent.KEYCODE_4, intervalMs = 3000L),
    DualMacroButton(id = "btn_5", label = "5", keyCode = KeyEvent.KEYCODE_5, intervalMs = 5000L),
    DualMacroButton(id = "btn_f1", label = "F1", keyCode = KeyEvent.KEYCODE_F1, intervalMs = 10000L),
    DualMacroButton(id = "btn_f2", label = "F2", keyCode = KeyEvent.KEYCODE_F2, intervalMs = 15000L),
    DualMacroButton(id = "btn_f3", label = "F3", keyCode = KeyEvent.KEYCODE_F3, intervalMs = 30000L),
    DualMacroButton(id = "btn_space", label = "SPC", keyCode = KeyEvent.KEYCODE_SPACE, intervalMs = 800L),
    DualMacroButton(id = "btn_z", label = "Z", keyCode = KeyEvent.KEYCODE_Z, intervalMs = 2000L)
)

data class DomKeyDescriptor(
    val key: String,
    val code: String,
    val legacyKeyCode: Int
)

object DomKeyMapper {
    val quickPresetKeys: List<Pair<Int, String>> = listOf(
        KeyEvent.KEYCODE_1 to "1",
        KeyEvent.KEYCODE_2 to "2",
        KeyEvent.KEYCODE_3 to "3",
        KeyEvent.KEYCODE_4 to "4",
        KeyEvent.KEYCODE_5 to "5",
        KeyEvent.KEYCODE_6 to "6",
        KeyEvent.KEYCODE_7 to "7",
        KeyEvent.KEYCODE_8 to "8",
        KeyEvent.KEYCODE_9 to "9",
        KeyEvent.KEYCODE_0 to "0",
        KeyEvent.KEYCODE_F1 to "F1",
        KeyEvent.KEYCODE_F2 to "F2",
        KeyEvent.KEYCODE_F3 to "F3",
        KeyEvent.KEYCODE_F4 to "F4",
        KeyEvent.KEYCODE_F5 to "F5",
        KeyEvent.KEYCODE_F6 to "F6",
        KeyEvent.KEYCODE_F7 to "F7",
        KeyEvent.KEYCODE_F8 to "F8",
        KeyEvent.KEYCODE_F9 to "F9",
        KeyEvent.KEYCODE_SPACE to "Space",
        KeyEvent.KEYCODE_TAB to "Tab",
        KeyEvent.KEYCODE_ENTER to "Enter",
        KeyEvent.KEYCODE_ESCAPE to "Esc",
        KeyEvent.KEYCODE_Z to "Z",
        KeyEvent.KEYCODE_C to "C",
        KeyEvent.KEYCODE_X to "X",
        KeyEvent.KEYCODE_Q to "Q",
        KeyEvent.KEYCODE_E to "E",
        KeyEvent.KEYCODE_R to "R",
        KeyEvent.KEYCODE_F to "F",
        KeyEvent.KEYCODE_W to "W",
        KeyEvent.KEYCODE_A to "A",
        KeyEvent.KEYCODE_S to "S",
        KeyEvent.KEYCODE_D to "D"
    )

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
        private const val KEY_CONFIG_JSON = "config_v1"
    }
}
