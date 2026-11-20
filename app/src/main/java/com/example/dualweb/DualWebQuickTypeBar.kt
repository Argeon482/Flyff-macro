package com.example.dualweb

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateWell
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Native Compose keyboard bridge docked cleanly above the Android soft keyboard.
 * Guarantees reliable phone keyboard typing into BOTH Account 1 (Main) and Account 2 (:acct2)
 * even in 50/50 split-screen or Desktop User-Agent mode where Flyff Universe suppresses mobile IME.
 */
@Composable
fun DualWebQuickTypeBar(
    targetAccount: Int,
    onSelectTargetAccount: (Int) -> Unit,
    onSendText: (account: Int, text: String, pressEnter: Boolean) -> Unit,
    onSendSpecialKey: (account: Int, keyCode: Int) -> Unit,
    onForceNativeImeAcct1: () -> Unit,
    onOpenFullAcct2Window: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val accent = if (targetAccount == 1) ElectricCyan else EmeraldRun

    LaunchedEffect(Unit) {
        delay(60L)
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    fun submit(pressEnter: Boolean) {
        val textToSend = draft
        if (textToSend.isEmpty() && !pressEnter) return
        onSendText(targetAccount, textToSend, pressEnter)
        draft = ""
    }

    Surface(
        color = SlateSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.7f)),
        shadowElevation = 14.dp,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 620.dp)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("dual_quick_type_bar")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Row 1: Target selector (A1 Main / A2 :acct2) + escape hatches + close
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickTypeTargetChip(
                    label = "A1 Main",
                    selected = targetAccount == 1,
                    accent = ElectricCyan,
                    onClick = { onSelectTargetAccount(1) },
                    testTag = "quick_type_target_a1"
                )
                QuickTypeTargetChip(
                    label = "A2 :acct2",
                    selected = targetAccount == 2,
                    accent = EmeraldRun,
                    onClick = { onSelectTargetAccount(2) },
                    testTag = "quick_type_target_a2"
                )

                Spacer(Modifier.weight(1f))

                if (targetAccount == 1) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onForceNativeImeAcct1()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, AmberLogic.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("quick_type_force_ime_a1")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = AmberLogic,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.dual_quick_type_force_ime),
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberLogic,
                            maxLines = 1
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenFullAcct2Window()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, EmeraldRun.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("quick_type_open_full_a2")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = EmeraldRun,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.dual_quick_type_full_a2),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldRun,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        onDismiss()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("quick_type_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Quick Type bar",
                        tint = TextSecondary
                    )
                }
            }

            // Row 2: Native auto-focused TextField + Send / Send+Enter buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.dual_quick_type_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { submit(pressEnter = true) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = HairlineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = accent,
                        focusedContainerColor = SlateWell,
                        unfocusedContainerColor = SlateWell
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("quick_type_input_field")
                )

                OutlinedButton(
                    onClick = { submit(pressEnter = false) },
                    enabled = draft.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("quick_type_send_only")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.dual_quick_type_send),
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Button(
                    onClick = { submit(pressEnter = true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = ObsidianBg
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("quick_type_send_enter")
                ) {
                    Text(
                        text = stringResource(R.string.dual_quick_type_send_enter),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Row 3: Direct special keys strip (sent straight to selected account)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                SpecialKeyChip(
                    label = "Enter",
                    icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                    accent = accent,
                    onClick = { onSendSpecialKey(targetAccount, KeyEvent.KEYCODE_ENTER) },
                    testTag = "quick_key_enter"
                )
                SpecialKeyChip(
                    label = "Bksp",
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    accent = AmberLogic,
                    onClick = { onSendSpecialKey(targetAccount, KeyEvent.KEYCODE_DEL) },
                    testTag = "quick_key_backspace"
                )
                SpecialKeyChip(
                    label = "Tab",
                    icon = null,
                    accent = TextPrimary,
                    onClick = { onSendSpecialKey(targetAccount, KeyEvent.KEYCODE_TAB) },
                    testTag = "quick_key_tab"
                )
                SpecialKeyChip(
                    label = "Esc",
                    icon = null,
                    accent = TextPrimary,
                    onClick = { onSendSpecialKey(targetAccount, KeyEvent.KEYCODE_ESCAPE) },
                    testTag = "quick_key_esc"
                )
                SpecialKeyChip(
                    label = "Space",
                    icon = null,
                    accent = TextPrimary,
                    onClick = { onSendSpecialKey(targetAccount, KeyEvent.KEYCODE_SPACE) },
                    testTag = "quick_key_space"
                )
            }
        }
    }
}

@Composable
private fun QuickTypeTargetChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        color = if (selected) accent.copy(alpha = 0.22f) else SlateWell,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.2.dp, if (selected) accent else HairlineBorder),
        modifier = Modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) accent else TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SpecialKeyChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    accent: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        color = SlateElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
