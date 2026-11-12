package com.audioeq.data

import com.audioeq.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val changelog: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long
)

sealed class GitHubUpdateState {
    data object Idle : GitHubUpdateState()
    data object Checking : GitHubUpdateState()
    data object RepoNotConfigured : GitHubUpdateState()
    data class UpToDate(
        val currentVersion: String,
        val latestRelease: GitHubReleaseInfo
    ) : GitHubUpdateState()
    data class UpdateAvailable(
        val currentVersion: String,
        val latestRelease: GitHubReleaseInfo
    ) : GitHubUpdateState()
    data class Error(val message: String) : GitHubUpdateState()
}

object GitHubUpdateChecker {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val repoSlugRegex = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

    fun normalizeRepoSlug(rawInput: String): String? {
        var cleaned = rawInput.trim()
        if (cleaned.isEmpty()) return null

        cleaned = cleaned
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removePrefix("git@github.com:")
            .trim('/')

        val parts = cleaned.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null

        val owner = parts[0].trim()
        val repo = parts[1].trim().removeSuffix(".git")
        val candidate = "$owner/$repo"
        return candidate.takeIf { repoSlugRegex.matches(it) }
    }

    fun parseVersionParts(version: String): List<Int> {
        val core = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()
        if (core.isEmpty()) return listOf(0)
        val nums = core.split('.').mapNotNull { seg ->
            seg.takeWhile { it.isDigit() }.toIntOrNull()
        }
        return if (nums.isEmpty()) listOf(0) else nums
    }

    fun isRemoteVersionNewer(remoteTag: String, localVersion: String): Boolean {
        val remoteParts = parseVersionParts(remoteTag)
        val localParts = parseVersionParts(localVersion)
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    fun parseReleaseJsonObject(obj: JSONObject): GitHubReleaseInfo? {
        val tagName = obj.optString("tag_name", "").trim()
        if (tagName.isEmpty()) return null
        val releaseName = obj.optString("name", tagName).trim().ifEmpty { tagName }
        val body = obj.optString("body", "").trim()
        val publishedAt = obj.optString("published_at", "").trim()
        val htmlUrl = obj.optString("html_url", "").trim()

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = 0L

        val assets = obj.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                    apkName = name
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
        }

        return GitHubReleaseInfo(
            tagName = tagName,
            releaseName = releaseName,
            changelog = body.ifEmpty { "No release notes provided for this build." },
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkUrl,
            apkFileName = apkName,
            apkSizeBytes = apkSize
        )
    }

    suspend fun checkLatestRelease(
        repoSlug: String,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): GitHubUpdateState = withContext(Dispatchers.IO) {
        val normalized = normalizeRepoSlug(repoSlug) ?: return@withContext GitHubUpdateState.RepoNotConfigured
        try {
            val latestUrl = "https://api.github.com/repos/$normalized/releases/latest"
            val req = Request.Builder()
                .url(latestUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AudioEQ-Android/$currentVersion")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val text = resp.body?.string().orEmpty()
                    val release = parseReleaseJsonObject(JSONObject(text))
                        ?: return@withContext GitHubUpdateState.Error("Received invalid release metadata from GitHub.")
                    return@withContext if (isRemoteVersionNewer(release.tagName, currentVersion)) {
                        GitHubUpdateState.UpdateAvailable(currentVersion, release)
                    } else {
                        GitHubUpdateState.UpToDate(currentVersion, release)
                    }
                }

                if (resp.code == 403 || resp.code == 429) {
                    return@withContext GitHubUpdateState.Error("GitHub API rate limit reached. Please try again later.")
                }

                if (resp.code == 404) {
                    val listUrl = "https://api.github.com/repos/$normalized/releases?per_page=1"
                    val listReq = Request.Builder()
                        .url(listUrl)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "AudioEQ-Android/$currentVersion")
                        .build()
                    client.newCall(listReq).execute().use { listResp ->
                        if (listResp.isSuccessful) {
                            val arr = JSONArray(listResp.body?.string().orEmpty())
                            if (arr.length() == 0) {
                                return@withContext GitHubUpdateState.Error("No GitHub releases published yet for $normalized.")
                            }
                            val release = parseReleaseJsonObject(arr.getJSONObject(0))
                                ?: return@withContext GitHubUpdateState.Error("Could not parse release data for $normalized.")
                            return@withContext if (isRemoteVersionNewer(release.tagName, currentVersion)) {
                                GitHubUpdateState.UpdateAvailable(currentVersion, release)
                            } else {
                                GitHubUpdateState.UpToDate(currentVersion, release)
                            }
                        }
                        if (listResp.code == 404) {
                            return@withContext GitHubUpdateState.Error("Repository \"$normalized\" not found on GitHub (or is private).")
                        }
                        return@withContext GitHubUpdateState.Error("GitHub check failed (HTTP ${listResp.code}).")
                    }
                }

                return@withContext GitHubUpdateState.Error("GitHub check failed (HTTP ${resp.code}).")
            }
        } catch (t: Throwable) {
            return@withContext GitHubUpdateState.Error(
                t.localizedMessage ?: "Network error while checking GitHub releases."
            )
        }
    }
}
