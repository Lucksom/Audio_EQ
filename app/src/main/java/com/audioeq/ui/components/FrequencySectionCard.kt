package com.audioeq.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioeq.model.OutputRoute
import com.audioeq.ui.theme.JetBrainsMonoFontFamily
import com.audioeq.ui.theme.LocalMonetPalette
import com.audioeq.ui.theme.SpaceGroteskFontFamily
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val FREQ_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

@Composable
fun EqualizerCard(
    masterEnabled: Boolean = true,
    isEnabled: Boolean,
    isAbCompare: Boolean,
    usbBitPerfectActive: Boolean,
    intensity: Float,
    activeRoute: OutputRoute,
    ceilingDb: Float,
    sliderGainsDb: List<Float>,
    effectiveGainsDb: List<Float>,
    splitLREnabled: Boolean,
    editingRightChannel: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectEditingChannel: (Boolean) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    val eqActive = masterEnabled && isEnabled

    val cardAlpha by animateFloatAsState(
        targetValue = if (masterEnabled) 1f else 0.55f,
        animationSpec = tween(220),
        label = "eqCardAlpha"
    )

    val powerBg by animateColorAsState(
        targetValue = if (eqActive) palette.primary else Color(0xFFCBB9AD),
        animationSpec = tween(200),
        label = "eqPowerBg"
    )
    val powerTint by animateColorAsState(
        targetValue = palette.onPrimary,
        animationSpec = tween(200),
        label = "eqPowerTint"
    )

    val subtitleText = when {
        !masterEnabled -> "Master is off · all effects disabled"
        !isEnabled -> "Equalizer is off · Tone & Enhancers active"
        usbBitPerfectActive -> "USB Bit-Perfect ON · EQ bypassed"
        isAbCompare -> "A/B bypass comparison active"
        else -> String.format(Locale.US, "%.1f× intensity", intensity)
    }

    val isWarn = ceilingDb > 4.0f
    val meterFillFraction = (ceilingDb / 9.0f).coerceIn(0f, 1f)
    val meterColor by animateColorAsState(
        targetValue = if (isWarn) palette.warn else palette.secondary,
        animationSpec = tween(180),
        label = "meterColor"
    )
    val ceilTextColor by animateColorAsState(
        targetValue = if (isWarn) palette.warn else palette.primary,
        animationSpec = tween(180),
        label = "ceilTextColor"
    )

    val bandsAlpha by animateFloatAsState(
        targetValue = if (eqActive) 1f else 0.42f,
        animationSpec = tween(220),
        label = "bandsAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .border(
                width = 1.dp,
                color = palette.outlineV,
                shape = RoundedCornerShape(22.dp)
            )
            .testTag("unified_equalizer_card"),
        shape = RoundedCornerShape(22.dp),
        color = palette.sCont
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
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .shadow(
                                elevation = if (eqActive) 8.dp else 0.dp,
                                shape = CircleShape,
                                ambientColor = palette.glow,
                                spotColor = palette.glow
                            )
                            .clip(CircleShape)
                            .background(powerBg)
                            .clickable(enabled = masterEnabled) { onToggleEnabled(!isEnabled) }
                            .testTag("eq_power_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = if (eqActive) "Disable Equalizer only" else "Enable Equalizer",
                            tint = powerTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Equalizer",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.2).sp,
                            color = palette.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = subtitleText,
                            fontSize = 14.sp,
                            color = palette.onVar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("eq_subtitle_text")
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.tertiaryC)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("route_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeRoute.label,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = palette.onTertiaryC,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.sLow)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("headroom_ceiling_meter"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "HEADROOM",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp,
                    color = palette.onVar
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.sTop)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(meterFillFraction)
                            .clip(RoundedCornerShape(999.dp))
                            .background(meterColor)
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.1f dB", ceilingDb),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = ceilTextColor,
                    modifier = Modifier.testTag("ceiling_db_text")
                )
            }

            FrequencyResponseGraph(
                displayGainsDb = effectiveGainsDb,
                isEnabled = masterEnabled && !isAbCompare && !usbBitPerfectActive
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val zones = listOf(
                    "BASS" to 1.9f,
                    "MIDS" to 2.6f,
                    "TREBLE" to 2.4f,
                    "AIR" to 1.2f
                )
                zones.forEach { (title, weightVal) ->
                    Column(
                        modifier = Modifier.weight(weightVal),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(palette.outlineV)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = title,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp,
                            letterSpacing = 1.1.sp,
                            color = palette.onVar,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            AnimatedVisibility(visible = splitLREnabled) {
                V7SegmentedRow(
                    options = listOf(false to "Left Channel (10-Band)", true to "Right Channel (10-Band)"),
                    selectedValue = editingRightChannel,
                    enabled = eqActive,
                    onSelect = onSelectEditingChannel
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(bandsAlpha)
                    .padding(top = 4.dp)
                    .testTag("eq_bands_row"),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (i in 0 until 10) {
                    val userGain = sliderGainsDb.getOrElse(i) { 0f }
                    val effGain = effectiveGainsDb.getOrElse(i) { 0f }
                    val freqLabel = FREQ_LABELS[i]
                    V7VerticalBandSlider(
                        bandIndex = i,
                        freqLabel = freqLabel,
                        userGainDb = userGain,
                        effectiveGainDb = if (eqActive) effGain else userGain,
                        intensity = intensity,
                        enabled = eqActive,
                        onGainChange = { newDb -> onBandGainChange(i, newDb) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun V7VerticalBandSlider(
    bandIndex: Int,
    freqLabel: String,
    userGainDb: Float,
    effectiveGainDb: Float,
    intensity: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    var isDragging by remember { mutableStateOf(false) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }

    val currentOnGainChange by rememberUpdatedState(onGainChange)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentUserGain by rememberUpdatedState(userGainDb)
    val currentEffGain by rememberUpdatedState(effectiveGainDb)
    val currentIntensity by rememberUpdatedState(intensity)

    val activeDbText = if (effectiveGainDb >= 0f) {
        String.format(Locale.US, "+%.1f", effectiveGainDb)
    } else {
        String.format(Locale.US, "%.1f", effectiveGainDb)
    }
    val hasGain = abs(effectiveGainDb) > 0.05f

    val knobScale by animateFloatAsState(
        targetValue = if (isDragging) 1.12f else 1.0f,
        animationSpec = tween(120),
        label = "knobScale_$bandIndex"
    )

    Column(
        modifier = modifier.testTag("band_column_$bandIndex"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = activeDbText,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.5.sp,
            color = if (hasGain) palette.primary else palette.onVar,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        BoxWithConstraints(
            modifier = Modifier
                .width(32.dp)
                .height(172.dp)
                .pointerInput(bandIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!currentEnabled) return@awaitEachGesture

                        val now = System.currentTimeMillis()
                        if (now - lastTapTimeMs <= 280L) {
                            lastTapTimeMs = 0L
                            currentOnGainChange(0f)
                            return@awaitEachGesture
                        }
                        lastTapTimeMs = now

                        val hPx = size.height.toFloat().coerceAtLeast(1f)
                        val enhancerDelta = currentEffGain - (currentUserGain * currentIntensity)

                        fun updateFromY(yPx: Float) {
                            val p = (1f - (yPx / hPx).coerceIn(0f, 1f))
                            val targetEffDb = (p * 24f) - 12f
                            val rawUserDb = if (currentIntensity > 0.1f) {
                                (targetEffDb - enhancerDelta) / currentIntensity
                            } else {
                                targetEffDb
                            }
                            val rounded = ((rawUserDb * 10f).roundToInt() / 10f).coerceIn(-12f, 12f)
                            currentOnGainChange(rounded)
                        }

                        isDragging = true
                        updateFromY(down.position.y)

                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    change.consume()
                                    updateFromY(change.position.y)
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            isDragging = false
                        }
                    }
                }
                .testTag("band_slider_$bandIndex"),
            contentAlignment = Alignment.Center
        ) {
            val railHeight = maxHeight
            val pct = ((effectiveGainDb.coerceIn(-12f, 12f) + 12f) / 24f).coerceIn(0f, 1f)
            val topFraction = min(0.5f, 1f - pct)
            val fillFraction = abs(pct - 0.5f)
            val knobYOffset = railHeight * (0.5f - pct)

            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.sTop)
            )

            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(palette.outline)
            )

            if (fillFraction > 0.002f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = railHeight * topFraction)
                        .width(8.dp)
                        .height(railHeight * fillFraction)
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.secondary)
                )
            }

            Box(
                modifier = Modifier
                    .offset(y = knobYOffset)
                    .scale(knobScale)
                    .width(26.dp)
                    .height(18.dp)
                    .shadow(4.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.onPrimary)
                    .border(2.5.dp, palette.primary, RoundedCornerShape(999.dp))
            )
        }

        Text(
            text = freqLabel,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            color = palette.onVar,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ToneCard(
    masterEnabled: Boolean,
    tiltBassDb: Float,
    tiltTrebleDb: Float,
    intensity: Float,
    volumeContourEnabled: Boolean,
    isAbCompare: Boolean,
    useKnobs: Boolean = false,
    deepBassEnabled: Boolean = false,
    onTiltBassChange: (Float) -> Unit,
    onTiltTrebleChange: (Float) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onToggleVolumeContour: (Boolean) -> Unit,
    onToggleAbCompare: (Boolean) -> Unit,
    onToggleDeepBass: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    val cardAlpha by animateFloatAsState(
        targetValue = if (masterEnabled) 1f else 0.55f,
        animationSpec = tween(220),
        label = "toneCardAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .border(
                width = 1.dp,
                color = palette.outlineV,
                shape = RoundedCornerShape(22.dp)
            )
            .testTag("tone_card"),
        shape = RoundedCornerShape(22.dp),
        color = palette.sCont
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    val iconColor = palette.primary
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val w = size.width
                        val h = size.height
                        val p = Path().apply {
                            moveTo(w * (4f / 24f), h * (12f / 24f))
                            lineTo(w * (8f / 24f), h * (12f / 24f))
                            lineTo(w * (11f / 24f), h * (5f / 24f))
                            lineTo(w * (14f / 24f), h * (19f / 24f))
                            lineTo(w * (17f / 24f), h * (12f / 24f))
                            lineTo(w * (20f / 24f), h * (12f / 24f))
                        }
                        drawPath(
                            path = p,
                            color = iconColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tone",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        letterSpacing = (-0.2).sp,
                        color = palette.onSurface
                    )
                    Text(
                        text = "Tilt, intensity and contour",
                        fontSize = 13.sp,
                        color = palette.onVar
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToneTiltMiniCard(
                    label = "Bass tilt",
                    valueDb = tiltBassDb,
                    enabled = masterEnabled,
                    onValueChange = { raw ->
                        val stepped = (raw * 2f).roundToInt() / 2f
                        onTiltBassChange(stepped)
                    },
                    testTag = "bass_tilt_slider",
                    modifier = Modifier.weight(1f)
                )
                ToneTiltMiniCard(
                    label = "Treble tilt",
                    valueDb = tiltTrebleDb,
                    enabled = masterEnabled,
                    onValueChange = { raw ->
                        val stepped = (raw * 2f).roundToInt() / 2f
                        onTiltTrebleChange(stepped)
                    },
                    testTag = "treble_tilt_slider",
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Intensity",
                        fontSize = 13.sp,
                        color = palette.onVar
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f×", intensity),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = palette.primary,
                        modifier = Modifier.testTag("intensity_value_text")
                    )
                }
                val selectedIntensityBucket = when {
                    intensity < 0.8f -> 0.6f
                    intensity > 1.15f -> 1.3f
                    else -> 1.0f
                }
                V7SegmentedRow(
                    options = listOf(
                        0.6f to "Light 0.6×",
                        1.0f to "Standard 1.0×",
                        1.3f to "Deep+ 1.3×"
                    ),
                    selectedValue = selectedIntensityBucket,
                    enabled = masterEnabled,
                    onSelect = onIntensityChange,
                    modifier = Modifier.testTag("intensity_segmented_row")
                )
            }

            if (useKnobs) {
                HorizontalDivider(color = palette.outlineV)

                V7SwitchRow(
                    title = "Deep bass",
                    subtitle = "Resonant sub-bass shelf & dynamic kick envelope",
                    checked = deepBassEnabled,
                    enabled = masterEnabled,
                    onCheckedChange = onToggleDeepBass,
                    testTag = "enhancer_deep_bass_switch"
                )
            }

            HorizontalDivider(color = palette.outlineV)

            V7SwitchRow(
                title = "Volume contour",
                subtitle = "Lifts bass and treble at low volume",
                checked = volumeContourEnabled,
                enabled = masterEnabled,
                onCheckedChange = onToggleVolumeContour,
                testTag = "volume_contour_switch"
            )

            HorizontalDivider(color = palette.outlineV)

            V7SwitchRow(
                title = "A/B loudness compare",
                subtitle = "Level-matched bypass comparison",
                checked = isAbCompare,
                enabled = masterEnabled,
                onCheckedChange = onToggleAbCompare,
                testTag = "ab_compare_switch"
            )
        }
    }
}

@Composable
private fun ToneTiltMiniCard(
    label: String,
    valueDb: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    val formattedVal = if (valueDb >= 0f) {
        String.format(Locale.US, "+%.1f dB", valueDb)
    } else {
        String.format(Locale.US, "%.1f dB", valueDb)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.sLow)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AudioTrackSlider(
            label = label,
            valueText = formattedVal,
            value = valueDb,
            valueRange = -6f..6f,
            step = 0.5f,
            bipolar = true,
            defaultValue = 0f,
            enabled = enabled,
            valueColor = palette.onSurface,
            onValueChange = onValueChange,
            testTag = testTag
        )
    }
}

@Composable
fun <T> V7SegmentedRow(
    options: List<Pair<T, String>>,
    selectedValue: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.sLow)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val selected = value == selectedValue
            val bg by animateColorAsState(
                targetValue = if (selected) palette.primaryC else Color.Transparent,
                animationSpec = tween(160),
                label = "segBg_$label"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) palette.onPrimaryC else palette.onVar,
                animationSpec = tween(160),
                label = "segFg_$label"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable(enabled = enabled) { onSelect(value) }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun V7SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = palette.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = palette.onVar
            )
        }
        V7PillSwitch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun V7PillSwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    val trackBg by animateColorAsState(
        targetValue = if (checked) palette.primary else Color(0xFFD5C6BC),
        animationSpec = tween(180),
        label = "swTrackBg"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 28.dp else 4.dp,
        animationSpec = tween(180),
        label = "swThumbOffset"
    )

    Box(
        modifier = modifier
            .width(58.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackBg)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(26.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
