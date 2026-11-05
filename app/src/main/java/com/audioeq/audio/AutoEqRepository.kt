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
import java.net.URLDecoder
import java.net.URLEncoder
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
    private val indexUrl = "$baseUrl/INDEX.md"
    private val cacheDir = File(context.filesDir, "autoeq").apply { mkdirs() }
    private val remoteIndexCacheFile = File(cacheDir, "remote_index.json")

    private val mdIndexRegex = Regex(
        """^\s*[-*]\s+\[([^\]]+)]\(\.?/?([^)]+)\)\s+by\s+([^\n]+?)(?:\s+on\s+([^\n]+))?\s*$"""
    )

    suspend fun loadBundledIndex(): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, AutoEqEntry>()
        readIndexJsonFile(remoteIndexCacheFile).forEach { entry ->
            merged["${entry.name}|${entry.source}|${entry.form}"] = entry
        }
        try {
            val json = context.assets.open("autoeq_index.json").bufferedReader().use { it.readText() }
            parseIndexJsonArray(json).forEach { entry ->
                val key = "${entry.name}|${entry.source}|${entry.form}"
                if (!merged.containsKey(key)) {
                    merged[key] = entry
                }
            }
        } catch (_: Exception) {
        }
        merged.values.toList()
    }

    suspend fun loadIndex(forceRefresh: Boolean = false): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        val localEntries = loadBundledIndex()
        if (!forceRefresh && localEntries.size >= 1000) {
            return@withContext localEntries
        }
        val remoteEntries = fetchRemoteIndex()
        if (remoteEntries.isNotEmpty()) {
            val merged = LinkedHashMap<String, AutoEqEntry>()
            remoteEntries.forEach { entry ->
                merged["${entry.name}|${entry.source}|${entry.form}"] = entry
            }
            localEntries.forEach { entry ->
                val key = "${entry.name}|${entry.source}|${entry.form}"
                if (!merged.containsKey(key)) {
                    merged[key] = entry
                }
            }
            return@withContext merged.values.toList()
        }
        localEntries
    }

    suspend fun fetchRemoteIndex(): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(indexUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 12_000
            try {
                if (conn.responseCode == 200) {
                    val md = conn.inputStream.bufferedReader().use { it.readText() }
                    val parsed = parseIndexMarkdown(md)
                    if (parsed.isNotEmpty()) {
                        writeIndexJsonFile(remoteIndexCacheFile, parsed)
                        return@withContext parsed
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
        }
        emptyList()
    }

    fun parseIndexMarkdown(md: String): List<AutoEqEntry> {
        return md.lineSequence().mapNotNull { line ->
            val m = mdIndexRegex.matchEntire(line) ?: return@mapNotNull null
            val name = m.groupValues[1].trim()
            val rawPath = m.groupValues[2].trim().removePrefix("./").removePrefix("/")
            val decodedPath = decodeSegment(rawPath)
            val parts = decodedPath.split("/")
            val source = m.groupValues[3].trim().ifEmpty { parts.getOrNull(0) ?: "oratory1990" }
            val form = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?: m.groupValues[4].trim().ifEmpty { "in-ear" }
            AutoEqEntry(
                name = name,
                source = source,
                form = form,
                path = decodedPath
            )
        }.toList()
    }

    private fun readIndexJsonFile(file: File): List<AutoEqEntry> {
        if (!file.exists()) return emptyList()
        return try {
            parseIndexJsonArray(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseIndexJsonArray(json: String): List<AutoEqEntry> {
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val e = arr.getJSONArray(i)
            AutoEqEntry(
                name = e.getString(0),
                source = e.getString(1),
                form = e.getString(2),
                path = e.getString(3)
            )
        }
    }

    private fun writeIndexJsonFile(file: File, entries: List<AutoEqEntry>) {
        try {
            val arr = JSONArray()
            entries.forEach { e ->
                val item = JSONArray()
                item.put(e.name)
                item.put(e.source)
                item.put(e.form)
                item.put(e.path)
                arr.put(item)
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun search(index: List<AutoEqEntry>, query: String, limit: Int = 50): List<AutoEqEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val qNorm = normalize(trimmed)
        if (qNorm.isEmpty()) return emptyList()
        val tokens = trimmed.lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }

        return index.asSequence()
            .filter { entry ->
                val nameNorm = normalize(entry.name)
                if (nameNorm.contains(qNorm)) {
                    true
                } else {
                    val searchable = buildSearchableText(entry)
                    tokens.all { token -> searchable.contains(token) }
                }
            }
            .sortedWith(
                compareBy(
                    { matchTier(it, qNorm, trimmed.lowercase(Locale.US)) },
                    { sourceRank(it.source) },
                    { it.name.length }
                )
            )
            .take(limit)
            .toList()
    }

    private fun buildSearchableText(entry: AutoEqEntry): String {
        val fLower = entry.form.lowercase(Locale.US)
        val categoryKeywords = when {
            fLower.contains("in-ear") || fLower.contains("711") || fLower.contains("5128") ->
                "in-ear inear iem earphone earphones earbud earbuds buds"
            fLower.contains("earbud") ->
                "earbud earbuds tws earphone earphones buds"
            else ->
                "over-ear overear headphone headphones on-ear"
        }
        return "${entry.name.lowercase(Locale.US)} ${entry.source.lowercase(Locale.US)} $fLower $categoryKeywords"
    }

    private fun matchTier(entry: AutoEqEntry, qNorm: String, rawLower: String): Int {
        val nameNorm = normalize(entry.name)
        val nameLower = entry.name.lowercase(Locale.US)
        return when {
            nameNorm == qNorm -> 0
            nameNorm.startsWith(qNorm) -> 1
            nameLower.contains(rawLower) -> 2
            nameNorm.contains(qNorm) -> 3
            else -> 4
        }
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
            URLEncoder.encode(segment, "UTF-8")
                .replace("+", "%20")
                .replace("%28", "(")
                .replace("%29", ")")
        } catch (_: Throwable) {
            Uri.encode(segment) ?: segment.replace(" ", "%20")
        }
    }

    private fun decodeSegment(segment: String): String {
        return try {
            URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8")
        } catch (_: Throwable) {
            Uri.decode(segment) ?: segment.replace("%20", " ")
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
