package com.audioeq.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import com.audioeq.MainActivity
import com.audioeq.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class TrackedMusicSession(
    val sessionId: Int,
    val packageName: String,
    val appLabel: String,
    val source: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class DeviceAppEntry(
    val packageName: String,
    val appLabel: String,
    val isAudioOrMedia: Boolean
)

object MusicAppTracker {

    private val KNOWN_MUSIC_PACKAGES = setOf(
        "com.spotify.music",
        "com.spotify.lite",
        "com.google.android.apps.youtube.music",
        "com.apple.android.music",
        "com.aspiro.tidal",
        "com.maxmpz.audioplayer",
        "in.krosbits.musicolet",
        "org.videolan.vlc",
        "com.neutroncode.mp",
        "com.foobar2000.foobar2000",
        "deezer.android.app",
        "com.soundcloud.android",
        "com.amazon.mp3",
        "com.jio.media.jiobeats",
        "com.gaana",
        "com.bsbportal.music",
        "com.afmobi.boomplayer",
        "com.aimp.player",
        "com.kodarkooperativet.blackplayerex",
        "com.piyush.music",
        "code.name.monkey.retromusic",
        "io.github.zyrouge.symphony",
        "org.oxycblt.auxio",
        "com.zionhuang.music",
        "it.vfsfitvnm.vimusic",
        "it.fast4x.rimusic",
        "com.maxrave.simpmusic",
        "tv.plex.labs.plexamp",
        "app.symfonium.music.player",
        "com.bandcamp.android",
        "com.netease.cloudmusic",
        "com.tencent.qqmusic",
        "com.kugou.android",
        "cn.kuwo.player",
        "com.miui.player",
        "com.sec.android.app.music",
        "com.sonyericsson.music",
        "com.oppo.music",
        "com.oneplus.music",
        "com.huawei.music",
        "com.google.android.music",
        "com.google.android.youtube",
        "app.morphe.android.youtube",
        "app.revanced.android.youtube",
        "app.revanced.android.apps.youtube.music",
        "com.vanced.android.youtube",
        "com.vanced.android.apps.youtube.music"
    )

    private val EXCLUDED_NON_MUSIC_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.google.android.tts",
        "com.samsung.SMT",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.google.android.apps.maps",
        "com.whatsapp",
        "org.telegram.messenger",
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.snapchat.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )

    val POPULAR_MUSIC_PACKAGES = listOf(
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.apple.android.music" to "Apple Music",
        "com.aspiro.tidal" to "TIDAL",
        "com.maxmpz.audioplayer" to "Poweramp",
        "in.krosbits.musicolet" to "Musicolet",
        "org.videolan.vlc" to "VLC",
        "deezer.android.app" to "Deezer",
        "com.soundcloud.android" to "SoundCloud",
        "com.amazon.mp3" to "Amazon Music",
        "com.aimp.player" to "AIMP",
        "code.name.monkey.retromusic" to "Retro Music",
        "it.vfsfitvnm.vimusic" to "ViMusic",
        "it.fast4x.rimusic" to "RiMusic",
        "tv.plex.labs.plexamp" to "Plexamp",
        "app.symfonium.music.player" to "Symfonium",
        "com.foobar2000.foobar2000" to "foobar2000",
        "com.neutroncode.mp" to "Neutron Music Player",
        "com.miui.player" to "Mi Music",
        "com.sec.android.app.music" to "Samsung Music",
        "org.oxycblt.auxio" to "Auxio"
    )

    private val POPULAR_MUSIC_PACKAGE_SET: Set<String> by lazy {
        POPULAR_MUSIC_PACKAGES.map { it.first }.toSet()
    }

    private val _selectedMusicPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedMusicPackages: StateFlow<Set<String>> = _selectedMusicPackages.asStateFlow()

    private val _excludedMusicPackages = MutableStateFlow<Set<String>>(emptySet())
    val excludedMusicPackages: StateFlow<Set<String>> = _excludedMusicPackages.asStateFlow()

    fun setSelectedMusicPackages(pkgs: Set<String>) {
        _selectedMusicPackages.value = pkgs
        pruneDisallowedSessions()
        lastAppContext?.let { rebuildSummary(it) }
    }

    fun setExcludedMusicPackages(pkgs: Set<String>) {
        _excludedMusicPackages.value = pkgs
        pruneDisallowedSessions()
        lastAppContext?.let { rebuildSummary(it) }
    }

    private fun isPackageCurrentlyAllowed(pkg: String): Boolean {
        if (pkg in EXCLUDED_NON_MUSIC_PACKAGES) return false
        val selected = _selectedMusicPackages.value
        if (selected.isNotEmpty()) return pkg in selected
        return pkg !in _excludedMusicPackages.value
    }

    private fun pruneDisallowedSessions() {
        broadcastMusicSessions.entries.removeIf { !isPackageCurrentlyAllowed(it.value.packageName) }
        privilegedMusicSessions.entries.removeIf { !isPackageCurrentlyAllowed(it.value.packageName) }
    }

    private val allBroadcastSessions = ConcurrentHashMap<Int, TrackedMusicSession>()
    private val broadcastMusicSessions = ConcurrentHashMap<Int, TrackedMusicSession>()
    private val privilegedMusicSessions = ConcurrentHashMap<Int, TrackedMusicSession>()

    @Volatile
    private var lastAppContext: Context? = null

    @Volatile
    private var privilegedModeActive: Boolean = false

    @Volatile
    private var legacyUniversalActive: Boolean = true

    @Volatile
    private var appReceiverRegistered: Boolean = false

    private const val PREFS_BROADCAST_SESSIONS = "audio_eq_broadcast_sessions"
    private const val KEY_BOOT_EPOCH_SEC = "boot_epoch_sec"
    private const val KEY_SESSION_ENTRIES = "session_entries"

    private val appSessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            handleSessionBroadcastIntent(ctx, intent)
        }
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext
        lastAppContext = appCtx
        restorePersistedBroadcastSessions(appCtx)
        if (!appReceiverRegistered) {
            synchronized(this) {
                if (!appReceiverRegistered) {
                    runCatching {
                        val filter = IntentFilter().apply {
                            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                        }
                        ContextCompat.registerReceiver(
                            appCtx,
                            appSessionReceiver,
                            filter,
                            ContextCompat.RECEIVER_EXPORTED
                        )
                        appReceiverRegistered = true
                    }
                }
            }
        }
    }

    fun handleSessionBroadcastIntent(context: Context, intent: Intent?) {
        if (intent == null) return
        val sid = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sid <= 0) return
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
        val contentType = intent.getIntExtra(
            AudioEffect.EXTRA_CONTENT_TYPE,
            AudioEffect.CONTENT_TYPE_MUSIC
        )
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                onBroadcastSessionOpened(
                    context = context.applicationContext,
                    sessionId = sid,
                    packageName = pkg,
                    contentType = contentType
                )
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                onBroadcastSessionClosed(context.applicationContext, sid)
            }
        }
    }

    private fun currentBootEpochSec(): Long =
        (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000L

    private fun persistBroadcastSessions(context: Context) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_BROADCAST_SESSIONS, Context.MODE_PRIVATE)
            val entries = allBroadcastSessions.values
                .take(16)
                .map { "${it.sessionId}|${it.packageName}|${it.source}" }
                .toSet()
            prefs.edit()
                .putLong(KEY_BOOT_EPOCH_SEC, currentBootEpochSec())
                .putStringSet(KEY_SESSION_ENTRIES, entries)
                .apply()
        }
    }

    private fun restorePersistedBroadcastSessions(context: Context) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_BROADCAST_SESSIONS, Context.MODE_PRIVATE)
            val savedBoot = prefs.getLong(KEY_BOOT_EPOCH_SEC, -1L)
            val curBoot = currentBootEpochSec()
            if (savedBoot < 0L || kotlin.math.abs(curBoot - savedBoot) > 30L) {
                prefs.edit().clear().apply()
                return
            }
            val rawSet = prefs.getStringSet(KEY_SESSION_ENTRIES, emptySet()).orEmpty()
            for (entry in rawSet) {
                val parts = entry.split('|')
                val sid = parts.getOrNull(0)?.toIntOrNull() ?: continue
                if (sid <= 0) continue
                val pkg = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "audio.session"
                if (pkg == context.packageName || pkg.startsWith("com.audioeq")) continue
                val src = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "AudioEffect"
                val tracked = TrackedMusicSession(
                    sessionId = sid,
                    packageName = pkg,
                    appLabel = resolveAppLabel(context, pkg),
                    source = src
                )
                allBroadcastSessions.putIfAbsent(sid, tracked)
                if (isMusicAppPackage(context, pkg, AudioEffect.CONTENT_TYPE_MUSIC)) {
                    broadcastMusicSessions.putIfAbsent(sid, tracked)
                }
            }
        }
    }

    fun setEngineTrackingContext(context: Context, isLegacyMode: Boolean, hasPrivilegedControl: Boolean) {
        lastAppContext = context.applicationContext
        val changed = (legacyUniversalActive != isLegacyMode) || (privilegedModeActive != hasPrivilegedControl)
        legacyUniversalActive = isLegacyMode
        privilegedModeActive = hasPrivilegedControl
        if (changed) {
            rebuildSummary(context)
        }
    }

    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

    private val _activeMediaPackages = MutableStateFlow<Set<String>>(emptySet())
    val activeMediaPackages: StateFlow<Set<String>> = _activeMediaPackages.asStateFlow()

    private val _activeMediaLabels = MutableStateFlow<List<String>>(emptyList())
    val activeMediaLabels: StateFlow<List<String>> = _activeMediaLabels.asStateFlow()

    private val _activeMusicPlaying = MutableStateFlow(false)
    val activeMusicPlaying: StateFlow<Boolean> = _activeMusicPlaying.asStateFlow()

    private val _trackedSessionsSummary = MutableStateFlow("Universal (All Audio)")
    val trackedSessionsSummary: StateFlow<String> = _trackedSessionsSummary.asStateFlow()

    @Volatile
    private var activeMediaPlaybackCount: Int = 0

    @Volatile
    private var playbackWentIdleSinceBroadcast: Boolean = false

    @Volatile
    private var onMusicStateChanged: (() -> Unit)? = null

    fun setOnMusicStateChangedListener(listener: (() -> Unit)?) {
        onMusicStateChanged = listener
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        return try {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponent = ComponentName(context, EqMediaNotificationListenerService::class.java)
            val flat = myComponent.flattenToString()
            val shortFlat = myComponent.flattenToShortString()
            val granted = enabledListeners.contains(flat) || enabledListeners.contains(shortFlat) ||
                enabledListeners.contains(context.packageName)
            _notificationAccessGranted.value = granted
            granted
        } catch (_: Throwable) {
            false
        }
    }

    fun openNotificationAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Throwable) {
            }
        }
    }

    fun isMusicAppPackage(
        context: Context,
        packageName: String?,
        contentType: Int = AudioEffect.CONTENT_TYPE_MUSIC
    ): Boolean {
        val pkg = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (pkg == context.packageName || pkg.startsWith("com.audioeq")) return false
        if (pkg in EXCLUDED_NON_MUSIC_PACKAGES) return false

        val selected = _selectedMusicPackages.value
        if (selected.isNotEmpty()) {
            return pkg in selected
        }
        if (pkg in _excludedMusicPackages.value) return false

        val lower = pkg.lowercase(Locale.US)
        if (lower.contains("telecom") || lower.contains("dialer") || lower.contains("inputmethod") ||
            lower.contains("keyboard") || lower.contains("ringtone") || lower.contains("alarm") ||
            lower.contains("deskclock") || lower.contains(".tts")
        ) {
            return false
        }
        if (pkg in _activeMediaPackages.value) return true
        if (pkg in KNOWN_MUSIC_PACKAGES) return true
        if (lower.contains("music") || lower.contains("audioplayer") || lower.contains("mediaplayer") ||
            lower.contains("spotify") || lower.contains("podcast") || lower.contains("mp3") ||
            lower.contains("equalizer") || lower.contains("radio") || lower.contains("flac") ||
            lower.contains("player") || lower.contains("youtube")
        ) {
            return true
        }
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_AUDIO ||
                    appInfo.category == ApplicationInfo.CATEGORY_VIDEO
                ) {
                    return true
                }
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                    appInfo.category == ApplicationInfo.CATEGORY_MAPS ||
                    appInfo.category == ApplicationInfo.CATEGORY_SOCIAL
                ) {
                    return false
                }
            }
        } catch (_: Throwable) {
        }
        return contentType == AudioEffect.CONTENT_TYPE_MUSIC
    }

    fun loadDeviceAppList(
        context: Context,
        selectedPackages: Set<String> = _selectedMusicPackages.value
    ): List<DeviceAppEntry> {
        val pm = context.packageManager
        val discovered = LinkedHashMap<String, DeviceAppEntry>()

        fun addApp(pkg: String?, mediaHint: Boolean) {
            val cleaned = pkg?.trim()?.takeIf { it.isNotEmpty() } ?: return
            if (cleaned == context.packageName || cleaned.startsWith("com.audioeq")) return
            val existing = discovered[cleaned]
            if (existing != null) {
                if (mediaHint && !existing.isAudioOrMedia) {
                    discovered[cleaned] = existing.copy(isAudioOrMedia = true)
                }
                return
            }
            val label = resolveAppLabel(context, cleaned)
            val isMediaCat = mediaHint || cleaned in KNOWN_MUSIC_PACKAGES || run {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching {
                        val cat = pm.getApplicationInfo(cleaned, 0).category
                        cat == ApplicationInfo.CATEGORY_AUDIO || cat == ApplicationInfo.CATEGORY_VIDEO
                    }.getOrDefault(false)
                } else {
                    false
                }
            }
            discovered[cleaned] = DeviceAppEntry(
                packageName = cleaned,
                appLabel = label,
                isAudioOrMedia = isMediaCat
            )
        }

        val queries = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC) to true,
            Intent("android.media.browse.MediaBrowserService") to true,
            Intent(Intent.ACTION_VIEW).setType("audio/*") to true,
            Intent(Intent.ACTION_VIEW).setType("video/*") to true,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER) to false
        )
        for ((intent, mediaHint) in queries) {
            runCatching {
                val resolved = pm.queryIntentActivities(intent, 0)
                for (ri in resolved) {
                    addApp(ri.activityInfo?.packageName, mediaHint)
                }
                if (intent.action == "android.media.browse.MediaBrowserService") {
                    val services = pm.queryIntentServices(intent, 0)
                    for (si in services) {
                        addApp(si.serviceInfo?.packageName, true)
                    }
                }
            }
        }
        selectedPackages.forEach { addApp(it, true) }

        return discovered.values.sortedWith(
            compareByDescending<DeviceAppEntry> { it.packageName in selectedPackages }
                .thenByDescending { it.isAudioOrMedia }
                .thenBy { it.appLabel.lowercase(Locale.US) }
        )
    }

    fun resolveAppLabel(context: Context, packageName: String): String {
        if (packageName.isBlank()) return "Music Player"
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString().takeIf { it.isNotBlank() } ?: packageName
        } catch (_: Throwable) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun removeOlderBroadcastSessionsForPackage(packageName: String, keepSessionId: Int) {
        if (packageName.isBlank() || packageName == "audio.session" || packageName == "external.player") {
            return
        }
        allBroadcastSessions.entries.removeIf { (sid, session) ->
            sid != keepSessionId && session.packageName == packageName
        }
        broadcastMusicSessions.entries.removeIf { (sid, session) ->
            sid != keepSessionId && session.packageName == packageName
        }
    }

    fun onBroadcastSessionOpened(
        context: Context,
        sessionId: Int,
        packageName: String?,
        contentType: Int
    ): Boolean {
        if (sessionId <= 0) return false
        val cleanedPkg = packageName?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanedPkg == context.packageName || cleanedPkg?.startsWith("com.audioeq") == true) {
            return false
        }
        playbackWentIdleSinceBroadcast = false
        val pkg = cleanedPkg ?: "audio.session"
        val label = resolveAppLabel(context, pkg)
        val tracked = TrackedMusicSession(
            sessionId = sessionId,
            packageName = pkg,
            appLabel = label,
            source = "AudioEffect",
            updatedAtMs = System.currentTimeMillis()
        )
        removeOlderBroadcastSessionsForPackage(pkg, sessionId)
        val prev = allBroadcastSessions.put(sessionId, tracked)
        persistBroadcastSessions(context)

        if (!isMusicAppPackage(context, cleanedPkg, contentType)) {
            broadcastMusicSessions.remove(sessionId)
            rebuildSummary(context)
            if (prev?.sessionId != tracked.sessionId || prev.packageName != tracked.packageName) {
                onMusicStateChanged?.invoke()
            }
            return false
        }
        broadcastMusicSessions[sessionId] = tracked
        rebuildSummary(context)
        if (prev?.sessionId != tracked.sessionId || prev.packageName != tracked.packageName) {
            onMusicStateChanged?.invoke()
        }
        return true
    }

    fun onExternalSessionOpened(context: Context, sessionId: Int, packageName: String? = null) {
        if (sessionId <= 0) return
        val pkg = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: "external.player"
        if (pkg == context.packageName || pkg.startsWith("com.audioeq")) return
        playbackWentIdleSinceBroadcast = false
        val tracked = TrackedMusicSession(
            sessionId = sessionId,
            packageName = pkg,
            appLabel = resolveAppLabel(context, pkg),
            source = "ControlPanel",
            updatedAtMs = System.currentTimeMillis()
        )
        removeOlderBroadcastSessionsForPackage(pkg, sessionId)
        val prev = allBroadcastSessions.put(sessionId, tracked)
        if (isMusicAppPackage(context, pkg, AudioEffect.CONTENT_TYPE_MUSIC)) {
            broadcastMusicSessions[sessionId] = tracked
        }
        persistBroadcastSessions(context)
        rebuildSummary(context)
        if (prev?.sessionId != tracked.sessionId || prev.packageName != tracked.packageName) {
            onMusicStateChanged?.invoke()
        }
    }

    fun onBroadcastSessionClosed(context: Context, sessionId: Int) {
        val removedA = allBroadcastSessions.remove(sessionId)
        val removedB = broadcastMusicSessions.remove(sessionId)
        val removedP = privilegedMusicSessions.remove(sessionId)
        persistBroadcastSessions(context)
        rebuildSummary(context)
        if (removedA != null || removedB != null || removedP != null) {
            onMusicStateChanged?.invoke()
        }
    }

    fun updatePrivilegedMusicSessions(context: Context, sessions: List<TrackedMusicSession>) {
        lastAppContext = context.applicationContext
        val allowedSessions = sessions.filter { it.sessionId > 0 && isPackageCurrentlyAllowed(it.packageName) }
        val liveSids = allowedSessions.map { it.sessionId }.toSet()
        val livePkgs = allowedSessions.map { it.packageName }.toSet()

        var prunedBroadcast = false
        if (liveSids.isNotEmpty()) {
            val removedB = broadcastMusicSessions.entries.removeIf { (sid, s) ->
                (privilegedModeActive && sid !in liveSids) ||
                    (s.packageName in livePkgs && sid !in liveSids)
            }
            val removedA = allBroadcastSessions.entries.removeIf { (sid, s) ->
                (privilegedModeActive && sid !in liveSids) ||
                    (s.packageName in livePkgs && sid !in liveSids)
            }
            prunedBroadcast = removedB || removedA
        } else if (privilegedModeActive && !_activeMusicPlaying.value && activeMediaPlaybackCount == 0) {
            if (broadcastMusicSessions.isNotEmpty() || allBroadcastSessions.isNotEmpty()) {
                broadcastMusicSessions.clear()
                allBroadcastSessions.clear()
                prunedBroadcast = true
            }
        }
        if (prunedBroadcast) {
            persistBroadcastSessions(context)
        }

        val sameKeys = privilegedMusicSessions.size == allowedSessions.size &&
            allowedSessions.all { existing ->
                val cur = privilegedMusicSessions[existing.sessionId]
                cur != null && cur.packageName == existing.packageName && cur.appLabel == existing.appLabel
            }
        if (sameKeys && !prunedBroadcast) return
        privilegedMusicSessions.clear()
        for (s in allowedSessions) {
            privilegedMusicSessions[s.sessionId] = s
        }
        rebuildSummary(context)
    }

    fun getAllBroadcastSessionIds(): Set<Int> = LinkedHashSet(allBroadcastSessions.keys)

    fun getBroadcastSessionIds(): Set<Int> = LinkedHashSet(broadcastMusicSessions.keys)

    fun getLegacyTargetSessionIds(musicOnly: Boolean = false): Set<Int> {
        val result = LinkedHashSet<Int>()
        for ((sid, session) in broadcastMusicSessions) {
            if (!musicOnly || isPackageCurrentlyAllowed(session.packageName)) {
                result.add(sid)
            }
        }
        for ((sid, session) in allBroadcastSessions) {
            if (!musicOnly || isPackageCurrentlyAllowed(session.packageName)) {
                result.add(sid)
            }
        }
        for ((sid, session) in privilegedMusicSessions) {
            if (!musicOnly || isPackageCurrentlyAllowed(session.packageName)) {
                result.add(sid)
            }
        }
        return result
    }

    fun hasActiveNonSessionMediaPlayback(): Boolean {
        val sessions = if (legacyUniversalActive) allBroadcastSessions else broadcastMusicSessions
        val activePkgs = _activeMediaPackages.value
        val broadcastPkgs = sessions.values.map { it.packageName }.toSet()
        if (activePkgs.isNotEmpty()) {
            return activePkgs.any { it !in broadcastPkgs } ||
                activeMediaPlaybackCount > sessions.size
        }
        return activeMediaPlaybackCount > sessions.size ||
            (activeMediaPlaybackCount > 0 && playbackWentIdleSinceBroadcast)
    }

    fun updateNotificationMediaPackages(
        context: Context,
        packages: Set<String>,
        playing: Boolean
    ) {
        val filtered = packages.filter { pkg ->
            pkg != context.packageName && pkg !in EXCLUDED_NON_MUSIC_PACKAGES
        }.toSet()
        val prev = _activeMediaPackages.value
        val prevPlaying = _activeMusicPlaying.value
        _activeMediaPackages.value = filtered
        _activeMediaLabels.value = filtered.map { resolveAppLabel(context, it) }.distinct()
        _activeMusicPlaying.value = playing || filtered.isNotEmpty()
        rebuildSummary(context)
        if (prev != filtered || prevPlaying != _activeMusicPlaying.value) {
            onMusicStateChanged?.invoke()
        }
    }

    fun updatePlaybackConfigurations(
        context: Context,
        configs: List<AudioPlaybackConfiguration>
    ) {
        var mediaCount = 0
        for (cfg in configs) {
            val attr = cfg.audioAttributes ?: continue
            val isMediaOrGameUsage = attr.usage == AudioAttributes.USAGE_MEDIA ||
                attr.usage == AudioAttributes.USAGE_GAME ||
                attr.usage == AudioAttributes.USAGE_UNKNOWN
            val isExcludedContent = attr.contentType == AudioAttributes.CONTENT_TYPE_SONIFICATION ||
                attr.contentType == AudioAttributes.CONTENT_TYPE_SPEECH
            if (isMediaOrGameUsage && !isExcludedContent) {
                mediaCount++
            }
        }
        val prevCount = activeMediaPlaybackCount
        activeMediaPlaybackCount = mediaCount
        if (mediaCount == 0 && (allBroadcastSessions.isNotEmpty() || broadcastMusicSessions.isNotEmpty())) {
            playbackWentIdleSinceBroadcast = true
        }
        val anyMediaPlaying = mediaCount > 0
        val combinedPlaying = anyMediaPlaying || _activeMediaPackages.value.isNotEmpty()
        if (_activeMusicPlaying.value != combinedPlaying || prevCount != mediaCount) {
            _activeMusicPlaying.value = combinedPlaying
            rebuildSummary(context)
            onMusicStateChanged?.invoke()
        }
    }

    fun getVerifiedMusicSessionIds(): Set<Int> {
        val result = LinkedHashSet<Int>()
        for ((sid, session) in privilegedMusicSessions) {
            if (isPackageCurrentlyAllowed(session.packageName)) {
                result.add(sid)
            }
        }
        if (!privilegedModeActive || result.isEmpty()) {
            for ((sid, session) in broadcastMusicSessions) {
                if (isPackageCurrentlyAllowed(session.packageName)) {
                    result.add(sid)
                }
            }
        }
        return result
    }

    fun getAllTrackedSessions(): List<TrackedMusicSession> {
        val merged = LinkedHashMap<Int, TrackedMusicSession>()
        for ((sid, s) in privilegedMusicSessions) {
            if (isPackageCurrentlyAllowed(s.packageName)) {
                merged[sid] = s
            }
        }
        if (!privilegedModeActive || merged.isEmpty()) {
            for ((sid, s) in broadcastMusicSessions) {
                if (isPackageCurrentlyAllowed(s.packageName)) {
                    merged.putIfAbsent(sid, s)
                }
            }
        }
        return merged.values.sortedBy { it.sessionId }
    }

    fun parseMusicSessionsFromDumpsys(
        context: Context,
        audioDumpsys: String?,
        flingerDumpsys: String?
    ): List<TrackedMusicSession> {
        val results = LinkedHashMap<Int, TrackedMusicSession>()
        val pm = context.packageManager
        val pidToUid = HashMap<Int, Int>()

        if (!audioDumpsys.isNullOrBlank()) {
            val lines = audioDumpsys.lineSequence()
            var lastSeenUid: Int? = null
            var lastSeenPkg: String? = null
            for (line in lines) {
                val lowerLine = line.lowercase(Locale.US)
                if (lowerLine.contains("audio event log") ||
                    lowerLine.contains("playback activity as reported") ||
                    lowerLine.trimStart().startsWith("event log:")
                ) {
                    break
                }
                val uPidPair = Regex("""u/pid\s*[:=]\s*(\d+)\s*/\s*(\d+)""", RegexOption.IGNORE_CASE).find(line)
                if (uPidPair != null) {
                    val u = uPidPair.groupValues[1].toIntOrNull()
                    val p = uPidPair.groupValues[2].toIntOrNull()
                    if (u != null && p != null && u >= 10000) {
                        pidToUid[p] = u
                        lastSeenUid = u
                    }
                }
                val pkgMatchOnLine = Regex("""(?:pkg|package|client)\s*[:=]\s*([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)""").find(line)
                if (pkgMatchOnLine != null) {
                    lastSeenPkg = pkgMatchOnLine.groupValues[1]
                }

                if (!line.contains("sessionId", ignoreCase = true) && !line.contains("session:", ignoreCase = true) && !line.contains("session=", ignoreCase = true)) {
                    continue
                }
                if (lowerLine.contains("state:") && !lowerLine.contains("state:started")) {
                    continue
                }
                if (lowerLine.contains("state:released") ||
                    lowerLine.contains("state:stopped") ||
                    lowerLine.contains("state:idle") ||
                    lowerLine.contains("state:paused") ||
                    lowerLine.contains("state:created") ||
                    lowerLine.contains("state:unknown")
                ) {
                    continue
                }
                if (lowerLine.contains("usage=") &&
                    !lowerLine.contains("usage=usage_media") &&
                    !lowerLine.contains("usage=usage_game") &&
                    !lowerLine.contains("usage=usage_unknown") &&
                    !Regex("""usage=(?:1|14|0)\b""").containsMatchIn(lowerLine)
                ) {
                    continue
                }
                if (lowerLine.contains("content=content_type_sonification") ||
                    lowerLine.contains("content=content_type_speech")
                ) {
                    continue
                }

                val sidMatch = Regex("""(?:sessionId|session)\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE).find(line)
                val sid = sidMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (sid <= 0) continue

                val uidMatch = Regex("""(?:u/pid|uid)\s*[:=]\s*(\d+)""").find(line)
                val uid = uidMatch?.groupValues?.get(1)?.toIntOrNull() ?: lastSeenUid

                val candidatePkgs = mutableListOf<String>()
                pkgMatchOnLine?.groupValues?.get(1)?.let { candidatePkgs.add(it) }
                lastSeenPkg?.let { if (it !in candidatePkgs) candidatePkgs.add(it) }
                if (uid != null && uid >= 10000) {
                    runCatching {
                        pm.getPackagesForUid(uid)?.let { candidatePkgs.addAll(it) }
                    }
                }

                val matchedPkg = candidatePkgs.firstOrNull { isMusicAppPackage(context, it, AudioEffect.CONTENT_TYPE_MUSIC) }
                    ?: candidatePkgs.firstOrNull { it != context.packageName && !it.startsWith("com.audioeq") && isPackageCurrentlyAllowed(it) }
                if (matchedPkg != null) {
                    results[sid] = TrackedMusicSession(
                        sessionId = sid,
                        packageName = matchedPkg,
                        appLabel = resolveAppLabel(context, matchedPkg),
                        source = "ADB/Root Audio"
                    )
                }
            }
        }

        if (!flingerDumpsys.isNullOrBlank()) {
            var inSkippedBlock = false
            var headerClientCol = 2
            var headerSessionCol = -1
            val lines = flingerDumpsys.lineSequence()
            for (line in lines) {
                val lowerLine = line.lowercase(Locale.US)
                val trimmedLower = lowerLine.trimStart()
                if (lowerLine.contains("effect chain") ||
                    trimmedLower.startsWith("effect id") ||
                    lowerLine.contains("notification client") ||
                    lowerLine.contains("event log")
                ) {
                    inSkippedBlock = true
                    continue
                }
                if (lowerLine.contains("tracks of which") || lowerLine.contains("output thread")) {
                    inSkippedBlock = false
                    headerClientCol = 2
                    headerSessionCol = -1
                    continue
                }
                if (inSkippedBlock) continue
                if (lowerLine.contains("active") && lowerLine.contains("session") &&
                    (lowerLine.contains("client") || lowerLine.contains("name") || lowerLine.contains("id"))
                ) {
                    val normHeader = lowerLine
                        .replace("chn mask", "chnmask")
                        .replace("port id", "portid")
                        .replace("l db", "ldb")
                        .replace("r db", "rdb")
                        .trim()
                        .split(Regex("""\s+"""))
                    val cIdx = normHeader.indexOfFirst { it == "client" }
                    val sIdx = normHeader.indexOfFirst { it == "session" || it == "sessionid" }
                    if (cIdx >= 0) headerClientCol = cIdx
                    if (sIdx >= 0) headerSessionCol = sIdx
                    continue
                }
                if (lowerLine.contains("active=false") ||
                    lowerLine.contains("active: no") ||
                    lowerLine.contains("standby: yes") ||
                    lowerLine.contains("state=stopped") ||
                    lowerLine.contains("state=paused") ||
                    lowerLine.contains("state=idle") ||
                    lowerLine.contains("state=terminated") ||
                    lowerLine.contains("state=flushed") ||
                    Regex("""^\s*\d+\s+no\s+""", RegexOption.IGNORE_CASE).containsMatchIn(line)
                ) {
                    continue
                }
                val sidMatch = Regex("""(?:session(?:Id)?|Session)\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE).find(line)
                var tabularClient: Int? = null
                var tabularSid: Int? = null
                if (sidMatch == null && Regex("""^\s*\d+\s+yes\s+""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                    val cols = line.trim().split(Regex("""\s+"""))
                    if (cols.size >= 4) {
                        tabularClient = cols.getOrNull(headerClientCol)?.toIntOrNull()
                        tabularSid = when {
                            headerSessionCol >= 0 -> cols.getOrNull(headerSessionCol)?.toIntOrNull()
                            cols.size >= 7 && cols[4].length >= 6 && cols[5].length >= 6 -> cols[6].toIntOrNull()
                            else -> cols.getOrNull(3)?.toIntOrNull()
                        }
                    }
                }

                val sid = sidMatch?.groupValues?.get(1)?.toIntOrNull() ?: tabularSid
                if (sid != null && sid > 0 && !results.containsKey(sid)) {
                    val uidMatch = Regex("""\buid\s*[:=]?\s*(\d{4,})""", RegexOption.IGNORE_CASE).find(line)
                    val clientMatch = Regex("""\bclient\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(line)
                    val directUid = uidMatch?.groupValues?.get(1)?.toIntOrNull()
                    val clientId = clientMatch?.groupValues?.get(1)?.toIntOrNull() ?: tabularClient
                    val resolvedUid = when {
                        directUid != null && directUid >= 10000 -> directUid
                        clientId != null && pidToUid.containsKey(clientId) -> pidToUid[clientId]
                        clientId != null && clientId >= 10000 -> clientId
                        else -> null
                    }
                    if (resolvedUid != null && resolvedUid >= 10000) {
                        val pkgs = runCatching { pm.getPackagesForUid(resolvedUid)?.toList() ?: emptyList() }.getOrDefault(emptyList())
                        val matchedPkg = pkgs.firstOrNull { isMusicAppPackage(context, it, AudioEffect.CONTENT_TYPE_MUSIC) }
                            ?: pkgs.firstOrNull { it != context.packageName && !it.startsWith("com.audioeq") && isPackageCurrentlyAllowed(it) }
                        if (matchedPkg != null) {
                            results[sid] = TrackedMusicSession(
                                sessionId = sid,
                                packageName = matchedPkg,
                                appLabel = resolveAppLabel(context, matchedPkg),
                                source = "AudioFlinger"
                            )
                        }
                    } else if (_activeMediaPackages.value.isNotEmpty()) {
                        val activePkg = _activeMediaPackages.value.first()
                        results[sid] = TrackedMusicSession(
                            sessionId = sid,
                            packageName = activePkg,
                            appLabel = resolveAppLabel(context, activePkg),
                            source = "AudioFlinger"
                        )
                    }
                }
            }
        }

        return results.values.toList()
    }

    private fun selectCurrentSession(tracked: List<TrackedMusicSession>): TrackedMusicSession? {
        if (tracked.isEmpty()) return null
        val activeMediaPkgs = _activeMediaPackages.value
        val prioritized = if (activeMediaPkgs.isNotEmpty()) {
            tracked.filter { it.packageName in activeMediaPkgs }.ifEmpty { tracked }
        } else {
            tracked
        }
        return prioritized.maxWithOrNull(
            compareBy<TrackedMusicSession> { it.updatedAtMs }.thenBy { it.sessionId }
        )
    }

    private fun rebuildSummary(context: Context) {
        lastAppContext = context.applicationContext
        isNotificationListenerEnabled(context)
        if (legacyUniversalActive) {
            _trackedSessionsSummary.value = "Universal (All Audio)"
            return
        }
        val current = selectCurrentSession(getAllTrackedSessions())
        _trackedSessionsSummary.value = if (current != null) {
            "${current.appLabel} #${current.sessionId}"
        } else {
            "No active music session"
        }
    }
}

class EqMediaNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshActiveMediaNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshActiveMediaNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.packageName == packageName && sbn.id == EqualizerNotificationHelper.NOTIFICATION_ID) {
            EqualizerNotificationHelper.restoreIfActive(applicationContext)
        }
        refreshActiveMediaNotifications()
    }

    private fun refreshActiveMediaNotifications() {
        try {
            val mediaPkgs = LinkedHashSet<String>()
            var anyPlaying = false

            val activeNotifs = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
            for (sbn in activeNotifs) {
                val pkg = sbn.packageName ?: continue
                if (pkg == packageName) continue
                val notif = sbn.notification ?: continue
                val extras = notif.extras
                val template = extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
                val hasMediaToken = extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true ||
                    extras?.containsKey("android.mediaSession") == true
                val isTransport = notif.category == Notification.CATEGORY_TRANSPORT
                val isMediaNotif = hasMediaToken ||
                    template.contains("MediaStyle", ignoreCase = true) ||
                    isTransport

                if (isMediaNotif && MusicAppTracker.isMusicAppPackage(applicationContext, pkg, AudioEffect.CONTENT_TYPE_MUSIC)) {
                    mediaPkgs.add(pkg)
                    if (sbn.isOngoing) {
                        anyPlaying = true
                    }
                }
            }

            runCatching {
                val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                val component = ComponentName(this, EqMediaNotificationListenerService::class.java)
                val controllers: List<MediaController> = msm?.getActiveSessions(component) ?: emptyList()
                for (controller in controllers) {
                    val pkg = controller.packageName ?: continue
                    if (MusicAppTracker.isMusicAppPackage(applicationContext, pkg, AudioEffect.CONTENT_TYPE_MUSIC)) {
                        mediaPkgs.add(pkg)
                        val state = controller.playbackState?.state
                        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
                            anyPlaying = true
                        }
                    }
                }
            }

            MusicAppTracker.updateNotificationMediaPackages(
                context = applicationContext,
                packages = mediaPkgs,
                playing = anyPlaying
            )
        } catch (_: Throwable) {
        }
    }
}

object EqualizerNotificationHelper {
    const val CHANNEL_ID = "audio_eq_running_channel"
    const val NOTIFICATION_ID = 3101
    const val ACTION_STOP_EQUALIZER = "com.audioeq.action.STOP_EQUALIZER"
    const val ACTION_RESTORE_NOTIFICATION = "com.audioeq.action.RESTORE_NOTIFICATION"

    private const val PREFS_NOTIF_STATE = "audio_eq_notif_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PRESET = "preset"
    private const val KEY_PROFILE = "profile"
    private const val KEY_ROUTE = "route"
    private const val KEY_ENGINE = "engine"
    private const val KEY_SUMMARY = "summary"

    internal data class NotificationPayload(
        val presetName: String,
        val profileTitle: String,
        val routeLabel: String,
        val engineShortTitle: String,
        val musicSummary: String
    )

    @Volatile
    private var lastNotificationSignature: String? = null

    @Volatile
    private var masterEqActive: Boolean = false

    @Volatile
    private var lastPayload: NotificationPayload? = null

    @Volatile
    private var lastRestoreElapsedMs: Long = 0L

    private fun savePersistedState(context: Context, active: Boolean, payload: NotificationPayload?) {
        runCatching {
            val editor = context.applicationContext
                .getSharedPreferences(PREFS_NOTIF_STATE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE, active)
            if (payload != null) {
                editor
                    .putString(KEY_PRESET, payload.presetName)
                    .putString(KEY_PROFILE, payload.profileTitle)
                    .putString(KEY_ROUTE, payload.routeLabel)
                    .putString(KEY_ENGINE, payload.engineShortTitle)
                    .putString(KEY_SUMMARY, payload.musicSummary)
            }
            editor.apply()
        }
    }

    internal fun loadPersistedState(context: Context): Pair<Boolean, NotificationPayload> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NOTIF_STATE, Context.MODE_PRIVATE)
        val active = masterEqActive || prefs.getBoolean(KEY_ACTIVE, false)
        val cached = lastPayload
        val payload = cached ?: NotificationPayload(
            presetName = prefs.getString(KEY_PRESET, "Flat") ?: "Flat",
            profileTitle = prefs.getString(KEY_PROFILE, "Off") ?: "Off",
            routeLabel = prefs.getString(KEY_ROUTE, "Phone Speaker") ?: "Phone Speaker",
            engineShortTitle = prefs.getString(KEY_ENGINE, "Legacy") ?: "Legacy",
            musicSummary = prefs.getString(KEY_SUMMARY, "Universal (All Audio)") ?: "Universal (All Audio)"
        )
        return active to payload
    }

    fun isPermanentNotificationActive(context: Context): Boolean {
        if (masterEqActive) return true
        return runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NOTIF_STATE, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false)
        }.getOrDefault(false)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Equalizer Active Status",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent status notification while Audio EQ is running"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun buildNotification(
        context: Context,
        presetName: String,
        profileTitle: String,
        routeLabel: String,
        engineShortTitle: String,
        musicSummary: String
    ): Notification {
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, EqualizerActionReceiver::class.java).apply {
            action = ACTION_STOP_EQUALIZER
        }
        val stopPending = PendingIntent.getBroadcast(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val restoreIntent = Intent(context, EqualizerActionReceiver::class.java).apply {
            action = ACTION_RESTORE_NOTIFICATION
        }
        val restorePending = PendingIntent.getBroadcast(
            context,
            2,
            restoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val profilePart = if (profileTitle.isNotBlank() && !profileTitle.equals("Off", ignoreCase = true)) {
            " · $profileTitle"
        } else {
            ""
        }
        val title = "Equalizer Running · $presetName$profilePart"
        val subtitle = "$routeLabel · $engineShortTitle · $musicSummary"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eq_notification)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPending)
            .setDeleteIntent(restorePending)
            .addAction(
                0,
                "Turn Off",
                stopPending
            )
            .build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        return notification
    }

    fun restoreIfActive(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRestoreElapsedMs < 200L) return false
        val (active, payload) = loadPersistedState(context)
        if (!active) return false
        lastRestoreElapsedMs = now
        updateOrCancelPersistentNotification(
            context = context.applicationContext,
            masterEnabled = true,
            presetName = payload.presetName,
            profileTitle = payload.profileTitle,
            routeLabel = payload.routeLabel,
            engineShortTitle = payload.engineShortTitle,
            musicSummary = payload.musicSummary,
            forceRepost = true
        )
        return true
    }

    fun updateOrCancelPersistentNotification(
        context: Context,
        masterEnabled: Boolean,
        presetName: String,
        profileTitle: String,
        routeLabel: String,
        engineShortTitle: String,
        musicSummary: String,
        forceRepost: Boolean = false
    ) {
        try {
            val signature = if (!masterEnabled) {
                "OFF"
            } else {
                "ON|$presetName|$profileTitle|$routeLabel|$engineShortTitle|$musicSummary"
            }
            if (!forceRepost && lastNotificationSignature == signature) return
            lastNotificationSignature = signature

            if (!masterEnabled) {
                masterEqActive = false
                lastPayload = null
                savePersistedState(context, active = false, payload = null)
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
                runCatching {
                    context.stopService(Intent(context, EqualizerNotificationService::class.java))
                }
                return
            }

            val payload = NotificationPayload(
                presetName = presetName,
                profileTitle = profileTitle,
                routeLabel = routeLabel,
                engineShortTitle = engineShortTitle,
                musicSummary = musicSummary
            )
            masterEqActive = true
            lastPayload = payload
            savePersistedState(context, active = true, payload = payload)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    lastNotificationSignature = null
                    return
                }
            }

            val notification = buildNotification(
                context = context,
                presetName = presetName,
                profileTitle = profileTitle,
                routeLabel = routeLabel,
                engineShortTitle = engineShortTitle,
                musicSummary = musicSummary
            )
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)

            runCatching {
                val svcIntent = Intent(context, EqualizerNotificationService::class.java).apply {
                    putExtra("presetName", presetName)
                    putExtra("profileTitle", profileTitle)
                    putExtra("routeLabel", routeLabel)
                    putExtra("engineShortTitle", engineShortTitle)
                    putExtra("musicSummary", musicSummary)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent)
                } else {
                    context.startService(svcIntent)
                }
            }
        } catch (_: Throwable) {
        }
    }
}

class EqualizerNotificationService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val (persistedActive, persistedPayload) = EqualizerNotificationHelper.loadPersistedState(applicationContext)
        if (intent == null && !persistedActive) {
            stopSelf()
            return START_NOT_STICKY
        }

        val presetName = intent?.getStringExtra("presetName") ?: persistedPayload.presetName
        val profileTitle = intent?.getStringExtra("profileTitle") ?: persistedPayload.profileTitle
        val routeLabel = intent?.getStringExtra("routeLabel") ?: persistedPayload.routeLabel
        val engineShortTitle = intent?.getStringExtra("engineShortTitle") ?: persistedPayload.engineShortTitle
        val musicSummary = intent?.getStringExtra("musicSummary") ?: persistedPayload.musicSummary

        try {
            val notification = EqualizerNotificationHelper.buildNotification(
                context = applicationContext,
                presetName = presetName,
                profileTitle = profileTitle,
                routeLabel = routeLabel,
                engineShortTitle = engineShortTitle,
                musicSummary = musicSummary
            )
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                EqualizerNotificationHelper.NOTIFICATION_ID,
                notification,
                fgsType
            )
        } catch (_: Throwable) {
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (EqualizerNotificationHelper.isPermanentNotificationActive(applicationContext)) {
            EqualizerNotificationHelper.restoreIfActive(applicationContext)
        }
    }

    override fun onDestroy() {
        if (!EqualizerNotificationHelper.isPermanentNotificationActive(applicationContext)) {
            runCatching {
                NotificationManagerCompat.from(applicationContext).cancel(EqualizerNotificationHelper.NOTIFICATION_ID)
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class EqualizerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        when (intent?.action) {
            EqualizerNotificationHelper.ACTION_STOP_EQUALIZER -> {
                runCatching {
                    val repo = com.audioeq.data.EqRepository(context.applicationContext)
                    repo.setMasterEnabled(false)
                    AudioEngineController.disableActiveInstanceFromNotification()
                    EqualizerNotificationHelper.updateOrCancelPersistentNotification(
                        context = context.applicationContext,
                        masterEnabled = false,
                        presetName = "Flat",
                        profileTitle = "Off",
                        routeLabel = "Speaker",
                        engineShortTitle = "Legacy",
                        musicSummary = "",
                        forceRepost = true
                    )
                }
            }
            EqualizerNotificationHelper.ACTION_RESTORE_NOTIFICATION -> {
                runCatching {
                    if (EqualizerNotificationHelper.isPermanentNotificationActive(context.applicationContext)) {
                        EqualizerNotificationHelper.restoreIfActive(context.applicationContext)
                    }
                }
            }
        }
    }
}

class AudioSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        MusicAppTracker.init(context)
        MusicAppTracker.handleSessionBroadcastIntent(context, intent)
    }
}
