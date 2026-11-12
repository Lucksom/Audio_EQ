package com.audioeq

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.audioeq.audio.AutoEqKind
import com.audioeq.audio.AutoEqMapper
import com.audioeq.audio.AutoEqParser
import com.audioeq.audio.AutoEqRepository
import com.audioeq.data.EqualizerDatabaseHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioEqRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Audio EQ", appName)
    }

    @Test
    fun `verify sqlite database seeds presets and ieq tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbHelper = EqualizerDatabaseHelper(context)
        val db = dbHelper.readableDatabase

        db.rawQuery("SELECT COUNT(*) FROM presets", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(9, c.getInt(0))
        }

        db.rawQuery("SELECT COUNT(*) FROM ieq_profiles", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(20, c.getInt(0))
        }
        dbHelper.close()
    }

    @Test
    fun `verify autoeq_index asset loads searches and maps HD 650`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AutoEqRepository(context)
        val index = repo.loadIndex()
        assertTrue("autoeq_index.json should contain entries", index.size >= 50)

        val hd650Results = repo.search(index, "HD 650")
        assertTrue(hd650Results.isNotEmpty())
        val hd650 = hd650Results.first()
        assertEquals("Sennheiser HD 650", hd650.name)
        assertEquals("oratory1990", hd650.source)

        val matched = repo.matchDevice(index, "WH-1000XM5")
        assertNotNull(matched)

        val peqText = repo.fetch(hd650, AutoEqKind.PARAMETRIC)
        val parsedPeq = AutoEqParser.parseParametric(peqText)
        assertTrue(parsedPeq.filters.isNotEmpty())
        val dpBands = AutoEqMapper.toDynamicsBands(parsedPeq)
        assertEquals(31, dpBands.size)

        val fbText = repo.fetch(hd650, AutoEqKind.FIXED_BAND)
        val parsedFb = AutoEqParser.parseParametric(fbText)
        val tenBand = AutoEqMapper.fixedBandToTenBand(parsedFb)
        assertEquals(10, tenBand.gainsDb.size)
    }

    @Test
    fun `verify GitHub release JSON parsing extracts version changelog and apk asset`() {
        val sampleJson = """
            {
              "tag_name": "v1.0.18.1",
              "name": "Audio EQ v1.0.18.1",
              "body": "### What's Changed\n- Fixed Shizuku/Root status badge",
              "published_at": "2026-11-12T10:00:00Z",
              "html_url": "https://github.com/user/AudioEQ/releases/tag/v1.0.18.1",
              "assets": [
                {
                  "name": "AudioEQ-v1.0.18.1.apk",
                  "size": 12345678,
                  "browser_download_url": "https://github.com/user/AudioEQ/releases/download/v1.0.18.1/AudioEQ-v1.0.18.1.apk"
                }
              ]
            }
        """.trimIndent()
        val parsed = com.audioeq.data.GitHubUpdateChecker.parseReleaseJsonObject(org.json.JSONObject(sampleJson))
        assertNotNull(parsed)
        assertEquals("v1.0.18.1", parsed!!.tagName)
        assertEquals("AudioEQ-v1.0.18.1.apk", parsed.apkFileName)
        assertTrue(parsed.changelog.contains("Fixed Shizuku/Root status badge"))
    }

    @Test
    fun `verify MusicAppTracker auto-detects when empty and strictly filters when device apps are selected`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 1. Empty selection -> auto-detect music & media players on the device
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(emptySet())
        com.audioeq.audio.MusicAppTracker.setExcludedMusicPackages(emptySet())
        assertTrue(
            com.audioeq.audio.MusicAppTracker.isMusicAppPackage(
                context,
                "com.miui.player",
                android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
            )
        )
        assertTrue(
            com.audioeq.audio.MusicAppTracker.isMusicAppPackage(
                context,
                "app.morphe.android.youtube",
                android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
            )
        )
        assertTrue(
            com.audioeq.audio.MusicAppTracker.isMusicAppPackage(
                context,
                "com.apple.android.music",
                android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
            )
        )
        org.junit.Assert.assertFalse(
            com.audioeq.audio.MusicAppTracker.isMusicAppPackage(
                context,
                "com.whatsapp",
                android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
            )
        )

        // 2. Non-empty device app selection -> strict whitelist
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(setOf("com.spotify.music"))
        assertTrue(
            com.audioeq.audio.MusicAppTracker.isMusicAppPackage(
                context,
                "com.spotify.music",
                android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
            )
        )
        org.junit.Assert.assertFalse(
            com.audioeq.audio.MusicAppTracker.isMusicAppPackage(
                context,
                "com.miui.player",
                android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
            )
        )

        // 3. Clearing returns to auto-detect, still respecting explicit exclusions
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(emptySet())
        com.audioeq.audio.MusicAppTracker.setExcludedMusicPackages(setOf("com.spotify.music"))
        org.junit.Assert.assertFalse(
            com.audioeq.audio.MusicAppTracker.isMusicAppPackage(
                context,
                "com.spotify.music",
                android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
            )
        )
        com.audioeq.audio.MusicAppTracker.setExcludedMusicPackages(emptySet())
    }

    @Test
    fun `verify loadDeviceAppList returns device entries without throwing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = com.audioeq.audio.MusicAppTracker.loadDeviceAppList(
            context = context,
            selectedPackages = setOf("com.spotify.music")
        )
        assertTrue(entries.any { it.packageName == "com.spotify.music" })
    }

    @Test
    fun `verify dumpsys parsing excludes historical log paused and unselected sessions and formats notification summary`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(setOf("com.spotify.music"))
        com.audioeq.audio.MusicAppTracker.setEngineTrackingContext(
            context = context,
            isLegacyMode = false,
            hasPrivilegedControl = true
        )

        val audioDumpsys = """
            players:
              ID:11 -- type:android.media.AudioTrack -- u/pid:10200/1111 -- state:started -- attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC -- pkg:com.spotify.music -- sessionId:42
              ID:12 -- type:android.media.AudioTrack -- u/pid:10201/2222 -- state:paused -- attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC -- pkg:com.spotify.music -- sessionId:43
              ID:13 -- type:android.media.AudioTrack -- u/pid:10202/3333 -- state:started -- attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MOVIE -- pkg:com.android.chrome -- sessionId:99
            Audio event log: playback activity as reported through PlayerBase
              11-12 10:00:00:000 player piid:99 u/pid:10200/1111 state:started pkg:com.spotify.music sessionId:77
        """.trimIndent()

        val sessions = com.audioeq.audio.MusicAppTracker.parseMusicSessionsFromDumpsys(
            context = context,
            audioDumpsys = audioDumpsys,
            flingerDumpsys = null
        )
        assertEquals(listOf(42), sessions.map { it.sessionId })

        com.audioeq.audio.MusicAppTracker.updatePrivilegedMusicSessions(context, sessions)
        val summary = com.audioeq.audio.MusicAppTracker.trackedSessionsSummary.value
        assertTrue(summary.contains("42"))
        org.junit.Assert.assertFalse(summary.contains("43"))
        org.junit.Assert.assertFalse(summary.contains("77"))
        org.junit.Assert.assertFalse(summary.contains("99"))

        com.audioeq.audio.MusicAppTracker.updatePrivilegedMusicSessions(context, emptyList())
        assertEquals("No active music session", com.audioeq.audio.MusicAppTracker.trackedSessionsSummary.value)

        com.audioeq.audio.MusicAppTracker.setEngineTrackingContext(
            context = context,
            isLegacyMode = true,
            hasPrivilegedControl = false
        )
        assertEquals("Universal (All Audio)", com.audioeq.audio.MusicAppTracker.trackedSessionsSummary.value)
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(emptySet())
    }

    @Test
    fun `verify Legacy mode tracks all broadcast sessions universally while privileged mode strictly filters`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(setOf("com.spotify.music"))
        com.audioeq.audio.MusicAppTracker.setEngineTrackingContext(
            context = context,
            isLegacyMode = true,
            hasPrivilegedControl = false
        )

        // Open broadcast session from an unselected app (e.g., Chrome) and a selected app (Spotify)
        com.audioeq.audio.MusicAppTracker.onBroadcastSessionOpened(
            context = context,
            sessionId = 501,
            packageName = "com.android.chrome",
            contentType = android.media.audiofx.AudioEffect.CONTENT_TYPE_MOVIE
        )
        com.audioeq.audio.MusicAppTracker.onBroadcastSessionOpened(
            context = context,
            sessionId = 502,
            packageName = "com.spotify.music",
            contentType = android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
        )

        val legacyAll = com.audioeq.audio.MusicAppTracker.getAllBroadcastSessionIds()
        assertTrue(legacyAll.contains(501))
        assertTrue(legacyAll.contains(502))

        // Switch to privileged mode (Root/Shizuku) -> strict filtering excludes 501 (Chrome)
        com.audioeq.audio.MusicAppTracker.setEngineTrackingContext(
            context = context,
            isLegacyMode = false,
            hasPrivilegedControl = false
        )
        val verifiedOnly = com.audioeq.audio.MusicAppTracker.getVerifiedMusicSessionIds()
        org.junit.Assert.assertFalse(verifiedOnly.contains(501))
        assertTrue(verifiedOnly.contains(502))

        // Cleanup
        com.audioeq.audio.MusicAppTracker.onBroadcastSessionClosed(context, 501)
        com.audioeq.audio.MusicAppTracker.onBroadcastSessionClosed(context, 502)
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(emptySet())
    }

    @Test
    fun `verify MasterEqEngine in Legacy mode ignores session tracking and keeps global mode`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = com.audioeq.audio.MasterEqEngine(
            context = context,
            onRouteOrDeviceChanged = { _, _, _ -> },
            onSessionsChanged = { _, _ -> }
        )
        try {
            engine.setEngineMode(com.audioeq.model.EngineMode.LEGACY, privilegeReady = true)
            engine.attachSession(404)
            engine.syncDiscoveredSessions(listOf(404, 505))
            Thread.sleep(150L)

            val sids = engine.getActiveSessionIds()
            org.junit.Assert.assertFalse("Legacy mode must not track external sid > 0", sids.contains(404))
            org.junit.Assert.assertFalse("Legacy mode must not track external sid > 0", sids.contains(505))
            assertTrue(
                "Legacy mode should keep global Legacy operating mode",
                engine.activeOperatingMode == com.audioeq.model.OperatingMode.LEGACY_TIER2_DP ||
                    engine.activeOperatingMode == com.audioeq.model.OperatingMode.LEGACY_TIER1_EQ
            )
            assertTrue(
                "Legacy Mix AUTO label should reflect 31-Band DP default",
                com.audioeq.model.LegacyGlobalMixMode.AUTO.label.contains("31-Band DP")
            )
            // Verify healthy query does not throw
            engine.isSession0Healthy()
            assertTrue(engine.getLegacyEngineSummary().startsWith("Mix 0:"))
        } finally {
            engine.release()
        }
    }
}
