package com.audioeq.ui.theme

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
data class OklchColor(
    val l: Double,
    val c: Double,
    val h: Double
)

@Immutable
data class MonetPalette(
    val seedHex: String,
    val hueDegrees: Int,
    val isDark: Boolean,
    val bg: Color,
    val sLow: Color,
    val sCont: Color,
    val sHigh: Color,
    val sTop: Color,
    val onSurface: Color,
    val onVar: Color,
    val outline: Color,
    val outlineV: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryC: Color,
    val onPrimaryC: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryC: Color,
    val onSecondaryC: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryC: Color,
    val onTertiaryC: Color,
    val warn: Color,
    val onWarn: Color,
    val warnC: Color,
    val glow: Color,
    val primaryRamp: Map<Int, Color>,
    val secondaryRamp: Map<Int, Color>,
    val tertiaryRamp: Map<Int, Color>,
    val neutralRamp: Map<Int, Color>
) {
    val bgCard: Color get() = sCont
    val surface1: Color get() = sLow
    val surface2: Color get() = sHigh
    val borderSubtle: Color get() = outlineV
    val textPrimary: Color get() = onSurface
    val textSecondary: Color get() = onVar
    val textMuted: Color get() = onVar.copy(alpha = 0.78f)
    val primaryContainer: Color get() = primaryC
    val onPrimaryContainer: Color get() = onPrimaryC
}

val LocalMonetPalette = staticCompositionLocalOf {
    MonetEngine.buildPalette(seedHex = "#6750a4", dark = false)
}

object MonetEngine {

    const val DEFAULT_SEED_HEX = "#5B8CFF"

    val PRESET_SWATCHES = listOf(
        "#6750a4",
        "#0061a4",
        "#006e1c",
        "#984061",
        "#a04a00",
        "#006a6a",
        "#7d5260",
        "#b3261e"
    )

    val TONAL_STRIP_TONES = listOf(95, 90, 80, 70, 60, 50, 40, 30, 20)
    private val RAMP_TONES = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99)

    private fun srgbToLin(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun linToSrgb(c: Double): Double =
        if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055

    fun parseHexToRgb01(hex: String): Triple<Double, Double, Double> {
        val clean = hex.trim().removePrefix("#")
        val normalized = when (clean.length) {
            3 -> buildString {
                clean.forEach { ch -> append(ch).append(ch) }
            }
            6 -> clean
            8 -> clean.substring(2)
            else -> "6750a4"
        }
        val n = normalized.toIntOrNull(16) ?: 0x6750a4
        val r = ((n shr 16) and 0xFF) / 255.0
        val g = ((n shr 8) and 0xFF) / 255.0
        val b = (n and 0xFF) / 255.0
        return Triple(r, g, b)
    }

    fun hexToOklch(hex: String): OklchColor {
        val (sR, sG, sB) = parseHexToRgb01(hex)
        return srgb01ToOklch(sR, sG, sB)
    }

    fun srgb01ToOklch(sR: Double, sG: Double, sB: Double): OklchColor {
        val r = srgbToLin(sR)
        val g = srgbToLin(sG)
        val b = srgbToLin(sB)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        val C = sqrt(A * A + B * B)
        val H = (atan2(B, A) * 180.0 / PI + 360.0) % 360.0
        return OklchColor(L, C, H)
    }

    fun oklchToColor(L: Double, C: Double, H: Double): Color {
        val hr = H * PI / 180.0
        val a = C * cos(hr)
        val b = C * sin(hr)
        val l_ = L + 0.3963377774 * a + 0.2158037573 * b
        val m_ = L - 0.1055613458 * a - 0.0638541728 * b
        val s_ = L - 0.0894841775 * a - 1.2914855480 * b
        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_
        val rLin = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val gLin = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val bLin = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        val r = (linToSrgb(rLin.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255)
        val g = (linToSrgb(gLin.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255)
        val bl = (linToSrgb(bLin.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255)
        return Color(red = r, green = g, blue = bl)
    }

    fun colorToHex(color: Color): String {
        val argb = color.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format(Locale.US, "#%02x%02x%02x", r, g, b)
    }

    fun buildRamp(H: Double, C: Double): Map<Int, Color> {
        val out = LinkedHashMap<Int, Color>()
        for (t in RAMP_TONES) {
            val l = t / 100.0
            val taper = 1.0 - ((l - 0.5).pow(2.0)) * 1.4
            val cEff = max(0.0, C * max(0.25, taper))
            out[t] = oklchToColor(l, cEff, H)
        }
        return out
    }

    fun buildPalette(seedHex: String, dark: Boolean): MonetPalette {
        val normalizedHex = if (seedHex.startsWith("#")) seedHex.lowercase(Locale.US) else "#${seedHex.lowercase(Locale.US)}"
        val oklch = hexToOklch(normalizedHex)
        val H = oklch.h
        val C = oklch.c
        val acc = min(0.20, max(0.10, C))
        val neu = min(0.02, max(0.005, C * 0.10))

        val P = buildRamp(H, acc)
        val S = buildRamp(H, neu * 2.5)
        val T = buildRamp((H + 60.0) % 360.0, acc * 0.9)
        val N = buildRamp(H, neu)

        return if (dark) {
            val primary = P[80]!!
            MonetPalette(
                seedHex = normalizedHex,
                hueDegrees = H.roundToInt(),
                isDark = true,
                bg = oklchToColor(0.11, neu, H),
                sLow = oklchToColor(0.15, neu, H),
                sCont = oklchToColor(0.18, neu, H),
                sHigh = oklchToColor(0.22, neu, H),
                sTop = oklchToColor(0.26, neu, H),
                onSurface = N[90]!!,
                onVar = oklchToColor(0.78, neu * 2.0, H),
                outline = oklchToColor(0.55, neu * 2.0, H),
                outlineV = oklchToColor(0.30, neu * 1.5, H),
                primary = primary,
                onPrimary = P[20]!!,
                primaryC = P[30]!!,
                onPrimaryC = P[90]!!,
                secondary = S[80]!!,
                onSecondary = S[20]!!,
                secondaryC = S[30]!!,
                onSecondaryC = S[90]!!,
                tertiary = T[80]!!,
                onTertiary = T[20]!!,
                tertiaryC = T[30]!!,
                onTertiaryC = T[90]!!,
                warn = Color(0xFFFFB77C),
                onWarn = Color(0xFF4A2800),
                warnC = Color(0xFF693C00),
                glow = primary.copy(alpha = 0.28f),
                primaryRamp = P,
                secondaryRamp = S,
                tertiaryRamp = T,
                neutralRamp = N
            )
        } else {
            val primary = P[40]!!
            MonetPalette(
                seedHex = normalizedHex,
                hueDegrees = H.roundToInt(),
                isDark = false,
                bg = N[99]!!,
                sLow = N[95]!!,
                sCont = oklchToColor(0.93, neu, H),
                sHigh = N[90]!!,
                sTop = oklchToColor(0.86, neu * 1.2, H),
                onSurface = N[10]!!,
                onVar = N[30]!!,
                outline = N[50]!!,
                outlineV = N[80]!!,
                primary = primary,
                onPrimary = P[99]!!,
                primaryC = P[90]!!,
                onPrimaryC = P[10]!!,
                secondary = S[40]!!,
                onSecondary = S[99]!!,
                secondaryC = S[90]!!,
                onSecondaryC = S[10]!!,
                tertiary = T[40]!!,
                onTertiary = T[99]!!,
                tertiaryC = T[90]!!,
                onTertiaryC = T[10]!!,
                warn = Color(0xFF8B5000),
                onWarn = Color(0xFFFFFFFF),
                warnC = Color(0xFFFFDCBE),
                glow = primary.copy(alpha = 0.22f),
                primaryRamp = P,
                secondaryRamp = S,
                tertiaryRamp = T,
                neutralRamp = N
            )
        }
    }

    fun toMaterialColorScheme(palette: MonetPalette): ColorScheme {
        return if (palette.isDark) {
            darkColorScheme(
                primary = palette.primary,
                onPrimary = palette.onPrimary,
                primaryContainer = palette.primaryC,
                onPrimaryContainer = palette.onPrimaryC,
                secondary = palette.secondary,
                onSecondary = palette.onSecondary,
                secondaryContainer = palette.secondaryC,
                onSecondaryContainer = palette.onSecondaryC,
                tertiary = palette.tertiary,
                onTertiary = palette.onTertiary,
                tertiaryContainer = palette.tertiaryC,
                onTertiaryContainer = palette.onTertiaryC,
                background = palette.bg,
                onBackground = palette.onSurface,
                surface = palette.bg,
                onSurface = palette.onSurface,
                surfaceVariant = palette.sHigh,
                onSurfaceVariant = palette.onVar,
                surfaceContainerLowest = palette.bg,
                surfaceContainerLow = palette.sLow,
                surfaceContainer = palette.sCont,
                surfaceContainerHigh = palette.sHigh,
                surfaceContainerHighest = palette.sTop,
                outline = palette.outline,
                outlineVariant = palette.outlineV,
                error = palette.warn,
                onError = palette.onWarn,
                errorContainer = palette.warnC,
                onErrorContainer = palette.warn
            )
        } else {
            lightColorScheme(
                primary = palette.primary,
                onPrimary = palette.onPrimary,
                primaryContainer = palette.primaryC,
                onPrimaryContainer = palette.onPrimaryC,
                secondary = palette.secondary,
                onSecondary = palette.onSecondary,
                secondaryContainer = palette.secondaryC,
                onSecondaryContainer = palette.onSecondaryC,
                tertiary = palette.tertiary,
                onTertiary = palette.onTertiary,
                tertiaryContainer = palette.tertiaryC,
                onTertiaryContainer = palette.onTertiaryC,
                background = palette.bg,
                onBackground = palette.onSurface,
                surface = palette.bg,
                onSurface = palette.onSurface,
                surfaceVariant = palette.sHigh,
                onSurfaceVariant = palette.onVar,
                surfaceContainerLowest = palette.bg,
                surfaceContainerLow = palette.sLow,
                surfaceContainer = palette.sCont,
                surfaceContainerHigh = palette.sHigh,
                surfaceContainerHighest = palette.sTop,
                outline = palette.outline,
                outlineVariant = palette.outlineV,
                error = palette.warn,
                onError = palette.onWarn,
                errorContainer = palette.warnC,
                onErrorContainer = palette.warn
            )
        }
    }

    fun extractSeedHexFromBitmap(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
        var bestHex = "#6750a4"
        var bestScore = -1.0
        val pixels = IntArray(48 * 48)
        scaled.getPixels(pixels, 0, 48, 0, 0, 48, 48)
        for (px in pixels) {
            val r = ((px shr 16) and 0xFF) / 255.0
            val g = ((px shr 8) and 0xFF) / 255.0
            val b = (px and 0xFF) / 255.0
            val oklch = srgb01ToOklch(r, g, b)
            if (oklch.l > 0.15 && oklch.l < 0.90 && oklch.c > bestScore) {
                bestScore = oklch.c
                bestHex = String.format(
                    Locale.US,
                    "#%02x%02x%02x",
                    (r * 255.0).roundToInt().coerceIn(0, 255),
                    (g * 255.0).roundToInt().coerceIn(0, 255),
                    (b * 255.0).roundToInt().coerceIn(0, 255)
                )
            }
        }
        return bestHex
    }
}
