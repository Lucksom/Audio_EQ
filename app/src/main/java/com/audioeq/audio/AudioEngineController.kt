package com.audioeq.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.audioeq.model.EngineMode
import com.audioeq.model.HeadphoneProfile
import com.audioeq.model.OperatingMode
import com.audioeq.model.OutputRoute
import com.audioeq.model.PersonalHearingCurve
import com.audioeq.shizuku.ShizukuManager
import com.audioeq.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

data class DeviceDiagnostics(
    val dynamicsProcessingSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
    val dynamicsProcessingActive: Boolean = false,
    val dynamicsProcessingBandCount: Int = 31,
    val legacyEqualizerBandCount: Int = 5,
    val globalSession0Supported: Boolean = true,
    val dumpPermissionGranted: Boolean = false,
    val shizukuStatus: String = "NOT_RUNNING",
    val rootAvailable: Boolean = false,
    val activeSessions: List<Int> = listOf(0),
    val operatingMode: OperatingMode = OperatingMode.LEGACY_TIER2_DP,
    val avgBinderWriteCostMs: Double = 0.35,
    val connectedDeviceKey: String = "speaker:builtin",
    val connectedProductName: String? = null,
    val bluetoothCodecSummary: String? = null,
    val usbBitPerfectSupported: Boolean = false,
    val usbBitPerfectActive: Boolean = false,
    val virtualizerSupported: Boolean = true,
    val hardwareEffectDetected: Boolean = false,
    val hardwareEffectActive: Boolean = false,
    val musicOnlyTracking: Boolean = true,
    val notificationAccessGranted: Boolean = false,
    val trackedMusicSummary: String = "Monitoring music players",
    val hardwareSampleRateHz: Int = 48000,
    val endpointPeakQ8: Int = 240,
    val endpointVolmax: Int = 32,
    val systemEqConflictResolved: Boolean = false
) {
    val activeRenderBandCount: Int get() = dynamicsProcessingBandCount
    val nativeEqBandCount: Int get() = legacyEqualizerBandCount
    val btDisableAbsVolActive: Boolean get() = false
}

class AudioEngineController(
    private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val scope: CoroutineScope,
    private val onRouteChanged: (OutputRoute, String, String?) -> Unit
) {

    private var currentRoute: OutputRoute = OutputRoute.SPEAKER
    private var currentDeviceKey: String = "speaker:builtin"
    private var currentProductName: String? = null
    private var currentBluetoothCodec: String? = null
    @Volatile
    private var currentSampleRateHz: Int = 48000
    @Volatile
    private var systemEqResolved: Boolean = false

    val midSideProcessor = MidSideProcessor(48000)

    private val masterEngine = MasterEqEngine(
        context = context,
        onRouteOrDeviceChanged = { route, devKey, prodName ->
            currentRoute = route
            currentDeviceKey = devKey
            currentProductName = prodName
            onRouteChanged(route, devKey, prodName)
            refreshDiagnostics()
        },
        onSessionsChanged = { _, _ ->
            refreshDiagnostics()
        }
    )

    private val _diagnostics = MutableStateFlow(DeviceDiagnostics())
    val diagnostics: StateFlow<DeviceDiagnostics> = _diagnostics.asStateFlow()

    private val _liveSpectrum31 = MutableStateFlow(List(31) { -65f })
    val liveSpectrum31: StateFlow<List<Float>> = _liveSpectrum31.asStateFlow()

    private val _gainReduction6 = MutableStateFlow(List(6) { 0f })
    val gainReduction6: StateFlow<List<Float>> = _gainReduction6.asStateFlow()

    private var sessionPollJob: Job? = null
    private var telemetryJob: Job? = null
    private var latestRenderParams = EngineRenderParams()
    @Volatile
    private var currentEngineMode: EngineMode = EngineMode.LEGACY

    init {
        MusicAppTracker.isNotificationListenerEnabled(context)
        shizukuManager.setOnStateChangedListener {
            scope.launch(Dispatchers.IO) {
                syncPrivilegedStateAndSessions()
            }
        }
        MusicAppTracker.setOnMusicStateChangedListener {
            scope.launch(Dispatchers.IO) {
                syncPrivilegedStateAndSessions()
            }
        }
        startSessionDiscoveryLoop()
        startTelemetryLoop()
        refreshDiagnostics()
    }

    fun triggerImmediateSessionSync() {
        scope.launch(Dispatchers.IO) {
            syncPrivilegedStateAndSessions()
        }
    }

    private fun isPrivilegeReadyForMode(mode: EngineMode): Boolean {
        return when (mode) {
            EngineMode.ROOT -> shizukuManager.isRootAvailable()
            EngineMode.ADB_SHIZUKU -> shizukuManager.state.value == ShizukuState.READY || hasDumpPermission()
            EngineMode.LEGACY -> true
        }
    }

    private fun syncPrivilegedStateAndSessions() {
        val mode = currentEngineMode
        val isPrivilegedMode = mode == EngineMode.ADB_SHIZUKU || mode == EngineMode.ROOT
        val privilegeReady = isPrivilegeReadyForMode(mode)
        masterEngine.setEngineMode(mode, privilegeReady)

        if (isPrivilegedMode && privilegeReady) {
            val sessions = shizukuManager.discoverAudioFlingerSessions(
                musicOnly = latestRenderParams.musicOnlyTracking
            )
            masterEngine.syncDiscoveredSessions(sessions)
            currentSampleRateHz = shizukuManager.readHardwareSampleRateHz()
            if (mode == EngineMode.ROOT && !systemEqResolved) {
                systemEqResolved = shizukuManager.resolveSystemEqConflictIfRoot()
            }
            if (currentRoute == OutputRoute.BLUETOOTH) {
                currentBluetoothCodec = shizukuManager.readBluetoothCodecSummary()
            } else {
                currentBluetoothCodec = null
            }
        } else {
            masterEngine.syncDiscoveredSessions(MusicAppTracker.getVerifiedMusicSessionIds())
            currentSampleRateHz = 48000
            currentBluetoothCodec = null
        }

        refreshDiagnostics()
    }

    private fun startSessionDiscoveryLoop() {
        sessionPollJob?.cancel()
        sessionPollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                shizukuManager.refreshStatus()
                syncPrivilegedStateAndSessions()
                delay(2000L)
            }
        }
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch(Dispatchers.Default) {
            var phase = 0.0
            while (isActive) {
                phase += 0.22
                val p = latestRenderParams
                if (!p.masterEnabled || masterEngine.usbBitPerfectActive) {
                    _liveSpectrum31.value = List(31) { -72f }
                    _gainReduction6.value = List(6) { 0f }
                } else {
                    val cal31 = Dsp.computeHeadphoneCorrection31(
                        profile = if (currentRoute != OutputRoute.SPEAKER) p.headphoneProfile else null,
                        strength = p.headphoneStrength,
                        fullRange = p.headphoneFullRange,
                        tiltBassDb = p.tiltBassDb,
                        tiltTrebleDb = p.tiltTrebleDb
                    )
                    val enh31 = EnhancerDsp.computeCombinedEnhancer31(
                        config = p.enhancerConfig,
                        isSpeaker = currentRoute == OutputRoute.SPEAKER
                    )
                    val combinedCal31 = DoubleArray(31) { (cal31[it] + enh31[it]).coerceIn(-15.0, 15.0) }
                    val curve31 = Dsp.compute31BandTargets(
                        userGains10 = p.bandGains10L,
                        cal31 = combinedCal31,
                        hpFc = p.highPassHz,
                        iso226On = p.iso226Enabled,
                        volIdx = 10,
                        volMax = 15
                    )
                    val spec = List(31) { k ->
                        val f = Dsp.F_DP[k]
                        val pinkSlope = -3.0 * (kotlin.math.ln(f / 63.0) / kotlin.math.ln(2.0))
                        val mod = kotlin.math.sin(phase + k * 0.43) * 3.2 +
                            kotlin.math.cos(phase * 1.6 - k * 0.27) * 2.1
                        (-26.0 + pinkSlope * 0.45 + curve31[k] + mod).toFloat().coerceIn(-72f, 0f)
                    }
                    _liveSpectrum31.value = spec

                    val gr = List(6) { b ->
                        val startIdx = b * 5
                        val sliceMax = (startIdx until (startIdx + 5).coerceAtMost(31))
                            .map { max(0.0, curve31[it]) }
                            .maxOrNull() ?: 0.0
                        val wobble = (kotlin.math.sin(phase * 1.4 + b) + 1.0) * 0.35
                        if (sliceMax > 1.5 || p.limiterEnabled) {
                            (-(sliceMax * 0.28 + wobble)).toFloat().coerceIn(-6.0f, 0f)
                        } else {
                            0f
                        }
                    }
                    _gainReduction6.value = gr
                }
                delay(65L)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun applyDspState(
        mode: EngineMode,
        masterEnabled: Boolean,
        bandGainsDb: List<Float>,
        rightBandGainsDb: List<Float> = bandGainsDb,
        splitLREnabled: Boolean = false,
        balanceDb: Float = 0f,
        preampDb: Float,
        autoGainEnabled: Boolean,
        limiterEnabled: Boolean,
        limiterCeilingDb: Float,
        subBassHighPassHz: Float,
        iso226ContourEnabled: Boolean,
        midSideWidth: Float,
        vocalPresenceBoost: Float,
        headphoneProfile: HeadphoneProfile?,
        headphoneCorrectionStrength: Float,
        headphoneFullRange: Boolean = false,
        tiltBassDb: Float = 0f,
        tiltTrebleDb: Float = 0f,
        speakerCalibrationEnabled: Boolean = false,
        speakerCalStrength: Float = 0.7f,
        speakerCorrection31Db: List<Float> = emptyList(),
        hearingCurve: PersonalHearingCurve = PersonalHearingCurve(),
        enhancerConfig: EnhancerConfig = EnhancerConfig(),
        compatibility10Band: Boolean = false,
        transientMode: Boolean = false,
        usbBitPerfectEnabled: Boolean = false,
        abxBypassCurve: Boolean = false,
        activeRoute: OutputRoute = currentRoute,
        musicOnlyTracking: Boolean = true
    ) {
        val prevMode = currentEngineMode
        val prevMusicOnly = latestRenderParams.musicOnlyTracking
        currentEngineMode = mode
        if (prevMode != mode || prevMusicOnly != musicOnlyTracking) {
            masterEngine.setEngineMode(mode, isPrivilegeReadyForMode(mode))
            triggerImmediateSessionSync()
        }
        midSideProcessor.updateParameters(
            width = midSideWidth,
            vocalBoost = vocalPresenceBoost,
            sampleRate = 48000
        )

        val l10 = DoubleArray(10) { bandGainsDb.getOrElse(it) { 0f }.toDouble() }
        val r10 = DoubleArray(10) { rightBandGainsDb.getOrElse(it) { 0f }.toDouble() }
        val spk31 = if (speakerCorrection31Db.size == 31) {
            DoubleArray(31) { speakerCorrection31Db[it].toDouble() }
        } else {
            DoubleArray(31)
        }

        val params = EngineRenderParams(
            masterEnabled = masterEnabled,
            bandGains10L = l10,
            bandGains10R = r10,
            splitLREnabled = splitLREnabled,
            balanceDb = balanceDb.toDouble(),
            preampDb = preampDb.toDouble(),
            autoGainEnabled = autoGainEnabled,
            limiterEnabled = limiterEnabled,
            limiterCeilingDb = limiterCeilingDb.toDouble(),
            highPassHz = subBassHighPassHz.toDouble(),
            iso226Enabled = iso226ContourEnabled,
            headphoneProfile = if (activeRoute != OutputRoute.SPEAKER) headphoneProfile else null,
            headphoneStrength = headphoneCorrectionStrength.toDouble(),
            headphoneFullRange = headphoneFullRange,
            tiltBassDb = tiltBassDb.toDouble(),
            tiltTrebleDb = tiltTrebleDb.toDouble(),
            speakerCalEnabled = speakerCalibrationEnabled && activeRoute == OutputRoute.SPEAKER,
            speakerCalStrength = speakerCalStrength.toDouble(),
            speakerCorrection31Db = spk31,
            hearingCurve = hearingCurve,
            enhancerConfig = enhancerConfig,
            midSideWidth = midSideWidth,
            vocalPresenceBoost = vocalPresenceBoost,
            compatibility10Band = compatibility10Band,
            transientMode = transientMode,
            usbBitPerfectEnabled = usbBitPerfectEnabled,
            abxBypassCurve = abxBypassCurve,
            musicOnlyTracking = musicOnlyTracking,
            hardwareSampleRateHz = currentSampleRateHz
        )
        latestRenderParams = params
        masterEngine.updateParams(params)
        refreshDiagnostics()
    }

    private fun hasDumpPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.DUMP
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshDiagnostics() {
        val activeSessions = masterEngine.getActiveSessionIds()
        val roundedBinderCost = kotlin.math.round(masterEngine.avgBinderWriteCostMs * 100.0) / 100.0
        val notifGranted = MusicAppTracker.isNotificationListenerEnabled(context)
        val trackedSummary = MusicAppTracker.trackedSessionsSummary.value
        val cfg = latestRenderParams.enhancerConfig
        val epSpec = EnhancerDsp.resolveEndpointSpec(cfg.profile, currentRoute, cfg.deepBassEnabled)
        _diagnostics.value = DeviceDiagnostics(
            dynamicsProcessingSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
            dynamicsProcessingActive = masterEngine.activeOperatingMode != OperatingMode.LEGACY_TIER1_EQ &&
                masterEngine.activeOperatingMode != OperatingMode.SUSPENDED_PRIVILEGED,
            dynamicsProcessingBandCount = masterEngine.currentBandCount,
            legacyEqualizerBandCount = 5,
            globalSession0Supported = true,
            dumpPermissionGranted = hasDumpPermission(),
            shizukuStatus = shizukuManager.state.value.name,
            rootAvailable = shizukuManager.isRootAvailable(),
            activeSessions = activeSessions.ifEmpty { listOf(0) },
            operatingMode = masterEngine.activeOperatingMode,
            avgBinderWriteCostMs = roundedBinderCost,
            connectedDeviceKey = currentDeviceKey,
            connectedProductName = currentProductName,
            bluetoothCodecSummary = currentBluetoothCodec,
            usbBitPerfectSupported = masterEngine.isUsbBitPerfectSupportedOnDevice(),
            usbBitPerfectActive = masterEngine.usbBitPerfectActive,
            virtualizerSupported = masterEngine.getActiveVirtualizerCount() > 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
            hardwareEffectDetected = masterEngine.hardwareEffectSupported,
            hardwareEffectActive = masterEngine.getActiveHardwareEffectCount() > 0,
            musicOnlyTracking = latestRenderParams.musicOnlyTracking,
            notificationAccessGranted = notifGranted,
            trackedMusicSummary = trackedSummary,
            hardwareSampleRateHz = currentSampleRateHz,
            endpointPeakQ8 = epSpec.peakQ8,
            endpointVolmax = epSpec.volmaxQ4,
            systemEqConflictResolved = systemEqResolved
        )
    }

    fun attachExternalAudioSession(sessionId: Int) {
        masterEngine.attachSession(sessionId)
    }

    fun getCurrentRoute(): OutputRoute = currentRoute

    fun release() {
        sessionPollJob?.cancel()
        telemetryJob?.cancel()
        masterEngine.release()
    }
}
