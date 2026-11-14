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
    val surroundWidth: Float = 0.45f,
    val subwooferEnabled: Boolean = false,
    val subwooferIntensity: Float = 0.85f,
    val subwooferSubOctaveMix: Float = 0.35f,
    val subwooferAutoLoudness: Boolean = true
) {
    fun isAnyActive(): Boolean {
        return profile != AudioProfile.OFF ||
            bassEnabled && bassBoostDb > 0.05f ||
            virtualBassEnabled && virtualBassStrength > 0.02f ||
            deepBassEnabled ||
            ieqPreset != IeqPreset.OFF && ieqAmount > 0 ||
            dialogueEnhancerEnabled && dialogueAmount > 0 ||
            surroundEnabled && surroundWidth > 0.02f ||
            subwooferEnabled && subwooferIntensity > 0.02f
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
        DeepBassShiftBand(0, 47, 0, "Stage A: 38 Hz resonant DC low-shelf (Q=0.90, +4.5 dB offset)"),
        DeepBassShiftBand(1, 141, 47, "Stage B: 47 Hz sub-bass fundamental peak (Q=1.15, +3.5 dB)"),
        DeepBassShiftBand(2, 234, 141, "Shifts upper-bass warmth downward"),
        DeepBassShiftBand(3, 328, 234, "210 Hz anti-mud pocket (-2.5 dB, Q=1.40) for vocal clarity"),
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
            bassEnabled = if (current.subwooferEnabled) false else bassEnabledKeepOrDefault(current, profile),
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

    private fun evalRbjLowShelfDb(
        freqHz: Double,
        fcHz: Double,
        gainDb: Double,
        q: Double,
        sampleRate: Double
    ): Double {
        if (abs(gainDb) < 1e-4) return 0.0
        val a = 10.0.pow(gainDb / 40.0)
        val omega0 = 2.0 * PI * fcHz / sampleRate
        val cosW0 = cos(omega0)
        val sinW0 = sin(omega0)
        val alpha = sinW0 / (2.0 * q)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
        val b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
        val a2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha

        return evalNormalizedBiquadDb(freqHz, sampleRate, b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun evalRbjPeakingDb(
        freqHz: Double,
        fcHz: Double,
        gainDb: Double,
        q: Double,
        sampleRate: Double
    ): Double {
        if (abs(gainDb) < 1e-4) return 0.0
        val a = 10.0.pow(gainDb / 40.0)
        val omega0 = 2.0 * PI * fcHz / sampleRate
        val cosW0 = cos(omega0)
        val sinW0 = sin(omega0)
        val alpha = sinW0 / (2.0 * q)

        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosW0
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha / a

        return evalNormalizedBiquadDb(freqHz, sampleRate, b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun evalNormalizedBiquadDb(
        freqHz: Double,
        sampleRate: Double,
        nb0: Double,
        nb1: Double,
        nb2: Double,
        na1: Double,
        na2: Double
    ): Double {
        val w = 2.0 * PI * freqHz / sampleRate
        val cos1 = cos(w)
        val sin1 = sin(w)
        val cos2 = cos(2.0 * w)
        val sin2 = sin(2.0 * w)

        val numRe = nb0 + nb1 * cos1 + nb2 * cos2
        val numIm = -(nb1 * sin1 + nb2 * sin2)
        val denRe = 1.0 + na1 * cos1 + na2 * cos2
        val denIm = -(na1 * sin1 + na2 * sin2)

        val magSq = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm).coerceAtLeast(1e-12)
        return 10.0 * log10(magSq.coerceAtLeast(1e-12))
    }

    fun computeDeepBassCurve31(
        config: EnhancerConfig,
        sampleRate: Double = 48000.0,
        userBand0Db: Double = 0.0,
        userBand1Db: Double = 0.0
    ): DoubleArray {
        val result = DoubleArray(31)
        val active = config.deepBassEnabled
        if (!active) return result

        val driveScale = if (config.bassEnabled && config.bassBoostDb > 0.5f) {
            (config.bassBoostDb.toDouble() / 4.5).coerceIn(0.65, 1.35)
        } else {
            1.0
        }

        val g0ShelfDb = (userBand0Db.coerceIn(-12.0, 12.0) + 4.5 * driveScale).coerceIn(2.5, 10.5)
        val g1PeakDb = (userBand1Db.coerceIn(-12.0, 12.0) + 3.5 * driveScale).coerceIn(2.0, 9.0)
        val gMudCutDb = (-2.5 * driveScale).coerceIn(-3.5, -1.5)

        val freqs = EqConstants.BAND_FREQUENCIES_31
        for (i in 0 until 31) {
            val f = freqs[i]
            val stageADb = evalRbjLowShelfDb(
                freqHz = f,
                fcHz = 38.0,
                gainDb = g0ShelfDb,
                q = 0.90,
                sampleRate = sampleRate
            )
            val stageBDb = evalRbjPeakingDb(
                freqHz = f,
                fcHz = 47.0,
                gainDb = g1PeakDb,
                q = 1.15,
                sampleRate = sampleRate
            )
            val stageMudCutDb = evalRbjPeakingDb(
                freqHz = f,
                fcHz = 210.0,
                gainDb = gMudCutDb,
                q = 1.40,
                sampleRate = sampleRate
            )
            result[i] = (stageADb + stageBDb + stageMudCutDb).coerceIn(-4.5, 12.0)
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
            val ratio = f / 85.0
            val shelfGain = (boost * 0.88) / sqrt(1.0 + ratio.pow(4.0))

            val logDist55 = ln(f / 55.0)
            val punchBell = (boost * 0.42) * exp(-0.5 * (logDist55 / 0.38).pow(2.0))

            val logDist105 = ln(f / 105.0)
            val upperPunchBell = (boost * 0.24) * exp(-0.5 * (logDist105 / 0.32).pow(2.0))

            result[i] = (shelfGain + punchBell + upperPunchBell).coerceIn(0.0, boost)
        }
        return result
    }

    fun computeSubwooferCurve31(
        config: EnhancerConfig,
        volumeRatio: Float = 0.7f
    ): DoubleArray {
        val result = DoubleArray(31)
        if (!config.subwooferEnabled || config.subwooferIntensity <= 0.02f) return result

        val wInt = config.subwooferIntensity.coerceIn(0.0f, 1.5f).toDouble()
        val loudnessComp = if (config.subwooferAutoLoudness) {
            1.0 + 0.35 * (1.0 - volumeRatio.coerceIn(0.1f, 1.0f).toDouble())
        } else {
            1.0
        }
        val subOctScale = 0.85 + 0.45 * config.subwooferSubOctaveMix.coerceIn(0.0f, 0.6f).toDouble()
        val eff = (wInt * loudnessComp).coerceAtMost(1.65)

        result[0] = (-6.5 * wInt).coerceIn(-12.0, 0.0)
        result[1] = (3.5 * eff * subOctScale).coerceIn(0.0, 10.0)
        result[2] = (7.0 * eff * subOctScale).coerceIn(0.0, 13.5)
        result[3] = (8.0 * eff * subOctScale).coerceIn(0.0, 14.5)
        result[4] = (6.5 * eff).coerceIn(0.0, 12.0)
        result[5] = (4.0 * eff).coerceIn(0.0, 8.5)
        result[6] = (1.5 * eff).coerceIn(0.0, 4.0)
        result[9] = (-3.0 * wInt).coerceIn(-6.5, 0.0)
        result[10] = (-4.5 * wInt).coerceIn(-8.0, 0.0)
        result[11] = (-2.5 * wInt).coerceIn(-5.5, 0.0)

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

    fun computeDynamicProfileSteering31(config: EnhancerConfig): DoubleArray {
        val result = DoubleArray(31)
        if (config.profile != AudioProfile.DYNAMIC) return result

        val freqs31 = EqConstants.BAND_FREQUENCIES_31
        val ieqFreqs = if (config.deepBassEnabled) IEQ_FREQS_20_DEEP_BASS else IEQ_FREQS_20

        val balQ4 = IeqPreset.BALANCED.targetsQ4
        val refMidDb = ((balQ4[7] + balQ4[8] + balQ4[9] + balQ4[10]) / 4.0) / 16.0
        val musicWeight = if (config.ieqPreset == IeqPreset.OFF) 0.35 else 0.0
        val speechWeight = if (!config.dialogueEnhancerEnabled) (4.0 / 16.0) else 0.0

        val balTargetsDb = DoubleArray(20) { b ->
            ((balQ4[b] / 16.0) - refMidDb) * musicWeight
        }

        for (i in 0 until 31) {
            val f = freqs31[i]
            val musicEq = interpolateLog20(f, balTargetsDb, ieqFreqs)
            val speechFormant = (speechWeight * 4.5) * exp(-0.5 * (ln(f / 2100.0) / 0.58).pow(2.0))
            val speechMudCut = -(speechWeight * 1.1) * exp(-0.5 * (ln(f / 220.0) / 0.38).pow(2.0))
            result[i] = musicEq + speechFormant + speechMudCut
        }
        return result
    }

    fun computeDeepBassShiftDelta31(
        userGains10: DoubleArray,
        deepBassEnabled: Boolean
    ): DoubleArray {
        val result = DoubleArray(31)
        if (!deepBassEnabled || userGains10.size < 10) return result
        val normal20 = interpolate10To20Logarithmic(userGains10, deepBass = false)
        val shifted20 = interpolate10To20Logarithmic(userGains10, deepBass = true)
        val freqs31 = EqConstants.BAND_FREQUENCIES_31
        for (i in 0 until 31) {
            val f = freqs31[i]
            val gNormal = interpolateLog20(f, normal20, IEQ_FREQS_20)
            val gShifted = interpolateLog20(f, shifted20, IEQ_FREQS_20_DEEP_BASS)
            result[i] = (gShifted - gNormal).coerceIn(-6.0, 6.0)
        }
        return result
    }

    fun computeSurroundContour31(
        config: EnhancerConfig,
        isSpeaker: Boolean = false,
        sampleRate: Double = 48000.0
    ): DoubleArray {
        val result = DoubleArray(31)
        if (!config.surroundEnabled || config.surroundWidth <= 0.02f) return result

        val w = config.surroundWidth.toDouble().coerceIn(0.0, 1.0)
        val freqs = EqConstants.BAND_FREQUENCIES_31
        val rHead = 0.0875
        val cSound = 343.0
        val thetaRad = 35.0 * PI / 180.0
        val tauSec = (rHead / cSound) * (thetaRad + sin(thetaRad))
        val beta = 1.0 - exp(-2.0 * PI * 850.0 / sampleRate)

        for (i in 0 until 31) {
            val f = freqs[i]
            if (f <= 234.0) {
                result[i] = 0.0
                continue
            }
            val omega = 2.0 * PI * f / sampleRate
            val oneMinusBeta = 1.0 - beta
            val denShadow = sqrt((1.0 - oneMinusBeta * cos(omega)).pow(2.0) + (oneMinusBeta * sin(omega)).pow(2.0)).coerceAtLeast(1e-6)
            val hShadowMag = (beta / denShadow).coerceIn(0.0, 1.0)
            val itdPhaseMod = 0.18 * w * (1.0 - hShadowMag) * cos(2.0 * PI * f * tauSec * 0.25)

            val sidePresence = (w * 2.2) * exp(-0.5 * (ln(f / 2800.0) / 0.42).pow(2.0))
            val pinnaNotch = -(w * 1.4) * exp(-0.5 * (ln(f / 7200.0) / 0.22).pow(2.0))
            val airShelf = (w * 2.6) / sqrt(1.0 + (9500.0 / f).pow(4.0))
            val speakerDispersion = if (isSpeaker) {
                (w * 1.1) * exp(-0.5 * (ln(f / 1600.0) / 0.35).pow(2.0))
            } else 0.0

            result[i] = sidePresence + pinnaNotch + airShelf + speakerDispersion + itdPhaseMod
        }
        return result
    }

    fun computeCombinedEnhancer31(
        config: EnhancerConfig,
        isSpeaker: Boolean = false,
        sampleRate: Double = 48000.0,
        userBand0Db: Double = 0.0,
        userBand1Db: Double = 0.0,
        volumeRatio: Float = 0.7f
    ): DoubleArray {
        val subActive = config.subwooferEnabled && config.subwooferIntensity > 0.02f
        val subwoofer = if (subActive) computeSubwooferCurve31(config, volumeRatio) else DoubleArray(31)
        val deepBass = computeDeepBassCurve31(config, sampleRate, userBand0Db, userBand1Db)
        val bass = if (subActive) DoubleArray(31) else computeBassEnhancer31(config)
        val virtBass = computeVirtualBass31(config)
        val ieq = computeIeq31(config)
        val dynSteer = computeDynamicProfileSteering31(config)
        val de = computeDialogueEnhancer31(config)
        val surr = computeSurroundContour31(config, isSpeaker, sampleRate)

        return DoubleArray(31) { i ->
            (subwoofer[i] + deepBass[i] + bass[i] + virtBass[i] + ieq[i] + dynSteer[i] + de[i] + surr[i])
                .coerceIn(-15.0, 15.0)
        }
    }

    fun computeTrueBiquadAutoPreampDb(
        userGains10: DoubleArray,
        extraCurve31: DoubleArray? = null,
        sampleRate: Double = 48000.0,
        safetyMarginDb: Double = 0.5
    ): Double {
        val freqs10 = EqConstants.BAND_FREQUENCIES_10
        val freqs31 = EqConstants.BAND_FREQUENCIES_31
        var maxGainDb = 0.0
        for (p in 0 until 64) {
            val probeFreq = 20.0 * (1000.0).pow(p.toDouble() / 63.0)
            var totalDbAtProbe = 0.0
            for (b in 0 until minOf(10, userGains10.size)) {
                val g = userGains10[b]
                if (abs(g) < 0.05) continue
                totalDbAtProbe += evalRbjPeakingDb(
                    freqHz = probeFreq,
                    fcHz = freqs10[b],
                    gainDb = g,
                    q = 1.414,
                    sampleRate = sampleRate
                )
            }
            if (extraCurve31 != null && extraCurve31.size == 31) {
                val nearestIdx = freqs31.indices.minByOrNull { abs(ln(freqs31[it]) - ln(probeFreq)) } ?: 0
                totalDbAtProbe += extraCurve31[nearestIdx]
            }
            if (totalDbAtProbe > maxGainDb) {
                maxGainDb = totalDbAtProbe
            }
        }
        return if (maxGainDb > 0.0) -(maxGainDb + safetyMarginDb) else 0.0
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
