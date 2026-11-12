package com.audioeq.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import com.audioeq.R
import com.audioeq.audio.DeviceDiagnostics
import com.audioeq.model.CapabilityLevel
import com.audioeq.model.EngineMode
import com.audioeq.model.ModeStatusInfo
import com.audioeq.shizuku.ShizukuState

@Composable
fun ModeSelectorCard(
    selectedMode: EngineMode,
    activeStatus: ModeStatusInfo,
    diagnostics: DeviceDiagnostics,
    shizukuState: ShizukuState = ShizukuState.NOT_INSTALLED,
    shizukuPrivilege: String = "none",
    onSelectMode: (EngineMode) -> Unit,
    onToggleRootBtAbsVol: (Boolean) -> Unit,
    onShizukuAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSetupDetails by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("engine_mode_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.engine_mode_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(
                    text = if (diagnostics.dynamicsProcessingSupported) {
                        "${diagnostics.activeRenderBandCount}-Band PCHIP Ready"
                    } else {
                        "Basic Engine (${diagnostics.nativeEqBandCount}-Band)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EngineMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    HorizontalModePill(
                        mode = mode,
                        isSelected = isSelected,
                        onClick = { onSelectMode(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val isAvailable = activeStatus.level == CapabilityLevel.AVAILABLE
            val pillContainer = if (isAvailable) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
            val pillContent = if (isAvailable) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = pillContainer,
                    contentColor = pillContent,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { showSetupDetails = !showSetupDetails }
                        .testTag("capability_status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(pillContent)
                        )
                        Text(
                            text = activeStatus.summaryText,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = { showSetupDetails = !showSetupDetails },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("toggle_mode_details_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Mode capability and setup details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showSetupDetails || activeStatus.level != CapabilityLevel.AVAILABLE) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = activeStatus.detailReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        for (step in activeStatus.setupSteps) {
                            Text(
                                text = step,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (selectedMode == EngineMode.ADB_SHIZUKU) {
                            val shizukuButtonText = when (shizukuState) {
                                ShizukuState.NOT_INSTALLED -> "Install Shizuku (Zero-PC Setup)"
                                ShizukuState.NOT_RUNNING -> "Open & Start Shizuku"
                                ShizukuState.UNSUPPORTED_VERSION -> "Update Shizuku App"
                                ShizukuState.NEEDS_PERMISSION -> "Grant Shizuku Permission"
                                ShizukuState.DENIED_PERMANENTLY -> "Re-allow in Shizuku App"
                                ShizukuState.READY -> if (diagnostics.dumpPermissionGranted) {
                                    "Shizuku Ready ($shizukuPrivilege) · DUMP Active"
                                } else {
                                    "Shizuku Ready ($shizukuPrivilege) · Sync DUMP"
                                }
                            }
                            Button(
                                onClick = onShizukuAction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("shizuku_action_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = shizukuButtonText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(shizukuButtonText)
                            }

                            val adbCmd = "adb shell pm grant ${context.packageName} android.permission.DUMP"
                            FilledTonalButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(adbCmd))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("copy_adb_command_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy ADB command",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Copy ADB DUMP Grant Command")
                            }
                        }

                        if (selectedMode == EngineMode.ROOT) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { onToggleRootBtAbsVol(!diagnostics.btDisableAbsVolActive) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("root_bt_abs_vol_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Bluetooth Absolute Volume",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(
                                        text = if (diagnostics.btDisableAbsVolActive) {
                                            "BT Absolute Volume Disabled (Contour Active)"
                                        } else {
                                            "Disable BT Absolute Volume via su"
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalModePill(
    mode: EngineMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        label = "modePillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "modePillContent"
    )

    val iconVector = when (mode) {
        EngineMode.LEGACY -> Icons.Default.Public
        EngineMode.ADB_SHIZUKU -> Icons.Default.Build
        EngineMode.ROOT -> Icons.Default.Security
    }

    val subLabel = when (mode) {
        EngineMode.LEGACY -> "Global · No ADB/Root"
        EngineMode.ADB_SHIZUKU -> "All Sessions"
        EngineMode.ROOT -> "APK-Only su"
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag("mode_option_${mode.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = mode.title,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = mode.shortTitle,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = subLabel,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.78f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
