package com.audioeq.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SpatialAudioOff
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioeq.audio.AudioProfile
import com.audioeq.audio.EnhancerConfig
import com.audioeq.audio.SubwooferEnclosure
import com.audioeq.audio.SubwooferRoomMode
import com.audioeq.model.OutputRoute
import com.audioeq.ui.theme.LocalMonetPalette
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EnhancerCard(
    config: EnhancerConfig,
    activeRoute: OutputRoute = OutputRoute.SPEAKER,
    enabled: Boolean = true,
    onSelectProfile: (AudioProfile) -> Unit = {},
    onToggleDeepBass: (Boolean) -> Unit = {},
    onToggleBass: (Boolean) -> Unit = {},
    onBassBoostChange: (Float) -> Unit = {},
    onToggleVirtualBass: (Boolean) -> Unit = {},
    onVirtualBassStrengthChange: (Float) -> Unit = {},
    onToggleDialogue: (Boolean) -> Unit = {},
    onDialogueAmountChange: (Int) -> Unit = {},
    onToggleSurround: (Boolean) -> Unit = {},
    onSurroundWidthChange: (Float) -> Unit = {},
    onResetAll: () -> Unit = {}
) {
    val palette = LocalMonetPalette.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("enhancer_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = palette.bgCard),
        border = BorderStroke(1.dp, palette.borderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Audio Enhancers",
                            tint = palette.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Audio Enhancers",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Adaptive profiles · Deep Bass · Bass Enhancer · Virtual Bass",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.textMuted,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (config.isAnyActive()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = palette.surface2,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(enabled = enabled) { onResetAll() }
                            .testTag("enhancer_reset_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Enhancers",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = palette.surface1,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (config.profile != AudioProfile.OFF) palette.primary.copy(alpha = 0.55f) else palette.borderSubtle
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Audio Profiles",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary
                                    )
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = palette.primaryContainer
                                ) {
                                    Text(
                                        text = activeRoute.label,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.onPrimaryContainer,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                            Text(
                                text = config.profile.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioProfile.entries.forEach { prof ->
                            val selected = config.profile == prof
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) palette.primary else palette.surface2,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) palette.primary else palette.borderSubtle
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = enabled) { onSelectProfile(prof) }
                                    .testTag("profile_chip_${prof.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = prof.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) palette.onPrimary else palette.textPrimary
                                        )
                                    )
                                    Text(
                                        text = prof.badge,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            color = if (selected) palette.onPrimary.copy(alpha = 0.85f) else palette.textMuted
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val deepBassActive = config.deepBassEnabled
            EnhancerModuleContainer(
                icon = Icons.Default.GraphicEq,
                title = "Deep Bass",
                subtitle = "Resonant sub-bass shelf & dynamic kick envelope booster",
                badgeText = if (deepBassActive) "ON" else "OFF",
                checked = deepBassActive,
                enabled = enabled,
                onCheckedChange = onToggleDeepBass,
                testTag = "enhancer_deep_bass_switch"
            )

            EnhancerModuleContainer(
                icon = Icons.Default.Speaker,
                title = "Bass Enhancer",
                subtitle = "Low-shelf punch & dynamic transient boost",
                badgeText = if (config.bassEnabled) {
                    String.format(Locale.US, "+%.1f dB", config.bassBoostDb)
                } else "OFF",
                checked = config.bassEnabled,
                enabled = enabled,
                onCheckedChange = onToggleBass,
                testTag = "enhancer_bass_switch"
            ) {
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
                            text = "Low-Shelf & Punch Gain",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format(Locale.US, "+%.1f / +6.0 dB", config.bassBoostDb),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = palette.primary
                            ),
                            maxLines = 1
                        )
                    }
                    Slider(
                        value = config.bassBoostDb.coerceIn(0f, 6f),
                        onValueChange = onBassBoostChange,
                        valueRange = 0f..6f,
                        enabled = enabled && config.bassEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.primary,
                            activeTrackColor = palette.primary,
                            inactiveTrackColor = palette.surface2
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("enhancer_bass_slider")
                    )
                }
            }

            EnhancerModuleContainer(
                icon = Icons.Default.Headphones,
                title = "Virtual Bass Synthesizer",
                subtitle = "Psychoacoustic sub-bass perception for small speakers & earbuds",
                badgeText = if (config.virtualBassEnabled) {
                    "${(config.virtualBassStrength * 100).roundToInt()}%"
                } else "OFF",
                checked = config.virtualBassEnabled,
                enabled = enabled,
                onCheckedChange = onToggleVirtualBass,
                testTag = "enhancer_virtual_bass_switch"
            ) {
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
                            text = "Harmonic Overtones",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%d%% (+%.1f dB)",
                                (config.virtualBassStrength * 100).roundToInt(),
                                config.virtualBassStrength * 6.5f
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = palette.primary
                            ),
                            maxLines = 1
                        )
                    }

                    Slider(
                        value = config.virtualBassStrength.coerceIn(0f, 1f),
                        onValueChange = onVirtualBassStrengthChange,
                        valueRange = 0f..1f,
                        enabled = enabled && config.virtualBassEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.primary,
                            activeTrackColor = palette.primary,
                            inactiveTrackColor = palette.surface2
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("enhancer_virtual_bass_slider")
                    )
                }
            }

            EnhancerModuleContainer(
                icon = Icons.Default.RecordVoiceOver,
                title = "Dialogue Enhancer",
                subtitle = "Vocal formant clarity boost & low-mid mud unmasking",
                badgeText = if (config.dialogueEnhancerEnabled) {
                    String.format(Locale.US, "+%.1f dB", (config.dialogueAmount / 16f) * 10f)
                } else "OFF",
                checked = config.dialogueEnhancerEnabled,
                enabled = enabled,
                onCheckedChange = onToggleDialogue,
                testTag = "enhancer_dialogue_switch"
            ) {
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
                            text = "Speech Intelligibility Boost",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "Step %d/16 (+%.2f dB)",
                                config.dialogueAmount,
                                (config.dialogueAmount / 16f) * 10f
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = palette.primary
                            ),
                            maxLines = 1
                        )
                    }
                    Slider(
                        value = config.dialogueAmount.toFloat(),
                        onValueChange = { onDialogueAmountChange(it.roundToInt().coerceIn(1, 16)) },
                        valueRange = 1f..16f,
                        steps = 14,
                        enabled = enabled && config.dialogueEnhancerEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.primary,
                            activeTrackColor = palette.primary,
                            inactiveTrackColor = palette.surface2
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("enhancer_dialogue_slider")
                    )
                }
            }

            EnhancerModuleContainer(
                icon = if (config.surroundEnabled) Icons.Default.SurroundSound else Icons.Default.SpatialAudioOff,
                title = "3D Surround Virtualizer",
                subtitle = "Binaural spatial soundstage & crossfeed",
                badgeText = if (config.surroundEnabled) {
                    "${(config.surroundWidth * 100).roundToInt()}%"
                } else "OFF",
                checked = config.surroundEnabled,
                enabled = enabled,
                onCheckedChange = onToggleSurround,
                testTag = "enhancer_surround_switch"
            ) {
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
                            text = "Soundstage Width",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(config.surroundWidth * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = palette.primary
                            ),
                            maxLines = 1
                        )
                    }
                    Slider(
                        value = config.surroundWidth.coerceIn(0f, 1f),
                        onValueChange = onSurroundWidthChange,
                        valueRange = 0f..1f,
                        enabled = enabled && config.surroundEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.primary,
                            activeTrackColor = palette.primary,
                            inactiveTrackColor = palette.surface2
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("enhancer_surround_slider")
                    )
                }
            }
        }
    }
}

@Composable
private fun EnhancerModuleContainer(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    content: (@Composable () -> Unit)? = null
) {
    val palette = LocalMonetPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = palette.surface1,
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) palette.primary.copy(alpha = 0.5f) else palette.borderSubtle
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (checked) palette.primary else palette.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (checked) palette.primaryContainer else palette.surface2
                            ) {
                                Text(
                                    text = badgeText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (checked) palette.onPrimaryContainer else palette.textMuted,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.textMuted,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.onPrimary,
                        checkedTrackColor = palette.primary,
                        uncheckedThumbColor = palette.textMuted,
                        uncheckedTrackColor = palette.surface2
                    ),
                    modifier = Modifier.testTag(testTag)
                )
            }

            if (content != null) {
                AnimatedVisibility(
                    visible = checked,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun SubwooferCard(
    config: EnhancerConfig,
    enabled: Boolean = true,
    liveSpectrum31: List<Float> = emptyList(),
    gainReduction6: List<Float> = emptyList(),
    onToggleSubwoofer: (Boolean) -> Unit = {},
    onSubwooferIntensityChange: (Float) -> Unit = {},
    onSubwooferSubOctaveChange: (Float) -> Unit = {},
    onToggleSubwooferAutoLoudness: (Boolean) -> Unit = {},
    onSelectEnclosure: (SubwooferEnclosure) -> Unit = {},
    onCrossoverHzChange: (Float) -> Unit = {},
    onPortHzChange: (Float) -> Unit = {},
    onDampingChange: (Float) -> Unit = {},
    onPhaseDegChange: (Float) -> Unit = {},
    onWarmthChange: (Float) -> Unit = {},
    onSubsonicHzChange: (Float) -> Unit = {},
    onToggleVocalPriority: (Boolean) -> Unit = {},
    onSelectRoomMode: (SubwooferRoomMode) -> Unit = {}
) {
    val palette = LocalMonetPalette.current
    val subActive = config.subwooferEnabled

    val subPeakDbfs = remember(liveSpectrum31, subActive, config.subwooferIntensity) {
        if (!subActive) {
            -60.0f
        } else {
            val rawSubPeak = (0..7).maxOfOrNull { idx -> liveSpectrum31.getOrElse(idx) { -54.0f } } ?: -42.0f
            (rawSubPeak + config.subwooferIntensity * 8.0f).coerceIn(-60.0f, 0.0f)
        }
    }
    val excursionAbsorbedDb = remember(gainReduction6, subActive, config.subwooferSubsonicHz, config.subwooferIntensity) {
        if (!subActive) {
            0.0f
        } else {
            val mbcGr = kotlin.math.abs(gainReduction6.getOrElse(0) { 0f })
            val guardDb = ((config.subwooferSubsonicHz - 15f) * 0.22f + config.subwooferIntensity * 1.4f + mbcGr)
            guardDb.coerceIn(0.4f, 12.0f)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subwoofer_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = palette.bgCard),
        border = BorderStroke(
            width = 1.dp,
            color = if (subActive) palette.primary.copy(alpha = 0.55f) else palette.borderSubtle
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (subActive) palette.primaryContainer else palette.surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = "Subwoofer Engine",
                            tint = if (subActive) palette.onPrimaryContainer else palette.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Subwoofer",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (subActive) palette.primaryContainer else palette.surface2
                            ) {
                                Text(
                                    text = if (subActive) {
                                        "${(config.subwooferIntensity * 100).roundToInt()}% SLAM"
                                    } else "OFF",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (subActive) palette.onPrimaryContainer else palette.textMuted,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            text = "${config.subwooferEnclosure.label} · ${config.subwooferRoomMode.label}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.textMuted,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = subActive,
                    onCheckedChange = onToggleSubwoofer,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.onPrimary,
                        checkedTrackColor = palette.primary,
                        uncheckedThumbColor = palette.textMuted,
                        uncheckedTrackColor = palette.surface2
                    ),
                    modifier = Modifier.testTag("subwoofer_master_switch")
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subwoofer_telemetry_strip"),
                shape = RoundedCornerShape(14.dp),
                color = palette.surface2.copy(alpha = if (subActive) 0.9f else 0.5f),
                border = BorderStroke(1.dp, if (subActive) palette.primary.copy(alpha = 0.35f) else palette.borderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "SUB RMS / PEAK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = palette.textMuted,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (subActive) String.format(Locale.US, "%.1f dB", subPeakDbfs) else "STANDBY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (subActive) palette.primary else palette.textMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SUB-OCTAVE LOCK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = palette.textMuted,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (subActive && config.subwooferSubOctaveMix > 0.01f) {
                                "${(config.subwooferSubOctaveMix * 100).roundToInt()}% ACTIVE"
                            } else "BYPASS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (subActive && config.subwooferSubOctaveMix > 0.01f) palette.textPrimary else palette.textMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "EXCURSION GUARD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = palette.textMuted,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (subActive) {
                                String.format(Locale.US, "-%.1f dB", excursionAbsorbedDb)
                            } else "IDLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (subActive) palette.primary else palette.textMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = palette.surface1,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (subActive) palette.primary.copy(alpha = 0.45f) else palette.borderSubtle
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                                text = "Cabinet Drive & Port Slam",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%d%% (+%.1f dB)",
                                    (config.subwooferIntensity * 100).roundToInt(),
                                    config.subwooferIntensity * 8.0f
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primary
                                ),
                                maxLines = 1
                            )
                        }
                        Slider(
                            value = config.subwooferIntensity.coerceIn(0f, 1.5f),
                            onValueChange = onSubwooferIntensityChange,
                            valueRange = 0f..1.5f,
                            enabled = enabled && subActive,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.primary,
                                activeTrackColor = palette.primary,
                                inactiveTrackColor = palette.surface2
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("subwoofer_intensity_slider")
                        )
                    }

                    HorizontalDivider(color = palette.borderSubtle.copy(alpha = 0.5f))

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
                                text = "Sub-Octave Synthesizer",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(config.subwooferSubOctaveMix * 100).roundToInt()}% Mix",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primary
                                ),
                                maxLines = 1
                            )
                        }
                        Slider(
                            value = config.subwooferSubOctaveMix.coerceIn(0f, 0.6f),
                            onValueChange = onSubwooferSubOctaveChange,
                            valueRange = 0f..0.6f,
                            enabled = enabled && subActive,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.primary,
                                activeTrackColor = palette.primary,
                                inactiveTrackColor = palette.surface2
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("subwoofer_sub_octave_slider")
                        )
                    }

                    HorizontalDivider(color = palette.borderSubtle.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Fletcher-Munson Equal-Loudness",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Automatically scales sub-port pressure at lower media volume",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = config.subwooferAutoLoudness,
                            onCheckedChange = onToggleSubwooferAutoLoudness,
                            enabled = enabled && subActive,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = palette.onPrimary,
                                checkedTrackColor = palette.primary,
                                uncheckedThumbColor = palette.textMuted,
                                uncheckedTrackColor = palette.surface2
                            ),
                            modifier = Modifier.testTag("subwoofer_loudness_switch")
                        )
                    }

                    HorizontalDivider(color = palette.borderSubtle.copy(alpha = 0.5f))

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
                                text = "Enclosure Alignment",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = config.subwooferEnclosure.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SubwooferEnclosure.entries.forEach { enc ->
                                val selected = config.subwooferEnclosure == enc
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) palette.primary else palette.surface2,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) palette.primary else palette.borderSubtle
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = enabled && subActive) { onSelectEnclosure(enc) }
                                        .testTag("subwoofer_enclosure_${enc.id}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = enc.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) palette.onPrimary else palette.textPrimary
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = enc.badge,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                color = if (selected) palette.onPrimary.copy(alpha = 0.85f) else palette.textMuted
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = palette.borderSubtle.copy(alpha = 0.5f))

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
                                text = "Low-Pass Crossover",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val lpfPct = (((config.subwooferCrossoverHz - 55f) / 65f) * 100f).roundToInt().coerceIn(0, 100)
                            Text(
                                text = "$lpfPct%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primary
                                )
                            )
                        }
                        Slider(
                            value = config.subwooferCrossoverHz.coerceIn(55f, 120f),
                            onValueChange = onCrossoverHzChange,
                            valueRange = 55f..120f,
                            enabled = enabled && subActive,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.primary,
                                activeTrackColor = palette.primary,
                                inactiveTrackColor = palette.surface2
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("subwoofer_crossover_slider")
                        )
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
                                text = "Port Tuning",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val portPct = (((config.subwooferPortHz - 28f) / 27f) * 100f).roundToInt().coerceIn(0, 100)
                            Text(
                                text = "$portPct%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primary
                                )
                            )
                        }
                        Slider(
                            value = config.subwooferPortHz.coerceIn(28f, 55f),
                            onValueChange = onPortHzChange,
                            valueRange = 28f..55f,
                            enabled = enabled && subActive,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.primary,
                                activeTrackColor = palette.primary,
                                inactiveTrackColor = palette.surface2
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("subwoofer_port_hz_slider")
                        )
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
                                text = "Cone Damping / Servo Tightness",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val dampingLabel = when {
                                config.subwooferDamping < 0.35f -> "Boomy (${(config.subwooferDamping * 100).roundToInt()}%)"
                                config.subwooferDamping > 0.70f -> "Servo Tight (${(config.subwooferDamping * 100).roundToInt()}%)"
                                else -> "Balanced (${(config.subwooferDamping * 100).roundToInt()}%)"
                            }
                            Text(
                                text = dampingLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primary
                                )
                            )
                        }
                        Slider(
                            value = config.subwooferDamping.coerceIn(0f, 1f),
                            onValueChange = onDampingChange,
                            valueRange = 0f..1f,
                            enabled = enabled && subActive,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.primary,
                                activeTrackColor = palette.primary,
                                inactiveTrackColor = palette.surface2
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("subwoofer_damping_slider")
                        )
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
                                text = "Phase Alignment",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val phasePct = ((config.subwooferPhaseDeg / 180f) * 100f).roundToInt().coerceIn(0, 100)
                            Text(
                                text = "$phasePct%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primary
                                )
                            )
                        }
                        Slider(
                            value = config.subwooferPhaseDeg.coerceIn(0f, 180f),
                            onValueChange = onPhaseDegChange,
                            valueRange = 0f..180f,
                            enabled = enabled && subActive,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.primary,
                                activeTrackColor = palette.primary,
                                inactiveTrackColor = palette.surface2
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("subwoofer_phase_slider")
                        )
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
                                text = "Harmonic Warmth / Tube Saturation",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(config.subwooferWarmth * 100).roundToInt()}% Drive",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primary
                                )
                            )
                        }
                        Slider(
                            value = config.subwooferWarmth.coerceIn(0f, 1f),
                            onValueChange = onWarmthChange,
                            valueRange = 0f..1f,
                            enabled = enabled && subActive,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.primary,
                                activeTrackColor = palette.primary,
                                inactiveTrackColor = palette.surface2
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("subwoofer_warmth_slider")
                        )
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
                                text = "Subsonic Excursion Guard",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val guardPct = (((config.subwooferSubsonicHz - 15f) / 20f) * 100f).roundToInt().coerceIn(0, 100)
                            Text(
                                text = "$guardPct%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primary
                                )
                            )
                        }
                        Slider(
                            value = config.subwooferSubsonicHz.coerceIn(15f, 35f),
                            onValueChange = onSubsonicHzChange,
                            valueRange = 15f..35f,
                            enabled = enabled && subActive,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.primary,
                                activeTrackColor = palette.primary,
                                inactiveTrackColor = palette.surface2
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("subwoofer_subsonic_slider")
                        )
                    }

                    HorizontalDivider(color = palette.borderSubtle.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Anti-IMD Vocal Priority",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Automatically ducks upper-bass when vocals are active",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = config.subwooferVocalPriority,
                            onCheckedChange = onToggleVocalPriority,
                            enabled = enabled && subActive,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = palette.onPrimary,
                                checkedTrackColor = palette.primary,
                                uncheckedThumbColor = palette.textMuted,
                                uncheckedTrackColor = palette.surface2
                            ),
                            modifier = Modifier.testTag("subwoofer_vocal_priority_switch")
                        )
                    }

                    HorizontalDivider(color = palette.borderSubtle.copy(alpha = 0.5f))

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
                                text = "Cabin Pressurization / Room Bloom",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = config.subwooferRoomMode.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SubwooferRoomMode.entries.forEach { mode ->
                                val selected = config.subwooferRoomMode == mode
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) palette.primary else palette.surface2,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) palette.primary else palette.borderSubtle
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = enabled && subActive) { onSelectRoomMode(mode) }
                                        .testTag("subwoofer_room_mode_${mode.id}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) palette.onPrimary else palette.textPrimary
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = mode.badge,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                color = if (selected) palette.onPrimary.copy(alpha = 0.85f) else palette.textMuted
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
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
}
