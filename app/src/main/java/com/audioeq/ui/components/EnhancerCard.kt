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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioeq.audio.AudioProfile
import com.audioeq.audio.EnhancerConfig
import com.audioeq.audio.IeqPreset
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
    onSelectIeqPreset: (IeqPreset) -> Unit = {},
    onIeqAmountChange: (Int) -> Unit = {},
    onToggleDialogue: (Boolean) -> Unit = {},
    onDialogueAmountChange: (Int) -> Unit = {},
    onToggleSurround: (Boolean) -> Unit = {},
    onSurroundWidthChange: (Float) -> Unit = {},
    onResetAll: () -> Unit = {}
) {
    val palette = LocalMonetPalette.current
    var showDeepBassMath by remember { mutableStateOf(false) }
    var showVirtualBassInfo by remember { mutableStateOf(false) }

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
                    Column {
                        Text(
                            text = "Audio Enhancers",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                        )
                        Text(
                            text = "Adaptive profiles · Deep Bass · Virtual Bass · 20-Band IEQ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                if (config.isAnyActive()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.surface2,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled) { onResetAll() }
                            .testTag("enhancer_reset_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Enhancers",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
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

            val deepBassActive = config.deepBassEnabled || config.profile == AudioProfile.DEEP_BASS
            EnhancerModuleContainer(
                icon = Icons.Default.GraphicEq,
                title = "Deep Bass",
                subtitle = "DC low-shelf sub-bass filter & 10-band downward bass shift",
                badgeText = if (deepBassActive) "DC SHELF" else "STD PEAK",
                checked = deepBassActive,
                enabled = enabled,
                onCheckedChange = onToggleDeepBass,
                testTag = "enhancer_deep_bass_switch"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shifts Bands 0–9 downward by one step for extended sub-bass",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (showDeepBassMath) "Hide info" else "How it works",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showDeepBassMath = !showDeepBassMath }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("deep_bass_info_toggle")
                        )
                    }

                    AnimatedVisibility(
                        visible = showDeepBassMath,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = palette.surface2.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, palette.borderSubtle)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Normal vs. Deep Bass:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primary
                                    )
                                )
                                Text(
                                    text = "• Normal Default Bass: Band 0 uses a standard peaking filter whose boost rolls off in the lowest sub-bass range toward 0 dB.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = palette.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = "• Deep Bass: Shifts the first 10 bands down by one step so Band 0 becomes a DC Low-Shelf filter (Q=0.7071), keeping full sub-bass boost across the lowest octaves without roll-off.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = palette.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            EnhancerModuleContainer(
                icon = Icons.Default.Speaker,
                title = "Bass Enhancer",
                subtitle = "Butterworth low-shelf + sub-bass punch bell (zero mid/treble volume suppression)",
                badgeText = if (config.bassEnabled) {
                    String.format(Locale.US, "+%.1f dB", config.bassBoostDb)
                } else "OFF",
                checked = config.bassEnabled,
                enabled = enabled,
                onCheckedChange = onToggleBass,
                testTag = "enhancer_bass_switch"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Low-Shelf & Punch Gain",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.textSecondary)
                        )
                        Text(
                            text = String.format(Locale.US, "+%.1f / +6.0 dB", config.bassBoostDb),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = palette.primary
                            )
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
                        modifier = Modifier.testTag("enhancer_bass_slider")
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showVirtualBassInfo = !showVirtualBassInfo }
                                .padding(vertical = 2.dp)
                                .testTag("virtual_bass_info_toggle")
                        ) {
                            Text(
                                text = "Harmonic Overtones (2× / 3× / 4×)",
                                style = MaterialTheme.typography.labelSmall.copy(color = palette.textSecondary)
                            )
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Virtual Bass info",
                                tint = palette.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (showVirtualBassInfo) "Hide info" else "What is this?",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                )
                            )
                        }
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
                            )
                        )
                    }

                    AnimatedVisibility(
                        visible = showVirtualBassInfo,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = palette.surface2.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, palette.borderSubtle)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Why Speaker Turns Virtual Bass ON & Headphones Turn It OFF:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primary
                                    )
                                )
                                Text(
                                    text = "• Missing Fundamental: Deep sub-bass that small phone speakers physically cannot reproduce without clipping.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = palette.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = "• Harmonic Series (H2, H3, H4): 2×, 3×, and 4× overtones that let your ear perceive the deep sub-bass pitch on small speakers.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = palette.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
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
                        modifier = Modifier.testTag("enhancer_virtual_bass_slider")
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = palette.surface1,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (config.ieqPreset != IeqPreset.OFF) palette.primary.copy(alpha = 0.5f) else palette.borderSubtle
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Intelligent EQ",
                                tint = if (config.ieqPreset != IeqPreset.OFF) palette.primary else palette.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Intelligent EQ (20-Band IEQ)",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary
                                    )
                                )
                                Text(
                                    text = config.ieqPreset.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = palette.textMuted,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (config.ieqPreset != IeqPreset.OFF) palette.primaryContainer else palette.surface2
                        ) {
                            Text(
                                text = if (config.ieqPreset != IeqPreset.OFF) {
                                    "μ=${String.format(Locale.US, "%.2f", config.ieqAmount / 16f)}"
                                } else "OFF",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (config.ieqPreset != IeqPreset.OFF) palette.onPrimaryContainer else palette.textMuted
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IeqPreset.entries.forEach { preset ->
                            val selected = config.ieqPreset == preset
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) palette.primary else palette.surface2,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) palette.primary else palette.borderSubtle
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = enabled) { onSelectIeqPreset(preset) }
                                    .testTag("ieq_chip_${preset.id}")
                            ) {
                                Text(
                                    text = preset.label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) palette.onPrimary else palette.textSecondary
                                    )
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = config.ieqPreset != IeqPreset.OFF,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Target Steering Strength (Amount 1–16)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = palette.textSecondary)
                                )
                                Text(
                                    text = "${config.ieqAmount} / 16 (${(config.ieqAmount * 100) / 16}%)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primary
                                    )
                                )
                            }
                            Slider(
                                value = config.ieqAmount.toFloat(),
                                onValueChange = { onIeqAmountChange(it.roundToInt().coerceIn(1, 16)) },
                                valueRange = 1f..16f,
                                steps = 14,
                                enabled = enabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.primary,
                                    activeTrackColor = palette.primary,
                                    inactiveTrackColor = palette.surface2
                                ),
                                modifier = Modifier.testTag("enhancer_ieq_amount_slider")
                            )
                        }
                    }
                }
            }

            EnhancerModuleContainer(
                icon = Icons.Default.RecordVoiceOver,
                title = "Dialogue Enhancer",
                subtitle = "Vocal formant clarity boost + low-mid mud unmasking",
                badgeText = if (config.dialogueEnhancerEnabled) {
                    String.format(Locale.US, "+%.1f dB", (config.dialogueAmount / 16f) * 10f)
                } else "OFF",
                checked = config.dialogueEnhancerEnabled,
                enabled = enabled,
                onCheckedChange = onToggleDialogue,
                testTag = "enhancer_dialogue_switch"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Speech Intelligibility Boost",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.textSecondary)
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
                            )
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
                        modifier = Modifier.testTag("enhancer_dialogue_slider")
                    )
                }
            }

            EnhancerModuleContainer(
                icon = if (config.surroundEnabled) Icons.Default.SurroundSound else Icons.Default.SpatialAudioOff,
                title = "3D Surround Virtualizer",
                subtitle = "Binaural HRTF pinna contour & M/S crossfeed (sub-bass phase locked)",
                badgeText = if (config.surroundEnabled) {
                    "${(config.surroundWidth * 100).roundToInt()}%"
                } else "OFF",
                checked = config.surroundEnabled,
                enabled = enabled,
                onCheckedChange = onToggleSurround,
                testTag = "enhancer_surround_switch"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Soundstage Width",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.textSecondary)
                        )
                        Text(
                            text = "${(config.surroundWidth * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = palette.primary
                            )
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
                        modifier = Modifier.testTag("enhancer_surround_slider")
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
    content: @Composable () -> Unit
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
