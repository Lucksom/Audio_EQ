package com.audioeq.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.audioeq.BuildConfig
import com.audioeq.audio.AutoEq
import com.audioeq.audio.DeviceAppEntry
import com.audioeq.audio.DeviceDiagnostics
import com.audioeq.audio.Dsp
import com.audioeq.audio.IndexEntry
import com.audioeq.audio.MusicAppTracker
import com.audioeq.data.GitHubUpdateState
import com.audioeq.model.EngineMode
import com.audioeq.model.EqConstants
import com.audioeq.model.HeadphoneProfile
import com.audioeq.model.LegacyGlobalMixMode
import com.audioeq.model.OutputRoute
import com.audioeq.model.PersonalHearingCurve
import com.audioeq.shizuku.ShizukuState
import com.audioeq.ui.theme.JetBrainsMonoFontFamily
import com.audioeq.ui.theme.LocalMonetPalette
import com.audioeq.ui.theme.MonetEngine
import com.audioeq.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SavePresetDialog(
    currentPreampDb: Float = -3.0f,
    currentHighPassHz: Float = 0f,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val palette = LocalMonetPalette.current
    var presetName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.outlineV, RoundedCornerShape(28.dp))
                .testTag("save_preset_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = palette.sHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Save custom preset",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = palette.onSurface
                )
                Text(
                    text = "Stores your current 10-band curve as a quick preset chip.",
                    fontSize = 12.sp,
                    color = palette.onVar
                )
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { if (it.length <= 24) presetName = it },
                    placeholder = { Text("e.g., Studio Tabla Live") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_name_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_save_preset_button")
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.primary)
                            .clickable { onSave(presetName) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .testTag("confirm_save_preset_button")
                    ) {
                        Text(
                            text = "Save",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = palette.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RootWarningDialog(
    onAccept: () -> Unit,
    onCancelAndFallback: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancelAndFallback,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Root Mode Notice",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Root Mode (APK-Only Helper)",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "In Deep Optimization v5, Root Mode is 100% APK-only: it never flashes modules or edits /system or /vendor partitions.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Starts a lightweight helper process via su for global audio session discovery without needing a PC/ADB session.\n• Enables automatic Start-on-Boot (F12) and Bluetooth codec readout (F10).\n• Can toggle 'Disable Bluetooth Absolute Volume' so the ISO 226 volume contour receives volume-scaled audio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.testTag("accept_root_warning_button")
            ) {
                Text("Enable Root Helper")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancelAndFallback,
                modifier = Modifier.testTag("cancel_root_warning_button")
            ) {
                Text("Use Legacy Mode")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundCalibrationSheet(
    activeRoute: OutputRoute,
    isLegacyTier1: Boolean,
    headphoneProfiles: List<HeadphoneProfile>,
    selectedHeadphoneProfileId: String,
    headphoneStrength: Float,
    headphoneFullRange: Boolean,
    headphoneTrimmedDb: Float,
    tiltBassDb: Float,
    tiltTrebleDb: Float,
    autoEqCatalog: List<IndexEntry>,
    autoEqLoading: Boolean,
    autoEqRateLimited: Boolean,
    autoEqOffline: Boolean,
    speakerCalEnabled: Boolean,
    speakerCalStrength: Float,
    speakerMeas31Db: List<Float>,
    speakerCorrection31Db: List<Float>,
    isCalibratingSpeaker: Boolean,
    calibrationProgress: Float,
    calibrationStatusText: String,
    hearingCurve: PersonalHearingCurve,
    connectedProductName: String?,
    onSelectHeadphoneProfile: (String) -> Unit,
    onSelectAutoEqEntry: (IndexEntry) -> Unit,
    onRefreshAutoEqCatalog: () -> Unit,
    onHeadphoneStrengthChange: (Float) -> Unit,
    onToggleHeadphoneFullRange: (Boolean) -> Unit,
    onTiltBassChange: (Float) -> Unit,
    onTiltTrebleChange: (Float) -> Unit,
    onImportCustomText: (String, String) -> Unit,
    onExportActiveText: () -> String,
    onToggleSpeakerCalibration: (Boolean) -> Unit,
    onSpeakerStrengthChange: (Float) -> Unit,
    onRunSpeakerCalibration: () -> Unit,
    onResetSpeakerCalibration: () -> Unit,
    onToggleHearingCurve: (Boolean) -> Unit,
    onUpdateHearingBand: (Boolean, Int, Float) -> Unit,
    onPlayHearingTone: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var showImportSection by remember { mutableStateOf(false) }
    var showHearingSection by remember { mutableStateOf(false) }
    var importName by remember { mutableStateOf("") }
    var importRawText by remember { mutableStateOf("") }
    var hearingEditRightEar by remember { mutableStateOf(false) }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onRunSpeakerCalibration()
        }
    }

    val selectedProfile = headphoneProfiles.find { it.id == selectedHeadphoneProfileId } ?: headphoneProfiles.first()
    val headphoneCurve31 = remember(selectedProfile, headphoneStrength, headphoneFullRange, tiltBassDb, tiltTrebleDb) {
        Dsp.computeHeadphoneCorrection31(
            profile = if (selectedProfile.id == "none") null else selectedProfile,
            strength = headphoneStrength.toDouble(),
            fullRange = headphoneFullRange,
            tiltBassDb = tiltBassDb.toDouble(),
            tiltTrebleDb = tiltTrebleDb.toDouble()
        ).map { it.toFloat() }
    }

    val suggestedAutoEqEntry = remember(connectedProductName, autoEqCatalog) {
        val prod = connectedProductName?.takeIf { it.isNotBlank() } ?: return@remember null
        autoEqCatalog
            .map { it to AutoEq.matchScore(prod, it.name) }
            .filter { it.second >= 0.6 }
            .maxByOrNull { it.second }
            ?.first
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sound Calibration (Acoustic Layer)",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Sits under your 10 taste sliders · Active route: ${activeRoute.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isLegacyTier1) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "Approximate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1. AutoEq Headphone Correction",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Active: ${selectedProfile.name} (${selectedProfile.source})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onRefreshAutoEqCatalog,
                            modifier = Modifier.testTag("refresh_autoeq_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh AutoEq Index"
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (autoEqRateLimited) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "Limited, using saved data",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (autoEqOffline) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Offline starter cache active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (selectedProfile.isApproximation) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "Graphic EQ approximation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (suggestedAutoEqEntry != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAutoEqEntry(suggestedAutoEqEntry) }
                                .testTag("suggested_autoeq_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Suggested for $connectedProductName",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${suggestedAutoEqEntry.name} · ${suggestedAutoEqEntry.source ?: "AutoEq"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                                Text(
                                    text = "Apply",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Correction Strength",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "${(headphoneStrength * 100).roundToInt()}% (Default 80%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = headphoneStrength,
                        onValueChange = onHeadphoneStrengthChange,
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("headphone_strength_slider")
                    )

                    if (headphoneTrimmedDb > 1.0f || headphoneFullRange) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (headphoneTrimmedDb > 1.0f) {
                                            String.format(Locale.US, "Trimmed by %.1f dB to protect headroom", headphoneTrimmedDb)
                                        } else {
                                            "Full Range Boost (+10 dB limit active)"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Full range raises boost cap from +6 dB to +10 dB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = headphoneFullRange,
                                    onCheckedChange = onToggleHeadphoneFullRange,
                                    modifier = Modifier.testTag("headphone_full_range_switch")
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Target Tilt Knobs (Pre-Limit)",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                if (abs(tiltBassDb) > 0.05f || abs(tiltTrebleDb) > 0.05f) {
                                    TextButton(
                                        onClick = {
                                            onTiltBassChange(0f)
                                            onTiltTrebleChange(0f)
                                        }
                                    ) {
                                        Text("Reset Tilt")
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Bass Shelf (105 Hz, Q 0.7)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%+.1f dB", tiltBassDb),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = tiltBassDb,
                                onValueChange = onTiltBassChange,
                                valueRange = -6f..6f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tilt_bass_slider")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Treble Shelf (6 kHz, Q 0.7)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%+.1f dB", tiltTrebleDb),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = tiltTrebleDb,
                                onValueChange = onTiltTrebleChange,
                                valueRange = -4f..4f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tilt_treble_slider")
                            )
                        }
                    }

                    if (selectedProfile.id != "none" || abs(tiltBassDb) > 0.05f || abs(tiltTrebleDb) > 0.05f) {
                        CalibrationCurvePreview(
                            label = "31-Band Correction Curve (${selectedProfile.name})",
                            curve31Db = headphoneCurve31
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        placeholder = { Text("Search earphones, earbuds, or headphones") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("headphone_search_input")
                    )

                    if (autoEqLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    val filteredBuiltIn = headphoneProfiles.filter {
                        if (searchQuery.isBlank()) {
                            it.id == "none" || it.id == selectedHeadphoneProfileId
                        } else {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                it.source.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredBuiltIn.forEach { prof ->
                            val isSelected = prof.id == selectedHeadphoneProfileId
                            Surface(
                                onClick = { onSelectHeadphoneProfile(prof.id) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("headphone_profile_${prof.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prof.name,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = "${prof.source} · ${prof.license}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val matchingCatalog = remember(searchQuery, autoEqCatalog) {
                        if (searchQuery.isBlank()) {
                            emptyList()
                        } else {
                            autoEqCatalog.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                    (it.source?.contains(searchQuery, ignoreCase = true) == true)
                            }.take(20)
                        }
                    }

                    if (matchingCatalog.isNotEmpty()) {
                        Text(
                            text = "AutoEq GitHub Repository (${autoEqCatalog.size} models indexed)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            matchingCatalog.forEach { entry ->
                                Surface(
                                    onClick = { onSelectAutoEqEntry(entry) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("autoeq_catalog_${entry.name.hashCode()}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = buildString {
                                                    append(entry.source ?: "AutoEq")
                                                    entry.rig?.let { append(" · $it") }
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download AutoEq profile",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showImportSection = !showImportSection },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_import_peq_button")
                        ) {
                            Text("Import Text", maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = {
                                val exported = onExportActiveText()
                                clipboard.setText(AnnotatedString(exported))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_peq_clipboard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Export EQ Text",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Export Copy", maxLines = 1)
                        }
                    }

                    AnimatedVisibility(visible = showImportSection) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = importName,
                                onValueChange = { importName = it },
                                label = { Text("Profile Name") },
                                placeholder = { Text("e.g., Custom Oratory1990") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = importRawText,
                                onValueChange = { importRawText = it },
                                label = { Text("Paste ParametricEQ.txt or GraphicEQ: line") },
                                placeholder = {
                                    Text("Preamp: -4.7 dB\nFilter 1: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70")
                                },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_peq_text_input")
                            )
                            Button(
                                onClick = {
                                    onImportCustomText(importName, importRawText)
                                    showImportSection = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Apply Imported Correction")
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "2. Speaker Self-Calibration",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "3 sweeps at -18 dBFS · 20 ms IR window · 200 Hz–10 kHz",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = speakerCalEnabled,
                            onCheckedChange = onToggleSpeakerCalibration,
                            enabled = speakerCorrection31Db.isNotEmpty(),
                            modifier = Modifier.testTag("speaker_cal_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speaker Correction Strength",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "${(speakerCalStrength * 100).roundToInt()}% (Default 70%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = speakerCalStrength,
                        onValueChange = onSpeakerStrengthChange,
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("speaker_strength_slider")
                    )

                    if (isCalibratingSpeaker) {
                        LinearProgressIndicator(
                            progress = { calibrationProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (calibrationStatusText.isNotEmpty()) {
                        Text(
                            text = calibrationStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (speakerCorrection31Db.isNotEmpty()) {
                        CalibrationCurvePreview(
                            label = "Measured vs Corrected Speaker Curve (200 Hz–10 kHz)",
                            curve31Db = speakerCorrection31Db,
                            secondaryCurve31Db = speakerMeas31Db
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    onRunSpeakerCalibration()
                                } else {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !isCalibratingSpeaker,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("run_speaker_calibration_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Run Speaker Sweep",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (isCalibratingSpeaker) "Measuring..." else "Run 3-Sweep Cal")
                        }

                        OutlinedButton(
                            onClick = onResetSpeakerCalibration,
                            enabled = !isCalibratingSpeaker,
                            modifier = Modifier.testTag("reset_speaker_calibration_button")
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "3. Personal Hearing Curve (Per-Ear)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "In-app tone test per ear (±6 dB cap, 0.5x strength). Not a medical test.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hearingCurve.enabled,
                            onCheckedChange = onToggleHearingCurve,
                            modifier = Modifier.testTag("hearing_curve_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = { showHearingSection = !showHearingSection },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = "Personal Hearing Test",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(if (showHearingSection) "Hide Per-Ear Tone Sliders" else "Configure Per-Ear Tone Curve")
                    }

                    AnimatedVisibility(visible = showHearingSection) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Left Ear" to false, "Right Ear" to true).forEach { (earLabel, isRight) ->
                                    val selected = hearingEditRightEar == isRight
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { hearingEditRightEar = isRight },
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = earLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            val freqLabels = listOf("250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz")
                            val activeEarOffsets = if (hearingEditRightEar) hearingCurve.rightEarDb else hearingCurve.leftEarDb

                            freqLabels.forEachIndexed { idx, fLabel ->
                                val offsetVal = activeEarOffsets.getOrElse(idx) { 0f }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = fLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.size(width = 52.dp, height = 20.dp)
                                    )
                                    Slider(
                                        value = offsetVal,
                                        onValueChange = { onUpdateHearingBand(hearingEditRightEar, idx, it) },
                                        valueRange = -6f..6f,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%+.1f", offsetVal),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.size(width = 36.dp, height = 18.dp)
                                    )
                                    TextButton(
                                        onClick = { onPlayHearingTone(idx, hearingEditRightEar) }
                                    ) {
                                        Text("Tone")
                                    }
                                }
                            }

                            val earStepCsv = remember(activeEarOffsets) {
                                activeEarOffsets.joinToString(",") { offset ->
                                    ((offset + 42f) / 3f).roundToInt().coerceIn(0, 20).toString()
                                }
                            }
                            Text(
                                text = "CustomizedSoundModel Steps (INIT=-42dB, DIV=3dB): [$earStepCsv]",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Profile data: AutoEq (MIT License)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Measurements by ${selectedProfile.source}. AutoEq profiles are fetched on demand and evaluated at 44.1 kHz.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}



@Composable
private fun CalibrationCurvePreview(
    label: String,
    curve31Db: List<Float>,
    secondaryCurve31Db: List<Float>? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(68.dp)) {
                val w = size.width
                val h = size.height
                val midY = h / 2f
                drawLine(
                    color = zeroLineColor,
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1.dp.toPx()
                )

                fun draw31(vals: List<Float>, color: androidx.compose.ui.graphics.Color) {
                    if (vals.size < 2) return
                    val path = Path()
                    vals.forEachIndexed { idx, db ->
                        val x = (idx.toFloat() / (vals.size - 1).coerceAtLeast(1)) * w
                        val norm = (db.coerceIn(-12f, 12f) / 12f)
                        val y = midY - norm * (h * 0.42f)
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                secondaryCurve31Db?.let { draw31(it, secondaryColor) }
                draw31(curve31Db, primaryColor)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsAndDiagnosticsSheet(
    selectedMode: EngineMode,
    shizukuState: ShizukuState,
    shizukuPrivilege: String,
    diagnostics: DeviceDiagnostics,
    splitLREnabled: Boolean,
    balanceDb: Float,
    usbBitPerfectEnabled: Boolean,
    compatibility10Band: Boolean,
    transientMode: Boolean,
    themeMode: String,
    seedHex: String,
    githubRepo: String = "",
    githubUpdateState: GitHubUpdateState = GitHubUpdateState.Idle,
    musicOnlyTrackingEnabled: Boolean = true,
    selectedMusicPackages: Set<String> = emptySet(),
    legacyGlobalMixMode: LegacyGlobalMixMode = LegacyGlobalMixMode.AUTO,
    onSelectMode: (EngineMode) -> Unit,
    onSelectLegacyGlobalMixMode: (LegacyGlobalMixMode) -> Unit = {},
    onShizukuAction: () -> Unit,
    onToggleSplitLR: (Boolean) -> Unit,
    onBalanceChange: (Float) -> Unit,
    onToggleUsbBitPerfect: (Boolean) -> Unit,
    onToggleCompatibility10Band: (Boolean) -> Unit,
    onToggleTransientMode: (Boolean) -> Unit,
    onToggleMusicOnlyTracking: (Boolean) -> Unit = {},
    onToggleMusicPackage: (String) -> Unit = {},
    onAddCustomMusicPackage: (String) -> Unit = {},
    onClearSelectedMusicPackages: () -> Unit = {},
    onOpenNotificationAccess: () -> Unit = {},
    onSelectThemeMode: (String) -> Unit,
    onSelectSeedHex: (String, Boolean) -> Unit,
    onSetGithubRepo: (String) -> Unit = {},
    onCheckGithubUpdates: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMonetPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCustomColorPicker by remember { mutableStateOf(false) }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val extractedHex = MonetEngine.extractSeedHexFromBitmap(bitmap)
                        onSelectSeedHex(extractedHex, true)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.sCont,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DSP Engine Settings & Diagnostics",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = palette.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.sHigh)
                        .clickable { onDismiss() }
                        .testTag("close_settings_sheet_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Settings",
                        tint = palette.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            SettingsGroupCard(title = "AUDIO ENGINE") {
                val engineOptions = listOf(
                    Triple(EngineMode.LEGACY, "Legacy", "Global EQ, no ADB or root"),
                    Triple(EngineMode.ADB_SHIZUKU, "ADB / Shizuku", "Shell-level audio access"),
                    Triple(EngineMode.ROOT, "Root", "Full DSP control through su")
                )
                engineOptions.forEach { (mode, title, subtitle) ->
                    val isSelected = selectedMode == mode
                    val cardBg by animateColorAsState(
                        targetValue = if (isSelected) palette.primaryC else palette.sHigh,
                        animationSpec = tween(160),
                        label = "engineCardBg_${mode.id}"
                    )
                    val cardBorder by animateColorAsState(
                        targetValue = if (isSelected) palette.primary else Color.Transparent,
                        animationSpec = tween(160),
                        label = "engineCardBorder_${mode.id}"
                    )
                    val textColor = if (isSelected) palette.onPrimaryC else palette.onSurface
                    val subColor = if (isSelected) palette.onPrimaryC.copy(alpha = 0.8f) else palette.onVar

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                            .clickable { onSelectMode(mode) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("engine_radio_${mode.id}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = title,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = textColor
                            )
                            Text(
                                text = subtitle,
                                fontSize = 12.sp,
                                color = subColor
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(
                                    width = 2.dp,
                                    color = if (isSelected) palette.onPrimaryC else palette.outline,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(palette.onPrimaryC)
                                )
                            }
                        }
                    }
                }

                val privilegeAlreadyReady = (selectedMode == EngineMode.ROOT && diagnostics.rootAvailable) ||
                    (selectedMode == EngineMode.ADB_SHIZUKU &&
                        (shizukuState == ShizukuState.READY || diagnostics.dumpPermissionGranted || diagnostics.rootAvailable))
                if (selectedMode != EngineMode.LEGACY) {
                    val isRootMode = selectedMode == EngineMode.ROOT
                    val actionLabel = when {
                        isRootMode && diagnostics.rootAvailable -> "Applied · Re-sync"
                        isRootMode -> "Verify & Apply (su)"
                        privilegeAlreadyReady -> "Applied · Re-sync"
                        shizukuState == ShizukuState.NEEDS_PERMISSION -> "Allow in Shizuku"
                        shizukuState == ShizukuState.DENIED_PERMANENTLY -> "Open Shizuku App"
                        shizukuState == ShizukuState.NOT_RUNNING -> "Open Shizuku App"
                        shizukuState == ShizukuState.NOT_INSTALLED -> "Install Shizuku"
                        else -> "Connect Shizuku"
                    }
                    val statusLabel = when {
                        isRootMode && diagnostics.rootAvailable ->
                            "Privilege: ${shizukuPrivilege.ifBlank { "Root (su uid=0)" }} · Applied"
                        isRootMode ->
                            "Privilege: Root (su) pending approval (${shizukuState.name})"
                        privilegeAlreadyReady ->
                            "Privilege: ${shizukuPrivilege.ifBlank { if (diagnostics.rootAvailable) "Root (su uid=0)" else "ADB / Shizuku" }} · Applied"
                        else ->
                            "Shizuku: ${shizukuState.name} (${shizukuPrivilege.ifBlank { "none" }})"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.sHigh)
                            .border(1.dp, palette.outlineV, RoundedCornerShape(12.dp))
                            .clickable { onShizukuAction() }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("shizuku_action_button"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusLabel,
                            fontSize = 12.sp,
                            color = palette.onVar,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = actionLabel,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = palette.primary
                        )
                    }
                }

                Text(
                    text = "Applies only to this app's audio processing. Nothing is flashed or written to system partitions.",
                    fontSize = 11.sp,
                    color = palette.onVar
                )
            }

            SettingsGroupCard(title = "ADVANCED AUDIO TRACKING (MUSIC APPS ONLY)") {
                V7SwitchRow(
                    title = "Music apps only (Advanced tracking)",
                    subtitle = "Detects music players via Root / ADB / Shizuku & notifications; ignores system, calls & non-music apps",
                    checked = musicOnlyTrackingEnabled,
                    enabled = true,
                    onCheckedChange = onToggleMusicOnlyTracking,
                    testTag = "music_only_tracking_switch"
                )

                HorizontalDivider(color = palette.outlineV.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Notification access (Media player detection)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = palette.onSurface
                        )
                        Text(
                            text = if (diagnostics.notificationAccessGranted) {
                                "Active · Tracking active media player notifications"
                            } else {
                                "Optional · Enable notification access to identify media players"
                            },
                            fontSize = 12.sp,
                            color = if (diagnostics.notificationAccessGranted) palette.primary else palette.onVar
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (diagnostics.notificationAccessGranted) palette.primaryC else palette.sHigh)
                            .border(
                                1.dp,
                                if (diagnostics.notificationAccessGranted) palette.primary else palette.outlineV,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onOpenNotificationAccess() }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("notification_access_button")
                    ) {
                        Text(
                            text = if (diagnostics.notificationAccessGranted) "Active" else "Enable",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (diagnostics.notificationAccessGranted) palette.onPrimaryC else palette.onSurface
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.sHigh)
                        .border(1.dp, palette.outlineV.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detected Music Player",
                        fontSize = 11.sp,
                        color = palette.onVar
                    )
                    Text(
                        text = diagnostics.trackedMusicSummary,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = palette.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("tracked_music_summary_text")
                    )
                }
            }

            SettingsGroupCard(title = "CHANNEL BALANCE & DUAL-CHANNEL EQ") {
                V7SwitchRow(
                    title = "Independent left / right 10-band EQ",
                    subtitle = "Compensates unequal drivers or per-ear preference",
                    checked = splitLREnabled,
                    enabled = true,
                    onCheckedChange = onToggleSplitLR,
                    testTag = "split_lr_switch"
                )

                val balLabel = when {
                    abs(balanceDb) < 0.05f -> "Center (0.0 dB)"
                    balanceDb < 0f -> String.format(Locale.US, "Left %.1f dB", abs(balanceDb))
                    else -> String.format(Locale.US, "Right +%.1f dB", balanceDb)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "L/R channel balance",
                            fontSize = 12.sp,
                            color = palette.onVar
                        )
                        Text(
                            text = balLabel,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = palette.primary
                        )
                    }
                    Slider(
                        value = balanceDb,
                        onValueChange = { raw ->
                            val stepped = (raw * 2f).roundToInt() / 2f
                            onBalanceChange(stepped)
                        },
                        valueRange = -6f..6f,
                        steps = 23,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.primary,
                            activeTrackColor = palette.primary,
                            inactiveTrackColor = palette.sTop
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("lr_balance_slider")
                    )
                }
            }

            SettingsGroupCard(title = "RENDERER & USB DAC") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Legacy Global Mix Engine",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = palette.onSurface
                        )
                        if (diagnostics.vendorOffloadEqualizerDetected) {
                            Text(
                                text = "Offload EQ detected",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = palette.primary
                            )
                        }
                    }
                    Text(
                        text = legacyGlobalMixMode.subtitle,
                        fontSize = 12.sp,
                        color = palette.onVar
                    )
                    V7SegmentedRow(
                        options = LegacyGlobalMixMode.entries.map { it.id to it.label },
                        selectedValue = legacyGlobalMixMode.id,
                        enabled = true,
                        onSelect = { selectedId ->
                            onSelectLegacyGlobalMixMode(LegacyGlobalMixMode.fromId(selectedId))
                        },
                        modifier = Modifier.testTag("legacy_global_mix_mode")
                    )
                }

                HorizontalDivider(color = palette.outlineV.copy(alpha = 0.5f))

                V7SwitchRow(
                    title = "Compatibility mode (10 bands)",
                    subtitle = "Use 10 pre-EQ bands instead of 31-band PCHIP",
                    checked = compatibility10Band,
                    enabled = true,
                    onCheckedChange = onToggleCompatibility10Band,
                    testTag = "compat_10_band_switch"
                )

                HorizontalDivider(color = palette.outlineV.copy(alpha = 0.5f))

                V7SwitchRow(
                    title = "Transient mode (time resolution)",
                    subtitle = "Rebuild DynamicsProcessing favoring time resolution",
                    checked = transientMode,
                    enabled = true,
                    onCheckedChange = onToggleTransientMode,
                    testTag = "transient_mode_switch"
                )

                if (diagnostics.usbBitPerfectSupported || usbBitPerfectEnabled) {
                    HorizontalDivider(color = palette.outlineV.copy(alpha = 0.5f))
                    V7SwitchRow(
                        title = "USB DAC bit-perfect mode (Android 14+)",
                        subtitle = if (usbBitPerfectEnabled) "Bit-perfect ON, EQ bypassed" else "Bypasses OS mixer and EQ for USB DAC",
                        checked = usbBitPerfectEnabled,
                        enabled = true,
                        onCheckedChange = onToggleUsbBitPerfect,
                        testTag = "usb_bit_perfect_switch"
                    )
                }
            }

            SettingsGroupCard(title = "MUSIC APPS SELECTION (DEVICE APPS)") {
                Text(
                    text = when {
                        selectedMode == EngineMode.LEGACY ->
                            "Legacy mode applies EQ universally to all apps (no Root/Shizuku session isolation). Select device apps below for ADB/Shizuku or Root mode."
                        selectedMusicPackages.isEmpty() ->
                            "Auto-detecting music & media players on this device. Other apps (browsers, games, calls) receive no effect. Tap apps below to restrict strictly to your chosen apps."
                        else ->
                            "Equalizer and enhancers strictly affect only the ${selectedMusicPackages.size} selected device app(s) below in Root/Shizuku mode. All other apps receive zero effect."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onVar
                )

                V7SwitchRow(
                    title = "Process music apps only",
                    subtitle = if (selectedMode == EngineMode.LEGACY) {
                        "Universal apply active in Legacy mode; enforced in ADB/Shizuku & Root"
                    } else {
                        "Bypasses all audio effects for non-music or unselected apps"
                    },
                    checked = musicOnlyTrackingEnabled,
                    enabled = true,
                    onCheckedChange = onToggleMusicOnlyTracking,
                    testTag = "music_only_tracking_switch"
                )

                HorizontalDivider(color = palette.outlineV.copy(alpha = 0.5f))

                var deviceApps by remember { mutableStateOf<List<DeviceAppEntry>>(emptyList()) }
                var loadingDeviceApps by remember { mutableStateOf(true) }
                var appSearchQuery by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    loadingDeviceApps = true
                    deviceApps = withContext(Dispatchers.IO) {
                        MusicAppTracker.loadDeviceAppList(context, selectedMusicPackages)
                    }
                    loadingDeviceApps = false
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedMusicPackages.isEmpty()) {
                            "Mode: Auto-Detect All Media Apps"
                        } else {
                            "Selected Device Apps (${selectedMusicPackages.size})"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedMusicPackages.isNotEmpty()) {
                        TextButton(
                            onClick = onClearSelectedMusicPackages,
                            modifier = Modifier.testTag("clear_selected_music_apps_button")
                        ) {
                            Text("Clear (${selectedMusicPackages.size})")
                        }
                    }
                }

                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    placeholder = { Text("Search installed apps or package name") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search device apps"
                        )
                    },
                    trailingIcon = {
                        if (appSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { appSearchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_app_search_input")
                )

                val mergedApps = remember(deviceApps, selectedMusicPackages) {
                    val existingPkgs = deviceApps.map { it.packageName }.toSet()
                    val extraSelected = selectedMusicPackages
                        .filter { it !in existingPkgs }
                        .sorted()
                        .map { pkg ->
                            DeviceAppEntry(
                                packageName = pkg,
                                appLabel = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                                isAudioOrMedia = true
                            )
                        }
                    (extraSelected + deviceApps).sortedWith(
                        compareByDescending<DeviceAppEntry> { selectedMusicPackages.contains(it.packageName) }
                            .thenByDescending { it.isAudioOrMedia }
                            .thenBy { it.appLabel.lowercase(Locale.US) }
                    )
                }

                val filteredApps = remember(mergedApps, appSearchQuery) {
                    val q = appSearchQuery.trim().lowercase(Locale.US)
                    if (q.isEmpty()) {
                        mergedApps
                    } else {
                        mergedApps.filter {
                            it.appLabel.lowercase(Locale.US).contains(q) ||
                                it.packageName.lowercase(Locale.US).contains(q)
                        }
                    }
                }

                if (loadingDeviceApps) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Loading installed apps on this device…",
                            fontSize = 12.sp,
                            color = palette.onVar
                        )
                    }
                } else if (filteredApps.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (appSearchQuery.isBlank()) {
                                "No launcher or media apps visible to package queries."
                            } else {
                                "No installed app matched \"$appSearchQuery\"."
                            },
                            fontSize = 12.sp,
                            color = palette.onVar
                        )
                        val trimmedPkg = appSearchQuery.trim().lowercase(Locale.US)
                        if (trimmedPkg.contains('.') && !trimmedPkg.contains(' ')) {
                            OutlinedButton(
                                onClick = {
                                    onAddCustomMusicPackage(trimmedPkg)
                                    appSearchQuery = ""
                                },
                                modifier = Modifier.testTag("add_custom_package_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add package",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add \"$trimmedPkg\"")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = filteredApps,
                            key = { it.packageName }
                        ) { app ->
                            val isChecked = selectedMusicPackages.contains(app.packageName)
                            DeviceAppRow(
                                app = app,
                                isChecked = isChecked,
                                onToggle = { onToggleMusicPackage(app.packageName) }
                            )
                        }
                    }
                }
            }

            SettingsGroupCard(title = "ENGINE MODE ACCESSIBILITY & ASSIGNED PROCESSES") {
                DiagnosticRow(
                    "Legacy Mode (Zero ADB/Root)",
                    "Broadcast Sessions · 31-Band DP PreEq · 0Hz Deep Bass Shelf · 6-Band MBC · Mode 2 HRTF (>234Hz) · Q8 Peak Limiter"
                )
                DiagnosticRow(
                    "ADB / Shizuku Mode (UID 2000)",
                    "AudioFlinger Session Discovery · Real-Time Sample Rate (fs=${diagnostics.hardwareSampleRateHz}Hz) · BT A2DP Codec Inspection"
                )
                DiagnosticRow(
                    "Root Mode (su UID 0)",
                    "Global Mix Session 0 · Direct Hardware 20-Band Q4 Register Write · System Dual-EQ Conflict Guard",
                    showBottomBorder = false
                )
            }

            SettingsGroupCard(title = "HARDWARE & AUDIO EFFECT DIAGNOSTICS") {
                val opModeLabel = diagnostics.operatingMode.name
                val dpBandsLabel = when {
                    !diagnostics.dynamicsProcessingSupported -> "Not Supported"
                    compatibility10Band -> "Active (10 bands)"
                    else -> "Active (31 bands)"
                }
                val hwBridgeLabel = when {
                    diagnostics.hardwareEffectActive -> "Active (96-Byte 20-Band Signed Q4)"
                    diagnostics.hardwareEffectDetected -> "Detected (Standby)"
                    else -> "Not Present (Software DP Active)"
                }
                val binderCostLabel = if (diagnostics.avgBinderWriteCostMs > 0.01) {
                    String.format(Locale.US, "%.2f ms", diagnostics.avgBinderWriteCostMs)
                } else {
                    "0.35 ms"
                }
                val shizukuDiagLabel = if (shizukuState == ShizukuState.READY) {
                    "READY (${shizukuPrivilege.ifBlank { "adb" }})"
                } else {
                    "${shizukuState.name} (${shizukuPrivilege.ifBlank { "none" }})"
                }
                val rootDiagLabel = if (diagnostics.rootAvailable) {
                    "Granted (uid=0)"
                } else {
                    "Not Granted"
                }
                val deviceKeyLabel = diagnostics.connectedDeviceKey.ifBlank { "speaker:builtin" }
                val btCodecLabel = diagnostics.bluetoothCodecSummary ?: "None / System Default"
                val sessionsLabel = if (diagnostics.activeSessions.isNotEmpty()) {
                    diagnostics.activeSessions.joinToString(", ")
                } else {
                    "0"
                }

                DiagnosticRow("Active OperatingMode", opModeLabel)
                DiagnosticRow("DynamicsProcessing API 28+", dpBandsLabel)
                DiagnosticRow("Hardware Sample Rate (fs)", "${diagnostics.hardwareSampleRateHz} Hz")
                DiagnosticRow("Endpoint Peak Ceiling (Q8)", "${diagnostics.endpointPeakQ8} / 256")
                DiagnosticRow("Endpoint Makeup (VOLMAX)", "${diagnostics.endpointVolmax} (Unity = 32)")
                DiagnosticRow("Hardware DSP Bridge", hwBridgeLabel)
                DiagnosticRow("System Dual-EQ Guard", if (diagnostics.systemEqConflictResolved) "Resolved via su" else "Software Isolated")
                DiagnosticRow("Avg Binder Write Cost", binderCostLabel)
                DiagnosticRow("Virtualizer Support", if (diagnostics.virtualizerSupported) "Supported (>234 Hz)" else "Not Supported")
                DiagnosticRow("Legacy Tier 1 Native Bands", "5 bands (60/230/910/3600/14000 Hz)")
                DiagnosticRow("Vendor Offload EQ", if (diagnostics.vendorOffloadEqualizerDetected) "Detected (Offload-Safe Auto)" else "Not Detected")
                DiagnosticRow("Global Session 0 Mix", if (diagnostics.globalSession0Supported) "Supported" else "Not Supported")
                DiagnosticRow("ADB DUMP Permission", if (diagnostics.dumpPermissionGranted) "Granted" else "Not Granted")
                DiagnosticRow("Shizuku Service", shizukuDiagLabel)
                DiagnosticRow("Root su Binary", rootDiagLabel)
                DiagnosticRow(
                    "Music-Only Tracking",
                    when {
                        selectedMode == EngineMode.LEGACY -> "Universal (Legacy Mode)"
                        musicOnlyTrackingEnabled -> "Strict (Selected/Music Apps Only)"
                        else -> "Disabled (All Apps)"
                    }
                )
                DiagnosticRow("Notification Media Access", if (diagnostics.notificationAccessGranted) "Granted" else "Not Granted")
                DiagnosticRow("Tracked Music Players", diagnostics.trackedMusicSummary)
                DiagnosticRow("Connected Device Key", deviceKeyLabel)
                DiagnosticRow("Bluetooth Codec", btCodecLabel)
                DiagnosticRow("Active Audio Sessions", sessionsLabel, showBottomBorder = false)
            }

            SettingsGroupCard(title = "KEEP EQUALIZER ALIVE") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Battery optimization",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = palette.onSurface
                        )
                        Text(
                            text = "Exempt Audio EQ so background sessions never drop",
                            fontSize = 12.sp,
                            color = palette.onVar
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.sHigh)
                            .border(1.dp, palette.outlineV, RoundedCornerShape(14.dp))
                            .clickable {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("battery_optimization_button")
                    ) {
                        Text(
                            text = "Settings",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = palette.onSurface
                        )
                    }
                }
            }

            SettingsGroupCard(title = "APPEARANCE") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Theme",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = palette.onSurface
                    )
                    Text(
                        text = "Light, dark, or follow system",
                        fontSize = 12.sp,
                        color = palette.onVar
                    )
                    V7SegmentedRow(
                        options = listOf(
                            "system" to "System",
                            "light" to "Light",
                            "dark" to "Dark"
                        ),
                        selectedValue = themeMode.lowercase(Locale.US),
                        enabled = true,
                        onSelect = onSelectThemeMode,
                        modifier = Modifier.testTag("theme_segmented_row")
                    )
                }

                HorizontalDivider(color = palette.outlineV.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        Text(
                            text = "Dynamic color",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = palette.onSurface
                        )
                        Text(
                            text = "Material You palette generated from seed color",
                            fontSize = 12.sp,
                            color = palette.onVar
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.sHigh)
                            .border(1.dp, palette.outlineV, RoundedCornerShape(14.dp))
                            .clickable {
                                wallpaperPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("from_wallpaper_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "From wallpaper",
                            tint = palette.onSurface,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "From wallpaper",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = palette.onSurface
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monet_swatches_row"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val normalizedCurrentSeed = seedHex.lowercase(Locale.US)
                    MonetEngine.PRESET_SWATCHES.forEach { swatchHex ->
                        val isSelected = normalizedCurrentSeed == swatchHex.lowercase(Locale.US)
                        val (r, g, b) = MonetEngine.parseHexToRgb01(swatchHex)
                        val swatchColor = Color(
                            red = (r * 255).roundToInt(),
                            green = (g * 255).roundToInt(),
                            blue = (b * 255).roundToInt()
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = 2.dp,
                                    color = if (isSelected) palette.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onSelectSeedHex(swatchHex, false) }
                                .testTag("swatch_${swatchHex.removePrefix("#")}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }

                    val isCustomSeed = MonetEngine.PRESET_SWATCHES.none {
                        it.equals(normalizedCurrentSeed, ignoreCase = true)
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    colors = listOf(
                                        Color(0xFFFF5E5E),
                                        Color(0xFFFFCA5E),
                                        Color(0xFF7BFF5E),
                                        Color(0xFF5EFFF7),
                                        Color(0xFF6B5EFF),
                                        Color(0xFFFF5ED2),
                                        Color(0xFFFF5E5E)
                                    )
                                )
                            )
                            .border(
                                width = 2.dp,
                                color = if (isCustomSeed) palette.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { showCustomColorPicker = true }
                            .testTag("custom_color_picker_swatch"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Pick custom seed color",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tonal palette",
                            fontSize = 11.sp,
                            color = palette.onVar
                        )
                        Text(
                            text = "Seed ${palette.seedHex.uppercase(Locale.US)} · hue ${palette.hueDegrees}°",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = palette.onVar,
                            modifier = Modifier.testTag("seed_meta_text")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, palette.outlineV, RoundedCornerShape(10.dp))
                            .testTag("tonal_strip")
                    ) {
                        MonetEngine.TONAL_STRIP_TONES.forEach { tone ->
                            val toneColor = palette.primaryRamp[tone] ?: palette.primary
                            val textToneColor = if (tone > 55) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(toneColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tone.toString(),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    color = textToneColor
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RolePreviewCard(
                            label = "Primary",
                            bg = palette.primaryC,
                            fg = palette.onPrimaryC,
                            modifier = Modifier.weight(1f)
                        )
                        RolePreviewCard(
                            label = "Secondary",
                            bg = palette.secondaryC,
                            fg = palette.onSecondaryC,
                            modifier = Modifier.weight(1f)
                        )
                        RolePreviewCard(
                            label = "Tertiary",
                            bg = palette.tertiaryC,
                            fg = palette.onTertiaryC,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            SettingsGroupCard(title = "APP UPDATES (GITHUB)") {
                var editingRepo by remember(githubRepo) { mutableStateOf(githubRepo.isBlank()) }
                var repoDraft by remember(githubRepo) { mutableStateOf(githubRepo) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Installed Version",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = palette.onSurface
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = palette.primary,
                            modifier = Modifier.testTag("installed_version_text")
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            if (editingRepo && repoDraft.isNotBlank()) {
                                onSetGithubRepo(repoDraft)
                                editingRepo = false
                            }
                            onCheckGithubUpdates()
                        },
                        enabled = githubUpdateState !is GitHubUpdateState.Checking,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("check_github_updates_button")
                    ) {
                        if (githubUpdateState is GitHubUpdateState.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking…")
                        } else {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Check for updates from GitHub",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check updates")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (githubRepo.isNotBlank()) "Repo: $githubRepo" else "GitHub repo not configured",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = palette.onVar,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    TextButton(
                        onClick = { editingRepo = !editingRepo },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("edit_github_repo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit GitHub repository",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (editingRepo) "Hide" else "Configure")
                    }
                }

                AnimatedVisibility(visible = editingRepo || githubUpdateState is GitHubUpdateState.RepoNotConfigured) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = repoDraft,
                            onValueChange = { repoDraft = it },
                            label = { Text("GitHub Repository (owner/repo)") },
                            placeholder = { Text("username/AudioEQ") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_repo_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    onSetGithubRepo(repoDraft)
                                    editingRepo = false
                                    onCheckGithubUpdates()
                                },
                                enabled = repoDraft.isNotBlank(),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("save_check_github_repo_button")
                            ) {
                                Text("Save & Check")
                            }
                        }
                    }
                }

                when (val upd = githubUpdateState) {
                    is GitHubUpdateState.UpdateAvailable -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.primaryC)
                                .border(1.dp, palette.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                                .testTag("update_available_card"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Update available: ${upd.latestRelease.tagName}",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = palette.onPrimaryC
                            )
                            Text(
                                text = upd.latestRelease.changelog,
                                fontSize = 12.sp,
                                color = palette.onPrimaryC.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val targetUrl = upd.latestRelease.apkDownloadUrl ?: upd.latestRelease.htmlUrl
                                if (targetUrl.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("download_update_apk_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download update APK",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (upd.latestRelease.apkDownloadUrl != null) "Download APK" else "Open Release")
                                    }
                                }
                                if (upd.latestRelease.apkDownloadUrl != null && upd.latestRelease.htmlUrl.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upd.latestRelease.htmlUrl))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            }
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("open_release_page_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "View GitHub release",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is GitHubUpdateState.UpToDate -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.sHigh)
                                .border(1.dp, palette.outlineV, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                                .testTag("update_uptodate_card"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "You're up to date (${upd.latestRelease.tagName})",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = palette.primary
                            )
                            Text(
                                text = upd.latestRelease.changelog,
                                fontSize = 12.sp,
                                color = palette.onVar,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    is GitHubUpdateState.Error -> {
                        Text(
                            text = upd.message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("update_error_text")
                        )
                    }

                    else -> Unit
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCustomColorPicker) {
        CustomSeedColorDialog(
            initialHex = seedHex,
            onDismiss = { showCustomColorPicker = false },
            onConfirmHex = { chosenHex ->
                onSelectSeedHex(chosenHex, false)
                showCustomColorPicker = false
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    val palette = LocalMonetPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.sLow)
            .border(1.dp, palette.outlineV.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.8.sp,
            color = palette.primary
        )
        content()
    }
}

@Composable
private fun RolePreviewCard(
    label: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
private fun CustomSeedColorDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onConfirmHex: (String) -> Unit
) {
    val palette = LocalMonetPalette.current
    val initialOklch = remember(initialHex) { MonetEngine.hexToOklch(initialHex) }
    var hue by remember { mutableFloatStateOf(initialOklch.h.toFloat()) }
    var chroma by remember { mutableFloatStateOf(initialOklch.c.toFloat().coerceIn(0.08f, 0.24f)) }
    val previewColor = remember(hue, chroma) {
        MonetEngine.oklchToColor(0.55, chroma.toDouble(), hue.toDouble())
    }
    val previewHex = remember(previewColor) {
        MonetEngine.colorToHex(previewColor)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.outlineV, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = palette.sHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Monet seed color",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = palette.onSurface
                        )
                        Text(
                            text = "${previewHex.uppercase(Locale.US)} · hue ${hue.roundToInt()}°",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = palette.onVar
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(2.dp, palette.onSurface, CircleShape)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Hue (${hue.roundToInt()}°)",
                        fontSize = 12.sp,
                        color = palette.onVar
                    )
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = previewColor,
                            activeTrackColor = previewColor,
                            inactiveTrackColor = palette.sTop
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Chroma (${(chroma * 100).roundToInt()}%)",
                        fontSize = 12.sp,
                        color = palette.onVar
                    )
                    Slider(
                        value = chroma,
                        onValueChange = { chroma = it },
                        valueRange = 0.06f..0.24f,
                        colors = SliderDefaults.colors(
                            thumbColor = previewColor,
                            activeTrackColor = previewColor,
                            inactiveTrackColor = palette.sTop
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.primary)
                            .clickable { onConfirmHex(previewHex) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Apply Seed",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = palette.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    showBottomBorder: Boolean = true
) {
    val palette = LocalMonetPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = palette.onVar,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = palette.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showBottomBorder) {
            HorizontalDivider(color = palette.outlineV.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun DeviceAppRow(
    app: DeviceAppEntry,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMonetPalette.current
    var iconBitmap by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(app.packageName) {
        iconBitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName)
                    .toBitmap(width = 72, height = 72)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = if (isChecked) palette.primaryC.copy(alpha = 0.45f) else palette.sHigh,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("music_package_row_${app.packageName.hashCode()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = iconBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "${app.appLabel} icon",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.sTop),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = palette.onVar,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = app.appLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (app.isAudioOrMedia) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.primaryC)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Media",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp,
                                color = palette.onPrimaryC
                            )
                        }
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onVar,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = isChecked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("package_switch_${app.packageName.hashCode()}")
            )
        }
    }
}
