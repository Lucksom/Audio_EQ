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
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
    val source: String
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
    }

    fun setExcludedMusicPackages(pkgs: Set<String>) {
        _excludedMusicPackages.value = pkgs
    }

    private val broadcastMusicSessions = ConcurrentHashMap<Int, TrackedMusicSession>()
    private val privilegedMusicSessions = ConcurrentHashMap<Int, TrackedMusicSession>()

    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

    private val _activeMediaPackages = MutableStateFlow<Set<String>>(emptySet())
    val activeMediaPackages: StateFlow<Set<String>> = _activeMediaPackages.asStateFlow()

    private val _activeMediaLabels = MutableStateFlow<List<String>>(emptyList())
    val activeMediaLabels: StateFlow<List<String>> = _activeMediaLabels.asStateFlow()

    private val _activeMusicPlaying = MutableStateFlow(false)
    val activeMusicPlaying: StateFlow<Boolean> = _activeMusicPlaying.asStateFlow()

    private val _trackedSessionsSummary = MutableStateFlow("Monitoring music players")
    val trackedSessionsSummary: StateFlow<String> = _trackedSessionsSummary.asStateFlow()

    @Volatile
    private var activeMediaPlaybackCount: Int = 0

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
        if (pkg in _excludedMusicPackages.value) return false

        if (pkg in _selectedMusicPackages.value) return true

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
            lower.contains("player")
        ) {
            return true
        }
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_AUDIO) {
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

    fun onBroadcastSessionOpened(
        context: Context,
        sessionId: Int,
        packageName: String?,
        contentType: Int
    ): Boolean {
        if (sessionId <= 0) return false
        if (!isMusicAppPackage(context, packageName, contentType)) {
            return false
        }
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: "music.player"
        val label = resolveAppLabel(context, pkg)
        broadcastMusicSessions[sessionId] = TrackedMusicSession(
            sessionId = sessionId,
            packageName = pkg,
            appLabel = label,
            source = "AudioEffect"
        )
        rebuildSummary(context)
        return true
    }

    fun onBroadcastSessionClosed(context: Context, sessionId: Int) {
        broadcastMusicSessions.remove(sessionId)
        privilegedMusicSessions.remove(sessionId)
        rebuildSummary(context)
    }

    fun updatePrivilegedMusicSessions(context: Context, sessions: List<TrackedMusicSession>) {
        if (privilegedMusicSessions.isEmpty() && sessions.isEmpty()) return
        privilegedMusicSessions.clear()
        for (s in sessions) {
            if (s.sessionId > 0) {
                privilegedMusicSessions[s.sessionId] = s
            }
        }
        rebuildSummary(context)
    }

    fun getBroadcastSessionIds(): Set<Int> = LinkedHashSet(broadcastMusicSessions.keys)

    fun hasActiveNonSessionMediaPlayback(): Boolean {
        val activePkgs = _activeMediaPackages.value
        if (activePkgs.isEmpty()) {
            return activeMediaPlaybackCount > broadcastMusicSessions.size
        }
        val broadcastPkgs = broadcastMusicSessions.values.map { it.packageName }.toSet()
        return activePkgs.any { it !in broadcastPkgs } ||
            activeMediaPlaybackCount > broadcastMusicSessions.size
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
            if (attr.usage == AudioAttributes.USAGE_MEDIA &&
                (attr.contentType == AudioAttributes.CONTENT_TYPE_MUSIC ||
                    attr.contentType == AudioAttributes.CONTENT_TYPE_UNKNOWN ||
                    attr.contentType == AudioAttributes.CONTENT_TYPE_MOVIE)
            ) {
                mediaCount++
            }
        }
        val prevCount = activeMediaPlaybackCount
        activeMediaPlaybackCount = mediaCount
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
        result.addAll(broadcastMusicSessions.keys)
        result.addAll(privilegedMusicSessions.keys)
        return result
    }

    fun getAllTrackedSessions(): List<TrackedMusicSession> {
        val merged = LinkedHashMap<Int, TrackedMusicSession>()
        for ((sid, s) in broadcastMusicSessions) merged[sid] = s
        for ((sid, s) in privilegedMusicSessions) merged[sid] = s
        return merged.values.sortedBy { it.sessionId }
    }

    fun parseMusicSessionsFromDumpsys(
        context: Context,
        audioDumpsys: String?,
        flingerDumpsys: String?
    ): List<TrackedMusicSession> {
        val results = LinkedHashMap<Int, TrackedMusicSession>()
        val pm = context.packageManager

        if (!audioDumpsys.isNullOrBlank()) {
            val lines = audioDumpsys.lineSequence()
            for (line in lines) {
                if (!line.contains("sessionId", ignoreCase = true) && !line.contains("session:", ignoreCase = true)) {
                    continue
                }
                val lowerLine = line.lowercase(Locale.US)
                if (lowerLine.contains("state:released") || lowerLine.contains("state:stopped") || lowerLine.contains("state:idle")) {
                    continue
                }
                if (lowerLine.contains("usage=") &&
                    !lowerLine.contains("usage=usage_media") &&
                    !lowerLine.contains("usage=1 ")
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

                val pkgMatch = Regex("""(?:pkg|package)\s*[:=]\s*([a-zA-Z0-9_.]+)""").find(line)
                val uidMatch = Regex("""(?:u/pid|uid)\s*[:=]\s*(\d+)""").find(line)
                val uid = uidMatch?.groupValues?.get(1)?.toIntOrNull()

                val candidatePkgs = mutableListOf<String>()
                pkgMatch?.groupValues?.get(1)?.let { candidatePkgs.add(it) }
                if (uid != null && uid >= 10000) {
                    runCatching {
                        pm.getPackagesForUid(uid)?.let { candidatePkgs.addAll(it) }
                    }
                }

                val matchedPkg = candidatePkgs.firstOrNull { isMusicAppPackage(context, it, AudioEffect.CONTENT_TYPE_MUSIC) }
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
            val lines = flingerDumpsys.lineSequence()
            for (line in lines) {
                val sidMatch = Regex("""(?:session(?:Id)?|Session)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(line)
                if (sidMatch != null) {
                    val sid = sidMatch.groupValues[1].toIntOrNull() ?: continue
                    if (sid <= 0 || results.containsKey(sid)) continue
                    val uidMatch = Regex("""(?:uid|client)\s*[:=]?\s*(\d{5,})""", RegexOption.IGNORE_CASE).find(line)
                    val uid = uidMatch?.groupValues?.get(1)?.toIntOrNull()
                    if (uid != null && uid >= 10000) {
                        val pkgs = runCatching { pm.getPackagesForUid(uid)?.toList() ?: emptyList() }.getOrDefault(emptyList())
                        val matchedPkg = pkgs.firstOrNull { isMusicAppPackage(context, it, AudioEffect.CONTENT_TYPE_MUSIC) }
                        if (matchedPkg != null) {
                            results[sid] = TrackedMusicSession(
                                sessionId = sid,
                                packageName = matchedPkg,
                                appLabel = resolveAppLabel(context, matchedPkg),
                                source = "AudioFlinger"
                            )
                        }
                    } else if (_activeMediaPackages.value.isNotEmpty()) {
                        val firstMediaPkg = _activeMediaPackages.value.first()
                        results[sid] = TrackedMusicSession(
                            sessionId = sid,
                            packageName = firstMediaPkg,
                            appLabel = resolveAppLabel(context, firstMediaPkg),
                            source = "Notif+Flinger"
                        )
                    }
                }
            }
        }

        return results.values.toList()
    }

    private fun rebuildSummary(context: Context) {
        isNotificationListenerEnabled(context)
        val tracked = getAllTrackedSessions()
        val mediaLabels = _activeMediaLabels.value
        _trackedSessionsSummary.value = when {
            tracked.isNotEmpty() -> {
                tracked.joinToString(", ") { "${it.appLabel} (#${it.sessionId})" }
            }
            mediaLabels.isNotEmpty() -> {
                "${mediaLabels.joinToString(", ")} (Notification tracked)"
            }
            _activeMusicPlaying.value -> {
                "Music stream active"
            }
            else -> {
                "Idle · Waiting for music player"
            }
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

        val profilePart = if (profileTitle.isNotBlank() && !profileTitle.equals("Off", ignoreCase = true)) {
            " · $profileTitle"
        } else {
            ""
        }
        val title = "Equalizer Running · $presetName$profilePart"
        val subtitle = "$routeLabel · $engineShortTitle · $musicSummary"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eq_notification)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPending)
            .addAction(
                0,
                "Turn Off",
                stopPending
            )
            .build()
    }

    @Volatile
    private var lastNotificationSignature: String? = null

    fun updateOrCancelPersistentNotification(
        context: Context,
        masterEnabled: Boolean,
        presetName: String,
        profileTitle: String,
        routeLabel: String,
        engineShortTitle: String,
        musicSummary: String
    ) {
        try {
            val signature = if (!masterEnabled) {
                "OFF"
            } else {
                "ON|$presetName|$profileTitle|$routeLabel|$engineShortTitle|$musicSummary"
            }
            if (lastNotificationSignature == signature) return
            lastNotificationSignature = signature

            if (!masterEnabled) {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
                runCatching {
                    context.stopService(Intent(context, EqualizerNotificationService::class.java))
                }
                return
            }

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
        val presetName = intent?.getStringExtra("presetName") ?: "Flat"
        val profileTitle = intent?.getStringExtra("profileTitle") ?: "Off"
        val routeLabel = intent?.getStringExtra("routeLabel") ?: "Speaker"
        val engineShortTitle = intent?.getStringExtra("engineShortTitle") ?: "Legacy"
        val musicSummary = intent?.getStringExtra("musicSummary") ?: "Music Tracking Active"

        try {
            val notification = EqualizerNotificationHelper.buildNotification(
                context = applicationContext,
                presetName = presetName,
                profileTitle = profileTitle,
                routeLabel = routeLabel,
                engineShortTitle = engineShortTitle,
                musicSummary = musicSummary
            )
            startForeground(EqualizerNotificationHelper.NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching {
            NotificationManagerCompat.from(applicationContext).cancel(EqualizerNotificationHelper.NOTIFICATION_ID)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class EqualizerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        if (intent?.action == EqualizerNotificationHelper.ACTION_STOP_EQUALIZER) {
            runCatching {
                val repo = com.audioeq.data.EqRepository(context.applicationContext)
                repo.setMasterEnabled(false)
                EqualizerNotificationHelper.updateOrCancelPersistentNotification(
                    context = context.applicationContext,
                    masterEnabled = false,
                    presetName = "Flat",
                    profileTitle = "Off",
                    routeLabel = "Speaker",
                    engineShortTitle = "Legacy",
                    musicSummary = ""
                )
            }
        }
    }
}
