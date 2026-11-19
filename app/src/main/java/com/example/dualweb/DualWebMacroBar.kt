package com.example.dualweb

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.KeyboardKeyCatalog
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateWell
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingAccount2MacroBar(
    config: DualWebConfigState,
    remoteStatus: Account2RemoteStatus,
    activeLoopIds: Set<String>,
    lastFiredButtonId: String?,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onDragBar: (dx: Float, dy: Float) -> Unit,
    onFireButton: (DualMacroButton) -> Unit,
    onToggleLoop: (String) -> Unit,
    onStopAllLoops: () -> Unit,
    onCycleLayout: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (collapsed) {
        Surface(
            onClick = onToggleCollapsed,
            color = SlateSurface.copy(alpha = 0.90f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (activeLoopIds.isNotEmpty()) EmeraldRun else ElectricCyan.copy(alpha = 0.7f)
            ),
            modifier = modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragBar(dragAmount.x, dragAmount.y)
                    }
                }
                .testTag("macro_bar_collapsed_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (remoteStatus.connected) EmeraldRun else AmberLogic)
                )
                Text(
                    text = if (activeLoopIds.isNotEmpty()) "A2 • ${activeLoopIds.size}↺" else "A2 BAR",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

    Surface(
        color = SlateSurface.copy(alpha = 0.90f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (activeLoopIds.isNotEmpty()) EmeraldRun.copy(alpha = 0.75f) else HairlineBorder
        ),
        shadowElevation = 8.dp,
        modifier = modifier
            .widthIn(max = 560.dp)
            .testTag("dual_macro_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            // Drag handle + A2 connection dot
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 34.dp, height = 48.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragBar(dragAmount.x, dragAmount.y)
                        }
                    }
                    .testTag("macro_bar_drag_handle")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (remoteStatus.connected) EmeraldRun else RoseAlert)
                    )
                    Spacer(Modifier.height(2.dp))
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Drag macro bar",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Horizontal scrollable macro buttons targeting Account 2
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState())
            ) {
                config.macroButtons.forEach { button ->
                    val isLooping = activeLoopIds.contains(button.id)
                    val isPulsing = lastFiredButtonId == button.id
                    val bgColor by animateColorAsState(
                        targetValue = when {
                            isPulsing -> ElectricCyan
                            isLooping -> EmeraldRun.copy(alpha = 0.24f)
                            else -> SlateElevated
                        },
                        label = "macro_btn_bg"
                    )
                    val borderColor = when {
                        isPulsing -> ElectricCyan
                        isLooping -> EmeraldRun
                        else -> HairlineBorder
                    }
                    val labelColor = if (isPulsing) Color(0xFF04131A) else TextPrimary

                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .height(48.dp)
                            .widthIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = { onFireButton(button) },
                                onLongClick = { onToggleLoop(button.id) }
                            )
                            .testTag("macro_key_${button.id}")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = button.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = labelColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (isLooping) {
                                Text(
                                    text = formatIntervalShort(button.intervalMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPulsing) Color(0xFF04131A) else EmeraldRun,
                                    maxLines = 1
                                )
                            } else if (button.actionKind == MacroActionKind.CANVAS_TAP) {
                                Text(
                                    text = "TAP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberLogic,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // Master Stop Loops button when any loop is active
            AnimatedVisibility(visible = activeLoopIds.isNotEmpty()) {
                IconButton(
                    onClick = onStopAllLoops,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("macro_bar_stop_loops")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop all Account 2 loops",
                        tint = RoseAlert
                    )
                }
            }

            IconButton(
                onClick = onCycleLayout,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("macro_bar_cycle_layout")
            ) {
                Icon(
                    imageVector = Icons.Default.ViewQuilt,
                    contentDescription = "Switch dual layout (${config.layoutMode.shortLabel})",
                    tint = ElectricCyan
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("macro_bar_settings")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Dual client settings and macro editor",
                    tint = TextPrimary
                )
            }

            IconButton(
                onClick = onToggleCollapsed,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("macro_bar_collapse")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Minimize macro bar",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DualWebSettingsSheet(
    config: DualWebConfigState,
    remoteStatus: Account2RemoteStatus,
    activeLoopIds: Set<String>,
    mainProcessName: String,
    mainProcessPid: Int,
    onDismiss: () -> Unit,
    onSetLayoutMode: (DualLayoutMode) -> Unit,
    onSetDispatchMode: (InputDispatchMode) -> Unit,
    onToggleAcct1Ua: (Boolean) -> Unit,
    onToggleAcct2Ua: (Boolean) -> Unit,
    onReloadAcct1: () -> Unit,
    onReloadAcct2: () -> Unit,
    onClearAcct1Session: () -> Unit,
    onClearAcct2Session: () -> Unit,
    onOpenAcct2SplitActivity: (Boolean) -> Unit,
    onToggleBadges: (Boolean) -> Unit,
    onToggleLoop: (String) -> Unit,
    onStopAllLoops: () -> Unit,
    onUpsertButton: (DualMacroButton) -> Unit,
    onDeleteButton: (String) -> Unit,
    onResetDefaultButtons: () -> Unit,
    onExitDualClient: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingButton by remember { mutableStateOf<DualMacroButton?>(null) }
    var creatingNewButton by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface,
        contentColor = TextPrimary
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Dual Flyff Control Deck",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tip: Tap macro keys to fire A2 • Long-press any key to toggle interval loop",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = onExitDualClient,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dual_exit_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Exit Dual Client",
                        tint = RoseAlert
                    )
                }
            }

            // Live Process Isolation Diagnostics
            Surface(
                color = SlateWell,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (remoteStatus.connected) EmeraldRun.copy(alpha = 0.45f) else AmberLogic.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROCESS & COOKIE ISOLATION",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                        Text(
                            text = if (remoteStatus.connected) "ISOLATED • LIVE" else "BINDING...",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (remoteStatus.connected) EmeraldRun else AmberLogic
                        )
                    }
                    Text(
                        text = "Account 1: $mainProcessName (PID $mainProcessPid) • WebView data dir: default",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "Account 2: ${remoteStatus.processName} (PID ${remoteStatus.pid}) • suffix: \"${remoteStatus.dataDirSuffix}\" • ${remoteStatus.hostMode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldRun
                    )
                    Text(
                        text = "Last Input -> A2: ${remoteStatus.lastDispatch} (Total: ${remoteStatus.dispatchCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                    if (remoteStatus.lastError != null) {
                        Text(
                            text = "Notice: ${remoteStatus.lastError}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoseAlert
                        )
                    }
                }
            }

            // Input Dispatch Mode (Primary Native vs JS DOM Fallback)
            Text("ACCOUNT 2 INPUT ROUTING (ZERO TOUCH COLLISION)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InputDispatchMode.entries.forEach { mode ->
                    val selected = config.dispatchMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSetDispatchMode(mode) },
                        label = { Text(mode.shortLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.20f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dispatch_mode_${mode.name.lowercase()}")
                    )
                }
            }
            Text(
                text = config.dispatchMode.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            HorizontalDivider(color = HairlineBorder)

            // Layout Mode Picker
            Text("FULLSCREEN LAYOUT MODE (BOTH WEBVIEWS STAY VISIBLE)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DualLayoutMode.entries.forEach { mode ->
                    FilterChip(
                        selected = config.layoutMode == mode,
                        onClick = { onSetLayoutMode(mode) },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("layout_mode_${mode.name.lowercase()}")
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onOpenAcct2SplitActivity(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_acct2_login_ime")
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp), tint = AmberLogic)
                    Spacer(Modifier.width(6.dp))
                    Text("A2 Login / IME", color = TextPrimary, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onOpenAcct2SplitActivity(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_acct2_split_adjacent")
                ) {
                    Icon(Icons.Default.Splitscreen, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricCyan)
                    Spacer(Modifier.width(6.dp))
                    Text("OS Split-Screen", color = TextPrimary, maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show corner A1/A2 status badges", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Switch(
                    checked = config.showAccountBadges,
                    onCheckedChange = onToggleBadges,
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = ElectricCyan)
                )
            }

            HorizontalDivider(color = HairlineBorder)

            // Per-Account User-Agent & Session Controls
            Text("PER-ACCOUNT USER-AGENT & SESSIONS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            AccountControlRow(
                title = "Account 1 (Main Process)",
                accent = ElectricCyan,
                desktopUa = config.acct1DesktopUa,
                onToggleUa = onToggleAcct1Ua,
                onReload = onReloadAcct1,
                onClearCookies = onClearAcct1Session,
                tagPrefix = "acct1"
            )
            AccountControlRow(
                title = "Account 2 (:acct2 Isolated)",
                accent = EmeraldRun,
                desktopUa = config.acct2DesktopUa,
                onToggleUa = onToggleAcct2Ua,
                onReload = onReloadAcct2,
                onClearCookies = onClearAcct2Session,
                tagPrefix = "acct2"
            )

            HorizontalDivider(color = HairlineBorder)

            // Customizable Macro Buttons Targeting Account 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ACCOUNT 2 MACRO BUTTONS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (activeLoopIds.isNotEmpty()) {
                        TextButton(onClick = onStopAllLoops) {
                            Text("Stop Loops", color = RoseAlert)
                        }
                    }
                    TextButton(onClick = onResetDefaultButtons) {
                        Text("Reset", color = TextSecondary)
                    }
                    Button(
                        onClick = { creatingNewButton = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("add_macro_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                config.macroButtons.forEach { btn ->
                    val looping = activeLoopIds.contains(btn.id)
                    Surface(
                        color = SlateWell,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (looping) EmeraldRun else HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = btn.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                val detail = if (btn.actionKind == MacroActionKind.KEY) {
                                    "Key ${KeyboardKeyCatalog.labelForKeyCode(btn.keyCode)} • Hold ${btn.holdMs}ms • Every ${formatIntervalShort(btn.intervalMs)}"
                                } else {
                                    "Canvas Tap (${(btn.tapNormX * 100).toInt()}%, ${(btn.tapNormY * 100).toInt()}%) • Every ${formatIntervalShort(btn.intervalMs)}"
                                }
                                Text(detail, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            IconButton(
                                onClick = { onToggleLoop(btn.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = if (looping) "Stop loop for ${btn.label}" else "Start loop for ${btn.label}",
                                    tint = if (looping) EmeraldRun else TextDim
                                )
                            }
                            IconButton(
                                onClick = { editingButton = btn },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${btn.label}", tint = ElectricCyan)
                            }
                            IconButton(
                                onClick = { onDeleteButton(btn.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${btn.label}", tint = RoseAlert)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (creatingNewButton || editingButton != null) {
        MacroButtonEditorDialog(
            initial = editingButton,
            onDismiss = {
                creatingNewButton = false
                editingButton = null
            },
            onSave = { saved ->
                onUpsertButton(saved)
                creatingNewButton = false
                editingButton = null
            }
        )
    }
}

@Composable
private fun AccountControlRow(
    title: String,
    accent: Color,
    desktopUa: Boolean,
    onToggleUa: (Boolean) -> Unit,
    onReload: () -> Unit,
    onClearCookies: () -> Unit,
    tagPrefix: String
) {
    Surface(
        color = SlateWell,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    text = if (desktopUa) "Desktop Chrome UA (full hotbars)" else "Mobile WebView UA",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
            IconButton(
                onClick = { onToggleUa(!desktopUa) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${tagPrefix}_ua_toggle")
            ) {
                Icon(
                    imageVector = if (desktopUa) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                    contentDescription = "Toggle Desktop or Mobile User-Agent for $title",
                    tint = accent
                )
            }
            IconButton(
                onClick = onReload,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${tagPrefix}_reload")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload $title", tint = TextPrimary)
            }
            IconButton(
                onClick = onClearCookies,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${tagPrefix}_clear_cookies")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear cookies for $title only", tint = RoseAlert)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MacroButtonEditorDialog(
    initial: DualMacroButton?,
    onDismiss: () -> Unit,
    onSave: (DualMacroButton) -> Unit
) {
    var label by remember { mutableStateOf(initial?.label ?: "F4") }
    var actionKind by remember { mutableStateOf(initial?.actionKind ?: MacroActionKind.KEY) }
    var keyCode by remember { mutableIntStateOf(initial?.keyCode ?: android.view.KeyEvent.KEYCODE_F4) }
    var holdMsText by remember { mutableStateOf((initial?.holdMs ?: 35L).toString()) }
    var intervalMsText by remember { mutableStateOf((initial?.intervalMs ?: 1500L).toString()) }
    var loopEnabled by remember { mutableStateOf(initial?.loopEnabled ?: false) }
    var tapX by remember { mutableFloatStateOf(initial?.tapNormX ?: 0.5f) }
    var tapY by remember { mutableFloatStateOf(initial?.tapNormY ?: 0.5f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = {
            Text(
                text = if (initial == null) "Add Account 2 Macro Button" else "Edit Macro Button",
                color = TextPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(10) },
                    label = { Text("Button Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = actionKind == MacroActionKind.KEY,
                        onClick = { actionKind = MacroActionKind.KEY },
                        leadingIcon = { Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Key Press") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = actionKind == MacroActionKind.CANVAS_TAP,
                        onClick = { actionKind = MacroActionKind.CANVAS_TAP },
                        leadingIcon = { Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Canvas Tap") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (actionKind == MacroActionKind.KEY) {
                    Text("Select Key (1–9, F1–F9, Space, etc.)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DomKeyMapper.quickPresetKeys.forEach { (code, name) ->
                            FilterChip(
                                selected = keyCode == code,
                                onClick = {
                                    keyCode = code
                                    if (initial == null || label.isBlank()) {
                                        label = if (name == "Space") "SPC" else name
                                    }
                                },
                                label = { Text(name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = ElectricCyan
                                )
                            )
                        }
                    }
                } else {
                    Text("Account 2 Canvas X: ${(tapX * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    Slider(
                        value = tapX,
                        onValueChange = { tapX = it },
                        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                    )
                    Text("Account 2 Canvas Y: ${(tapY * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    Slider(
                        value = tapY,
                        onValueChange = { tapY = it },
                        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = holdMsText,
                        onValueChange = { holdMsText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Hold (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = intervalMsText,
                        onValueChange = { intervalMsText = it.filter(Char::isDigit).take(6) },
                        label = { Text("Loop (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-loop on save", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Switch(
                        checked = loopEnabled,
                        onCheckedChange = { loopEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = EmeraldRun)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hold = (holdMsText.toLongOrNull() ?: 35L).coerceIn(15L, 2000L)
                    val interval = (intervalMsText.toLongOrNull() ?: 1500L).coerceIn(150L, 600_000L)
                    val safeLabel = label.trim().ifBlank {
                        if (actionKind == MacroActionKind.KEY) KeyboardKeyCatalog.labelForKeyCode(keyCode) else "TAP"
                    }
                    onSave(
                        (initial ?: DualMacroButton(label = safeLabel)).copy(
                            label = safeLabel,
                            actionKind = actionKind,
                            keyCode = keyCode,
                            holdMs = hold,
                            intervalMs = interval,
                            loopEnabled = loopEnabled,
                            tapNormX = tapX,
                            tapNormY = tapY
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

private fun formatIntervalShort(ms: Long): String = when {
    ms >= 60_000L -> "${ms / 60_000L}m"
    ms >= 1000L && ms % 1000L == 0L -> "${ms / 1000L}s"
    ms >= 1000L -> String.format("%.1fs", ms / 1000f)
    else -> "${ms}ms"
}
