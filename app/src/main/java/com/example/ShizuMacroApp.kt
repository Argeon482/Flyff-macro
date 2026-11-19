package com.example

import android.app.Application
import android.os.Build
import android.webkit.WebView
import com.example.data.AppDatabase
import com.example.data.MacroRepository
import com.example.dualweb.Account2WebViewManager
import com.example.dualweb.DualWebConstants
import com.example.engine.MacroInterpreter
import com.example.model.ScriptClipboard
import com.example.service.OverlayCoordinator
import com.example.shizuku.ShizukuInputBridge
import com.example.vision.ScreenDetector

class ShizuMacroApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: MacroRepository
        private set
    lateinit var shizukuBridge: ShizukuInputBridge
        private set
    lateinit var screenDetector: ScreenDetector
        private set
    lateinit var interpreter: MacroInterpreter
        private set
    lateinit var overlayCoordinator: OverlayCoordinator
        private set
    lateinit var scriptClipboard: ScriptClipboard
        private set

    override fun onCreate() {
        super.onCreate()

        val processName = Account2WebViewManager.resolveCurrentProcessName()
        if (processName.endsWith(DualWebConstants.ACCT2_PROCESS_SUFFIX)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WebView.setDataDirectorySuffix(DualWebConstants.ACCT2_DATA_DIR_SUFFIX)
            }
            // Keep the isolated :acct2 process lightweight; do not touch main-process Room DB or Shizuku listeners.
            return
        }

        database = AppDatabase.getInstance(this)
        repository = MacroRepository(this, database.macroDao())
        shizukuBridge = ShizukuInputBridge(this)
        screenDetector = ScreenDetector(this)
        overlayCoordinator = OverlayCoordinator()
        scriptClipboard = ScriptClipboard()
        interpreter = MacroInterpreter(repository, shizukuBridge, screenDetector, overlayCoordinator)

        shizukuBridge.registerListeners()
    }
}
