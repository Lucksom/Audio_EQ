package com.audioeq.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.audioeq.audio.AutoEqEntry
import com.audioeq.model.EngineMode
import com.audioeq.model.HeadphoneProfile
import com.audioeq.model.OperatingMode
import com.audioeq.model.OutputRoute
import com.audioeq.model.PresetConfig
import com.audioeq.shizuku.ShizukuState
import kotlin.math.roundToInt
import com.audioeq.ui.theme.JetBrainsMonoFontFamily
import com.audioeq.ui.theme.LocalMonetPalette
import com.audioeq.ui.theme.SpaceGroteskFontFamily
import java.util.Locale

@Composable
fun TopBarHeader(
    masterEnabled: Boolean,
    activePresetName: String,
    selectedMode: EngineMode,
    shizukuState: ShizukuState = ShizukuState.NOT_RUNNING,
    shizukuPrivilege: String = "",
    rootAvailable: Boolean = false,
    onToggleMaster: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current

    val masterBg by animateColorAsState(
        targetValue = if (masterEnabled) palette.primary else palette.sHigh,
        animationSpec = tween(200),
        label = "masterBg"
    )
    val masterTint by animateColorAsState(
        targetValue = if (masterEnabled) palette.onPrimary else palette.onVar,
        animationSpec = tween(200),
        label = "masterTint"
    )
    val masterCorner by animateDpAsState(
        targetValue = if (masterEnabled) 23.dp else 16.dp,
        animationSpec = tween(220),
        label = "masterCorner"
    )

    val modeStatusLabel = when (selectedMode) {
        EngineMode.ROOT -> when {
            rootAvailable && shizukuPrivilege.contains("Shizuku", ignoreCase = true) -> "Shizuku (Su)"
            rootAvailable -> "Root (Su)"
            shizukuState == ShizukuState.NEEDS_PERMISSION -> "Root (Grant)"
            else -> "Root"
        }
        EngineMode.ADB_SHIZUKU -> when {
            shizukuState == ShizukuState.READY && shizukuPrivilege.contains("uid=0") -> "Shizuku (Su)"
            shizukuState == ShizukuState.READY -> "Shizuku (Shell)"
            shizukuState == ShizukuState.NEEDS_PERMISSION || shizukuState == ShizukuState.DENIED_PERMANENTLY -> "Shizuku (Grant)"
            else -> "ADB / Shizuku"
        }
        EngineMode.LEGACY -> "Legacy"
    }

    val isModePrivilegeReady = when (selectedMode) {
        EngineMode.ROOT -> rootAvailable
        EngineMode.ADB_SHIZUKU -> shizukuState == ShizukuState.READY
        EngineMode.LEGACY -> true
    }

    val subtitleText = if (masterEnabled) {
        "$activePresetName · $modeStatusLabel"
    } else {
        "All features are off"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(
                        elevation = if (masterEnabled) 8.dp else 0.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = palette.glow,
                        spotColor = palette.glow
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.primaryC),
                contentAlignment = Alignment.Center
            ) {
                val iconColor = palette.onPrimaryC
                Canvas(modifier = Modifier.size(24.dp)) {
                    val w = size.width
                    val h = size.height
                    val strokeW = 2.2.dp.toPx()

                    val x1 = w * (6f / 24f)
                    val x2 = w * (12f / 24f)
                    val x3 = w * (18f / 24f)
                    drawLine(
                        color = iconColor,
                        start = Offset(x1, h * (4f / 24f)),
                        end = Offset(x1, h * (20f / 24f)),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(x2, h * (4f / 24f)),
                        end = Offset(x2, h * (20f / 24f)),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(x3, h * (4f / 24f)),
                        end = Offset(x3, h * (20f / 24f)),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = iconColor,
                        radius = 2.5.dp.toPx(),
                        center = Offset(x1, h * (9f / 24f))
                    )
                    drawCircle(
                        color = iconColor,
                        radius = 2.5.dp.toPx(),
                        center = Offset(x2, h * (15f / 24f))
                    )
                    drawCircle(
                        color = iconColor,
                        radius = 2.5.dp.toPx(),
                        center = Offset(x3, h * (11f / 24f))
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Audio EQ",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    letterSpacing = (-0.5).sp,
                    color = palette.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onVar,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("top_subtitle_text")
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.sHigh)
                    .border(1.dp, palette.outlineV, RoundedCornerShape(999.dp))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("engine_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dotColor = when {
                    !masterEnabled -> palette.outline
                    isModePrivilegeReady -> Color(0xFF6DD58C)
                    else -> Color(0xFFFFB74D)
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .shadow(
                            elevation = if (masterEnabled) 6.dp else 0.dp,
                            shape = CircleShape,
                            ambientColor = dotColor,
                            spotColor = dotColor
                        )
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = modeStatusLabel,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = palette.onVar,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(
                        elevation = if (masterEnabled) 8.dp else 0.dp,
                        shape = RoundedCornerShape(masterCorner),
                        ambientColor = palette.glow,
                        spotColor = palette.glow
                    )
                    .clip(RoundedCornerShape(masterCorner))
                    .background(masterBg)
                    .border(
                        width = 1.dp,
                        color = if (masterEnabled) Color.Transparent else palette.outlineV,
                        shape = RoundedCornerShape(masterCorner)
                    )
                    .clickable { onToggleMaster(!masterEnabled) }
                    .testTag("top_power_icon_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = if (masterEnabled) {
                        "Master power on (disable Equalizer and all effects)"
                    } else {
                        "Master power off (enable Equalizer and all effects)"
                    },
                    tint = masterTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.sHigh)
                    .border(1.dp, palette.outlineV, RoundedCornerShape(16.dp))
                    .clickable { onOpenSettings() }
                    .testTag("settings_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings & Diagnostics",
                    tint = palette.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun StatusBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.secondaryC)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("status_feedback_banner"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = palette.onSecondaryC,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onDismiss() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("banner_dismiss_button")
        ) {
            Text(
                text = "Dismiss",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = palette.primary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetsCard(
    masterEnabled: Boolean = true,
    presets: List<PresetConfig>,
    selectedPresetId: String,
    activePresetName: String,
    onSelectPreset: (PresetConfig) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = palette.outlineV.copy(alpha = 0.55f),
                shape = RoundedCornerShape(28.dp)
            )
            .testTag("preset_card"),
        shape = RoundedCornerShape(28.dp),
        color = palette.sCont.copy(alpha = if (masterEnabled) 1f else 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PRESETS",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.3.sp,
                    color = palette.onVar
                )
                Text(
                    text = activePresetName,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = palette.primary,
                    modifier = Modifier.testTag("preset_tag")
                )
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("preset_chip_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = preset.id == selectedPresetId
                    val chipBg by animateColorAsState(
                        targetValue = if (isSelected) palette.secondaryC else palette.sHigh,
                        animationSpec = tween(160),
                        label = "presetChipBg_${preset.id}"
                    )
                    val chipContent by animateColorAsState(
                        targetValue = if (isSelected) palette.onSecondaryC else palette.onSurface,
                        animationSpec = tween(160),
                        label = "presetChipContent_${preset.id}"
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipBg)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else palette.outlineV,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = masterEnabled) { onSelectPreset(preset) }
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                            .testTag("preset_chip_${preset.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset.name,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = chipContent,
                            maxLines = 1
                        )
                        if (!preset.isBuiltIn) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete preset ${preset.name}",
                                tint = chipContent.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onDeleteCustomPreset(preset.id) }
                                    .testTag("delete_preset_${preset.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutoEqCard(
    masterEnabled: Boolean = true,
    activeRoute: OutputRoute,
    connectedProductName: String?,
    engineMode: EngineMode,
    operatingMode: OperatingMode,
    headphoneProfiles: List<HeadphoneProfile>,
    selectedHeadphoneProfileId: String,
    preampDb: Float,
    autoEqIndex: List<AutoEqEntry>,
    suggestedAutoEqEntry: AutoEqEntry?,
    autoEqClipped: Boolean,
    autoEqUseTenBandSliders: Boolean,
    onApplyAutoEqEntry: (AutoEqEntry) -> Unit,
    onToggleUseTenBandSliders: (Boolean) -> Unit,
    onClearAutoEq: () -> Unit,
    onDismissSuggestion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    var searchQuery by remember { mutableStateOf("") }

    val selectedProfile = headphoneProfiles.find { it.id == selectedHeadphoneProfileId }
    val isProfileActive = selectedProfile != null && selectedProfile.id != "none"

    val trimmedQuery = remember(searchQuery) { searchQuery.trim() }
    val normalizedQuery = remember(trimmedQuery) {
        trimmedQuery.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
    }
    val matchingEntries = remember(autoEqIndex, trimmedQuery, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            emptyList()
        } else {
            val tokens = trimmedQuery.lowercase(Locale.US)
                .split(Regex("[^a-z0-9]+"))
                .filter { it.isNotEmpty() }
            val rawLower = trimmedQuery.lowercase(Locale.US)

            autoEqIndex.asSequence()
                .filter { entry ->
                    val nameNorm = entry.name.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
                    if (nameNorm.contains(normalizedQuery)) {
                        true
                    } else {
                        val fLower = entry.form.lowercase(Locale.US)
                        val catKeywords = when {
                            fLower.contains("in-ear") || fLower.contains("711") || fLower.contains("5128") ->
                                "in-ear inear iem earphone earphones earbud earbuds buds"
                            fLower.contains("earbud") ->
                                "earbud earbuds tws earphone earphones buds"
                            else ->
                                "over-ear overear headphone headphones on-ear"
                        }
                        val searchable = "${entry.name.lowercase(Locale.US)} ${entry.source.lowercase(Locale.US)} $fLower $catKeywords"
                        tokens.all { token -> searchable.contains(token) }
                    }
                }
                .sortedWith(
                    compareBy(
                        {
                            val nameNorm = it.nameNorm()
                            val nameLower = it.name.lowercase(Locale.US)
                            when {
                                nameNorm == normalizedQuery -> 0
                                nameNorm.startsWith(normalizedQuery) -> 1
                                nameLower.contains(rawLower) -> 2
                                nameNorm.contains(normalizedQuery) -> 3
                                else -> 4
                            }
                        },
                        {
                            when (it.source.lowercase(Locale.US)) {
                                "oratory1990" -> 0
                                "crinacle" -> 1
                                "rtings" -> 2
                                else -> 3
                            }
                        },
                        { it.name.length }
                    )
                )
                .take(20)
                .toList()
        }
    }

    val modeFileDescription = when {
        autoEqUseTenBandSliders -> "10-Band Sliders · FixedBandEQ.txt"
        engineMode == EngineMode.ROOT -> "Root Mode · ParametricEQ.txt (Exact Biquads)"
        operatingMode == OperatingMode.LEGACY_TIER1_EQ -> "Legacy 5-Band EQ · ParametricEQ.txt (Low accuracy)"
        else -> "${engineMode.shortTitle} · ParametricEQ.txt (31-Band DP)"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isProfileActive) {
                    palette.primary.copy(alpha = 0.55f)
                } else {
                    palette.outlineV.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(28.dp)
            )
            .testTag("autoeq_main_card"),
        shape = RoundedCornerShape(28.dp),
        color = palette.sCont.copy(alpha = if (masterEnabled) 1f else 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.primaryC),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "AutoEq",
                            tint = palette.onPrimaryC,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AUTOEQ · ${activeRoute.label}".uppercase(Locale.US),
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.1.sp,
                            color = palette.onVar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isProfileActive && selectedProfile != null) {
                                "${selectedProfile.name} (${selectedProfile.source}) · Preamp ${String.format(Locale.US, "%.1f", preampDb)} dB"
                            } else {
                                modeFileDescription
                            },
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = if (isProfileActive) palette.primary else palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("autoeq_active_name")
                        )
                    }
                }

                if (isProfileActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.sHigh)
                            .clickable(enabled = masterEnabled) { onClearAutoEq() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("autoeq_clear_button")
                    ) {
                        Text(
                            text = "Clear",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = palette.onVar
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!autoEqUseTenBandSliders) palette.primary else palette.sHigh)
                        .clickable(enabled = masterEnabled) { onToggleUseTenBandSliders(false) }
                        .padding(vertical = 7.dp)
                        .testTag("autoeq_mode_parametric"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ParametricEQ (Engine)",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = if (!autoEqUseTenBandSliders) palette.onPrimary else palette.onVar,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (autoEqUseTenBandSliders) palette.primary else palette.sHigh)
                        .clickable(enabled = masterEnabled) { onToggleUseTenBandSliders(true) }
                        .padding(vertical = 7.dp)
                        .testTag("autoeq_mode_fixedband"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "FixedBandEQ (10 Sliders)",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = if (autoEqUseTenBandSliders) palette.onPrimary else palette.onVar,
                        maxLines = 1
                    )
                }
            }

            if (operatingMode == OperatingMode.LEGACY_TIER1_EQ) {
                Text(
                    text = "Native 5-band Equalizer active: AutoEq accuracy is low.",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 11.sp,
                    color = palette.tertiary,
                    modifier = Modifier.testTag("autoeq_low_accuracy_note")
                )
            }

            if (autoEqClipped) {
                Text(
                    text = "Note: One or more bands exceeded ±12 dB and were clamped; negative preamp applied.",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 11.sp,
                    color = palette.primary,
                    modifier = Modifier.testTag("autoeq_clipped_note")
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                enabled = masterEnabled,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Search earphones, earbuds, or headphones",
                        fontSize = 12.sp,
                        color = palette.onVar.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search AutoEq",
                        tint = palette.onVar,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = palette.onVar,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.primary,
                    unfocusedBorderColor = palette.outlineV,
                    focusedContainerColor = palette.sHigh.copy(alpha = 0.55f),
                    unfocusedContainerColor = palette.sHigh.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("autoeq_search_box")
            )

            if (normalizedQuery.isNotEmpty()) {
                if (matchingEntries.isEmpty()) {
                    Text(
                        text = "No matching earphones or headphones found for \"$trimmedQuery\"",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 11.5.sp,
                        color = palette.onVar,
                        modifier = Modifier.testTag("autoeq_empty_results")
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        matchingEntries.forEach { entry ->
                            val isCurrent = selectedProfile?.name.equals(entry.name, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCurrent) palette.secondaryC.copy(alpha = 0.55f) else palette.sHigh)
                                    .border(
                                        width = 1.dp,
                                        color = if (isCurrent) palette.primary else palette.outlineV.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("autoeq_row_${entry.name.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = palette.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.source} · ${entry.form}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.5.sp,
                                        color = palette.onVar,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isCurrent) palette.primary else palette.primaryC)
                                        .clickable(enabled = masterEnabled) { onApplyAutoEqEntry(entry) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("autoeq_use_${entry.name.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")}")
                                ) {
                                    Text(
                                        text = if (isCurrent) "Active" else "Use this",
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp,
                                        color = if (isCurrent) palette.onPrimary else palette.onPrimaryC
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = if (autoEqIndex.isNotEmpty()) {
                    "AutoEq Repository (${autoEqIndex.size} models) · Type in search above to select your device"
                } else {
                    "AutoEq Repository · Type in search above to select your device"
                },
                fontSize = 10.sp,
                color = palette.onVar.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun AutoEqEntry.nameNorm(): String =
    name.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")


