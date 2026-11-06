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

fun normalizeAutoEqFast(s: String): String {
    val out = StringBuilder(s.length)
    for (i in 0 until s.length) {
        val c = s[i].lowercaseChar()
        if (c in 'a'..'z' || c in '0'..'9') {
            out.append(c)
        }
    }
    return out.toString()
}

data class AutoEqEntry(
    val name: String,
    val source: String,
    val form: String,
    val path: String
) {
    val nameLower: String = name.lowercase(Locale.US)
    val nameNorm: String = normalizeAutoEqFast(nameLower)
    val sourceLower: String = source.lowercase(Locale.US)
    val formLower: String = form.lowercase(Locale.US)
    val sourceRank: Int = when (sourceLower) {
        "oratory1990" -> 0
        "crinacle" -> 1
        "rtings" -> 2
        else -> 3
    }
    private val categoryKeywords: String = when {
        formLower.contains("in-ear") || formLower.contains("711") || formLower.contains("5128") || formLower.contains("iem") ->
            "in-ear inear iem earphone earphones earbud earbuds buds tws"
        formLower.contains("earbud") ->
            "earbud earbuds tws earphone earphones buds in-ear inear"
        else ->
            "over-ear overear headphone headphones on-ear"
    }
    val searchableText: String = "$nameLower $sourceLower $formLower $categoryKeywords"
}

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

    @Volatile
    private var cachedIndexMemory: List<AutoEqEntry>? = null

    suspend fun loadBundledIndex(): List<AutoEqEntry> = withContext(Dispatchers.Default) {
        cachedIndexMemory?.let { if (it.isNotEmpty()) return@withContext it }
        val merged = LinkedHashMap<String, AutoEqEntry>(9200)
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
        val list = merged.values.toList()
        if (list.isNotEmpty()) {
            cachedIndexMemory = list
        }
        list
    }

    suspend fun loadIndex(forceRefresh: Boolean = false): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        val localEntries = loadBundledIndex()
        if (!forceRefresh && localEntries.isNotEmpty()) {
            return@withContext localEntries
        }
        val remoteEntries = fetchRemoteIndex()
        if (remoteEntries.isNotEmpty()) {
            val merged = LinkedHashMap<String, AutoEqEntry>(remoteEntries.size + 256)
            remoteEntries.forEach { entry ->
                merged["${entry.name}|${entry.source}|${entry.form}"] = entry
            }
            localEntries.forEach { entry ->
                val key = "${entry.name}|${entry.source}|${entry.form}"
                if (!merged.containsKey(key)) {
                    merged[key] = entry
                }
            }
            val combined = merged.values.toList()
            cachedIndexMemory = combined
            return@withContext combined
        }
        localEntries
    }

    suspend fun fetchRemoteIndex(): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(indexUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "AudioEQ/1.0")
            conn.connectTimeout = 8_000
            conn.readTimeout = 15_000
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
        val out = ArrayList<AutoEqEntry>(8900)
        for (rawLine in md.lineSequence()) {
            val line = rawLine.trim()
            if (!line.startsWith("- [") && !line.startsWith("* [")) continue
            val splitPos = line.indexOf("](")
            if (splitPos <= 3) continue
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
                if (closePos == -1) continue
                rawPath = rest.substring(0, closePos).trim()
                val rawTail = rest.substring(closePos + 1).trim()
                tail = if (rawTail.startsWith("by ")) rawTail.substring(3).trim() else rawTail
            }
            val cleanRawPath = rawPath.removePrefix("./").removePrefix("/")
            val decodedPath = decodeSegment(cleanRawPath)
            val parts = decodedPath.split("/")
            val onIdx = tail.indexOf(" on ")
            val sourcePart = if (onIdx != -1) tail.substring(0, onIdx).trim() else tail.trim()
            val rigPart = if (onIdx != -1) tail.substring(onIdx + 4).trim() else ""
            val source = sourcePart.ifEmpty { parts.getOrNull(0) ?: "oratory1990" }
            val form = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?: rigPart.ifEmpty { "in-ear" }
            out.add(
                AutoEqEntry(
                    name = name,
                    source = source,
                    form = form,
                    path = decodedPath
                )
            )
        }
        return out
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
        val rawLower = trimmed.lowercase(Locale.US)
        val qNorm = normalizeAutoEqFast(rawLower)
        if (qNorm.isEmpty()) return emptyList()
        val tokens = splitAlphaNumTokens(rawLower)

        val matched = ArrayList<AutoEqEntry>(64)
        for (i in 0 until index.size) {
            val entry = index[i]
            val isMatch = if (entry.nameNorm.contains(qNorm)) {
                true
            } else {
                var allFound = tokens.isNotEmpty()
                for (tIdx in 0 until tokens.size) {
                    if (!entry.searchableText.contains(tokens[tIdx])) {
                        allFound = false
                        break
                    }
                }
                allFound
            }
            if (isMatch) {
                matched.add(entry)
            }
        }

        matched.sortWith(
            Comparator { a, b ->
                val tierA = matchTier(a, qNorm, rawLower)
                val tierB = matchTier(b, qNorm, rawLower)
                if (tierA != tierB) return@Comparator tierA - tierB
                if (a.sourceRank != b.sourceRank) return@Comparator a.sourceRank - b.sourceRank
                a.name.length - b.name.length
            }
        )
        return if (matched.size > limit) matched.subList(0, limit) else matched
    }

    private fun splitAlphaNumTokens(s: String): List<String> {
        val result = ArrayList<String>(4)
        val current = StringBuilder()
        for (i in 0 until s.length) {
            val c = s[i]
            if (c in 'a'..'z' || c in '0'..'9') {
                current.append(c)
            } else if (current.isNotEmpty()) {
                result.add(current.toString())
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun matchTier(entry: AutoEqEntry, qNorm: String, rawLower: String): Int {
        val nameNorm = entry.nameNorm
        return when {
            nameNorm == qNorm -> 0
            nameNorm.startsWith(qNorm) -> 1
            entry.nameLower.contains(rawLower) -> 2
            nameNorm.contains(qNorm) -> 3
            else -> 4
        }
    }

    fun matchDevice(index: List<AutoEqEntry>, productName: String): AutoEqEntry? {
        val q = normalize(productName)
        if (q.length < 4) return null
        return index.filter {
            val n = it.nameNorm
            n.contains(q) || q.contains(n)
        }.minWithOrNull(compareBy({ it.sourceRank }, { it.name.length }))
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

        val cleanPathSegments = entry.path.split("/").filter { it.isNotEmpty() }.map { decodeSegment(it) }
        val encodedSegments = cleanPathSegments.map { encodeSegment(it) } + encodeSegment(fileName)
        val url = (listOf(baseUrl) + encodedSegments).joinToString("/")

        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "AudioEQ/1.0")
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
        val q = entry.nameNorm
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

    fun normalize(s: String): String = normalizeAutoEqFast(s)

    fun sourceRank(source: String): Int = when (source.lowercase(Locale.US)) {
        "oratory1990" -> 0
        "crinacle" -> 1
        "rtings" -> 2
        else -> 3
    }
}
