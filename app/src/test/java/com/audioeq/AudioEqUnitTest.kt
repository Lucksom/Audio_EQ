package com.audioeq

import com.audioeq.audio.AudioProfile
import com.audioeq.audio.AutoEq
import com.audioeq.audio.AutoEqManager
import com.audioeq.audio.Dsp
import com.audioeq.audio.DspV4Engine
import com.audioeq.audio.EnhancerConfig
import com.audioeq.audio.EnhancerDsp
import com.audioeq.audio.Enhancers
import com.audioeq.audio.IeqPreset
import com.audioeq.audio.MidSideProcessor
import com.audioeq.model.EqConstants
import com.audioeq.model.EqMode
import com.audioeq.model.HeadphoneProfile
import com.audioeq.model.OutputRoute
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioEqUnitTest {

    @Test
    fun verifyAllModeBandCountsAndMonotonicFrequencies() {
        for (mode in EqMode.entries) {
            val freqs = EqConstants.frequenciesForMode(mode)
            assertEquals("Band count mismatch for $mode", mode.bandCount, freqs.size)
            for (i in 0 until freqs.size - 1) {
                assertTrue(
                    "Frequencies must be strictly increasing in $mode",
                    freqs[i] < freqs[i + 1]
                )
            }
        }
    }

    @Test
    fun verifyResampleCurvePreservesFlatAndEndpoints() {
        val flat10 = List(10) { 0f }
        val resampled64 = EqConstants.resampleCurve(
            EqConstants.FREQ_10_BAND,
            flat10,
            EqConstants.FREQ_64_BAND
        )
        assertEquals(64, resampled64.size)
        assertTrue(resampled64.all { abs(it) < 0.001f })

        val ramp10 = List(10) { idx -> -6f + idx * 1.5f }
        val resampled32 = EqConstants.resampleCurve(
            EqConstants.FREQ_10_BAND,
            ramp10,
            EqConstants.FREQ_32_BAND
        )
        assertEquals(32, resampled32.size)
        assertTrue(resampled32.first() <= resampled32.last())
    }

    @Test
    fun verifyNineSpecificationPresetsAndExactGains() {
        val presets = EqConstants.BUILT_IN_PRESETS
        assertEquals(9, presets.size)
        val expectedIds = listOf(
            "flat", "rock", "jazz", "pop", "classical",
            "hip_hop", "blues", "electronic", "metal"
        )
        assertEquals(expectedIds, presets.map { it.id })

        val rock = presets.first { it.id == "rock" }
        assertEquals(
            listOf(4.0f, 1.0f, -2.0f, -0.25f, 0.0f, -2.0f, 0.0f, -2.0f, 0.5f, 4.0f),
            rock.gainsDb
        )

        val classical = presets.first { it.id == "classical" }
        assertEquals(
            listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 3.0f, 1.0f, 6.0f, 2.0f, 6.0f),
            classical.gainsDb
        )

        val metal = presets.first { it.id == "metal" }
        assertEquals(
            listOf(2.0f, 0.0f, 0.0f, -1.25f, -1.0f, -4.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            metal.gainsDb
        )
    }

    @Test
    fun verifyEndpointSpecificProfileAdaptationAcrossRoutes() {
        val dynSpk = EnhancerDsp.resolveEndpointSpec(AudioProfile.DYNAMIC, OutputRoute.SPEAKER)
        assertTrue(dynSpk.virtualBassAuto)
        assertEquals(240, dynSpk.peakQ8)
        assertEquals(17, dynSpk.volmaxQ4)

        val dynHp = EnhancerDsp.resolveEndpointSpec(AudioProfile.DYNAMIC, OutputRoute.HEADPHONES)
        assertFalse(dynHp.virtualBassAuto)
        assertEquals(256, dynHp.peakQ8)
        assertEquals(32, dynHp.volmaxQ4)

        val dynBt = EnhancerDsp.resolveEndpointSpec(AudioProfile.DYNAMIC, OutputRoute.BLUETOOTH)
        assertFalse(dynBt.virtualBassAuto)
        assertTrue(dynBt.ieqEnabled)
        assertEquals(IeqPreset.BALANCED, dynBt.ieqPreset)
        assertEquals(6, dynBt.ieqAmount)
        assertTrue(dynBt.deEnabled)
        assertEquals(4, dynBt.deAmount)
        assertEquals(256, dynBt.peakQ8)
        assertEquals(32, dynBt.volmaxQ4)

        val vidSpk = EnhancerDsp.resolveEndpointSpec(AudioProfile.VIDEO, OutputRoute.SPEAKER)
        assertTrue(vidSpk.virtualBassAuto)
        assertEquals(IeqPreset.DETAILED, vidSpk.ieqPreset)
        assertEquals(12, vidSpk.ieqAmount)
        assertEquals(5, vidSpk.deAmount)
        assertEquals(241, vidSpk.peakQ8)
        assertEquals(32, vidSpk.volmaxQ4)

        val vidHp = EnhancerDsp.resolveEndpointSpec(AudioProfile.VIDEO, OutputRoute.HEADPHONES)
        assertFalse(vidHp.virtualBassAuto)
        assertEquals(IeqPreset.DETAILED, vidHp.ieqPreset)
        assertEquals(13, vidHp.ieqAmount)
        assertEquals(4, vidHp.deAmount)
        assertEquals(256, vidHp.peakQ8)
        assertEquals(48, vidHp.volmaxQ4)
        assertEquals(3.0f, vidHp.volmaxMakeupDb, 0.001f)
        assertTrue(vidHp.virtualizerEnabled)

        val vidBt = EnhancerDsp.resolveEndpointSpec(AudioProfile.VIDEO, OutputRoute.BLUETOOTH)
        assertFalse("BT Video disables IEQ to avoid A2DP lip-sync latency", vidBt.ieqEnabled)
        assertTrue(vidBt.deEnabled)
        assertEquals(4, vidBt.deAmount)

        val musSpk = EnhancerDsp.resolveEndpointSpec(AudioProfile.MUSIC, OutputRoute.SPEAKER)
        assertTrue(musSpk.virtualBassAuto)
        assertEquals(16, musSpk.volmaxQ4)
        assertEquals(241, musSpk.peakQ8)

        val musBt = EnhancerDsp.resolveEndpointSpec(AudioProfile.MUSIC, OutputRoute.BLUETOOTH)
        assertTrue("BT Music enables Balanced IEQ (amt=6) to compensate for lossy BT compression", musBt.ieqEnabled)
        assertEquals(IeqPreset.BALANCED, musBt.ieqPreset)
        assertEquals(6, musBt.ieqAmount)
    }

    @Test
    fun verifyDeepBassZeroHzLowShelfVersusNormal47HzPeaking() {
        assertEquals(47, EnhancerDsp.IEQ_FREQS_20[0])
        assertEquals(0, EnhancerDsp.IEQ_FREQS_20_DEEP_BASS[0])
        for (i in 1..9) {
            assertEquals(EnhancerDsp.IEQ_FREQS_20[i - 1], EnhancerDsp.IEQ_FREQS_20_DEEP_BASS[i])
        }
        for (i in 10..19) {
            assertEquals(EnhancerDsp.IEQ_FREQS_20[i], EnhancerDsp.IEQ_FREQS_20_DEEP_BASS[i])
        }

        val normal47 = EnhancerDsp.computeNormal47HzPeaking31(gainDb = 4.5, sampleRate = 48000.0)
        val deepBass = EnhancerDsp.computeDeepBassCurve31(
            config = EnhancerConfig(deepBassEnabled = true, bassEnabled = true, bassBoostDb = 4.5f),
            sampleRate = 48000.0
        )
        assertTrue(
            "Deep Bass 0 Hz DC Low-Shelf must preserve higher 20 Hz sub-bass than Normal 47 Hz Peaking filter",
            deepBass[0] > normal47[0] + 1.5
        )
    }

    @Test
    fun verify10To20BandLogarithmicInterpolationAndQ4Registers() {
        val rock10 = EqConstants.BUILT_IN_PRESETS.first { it.id == "rock" }.gainsDb
            .map { it.toDouble() }
            .toDoubleArray()
        val interp20 = EnhancerDsp.interpolate10To20Logarithmic(rock10, deepBass = false)
        assertEquals(20, interp20.size)
        val q4 = EnhancerDsp.toQ4RegisterValues(interp20)
        assertEquals(20, q4.size)
        assertEquals(64, q4.last())
    }

    @Test
    fun verifyAutoEqAppliesAccuratelyAcrossEarphonesEarbudsAndHeadphones() {
        val categories = EqConstants.BUILT_IN_HEADPHONE_PROFILES.map { it.earphoneCategory }.toSet()
        assertTrue(categories.contains("Earphones"))
        assertTrue(categories.contains("Earbuds"))
        assertTrue(categories.contains("Headphones"))

        val hd650 = EqConstants.BUILT_IN_HEADPHONE_PROFILES.find { it.id == "hd650" }
        assertTrue("HD 650 profile must exist in built-ins", hd650 != null)
        val hd650Curve = Dsp.computeHeadphoneCorrection31(
            profile = hd650,
            strength = 0.8,
            fullRange = false,
            tiltBassDb = 0.0,
            tiltTrebleDb = 0.0
        )
        assertEquals(31, hd650Curve.size)
        assertTrue("HD 650 correction must apply non-zero gains", hd650Curve.any { abs(it) > 0.5 })

        val blessing3 = EqConstants.BUILT_IN_HEADPHONE_PROFILES.find { it.id == "blessing_3" }
        assertTrue("Moondrop Blessing 3 earphone profile must exist", blessing3 != null)
        val blessingCurve = Dsp.computeHeadphoneCorrection31(blessing3, strength = 1.0)
        assertEquals(31, blessingCurve.size)
        assertTrue("Earphone correction must apply non-zero gains", blessingCurve.any { abs(it) > 0.5 })

        val airpodsPro2 = EqConstants.BUILT_IN_HEADPHONE_PROFILES.find { it.id == "airpods_pro_2" }
        assertTrue("AirPods Pro 2 earbud profile must exist", airpodsPro2 != null)
        val airpodsCurve = Dsp.computeHeadphoneCorrection31(airpodsPro2, strength = 1.0)
        assertEquals(31, airpodsCurve.size)
        assertTrue("Earbud correction must apply non-zero gains", airpodsCurve.any { abs(it) > 0.5 })

        val geqProfile = HeadphoneProfile(
            id = "custom_geq",
            name = "Custom GraphicEQ",
            source = "AutoEq",
            rig = "HMS II.3",
            peqFilters = emptyList(),
            graphicPoints = listOf(
                20.0 to 3.0,
                100.0 to 2.5,
                1000.0 to 0.0,
                3000.0 to 4.0,
                10000.0 to 1.5
            ),
            correction31Db = List(31) { 0f }
        )
        val geqCurve = Dsp.computeHeadphoneCorrection31(geqProfile, strength = 1.0)
        assertEquals(31, geqCurve.size)
        assertTrue("GraphicEQ profile must produce non-zero correction", geqCurve.any { abs(it) > 0.5 })
    }

    @Test
    fun verify20BandIeqTargetVectors() {
        assertEquals(20, EnhancerDsp.IEQ_FREQS_20.size)
        assertArrayEquals(
            intArrayOf(-50, 5, 19, 25, 29, 44, 52, 57, 56, 56, 61, 69, 77, 78, 72, 74, 48, 22, -2, -59),
            IeqPreset.DETAILED.targetsQ4
        )
        assertArrayEquals(
            intArrayOf(-38, 17, 32, 41, 46, 55, 57, 62, 65, 68, 69, 69, 60, 49, 34, 24, -12, -55, -102, -162),
            IeqPreset.WARM.targetsQ4
        )
        assertArrayEquals(
            intArrayOf(-65, 0, 14, 29, 39, 52, 56, 61, 67, 70, 73, 74, 67, 61, 52, 49, 20, -14, -50, -108),
            IeqPreset.BALANCED.targetsQ4
        )
    }

    @Test
    fun verifyMidSideProcessorProcessesPcmCleanly() {
        val processor = MidSideProcessor(48000)
        processor.updateParameters(
            width = 1.35f,
            vocalBoost = 0.4f,
            sampleRate = 48000
        )

        val pcm = ShortArray(256) { idx ->
            if (idx % 2 == 0) 8000.toShort() else (-6000).toShort()
        }
        processor.processInterleavedPcm16(pcm, pcm.size)
        assertTrue("Processed PCM must contain non-zero samples", pcm.any { it.toInt() != 0 })
    }

    @Test
    fun verifyAutoEqRbjBiquadTaperAndParsers() {
        val peqText = """
            Preamp: -5.4 dB
            Filter 1: ON LSC Fc 105 Hz Gain 5.8 dB Q 0.70
            Filter 2: ON PK Fc 1850 Hz Gain 2.4 dB Q 1.41
            Filter 3: ON HSC Fc 9500 Hz Gain -2.2 dB Q 0.71
        """.trimIndent()
        val parsed = AutoEqManager.parseParametricEq(peqText)
        assertEquals(-5.4, parsed.preamp, 0.001)
        assertEquals(3, parsed.filters.size)
        assertEquals("LSC", parsed.filters[0].type)
        assertEquals("PK", parsed.filters[1].type)
        assertEquals("HSC", parsed.filters[2].type)

        val autoProfile = com.audioeq.audio.AutoEqParser.parseParametric(peqText)
        assertEquals(-5.4, autoProfile.preampDb, 0.001)
        assertEquals(3, autoProfile.filters.size)
        val dpBands = com.audioeq.audio.AutoEqMapper.toDynamicsBands(autoProfile)
        assertEquals(31, dpBands.size)
        assertTrue(com.audioeq.audio.AutoEqMapper.safePreampDb(autoProfile) <= -5.4)

        val fixedText = """
            Preamp: -4.2 dB
            Filter 1: ON PK Fc 31 Hz Gain 4.0 dB Q 1.41
            Filter 2: ON PK Fc 62 Hz Gain 3.0 dB Q 1.41
            Filter 3: ON PK Fc 125 Hz Gain 1.5 dB Q 1.41
            Filter 4: ON PK Fc 250 Hz Gain -1.0 dB Q 1.41
            Filter 5: ON PK Fc 500 Hz Gain 0.0 dB Q 1.41
            Filter 6: ON PK Fc 1000 Hz Gain 1.0 dB Q 1.41
            Filter 7: ON PK Fc 2000 Hz Gain 2.0 dB Q 1.41
            Filter 8: ON PK Fc 4000 Hz Gain 2.5 dB Q 1.41
            Filter 9: ON PK Fc 8000 Hz Gain -1.5 dB Q 1.41
            Filter 10: ON PK Fc 16000 Hz Gain 1.0 dB Q 1.41
        """.trimIndent()
        val fixedProf = com.audioeq.audio.AutoEqParser.parseParametric(fixedText)
        val tenBand = com.audioeq.audio.AutoEqMapper.fixedBandToTenBand(fixedProf)
        assertEquals(10, tenBand.gainsDb.size)
        assertEquals(4.0, tenBand.gainsDb[0], 0.001)
        assertFalse(tenBand.clipped)
    }

    @Test
    fun verifyDspV4EngineAndEnhancersSection15And17() {
        val gains = FloatArray(10) { idx -> if (idx < 3) 6.0f else 1.5f }
        val qs = FloatArray(10) { 1.41f }
        val autoPreamp = DspV4Engine.computeAutoPreamp(
            bandFreqs = DspV4Engine.STANDARD_10_BAND_HZ,
            bandGains = gains,
            bandQs = qs,
            bassGainDb = 4.0f,
            extraCeilingDb = -1.0f
        )
        assertTrue("Auto preamp should be negative to prevent clipping", autoPreamp < -5.0f)

        val (dlgL, dlgR) = Enhancers.computeDialoguePreEqOffsets(
            bandFreqs = DspV4Engine.STANDARD_10_BAND_HZ,
            enabled = true,
            amount = 0.8f,
            virtualizerStrength = 500
        )
        assertEquals(10, dlgL.size)
        assertEquals(10, dlgR.size)
        assertTrue("1-4 kHz speech band should be boosted", dlgL[6] > 1.5f)
    }

    @Test
    fun verifyBassEnhancerStaysWithinSixDecibelsAndNeverSuppressesMidsOrTreble() {
        val maxBassCfg = EnhancerConfig(
            bassEnabled = true,
            bassBoostDb = 6.0f,
            virtualBassEnabled = true,
            virtualBassStrength = 0.6f
        )
        val bass31 = EnhancerDsp.computeBassEnhancer31(maxBassCfg)
        assertEquals(31, bass31.size)
        assertTrue("Bass Enhancer 31-band curve must never exceed 6.0 dB", bass31.all { it in 0.0..6.0 })
        for (i in 17 until 31) {
            assertTrue("Bass enhancer must not attenuate mids/treble", bass31[i] >= 0.0)
        }
    }

    @Test
    fun verifyGitHubUpdateCheckerNormalizationVersioningAndParsing() {
        assertEquals("user/AudioEQ", com.audioeq.data.GitHubUpdateChecker.normalizeRepoSlug("user/AudioEQ"))
        assertEquals(
            "user/AudioEQ",
            com.audioeq.data.GitHubUpdateChecker.normalizeRepoSlug("https://github.com/user/AudioEQ/releases")
        )
        assertEquals(
            "user/AudioEQ",
            com.audioeq.data.GitHubUpdateChecker.normalizeRepoSlug("git@github.com:user/AudioEQ.git")
        )
        assertEquals(null, com.audioeq.data.GitHubUpdateChecker.normalizeRepoSlug("invalid"))

        assertTrue(com.audioeq.data.GitHubUpdateChecker.isRemoteVersionNewer("v1.0.15.1", "1.0.2"))
        assertTrue(com.audioeq.data.GitHubUpdateChecker.isRemoteVersionNewer("v1.0.15.2-20261112", "1.0.15.1"))
        assertFalse(com.audioeq.data.GitHubUpdateChecker.isRemoteVersionNewer("v1.0.15.1", "1.0.15.1"))
        assertFalse(com.audioeq.data.GitHubUpdateChecker.isRemoteVersionNewer("v1.0.2", "1.0.15.1"))
    }

    @Test
    fun verifyOperatingModeBadgesAvoidAwaitingGrantWording() {
        assertFalse(
            com.audioeq.model.OperatingMode.SUSPENDED_PRIVILEGED.badge.contains("Grant", ignoreCase = true)
        )
    }

    @Test
    fun verifyLegacyGlobalMixModeIdRoundTrip() {
        for (mode in com.audioeq.model.LegacyGlobalMixMode.entries) {
            assertEquals(mode, com.audioeq.model.LegacyGlobalMixMode.fromId(mode.id))
        }
        assertEquals(
            com.audioeq.model.LegacyGlobalMixMode.AUTO,
            com.audioeq.model.LegacyGlobalMixMode.fromId("unknown_mode")
        )
        assertEquals(
            com.audioeq.model.LegacyGlobalMixMode.AUTO,
            com.audioeq.model.LegacyGlobalMixMode.fromId(null)
        )
    }

    @Test
    fun verifyLegacyMixEnginePreferenceForOffloadVsAosp() {
        assertTrue(
            com.audioeq.audio.MasterEqEngine.shouldPreferNativeEqForLegacyMix(
                legacyMixMode = com.audioeq.model.LegacyGlobalMixMode.AUTO,
                vendorOffloadDetected = true,
                sdkInt = 34
            )
        )
        assertFalse(
            com.audioeq.audio.MasterEqEngine.shouldPreferNativeEqForLegacyMix(
                legacyMixMode = com.audioeq.model.LegacyGlobalMixMode.AUTO,
                vendorOffloadDetected = false,
                sdkInt = 34
            )
        )
        assertTrue(
            com.audioeq.audio.MasterEqEngine.shouldPreferNativeEqForLegacyMix(
                legacyMixMode = com.audioeq.model.LegacyGlobalMixMode.OFFLOAD_EQ,
                vendorOffloadDetected = false,
                sdkInt = 34
            )
        )
        assertFalse(
            com.audioeq.audio.MasterEqEngine.shouldPreferNativeEqForLegacyMix(
                legacyMixMode = com.audioeq.model.LegacyGlobalMixMode.DYNAMICS_PROCESSING,
                vendorOffloadDetected = true,
                sdkInt = 34
            )
        )
        assertTrue(
            com.audioeq.audio.MasterEqEngine.shouldPreferNativeEqForLegacyMix(
                legacyMixMode = com.audioeq.model.LegacyGlobalMixMode.DYNAMICS_PROCESSING,
                vendorOffloadDetected = false,
                sdkInt = 26
            )
        )
    }
}
