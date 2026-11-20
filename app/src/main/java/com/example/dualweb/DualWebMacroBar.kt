package com.example.dualweb

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldMore
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.ExecutionStatus
import com.example.engine.RuntimeExecutionState
import com.example.model.MacroDefinition
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateWell
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Compact, draggable Selected Macro Start/Stop bar for Dual WebView.
 * Replaces the key dock with direct control of the user's selected macro built
 * in the Floating Builder, plus 1-tap View Mode switching (50/50, A1 Full, A2 Full).
 */
@Composable
fun FloatingSelectedMacroBar(
    selectedMacro: MacroDefinition?,
    executionState: RuntimeExecutionState,
    config: DualWebConfigState,
    remoteStatus: Account2RemoteStatus,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onDragBar: (dx: Float, dy: Float) -> Unit,
    onStartMacro: () -> Unit,
    onStopMacro: () -> Unit,
    onOpenMacroPicker: () -> Unit,
    onOpenFloatingDock: () -> Unit,
    onOpenQuickType: () -> Unit,
    onCycleLayout: () -> Unit,
    onCycleTarget: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = executionState.status == ExecutionStatus.RUNNING ||
        executionState.status == ExecutionStatus.PAUSED
    val statusDotColor = when (executionState.status) {
        ExecutionStatus.RUNNING -> EmeraldRun
        ExecutionStatus.PAUSED -> AmberLogic
        ExecutionStatus.ERROR -> RoseAlert
        ExecutionStatus.IDLE -> if (remoteStatus.connected) ElectricCyan else AmberLogic
    }
    val macroTitle = selectedMacro?.name ?: "No Macro"

    if (collapsed) {
        Surface(
            color = SlateSurface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (isRunning) EmeraldRun else ElectricCyan.copy(alpha = 0.65f)
            ),
            shadowElevation = 8.dp,
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .clickable(onClick = onToggleCollapsed)
                        .padding(horizontal = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Text(
                        text = macroTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp)
                    )
                }

                IconButton(
                    onClick = onOpenQuickType,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("collapsed_quick_type_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = stringResource(R.string.dual_quick_type_open),
                        tint = ElectricCyan
                    )
                }

                IconButton(
                    onClick = if (isRunning) onStopMacro else onStartMacro,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("collapsed_start_stop_btn")
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Stop macro" else "Start macro",
                        tint = if (isRunning) RoseAlert else EmeraldRun
                    )
                }
            }
        }
        return
    }

    Surface(
        color = SlateSurface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isRunning) EmeraldRun.copy(alpha = 0.8f) else HairlineBorder
        ),
        shadowElevation = 10.dp,
        modifier = modifier
            .widthIn(max = 620.dp)
            .testTag("dual_selected_macro_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            // Drag handle + connection/run dot
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
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
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

            // Selected Macro Selector Pill
            Surface(
                onClick = onOpenMacroPicker,
                color = SlateElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                    .height(48.dp)
                    .widthIn(min = 96.dp, max = 180.dp)
                    .testTag("selected_macro_picker_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = macroTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isRunning) {
                                executionState.lastTelemetry
                            } else {
                                "${selectedMacro?.steps?.size ?: 0} steps • ${config.macroTarget.shortLabel}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (executionState.status == ExecutionStatus.ERROR) RoseAlert else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Change selected macro",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Primary Start / Stop Button
            Button(
                onClick = if (isRunning) onStopMacro else onStartMacro,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) RoseAlert else EmeraldRun,
                    contentColor = if (isRunning) Color.White else Color(0xFF031911)
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("dual_macro_start_stop_btn")
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Stop selected macro" else "Start selected macro",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isRunning) "Stop" else "Start",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // Quick Target Pill (A2 / A1 / Auto)
            Surface(
                onClick = onCycleTarget,
                color = SlateWell,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    when (config.macroTarget) {
                        DualWebMacroTarget.ACCOUNT_2 -> EmeraldRun.copy(alpha = 0.6f)
                        DualWebMacroTarget.ACCOUNT_1 -> ElectricCyan.copy(alpha = 0.6f)
                        DualWebMacroTarget.AUTO_PANE -> AmberLogic.copy(alpha = 0.6f)
                    }
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("dual_target_cycle_btn")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 9.dp)
                ) {
                    Text(
                        text = config.macroTarget.shortLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = when (config.macroTarget) {
                            DualWebMacroTarget.ACCOUNT_2 -> EmeraldRun
                            DualWebMacroTarget.ACCOUNT_1 -> ElectricCyan
                            DualWebMacroTarget.AUTO_PANE -> AmberLogic
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // View Mode Pill (50/50 -> A1 Full -> A2 Full)
            Surface(
                onClick = onCycleLayout,
                color = SlateWell,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("macro_bar_cycle_layout")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewQuilt,
                        contentDescription = "Switch view mode (${config.layoutMode.label})",
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = config.layoutMode.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // Quick Type Native Keyboard Bridge button (works in 50/50 & Desktop UA)
            IconButton(
                onClick = onOpenQuickType,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("macro_bar_quick_type")
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = stringResource(R.string.dual_quick_type_open),
                    tint = ElectricCyan
                )
            }

            // Floating Dock button (opens compact Floating Dock only)
            IconButton(
                onClick = onOpenFloatingDock,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("open_floating_dock_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Open Floating Dock",
                    tint = AmberLogic
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
                    contentDescription = "Dual client settings",
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
                    contentDescription = "Minimize bar",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SelectMacroDialog(
    macros: List<MacroDefinition>,
    selectedMacroId: String?,
    onSelectMacro: (MacroDefinition) -> Unit,
    onOpenFloatingDock: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = { Text("Select Macro", color = TextPrimary) },
        text = {
            if (macros.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "No macros saved yet. Create a macro in Studio or launch the Floating Dock over Dual Flyff.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    macros.forEach { macro ->
                        val selected = macro.id == selectedMacroId
                        Surface(
                            onClick = { onSelectMacro(macro) },
                            color = if (selected) ElectricCyan.copy(alpha = 0.16f) else SlateWell,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) ElectricCyan else HairlineBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_macro_${macro.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = macro.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) ElectricCyan else TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${macro.steps.size} steps • ${if (macro.loopCount <= 0) "∞ loops" else "${macro.loopCount}x"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onOpenFloatingDock()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberLogic, contentColor = ObsidianBg)
            ) {
                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Floating Dock", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DualWebSettingsSheet(
    config: DualWebConfigState,
    remoteStatus: Account2RemoteStatus,
    executionState: RuntimeExecutionState,
    mainProcessName: String,
    mainProcessPid: Int,
    onDismiss: () -> Unit,
    onSetLayoutMode: (DualLayoutMode) -> Unit,
    onSetMacroTarget: (DualWebMacroTarget) -> Unit,
    onSetDispatchMode: (InputDispatchMode) -> Unit,
    onToggleAcct1Ua: (Boolean) -> Unit,
    onToggleAcct2Ua: (Boolean) -> Unit,
    onReloadAcct1: () -> Unit,
    onReloadAcct2: () -> Unit,
    onClearAcct1Session: () -> Unit,
    onClearAcct2Session: () -> Unit,
    onOpenAcct2SplitActivity: (Boolean) -> Unit,
    onOpenFloatingDock: () -> Unit,
    onToggleBadges: (Boolean) -> Unit,
    onExitDualClient: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dual Flyff Control Deck",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Direct non-colliding WebView routing for Floating Builder macros",
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

            // Live Process Isolation & Telemetry Card
            Surface(
                color = SlateWell,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (remoteStatus.connected) EmeraldRun.copy(alpha = 0.45f) else AmberLogic.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
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
                        text = "Account 1: $mainProcessName (PID $mainProcessPid) • data dir: default",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "Account 2: ${remoteStatus.processName} (PID ${remoteStatus.pid}) • suffix: \"${remoteStatus.dataDirSuffix}\" • ${remoteStatus.hostMode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldRun
                    )
                    Text(
                        text = "Macro Engine: ${executionState.status.name} • ${executionState.lastTelemetry}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (executionState.status == ExecutionStatus.ERROR) RoseAlert else TextPrimary
                    )
                    Text(
                        text = "Account 2 Dispatches: ${remoteStatus.dispatchCount} (${remoteStatus.lastDispatch})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // View Mode (50/50 Split or Single WebView View)
            Text("VIEW MODE (BOTH WEBVIEWS STAY VISIBLE)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DualLayoutMode.entries.forEach { mode ->
                    FilterChip(
                        selected = config.layoutMode == mode,
                        onClick = { onSetLayoutMode(mode) },
                        label = { Text(mode.shortLabel, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("layout_mode_${mode.name.lowercase()}")
                    )
                }
            }

            HorizontalDivider(color = HairlineBorder)

            // Default Macro Target WebView (overridden by per-step A1 / A2 selection)
            Text("DEFAULT MACRO TARGET (WHEN STEP = AUTO)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DualWebMacroTarget.entries.forEach { target ->
                    FilterChip(
                        selected = config.macroTarget == target,
                        onClick = { onSetMacroTarget(target) },
                        label = { Text(target.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldRun.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldRun
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("macro_target_${target.name.lowercase()}")
                    )
                }
            }
            Text(
                text = "${config.macroTarget.description} Steps set explicitly to A1 (Main) or A2 (:acct2) always route to their chosen WebView.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // Input Dispatch Method
            Text("WEBVIEW DISPATCH METHOD", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InputDispatchMode.entries.forEach { mode ->
                    FilterChip(
                        selected = config.dispatchMode == mode,
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

            // Per-Account UA & Session Controls
            Text("PER-ACCOUNT USER-AGENT & SESSIONS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(
                text = stringResource(R.string.dual_ua_keyboard_hint),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onOpenFloatingDock()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberLogic, contentColor = ObsidianBg),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Floating Dock", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onOpenAcct2SplitActivity(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_acct2_login_ime")
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricCyan)
                    Spacer(Modifier.width(6.dp))
                    Text("A2 Login / IME", color = TextPrimary, maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = { onOpenAcct2SplitActivity(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_acct2_split_adjacent")
            ) {
                Icon(Icons.Default.Splitscreen, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldRun)
                Spacer(Modifier.width(6.dp))
                Text("Launch Account 2 in OS Split-Screen", color = TextPrimary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show corner A1 / A2 badges", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Switch(
                    checked = config.showAccountBadges,
                    onCheckedChange = onToggleBadges,
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = ElectricCyan)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
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
