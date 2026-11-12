package com.audioeq.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.audioeq.model.EngineMode
import com.audioeq.model.HeadphoneProfile
import com.audioeq.model.LegacyGlobalMixMode
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
    val systemEqConflictResolved: Boolean = false,
    val vendorOffloadEqualizerDetected: Boolean = false,
    val session0ControlActive: Boolean = false,
    val legacyEngineSummary: String = "Initializing"
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
    @Volatile
    private var lastSyncedMode: EngineMode? = null
    @Volatile
    private var lastSyncedPrivilegeReady: Boolean? = null
    @Volatile
    private var forceNextSync: Boolean = true

    init {
        MusicAppTracker.isNotificationListenerEnabled(context)
        shizukuManager.setOnStateChangedListener {
            forceNextSync = true
            scope.launch(Dispatchers.IO) {
                syncPrivilegedStateAndSessions()
            }
        }
        MusicAppTracker.setOnMusicStateChangedListener {
            forceNextSync = true
            scope.launch(Dispatchers.IO) {
                syncPrivilegedStateAndSessions()
            }
        }
        startSessionDiscoveryLoop()
        startTelemetryLoop()
        refreshDiagnostics()
    }

    fun triggerImmediateSessionSync() {
        forceNextSync = true
        scope.launch(Dispatchers.IO) {
            syncPrivilegedStateAndSessions()
        }
    }

    private fun isPrivilegeReadyForMode(mode: EngineMode): Boolean {
        return when (mode) {
            EngineMode.ROOT -> shizukuManager.isRootAvailable()
            EngineMode.ADB_SHIZUKU ->
                shizukuManager.state.value == ShizukuState.READY ||
                    hasDumpPermission() ||
                    shizukuManager.isRootAvailable()
            EngineMode.LEGACY -> true
        }
    }

    private fun syncPrivilegedStateAndSessions() {
        val mode = currentEngineMode
        val isPrivilegedMode = mode == EngineMode.ADB_SHIZUKU || mode == EngineMode.ROOT
        val privilegeReady = isPrivilegeReadyForMode(mode)
        val forced = forceNextSync
        forceNextSync = false

        if (!forced &&
            mode == EngineMode.LEGACY &&
            lastSyncedMode == EngineMode.LEGACY &&
            lastSyncedPrivilegeReady == privilegeReady &&
            masterEngine.isSession0Healthy()
        ) {
            refreshDiagnostics()
            return
        }

        lastSyncedMode = mode
        lastSyncedPrivilegeReady = privilegeReady
        masterEngine.setEngineMode(mode, privilegeReady)
        MusicAppTracker.setEngineTrackingContext(
            context = context,
            isLegacyMode = mode == EngineMode.LEGACY,
            hasPrivilegedControl = isPrivilegedMode && privilegeReady
        )

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
        } else if (isPrivilegedMode) {
            MusicAppTracker.updatePrivilegedMusicSessions(context, emptyList())
            val fallbackSids = MusicAppTracker.getVerifiedMusicSessionIds()
            masterEngine.syncDiscoveredSessions(fallbackSids)
            currentSampleRateHz = 48000
            currentBluetoothCodec = null
        } else {
            // EngineMode.LEGACY: zero session tracking; apply globally on output Mix session 0
            MusicAppTracker.updatePrivilegedMusicSessions(context, emptyList())
            masterEngine.syncDiscoveredSessions(emptyList())
            currentSampleRateHz = 48000
            currentBluetoothCodec = null
        }

        refreshDiagnostics()
    }

    private fun startSessionDiscoveryLoop() {
        sessionPollJob?.cancel()
        sessionPollJob = scope.launch(Dispatchers.IO) {
            var pollTick = 0
            while (isActive) {
                shizukuManager.refreshStatus()
                if (currentEngineMode == EngineMode.ROOT &&
                    !shizukuManager.isRootAvailable() &&
                    pollTick % 3 == 0
                ) {
                    shizukuManager.verifyRootAccess(timeoutMs = 2500L)
                }
                syncPrivilegedStateAndSessions()
                pollTick++
                delay(2000L)
            }
        }
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = null
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
        musicOnlyTracking: Boolean = true,
        legacyGlobalMixMode: LegacyGlobalMixMode = LegacyGlobalMixMode.AUTO
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
            headphoneProfile = headphoneProfile,
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
            hardwareSampleRateHz = currentSampleRateHz,
            legacyGlobalMixMode = legacyGlobalMixMode
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
        val newDiag = DeviceDiagnostics(
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
            systemEqConflictResolved = systemEqResolved,
            vendorOffloadEqualizerDetected = masterEngine.vendorOffloadEqualizerDetected,
            session0ControlActive = masterEngine.hasSession0Control(),
            legacyEngineSummary = masterEngine.getLegacyEngineSummary()
        )
        if (_diagnostics.value != newDiag) {
            _diagnostics.value = newDiag
        }
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
