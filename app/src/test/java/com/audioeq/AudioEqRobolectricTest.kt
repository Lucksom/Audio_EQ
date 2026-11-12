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
    fun `verify MusicAppTracker accepts music and media apps even when popular list is preloaded`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(
            setOf("com.spotify.music", "com.google.android.apps.youtube.music")
        )
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
        // Explicitly excluded popular package should be rejected; cleared exclusion re-enables it
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(
            setOf("com.google.android.apps.youtube.music")
        )
        com.audioeq.audio.MusicAppTracker.setExcludedMusicPackages(setOf("com.spotify.music"))
        org.junit.Assert.assertFalse(
            com.audioeq.audio.MusicAppTracker.isMusicAppPackage(
                context,
                "com.spotify.music",
                android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
            )
        )
        com.audioeq.audio.MusicAppTracker.setExcludedMusicPackages(emptySet())
        org.junit.Assert.assertFalse(
            com.audioeq.audio.MusicAppTracker.isMusicAppPackage(
                context,
                "com.whatsapp",
                android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
            )
        )
    }
}
