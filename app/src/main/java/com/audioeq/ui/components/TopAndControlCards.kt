package com.audioeq.ui.components

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioeq.audio.AutoEqEntry
import com.audioeq.audio.AutoEqRepository
import com.audioeq.audio.DeviceFilter
import com.audioeq.model.EngineMode
import com.audioeq.model.HeadphoneProfile
import com.audioeq.model.OperatingMode
import com.audioeq.model.OutputRoute
import com.audioeq.model.PresetConfig
import com.audioeq.shizuku.ShizukuState
import com.audioeq.ui.theme.JetBrainsMonoFontFamily
import com.audioeq.ui.theme.LocalMonetPalette
import com.audioeq.ui.theme.SpaceGroteskFontFamily
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun AudioTrackSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float = 0f,
    bipolar: Boolean = false,
    defaultValue: Float = if (bipolar) 0f else valueRange.start,
    enabled: Boolean = true,
    valueColor: Color = Color.Unspecified,
    onValueChange: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    val resolvedValueColor = if (valueColor == Color.Unspecified) palette.primary else valueColor
    var isDragging by remember { mutableStateOf(false) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentEnabled by rememberUpdatedState(enabled)

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val pct = ((value.coerceIn(valueRange.start, valueRange.endInclusive) - valueRange.start) / span)
        .coerceIn(0f, 1f)

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.15f else 1.0f,
        animationSpec = tween(120),
        label = "thumbScale_$testTag"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (label.isNotEmpty() || valueText.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = palette.onVar,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = valueText,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = resolvedValueColor,
                    maxLines = 1
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .pointerInput(valueRange.start, valueRange.endInclusive, step, defaultValue) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!currentEnabled) return@awaitEachGesture

                        val now = System.currentTimeMillis()
                        if (now - lastTapTimeMs <= 280L) {
                            lastTapTimeMs = 0L
                            currentOnValueChange(defaultValue)
                            return@awaitEachGesture
                        }
                        lastTapTimeMs = now

                        val wPx = size.width.toFloat().coerceAtLeast(1f)

                        fun updateFromX(xPx: Float) {
                            val norm = (xPx / wPx).coerceIn(0f, 1f)
                            val raw = valueRange.start + norm * span
                            val snapped = if (step > 0f) {
                                (kotlin.math.round(raw / step) * step)
                                    .coerceIn(valueRange.start, valueRange.endInclusive)
                            } else {
                                raw.coerceIn(valueRange.start, valueRange.endInclusive)
                            }
                            currentOnValueChange(snapped)
                        }

                        isDragging = true
                        updateFromX(down.position.x)

                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    change.consume()
                                    updateFromX(change.position.x)
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            isDragging = false
                        }
                    }
                }
                .testTag(testTag),
            contentAlignment = Alignment.CenterStart
        ) {
            val trackW = maxWidth
            val thumbSize = 24.dp
            val usableW = (trackW - thumbSize).coerceAtLeast(0.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.sTop)
            )

            if (bipolar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(2.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(palette.outline)
                )
                val leftFrac = min(0.5f, pct)
                val fillFrac = abs(pct - 0.5f)
                if (fillFrac > 0.003f) {
                    Box(
                        modifier = Modifier
                            .offset(x = trackW * leftFrac)
                            .width(trackW * fillFrac)
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.secondary)
                    )
                }
            } else if (pct > 0.003f) {
                Box(
                    modifier = Modifier
                        .width(trackW * pct)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.secondary)
                )
            }

            Box(
                modifier = Modifier
                    .offset(x = usableW * pct)
                    .scale(thumbScale)
                    .size(thumbSize)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(palette.onPrimary)
                    .border(3.dp, palette.primary, CircleShape)
            )
        }
    }
}

@Composable
fun AudioRotaryKnob(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float = 0f,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    var isDragging by remember { mutableStateOf(false) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentValue by rememberUpdatedState(value)

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val norm = ((value.coerceIn(valueRange.start, valueRange.endInclusive) - valueRange.start) / span)
        .coerceIn(0f, 1f)
    val angleDeg = -135f + norm * 270f

    val knobScale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1.0f,
        animationSpec = tween(140),
        label = "knobScale_$testTag"
    )

    Column(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(knobScale)
                .pointerInput(valueRange.start, valueRange.endInclusive, step) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!currentEnabled) return@awaitEachGesture

                        val now = System.currentTimeMillis()
                        if (now - lastTapTimeMs <= 280L) {
                            lastTapTimeMs = 0L
                            currentOnValueChange(valueRange.start)
                            return@awaitEachGesture
                        }
                        lastTapTimeMs = now

                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        var acc = currentValue
                        var lastAng = (atan2(down.position.y - cy, down.position.x - cx) * (180.0 / PI)).toFloat()
                        var lastY = down.position.y

                        fun snapValue(raw: Float): Float {
                            val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
                            return if (step > 0f) {
                                (kotlin.math.round(clamped / step) * step)
                                    .coerceIn(valueRange.start, valueRange.endInclusive)
                            } else {
                                clamped
                            }
                        }

                        val downDx = down.position.x - cx
                        val downDy = down.position.y - cy
                        val downDist = hypot(downDx, downDy)
                        if (downDist >= size.width * 0.28f) {
                            val tapDeg = (atan2(downDx, -downDy) * (180.0 / PI)).toFloat()
                            if (tapDeg >= -150f && tapDeg <= 150f) {
                                val clampedDeg = tapDeg.coerceIn(-135f, 135f)
                                val n = ((clampedDeg + 135f) / 270f).coerceIn(0f, 1f)
                                acc = valueRange.start + n * span
                                currentOnValueChange(snapValue(acc))
                            }
                        }

                        isDragging = true

                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    change.consume()
                                    val px = change.position.x
                                    val py = change.position.y
                                    val dx = px - cx
                                    val dy = py - cy
                                    val dist = hypot(dx, dy)
                                    if (dist >= size.width * 0.14f) {
                                        val a = (atan2(dy, dx) * (180.0 / PI)).toFloat()
                                        var d = a - lastAng
                                        if (d > 180f) d -= 360f
                                        if (d < -180f) d += 360f
                                        lastAng = a
                                        lastY = py
                                        acc = (acc + d * (span / 270f))
                                            .coerceIn(valueRange.start, valueRange.endInclusive)
                                        currentOnValueChange(snapValue(acc))
                                    } else {
                                        val dyStep = lastY - py
                                        lastY = py
                                        lastAng = (atan2(dy, dx) * (180.0 / PI)).toFloat()
                                        acc = (acc + (dyStep / (size.height * 0.9f)) * span)
                                            .coerceIn(valueRange.start, valueRange.endInclusive)
                                        currentOnValueChange(snapValue(acc))
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            isDragging = false
                        }
                    }
                }
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(130.dp)) {
                val u = min(size.width, size.height) / 100f
                val cx = size.width / 2f
                val cy = size.height / 2f

                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0x3350230A),
                            0.78f to Color(0x2250230A),
                            1.0f to Color.Transparent
                        ),
                        center = Offset(cx, cy + 5.5f * u),
                        radius = 52f * u
                    ),
                    radius = 52f * u,
                    center = Offset(cx, cy + 5.5f * u)
                )

                val bezelRadius = 48f * u
                val bezelBrush = if (palette.isDark) {
                    Brush.radialGradient(
                        colors = listOf(palette.sHigh, palette.sCont, palette.sLow),
                        center = Offset(35.6f * u, 26f * u),
                        radius = 81.6f * u
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFFFAF4), Color(0xFFE3CFC2)),
                        center = Offset(35.6f * u, 26f * u),
                        radius = 81.6f * u
                    )
                }
                drawCircle(
                    brush = bezelBrush,
                    radius = bezelRadius,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = if (palette.isDark) palette.outlineV else Color(0xFFD6BFB1),
                    radius = bezelRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.8f * u)
                )

                val ticks = 21
                for (i in 0 until ticks) {
                    val ta = -135f + 270f * (i.toFloat() / (ticks - 1).toFloat())
                    val rad = (ta - 90f) * (PI / 180.0)
                    val r1 = 44f * u
                    val r2 = 40.5f * u
                    val x1 = cx + (r1 * cos(rad)).toFloat()
                    val y1 = cy + (r1 * sin(rad)).toFloat()
                    val x2 = cx + (r2 * cos(rad)).toFloat()
                    val y2 = cy + (r2 * sin(rad)).toFloat()
                    val isLit = ta <= angleDeg + 0.01f
                    drawLine(
                        color = if (isLit) Color(0xFFC2410C) else Color(0xFFC4AA9C),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 1.3f * u,
                        cap = StrokeCap.Round
                    )
                }

                val arcRadius = 38f * u
                val arcTopLeft = Offset(cx - arcRadius, cy - arcRadius)
                val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                drawArc(
                    color = if (palette.isDark) palette.sTop else Color(0xFFE6D6CC),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 7f * u, cap = StrokeCap.Round)
                )

                if (norm > 0.002f) {
                    val arcBrush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFBE5C), Color(0xFFB8461A)),
                        start = Offset(12f * u, 12f * u),
                        end = Offset(88f * u, 88f * u)
                    )
                    drawArc(
                        brush = arcBrush,
                        startAngle = 135f,
                        sweepAngle = 270f * norm,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        alpha = 0.38f,
                        style = Stroke(width = 11.5f * u, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = arcBrush,
                        startAngle = 135f,
                        sweepAngle = 270f * norm,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 7f * u, cap = StrokeCap.Round)
                    )
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0x59461E05),
                            0.72f to Color(0x38461E05),
                            1.0f to Color.Transparent
                        ),
                        center = Offset(cx, cy + 4.8f * u),
                        radius = 33.5f * u
                    ),
                    radius = 33.5f * u,
                    center = Offset(cx, cy + 4.8f * u)
                )

                val bodyRadius = 29f * u
                val bodyBrush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFFCC6428),
                        0.5f to Color(0xFF8F3209),
                        1.0f to Color(0xFF4A1500)
                    ),
                    center = Offset(41.88f * u, 37.24f * u),
                    radius = 46.4f * u
                )
                drawCircle(
                    brush = bodyBrush,
                    radius = bodyRadius,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0xFF3A1000),
                    radius = bodyRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.6f * u)
                )

                val capRadius = 20f * u
                val capBrush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = 0.25f),
                        1.0f to Color.Transparent
                    ),
                    center = Offset(46f * u, 42f * u),
                    radius = 28f * u
                )
                drawCircle(
                    brush = capBrush,
                    radius = capRadius,
                    center = Offset(cx, cy)
                )

                val pointerRad = (angleDeg - 90f) * (PI / 180.0)
                val pStart = 19f * u
                val pEnd = 33f * u
                val px1 = cx + (pStart * cos(pointerRad)).toFloat()
                val py1 = cy + (pStart * sin(pointerRad)).toFloat()
                val px2 = cx + (pEnd * cos(pointerRad)).toFloat()
                val py2 = cy + (pEnd * sin(pointerRad)).toFloat()
                drawLine(
                    color = Color(0xFFFFDCAA).copy(alpha = 0.45f),
                    start = Offset(px1, py1),
                    end = Offset(px2, py2),
                    strokeWidth = 7f * u,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFFFF4DC),
                    start = Offset(px1, py1),
                    end = Offset(px2, py2),
                    strokeWidth = 4f * u,
                    cap = StrokeCap.Round
                )
            }

            Text(
                text = valueText,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = Color(0xFFFFF4DC),
                maxLines = 1,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0x80280F00),
                        offset = Offset(0f, 2f),
                        blurRadius = 4f
                    )
                )
            )
        }

        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = palette.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TopBarHeader(
    masterEnabled: Boolean,
    activePresetName: String,
    selectedMode: EngineMode,
    shizukuState: ShizukuState = ShizukuState.NOT_RUNNING,
    shizukuPrivilege: String = "",
    rootAvailable: Boolean = false,
    dumpPermissionGranted: Boolean = false,
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

    val isShizukuRoot = shizukuState == ShizukuState.READY &&
        (shizukuPrivilege.contains("uid=0") || shizukuPrivilege.contains("Root", ignoreCase = true))
    val isShizukuShell = shizukuState == ShizukuState.READY && !isShizukuRoot

    val modeStatusLabel = when (selectedMode) {
        EngineMode.ROOT -> when {
            isShizukuRoot -> "Shizuku (Su)"
            rootAvailable -> "Root (Su)"
            isShizukuShell -> "Shizuku (Shell)"
            dumpPermissionGranted -> "ADB (Shell)"
            else -> "Root"
        }
        EngineMode.ADB_SHIZUKU -> when {
            isShizukuRoot -> "Shizuku (Su)"
            isShizukuShell -> "Shizuku (Shell)"
            rootAvailable -> "Root (Su)"
            dumpPermissionGranted -> "ADB (Shell)"
            else -> "ADB / Shizuku"
        }
        EngineMode.LEGACY -> "Legacy"
    }

    val isModePrivilegeReady = when (selectedMode) {
        EngineMode.ROOT -> rootAvailable || isShizukuRoot
        EngineMode.ADB_SHIZUKU -> shizukuState == ShizukuState.READY || dumpPermissionGranted || rootAvailable
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
                        elevation = if (masterEnabled) 6.dp else 0.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = palette.glow,
                        spotColor = palette.glow
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFF7EC), Color(0xFFFFE1EC)),
                            start = Offset.Zero,
                            end = Offset(140f, 140f)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(34.dp)) {
                    val scaleX = size.width / 512f
                    val scaleY = size.height / 512f
                    val barSpecs = listOf(
                        Triple(78f, 218f, 76f),
                        Triple(152f, 172f, 168f),
                        Triple(226f, 120f, 272f),
                        Triple(300f, 172f, 168f),
                        Triple(374f, 218f, 76f)
                    )
                    barSpecs.forEach { (bx, by, bh) ->
                        val topLeft = Offset(bx * scaleX, by * scaleY)
                        val barSize = Size(58f * scaleX, bh * scaleY)
                        val corner = androidx.compose.ui.geometry.CornerRadius(29f * scaleX, 29f * scaleY)
                        drawRoundRect(
                            color = Color(0x33E61E6E),
                            topLeft = Offset(topLeft.x, topLeft.y + 8f * scaleY),
                            size = barSize,
                            cornerRadius = corner
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFE61E6E), Color(0xFFFF7A3D)),
                                startY = topLeft.y,
                                endY = topLeft.y + barSize.height
                            ),
                            topLeft = topLeft,
                            size = barSize,
                            cornerRadius = corner
                        )
                    }
                    val circleCentersX = listOf(107f, 181f, 255f, 329f, 403f)
                    circleCentersX.forEach { cx ->
                        val center = Offset(cx * scaleX, 256f * scaleY)
                        drawCircle(
                            color = Color.White,
                            radius = 22f * scaleX,
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFFE61E6E),
                            radius = 22f * scaleX,
                            center = center,
                            style = Stroke(width = 12f * scaleX)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Audio EQ",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.4).sp,
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
                    .background(palette.sCont)
                    .border(1.dp, palette.outlineV, RoundedCornerShape(999.dp))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("engine_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dotColor = when {
                    !masterEnabled -> palette.outline
                    isModePrivilegeReady -> Color(0xFF4CAF50)
                    else -> Color(0xFFFF9800)
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
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
                        elevation = if (masterEnabled) 6.dp else 0.dp,
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.sCont)
                    .border(1.dp, palette.outlineV, RoundedCornerShape(14.dp))
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
                color = palette.outlineV,
                shape = RoundedCornerShape(22.dp)
            )
            .testTag("preset_card"),
        shape = RoundedCornerShape(22.dp),
        color = palette.sCont.copy(alpha = if (masterEnabled) 1f else 0.6f)
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
                Text(
                    text = "PRESETS",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp,
                    color = palette.onVar
                )
                Text(
                    text = activePresetName,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
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
                        targetValue = if (isSelected) palette.primaryC else palette.sCont,
                        animationSpec = tween(160),
                        label = "presetChipBg_${preset.id}"
                    )
                    val chipBorder by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFFF3B787) else palette.outlineV,
                        animationSpec = tween(160),
                        label = "presetChipBorder_${preset.id}"
                    )
                    val chipContent by animateColorAsState(
                        targetValue = if (isSelected) palette.onPrimaryC else palette.onSurface,
                        animationSpec = tween(160),
                        label = "presetChipContent_${preset.id}"
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(chipBg)
                            .border(
                                width = 1.dp,
                                color = chipBorder,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable(enabled = masterEnabled) { onSelectPreset(preset) }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                            .testTag("preset_chip_${preset.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset.name,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 14.sp,
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
    autoEqLoading: Boolean = false,
    onApplyAutoEqEntry: (AutoEqEntry) -> Unit,
    onToggleUseTenBandSliders: (Boolean) -> Unit,
    onClearAutoEq: () -> Unit,
    onDismissSuggestion: () -> Unit,
    onRefreshDatabase: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(DeviceFilter.ALL) }

    val selectedProfile = headphoneProfiles.find { it.id == selectedHeadphoneProfileId }
    val isProfileActive = selectedProfile != null && selectedProfile.id != "none"

    val trimmedQuery = searchQuery.trim()
    val matchingEntries = remember(trimmedQuery, selectedFilter, autoEqIndex) {
        AutoEqRepository.searchStatic(
            index = autoEqIndex,
            query = trimmedQuery,
            filter = selectedFilter,
            limit = 30
        )
    }

    val modeFileDescription = when {
        autoEqUseTenBandSliders -> "10-band sliders · FixedBandEQ.txt"
        engineMode == EngineMode.ROOT -> "Root mode · ParametricEQ.txt"
        operatingMode == OperatingMode.LEGACY_TIER1_EQ -> "Legacy 5-band EQ · ParametricEQ.txt"
        else -> "${engineMode.shortTitle} · ParametricEQ.txt"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isProfileActive) {
                    palette.primary.copy(alpha = 0.55f)
                } else {
                    palette.outlineV
                },
                shape = RoundedCornerShape(22.dp)
            )
            .testTag("autoeq_main_card"),
        shape = RoundedCornerShape(22.dp),
        color = palette.sCont.copy(alpha = if (masterEnabled) 1f else 0.6f)
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
                    modifier = Modifier.weight(1f),
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
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "AutoEq",
                            tint = palette.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AUTOEQ · ${activeRoute.label}".uppercase(Locale.US),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = 1.4.sp,
                            color = palette.onVar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isProfileActive && selectedProfile != null) {
                                "Applied: ${selectedProfile.name} (${selectedProfile.source}) · Preamp ${String.format(Locale.US, "%.1f", preampDb)} dB"
                            } else {
                                modeFileDescription
                            },
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
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
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.sLow)
                            .border(1.dp, palette.outlineV, RoundedCornerShape(999.dp))
                            .clickable(enabled = masterEnabled) { onClearAutoEq() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("autoeq_clear_button")
                    ) {
                        Text(
                            text = "Clear",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = palette.onVar
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.sLow)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (!autoEqUseTenBandSliders) palette.primary else Color.Transparent)
                        .clickable(enabled = masterEnabled) { onToggleUseTenBandSliders(false) }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                        .testTag("autoeq_mode_parametric"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ParametricEQ (Engine)",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = if (!autoEqUseTenBandSliders) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = if (!autoEqUseTenBandSliders) palette.onPrimary else palette.onVar,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (autoEqUseTenBandSliders) palette.primary else Color.Transparent)
                        .clickable(enabled = masterEnabled) { onToggleUseTenBandSliders(true) }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                        .testTag("autoeq_mode_fixedband"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "FixedBandEQ (10 sliders)",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = if (autoEqUseTenBandSliders) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = if (autoEqUseTenBandSliders) palette.onPrimary else palette.onVar,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                enabled = masterEnabled,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Search earphones, earbuds, or headphones",
                        fontSize = 13.5.sp,
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
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.primary,
                    unfocusedBorderColor = palette.outlineV,
                    focusedContainerColor = palette.sLow,
                    unfocusedContainerColor = palette.sLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("autoeq_search_box")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceFilter.values().forEach { f ->
                    val isSelected = selectedFilter == f
                    val chipBg = if (isSelected) palette.primaryC else palette.sCont
                    val chipBorder = if (isSelected) Color(0xFFF3B787) else palette.outlineV
                    val chipText = if (isSelected) palette.onPrimaryC else palette.onSurface

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorder, RoundedCornerShape(999.dp))
                            .clickable(enabled = masterEnabled) {
                                selectedFilter = if (selectedFilter == f && f != DeviceFilter.ALL) {
                                    DeviceFilter.ALL
                                } else {
                                    f
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = f.label,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.5.sp,
                            color = chipText
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isProfileActive && selectedProfile != null) {
                        "Applied: ${selectedProfile.name} (${selectedProfile.source})"
                    } else {
                        "${autoEqIndex.size} profiles"
                    },
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 13.sp,
                    color = palette.onVar,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onRefreshDatabase,
                    enabled = masterEnabled && !autoEqLoading
                ) {
                    Text(
                        text = if (autoEqIndex.isEmpty()) "Download database" else "Update",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = palette.primary
                    )
                }
            }

            if (autoEqLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.primary,
                    trackColor = palette.sTop
                )
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

            val showSuggestions = trimmedQuery.isNotEmpty() || selectedFilter != DeviceFilter.ALL
            if (showSuggestions) {
                if (matchingEntries.isEmpty()) {
                    Text(
                        text = "No matching earphones or headphones found for \"$trimmedQuery\"",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 12.sp,
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
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isCurrent) palette.primaryC.copy(alpha = 0.65f) else palette.sLow)
                                    .border(
                                        width = 1.dp,
                                        color = if (isCurrent) palette.primary else palette.outlineV,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable(enabled = masterEnabled && !autoEqLoading) { onApplyAutoEqEntry(entry) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                                    .testTag("autoeq_row_${entry.nameCompact}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = palette.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.source} · ${entry.form}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        color = palette.onVar,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (isCurrent) palette.primary else palette.primaryC)
                                        .clickable(enabled = masterEnabled && !autoEqLoading) { onApplyAutoEqEntry(entry) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("autoeq_use_${entry.nameCompact}")
                                ) {
                                    Text(
                                        text = if (isCurrent) "Active" else "Apply",
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
        }
    }
}
