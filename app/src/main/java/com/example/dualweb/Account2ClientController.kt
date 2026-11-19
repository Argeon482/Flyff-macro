package com.example.dualweb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.view.MotionEvent
import android.view.SurfaceControlViewHost
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class Account2RemoteStatus(
    val connected: Boolean = false,
    val processName: String = ":acct2 (connecting...)",
    val pid: Int = -1,
    val dataDirSuffix: String = DualWebConstants.ACCT2_DATA_DIR_SUFFIX,
    val currentUrl: String = DualWebConstants.FLYFF_UNIVERSE_URL,
    val pageTitle: String = "",
    val desktopUa: Boolean = true,
    val hostMode: String = "IDLE",
    val dispatchCount: Int = 0,
    val lastDispatch: String = "Idle",
    val webViewVisible: Boolean = false,
    val lastError: String? = null
)

class Account2ClientController(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val preferences = DualWebPreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var service: IAccount2WebViewService? = null
    private var bound: Boolean = false
    private var statusPollJob: Job? = null

    private val _config = MutableStateFlow(preferences.load())
    val config: StateFlow<DualWebConfigState> = _config.asStateFlow()

    private val _remoteStatus = MutableStateFlow(Account2RemoteStatus())
    val remoteStatus: StateFlow<Account2RemoteStatus> = _remoteStatus.asStateFlow()

    val mainProcessPid: Int = Process.myPid()
    val mainProcessName: String = Account2WebViewManager.resolveCurrentProcessName()

    private var onServiceConnectedCallback: (() -> Unit)? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IAccount2WebViewService.Stub.asInterface(binder)
            service = svc
            bound = true
            runCatching { svc.setDesktopUserAgent(_config.value.acct2DesktopUa) }
            refreshRemoteStatus()
            startStatusPolling()
            onServiceConnectedCallback?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            _remoteStatus.update {
                it.copy(
                    connected = false,
                    hostMode = "DISCONNECTED",
                    lastError = "Account 2 process disconnected"
                )
            }
        }
    }

    fun bind(onConnected: (() -> Unit)? = null) {
        onServiceConnectedCallback = onConnected
        if (bound && service != null) {
            onConnected?.invoke()
            return
        }
        val intent = Intent(appContext, Account2HostService::class.java)
        runCatching {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        }.onFailure { err ->
            _remoteStatus.update { it.copy(lastError = err.message) }
        }
    }

    fun unbindAndRelease() {
        statusPollJob?.cancel()
        statusPollJob = null
        runCatching { service?.detachSurface() }
        if (bound) {
            runCatching { appContext.unbindService(connection) }
            bound = false
        }
        service = null
        scope.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun attachSurface(
        hostToken: IBinder,
        displayId: Int,
        width: Int,
        height: Int
    ): SurfaceControlViewHost.SurfacePackage? {
        val svc = service ?: return null
        return runCatching {
            val bundle = svc.attachSurface(hostToken, displayId, width, height)
            bundle.classLoader = SurfaceControlViewHost.SurfacePackage::class.java.classLoader
            val attached = bundle.getBoolean(DualWebConstants.KEY_ATTACHED, false)
            val err = bundle.getString(DualWebConstants.KEY_ERROR)
            if (!attached) {
                _remoteStatus.update { it.copy(lastError = err ?: "Failed to attach surface") }
                null
            } else {
                _remoteStatus.update { it.copy(lastError = null) }
                refreshRemoteStatus()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bundle.getParcelable(
                        DualWebConstants.KEY_SURFACE_PACKAGE,
                        SurfaceControlViewHost.SurfacePackage::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    bundle.getParcelable(DualWebConstants.KEY_SURFACE_PACKAGE)
                }
            }
        }.onFailure { err ->
            _remoteStatus.update { it.copy(lastError = err.message) }
        }.getOrNull()
    }

    fun resizeSurface(width: Int, height: Int) {
        runCatching { service?.resizeSurface(width, height) }
    }

    fun detachSurface() {
        runCatching { service?.detachSurface() }
    }

    /**
     * Forwards live user touch events landing on Account 2's SurfaceView directly into
     * WebView 2 inside `:acct2` via oneway AIDL (zero main-thread blocking).
     */
    fun dispatchUserMotionEvent(event: MotionEvent): Boolean {
        val svc = service ?: return false
        return runCatching {
            svc.dispatchMotionEvent(event)
            true
        }.getOrDefault(false)
    }

    fun dispatchKeyDirect(keyCode: Int, holdMs: Long, metaState: Int): Boolean {
        val svc = service ?: return false
        val modeWire = _config.value.dispatchMode.wireValue
        val ok = runCatching { svc.dispatchKey(keyCode, holdMs, metaState, modeWire) }.getOrDefault(false)
        if (ok) refreshRemoteStatus()
        return ok
    }

    fun dispatchTapDirect(normX: Float, normY: Float, holdMs: Long): Boolean {
        val svc = service ?: return false
        val modeWire = _config.value.dispatchMode.wireValue
        val ok = runCatching { svc.dispatchCanvasTap(normX, normY, holdMs, modeWire) }.getOrDefault(false)
        if (ok) refreshRemoteStatus()
        return ok
    }

    fun dispatchSwipeDirect(
        startNormX: Float,
        startNormY: Float,
        endNormX: Float,
        endNormY: Float,
        durationMs: Long,
        steps: Int
    ): Boolean {
        val svc = service ?: return false
        val modeWire = _config.value.dispatchMode.wireValue
        val ok = runCatching {
            svc.dispatchCanvasSwipe(startNormX, startNormY, endNormX, endNormY, durationMs, steps, modeWire)
        }.getOrDefault(false)
        if (ok) refreshRemoteStatus()
        return ok
    }

    fun setAcct1DesktopUa(desktop: Boolean) {
        updateConfig { it.copy(acct1DesktopUa = desktop) }
    }

    fun setAcct2DesktopUa(desktop: Boolean) {
        updateConfig { it.copy(acct2DesktopUa = desktop) }
        runCatching { service?.setDesktopUserAgent(desktop) }
        refreshRemoteStatus()
    }

    fun setDispatchMode(mode: InputDispatchMode) {
        updateConfig { it.copy(dispatchMode = mode) }
    }

    fun setLayoutMode(mode: DualLayoutMode) {
        updateConfig { it.copy(layoutMode = mode) }
    }

    fun cycleLayoutMode() {
        val all = DualLayoutMode.entries
        val next = all[(all.indexOf(_config.value.layoutMode) + 1) % all.size]
        setLayoutMode(next)
    }

    fun setMacroTarget(target: DualWebMacroTarget) {
        updateConfig { it.copy(macroTarget = target) }
    }

    fun cycleMacroTarget() {
        val all = DualWebMacroTarget.entries
        val next = all[(all.indexOf(_config.value.macroTarget) + 1) % all.size]
        setMacroTarget(next)
    }

    fun setSelectedMacroId(macroId: String?) {
        updateConfig { it.copy(selectedMacroId = macroId) }
    }

    fun setShowAccountBadges(show: Boolean) {
        updateConfig { it.copy(showAccountBadges = show) }
    }

    fun reloadAccount2() {
        runCatching { service?.reload() }
    }

    fun clearAccount2Session() {
        runCatching { service?.clearAccount2Session() }
        refreshRemoteStatus()
    }

    fun launchAccount2Activity(adjacentSplitScreen: Boolean) {
        val intent = Intent(context, Account2Activity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (adjacentSplitScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            }
        }
        runCatching { context.startActivity(intent) }
            .onFailure { err ->
                _remoteStatus.update { it.copy(lastError = err.message) }
            }
    }

    fun refreshRemoteStatus() {
        val svc = service ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { svc.status }.onSuccess { bundle ->
                _remoteStatus.value = Account2RemoteStatus(
                    connected = true,
                    processName = bundle.getString(DualWebConstants.STATUS_PROCESS_NAME).orEmpty(),
                    pid = bundle.getInt(DualWebConstants.STATUS_PID, -1),
                    dataDirSuffix = bundle.getString(DualWebConstants.STATUS_DATA_DIR_SUFFIX)
                        ?: DualWebConstants.ACCT2_DATA_DIR_SUFFIX,
                    currentUrl = bundle.getString(DualWebConstants.STATUS_CURRENT_URL)
                        ?: DualWebConstants.FLYFF_UNIVERSE_URL,
                    pageTitle = bundle.getString(DualWebConstants.STATUS_PAGE_TITLE).orEmpty(),
                    desktopUa = bundle.getBoolean(DualWebConstants.STATUS_DESKTOP_UA, true),
                    hostMode = bundle.getString(DualWebConstants.STATUS_HOST_MODE) ?: "IDLE",
                    dispatchCount = bundle.getInt(DualWebConstants.STATUS_DISPATCH_COUNT, 0),
                    lastDispatch = bundle.getString(DualWebConstants.STATUS_LAST_DISPATCH) ?: "Idle",
                    webViewVisible = bundle.getBoolean(DualWebConstants.STATUS_WEBVIEW_VISIBLE, false),
                    lastError = _remoteStatus.value.lastError
                )
            }
        }
    }

    private fun startStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (isActive) {
                delay(2000L)
                refreshRemoteStatus()
            }
        }
    }

    private fun updateConfig(transform: (DualWebConfigState) -> DualWebConfigState) {
        val next = transform(_config.value)
        _config.value = next
        preferences.save(next)
    }
}
