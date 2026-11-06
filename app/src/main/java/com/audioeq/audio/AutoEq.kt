package com.audioeq.audio

import android.content.Context
import com.audioeq.model.HeadphoneProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType { PK, LSC, HSC }

data class AutoEqFilter(
    val type: FilterType,
    val fc: Double,
    val gainDb: Double,
    val q: Double
) {
    fun toPeqFilter(): PeqFilter = PeqFilter(type.name, fc, gainDb, q)
}

data class AutoEqProfile(
    val preampDb: Double,
    val filters: List<AutoEqFilter>
)

object AutoEqParser {
    private val preampRe = Regex("""Preamp:\s*(-?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val filterRe = Regex(
        """Filter\s*\d+:\s*ON\s+(PK|PEQ|LSC|HSC|LS|HS)\s+Fc\s+(\d+(?:\.\d+)?)\s*Hz\s+Gain\s+([+-]?\d+(?:\.\d+)?)\s*dB(?:\s+Q\s+(\d+(?:\.\d+)?))?""",
        RegexOption.IGNORE_CASE
    )

    fun parseParametric(text: String): AutoEqProfile {
        val preamp = preampRe.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val filters = filterRe.findAll(text).mapNotNull { m ->
            val type = when (m.groupValues[1].uppercase(Locale.US)) {
                "PK", "PEQ" -> FilterType.PK
                "LSC", "LS" -> FilterType.LSC
                "HSC", "HS" -> FilterType.HSC
                else -> return@mapNotNull null
            }
            val qVal = m.groupValues[4].ifEmpty { "0.7071" }.toDouble()
            AutoEqFilter(
                type = type,
                fc = m.groupValues[2].toDouble(),
                gainDb = m.groupValues[3].toDouble(),
                q = qVal
            )
        }.toList()
        require(filters.isNotEmpty()) { "No filters found: wrong file?" }
        return AutoEqProfile(preamp, filters)
    }

    fun parseGraphic(text: String): List<Pair<Double, Double>> {
        val line = text.lineSequence().firstOrNull { it.trim().startsWith("GraphicEQ:", true) }
            ?: return emptyList()
        return line.substringAfter(":").split(";").mapNotNull { point ->
            val p = point.trim().split(Regex("\\s+"))
            val f = p.getOrNull(0)?.toDoubleOrNull()
            val g = p.getOrNull(1)?.toDoubleOrNull()
            if (p.size >= 2 && f != null && g != null) f to g else null
        }
    }
}

class Biquad(
    type: FilterType,
    private val fs: Double,
    f0: Double,
    gainDb: Double,
    q: Double
) {
    val b0: Double
    val b1: Double
    val b2: Double
    val a1: Double
    val a2: Double

    init {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val cw = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val sqA2alpha = 2.0 * sqrt(a) * alpha
        val nb0: Double
        val nb1: Double
        val nb2: Double
        val na0: Double
        val na1: Double
        val na2: Double
        when (type) {
            FilterType.PK -> {
                nb0 = 1.0 + alpha * a
                nb1 = -2.0 * cw
                nb2 = 1.0 - alpha * a
                na0 = 1.0 + alpha / a
                na1 = -2.0 * cw
                na2 = 1.0 - alpha / a
            }
            FilterType.LSC -> {
                nb0 = a * ((a + 1.0) - (a - 1.0) * cw + sqA2alpha)
                nb1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cw)
                nb2 = a * ((a + 1.0) - (a - 1.0) * cw - sqA2alpha)
                na0 = (a + 1.0) + (a - 1.0) * cw + sqA2alpha
                na1 = -2.0 * ((a - 1.0) + (a + 1.0) * cw)
                na2 = (a + 1.0) + (a - 1.0) * cw - sqA2alpha
            }
            FilterType.HSC -> {
                nb0 = a * ((a + 1.0) + (a - 1.0) * cw + sqA2alpha)
                nb1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cw)
                nb2 = a * ((a + 1.0) + (a - 1.0) * cw - sqA2alpha)
                na0 = (a + 1.0) - (a - 1.0) * cw + sqA2alpha
                na1 = 2.0 * ((a - 1.0) - (a + 1.0) * cw)
                na2 = (a + 1.0) - (a - 1.0) * cw - sqA2alpha
            }
        }
        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun magnitudeDb(f: Double): Double {
        val w = 2.0 * PI * f / fs
        val cw = cos(w)
        val sw = sin(w)
        val c2 = cos(2.0 * w)
        val s2 = sin(2.0 * w)
        val nr = b0 + b1 * cw + b2 * c2
        val ni = -(b1 * sw + b2 * s2)
        val dr = 1.0 + a1 * cw + a2 * c2
        val di = -(a1 * sw + a2 * s2)
        return 20.0 * log10(hypot(nr, ni) / max(hypot(dr, di), 1e-20))
    }
}

object AutoEqMapper {
    const val FS = 48_000.0
    const val MAX_GAIN_DB = 12.0

    fun responseDb(p: AutoEqProfile, f: Double, fs: Double = FS): Double =
        p.filters.sumOf { Biquad(it.type, fs, it.fc, it.gainDb, it.q).magnitudeDb(f) }

    val TEN_BANDS = doubleArrayOf(
        31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
    )

    data class TenBand(
        val gainsDb: List<Double>,
        val preampDb: Double,
        val clipped: Boolean
    )

    fun fixedBandToTenBand(fixedBand: AutoEqProfile): TenBand {
        var clipped = false
        val gains = TEN_BANDS.map { center ->
            val nearest = fixedBand.filters.minByOrNull { abs(ln(it.fc / center)) }!!
            val g = nearest.gainDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)
            if (g != nearest.gainDb) clipped = true
            g
        }
        return TenBand(gains, fixedBand.preampDb, clipped)
    }

    val THIRD_OCTAVE = doubleArrayOf(
        20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0,
        400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0,
        5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0
    )

    data class DpBand(
        val cutoffHz: Float,
        val gainDb: Float
    )

    fun toDynamicsBands(p: AutoEqProfile): List<DpBand> =
        THIRD_OCTAVE.mapIndexed { i, center ->
            val upper = if (i == THIRD_OCTAVE.lastIndex) 22_000.0 else center * 2.0.pow(1.0 / 6.0)
            val g = responseDb(p, center).coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)
            DpBand(upper.toFloat(), g.toFloat())
        }

    fun toNativeMillibels(
        p: AutoEqProfile,
        centersHz: List<Double>,
        minMb: Int,
        maxMb: Int
    ): List<Int> =
        centersHz.map { (responseDb(p, it) * 100.0).roundToInt().coerceIn(minMb, maxMb) }

    fun safePreampDb(p: AutoEqProfile): Double {
        val peak = (p.filters.map { responseDb(p, it.fc) } + 0.0).max()
        return min(p.preampDb, -peak)
    }

    fun fromPeqFilters(filters: List<PeqFilter>, preampDb: Double = 0.0): AutoEqProfile {
        val mapped = filters.map {
            val ft = when (it.type.uppercase(Locale.US)) {
                "LSC", "LS" -> FilterType.LSC
                "HSC", "HS" -> FilterType.HSC
                else -> FilterType.PK
            }
            AutoEqFilter(ft, it.fc, it.gain, it.q)
        }
        val autoPreamp = if (abs(preampDb) > 0.001) preampDb else {
            val tmp = AutoEqProfile(0.0, mapped)
            safePreampDb(tmp)
        }
        return AutoEqProfile(autoPreamp, mapped)
    }
}

data class PeqFilter(
    val type: String,
    val fc: Double,
    val gain: Double,
    val q: Double
) {
    fun toAutoEqFilter(): AutoEqFilter {
        val ft = when (type.uppercase(Locale.US)) {
            "LSC", "LS" -> FilterType.LSC
            "HSC", "HS" -> FilterType.HSC
            else -> FilterType.PK
        }
        return AutoEqFilter(ft, fc, gain, q)
    }
}

object AutoEqManager {
    data class Flt(val type: String, val fc: Double, val gain: Double, val q: Double) {
        fun toPeqFilter() = PeqFilter(type, fc, gain, q)
    }

    fun taper(freqHz: Double): Double = AutoEq.taper(freqHz)

    fun peqToCorrection31WithTrim(
        filters: List<Flt>,
        fullRange: Boolean = false
    ): Pair<List<Float>, Float> = AutoEq.peqToCorrection31WithTrim(filters, fullRange)

    fun parseParametricEq(text: String): ParsedPeq = AutoEq.parseParametricEq(text)
    fun parseGraphic(text: String) = AutoEq.parseGraphic(text)
    fun fromBuiltInProfile(profile: HeadphoneProfile) = AutoEq.fromBuiltInProfile(profile)
}

typealias Flt = AutoEqManager.Flt

data class ParsedPeq(val filters: List<PeqFilter>, val preamp: Double = 0.0) : List<PeqFilter> by filters

data class IndexEntry(
    val name: String,
    val relPath: String,
    val source: String?,
    val rig: String?
)

data class AutoEqFetchResult(
    val entries: List<IndexEntry>,
    val fromCache: Boolean,
    val isRateLimited: Boolean = false,
    val errorMessage: String? = null
)

object AutoEq {

    fun parseIndexMd(md: String): List<IndexEntry> =
        md.lineSequence().mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("- [") && !line.startsWith("* [")) return@mapNotNull null
            val splitPos = line.indexOf("](")
            if (splitPos <= 3) return@mapNotNull null
            val name = line.substring(3, splitPos).trim()
            val rest = line.substring(splitPos + 2)
            val byPos = rest.lastIndexOf(") by ")
            val rawPath: String
            val tail: String
            if (byPos != -1) {
                rawPath = rest.substring(0, byPos).trim()
                tail = rest.substring(byPos + 5).trim()
            } else {
                val closePos = rest.lastIndexOf(')')
                if (closePos == -1) return@mapNotNull null
                rawPath = rest.substring(0, closePos).trim()
                val rawTail = rest.substring(closePos + 1).trim()
                tail = if (rawTail.startsWith("by ")) rawTail.substring(3).trim() else rawTail
            }
            val onIdx = tail.indexOf(" on ")
            val sourcePart = if (onIdx != -1) tail.substring(0, onIdx).trim() else tail.trim()
            val rigPart = if (onIdx != -1) tail.substring(onIdx + 4).trim() else ""
            IndexEntry(
                name = name,
                relPath = rawPath.removePrefix("./").removePrefix("/"),
                source = sourcePart.ifEmpty { null },
                rig = rigPart.ifEmpty { null }
            )
        }.toList()

    private val PEQ_LINE = Regex(
        """^Filter\s*\d+:\s*ON\s+(PK|PEQ|LSC|LS|HSC|HS)\s+Fc\s+([\d.]+)\s*Hz\s+Gain\s+([+-]?[\d.]+)\s*dB(?:\s+Q\s+([\d.]+))?""",
        RegexOption.IGNORE_CASE
    )

    fun parsePeq(text: String): ParsedPeq {
        val preampRegex = Regex("""Preamp:\s*([+-]?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)
        val preamp = preampRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val filters = text.lineSequence().mapNotNull { raw ->
            val m = PEQ_LINE.find(raw.trim()) ?: return@mapNotNull null
            val rawType = m.groupValues[1].uppercase(Locale.US)
            val normType = when (rawType) {
                "PEQ" -> "PK"
                "LS" -> "LSC"
                "HS" -> "HSC"
                else -> rawType
            }
            val fc = m.groupValues[2].toDouble()
            val gain = m.groupValues[3].toDouble()
            val q = m.groupValues[4].ifEmpty { "0.7071" }.toDouble()
            PeqFilter(normType, fc, gain, q)
        }.toList()
        return ParsedPeq(filters, preamp)
    }

    fun parseParametricEq(text: String): ParsedPeq = parsePeq(text)

    fun parseGraphic(text: String): List<Pair<Double, Double>> = parseGraphicEq(text)

    fun fromBuiltInProfile(profile: HeadphoneProfile): List<PeqFilter> = profile.peqFilters

    fun parseGraphicEq(text: String): List<Pair<Double, Double>> {
        val body = text.substringAfter("GraphicEQ:", missingDelimiterValue = "")
        if (body.isBlank()) return emptyList()
        return body.split(';')
            .mapNotNull { pair ->
                val parts = pair.trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val f = parts[0].toDoubleOrNull()
                    val g = parts[1].toDoubleOrNull()
                    if (f != null && g != null) f to g else null
                } else {
                    null
                }
            }
            .sortedBy { it.first }
    }

    fun biquadDb(
        type: String,
        fc: Double,
        gain: Double,
        q: Double,
        f: Double,
        fs: Double = AutoEqMapper.FS
    ): Double {
        if (abs(gain) < 1e-9 || q <= 0.0 || fc <= 0.0 || fc >= fs * 0.499) return 0.0
        val filterType = when (type.uppercase(Locale.US)) {
            "LSC", "LS" -> FilterType.LSC
            "HSC", "HS" -> FilterType.HSC
            "PK", "PEQ" -> FilterType.PK
            else -> return 0.0
        }
        return Biquad(filterType, fs, fc, gain, q).magnitudeDb(f)
    }

    fun taper(f: Double): Double = when {
        f <= 10000.0 -> 1.0
        f >= 14000.0 -> 0.0
        else -> 1.0 - ln(f / 10000.0) / ln(14000.0 / 10000.0)
    }

    fun peqToCorrection31(
        filters: List<PeqFilter>,
        fDp: DoubleArray = Dsp.F_DP,
        maxBoostDb: Double = 12.0,
        maxCutDb: Double = -12.0
    ): DoubleArray {
        val raw = DoubleArray(fDp.size) { k ->
            val f = fDp[k]
            filters.sumOf { biquadDb(it.type, it.fc, it.gain, it.q, f) }
        }
        return DoubleArray(fDp.size) { k ->
            raw[k].coerceIn(maxCutDb, maxBoostDb)
        }
    }

    fun peqRawUnclamped31(
        filters: List<PeqFilter>,
        fDp: DoubleArray = Dsp.F_DP
    ): DoubleArray {
        return DoubleArray(fDp.size) { k ->
            val f = fDp[k]
            filters.sumOf { biquadDb(it.type, it.fc, it.gain, it.q, f) }
        }
    }

    fun graphicToCorrection31(
        pts: List<Pair<Double, Double>>,
        fDp: DoubleArray = Dsp.F_DP,
        maxBoostDb: Double = 12.0,
        maxCutDb: Double = -12.0
    ): DoubleArray {
        require(pts.size >= 2)
        val xs = DoubleArray(pts.size) { ln(pts[it].first) }
        val ys = DoubleArray(pts.size) { pts[it].second }
        val raw = DoubleArray(fDp.size) { k ->
            val x = ln(fDp[k])
            when {
                x <= xs.first() -> ys.first()
                x >= xs.last() -> ys.last()
                else -> {
                    var idx = xs.binarySearch(x)
                    if (idx < 0) idx = -idx - 2
                    val i = idx.coerceIn(0, xs.size - 2)
                    val t = (x - xs[i]) / (xs[i + 1] - xs[i])
                    ys[i] + t * (ys[i + 1] - ys[i])
                }
            }
        }
        val midMean = (11..19).map { raw[it] }.average()
        return DoubleArray(fDp.size) { k ->
            ((raw[k] - midMean) * taper(fDp[k])).coerceIn(maxCutDb, maxBoostDb)
        }
    }

    fun peqToCorrection31(
        filters: List<PeqFilter>,
        strength: Double = 1.0,
        sampleRate: Double = AutoEqMapper.FS,
        fullRange: Boolean = false,
        tiltBassDb: Double = 0.0,
        tiltTrebleDb: Double = 0.0
    ): DoubleArray {
        val maxBoost = if (fullRange) 12.0 else 12.0
        val base = DoubleArray(Dsp.F_DP.size) { k ->
            val f = Dsp.F_DP[k]
            filters.sumOf { biquadDb(it.type, it.fc, it.gain, it.q, f, sampleRate) }
                .coerceIn(-12.0, maxBoost)
        }
        val tilt = computeTiltCurve31(tiltBassDb, tiltTrebleDb)
        val s = strength.coerceIn(0.0, 1.0)
        return DoubleArray(base.size) { (base[it] * s + tilt[it]).coerceIn(-15.0, 15.0) }
    }

    fun graphicToCorrection31(
        pts: List<Pair<Double, Double>>,
        strength: Double = 1.0,
        fullRange: Boolean = false,
        tiltBassDb: Double = 0.0,
        tiltTrebleDb: Double = 0.0
    ): DoubleArray {
        val maxBoost = if (fullRange) 12.0 else 12.0
        val base = graphicToCorrection31(pts, maxBoostDb = maxBoost)
        val tilt = computeTiltCurve31(tiltBassDb, tiltTrebleDb)
        val s = strength.coerceIn(0.0, 1.0)
        return DoubleArray(base.size) { (base[it] * s + tilt[it]).coerceIn(-15.0, 15.0) }
    }

    fun peqToCorrection31WithTrim(filters: List<Flt>, fullRange: Boolean = false): Pair<List<Float>, Float> {
        val peqFilters = filters.map { it.toPeqFilter() }
        val maxBoost = if (fullRange) 12.0 else 6.0
        val corr = peqToCorrection31(peqFilters, maxBoostDb = maxBoost).map { it.toFloat() }
        val trim = computeTrimmedDb(peqFilters, fullRange).toFloat()
        return corr to trim
    }

    fun computePeqTrimmedDb(
        filters: List<PeqFilter>,
        sampleRate: Double = AutoEqMapper.FS,
        fullRange: Boolean = false,
        tiltBassDb: Double = 0.0,
        tiltTrebleDb: Double = 0.0
    ): Double = computeTrimmedDb(filters, fullRange)

    fun computeTrimmedDb(
        filters: List<PeqFilter>,
        fullRange: Boolean = false
    ): Double {
        if (filters.isEmpty()) return 0.0
        val profile = AutoEqMapper.fromPeqFilters(filters)
        return abs(AutoEqMapper.safePreampDb(profile))
    }

    fun computeTiltCurve31(
        bassDb: Double,
        trebleDb: Double,
        fDp: DoubleArray = Dsp.F_DP
    ): DoubleArray {
        val bClamped = bassDb.coerceIn(-6.0, 6.0)
        val tClamped = trebleDb.coerceIn(-4.0, 4.0)
        if (abs(bClamped) < 1e-6 && abs(tClamped) < 1e-6) return DoubleArray(fDp.size)
        return DoubleArray(fDp.size) { k ->
            val f = fDp[k]
            val b = biquadDb("LSC", 105.0, bClamped, 0.7, f)
            val t = biquadDb("HSC", 6000.0, tClamped, 0.7, f)
            b + t
        }
    }

    fun mapCorrection31ToNativeBands(
        correction31Db: DoubleArray,
        nativeBandsHz: List<Pair<Double, Double>>,
        strength: Double = 0.8
    ): IntArray {
        val s = strength.coerceIn(0.0, 1.0)
        return IntArray(nativeBandsHz.size) { b ->
            val (fLo, fHi) = nativeBandsHz[b]
            val covered = Dsp.F_DP.indices.filter { k ->
                Dsp.F_DP[k] >= fLo && Dsp.F_DP[k] <= fHi
            }
            val meanDb = if (covered.isNotEmpty()) {
                covered.map { correction31Db[it] * s }.average()
            } else {
                val fCenter = sqrt(max(20.0, fLo) * max(20.0, fHi))
                val nearestIdx = Dsp.F_DP.indices.minByOrNull { abs(ln(Dsp.F_DP[it]) - ln(fCenter)) } ?: 0
                correction31Db[nearestIdx] * s
            }
            val clampedDb = meanDb.coerceIn(-12.0, 12.0)
            (clampedDb * 100.0).toInt()
        }
    }

    fun exportToParametricEqText(
        profileName: String,
        preampDb: Double,
        filters: List<PeqFilter>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# AutoEq / Equalizer APO ParametricEQ export — $profileName")
        sb.appendLine(String.format(Locale.US, "Preamp: %.1f dB", preampDb))
        filters.forEachIndexed { idx, f ->
            sb.appendLine(
                String.format(
                    Locale.US,
                    "Filter %d: ON %s Fc %.1f Hz Gain %+.1f dB Q %.2f",
                    idx + 1,
                    f.type,
                    f.fc,
                    f.gain,
                    f.q
                )
            )
        }
        return sb.toString()
    }

    fun export31ToGraphicEqText(curve31Db: DoubleArray): String {
        val pairs = Dsp.F_DP.indices.joinToString("; ") { k ->
            val f = Dsp.F_DP[k]
            val fStr = if (f == f.toLong().toDouble()) f.toLong().toString() else String.format(Locale.US, "%.1f", f)
            String.format(Locale.US, "%s %.2f", fStr, curve31Db[k])
        }
        return "GraphicEQ: $pairs"
    }

    private val NOISE_WORDS = setOf(
        "anc", "off", "on", "active", "passive", "wireless", "wired", "bluetooth", "edition"
    )

    fun normalizeTokens(name: String): Set<String> {
        return name.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in NOISE_WORDS }
            .toSet()
    }

    fun matchScore(btProductName: String, candidateModelName: String): Double {
        val a = normalizeTokens(btProductName)
        val b = normalizeTokens(candidateModelName)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union > 0.0) inter / union else 0.0
    }
}

class AutoEqRepo(private val context: Context) {

    private val repository = AutoEqRepository(context)

    companion object {
        val STARTER_ENTRIES: List<IndexEntry> = listOf(
            IndexEntry("Sennheiser HD 650", "oratory1990/over-ear/Sennheiser HD 650", "oratory1990", "over-ear"),
            IndexEntry("Sennheiser HD 600", "oratory1990/over-ear/Sennheiser HD 600", "oratory1990", "over-ear"),
            IndexEntry("Sony WH-1000XM5", "oratory1990/over-ear/Sony WH-1000XM5", "oratory1990", "over-ear"),
            IndexEntry("Sony WF-1000XM5", "oratory1990/in-ear/Sony WF-1000XM5", "oratory1990", "in-ear"),
            IndexEntry("Apple AirPods Pro 2", "crinacle/Bruel & Kjaer 5128 in-ear/Apple AirPods Pro 2", "crinacle", "in-ear"),
            IndexEntry("Apple AirPods Max", "oratory1990/over-ear/Apple AirPods Max", "oratory1990", "over-ear"),
            IndexEntry("Samsung Galaxy Buds2 Pro", "crinacle/711 in-ear/Samsung Galaxy Buds2 Pro", "crinacle", "711 in-ear"),
            IndexEntry("Beyerdynamic DT 990 Pro", "oratory1990/over-ear/Beyerdynamic DT 990 Pro", "oratory1990", "over-ear"),
            IndexEntry("Beyerdynamic DT 770 Pro (80 Ohm)", "oratory1990/over-ear/Beyerdynamic DT 770 Pro (80 Ohm)", "oratory1990", "over-ear"),
            IndexEntry("HIFIMAN Sundara", "oratory1990/over-ear/HIFIMAN Sundara", "oratory1990", "over-ear"),
            IndexEntry("HIFIMAN Edition XS", "oratory1990/over-ear/HIFIMAN Edition XS", "oratory1990", "over-ear"),
            IndexEntry("Audio-Technica ATH-M50x", "oratory1990/over-ear/Audio-Technica ATH-M50x", "oratory1990", "over-ear"),
            IndexEntry("Moondrop Blessing 3", "crinacle/711 in-ear/Moondrop Blessing 3", "crinacle", "711 in-ear"),
            IndexEntry("Truthear HEXA", "crinacle/711 in-ear/Truthear HEXA", "crinacle", "711 in-ear"),
            IndexEntry("AKG K371", "oratory1990/over-ear/AKG K371", "oratory1990", "over-ear"),
            IndexEntry("Bose QuietComfort Ultra Headphones", "rtings/over-ear/Bose QuietComfort Ultra Headphones", "rtings", "over-ear")
        )
    }

    suspend fun loadIndex(forceRefresh: Boolean = false): AutoEqFetchResult = withContext(Dispatchers.IO) {
        val assetEntries = repository.loadIndex(forceRefresh = forceRefresh)
        if (assetEntries.isNotEmpty()) {
            val mapped = assetEntries.map {
                IndexEntry(
                    name = it.name,
                    relPath = it.path,
                    source = it.source,
                    rig = it.form
                )
            }
            return@withContext AutoEqFetchResult(entries = mapped, fromCache = !forceRefresh)
        }
        AutoEqFetchResult(entries = STARTER_ENTRIES, fromCache = true)
    }

    suspend fun fetchProfileForEntry(entry: IndexEntry): Pair<List<PeqFilter>, List<Pair<Double, Double>>> =
        withContext(Dispatchers.IO) {
            val autoEntry = AutoEqEntry(
                name = entry.name,
                source = entry.source ?: "oratory1990",
                form = entry.rig ?: "over-ear",
                path = entry.relPath
            )
            try {
                val text = repository.fetch(autoEntry, AutoEqKind.PARAMETRIC)
                val parsed = AutoEqParser.parseParametric(text)
                if (parsed.filters.isNotEmpty()) {
                    return@withContext parsed.filters.map { it.toPeqFilter() } to emptyList()
                }
            } catch (_: Exception) {
            }
            emptyList<PeqFilter>() to emptyList()
        }
}
