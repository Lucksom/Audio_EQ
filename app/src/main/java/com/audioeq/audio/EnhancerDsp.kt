package com.audioeq.audio

import com.audioeq.model.EqConstants
import com.audioeq.model.OutputRoute
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class IeqPreset(
    val id: String,
    val label: String,
    val subtitle: String,
    val targetsQ4: IntArray
) {
    OFF(
        id = "ieq_off",
        label = "Off",
        subtitle = "Bypass spectral target steering",
        targetsQ4 = IntArray(20) { 0 }
    ),
    DETAILED(
        id = "ieq_detailed",
        label = "Detailed",
        subtitle = "Treble clarity & vocal presence (+4.88 dB peak)",
        targetsQ4 = intArrayOf(
            -50, 5, 19, 25, 29, 44, 52, 57, 56, 56,
            61, 69, 77, 78, 72, 74, 48, 22, -2, -59
        )
    ),
    WARM(
        id = "ieq_warm",
        label = "Warm",
        subtitle = "Rich low-mids & smooth upper-harmonic roll-off",
        targetsQ4 = intArrayOf(
            -38, 17, 32, 41, 46, 55, 57, 62, 65, 68,
            69, 69, 60, 49, 34, 24, -12, -55, -102, -162
        )
    ),
    BALANCED(
        id = "ieq_balanced",
        label = "Balanced",
        subtitle = "Equal-loudness contour across bass, mids & air",
        targetsQ4 = intArrayOf(
            -65, 0, 14, 29, 39, 52, 56, 61, 67, 70,
            73, 74, 67, 61, 52, 49, 20, -14, -50, -108
        )
    );

    companion object {
        fun fromId(id: String): IeqPreset = entries.firstOrNull { it.id == id } ?: OFF
    }
}

enum class AudioProfile(
    val id: String,
    val label: String,
    val subtitle: String,
    val badge: String
) {
    OFF(
        id = "profile_off",
        label = "Off",
        subtitle = "Manual module control without profile presets",
        badge = "Manual"
    ),
    DYNAMIC(
        id = "profile_dynamic",
        label = "Dynamic",
        subtitle = "Adaptive real-time Movie & Music steering across output routes",
        badge = "Adaptive"
    ),
    VIDEO(
        id = "profile_video",
        label = "Video",
        subtitle = "Binaural 3D surround, Detailed IEQ & speech clarity boost",
        badge = "Cinema 3D"
    ),
    MUSIC(
        id = "profile_music",
        label = "Music",
        subtitle = "Direct acoustic fidelity on Speaker/Headphones; Balanced IEQ on Bluetooth",
        badge = "Hi-Fi"
    ),
    VOICE(
        id = "profile_voice",
        label = "Voice",
        subtitle = "Vocal formant emphasis & Detailed IEQ clarity",
        badge = "Vocal"
    );

    companion object {
        fun fromId(id: String): AudioProfile = entries.firstOrNull { it.id == id } ?: OFF
    }
}

data class EndpointProfileSpec(
    val profile: AudioProfile,
    val route: OutputRoute,
    val endpointName: String,
    val ieqEnabled: Boolean,
    val ieqPreset: IeqPreset,
    val ieqAmount: Int,
    val deEnabled: Boolean,
    val deAmount: Int,
    val virtualBassAuto: Boolean,
    val peakQ8: Int,
    val peakCeilingDb: Float,
    val volmaxQ4: Int,
    val volmaxMakeupDb: Float,
    val virtualizerEnabled: Boolean,
    val virtualizerModeLabel: String,
    val virtualizerWidth: Float,
    val deepBassActive: Boolean
) {
    val mu: Float get() = (ieqAmount.coerceIn(0, 16) / 16.0f)
    val deBoostDb: Float get() = if (deEnabled) (deAmount.coerceIn(0, 16) / 16.0f) * 10.0f else 0.0f
}

data class DeepBassShiftBand(
    val bandIndex: Int,
    val normalHz: Int,
    val deepBassHz: Int,
    val acousticEffect: String
)

data class EnhancerConfig(
    val profile: AudioProfile = AudioProfile.OFF,
    val bassEnabled: Boolean = false,
    val bassBoostDb: Float = 0.0f,
    val virtualBassEnabled: Boolean = false,
    val virtualBassStrength: Float = 0.5f,
    val deepBassEnabled: Boolean = false,
    val ieqPreset: IeqPreset = IeqPreset.OFF,
    val ieqAmount: Int = 10,
    val dialogueEnhancerEnabled: Boolean = false,
    val dialogueAmount: Int = 8,
    val surroundEnabled: Boolean = false,
    val surroundWidth: Float = 0.45f
) {
    fun isAnyActive(): Boolean {
        return profile != AudioProfile.OFF ||
            bassEnabled && bassBoostDb > 0.05f ||
            virtualBassEnabled && virtualBassStrength > 0.02f ||
            deepBassEnabled ||
            ieqPreset != IeqPreset.OFF && ieqAmount > 0 ||
            dialogueEnhancerEnabled && dialogueAmount > 0 ||
            surroundEnabled && surroundWidth > 0.02f
    }
}

object EnhancerDsp {

    val IEQ_FREQS_20: IntArray = intArrayOf(
        47, 141, 234, 328, 469, 656, 844, 1031, 1313, 1688,
        2250, 3000, 3750, 4688, 5813, 7125, 9000, 11250, 13875, 19688
    )

    val IEQ_FREQS_20_DEEP_BASS: IntArray = intArrayOf(
        0, 47, 141, 234, 328, 469, 656, 844, 1031, 1313,
        2250, 3000, 3750, 4688, 5813, 7125, 9000, 11250, 13875, 19688
    )

    val DEEP_BASS_SHIFT_BANDS: List<DeepBassShiftBand> = listOf(
        DeepBassShiftBand(0, 47, 0, "DC low-shelf filter with zero sub-bass roll-off"),
        DeepBassShiftBand(1, 141, 47, "Reclaims deep sub-bass punch"),
        DeepBassShiftBand(2, 234, 141, "Shifts upper-bass warmth downward"),
        DeepBassShiftBand(3, 328, 234, "Concentrates low-mid body"),
        DeepBassShiftBand(4, 469, 328, "Tightens lower-harmonic fundamental"),
        DeepBassShiftBand(5, 656, 469, "Increases lower-band filter density"),
        DeepBassShiftBand(6, 844, 656, "Smooths transition between bass and vocal fundamentals"),
        DeepBassShiftBand(7, 1031, 844, "Refines lower midrange articulation"),
        DeepBassShiftBand(8, 1313, 1031, "Anchors reference midrange pivot"),
        DeepBassShiftBand(9, 1688, 1313, "Bridges lower-mids to fixed upper-mid band")
    )

    fun resolveEndpointSpec(
        profile: AudioProfile,
        route: OutputRoute,
        deepBassOverride: Boolean = false
    ): EndpointProfileSpec {
        val isSpeaker = (route == OutputRoute.SPEAKER)
        val isBluetooth = (route == OutputRoute.BLUETOOTH)
        val endpointName = when (route) {
            OutputRoute.SPEAKER -> "Internal Stereo Speakers"
            OutputRoute.HEADPHONES -> "Wired Headphones / Line-Out"
            OutputRoute.USB_DAC -> "USB Hi-Res DAC / Line-Out"
            OutputRoute.BLUETOOTH -> "Bluetooth Audio (A2DP)"
        }
        val deepBassActive = deepBassOverride

        return when (profile) {
            AudioProfile.DYNAMIC -> {
                when {
                    isSpeaker -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = false,
                        ieqPreset = IeqPreset.BALANCED,
                        ieqAmount = 6,
                        deEnabled = false,
                        deAmount = 4,
                        virtualBassAuto = true,
                        peakQ8 = 240,
                        peakCeilingDb = -1.0f,
                        volmaxQ4 = 17,
                        volmaxMakeupDb = 0.0f,
                        virtualizerEnabled = true,
                        virtualizerModeLabel = "Adaptive Speaker Stage",
                        virtualizerWidth = 0.40f,
                        deepBassActive = deepBassActive
                    )
                    isBluetooth -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = true,
                        ieqPreset = IeqPreset.BALANCED,
                        ieqAmount = 6,
                        deEnabled = true,
                        deAmount = 4,
                        virtualBassAuto = false,
                        peakQ8 = 256,
                        peakCeilingDb = 0.0f,
                        volmaxQ4 = 32,
                        volmaxMakeupDb = 0.0f,
                        virtualizerEnabled = false,
                        virtualizerModeLabel = "Bypassed (A2DP Phase Coherence)",
                        virtualizerWidth = 0.0f,
                        deepBassActive = deepBassActive
                    )
                    else -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = false,
                        ieqPreset = IeqPreset.BALANCED,
                        ieqAmount = 6,
                        deEnabled = false,
                        deAmount = 4,
                        virtualBassAuto = false,
                        peakQ8 = 256,
                        peakCeilingDb = 0.0f,
                        volmaxQ4 = 32,
                        volmaxMakeupDb = 0.0f,
                        virtualizerEnabled = true,
                        virtualizerModeLabel = "Adaptive Binaural Stage (1.0·Movie + 0.35·Music)",
                        virtualizerWidth = 0.50f,
                        deepBassActive = deepBassActive
                    )
                }
            }

            AudioProfile.VIDEO -> {
                when {
                    isSpeaker -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = true,
                        ieqPreset = IeqPreset.DETAILED,
                        ieqAmount = 12,
                        deEnabled = true,
                        deAmount = 5,
                        virtualBassAuto = true,
                        peakQ8 = 241,
                        peakCeilingDb = -1.0f,
                        volmaxQ4 = 32,
                        volmaxMakeupDb = 0.5f,
                        virtualizerEnabled = true,
                        virtualizerModeLabel = "Cinema Wide Crosstalk Canceller",
                        virtualizerWidth = 0.65f,
                        deepBassActive = deepBassActive
                    )
                    isBluetooth -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = false,
                        ieqPreset = IeqPreset.OFF,
                        ieqAmount = 0,
                        deEnabled = true,
                        deAmount = 4,
                        virtualBassAuto = false,
                        peakQ8 = 256,
                        peakCeilingDb = 0.0f,
                        volmaxQ4 = 32,
                        volmaxMakeupDb = 0.0f,
                        virtualizerEnabled = false,
                        virtualizerModeLabel = "Zero-Latency A2DP Cinema Direct",
                        virtualizerWidth = 0.0f,
                        deepBassActive = deepBassActive
                    )
                    else -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = true,
                        ieqPreset = IeqPreset.DETAILED,
                        ieqAmount = 13,
                        deEnabled = true,
                        deAmount = 4,
                        virtualBassAuto = false,
                        peakQ8 = 256,
                        peakCeilingDb = 0.0f,
                        volmaxQ4 = 48,
                        volmaxMakeupDb = 3.0f,
                        virtualizerEnabled = true,
                        virtualizerModeLabel = "Mode 2 Binaural HRTF (±35°, τ=0.302 ms / 14 samples)",
                        virtualizerWidth = 0.72f,
                        deepBassActive = deepBassActive
                    )
                }
            }

            AudioProfile.MUSIC -> {
                when {
                    isSpeaker -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = false,
                        ieqPreset = IeqPreset.OFF,
                        ieqAmount = 4,
                        deEnabled = false,
                        deAmount = 0,
                        virtualBassAuto = true,
                        peakQ8 = 241,
                        peakCeilingDb = -1.0f,
                        volmaxQ4 = 16,
                        volmaxMakeupDb = 0.0f,
                        virtualizerEnabled = false,
                        virtualizerModeLabel = "Bypassed (Pure Stereo Reference)",
                        virtualizerWidth = 0.0f,
                        deepBassActive = deepBassActive
                    )
                    isBluetooth -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = true,
                        ieqPreset = IeqPreset.BALANCED,
                        ieqAmount = 6,
                        deEnabled = false,
                        deAmount = 0,
                        virtualBassAuto = false,
                        peakQ8 = 256,
                        peakCeilingDb = 0.0f,
                        volmaxQ4 = 32,
                        volmaxMakeupDb = 0.0f,
                        virtualizerEnabled = false,
                        virtualizerModeLabel = "Bypassed (Pure Stereo Reference)",
                        virtualizerWidth = 0.0f,
                        deepBassActive = deepBassActive
                    )
                    else -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = false,
                        ieqPreset = IeqPreset.OFF,
                        ieqAmount = 0,
                        deEnabled = false,
                        deAmount = 0,
                        virtualizerEnabled = false,
                        virtualBassAuto = false,
                        peakQ8 = 256,
                        peakCeilingDb = 0.0f,
                        volmaxQ4 = 32,
                        volmaxMakeupDb = 0.0f,
                        virtualizerModeLabel = "Direct GEQ Acoustic Path",
                        virtualizerWidth = 0.0f,
                        deepBassActive = deepBassActive
                    )
                }
            }

            AudioProfile.VOICE -> {
                when {
                    isSpeaker -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = true,
                        ieqPreset = IeqPreset.DETAILED,
                        ieqAmount = 13,
                        deEnabled = true,
                        deAmount = 9,
                        virtualBassAuto = true,
                        peakQ8 = 241,
                        peakCeilingDb = -1.0f,
                        volmaxQ4 = 48,
                        volmaxMakeupDb = 3.0f,
                        virtualizerEnabled = false,
                        virtualizerModeLabel = "Bypassed (Center-Channel Vocal Focus)",
                        virtualizerWidth = 0.0f,
                        deepBassActive = deepBassActive
                    )
                    isBluetooth -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = false,
                        ieqPreset = IeqPreset.OFF,
                        ieqAmount = 0,
                        deEnabled = false,
                        deAmount = 0,
                        virtualBassAuto = false,
                        peakQ8 = 256,
                        peakCeilingDb = 0.0f,
                        volmaxQ4 = 32,
                        volmaxMakeupDb = 0.0f,
                        virtualizerEnabled = false,
                        virtualizerModeLabel = "Bypassed (Direct BT Speech)",
                        virtualizerWidth = 0.0f,
                        deepBassActive = deepBassActive
                    )
                    else -> EndpointProfileSpec(
                        profile = profile,
                        route = route,
                        endpointName = endpointName,
                        ieqEnabled = true,
                        ieqPreset = IeqPreset.DETAILED,
                        ieqAmount = 9,
                        deEnabled = true,
                        deAmount = 7,
                        virtualBassAuto = false,
                        peakQ8 = 256,
                        peakCeilingDb = 0.0f,
                        volmaxQ4 = 32,
                        volmaxMakeupDb = 0.0f,
                        virtualizerEnabled = false,
                        virtualizerModeLabel = "Bypassed (Zero Phase Smearing)",
                        virtualizerWidth = 0.0f,
                        deepBassActive = deepBassActive
                    )
                }
            }

            AudioProfile.OFF -> EndpointProfileSpec(
                profile = AudioProfile.OFF,
                route = route,
                endpointName = endpointName,
                ieqEnabled = false,
                ieqPreset = IeqPreset.OFF,
                ieqAmount = 0,
                deEnabled = false,
                deAmount = 0,
                virtualBassAuto = isSpeaker,
                peakQ8 = if (isSpeaker) 240 else 256,
                peakCeilingDb = if (isSpeaker) -1.0f else 0.0f,
                volmaxQ4 = 32,
                volmaxMakeupDb = 0.0f,
                virtualizerEnabled = false,
                virtualizerModeLabel = "Manual Module Control",
                virtualizerWidth = 0.0f,
                deepBassActive = deepBassOverride
            )
        }
    }

    fun applyProfileForRoute(
        current: EnhancerConfig,
        profile: AudioProfile,
        route: OutputRoute
    ): EnhancerConfig {
        if (profile == AudioProfile.OFF) {
            return current.copy(profile = AudioProfile.OFF)
        }
        val spec = resolveEndpointSpec(profile, route, current.deepBassEnabled)
        return current.copy(
            profile = profile,
            deepBassEnabled = current.deepBassEnabled,
            bassEnabled = bassEnabledKeepOrDefault(current, profile),
            bassBoostDb = when {
                profile == AudioProfile.VIDEO && current.bassBoostDb < 2.0f -> 2.5f
                profile == AudioProfile.DYNAMIC && current.bassBoostDb < 1.5f -> 2.0f
                else -> current.bassBoostDb
            },
            virtualBassEnabled = spec.virtualBassAuto,
            virtualBassStrength = if (spec.virtualBassAuto) max(current.virtualBassStrength, 0.55f) else current.virtualBassStrength,
            ieqPreset = if (spec.ieqEnabled) spec.ieqPreset else IeqPreset.OFF,
            ieqAmount = if (spec.ieqEnabled) spec.ieqAmount else current.ieqAmount,
            dialogueEnhancerEnabled = spec.deEnabled,
            dialogueAmount = if (spec.deEnabled) spec.deAmount else current.dialogueAmount,
            surroundEnabled = spec.virtualizerEnabled,
            surroundWidth = if (spec.virtualizerEnabled) spec.virtualizerWidth else current.surroundWidth
        )
    }

    private fun bassEnabledKeepOrDefault(current: EnhancerConfig, profile: AudioProfile): Boolean {
        return when (profile) {
            AudioProfile.VIDEO, AudioProfile.DYNAMIC -> true
            AudioProfile.VOICE -> false
            else -> current.bassEnabled
        }
    }

    fun interpolate10To20Logarithmic(gains10: DoubleArray, deepBass: Boolean = false): DoubleArray {
        val freqs10 = EqConstants.BAND_FREQUENCIES_10
        val freqs20 = if (deepBass) IEQ_FREQS_20_DEEP_BASS else IEQ_FREQS_20
        return DoubleArray(20) { k ->
            val fk = freqs20[k]
            when {
                fk <= freqs10.first() -> gains10.firstOrNull() ?: 0.0
                fk >= freqs10.last() -> gains10.lastOrNull() ?: 0.0
                else -> {
                    var idx = 0
                    for (i in 0 until freqs10.size - 1) {
                        if (fk >= freqs10[i] && fk <= freqs10[i + 1]) {
                            idx = i
                            break
                        }
                    }
                    val fLow = freqs10[idx]
                    val fHigh = freqs10[idx + 1]
                    val wk = ln(fk / fLow) / ln(fHigh / fLow)
                    (1.0 - wk) * gains10[idx] + wk * gains10[idx + 1]
                }
            }
        }
    }

    fun toQ4RegisterValues(gains20Db: DoubleArray): IntArray {
        return IntArray(gains20Db.size) { k ->
            (gains20Db[k] * 16.0).roundToInt()
        }
    }

    fun computeDeepBassCurve31(
        config: EnhancerConfig,
        sampleRate: Double = 48000.0
    ): DoubleArray {
        val result = DoubleArray(31)
        val active = config.deepBassEnabled
        if (!active) return result

        val shelfGainDb = if (config.bassEnabled && config.bassBoostDb > 0.5f) {
            config.bassBoostDb.toDouble().coerceIn(2.5, 6.0)
        } else {
            4.5
        }

        val fc = 35.0
        val q = 0.70710678
        val a = 10.0.pow(shelfGainDb / 40.0)
        val omegaC = 2.0 * PI * fc / sampleRate
        val cosW = cos(omegaC)
        val sinW = sin(omegaC)
        val alpha = sinW / (2.0 * q)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val b0 = a * ((a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha)
        val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW)
        val b2 = a * ((a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha)
        val a0 = (a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha
        val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW)
        val a2 = (a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha

        val nb0 = b0 / a0
        val nb1 = b1 / a0
        val nb2 = b2 / a0
        val na1 = a1 / a0
        val na2 = a2 / a0

        val freqs = EqConstants.BAND_FREQUENCIES_31
        for (i in 0 until 31) {
            val f = freqs[i]
            val w = 2.0 * PI * f / sampleRate
            val cos1 = cos(w)
            val sin1 = sin(w)
            val cos2 = cos(2.0 * w)
            val sin2 = sin(2.0 * w)

            val numRe = nb0 + nb1 * cos1 + nb2 * cos2
            val numIm = -(nb1 * sin1 + nb2 * sin2)
            val denRe = 1.0 + na1 * cos1 + na2 * cos2
            val denIm = -(na1 * sin1 + na2 * sin2)

            val magSq = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm).coerceAtLeast(1e-12)
            val dcShelfDb = 10.0 * log10(magSq.coerceAtLeast(1e-12))

            val logShiftReinforcement = 1.35 * exp(-0.5 * (ln(f / 47.0) / 0.55).pow(2.0))
            result[i] = (dcShelfDb + logShiftReinforcement).coerceIn(0.0, 6.5)
        }
        return result
    }

    fun computeNormal47HzPeaking31(
        gainDb: Double = 4.5,
        sampleRate: Double = 48000.0
    ): DoubleArray {
        val f0 = 47.0
        val q = 1.414
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)

        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosW0
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha / a

        val nb0 = b0 / a0
        val nb1 = b1 / a0
        val nb2 = b2 / a0
        val na1 = a1 / a0
        val na2 = a2 / a0

        val freqs = EqConstants.BAND_FREQUENCIES_31
        return DoubleArray(31) { i ->
            val w = 2.0 * PI * freqs[i] / sampleRate
            val cos1 = cos(w)
            val sin1 = sin(w)
            val cos2 = cos(2.0 * w)
            val sin2 = sin(2.0 * w)

            val numRe = nb0 + nb1 * cos1 + nb2 * cos2
            val numIm = -(nb1 * sin1 + nb2 * sin2)
            val denRe = 1.0 + na1 * cos1 + na2 * cos2
            val denIm = -(na1 * sin1 + na2 * sin2)

            val magSq = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm).coerceAtLeast(1e-12)
            10.0 * log10(magSq.coerceAtLeast(1e-12))
        }
    }

    fun computeBassEnhancer31(config: EnhancerConfig): DoubleArray {
        val result = DoubleArray(31)
        if (!config.bassEnabled || config.bassBoostDb <= 0.05f) return result

        val boost = config.bassBoostDb.toDouble().coerceIn(0.0, 6.0)
        val freqs = EqConstants.BAND_FREQUENCIES_31

        for (i in 0 until 31) {
            val f = freqs[i]
            val ratio = f / 95.0
            val shelfGain = (boost * 0.62) / sqrt(1.0 + ratio.pow(4.0))

            val logDist62 = ln(f / 62.0)
            val punchBell = (boost * 0.38) * exp(-0.5 * (logDist62 / 0.42).pow(2.0))

            val logDist140 = ln(f / 140.0)
            val warmthBell = (boost * 0.10) * exp(-0.5 * (logDist140 / 0.32).pow(2.0))

            result[i] = (shelfGain + punchBell + warmthBell).coerceIn(0.0, 6.0)
        }
        return result
    }

    fun computeVirtualBass31(config: EnhancerConfig): DoubleArray {
        val result = DoubleArray(31)
        if (!config.virtualBassEnabled || config.virtualBassStrength <= 0.02f) return result

        val strength = config.virtualBassStrength.toDouble().coerceIn(0.0, 1.0)
        val maxHarmonicDb = strength * 6.5
        val freqs = EqConstants.BAND_FREQUENCIES_31

        for (i in 0 until 31) {
            val f = freqs[i]
            val subAnchor = (maxHarmonicDb * 0.35) / sqrt(1.0 + (f / 55.0).pow(4.0))
            val h2 = (maxHarmonicDb * 0.85) * exp(-0.5 * (ln(f / 110.0) / 0.28).pow(2.0))
            val h3 = (maxHarmonicDb * 0.55) * exp(-0.5 * (ln(f / 165.0) / 0.24).pow(2.0))
            val h4 = (maxHarmonicDb * 0.28) * exp(-0.5 * (ln(f / 220.0) / 0.20).pow(2.0))
            val mudDip = -(maxHarmonicDb * 0.18) * exp(-0.5 * (ln(f / 340.0) / 0.22).pow(2.0))

            result[i] = subAnchor + h2 + h3 + h4 + mudDip
        }
        return result
    }

    fun computeIeq31(config: EnhancerConfig): DoubleArray {
        val result = DoubleArray(31)
        if (config.ieqPreset == IeqPreset.OFF || config.ieqAmount <= 0) return result

        val mu = (config.ieqAmount.coerceIn(0, 16) / 16.0)
        val targetsQ4 = config.ieqPreset.targetsQ4
        val refMidDb = ((targetsQ4[7] + targetsQ4[8] + targetsQ4[9] + targetsQ4[10]) / 4.0) / 16.0
        val targetsDb = DoubleArray(20) { b ->
            ((targetsQ4[b] / 16.0) - refMidDb) * mu
        }

        val ieqFreqs = if (config.deepBassEnabled) {
            IEQ_FREQS_20_DEEP_BASS
        } else {
            IEQ_FREQS_20
        }

        val freqs31 = EqConstants.BAND_FREQUENCIES_31
        for (i in 0 until 31) {
            val f = freqs31[i]
            result[i] = interpolateLog20(f, targetsDb, ieqFreqs)
        }
        return result
    }

    fun computeDialogueEnhancer31(config: EnhancerConfig): DoubleArray {
        val result = DoubleArray(31)
        if (!config.dialogueEnhancerEnabled || config.dialogueAmount <= 0) return result

        val amountNorm = config.dialogueAmount.coerceIn(0, 16) / 16.0
        val maxBoostDb = amountNorm * 10.0
        val freqs = EqConstants.BAND_FREQUENCIES_31

        for (i in 0 until 31) {
            val f = freqs[i]
            val logDistFormant = ln(f / 2100.0)
            val formantBoost = maxBoostDb * exp(-0.5 * (logDistFormant / 0.58).pow(2.0))

            val logDistChest = ln(f / 550.0)
            val chestPresence = (maxBoostDb * 0.22) * exp(-0.5 * (logDistChest / 0.35).pow(2.0))

            val logDistMud = ln(f / 220.0)
            val mudUnmask = -(maxBoostDb * 0.25) * exp(-0.5 * (logDistMud / 0.38).pow(2.0))

            result[i] = formantBoost + chestPresence + mudUnmask
        }
        return result
    }

    fun computeSurroundContour31(config: EnhancerConfig, isSpeaker: Boolean = false): DoubleArray {
        val result = DoubleArray(31)
        if (!config.surroundEnabled || config.surroundWidth <= 0.02f) return result

        val w = config.surroundWidth.toDouble().coerceIn(0.0, 1.0)
        val freqs = EqConstants.BAND_FREQUENCIES_31

        for (i in 0 until 31) {
            val f = freqs[i]
            if (f <= 234.0) {
                result[i] = 0.0
                continue
            }
            val sidePresence = (w * 2.2) * exp(-0.5 * (ln(f / 2800.0) / 0.42).pow(2.0))
            val pinnaNotch = -(w * 1.4) * exp(-0.5 * (ln(f / 7200.0) / 0.22).pow(2.0))
            val airShelf = (w * 2.6) / sqrt(1.0 + (9500.0 / f).pow(4.0))
            val speakerDispersion = if (isSpeaker) {
                (w * 1.1) * exp(-0.5 * (ln(f / 1600.0) / 0.35).pow(2.0))
            } else 0.0

            result[i] = sidePresence + pinnaNotch + airShelf + speakerDispersion
        }
        return result
    }

    fun computeCombinedEnhancer31(
        config: EnhancerConfig,
        isSpeaker: Boolean = false,
        sampleRate: Double = 48000.0
    ): DoubleArray {
        val deepBass = computeDeepBassCurve31(config, sampleRate)
        val bass = computeBassEnhancer31(config)
        val virtBass = computeVirtualBass31(config)
        val ieq = computeIeq31(config)
        val de = computeDialogueEnhancer31(config)
        val surr = computeSurroundContour31(config, isSpeaker)

        return DoubleArray(31) { i ->
            (deepBass[i] + bass[i] + virtBass[i] + ieq[i] + de[i] + surr[i]).coerceIn(-12.0, 14.0)
        }
    }

    private fun interpolateLog20(freqHz: Double, gains20: DoubleArray, freqs20: IntArray = IEQ_FREQS_20): Double {
        val firstFreq = freqs20.first().coerceAtLeast(20).toDouble()
        if (freqHz <= firstFreq) return gains20.first()
        if (freqHz >= freqs20.last()) return gains20.last()

        val logF = ln(freqHz.coerceAtLeast(20.0))
        for (b in 0 until freqs20.size - 1) {
            val f0 = freqs20[b].coerceAtLeast(20).toDouble()
            val f1 = freqs20[b + 1].coerceAtLeast(21).toDouble()
            if (freqHz >= f0 && freqHz <= f1) {
                val l0 = ln(f0)
                val l1 = ln(f1)
                val t = if (abs(l1 - l0) < 1e-6) 0.0 else (logF - l0) / (l1 - l0)
                return gains20[b] + t * (gains20[b + 1] - gains20[b])
            }
        }
        return 0.0
    }

    val UUID_BASS_BOOST_MZ: java.util.UUID =
        java.util.UUID.fromString("0634f220-ddd4-11db-a0fc-0002a5d5c51b")
    val UUID_HARDWARE_EFFECT: java.util.UUID =
        java.util.UUID.fromString("46d279d0-9e77-11e4-89d3-0002a5d5c51b")

    fun buildHardwareRegisterPayload(
        gains20Q4: IntArray,
        ieqPresetIndex: Int,
        deepBassEnabled: Boolean = false,
        peakQ8: Int = 256,
        volmaxQ4: Int = 32
    ): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(96).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.putInt(ieqPresetIndex)
        buf.putInt(if (deepBassEnabled) 1 else 0)
        buf.putInt(peakQ8)
        buf.putInt(volmaxQ4)
        for (i in 0 until 20) {
            buf.putInt(gains20Q4.getOrElse(i) { 0 })
        }
        return buf.array()
    }
}

typealias EnhancerMath = EnhancerDsp
