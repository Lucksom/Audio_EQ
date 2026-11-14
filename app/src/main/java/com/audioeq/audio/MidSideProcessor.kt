package com.audioeq.audio

import com.audioeq.model.FilterType
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tanh

object SoftKneeLimiter {
    private const val KNEE_START = 0.8414f
    private const val CEILING = 0.9885f

    fun processSample(x: Float): Float {
        val absX = abs(x)
        if (absX <= KNEE_START) return x
        val sign = if (x >= 0f) 1f else -1f
        val excess = (absX - KNEE_START) / (CEILING - KNEE_START).coerceAtLeast(1e-5f)
        val compressed = KNEE_START + (CEILING - KNEE_START) * tanh(excess.toDouble()).toFloat()
        return sign * compressed
    }
}

class MidSideProcessor(sampleRate: Int = 48000) {

    constructor(sampleRate: Double) : this(sampleRate.toInt())

    private var currentSampleRate: Int = sampleRate

    var enabled: Boolean = true
        private set

    var effective10BandDb: List<Float> = emptyList()
        private set

    var preampDb: Float = 0f
        private set

    var virtualizerPercent: Int = 0
        private set

    var channelBalance: Float = 0f
        private set

    var limiterThresholdDb: Float = 0f
        private set

    var enhancerConfig: EnhancerConfig = EnhancerConfig()
        private set

    var stereoWidth: Float = 1.0f
        private set

    var vocalPresenceBoost: Float = 0.0f
        private set

    private val midVocalFilter = BiquadFilter()
    private val sideHighShelfFilter = BiquadFilter()

    private val monoLowPassFilter = BiquadFilter()
    private val monoHighPassFilter = BiquadFilter()

    private val crossfeedFilterL = BiquadFilter()
    private val crossfeedFilterR = BiquadFilter()
    private var delaySampleCount: Int = 14
    private var delayLineL = FloatArray(32)
    private var delayLineR = FloatArray(32)
    private var delayWriteIdx: Int = 0

    private val deepStageALowShelf = BiquadFilter()
    private val deepStageBSubPeak = BiquadFilter()
    private val deepStageBAntiMud = BiquadFilter()
    private val deepStageCSubExtractor = BiquadFilter()
    private var subEnvelope: Double = 0.0
    private var deepBassBoostLinear: Double = 0.0

    private val subInfraGuard1 = BiquadFilter()
    private val subInfraGuard2 = BiquadFilter()
    private val subPortHelmholtz = BiquadFilter()
    private val subChestPunch = BiquadFilter()
    private val subTrunkCarve1 = BiquadFilter()
    private val subTrunkCarve2 = BiquadFilter()
    private val subSynthBpHpf = BiquadFilter()
    private val subSynthBpLpf = BiquadFilter()
    private val subSynthSmooth1 = BiquadFilter()
    private val subSynthSmooth2 = BiquadFilter()
    private val subSatDcBlocker = BiquadFilter()
    private var subPrevBpSample: Double = 0.0
    private var subFlipOctave: Double = 1.0
    private var subFastEnv: Double = 0.0
    private var subSlowEnv: Double = 0.0
    private var subAttackFastCoeff: Double = 0.0
    private var subAttackSlowCoeff: Double = 0.0
    private var subReleaseCoeff: Double = 0.0
    private var subTanhBias: Double = tanh(0.14)
    private var subCurrentVolumeRatio: Float = 0.7f

    init {
        configureFilters(sampleRate)
    }

    fun updateParameters(
        enabled: Boolean,
        effective10BandDb: List<Float> = emptyList(),
        preampDb: Float = 0f,
        virtualizerPercent: Int = 0,
        channelBalance: Float = 0f,
        limiterThresholdDb: Float = 0f,
        enhancerConfig: EnhancerConfig = EnhancerConfig()
    ) {
        this.enabled = enabled
        this.effective10BandDb = effective10BandDb
        this.preampDb = preampDb
        this.virtualizerPercent = virtualizerPercent
        this.channelBalance = channelBalance
        this.limiterThresholdDb = limiterThresholdDb
        this.enhancerConfig = enhancerConfig

        val width = 1.0f + (virtualizerPercent / 100f)
        val vocalBoost = if (enhancerConfig.dialogueEnhancerEnabled) {
            (enhancerConfig.dialogueAmount / 16f).coerceIn(0f, 1f)
        } else {
            0f
        }
        updateParameters(
            width = width,
            vocalBoost = vocalBoost,
            sampleRate = currentSampleRate
        )
    }

    fun updateParameters(
        width: Float,
        vocalBoost: Float,
        sampleRate: Int = 48000,
        deepBassEnabled: Boolean = enhancerConfig.deepBassEnabled,
        deepBassBoostDb: Float = enhancerConfig.bassBoostDb,
        userBand0Db: Float = effective10BandDb.getOrElse(0) { 0f },
        userBand1Db: Float = effective10BandDb.getOrElse(1) { 0f },
        subwooferEnabled: Boolean = enhancerConfig.subwooferEnabled,
        subwooferIntensity: Float = enhancerConfig.subwooferIntensity,
        subwooferSubOctaveMix: Float = enhancerConfig.subwooferSubOctaveMix,
        subwooferAutoLoudness: Boolean = enhancerConfig.subwooferAutoLoudness,
        volumeRatio: Float = subCurrentVolumeRatio
    ) {
        currentSampleRate = sampleRate.coerceIn(44100, 192000)
        stereoWidth = width.coerceIn(0.5f, 2.0f)
        vocalPresenceBoost = vocalBoost.coerceIn(0f, 1f)
        subCurrentVolumeRatio = volumeRatio.coerceIn(0.1f, 1.0f)
        enhancerConfig = enhancerConfig.copy(
            deepBassEnabled = deepBassEnabled,
            bassEnabled = deepBassBoostDb > 0.05f || enhancerConfig.bassEnabled,
            bassBoostDb = if (deepBassBoostDb > 0.05f) deepBassBoostDb else enhancerConfig.bassBoostDb,
            subwooferEnabled = subwooferEnabled,
            subwooferIntensity = subwooferIntensity.coerceIn(0f, 1.5f),
            subwooferSubOctaveMix = subwooferSubOctaveMix.coerceIn(0f, 0.6f),
            subwooferAutoLoudness = subwooferAutoLoudness
        )
        if (effective10BandDb.size < 2 ||
            abs(effective10BandDb[0] - userBand0Db) > 0.01f ||
            abs(effective10BandDb[1] - userBand1Db) > 0.01f
        ) {
            val updated = MutableList(10) { idx -> effective10BandDb.getOrElse(idx) { 0f } }
            updated[0] = userBand0Db
            updated[1] = userBand1Db
            effective10BandDb = updated
        }
        configureFilters(currentSampleRate)
    }

    private fun configureFilters(sampleRate: Int) {
        val fs = sampleRate.toDouble()
        val vocalGainDb = vocalPresenceBoost * 4.2
        midVocalFilter.configurePeaking(
            frequencyHz = 2150.0,
            sampleRate = fs,
            gainDb = vocalGainDb,
            q = 1.15
        )
        val airDb = (stereoWidth - 1.0f).coerceAtLeast(0f) * 2.5
        sideHighShelfFilter.configurePeaking(
            frequencyHz = 6500.0,
            sampleRate = fs,
            gainDb = airDb,
            q = 0.85
        )

        monoLowPassFilter.configureLowPass(180.0, fs, q = 0.5)
        monoHighPassFilter.configure(FilterType.HIGH_PASS, 180.0, fs, 0.0, q = 0.5)

        crossfeedFilterL.configureLowPass(700.0, fs, q = 0.7071)
        crossfeedFilterR.configureLowPass(700.0, fs, q = 0.7071)
        delaySampleCount = ((0.00030 * fs).toInt()).coerceIn(4, 30)
        if (delayLineL.size < delaySampleCount + 2) {
            delayLineL = FloatArray(delaySampleCount + 4)
            delayLineR = FloatArray(delaySampleCount + 4)
            delayWriteIdx = 0
        }

        val userBand0 = effective10BandDb.getOrElse(0) { 0f }.toDouble()
        val userBand1 = effective10BandDb.getOrElse(1) { 0f }.toDouble()
        val driveScale = if (enhancerConfig.bassEnabled && enhancerConfig.bassBoostDb > 0.5f) {
            (enhancerConfig.bassBoostDb.toDouble() / 5.0).coerceIn(0.75, 1.10)
        } else {
            1.0
        }
        val subCoactiveScale = if (enhancerConfig.subwooferEnabled && enhancerConfig.subwooferIntensity > 0.02f) 0.55 else 1.0
        val g0ShelfDb = ((userBand0 * 0.5 + 3.8 * driveScale) * subCoactiveScale).coerceIn(1.5, 6.8)
        val g1PeakDb = ((userBand1 * 0.5 + 2.8 * driveScale) * subCoactiveScale).coerceIn(1.2, 5.5)
        val gMudCutDb = (-2.5 * driveScale * subCoactiveScale).coerceIn(-3.5, -1.0)

        deepStageALowShelf.configure(FilterType.LOW_SHELF, 38.0, fs, g0ShelfDb, q = 0.85)
        deepStageBSubPeak.configurePeaking(47.0, fs, g1PeakDb, q = 1.15)
        deepStageBAntiMud.configurePeaking(210.0, fs, gMudCutDb, q = 1.40)
        deepStageCSubExtractor.configureLowPass(120.0, fs, q = 0.7071)

        val bassEnhancerBoostDb = if (enhancerConfig.deepBassEnabled) {
            if (!enhancerConfig.subwooferEnabled && enhancerConfig.bassEnabled && enhancerConfig.bassBoostDb > 0.5f) {
                enhancerConfig.bassBoostDb.toDouble().coerceIn(2.0, 5.0)
            } else if (enhancerConfig.subwooferEnabled) {
                1.8
            } else {
                3.2
            }
        } else {
            0.0
        }
        deepBassBoostLinear = (10.0.pow(bassEnhancerBoostDb / 20.0) - 1.0) * (if (enhancerConfig.subwooferEnabled) 0.16 else 0.28)

        val wInt = enhancerConfig.subwooferIntensity.coerceIn(0f, 1.5f).toDouble()
        val cleanSubDrive = tanh(wInt * 0.90) / tanh(0.90)
        val loudnessComp = if (enhancerConfig.subwooferAutoLoudness) {
            1.0 + 0.25 * (1.0 - subCurrentVolumeRatio.coerceIn(0.1f, 1.0f).toDouble())
        } else {
            1.0
        }
        val effSub = (cleanSubDrive * loudnessComp).coerceIn(0.0, 1.25)

        subInfraGuard1.configure(FilterType.HIGH_PASS, 22.0, fs, 0.0, q = 0.7071)
        subInfraGuard2.configure(FilterType.HIGH_PASS, 22.0, fs, 0.0, q = 0.7071)
        subPortHelmholtz.configurePeaking(38.0, fs, 4.2 * effSub, q = 1.45)
        subChestPunch.configurePeaking(48.0, fs, 2.8 * effSub, q = 1.25)
        subTrunkCarve1.configurePeaking(185.0, fs, -3.5 * cleanSubDrive, q = 1.50)
        subTrunkCarve2.configurePeaking(240.0, fs, -2.2 * cleanSubDrive, q = 1.60)
        subSynthBpHpf.configure(FilterType.HIGH_PASS, 52.0, fs, 0.0, q = 0.7071)
        subSynthBpLpf.configureLowPass(95.0, fs, q = 0.7071)
        subSynthSmooth1.configureLowPass(50.0, fs, q = 0.7071)
        subSynthSmooth2.configureLowPass(50.0, fs, q = 0.7071)
        subSatDcBlocker.configure(FilterType.HIGH_PASS, 15.0, fs, 0.0, q = 0.7071)

        subAttackFastCoeff = kotlin.math.exp(-1.0 / (0.0025 * fs))
        subAttackSlowCoeff = kotlin.math.exp(-1.0 / (0.0550 * fs))
        subReleaseCoeff = kotlin.math.exp(-1.0 / (0.1800 * fs))
    }

    fun processInterleavedStereo(buffer: FloatArray, frameCount: Int) {
        if (!enabled) return
        val width = stereoWidth.coerceIn(0.5f, 2.0f)
        val subActive = enhancerConfig.subwooferEnabled && enhancerConfig.subwooferIntensity > 0.02f
        val deepActive = enhancerConfig.deepBassEnabled
        val subIntensity = enhancerConfig.subwooferIntensity.coerceIn(0f, 1.5f).toDouble()
        val cleanSubDrive = tanh(subIntensity * 0.90) / tanh(0.90)
        val subOctaveMix = enhancerConfig.subwooferSubOctaveMix.coerceIn(0f, 0.6f).toDouble()
        val subLoudnessComp = if (enhancerConfig.subwooferAutoLoudness) {
            1.0 + 0.25 * (1.0 - subCurrentVolumeRatio.coerceIn(0.1f, 1.0f).toDouble())
        } else {
            1.0
        }
        val effSub = (cleanSubDrive * subLoudnessComp).coerceIn(0.0, 1.25)
        val preHeadroomScale = when {
            subActive && deepActive -> (1.0 / (1.0 + 0.34 * effSub)).coerceIn(0.66, 0.85)
            subActive -> (1.0 / (1.0 + 0.26 * effSub)).coerceIn(0.72, 0.92)
            deepActive && enhancerConfig.bassEnabled -> 0.78
            deepActive -> 0.84
            else -> 1.0
        }
        val crossfeedMix = if (width > 1.01f) ((width - 1.0f) * 0.18f).coerceIn(0f, 0.20f) else 0f
        val dLen = delayLineL.size

        var idx = 0
        for (frame in 0 until frameCount) {
            var left = buffer[idx].toDouble() * preHeadroomScale
            var right = buffer[idx + 1].toDouble() * preHeadroomScale

            if (deepActive) {
                left = deepStageALowShelf.processLeft(left)
                right = deepStageALowShelf.processRight(right)

                left = deepStageBSubPeak.processLeft(left)
                right = deepStageBSubPeak.processRight(right)
                left = deepStageBAntiMud.processLeft(left)
                right = deepStageBAntiMud.processRight(right)

                val subL = deepStageCSubExtractor.processLeft(left)
                val subR = deepStageCSubExtractor.processRight(right)
                val subMono = 0.5 * (subL + subR)
                subEnvelope = 0.995 * subEnvelope + 0.005 * abs(subMono)
                val dynGain = deepBassBoostLinear / (1.0 + 2.4 * subEnvelope)
                val excitedSub = tanh(1.25 * subMono) * dynGain
                left += excitedSub
                right += excitedSub
            }

            if (subActive) {
                left = subInfraGuard1.processLeft(subInfraGuard2.processLeft(left))
                right = subInfraGuard1.processRight(subInfraGuard2.processRight(right))

                left = subTrunkCarve1.processLeft(subTrunkCarve2.processLeft(left))
                right = subTrunkCarve1.processRight(subTrunkCarve2.processRight(right))

                val monoIn = 0.5 * (left + right)
                val bp = subSynthBpLpf.processLeft(subSynthBpHpf.processLeft(monoIn))
                val absBp = abs(bp)

                subFastEnv = if (absBp > subFastEnv) {
                    subAttackFastCoeff * subFastEnv + (1.0 - subAttackFastCoeff) * absBp
                } else {
                    subReleaseCoeff * subFastEnv + (1.0 - subReleaseCoeff) * absBp
                }
                subSlowEnv = if (absBp > subSlowEnv) {
                    subAttackSlowCoeff * subSlowEnv + (1.0 - subAttackSlowCoeff) * absBp
                } else {
                    subReleaseCoeff * subSlowEnv + (1.0 - subReleaseCoeff) * absBp
                }

                val transientPunch = 1.0 + 1.25 * kotlin.math.max(0.0, subFastEnv - subSlowEnv)

                if (subPrevBpSample <= 0.0 && bp > 0.0) {
                    subFlipOctave = -subFlipOctave
                }
                subPrevBpSample = bp

                val rawSubWave = subFlipOctave * subFastEnv * transientPunch
                val pureSubOctave = subSynthSmooth2.processLeft(subSynthSmooth1.processLeft(rawSubWave))

                left = subPortHelmholtz.processLeft(subChestPunch.processLeft(left))
                right = subPortHelmholtz.processRight(subChestPunch.processRight(right))

                val rawHarmonic = tanh(1.15 * bp + 0.14) - subTanhBias
                val warmHarmonic = subSatDcBlocker.processLeft(rawHarmonic) * (0.16 * effSub)
                val subInjection = (pureSubOctave * subOctaveMix * 0.65 * effSub) + warmHarmonic

                left += subInjection
                right += subInjection
            }

            val lowL = monoLowPassFilter.processLeft(left)
            val lowR = monoLowPassFilter.processRight(right)
            val monoSub = 0.5f * (lowL + lowR).toFloat()

            val highL = monoHighPassFilter.processLeft(left).toFloat()
            val highR = monoHighPassFilter.processRight(right).toFloat()

            var mid = 0.5f * (highL + highR)
            var side = 0.5f * (highL - highR)

            if (vocalPresenceBoost > 0.01f) {
                mid = midVocalFilter.processSample(mid)
            }
            if (width > 1.01f) {
                side = sideHighShelfFilter.processSample(side)
            }

            side *= width

            var outLeft = monoSub + (mid + side)
            var outRight = monoSub + (mid - side)

            if (crossfeedMix > 0.005f && dLen > delaySampleCount) {
                val readIdx = (delayWriteIdx - delaySampleCount + dLen) % dLen
                val delayedL = delayLineL[readIdx]
                val delayedR = delayLineR[readIdx]
                delayLineL[delayWriteIdx] = crossfeedFilterL.processLeft(outLeft.toDouble()).toFloat()
                delayLineR[delayWriteIdx] = crossfeedFilterR.processRight(outRight.toDouble()).toFloat()
                delayWriteIdx = (delayWriteIdx + 1) % dLen

                outLeft += delayedR * crossfeedMix
                outRight += delayedL * crossfeedMix
            }

            buffer[idx] = SoftKneeLimiter.processSample(outLeft)
            buffer[idx + 1] = SoftKneeLimiter.processSample(outRight)
            idx += 2
        }
    }

    fun processInterleavedPcm16(buffer: ShortArray, size: Int) {
        val frameCount = size / 2
        if (frameCount <= 0) return
        val floatBuf = FloatArray(size)
        for (i in 0 until size) {
            floatBuf[i] = buffer[i] / 32768.0f
        }
        processInterleavedStereo(floatBuf, frameCount)
        for (i in 0 until size) {
            val s = (floatBuf[i] * 32767.0f).toInt().coerceIn(-32768, 32767)
            buffer[i] = s.toShort()
        }
    }

    fun computeVirtualizerStrengthPermille(): Int {
        val normalized = ((stereoWidth - 1.0f) / 1.0f).coerceIn(0f, 1f)
        return (normalized * 1000f).toInt()
    }
}

