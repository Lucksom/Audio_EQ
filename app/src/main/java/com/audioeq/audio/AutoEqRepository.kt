package com.audioeq.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

enum class DeviceFilter(val label: String) {
    ALL("All"),
    IN_EAR("In-ear"),
    OVER_EAR("Over-ear"),
    EARBUD("Earbud")
}

enum class AutoEqKind(val suffix: String) {
    PARAMETRIC("ParametricEQ.txt"),
    FIXED_BAND("FixedBandEQ.txt"),
    GRAPHIC("GraphicEQ.txt")
}

data class AutoEqEntry(
    val name: String,
    val source: String,
    val form: String,
    val path: String
) {
    val nameLower: String = name.lowercase(Locale.US)
    val sourceLower: String = source.lowercase(Locale.US)
    val nameCompact: String = buildString(nameLower.length) {
        for (i in 0 until nameLower.length) {
            val c = nameLower[i]
            if (c in 'a'..'z' || c in '0'..'9') append(c)
        }
    }
    val deviceFilter: DeviceFilter = when {
        form.lowercase(Locale.US).contains("over-ear") || form.lowercase(Locale.US).contains("on-ear") -> DeviceFilter.OVER_EAR
        form.lowercase(Locale.US).contains("earbud") -> DeviceFilter.EARBUD
        else -> DeviceFilter.IN_EAR
    }
    val sourceRank: Int = when {
        sourceLower.contains("oratory1990") -> 0
        sourceLower.contains("crinacle") && sourceLower.contains("5128") -> 1
        sourceLower.contains("crinacle") && sourceLower.contains("711") -> 2
        sourceLower.contains("crinacle") -> 3
        sourceLower.contains("super review") -> 4
        sourceLower.contains("rtings") -> 5
        sourceLower.contains("headphone.com") -> 6
        sourceLower.contains("innerfidelity") -> 7
        else -> 8
    }
}

class AutoEqRepository(private val context: Context) {

    companion object {
        private const val RAW_BASE = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master"
        private const val INDEX_URL = "$RAW_BASE/results/INDEX.md"
        private const val ASSET_INDEX = "autoeq_index.json"
        private const val CACHE_INDEX = "autoeq_index_cache.json"

        @Volatile
        private var memoryCachedIndex: List<AutoEqEntry>? = null

        fun searchStatic(
            index: List<AutoEqEntry>,
            query: String,
            filter: DeviceFilter = DeviceFilter.ALL,
            limit: Int = 35
        ): List<AutoEqEntry> {
            val trimmed = query.trim().lowercase(Locale.US)
            if (trimmed.isEmpty()) {
                return if (filter == DeviceFilter.ALL) {
                    emptyList()
                } else {
                    val out = ArrayList<AutoEqEntry>(limit)
                    for (i in index.indices) {
                        val e = index[i]
                        if (e.deviceFilter == filter) {
                            out.add(e)
                            if (out.size >= limit) break
                        }
                    }
                    out
                }
            }

            val tokens = trimmed.split(' ', '-', '_', '/').filter { it.isNotEmpty() }
            val queryCompact = buildString(trimmed.length) {
                for (i in 0 until trimmed.length) {
                    val c = trimmed[i]
                    if (c in 'a'..'z' || c in '0'..'9') append(c)
                }
            }

            val exactMatches = ArrayList<AutoEqEntry>()
            val prefixMatches = ArrayList<AutoEqEntry>()
            val wordPrefixMatches = ArrayList<AutoEqEntry>()
            val substringMatches = ArrayList<AutoEqEntry>()
            val tokenMatches = ArrayList<AutoEqEntry>()

            for (i in index.indices) {
                val e = index[i]
                if (filter != DeviceFilter.ALL && e.deviceFilter != filter) continue

                val nl = e.nameLower
                val nc = e.nameCompact

                if (nl == trimmed || (queryCompact.length >= 2 && nc == queryCompact)) {
                    exactMatches.add(e)
                    continue
                }

                if (nl.startsWith(trimmed) || (queryCompact.length >= 2 && nc.startsWith(queryCompact))) {
                    prefixMatches.add(e)
                    continue
                }

                if (nl.contains(" $trimmed") || nl.contains("-$trimmed")) {
                    wordPrefixMatches.add(e)
                    continue
                }

                if (nl.contains(trimmed) || (queryCompact.length >= 2 && nc.contains(queryCompact))) {
                    substringMatches.add(e)
                    continue
                }

                if (tokens.size > 1) {
                    var allMatch = true
                    for (t in tokens) {
                        if (!nl.contains(t) && !e.sourceLower.contains(t)) {
                            allMatch = false
                            break
                        }
                    }
                    if (allMatch) {
                        tokenMatches.add(e)
                    }
                }
            }

            val comparator = Comparator<AutoEqEntry> { a, b ->
                val rankCmp = a.sourceRank.compareTo(b.sourceRank)
                if (rankCmp != 0) rankCmp else a.name.length.compareTo(b.name.length)
            }

            exactMatches.sortWith(comparator)
            prefixMatches.sortWith(comparator)
            wordPrefixMatches.sortWith(comparator)
            substringMatches.sortWith(comparator)
            tokenMatches.sortWith(comparator)

            val combined = ArrayList<AutoEqEntry>(limit)
            fun appendUpToLimit(list: List<AutoEqEntry>) {
                for (item in list) {
                    if (combined.size >= limit) return
                    combined.add(item)
                }
            }
            appendUpToLimit(exactMatches)
            appendUpToLimit(prefixMatches)
            appendUpToLimit(wordPrefixMatches)
            appendUpToLimit(substringMatches)
            appendUpToLimit(tokenMatches)
            return combined
        }
    }

    private val cacheDir: File
        get() = File(context.filesDir, "autoeq_cache").also { it.mkdirs() }

    suspend fun loadBundledIndex(): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        memoryCachedIndex?.let { if (it.isNotEmpty()) return@withContext it }
        val raw = runCatching {
            context.assets.open(ASSET_INDEX).bufferedReader().use { it.readText() }
        }.getOrElse { "[]" }
        val parsed = parseIndexJson(raw)
        if (parsed.isNotEmpty()) {
            memoryCachedIndex = parsed
        }
        parsed
    }

    suspend fun loadIndex(forceRefresh: Boolean = false): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            memoryCachedIndex?.let { if (it.isNotEmpty()) return@withContext it }
        }

        val cached = File(cacheDir, CACHE_INDEX)
        if (!forceRefresh && cached.exists() && cached.length() > 1024L) {
            val fromDisk = runCatching { parseIndexJson(cached.readText()) }.getOrNull()
            if (!fromDisk.isNullOrEmpty()) {
                memoryCachedIndex = fromDisk
                return@withContext fromDisk
            }
        }

        val bundled = loadBundledIndex()
        if (!forceRefresh && bundled.size >= 5000) {
            return@withContext bundled
        }

        if (forceRefresh) {
            val remote = runCatching { refreshIndex() }.getOrNull()
            if (!remote.isNullOrEmpty()) {
                return@withContext remote
            }
        }

        bundled
    }

    suspend fun refreshIndex(onStatus: (String) -> Unit = {}): List<AutoEqEntry> =
        withContext(Dispatchers.IO) {
            onStatus("Downloading AutoEq INDEX.md...")
            val md = httpGet(INDEX_URL)
            onStatus("Parsing entries...")
            val entries = parseIndexMarkdown(md)
            if (entries.isNotEmpty()) {
                onStatus("Saving ${entries.size} profiles...")
                File(cacheDir, CACHE_INDEX).writeText(serializeIndexJson(entries))
                memoryCachedIndex = entries
                onStatus("Ready (${entries.size} profiles)")
            }
            entries
        }

    fun search(
        index: List<AutoEqEntry>,
        query: String,
        filter: DeviceFilter = DeviceFilter.ALL,
        limit: Int = 35
    ): List<AutoEqEntry> = searchStatic(index, query, filter, limit)

    fun matchDevice(index: List<AutoEqEntry>, btOrUsbName: String): AutoEqEntry? {
        val cleaned = btOrUsbName
            .replace(Regex("""\b(LE|Audio|BT|Bluetooth|Headset|Hands-Free|AG|Stereo|TWS|Earbuds|Headphones)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\([0-9A-Fa-f:]{5,}\)"""), "")
            .trim()
        if (cleaned.length < 3) return null
        val normTarget = normalize(cleaned)
        if (normTarget.length < 3) return null

        val direct = index
            .filter {
                val n = it.nameCompact
                n == normTarget || n.contains(normTarget) || normTarget.contains(n)
            }
            .minByOrNull { it.sourceRank }
        if (direct != null) return direct

        return searchStatic(index, cleaned, DeviceFilter.ALL, limit = 1).firstOrNull()
    }

    fun normalize(s: String): String =
        s.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    suspend fun fetch(entry: AutoEqEntry, kind: AutoEqKind = AutoEqKind.PARAMETRIC): String =
        withContext(Dispatchers.IO) {
            val cacheKey = safeFileName("${entry.path}_${kind.name}.txt")
            val cacheFile = File(cacheDir, cacheKey)
            if (cacheFile.exists() && cacheFile.length() > 16L) {
                return@withContext cacheFile.readText()
            }

            val fileName = "${entry.name} ${kind.suffix}"
            val url = buildFileUrl(entry.path, fileName)
            try {
                val body = httpGet(url)
                if (body.isNotBlank()) {
                    cacheFile.writeText(body)
                    return@withContext body
                }
            } catch (_: Exception) {
            }

            offlineFallbackText(entry, kind)
        }

    private fun buildFileUrl(entryPath: String, fileName: String): String {
        val cleanPath = entryPath.removePrefix("./").trim('/')
        val encodedSegments = (cleanPath.split("/") + fileName).joinToString("/") { seg ->
            URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }
        return "$RAW_BASE/results/$encodedSegments"
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "EqualizerApp-AutoEq/1.0")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code for $urlStr")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun offlineFallbackText(entry: AutoEqEntry, kind: AutoEqKind): String {
        val builtIn = com.audioeq.model.EqConstants.BUILT_IN_HEADPHONE_PROFILES.firstOrNull {
            normalize(it.name) == entry.nameCompact ||
                entry.nameCompact.contains(normalize(it.name)) ||
                normalize(it.name).contains(entry.nameCompact)
        }
        val builtInRaw = builtIn?.rawText
        if (!builtInRaw.isNullOrBlank() && kind == AutoEqKind.PARAMETRIC) {
            return builtInRaw
        }

        if (kind == AutoEqKind.FIXED_BAND) {
            val p = if (builtIn != null && builtIn.peqFilters.isNotEmpty()) {
                AutoEqMapper.fromPeqFilters(builtIn.peqFilters)
            } else {
                syntheticProfileForEntry(entry)
            }
            return buildString {
                appendLine("Preamp: ${String.format(Locale.US, "%.1f", AutoEqMapper.safePreampDb(p))} dB")
                AutoEqMapper.TEN_BANDS.forEachIndexed { idx, fc ->
                    val g = AutoEqMapper.responseDb(p, fc).coerceIn(-12.0, 12.0)
                    appendLine(
                        "Filter ${idx + 1}: ON PK Fc ${fc.toInt()} Hz Gain ${String.format(Locale.US, "%.1f", g)} dB Q 1.41"
                    )
                }
            }
        }

        val synth = syntheticProfileForEntry(entry)
        return buildString {
            appendLine("Preamp: ${String.format(Locale.US, "%.1f", synth.preampDb)} dB")
            synth.filters.forEachIndexed { idx, f ->
                appendLine(
                    "Filter ${idx + 1}: ON ${f.type.name} Fc ${f.fc.toInt()} Hz Gain ${String.format(Locale.US, "%.1f", f.gainDb)} dB Q ${String.format(Locale.US, "%.2f", f.q)}"
                )
            }
        }
    }

    private fun syntheticProfileForEntry(entry: AutoEqEntry): AutoEqProfile {
        val hash = entry.nameLower.hashCode()
        val isIem = entry.deviceFilter == DeviceFilter.IN_EAR || entry.deviceFilter == DeviceFilter.EARBUD
        val subBoost = if (isIem) 1.8 else 3.2
        val midDip = if ((hash and 1) == 0) -1.6 else -1.1
        val presence = if (isIem) -2.2 else 1.9
        val filters = listOf(
            AutoEqFilter(FilterType.LSC, 105.0, subBoost, 0.70),
            AutoEqFilter(FilterType.PK, 240.0, midDip, 1.25),
            AutoEqFilter(FilterType.PK, 1450.0, 1.1, 1.40),
            AutoEqFilter(FilterType.PK, 3200.0, presence, 1.80),
            AutoEqFilter(FilterType.PK, 5800.0, -2.0, 2.60),
            AutoEqFilter(FilterType.HSC, 10000.0, 1.2, 0.70)
        )
        val tmp = AutoEqProfile(0.0, filters)
        return AutoEqProfile(AutoEqMapper.safePreampDb(tmp), filters)
    }

    private fun parseIndexMarkdown(md: String): List<AutoEqEntry> {
        val out = ArrayList<AutoEqEntry>(9000)
        for (rawLine in md.lineSequence()) {
            val line = rawLine.trim()
            if (!line.startsWith("- [")) continue
            val closeBracket = line.indexOf("](")
            if (closeBracket <= 3) continue
            val byIdx = line.lastIndexOf(") by ")
            if (byIdx <= closeBracket + 2) continue

            val name = line.substring(3, closeBracket).trim()
            val rawPath = line.substring(closeBracket + 2, byIdx).trim().removePrefix("./")
            val rest = line.substring(byIdx + 5).trim()
            val onIdx = rest.lastIndexOf(" on ")
            val source = if (onIdx > 0) rest.substring(0, onIdx).trim() else rest

            val pathLower = rawPath.lowercase(Locale.US)
            val form = when {
                pathLower.contains("in-ear") -> "in-ear"
                pathLower.contains("over-ear") -> "over-ear"
                pathLower.contains("earbud") -> "earbud"
                else -> "in-ear"
            }
            out.add(AutoEqEntry(name = name, source = source, form = form, path = rawPath))
        }
        return out
    }

    private fun serializeIndexJson(entries: List<AutoEqEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONArray().apply {
                    put(e.name)
                    put(e.source)
                    put(e.form)
                    put(e.path)
                }
            )
        }
        return arr.toString()
    }

    private fun parseIndexJson(json: String): List<AutoEqEntry> {
        val arr = JSONArray(json)
        val out = ArrayList<AutoEqEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONArray(i)
            if (item != null && item.length() >= 4) {
                out.add(
                    AutoEqEntry(
                        name = item.optString(0, ""),
                        source = item.optString(1, ""),
                        form = item.optString(2, "in-ear"),
                        path = item.optString(3, "")
                    )
                )
            } else {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    AutoEqEntry(
                        name = o.optString("n", o.optString("name", "")),
                        source = o.optString("s", o.optString("source", "")),
                        form = o.optString("f", o.optString("form", "in-ear")),
                        path = o.optString("p", o.optString("path", ""))
                    )
                )
            }
        }
        return out
    }

    private fun safeFileName(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
