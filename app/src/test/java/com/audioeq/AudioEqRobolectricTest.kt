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
    fun `verify sqlite database seeds presets and subwoofer settings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbHelper = EqualizerDatabaseHelper(context)
        val db = dbHelper.readableDatabase

        db.rawQuery("SELECT COUNT(*) FROM presets", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(9, c.getInt(0))
        }

        db.rawQuery("SELECT COUNT(*) FROM dsp_mathematical_models", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4, c.getInt(0))
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

        com.audioeq.audio.MusicAppTracker.setEngineTrackingContext(
            context = context,
            isLegacyMode = false,
            hasPrivilegedControl = false
        )
        val verifiedOnly = com.audioeq.audio.MusicAppTracker.getVerifiedMusicSessionIds()
        org.junit.Assert.assertFalse(verifiedOnly.contains(501))
        assertTrue(verifiedOnly.contains(502))

        com.audioeq.audio.MusicAppTracker.onBroadcastSessionClosed(context, 501)
        com.audioeq.audio.MusicAppTracker.onBroadcastSessionClosed(context, 502)
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(emptySet())
    }

    @Test
    fun `verify MasterEqEngine in Legacy mode tracks sessions and keeps global mode`() {
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
            assertTrue("Legacy mode must attach to external sid 404", sids.contains(404))
            assertTrue("Legacy mode must attach to discovered sid 505", sids.contains(505))
            assertTrue("Legacy mode must retain global Mix 0", sids.contains(0))
            assertTrue(
                "Legacy mode should keep global Legacy operating mode",
                engine.activeOperatingMode == com.audioeq.model.OperatingMode.LEGACY_TIER2_DP ||
                    engine.activeOperatingMode == com.audioeq.model.OperatingMode.LEGACY_TIER1_EQ
            )
            assertTrue(
                "Legacy Mix AUTO label should reflect 31-Band DP default",
                com.audioeq.model.LegacyGlobalMixMode.AUTO.label.contains("31-Band DP")
            )
            engine.isSession0Healthy()
            assertTrue(engine.getLegacyEngineSummary().startsWith("Mix 0:"))
        } finally {
            com.audioeq.audio.MusicAppTracker.onBroadcastSessionClosed(context, 404)
            engine.release()
        }
    }

    @Test
    fun `verify per-device Equalizer and Enhancer settings save and switch independently across Speaker Wired and Bluetooth`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.audioeq.data.EqRepository(context)

        repo.switchOutputRoute(com.audioeq.model.OutputRoute.SPEAKER, "speaker:builtin")
        repo.updateBandGain(0, 2.0f)
        repo.updateEnhancerConfig {
            it.copy(
                subwooferEnabled = true,
                subwooferIntensity = 0.40f,
                deepBassEnabled = true
            )
        }

        repo.switchOutputRoute(com.audioeq.model.OutputRoute.BLUETOOTH, "bt:AA:BB:CC:DD:EE:FF")
        assertEquals(0.0f, repo.currentRouteProfile.value.bandGainsDb[0], 0.01f)
        assertEquals(false, repo.enhancerConfig.value.subwooferEnabled)
        assertEquals(0.0f, repo.enhancerConfig.value.subwooferIntensity, 0.01f)

        repo.updateBandGain(0, 4.5f)
        repo.updateEnhancerConfig {
            it.copy(
                subwooferEnabled = true,
                subwooferIntensity = 0.15f,
                deepBassEnabled = false
            )
        }

        repo.switchOutputRoute(com.audioeq.model.OutputRoute.HEADPHONES, "wired:3.5mm")
        assertEquals(0.0f, repo.currentRouteProfile.value.bandGainsDb[0], 0.01f)
        assertEquals(false, repo.enhancerConfig.value.subwooferEnabled)

        repo.switchOutputRoute(com.audioeq.model.OutputRoute.SPEAKER, "speaker:builtin")
        assertEquals(2.0f, repo.currentRouteProfile.value.bandGainsDb[0], 0.01f)
        assertEquals(true, repo.enhancerConfig.value.subwooferEnabled)
        assertEquals(0.40f, repo.enhancerConfig.value.subwooferIntensity, 0.01f)
        assertEquals(true, repo.enhancerConfig.value.deepBassEnabled)

        repo.switchOutputRoute(com.audioeq.model.OutputRoute.BLUETOOTH, "bt:AA:BB:CC:DD:EE:FF")
        assertEquals(4.5f, repo.currentRouteProfile.value.bandGainsDb[0], 0.01f)
        assertEquals(true, repo.enhancerConfig.value.subwooferEnabled)
        assertEquals(0.15f, repo.enhancerConfig.value.subwooferIntensity, 0.01f)
        assertEquals(false, repo.enhancerConfig.value.deepBassEnabled)
    }

    @Test
    fun `verify MasterEqEngine in Root and Shizuku modes disables Global Mix 0 and attaches discovered app sessions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.audioeq.audio.MusicAppTracker.updateNotificationMediaPackages(
            context = context,
            packages = emptySet(),
            playing = false
        )
        val engine = com.audioeq.audio.MasterEqEngine(
            context = context,
            onRouteOrDeviceChanged = { _, _, _ -> },
            onSessionsChanged = { _, _ -> }
        )
        try {
            fun waitUntil(timeoutMs: Long = 1000L, predicate: () -> Boolean): Boolean {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    if (predicate()) return true
                    Thread.sleep(25L)
                }
                return predicate()
            }

            engine.setEngineMode(com.audioeq.model.EngineMode.ADB_SHIZUKU, privilegeReady = true)
            engine.syncDiscoveredSessions(listOf(707, 808))
            waitUntil {
                val s = engine.getActiveSessionIds()
                s.contains(707) && s.contains(808) && !s.contains(0)
            }

            val sids = engine.getActiveSessionIds()
            assertTrue("Shizuku mode must attach discovered session 707", sids.contains(707))
            assertTrue("Shizuku mode must attach discovered session 808", sids.contains(808))
            org.junit.Assert.assertFalse("Shizuku mode must disable Global Mix 0", sids.contains(0))
            org.junit.Assert.assertFalse(
                "Privileged Mix 0 fallback must stay off when idle",
                engine.isPrivilegedMix0FallbackActive()
            )

            com.audioeq.audio.MusicAppTracker.updateNotificationMediaPackages(
                context = context,
                packages = setOf("com.spotify.music"),
                playing = true
            )
            engine.syncDiscoveredSessions(emptyList())
            waitUntil {
                engine.isPrivilegedMix0FallbackActive() && engine.getActiveSessionIds() == listOf(0)
            }

            val fallbackSids = engine.getActiveSessionIds()
            assertTrue(
                "Privileged Mix 0 fallback must activate when audio is playing and zero non-zero sessions exist",
                engine.isPrivilegedMix0FallbackActive()
            )
            assertEquals(listOf(0), fallbackSids)

            com.audioeq.audio.MusicAppTracker.updateNotificationMediaPackages(
                context = context,
                packages = emptySet(),
                playing = false
            )
            engine.syncDiscoveredSessions(emptyList())
            waitUntil {
                !engine.isPrivilegedMix0FallbackActive() && !engine.getActiveSessionIds().contains(0)
            }

            org.junit.Assert.assertFalse(
                "Privileged Mix 0 fallback must disengage when audio stops playing",
                engine.isPrivilegedMix0FallbackActive()
            )
            org.junit.Assert.assertFalse(
                "Global Mix 0 must be removed once playback stops",
                engine.getActiveSessionIds().contains(0)
            )
        } finally {
            com.audioeq.audio.MusicAppTracker.updateNotificationMediaPackages(
                context = context,
                packages = emptySet(),
                playing = false
            )
            engine.release()
        }
    }

    @Test
    fun `verify Bass Enhancer scales clearly and monotonically from 1 dB to 6 dB`() {
        val freqs = com.audioeq.model.EqConstants.BAND_FREQUENCIES_31
        val idx63 = freqs.indices.minByOrNull { kotlin.math.abs(freqs[it] - 63.0) } ?: 5
        val idx100 = freqs.indices.minByOrNull { kotlin.math.abs(freqs[it] - 100.0) } ?: 7

        val curveOff = com.audioeq.audio.EnhancerDsp.computeBassEnhancer31(
            com.audioeq.audio.EnhancerConfig(bassEnabled = false, bassBoostDb = 6.0f)
        )
        assertEquals(0.0, curveOff[idx63], 0.001)

        val curve1Db = com.audioeq.audio.EnhancerDsp.computeBassEnhancer31(
            com.audioeq.audio.EnhancerConfig(bassEnabled = true, bassBoostDb = 1.0f)
        )
        val curve3Db = com.audioeq.audio.EnhancerDsp.computeBassEnhancer31(
            com.audioeq.audio.EnhancerConfig(bassEnabled = true, bassBoostDb = 3.0f)
        )
        val curve6Db = com.audioeq.audio.EnhancerDsp.computeBassEnhancer31(
            com.audioeq.audio.EnhancerConfig(bassEnabled = true, bassBoostDb = 6.0f)
        )

        assertTrue("1 dB Bass Enhancer should lift 63 Hz by > 1.0 dB", curve1Db[idx63] > 1.0)
        assertTrue("3 dB Bass Enhancer should exceed 1 dB at 63 Hz", curve3Db[idx63] > curve1Db[idx63] + 2.0)
        assertTrue("6 dB Bass Enhancer should exceed 1 dB by at least 5.0 dB at 63 Hz", curve6Db[idx63] - curve1Db[idx63] >= 5.0)
        assertTrue("6 dB Bass Enhancer should deliver noticeable upper-bass lift at 100 Hz", curve6Db[idx100] > curve1Db[idx100] + 3.0)
    }

    @Test
    fun `verify Subwoofer curve delivers strong earbud-audible sub-bass lift and responds to crossover and sub-octave mix`() {
        val baseCfg = com.audioeq.audio.EnhancerConfig(
            subwooferEnabled = true,
            subwooferIntensity = 0.65f,
            subwooferSubOctaveMix = 0.25f
        )
        assertEquals(80.0f, baseCfg.subwooferCrossoverHz, 0.01f)

        val baseCurve = com.audioeq.audio.EnhancerDsp.computeSubwooferCurve31(baseCfg)
        assertTrue("20 Hz band must not be cut negative on earbuds", baseCurve[0] >= 0.0)
        assertTrue("40 Hz band must deliver strong > +4.0 dB lift", baseCurve[3] > 4.0)
        assertTrue("50 Hz band must deliver strong > +4.0 dB lift", baseCurve[4] > 4.0)
        assertTrue("63 Hz band must deliver strong > +3.5 dB lift", baseCurve[5] > 3.5)

        val lowXoverCurve = com.audioeq.audio.EnhancerDsp.computeSubwooferCurve31(
            baseCfg.copy(subwooferCrossoverHz = 58.0f)
        )
        val highXoverCurve = com.audioeq.audio.EnhancerDsp.computeSubwooferCurve31(
            baseCfg.copy(subwooferCrossoverHz = 110.0f)
        )
        assertTrue(
            "Raising crossover from 58 Hz to 110 Hz should boost 100 Hz band",
            highXoverCurve[7] > lowXoverCurve[7] + 0.5
        )

        val lowSubOctCurve = com.audioeq.audio.EnhancerDsp.computeSubwooferCurve31(
            baseCfg.copy(subwooferSubOctaveMix = 0.0f)
        )
        val highSubOctCurve = com.audioeq.audio.EnhancerDsp.computeSubwooferCurve31(
            baseCfg.copy(subwooferSubOctaveMix = 0.50f)
        )
        assertTrue(
            "Increasing sub-octave mix should boost 31.5 Hz sub-bass band",
            highSubOctCurve[2] > lowSubOctCurve[2] + 0.5
        )
    }

    @Test
    fun `verify all Audio Profiles produce distinct audible contours and specs on Bluetooth earbuds`() {
        val freqs = com.audioeq.model.EqConstants.BAND_FREQUENCIES_31
        val idx40 = freqs.indices.minByOrNull { kotlin.math.abs(freqs[it] - 40.0) } ?: 3
        val idx63 = freqs.indices.minByOrNull { kotlin.math.abs(freqs[it] - 63.0) } ?: 5
        val idx2000 = freqs.indices.minByOrNull { kotlin.math.abs(freqs[it] - 2000.0) } ?: 20
        val idx12500 = freqs.indices.minByOrNull { kotlin.math.abs(freqs[it] - 12500.0) } ?: 28

        val bt = com.audioeq.model.OutputRoute.BLUETOOTH
        val base = com.audioeq.audio.EnhancerConfig()

        val dynamicSpec = com.audioeq.audio.EnhancerDsp.resolveEndpointSpec(com.audioeq.audio.AudioProfile.DYNAMIC, bt)
        val videoSpec = com.audioeq.audio.EnhancerDsp.resolveEndpointSpec(com.audioeq.audio.AudioProfile.VIDEO, bt)
        val musicSpec = com.audioeq.audio.EnhancerDsp.resolveEndpointSpec(com.audioeq.audio.AudioProfile.MUSIC, bt)
        val voiceSpec = com.audioeq.audio.EnhancerDsp.resolveEndpointSpec(com.audioeq.audio.AudioProfile.VOICE, bt)

        assertTrue(dynamicSpec.deEnabled && dynamicSpec.virtualizerEnabled)
        assertTrue(videoSpec.deEnabled && videoSpec.deAmount >= 8 && videoSpec.virtualizerEnabled && videoSpec.virtualizerWidth >= 0.6f)
        assertTrue(!musicSpec.deEnabled && !musicSpec.virtualizerEnabled && musicSpec.volmaxMakeupDb > 0.5f)
        assertTrue(voiceSpec.deEnabled && voiceSpec.deAmount >= 10 && !voiceSpec.virtualizerEnabled)

        val dynamicCfg = com.audioeq.audio.EnhancerDsp.applyProfileForRoute(base, com.audioeq.audio.AudioProfile.DYNAMIC, bt)
        val videoCfg = com.audioeq.audio.EnhancerDsp.applyProfileForRoute(base, com.audioeq.audio.AudioProfile.VIDEO, bt)
        val musicCfg = com.audioeq.audio.EnhancerDsp.applyProfileForRoute(base, com.audioeq.audio.AudioProfile.MUSIC, bt)
        val voiceCfg = com.audioeq.audio.EnhancerDsp.applyProfileForRoute(
            base.copy(subwooferEnabled = true, subwooferIntensity = 0.65f, bassEnabled = true, bassBoostDb = 4.0f),
            com.audioeq.audio.AudioProfile.VOICE,
            bt
        )

        assertTrue(dynamicCfg.bassEnabled && dynamicCfg.surroundEnabled && dynamicCfg.dialogueEnhancerEnabled)
        assertTrue(videoCfg.surroundEnabled && videoCfg.surroundWidth >= 0.6f && videoCfg.dialogueAmount >= 8)
        assertTrue(musicCfg.bassEnabled && musicCfg.bassBoostDb > 1.5f)
        org.junit.Assert.assertFalse("Voice profile must disable subwoofer", voiceCfg.subwooferEnabled)
        org.junit.Assert.assertFalse("Voice profile must disable bass boost", voiceCfg.bassEnabled)
        assertTrue("Voice profile must enable strong dialogue enhancement", voiceCfg.dialogueEnhancerEnabled && voiceCfg.dialogueAmount >= 10)

        val dynamicCurve = com.audioeq.audio.EnhancerDsp.computeCombinedEnhancer31(dynamicCfg, isSpeaker = false)
        val videoCurve = com.audioeq.audio.EnhancerDsp.computeCombinedEnhancer31(videoCfg, isSpeaker = false)
        val musicCurve = com.audioeq.audio.EnhancerDsp.computeCombinedEnhancer31(musicCfg, isSpeaker = false)
        val voiceCurve = com.audioeq.audio.EnhancerDsp.computeCombinedEnhancer31(voiceCfg, isSpeaker = false)

        assertTrue("Dynamic profile must lift 63 Hz and 2 kHz", dynamicCurve[idx63] > 3.0 && dynamicCurve[idx2000] > 3.0)
        assertTrue("Video profile must deliver stronger dialogue presence than Music", videoCurve[idx2000] > musicCurve[idx2000] + 4.0)
        assertTrue("Music profile must deliver bass lift and high-frequency sparkle", musicCurve[idx63] > 3.5 && musicCurve[idx12500] > 2.0)
        assertTrue("Voice profile must cut low rumble at 40 Hz and strongly boost 2 kHz speech", voiceCurve[idx40] < -2.0 && voiceCurve[idx2000] > 8.0)

        val curves = listOf(dynamicCurve, videoCurve, musicCurve, voiceCurve)
        for (i in curves.indices) {
            for (j in i + 1 until curves.size) {
                val maxDiff = (0 until 31).maxOf { k -> kotlin.math.abs(curves[i][k] - curves[j][k]) }
                assertTrue("Profiles $i and $j must be distinctly audible (maxDiff=$maxDiff)", maxDiff > 1.5)
            }
        }
    }

    @Test
    fun `verify ShizukuManager init idempotency debuggable flag and empty-stdout command success`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

        assertEquals(expectedDebuggable, com.audioeq.shizuku.ShizukuManager.isUserServiceDebuggable(context))
        assertTrue(com.audioeq.shizuku.ShizukuManager.isCommandOutputSuccessful("", allowEmptyOutput = true))
        assertTrue(com.audioeq.shizuku.ShizukuManager.isCommandOutputSuccessful("ok", allowEmptyOutput = true))
        org.junit.Assert.assertFalse(com.audioeq.shizuku.ShizukuManager.isCommandOutputSuccessful("", allowEmptyOutput = false))
        org.junit.Assert.assertFalse(com.audioeq.shizuku.ShizukuManager.isCommandOutputSuccessful("   ", allowEmptyOutput = false))
        org.junit.Assert.assertFalse(com.audioeq.shizuku.ShizukuManager.isCommandOutputSuccessful("ERROR(1): denied", allowEmptyOutput = true))
        org.junit.Assert.assertFalse(com.audioeq.shizuku.ShizukuManager.isCommandOutputSuccessful("ERROR: failed", allowEmptyOutput = false))

        com.audioeq.shizuku.ShizukuManager.init(context)
        com.audioeq.shizuku.ShizukuManager.init(context)
        com.audioeq.shizuku.ShizukuManager.refresh()
    }

    @Test
    fun `verify notification session summary shows only current latest session and ignores inactive AudioFlinger tracks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(emptySet())
        com.audioeq.audio.MusicAppTracker.setExcludedMusicPackages(emptySet())
        com.audioeq.audio.MusicAppTracker.setEngineTrackingContext(
            context = context,
            isLegacyMode = false,
            hasPrivilegedControl = true
        )

        com.audioeq.audio.MusicAppTracker.onBroadcastSessionOpened(
            context = context,
            sessionId = 2448257,
            packageName = "app.morphe.android.youtube",
            contentType = android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
        )
        com.audioeq.audio.MusicAppTracker.onBroadcastSessionOpened(
            context = context,
            sessionId = 2448305,
            packageName = "app.morphe.android.youtube",
            contentType = android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
        )
        com.audioeq.audio.MusicAppTracker.onBroadcastSessionOpened(
            context = context,
            sessionId = 2448545,
            packageName = "app.morphe.android.youtube",
            contentType = android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
        )

        val broadcastSummary = com.audioeq.audio.MusicAppTracker.trackedSessionsSummary.value
        assertTrue("Summary must contain latest session #2448545: $broadcastSummary", broadcastSummary.contains("#2448545"))
        org.junit.Assert.assertFalse("Summary must not accumulate old session #2448257: $broadcastSummary", broadcastSummary.contains("2448257"))
        org.junit.Assert.assertFalse("Summary must not accumulate old session #2448305: $broadcastSummary", broadcastSummary.contains("2448305"))
        org.junit.Assert.assertFalse("Summary must not list multiple comma-separated sessions: $broadcastSummary", broadcastSummary.contains(","))

        val flingerDump = """
            Output thread 0x1234:
            2 Tracks of which 1 are active
              Name Active Client Type      Fmt Chn mask Session
              101     no    1111    3 00000001 00000003 2448305
              102    yes    1111    3 00000001 00000003 2448545
        """.trimIndent()
        com.audioeq.audio.MusicAppTracker.updateNotificationMediaPackages(
            context = context,
            packages = setOf("app.morphe.android.youtube"),
            playing = true
        )
        val parsed = com.audioeq.audio.MusicAppTracker.parseMusicSessionsFromDumpsys(
            context = context,
            audioDumpsys = null,
            flingerDumpsys = flingerDump
        )
        assertEquals(listOf(2448545), parsed.map { it.sessionId })

        com.audioeq.audio.MusicAppTracker.updatePrivilegedMusicSessions(context, parsed)
        val finalSummary = com.audioeq.audio.MusicAppTracker.trackedSessionsSummary.value
        assertTrue("Final summary must show latest active session #2448545: $finalSummary", finalSummary.contains("#2448545"))
        org.junit.Assert.assertFalse("Final summary must exclude inactive session #2448305: $finalSummary", finalSummary.contains("2448305"))

        com.audioeq.audio.MusicAppTracker.updateNotificationMediaPackages(context, emptySet(), playing = false)
        com.audioeq.audio.MusicAppTracker.updatePrivilegedMusicSessions(context, emptyList())
        com.audioeq.audio.MusicAppTracker.onBroadcastSessionClosed(context, 2448545)
    }

    @Test
    fun `verify equalizer notification is permanent with auto-restore deleteIntent until turned off`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notif = com.audioeq.audio.EqualizerNotificationHelper.buildNotification(
            context = context,
            presetName = "Flat",
            profileTitle = "Off",
            routeLabel = "Phone Speaker",
            engineShortTitle = "Root",
            musicSummary = "YouTube Morphe #2448545"
        )
        assertTrue(
            "Notification must set FLAG_ONGOING_EVENT",
            (notif.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
        )
        assertTrue(
            "Notification must set FLAG_NO_CLEAR",
            (notif.flags and android.app.Notification.FLAG_NO_CLEAR) != 0
        )
        assertNotNull("Notification must attach deleteIntent for Android 14+ swipe-away auto-restore", notif.deleteIntent)

        com.audioeq.audio.EqualizerNotificationHelper.updateOrCancelPersistentNotification(
            context = context,
            masterEnabled = true,
            presetName = "Flat",
            profileTitle = "Off",
            routeLabel = "Phone Speaker",
            engineShortTitle = "Root",
            musicSummary = "YouTube Morphe #2448545",
            forceRepost = true
        )
        assertTrue(com.audioeq.audio.EqualizerNotificationHelper.isPermanentNotificationActive(context))

        val receiver = com.audioeq.audio.EqualizerActionReceiver()
        receiver.onReceive(
            context,
            android.content.Intent(com.audioeq.audio.EqualizerNotificationHelper.ACTION_RESTORE_NOTIFICATION)
        )
        assertTrue(
            "Swiping away notification while EQ is ON must keep permanent notification active",
            com.audioeq.audio.EqualizerNotificationHelper.isPermanentNotificationActive(context)
        )

        receiver.onReceive(
            context,
            android.content.Intent(com.audioeq.audio.EqualizerNotificationHelper.ACTION_STOP_EQUALIZER)
        )
        org.junit.Assert.assertFalse(
            "Turning off Equalizer must deactivate permanent notification",
            com.audioeq.audio.EqualizerNotificationHelper.isPermanentNotificationActive(context)
        )
        org.junit.Assert.assertFalse(
            "restoreIfActive must not restore notification once Equalizer is turned off",
            com.audioeq.audio.EqualizerNotificationHelper.restoreIfActive(context)
        )
    }
}
