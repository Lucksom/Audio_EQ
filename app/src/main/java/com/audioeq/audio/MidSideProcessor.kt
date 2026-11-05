package com.audioeq.audio

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
        sampleRate: Int = 48000
    ) {
        currentSampleRate = sampleRate
        stereoWidth = width.coerceIn(0.5f, 2.0f)
        vocalPresenceBoost = vocalBoost.coerceIn(0f, 1f)
        configureFilters(sampleRate)
    }

    private fun configureFilters(sampleRate: Int) {
        val vocalGainDb = vocalPresenceBoost * 4.2
        midVocalFilter.configurePeaking(
            frequencyHz = 2150.0,
            sampleRate = sampleRate.toDouble(),
            gainDb = vocalGainDb,
            q = 1.15
        )
        val airDb = (stereoWidth - 1.0f).coerceAtLeast(0f) * 2.5
        sideHighShelfFilter.configurePeaking(
            frequencyHz = 6500.0,
            sampleRate = sampleRate.toDouble(),
            gainDb = airDb,
            q = 0.85
        )
    }

    fun processInterleavedStereo(buffer: FloatArray, frameCount: Int) {
        val width = stereoWidth.coerceIn(0.5f, 2.0f)
        var idx = 0
        for (frame in 0 until frameCount) {
            val left = buffer[idx]
            val right = buffer[idx + 1]

            var mid = 0.5f * (left + right)
            var side = 0.5f * (left - right)

            if (vocalPresenceBoost > 0.01f) {
                mid = midVocalFilter.processSample(mid)
            }
            if (width > 1.01f) {
                side = sideHighShelfFilter.processSample(side)
            }

            side *= width

            val outLeft = (mid + side).coerceIn(-0.98f, 0.98f)
            val outRight = (mid - side).coerceIn(-0.98f, 0.98f)

            buffer[idx] = outLeft
            buffer[idx + 1] = outRight
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
