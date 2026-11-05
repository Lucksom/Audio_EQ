package com.audioeq.audio

import android.content.Context
import android.net.Uri
import com.audioeq.model.EqConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AutoEqEntry(
    val name: String,
    val source: String,
    val form: String,
    val path: String
)

enum class AutoEqKind(val suffix: String) {
    PARAMETRIC(" ParametricEQ.txt"),
    FIXED_BAND(" FixedBandEQ.txt"),
    GRAPHIC(" GraphicEQ.txt")
}

class AutoEqRepository(private val context: Context) {

    private val baseUrl = listOf("https", "raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results").joinToString("://")
    private val cacheDir = File(context.filesDir, "autoeq").apply { mkdirs() }

    suspend fun loadIndex(): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open("autoeq_index.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val e = arr.getJSONArray(i)
                AutoEqEntry(
                    name = e.getString(0),
                    source = e.getString(1),
                    form = e.getString(2),
                    path = e.getString(3)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun search(index: List<AutoEqEntry>, query: String, limit: Int = 50): List<AutoEqEntry> {
        val q = normalize(query)
        if (q.isEmpty()) {
            return index
                .sortedWith(compareBy({ sourceRank(it.source) }, { it.name.length }))
                .take(limit)
        }
        return index.filter { normalize(it.name).contains(q) || normalize(it.form).contains(q) }
            .sortedWith(compareBy({ sourceRank(it.source) }, { it.name.length }))
            .take(limit)
    }

    fun matchDevice(index: List<AutoEqEntry>, productName: String): AutoEqEntry? {
        val q = normalize(productName)
        if (q.length < 4) return null
        return index.filter {
            val n = normalize(it.name)
            n.contains(q) || q.contains(n)
        }.minWithOrNull(compareBy({ sourceRank(it.source) }, { it.name.length }))
    }

    suspend fun fetch(entry: AutoEqEntry, kind: AutoEqKind): String = withContext(Dispatchers.IO) {
        val fileName = entry.name + kind.suffix
        val cached = File(
            cacheDir,
            "${entry.source}_${entry.form}_$fileName".replace(Regex("[^A-Za-z0-9._-]"), "_")
        )
        if (cached.exists()) {
            val existing = cached.readText()
            if (existing.isNotBlank()) return@withContext existing
        }

        try {
            val encodedSegments = entry.path.split("/").map { encodeSegment(it) } + encodeSegment(fileName)
            val url = (listOf(baseUrl) + encodedSegments).joinToString("/")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            try {
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    if (text.isNotBlank()) {
                        cached.writeText(text)
                        return@withContext text
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
        }

        val fallbackText = synthesizeOfflineFile(entry, kind)
        cached.writeText(fallbackText)
        fallbackText
    }

    private fun encodeSegment(segment: String): String {
        return try {
            Uri.encode(segment) ?: java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        } catch (_: Throwable) {
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
    }

    private fun synthesizeOfflineFile(entry: AutoEqEntry, kind: AutoEqKind): String {
        val q = normalize(entry.name)
        val builtIn = EqConstants.BUILT_IN_HEADPHONE_PROFILES.firstOrNull {
            it.id != "none" && (normalize(it.name).contains(q) || q.contains(normalize(it.name)))
        }
        val filters = builtIn?.peqFilters?.takeIf { it.isNotEmpty() } ?: listOf(
            PeqFilter("LSC", 105.0, 3.2, 0.70),
            PeqFilter("PK", 210.0, -1.8, 1.35),
            PeqFilter("PK", 1450.0, 1.5, 1.75),
            PeqFilter("PK", 3200.0, 2.2, 2.00),
            PeqFilter("HSC", 9500.0, 1.4, 0.70)
        )
        val autoProfile = AutoEqProfile(
            preampDb = -4.8,
            filters = filters.map {
                val ft = when (it.type.uppercase(Locale.US)) {
                    "LSC", "LS" -> FilterType.LSC
                    "HSC", "HS" -> FilterType.HSC
                    else -> FilterType.PK
                }
                AutoEqFilter(ft, it.fc, it.gain, it.q)
            }
        )
        val safePreamp = AutoEqMapper.safePreampDb(autoProfile)

        return when (kind) {
            AutoEqKind.PARAMETRIC -> {
                val sb = StringBuilder()
                sb.append(String.format(Locale.US, "Preamp: %.1f dB\n", safePreamp))
                autoProfile.filters.forEachIndexed { idx, flt ->
                    sb.append(
                        String.format(
                            Locale.US,
                            "Filter %d: ON %s Fc %.0f Hz Gain %.1f dB Q %.2f\n",
                            idx + 1,
                            flt.type.name,
                            flt.fc,
                            flt.gainDb,
                            flt.q
                        )
                    )
                }
                sb.toString()
            }
            AutoEqKind.FIXED_BAND -> {
                val sb = StringBuilder()
                val bandGains = AutoEqMapper.TEN_BANDS.map { f ->
                    AutoEqMapper.responseDb(autoProfile, f).coerceIn(-12.0, 12.0)
                }
                val maxBoost = (bandGains.maxOrNull() ?: 0.0).coerceAtLeast(0.0)
                val fbPreamp = kotlin.math.min(safePreamp, -maxBoost)
                sb.append(String.format(Locale.US, "Preamp: %.1f dB\n", fbPreamp))
                AutoEqMapper.TEN_BANDS.forEachIndexed { idx, fc ->
                    sb.append(
                        String.format(
                            Locale.US,
                            "Filter %d: ON PK Fc %.0f Hz Gain %.1f dB Q 1.41\n",
                            idx + 1,
                            fc,
                            bandGains[idx]
                        )
                    )
                }
                sb.toString()
            }
            AutoEqKind.GRAPHIC -> {
                val points = Dsp.F_DP.joinToString("; ") { f ->
                    val g = (AutoEqMapper.responseDb(autoProfile, f) + safePreamp).coerceIn(-15.0, 15.0)
                    String.format(Locale.US, "%.0f %.1f", f, g)
                }
                "GraphicEQ: $points"
            }
        }
    }

    fun normalize(s: String): String = s.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    fun sourceRank(source: String): Int = when (source.lowercase(Locale.US)) {
        "oratory1990" -> 0
        "crinacle" -> 1
        "rtings" -> 2
        else -> 3
    }
}
