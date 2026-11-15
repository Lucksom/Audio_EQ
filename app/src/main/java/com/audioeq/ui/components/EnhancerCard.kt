package com.audioeq.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SpatialAudioOff
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioeq.audio.AudioProfile
import com.audioeq.audio.EnhancerConfig
import com.audioeq.model.OutputRoute
import com.audioeq.ui.theme.JetBrainsMonoFontFamily
import com.audioeq.ui.theme.LocalMonetPalette
import com.audioeq.ui.theme.SpaceGroteskFontFamily
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EnhancerCard(
    config: EnhancerConfig,
    activeRoute: OutputRoute = OutputRoute.SPEAKER,
    enabled: Boolean = true,
    useKnobs: Boolean = false,
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
            .alpha(if (enabled) 1f else 0.55f)
            .testTag("enhancer_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = palette.sCont),
        border = BorderStroke(1.dp, palette.outlineV),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.primaryC),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Audio Enhancers",
                            tint = palette.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Audio Enhancers",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (useKnobs) "Drag a knob to adjust" else "Tap an effect to adjust it",
                            fontSize = 13.sp,
                            color = palette.onVar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.sCont)
                        .border(1.dp, palette.outlineV, RoundedCornerShape(999.dp))
                        .clickable(enabled = enabled) { onResetAll() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("enhancer_reset_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Reset",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = palette.onSurface,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = "AUDIO PROFILE · ${activeRoute.label}".uppercase(Locale.US),
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
                color = palette.onVar
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioProfile.entries.forEach { prof ->
                    val selected = config.profile == prof
                    val cardBg by animateColorAsState(
                        targetValue = if (selected) palette.primary else palette.sCont,
                        animationSpec = tween(160),
                        label = "profCardBg_${prof.id}"
                    )
                    val cardBorder by animateColorAsState(
                        targetValue = if (selected) palette.primary else palette.outlineV,
                        animationSpec = tween(160),
                        label = "profCardBorder_${prof.id}"
                    )
                    val titleColor = if (selected) palette.onPrimary else palette.onSurface
                    val subColor = if (selected) palette.primaryC else palette.onVar

                    Column(
                        modifier = Modifier
                            .widthIn(min = 116.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                            .clickable(enabled = enabled) { onSelectProfile(prof) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("profile_chip_${prof.id}")
                    ) {
                        Text(
                            text = prof.label,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = titleColor,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = prof.badge,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = subColor,
                            maxLines = 1
                        )
                    }
                }
            }

            if (useKnobs) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val deepPct = if (config.deepBassEnabled) 100f else 0f
                        AudioRotaryKnob(
                            label = "Deep Bass",
                            valueText = if (config.deepBassEnabled) "100%" else "0%",
                            value = deepPct,
                            valueRange = 0f..100f,
                            step = 1f,
                            enabled = enabled,
                            onValueChange = { v -> onToggleDeepBass(v > 1f) },
                            testTag = "enhancer_deep_bass_switch",
                            modifier = Modifier.weight(1f)
                        )

                        val bassVal = if (config.bassEnabled) config.bassBoostDb.coerceIn(0f, 6f) else 0f
                        AudioRotaryKnob(
                            label = "Bass Enhancer",
                            valueText = String.format(Locale.US, "+%.1f dB", bassVal),
                            value = bassVal,
                            valueRange = 0f..6f,
                            step = 0.1f,
                            enabled = enabled,
                            onValueChange = { v ->
                                if (v <= 0.05f) {
                                    onToggleBass(false)
                                } else {
                                    onBassBoostChange(v)
                                }
                            },
                            testTag = "enhancer_bass_slider",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val vbPct = if (config.virtualBassEnabled) (config.virtualBassStrength * 100f).coerceIn(0f, 100f) else 0f
                        AudioRotaryKnob(
                            label = "Virtual Bass",
                            valueText = "${vbPct.roundToInt()}%",
                            value = vbPct,
                            valueRange = 0f..100f,
                            step = 1f,
                            enabled = enabled,
                            onValueChange = { pct ->
                                if (pct <= 0.5f) {
                                    onToggleVirtualBass(false)
                                } else {
                                    onVirtualBassStrengthChange(pct / 100f)
                                }
                            },
                            testTag = "enhancer_virtual_bass_slider",
                            modifier = Modifier.weight(1f)
                        )

                        val dlgVal = if (config.dialogueEnhancerEnabled) config.dialogueAmount.toFloat().coerceIn(0f, 16f) else 0f
                        AudioRotaryKnob(
                            label = "Dialogue Enhancer",
                            valueText = "${dlgVal.roundToInt()}",
                            value = dlgVal,
                            valueRange = 0f..16f,
                            step = 1f,
                            enabled = enabled,
                            onValueChange = { v ->
                                val stepVal = v.roundToInt().coerceIn(0, 16)
                                if (stepVal <= 0) {
                                    onToggleDialogue(false)
                                } else {
                                    onDialogueAmountChange(stepVal)
                                }
                            },
                            testTag = "enhancer_dialogue_slider",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val surPct = if (config.surroundEnabled) (config.surroundWidth * 100f).coerceIn(0f, 100f) else 0f
                        AudioRotaryKnob(
                            label = "3D Surround",
                            valueText = "${surPct.roundToInt()}%",
                            value = surPct,
                            valueRange = 0f..100f,
                            step = 1f,
                            enabled = enabled,
                            onValueChange = { pct ->
                                if (pct <= 0.5f) {
                                    onToggleSurround(false)
                                } else {
                                    onSurroundWidthChange(pct / 100f)
                                }
                            },
                            testTag = "enhancer_surround_slider",
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            } else {
                val deepBassActive = config.deepBassEnabled
                EnhancerModuleContainer(
                    icon = Icons.Default.GraphicEq,
                    title = "Deep Bass",
                    subtitle = "Resonant sub-bass shelf & dynamic kick envelope",
                    badgeText = if (deepBassActive) "ON" else "OFF",
                    checked = deepBassActive,
                    enabled = enabled,
                    onCheckedChange = onToggleDeepBass,
                    testTag = "enhancer_deep_bass_switch"
                ) {
                    AudioTrackSlider(
                        label = "Kick envelope",
                        valueText = if (deepBassActive) "100%" else "0%",
                        value = if (deepBassActive) 100f else 0f,
                        valueRange = 0f..100f,
                        step = 1f,
                        enabled = enabled,
                        onValueChange = { pct -> onToggleDeepBass(pct > 1f) },
                        testTag = "enhancer_deep_bass_slider"
                    )
                }

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
                    AudioTrackSlider(
                        label = "Low-shelf & punch gain",
                        valueText = String.format(Locale.US, "+%.1f dB", config.bassBoostDb),
                        value = config.bassBoostDb.coerceIn(0f, 6f),
                        valueRange = 0f..6f,
                        step = 0.1f,
                        enabled = enabled,
                        onValueChange = { v ->
                            onBassBoostChange(v)
                            if (v > 0f && !config.bassEnabled) onToggleBass(true)
                        },
                        testTag = "enhancer_bass_slider",
                        valueColor = palette.onSurface
                    )
                }

                EnhancerModuleContainer(
                    icon = Icons.Default.Headphones,
                    title = "Virtual Bass Synthesizer",
                    subtitle = "Psychoacoustic sub-bass for small speakers",
                    badgeText = if (config.virtualBassEnabled) {
                        "${(config.virtualBassStrength * 100).roundToInt()}%"
                    } else "OFF",
                    checked = config.virtualBassEnabled,
                    enabled = enabled,
                    onCheckedChange = onToggleVirtualBass,
                    testTag = "enhancer_virtual_bass_switch"
                ) {
                    AudioTrackSlider(
                        label = "Harmonic overtones",
                        valueText = "${(config.virtualBassStrength * 100).roundToInt()}%",
                        value = (config.virtualBassStrength * 100f).coerceIn(0f, 100f),
                        valueRange = 0f..100f,
                        step = 1f,
                        enabled = enabled,
                        onValueChange = { pct ->
                            onVirtualBassStrengthChange(pct / 100f)
                            if (pct > 0f && !config.virtualBassEnabled) onToggleVirtualBass(true)
                        },
                        testTag = "enhancer_virtual_bass_slider",
                        valueColor = palette.onSurface
                    )
                }

                EnhancerModuleContainer(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Dialogue Enhancer",
                    subtitle = "Vocal clarity boost & low-mid mud reduction",
                    badgeText = if (config.dialogueEnhancerEnabled) {
                        "${config.dialogueAmount}"
                    } else "OFF",
                    checked = config.dialogueEnhancerEnabled,
                    enabled = enabled,
                    onCheckedChange = onToggleDialogue,
                    testTag = "enhancer_dialogue_switch"
                ) {
                    AudioTrackSlider(
                        label = "Speech intelligibility",
                        valueText = "${config.dialogueAmount}",
                        value = config.dialogueAmount.toFloat().coerceIn(1f, 16f),
                        valueRange = 1f..16f,
                        step = 1f,
                        enabled = enabled,
                        onValueChange = { v ->
                            val amt = v.roundToInt().coerceIn(1, 16)
                            onDialogueAmountChange(amt)
                            if (!config.dialogueEnhancerEnabled) onToggleDialogue(true)
                        },
                        testTag = "enhancer_dialogue_slider",
                        valueColor = palette.onSurface
                    )
                }

                EnhancerModuleContainer(
                    icon = if (config.surroundEnabled) Icons.Default.SurroundSound else Icons.Default.SpatialAudioOff,
                    title = "3D Surround Virtualizer",
                    subtitle = "Binaural soundstage & crossfeed",
                    badgeText = if (config.surroundEnabled) {
                        "${(config.surroundWidth * 100).roundToInt()}%"
                    } else "OFF",
                    checked = config.surroundEnabled,
                    enabled = enabled,
                    onCheckedChange = onToggleSurround,
                    testTag = "enhancer_surround_switch"
                ) {
                    AudioTrackSlider(
                        label = "Soundstage width",
                        valueText = "${(config.surroundWidth * 100).roundToInt()}%",
                        value = (config.surroundWidth * 100f).coerceIn(0f, 100f),
                        valueRange = 0f..100f,
                        step = 1f,
                        enabled = enabled,
                        onValueChange = { pct ->
                            onSurroundWidthChange(pct / 100f)
                            if (pct > 0f && !config.surroundEnabled) onToggleSurround(true)
                        },
                        testTag = "enhancer_surround_slider",
                        valueColor = palette.onSurface
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
    var expanded by remember(checked) { mutableStateOf(checked) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = palette.sCont,
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) Color(0xFFD89A6C) else palette.outlineV
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) {
                        if (content != null) {
                            expanded = !expanded
                        } else {
                            onCheckedChange(!checked)
                        }
                    }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (checked) palette.primaryC else palette.sTop),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (checked) palette.primary else palette.onVar,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = title,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = palette.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (checked) palette.primaryC else palette.sTop)
                                    .padding(horizontal = 9.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = badgeText,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (checked) palette.primary else palette.onVar,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = palette.onVar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                V7PillSwitch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = { next ->
                        onCheckedChange(next)
                        if (next) expanded = true
                    },
                    modifier = Modifier.testTag(testTag)
                )
            }

            if (content != null) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                    ) {
                        HorizontalDivider(color = palette.outlineV)
                        Spacer(modifier = Modifier.height(10.dp))
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun SubwooferCard(
    config: EnhancerConfig,
    enabled: Boolean = true,
    useKnobs: Boolean = false,
    onToggleSubwoofer: (Boolean) -> Unit = {},
    onSubwooferIntensityChange: (Float) -> Unit = {},
    onSubwooferSubOctaveChange: (Float) -> Unit = {},
    onCrossoverHzChange: (Float) -> Unit = {},
    onToggleSubwooferAutoLoudness: (Boolean) -> Unit = {}
) {
    val palette = LocalMonetPalette.current
    val subActive = config.subwooferEnabled
    val lpfPct = (((config.subwooferCrossoverHz - 55f) / 65f) * 100f).roundToInt().coerceIn(0, 100)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
            .testTag("subwoofer_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = palette.sCont),
        border = BorderStroke(
            width = 1.dp,
            color = if (subActive) Color(0xFFD89A6C) else palette.outlineV
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (subActive) palette.primaryC else palette.sTop),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = "Subwoofer Engine",
                            tint = if (subActive) palette.primary else palette.onVar,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Subwoofer",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                color = palette.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (subActive) palette.primaryC else palette.sTop)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (subActive) {
                                        "${(config.subwooferIntensity * 100).roundToInt()}% SLAM"
                                    } else "OFF",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (subActive) palette.onPrimaryC else palette.onVar,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            text = "Deep sub-bass synthesizer & cabinet air slam",
                            fontSize = 13.sp,
                            color = palette.onVar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                V7PillSwitch(
                    checked = subActive,
                    enabled = enabled,
                    onCheckedChange = onToggleSubwoofer,
                    modifier = Modifier.testTag("subwoofer_master_switch")
                )
            }

            if (useKnobs) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.sLow)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val slamPct = if (subActive) (config.subwooferIntensity * 100f).coerceIn(0f, 150f) else 0f
                        AudioRotaryKnob(
                            label = "Cabinet drive & port slam",
                            valueText = "${slamPct.roundToInt()}%",
                            value = slamPct,
                            valueRange = 0f..150f,
                            step = 1f,
                            enabled = enabled,
                            onValueChange = { pct ->
                                if (pct <= 0.5f) {
                                    onToggleSubwoofer(false)
                                } else {
                                    onSubwooferIntensityChange(pct / 100f)
                                }
                            },
                            testTag = "subwoofer_intensity_slider",
                            modifier = Modifier.weight(1f)
                        )

                        val subOctPct = if (subActive) (config.subwooferSubOctaveMix * 100f).coerceIn(0f, 60f) else 0f
                        AudioRotaryKnob(
                            label = "Sub-octave synthesizer",
                            valueText = "${subOctPct.roundToInt()}% mix",
                            value = subOctPct,
                            valueRange = 0f..60f,
                            step = 1f,
                            enabled = enabled,
                            onValueChange = { pct ->
                                if (pct > 0.5f && !subActive) {
                                    onToggleSubwoofer(true)
                                }
                                onSubwooferSubOctaveChange(pct / 100f)
                            },
                            testTag = "subwoofer_sub_octave_slider",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val lpfKnobVal = if (subActive) lpfPct.toFloat() else 0f
                        AudioRotaryKnob(
                            label = "Low-pass crossover",
                            valueText = "${lpfKnobVal.roundToInt()}%",
                            value = lpfKnobVal,
                            valueRange = 0f..100f,
                            step = 1f,
                            enabled = enabled,
                            onValueChange = { pct ->
                                if (pct > 0.5f && !subActive) {
                                    onToggleSubwoofer(true)
                                }
                                val hz = 55f + (pct / 100f) * 65f
                                onCrossoverHzChange(hz)
                            },
                            testTag = "subwoofer_crossover_slider",
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.sLow)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AudioTrackSlider(
                        label = "Cabinet drive & port slam",
                        valueText = "${(config.subwooferIntensity * 100).roundToInt()}%",
                        value = (config.subwooferIntensity * 100f).coerceIn(0f, 150f),
                        valueRange = 0f..150f,
                        step = 1f,
                        enabled = enabled && subActive,
                        onValueChange = { pct -> onSubwooferIntensityChange(pct / 100f) },
                        testTag = "subwoofer_intensity_slider"
                    )

                    HorizontalDivider(color = palette.outlineV)

                    AudioTrackSlider(
                        label = "Sub-octave synthesizer",
                        valueText = "${(config.subwooferSubOctaveMix * 100).roundToInt()}% mix",
                        value = (config.subwooferSubOctaveMix * 100f).coerceIn(0f, 60f),
                        valueRange = 0f..60f,
                        step = 1f,
                        enabled = enabled && subActive,
                        onValueChange = { pct -> onSubwooferSubOctaveChange(pct / 100f) },
                        testTag = "subwoofer_sub_octave_slider"
                    )

                    HorizontalDivider(color = palette.outlineV)

                    AudioTrackSlider(
                        label = "Low-pass crossover",
                        valueText = "$lpfPct%",
                        value = lpfPct.toFloat(),
                        valueRange = 0f..100f,
                        step = 1f,
                        enabled = enabled && subActive,
                        onValueChange = { pct ->
                            val hz = 55f + (pct / 100f) * 65f
                            onCrossoverHzChange(hz)
                        },
                        testTag = "subwoofer_crossover_slider"
                    )
                }
            }

            HorizontalDivider(color = palette.outlineV)

            V7SwitchRow(
                title = "Fletcher-Munson equal-loudness",
                subtitle = "Scales sub-port pressure at lower volume",
                checked = config.subwooferAutoLoudness,
                enabled = enabled && subActive,
                onCheckedChange = onToggleSubwooferAutoLoudness,
                testTag = "subwoofer_loudness_switch"
            )
        }
    }
}
