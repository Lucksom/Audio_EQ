package com.audioeq.model

import android.media.AudioDeviceInfo
import com.audioeq.audio.AutoEq
import com.audioeq.audio.PeqFilter

enum class CapabilityLevel {
    AVAILABLE,
    SETUP_REQUIRED,
    UNSUPPORTED
}

data class ModeStatusInfo(
    val level: CapabilityLevel = CapabilityLevel.AVAILABLE,
    val summaryText: String = "Ready",
    val description: String = "",
    val detailReason: String = description,
    val setupSteps: List<String> = emptyList()
)

enum class FilterType {
    PEAKING,
    LOW_SHELF,
    HIGH_SHELF,
    HIGH_PASS
}

enum class EqMode(val bandCount: Int) {
    TEN_BAND(10),
    THIRTY_TWO_BAND(32),
    SIXTY_FOUR_BAND(64)
}

data class MbcConfig(
    val baseThresholdDb: FloatArray = floatArrayOf(-12f, -14f, -16f, -16f, -18f, -20f),
    val attackMs: FloatArray = floatArrayOf(20f, 15f, 10f, 8f, 5f, 5f),
    val releaseMs: FloatArray = floatArrayOf(120f, 100f, 80f, 60f, 50f, 40f),
    val ratio: FloatArray = floatArrayOf(2.5f, 2.5f, 2.0f, 2.0f, 1.8f, 1.5f),
    val kneeDb: FloatArray = floatArrayOf(6f, 6f, 6f, 6f, 6f, 6f)
)

enum class EngineMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val description: String,
    val shortTitle: String = when (id) {
        "adb_shizuku" -> "ADB / Shizuku"
        "root" -> "Root"
        else -> "Legacy"
    }
) {
    LEGACY(
        id = "legacy",
        title = "Legacy Mode",
        subtitle = "Zero-Permission · Global Output Mix (Session 0)",
        badgeText = "Legacy (Global)",
        description = "Standard non-root global output mode. Applies effects directly to the device global audio output (session 0) everywhere without tracking any apps or sessions."
    ),
    ADB_SHIZUKU(
        id = "adb_shizuku",
        title = "ADB / Shizuku",
        subtitle = "AudioFlinger Session Hook + Dynamic Sample Rate (fs)",
        badgeText = "Shizuku Global",
        description = "Uses DUMP privilege via Shizuku/ADB to discover all AudioFlinger sessions, read real hardware sample rate (44.1/48/96 kHz) for dynamic biquad recalculation, and inspect Bluetooth A2DP codecs."
    ),
    ROOT(
        id = "root",
        title = "Root Mode",
        subtitle = "HAL Register Q4 Write + Session 0 + System EQ Guard",
        badgeText = "Root HAL",
        description = "Full su access: attaches to Global Mix Session 0, writes 20-band signed Q4 registers to hardware SoundFX HAL, isolates conflicting system equalizers, and manages BT Absolute Volume."
    );

    companion object {
        fun fromId(id: String?): EngineMode = entries.find { it.id == id } ?: LEGACY
    }
}

enum class OperatingMode(val badge: String) {
    ROOT_GLOBAL("Root · Global DSP"),
    SHIZUKU_GLOBAL("Shizuku · Global DSP"),
    LEGACY_TIER2_DP("Legacy · 31-Band DP"),
    LEGACY_TIER1_EQ("Legacy · 5-Band EQ"),
    SUSPENDED_PRIVILEGED("Standard Engine · Awaiting Privilege")
}

enum class LegacyGlobalMixMode(
    val id: String,
    val label: String,
    val subtitle: String
) {
    AUTO(
        id = "auto",
        label = "Auto (31-Band DP)",
        subtitle = "Uses 31-Band DynamicsProcessing on Global Output (Session 0) with automatic Native 5-Band FX fallback if unavailable"
    ),
    OFFLOAD_EQ(
        id = "offload_eq",
        label = "Native 5-Band FX",
        subtitle = "Uses Equalizer + BassBoost + Virtualizer + LoudnessEnhancer directly on Global Output (Session 0)"
    ),
    DYNAMICS_PROCESSING(
        id = "dynamics_processing",
        label = "31-Band DP Only",
        subtitle = "Prioritizes 31-Band DynamicsProcessing on Global Output (Session 0) with 5-Band FX fallback if unavailable"
    );

    companion object {
        fun fromId(id: String?): LegacyGlobalMixMode = entries.find { it.id == id } ?: AUTO
    }
}

enum class OutputRoute(
    val id: String,
    val label: String,
    val shortBadge: String,
    val defaultSafeCutDb: Float,
    val defaultLimiterCeilingDb: Float,
    val defaultHighPassHz: Float
) {
    SPEAKER(
        id = "speaker",
        label = "Phone Speaker",
        shortBadge = "Speaker",
        defaultSafeCutDb = 0.0f,
        defaultLimiterCeilingDb = -1.0f,
        defaultHighPassHz = 0f
    ),
    HEADPHONES(
        id = "headphones",
        label = "Wired Headphones",
        shortBadge = "Wired HP",
        defaultSafeCutDb = 0.0f,
        defaultLimiterCeilingDb = 0.0f,
        defaultHighPassHz = 0f
    ),
    BLUETOOTH(
        id = "bluetooth",
        label = "Bluetooth Audio",
        shortBadge = "Bluetooth",
        defaultSafeCutDb = 0.0f,
        defaultLimiterCeilingDb = 0.0f,
        defaultHighPassHz = 0f
    ),
    USB_DAC(
        id = "usb_dac",
        label = "USB DAC",
        shortBadge = "USB DAC",
        defaultSafeCutDb = 0.0f,
        defaultLimiterCeilingDb = 0.0f,
        defaultHighPassHz = 0f
    );

    companion object {
        fun fromId(id: String?): OutputRoute = entries.find { it.id == id } ?: SPEAKER

        fun fromDeviceTypes(types: Collection<Int>): OutputRoute {
            if (types.any {
                    it == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it == AudioDeviceInfo.TYPE_USB_DEVICE ||
                        it == AudioDeviceInfo.TYPE_USB_ACCESSORY
                }
            ) {
                return USB_DAC
            }
            if (types.any {
                    it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        it == AudioDeviceInfo.TYPE_BLE_BROADCAST
                }
            ) {
                return BLUETOOTH
            }
            if (types.any {
                    it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it == AudioDeviceInfo.TYPE_LINE_ANALOG ||
                        it == AudioDeviceInfo.TYPE_LINE_DIGITAL
                }
            ) {
                return HEADPHONES
            }
            return SPEAKER
        }
    }
}

data class EqBand(
    val index: Int,
    val frequencyHz: Int,
    val label: String,
    val gainDb: Float = 0f
)

typealias PresetConfig = EqPreset

data class EqPreset(
    val id: String,
    val name: String,
    val gainsDb: List<Float>,
    val isCustom: Boolean = false,
    val recommendedPreampDb: Float = 0.0f,
    val recommendedHighPassHz: Float = 0f
) {
    val gains10Band: List<Float> get() = gainsDb
    val isBuiltIn: Boolean get() = !isCustom
}

data class HeadphoneProfile(
    val id: String,
    val name: String,
    val source: String,
    val rig: String = "HMS II.3",
    val earphoneCategory: String = "Headphones",
    val peqFilters: List<PeqFilter> = emptyList(),
    val graphicPoints: List<Pair<Double, Double>> = emptyList(),
    val correction31Db: List<Float>,
    val isApproximation: Boolean = false,
    val license: String = "CC-BY-NC-SA-4.0",
    val rawText: String? = null
) {
    val filters: List<PeqFilter> get() = peqFilters
}

data class PersonalHearingCurve(
    val enabled: Boolean = false,
    val frequenciesHz: List<Int> = listOf(250, 500, 1000, 2000, 4000, 8000),
    val leftEarBoostDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f),
    val rightEarBoostDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f)
) {
    val leftEarDb: List<Float> get() = leftEarBoostDb
    val rightEarDb: List<Float> get() = rightEarBoostDb
}

data class RouteMemoryProfile(
    val route: OutputRoute,
    val deviceKey: String = route.id,
    val presetId: String = "flat",
    val bandGainsDb: List<Float> = List(10) { 0f },
    val rightBandGainsDb: List<Float> = List(10) { 0f },
    val splitLREnabled: Boolean = false,
    val balanceDb: Float = 0f,
    val preampDb: Float = 0f,
    val autoGainEnabled: Boolean = false,
    val limiterEnabled: Boolean = true,
    val limiterCeilingDb: Float = route.defaultLimiterCeilingDb,
    val subBassHighPassHz: Float = 0f,
    val iso226ContourEnabled: Boolean = false,
    val midSideWidth: Float = 1.0f,
    val vocalPresenceBoost: Float = 0.0f,
    val headphoneProfileId: String = "none",
    val headphoneCorrectionStrength: Float = 0.8f,
    val headphoneFullRange: Boolean = false,
    val tiltBassDb: Float = 0f,
    val tiltTrebleDb: Float = 0f,
    val speakerCalibrationEnabled: Boolean = false,
    val speakerCalStrength: Float = 0.7f
)

object EqConstants {
    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f
    const val MIN_PREAMP_DB = -12f
    const val MAX_PREAMP_DB = 6f
    const val DEFAULT_SEED_HEX = "#5B8CFF"

    val DEFAULT_MBC_CONFIG = MbcConfig()

    fun mbcForPreset(preset: EqPreset? = null, nightMode: Boolean = false): MbcConfig {
        if (nightMode) {
            return MbcConfig(
                baseThresholdDb = floatArrayOf(-18f, -20f, -22f, -22f, -24f, -26f),
                ratio = floatArrayOf(3.5f, 3.5f, 3.0f, 3.0f, 2.5f, 2.0f)
            )
        }
        return DEFAULT_MBC_CONFIG
    }

    val TEN_BAND_FREQUENCIES_HZ = listOf(
        31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
    )

    val TEN_BAND_LABELS = listOf(
        "31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"
    )

    val THIRTY_ONE_BAND_FREQUENCIES_HZ = listOf(
        20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
        200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
        2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f,
        20000f
    )

    val BAND_FREQUENCIES_10: DoubleArray = DoubleArray(10) { TEN_BAND_FREQUENCIES_HZ[it].toDouble() }
    val BAND_FREQUENCIES_31: DoubleArray = DoubleArray(31) { THIRTY_ONE_BAND_FREQUENCIES_HZ[it].toDouble() }

    val HEARING_TEST_FREQUENCIES_HZ = listOf(250, 500, 1000, 2000, 4000, 8000)
    val HEARING_TEST_FREQS_HZ = HEARING_TEST_FREQUENCIES_HZ.map { it.toDouble() }

    val FREQ_10_BAND = listOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val FREQ_32_BAND = (0 until 32).map { i -> (20.0 * Math.pow(2.0, i / 3.1)).toFloat() }
    val FREQ_64_BAND = (0 until 64).map { i -> (20.0 * Math.pow(2.0, i / 6.33)).toFloat() }

    fun frequenciesForMode(mode: EqMode): List<Float> = when (mode) {
        EqMode.TEN_BAND -> FREQ_10_BAND
        EqMode.THIRTY_TWO_BAND -> FREQ_32_BAND
        EqMode.SIXTY_FOUR_BAND -> FREQ_64_BAND
    }

    fun resampleCurve(srcFreqs: List<Float>, srcGains: List<Float>, dstFreqs: List<Float>): List<Float> {
        if (srcFreqs.isEmpty() || srcGains.isEmpty()) return dstFreqs.map { 0f }
        val srcLog = srcFreqs.map { kotlin.math.ln(it.toDouble()) }
        return dstFreqs.map { df ->
            val x = kotlin.math.ln(df.toDouble())
            when {
                x <= srcLog.first() -> srcGains.first()
                x >= srcLog.last() -> srcGains.last()
                else -> {
                    var idx = 0
                    while (idx < srcLog.size - 1 && srcLog[idx + 1] < x) {
                        idx++
                    }
                    val t = (x - srcLog[idx]) / (srcLog[idx + 1] - srcLog[idx])
                    (srcGains[idx] + t * (srcGains[idx + 1] - srcGains[idx])).toFloat()
                }
            }
        }
    }

    val BUILT_IN_PRESETS = listOf(
        EqPreset(
            id = "flat",
            name = "Flat",
            gainsDb = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            recommendedPreampDb = 0.0f,
            recommendedHighPassHz = 0f
        ),
        EqPreset(
            id = "rock",
            name = "Rock",
            gainsDb = listOf(4.0f, 1.0f, -2.0f, -0.25f, 0.0f, -2.0f, 0.0f, -2.0f, 0.5f, 4.0f),
            recommendedPreampDb = 0.0f,
            recommendedHighPassHz = 0f
        ),
        EqPreset(
            id = "jazz",
            name = "Jazz",
            gainsDb = listOf(0.0f, 0.0f, 0.0f, -1.0f, -1.0f, -3.0f, -0.5f, 0.0f, 0.0f, 0.0f),
            recommendedPreampDb = 0.0f,
            recommendedHighPassHz = 0f
        ),
        EqPreset(
            id = "pop",
            name = "Pop",
            gainsDb = listOf(-2.0f, -0.5f, -5.0f, -1.0f, 0.0f, 0.0f, -0.5f, -3.0f, -0.5f, 0.0f),
            recommendedPreampDb = 0.0f,
            recommendedHighPassHz = 0f
        ),
        EqPreset(
            id = "classical",
            name = "Classical",
            gainsDb = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 3.0f, 1.0f, 6.0f, 2.0f, 6.0f),
            recommendedPreampDb = 0.0f,
            recommendedHighPassHz = 0f
        ),
        EqPreset(
            id = "hip_hop",
            name = "Hip-Hop",
            gainsDb = listOf(3.0f, 0.0f, -3.0f, -0.5f, -0.5f, -3.0f, -0.5f, 0.0f, 0.0f, 2.0f),
            recommendedPreampDb = 0.0f,
            recommendedHighPassHz = 0f
        ),
        EqPreset(
            id = "blues",
            name = "Blues",
            gainsDb = listOf(2.0f, 2.0f, -6.0f, -2.0f, 3.0f, 1.0f, 0.0f, 1.0f, 0.0f, 2.0f),
            recommendedPreampDb = 0.0f,
            recommendedHighPassHz = 0f
        ),
        EqPreset(
            id = "electronic",
            name = "Electronic",
            gainsDb = listOf(3.0f, 1.0f, -1.0f, 0.0f, -0.5f, -3.0f, -0.5f, 0.0f, 0.0f, 0.0f),
            recommendedPreampDb = 0.0f,
            recommendedHighPassHz = 0f
        ),
        EqPreset(
            id = "metal",
            name = "Metal",
            gainsDb = listOf(2.0f, 0.0f, 0.0f, -1.25f, -1.0f, -4.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            recommendedPreampDb = 0.0f,
            recommendedHighPassHz = 0f
        )
    )

    private fun buildProfileFromPeq(
        id: String,
        name: String,
        source: String,
        rig: String,
        category: String,
        filters: List<PeqFilter>
    ): HeadphoneProfile {
        val corr31 = AutoEq.peqToCorrection31(filters).map { it.toFloat() }
        return HeadphoneProfile(
            id = id,
            name = name,
            source = source,
            rig = rig,
            earphoneCategory = category,
            peqFilters = filters,
            correction31Db = corr31,
            isApproximation = false,
            license = "CC-BY-NC-SA-4.0"
        )
    }

    val BUILT_IN_HEADPHONE_PROFILES: List<HeadphoneProfile> = listOf(
        HeadphoneProfile(
            id = "none",
            name = "Off (No Profile)",
            source = "Bypass",
            rig = "None",
            earphoneCategory = "All",
            peqFilters = emptyList(),
            correction31Db = List(31) { 0f }
        ),
        buildProfileFromPeq(
            id = "blessing_3",
            name = "Moondrop Blessing 3",
            source = "crinacle",
            rig = "711 In-Ear",
            category = "Earphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, 2.8, 0.70),
                PeqFilter("PK", 210.0, -1.2, 1.40),
                PeqFilter("PK", 1450.0, -1.4, 1.80),
                PeqFilter("PK", 3100.0, -2.2, 2.10),
                PeqFilter("PK", 5800.0, -2.5, 2.60),
                PeqFilter("HSC", 10000.0, 1.2, 0.70)
            )
        ),
        buildProfileFromPeq(
            id = "truthear_hexa",
            name = "Truthear HEXA",
            source = "crinacle",
            rig = "711 In-Ear",
            category = "Earphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, 2.2, 0.70),
                PeqFilter("PK", 185.0, -1.5, 1.30),
                PeqFilter("PK", 1300.0, -0.9, 1.90),
                PeqFilter("PK", 3000.0, -1.6, 2.20),
                PeqFilter("HSC", 9500.0, -1.8, 0.70)
            )
        ),
        buildProfileFromPeq(
            id = "ie600",
            name = "Sennheiser IE 600",
            source = "oratory1990",
            rig = "GRAS 43AC",
            category = "Earphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, -1.8, 0.70),
                PeqFilter("PK", 220.0, -1.4, 1.20),
                PeqFilter("PK", 1600.0, 2.1, 1.60),
                PeqFilter("PK", 3200.0, -1.9, 2.00),
                PeqFilter("PK", 6800.0, -3.8, 2.90)
            )
        ),
        buildProfileFromPeq(
            id = "7hz_timeless",
            name = "7Hz Timeless",
            source = "crinacle",
            rig = "711 In-Ear",
            category = "Earphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, -1.5, 0.70),
                PeqFilter("PK", 190.0, -2.1, 1.30),
                PeqFilter("PK", 2100.0, -2.4, 1.90),
                PeqFilter("PK", 4200.0, 1.8, 2.20),
                PeqFilter("PK", 8000.0, -3.2, 2.80)
            )
        ),
        buildProfileFromPeq(
            id = "shure_se215",
            name = "Shure SE215",
            source = "oratory1990",
            rig = "GRAS 43AC",
            category = "Earphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, -2.5, 0.70),
                PeqFilter("PK", 240.0, -3.4, 1.20),
                PeqFilter("PK", 2700.0, 4.2, 1.60),
                PeqFilter("PK", 5200.0, 3.8, 2.00),
                PeqFilter("HSC", 9000.0, 4.5, 0.70)
            )
        ),
        buildProfileFromPeq(
            id = "kz_zs10_pro",
            name = "KZ ZS10 Pro",
            source = "crinacle",
            rig = "711 In-Ear",
            category = "Earphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, -2.8, 0.70),
                PeqFilter("PK", 180.0, -3.1, 1.20),
                PeqFilter("PK", 1100.0, 2.2, 1.50),
                PeqFilter("PK", 2400.0, -3.5, 2.00),
                PeqFilter("PK", 4800.0, -4.2, 2.50)
            )
        ),
        buildProfileFromPeq(
            id = "airpods_pro_2",
            name = "Apple AirPods Pro 2",
            source = "crinacle",
            rig = "Bruel & Kjaer 5128",
            category = "Earbuds",
            filters = listOf(
                PeqFilter("LSC", 105.0, 1.4, 0.70),
                PeqFilter("PK", 230.0, -1.6, 1.50),
                PeqFilter("PK", 1200.0, -1.1, 1.80),
                PeqFilter("PK", 2850.0, 2.4, 1.90),
                PeqFilter("PK", 5800.0, -2.0, 2.50),
                PeqFilter("HSC", 10000.0, 1.5, 0.70)
            )
        ),
        buildProfileFromPeq(
            id = "wf1000xm5",
            name = "WF-1000XM5",
            source = "oratory1990",
            rig = "HMS II.3",
            category = "Earbuds",
            filters = listOf(
                PeqFilter("LSC", 105.0, -1.9, 0.70),
                PeqFilter("PK", 240.0, -2.5, 1.30),
                PeqFilter("PK", 1400.0, 1.6, 1.70),
                PeqFilter("PK", 3200.0, 2.8, 1.90),
                PeqFilter("HSC", 9000.0, 2.4, 0.70)
            )
        ),
        buildProfileFromPeq(
            id = "galaxy_buds2_pro",
            name = "Samsung Galaxy Buds2 Pro",
            source = "crinacle",
            rig = "GRAS 43AG-7",
            category = "Earbuds",
            filters = listOf(
                PeqFilter("LSC", 105.0, -1.6, 0.70),
                PeqFilter("PK", 320.0, 1.2, 1.40),
                PeqFilter("PK", 1600.0, -1.5, 1.70),
                PeqFilter("PK", 3000.0, -2.8, 2.10),
                PeqFilter("PK", 7200.0, -3.0, 2.80)
            )
        ),
        buildProfileFromPeq(
            id = "nothing_ear_2",
            name = "Nothing Ear (2)",
            source = "crinacle",
            rig = "711 In-Ear",
            category = "Earbuds",
            filters = listOf(
                PeqFilter("LSC", 105.0, -2.1, 0.70),
                PeqFilter("PK", 215.0, -2.4, 1.30),
                PeqFilter("PK", 1500.0, 1.8, 1.60),
                PeqFilter("PK", 3400.0, -2.2, 2.00),
                PeqFilter("PK", 6500.0, -2.6, 2.50)
            )
        ),
        buildProfileFromPeq(
            id = "qc_ultra_earbuds",
            name = "QuietComfort Ultra Earbuds",
            source = "rtings",
            rig = "HMS II.3",
            category = "Earbuds",
            filters = listOf(
                PeqFilter("LSC", 105.0, -2.9, 0.70),
                PeqFilter("PK", 250.0, -2.2, 1.40),
                PeqFilter("PK", 1800.0, 1.5, 1.80),
                PeqFilter("PK", 3600.0, 2.1, 2.10),
                PeqFilter("HSC", 8500.0, -1.5, 0.70)
            )
        ),
        buildProfileFromPeq(
            id = "hd650",
            name = "Sennheiser HD 650",
            source = "oratory1990",
            rig = "GRAS 43AG-7",
            category = "Headphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, 5.5, 0.70),
                PeqFilter("PK", 210.0, -2.1, 1.30),
                PeqFilter("PK", 1050.0, 1.0, 1.80),
                PeqFilter("PK", 3300.0, 2.3, 2.10),
                PeqFilter("HSC", 10000.0, 1.8, 0.70)
            )
        ),
        buildProfileFromPeq(
            id = "hd600",
            name = "Sennheiser HD 600",
            source = "oratory1990",
            rig = "GRAS 43AG-7",
            category = "Headphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, 5.8, 0.70),
                PeqFilter("PK", 220.0, -1.6, 1.30),
                PeqFilter("PK", 3200.0, -2.0, 2.20),
                PeqFilter("HSC", 10000.0, 1.4, 0.70)
            )
        ),
        buildProfileFromPeq(
            id = "wh1000xm5",
            name = "WH-1000XM5",
            source = "oratory1990",
            rig = "GRAS 43AG-7",
            category = "Headphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, -3.8, 0.70),
                PeqFilter("PK", 195.0, -4.6, 1.10),
                PeqFilter("PK", 460.0, 2.4, 1.40),
                PeqFilter("PK", 1550.0, 3.2, 1.60),
                PeqFilter("PK", 3100.0, 2.5, 2.00),
                PeqFilter("HSC", 8500.0, 2.6, 0.70)
            )
        ),
        buildProfileFromPeq(
            id = "airpods_max",
            name = "Apple AirPods Max",
            source = "oratory1990",
            rig = "GRAS 43AG-7",
            category = "Headphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, -1.8, 0.70),
                PeqFilter("PK", 310.0, 2.6, 1.40),
                PeqFilter("PK", 1150.0, -1.9, 1.80),
                PeqFilter("PK", 2350.0, 3.8, 1.90),
                PeqFilter("PK", 4600.0, 2.4, 2.20)
            )
        ),
        buildProfileFromPeq(
            id = "dt990_pro",
            name = "Beyerdynamic DT 990 Pro",
            source = "oratory1990",
            rig = "GRAS 43AG-7",
            category = "Headphones",
            filters = listOf(
                PeqFilter("LSC", 85.0, 4.8, 0.70),
                PeqFilter("PK", 180.0, -2.8, 1.20),
                PeqFilter("PK", 650.0, 1.4, 1.40),
                PeqFilter("PK", 3500.0, 2.0, 2.00),
                PeqFilter("PK", 6000.0, -3.5, 3.00),
                PeqFilter("PK", 8800.0, -6.2, 3.20)
            )
        ),
        buildProfileFromPeq(
            id = "dt770_pro",
            name = "Beyerdynamic DT 770 Pro (80 Ohm)",
            source = "oratory1990",
            rig = "GRAS 43AG-7",
            category = "Headphones",
            filters = listOf(
                PeqFilter("LSC", 100.0, -1.5, 0.70),
                PeqFilter("PK", 210.0, 3.4, 1.40),
                PeqFilter("PK", 3600.0, -2.5, 2.20),
                PeqFilter("PK", 8400.0, -5.4, 3.00)
            )
        ),
        buildProfileFromPeq(
            id = "sundara",
            name = "HIFIMAN Sundara",
            source = "oratory1990",
            rig = "GRAS 43AG-7",
            category = "Headphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, 5.2, 0.70),
                PeqFilter("PK", 950.0, -1.4, 2.10),
                PeqFilter("PK", 1950.0, 3.0, 1.80),
                PeqFilter("PK", 4600.0, -1.8, 2.60),
                PeqFilter("PK", 7200.0, -2.5, 3.00)
            )
        ),
        buildProfileFromPeq(
            id = "edition_xs",
            name = "HIFIMAN Edition XS",
            source = "oratory1990",
            rig = "GRAS 43AG-7",
            category = "Headphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, 4.5, 0.70),
                PeqFilter("PK", 1750.0, 3.2, 1.70),
                PeqFilter("PK", 3800.0, -1.8, 2.40),
                PeqFilter("PK", 8200.0, -3.0, 3.00)
            )
        ),
        buildProfileFromPeq(
            id = "ath_m50x",
            name = "Audio-Technica ATH-M50x",
            source = "oratory1990",
            rig = "GRAS 43AG-7",
            category = "Headphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, -1.2, 0.70),
                PeqFilter("PK", 155.0, -3.5, 1.30),
                PeqFilter("PK", 360.0, 2.8, 1.50),
                PeqFilter("PK", 1900.0, 2.1, 1.90),
                PeqFilter("PK", 4500.0, -2.6, 2.40)
            )
        ),
        buildProfileFromPeq(
            id = "akg_k371",
            name = "AKG K371",
            source = "oratory1990",
            rig = "GRAS 43AG-7",
            category = "Headphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, -1.0, 0.70),
                PeqFilter("PK", 380.0, 1.4, 1.50),
                PeqFilter("PK", 2100.0, 1.8, 1.90),
                PeqFilter("PK", 3900.0, -2.4, 2.30)
            )
        ),
        buildProfileFromPeq(
            id = "qc_ultra",
            name = "QuietComfort Ultra",
            source = "rtings",
            rig = "HMS II.3",
            category = "Headphones",
            filters = listOf(
                PeqFilter("LSC", 105.0, -3.2, 0.70),
                PeqFilter("PK", 220.0, -2.6, 1.30),
                PeqFilter("PK", 1600.0, 1.8, 1.70),
                PeqFilter("PK", 3400.0, 2.2, 2.00)
            )
        )
    )
}
