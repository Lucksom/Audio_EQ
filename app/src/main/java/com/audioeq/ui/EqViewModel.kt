package com.audioeq.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audioeq.audio.AudioEngineController
import com.audioeq.audio.AudioProfile
import com.audioeq.audio.AutoEq
import com.audioeq.audio.AutoEqEntry
import com.audioeq.audio.AutoEqKind
import com.audioeq.audio.AutoEqMapper
import com.audioeq.audio.AutoEqParser
import com.audioeq.audio.AutoEqRepo
import com.audioeq.audio.AutoEqRepository
import com.audioeq.audio.DeviceDiagnostics
import com.audioeq.audio.Dsp
import com.audioeq.audio.DspReferenceMonitor
import com.audioeq.audio.EnhancerConfig
import com.audioeq.audio.EnhancerDsp
import com.audioeq.audio.EqualizerNotificationHelper
import com.audioeq.audio.IeqPreset
import com.audioeq.audio.IndexEntry
import com.audioeq.audio.MusicAppTracker
import com.audioeq.data.EqRepository
import com.audioeq.model.EngineMode
import com.audioeq.model.EqBand
import com.audioeq.model.EqConstants
import com.audioeq.model.EqPreset
import com.audioeq.model.HeadphoneProfile
import com.audioeq.model.OperatingMode
import com.audioeq.model.OutputRoute
import com.audioeq.model.PersonalHearingCurve
import com.audioeq.model.RouteMemoryProfile
import com.audioeq.shizuku.ShizukuManager
import com.audioeq.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EqUiState(
    val engineMode: EngineMode = EngineMode.LEGACY,
    val operatingMode: OperatingMode = OperatingMode.LEGACY_TIER2_DP,
    val masterEnabled: Boolean = true,
    val activeRoute: OutputRoute = OutputRoute.SPEAKER,
    val activeDeviceKey: String = "speaker:builtin",
    val connectedProductName: String? = null,
    val currentPresetId: String = "flat",
    val activePresetName: String = "Flat",
    val presets: List<EqPreset> = emptyList(),
    val bands: List<EqBand> = emptyList(),
    val rightBands: List<EqBand> = emptyList(),
    val isEqEnabled: Boolean = true,
    val isAbCompare: Boolean = false,
    val editingRightChannel: Boolean = false,
    val intensity: Float = 1.0f,
    val ceilingDb: Float = 0.0f,
    val effectiveGainsDb: List<Float> = emptyList(),
    val splitLREnabled: Boolean = false,
    val balanceDb: Float = 0f,
    val preampDb: Float = 0f,
    val autoGainEnabled: Boolean = false,
    val limiterEnabled: Boolean = true,
    val limiterCeilingDb: Float = -0.3f,
    val highPassHz: Float = 0f,
    val iso226Enabled: Boolean = false,
    val midSideWidth: Float = 1.0f,
    val vocalPresenceBoost: Float = 0.0f,
    val headphoneProfileId: String = "none",
    val headphoneStrength: Float = 0.8f,
    val headphoneFullRange: Boolean = false,
    val headphoneTrimmedDb: Float = 0f,
    val tiltBassDb: Float = 0f,
    val tiltTrebleDb: Float = 0f,
    val headphoneProfiles: List<HeadphoneProfile> = emptyList(),
    val autoEqIndex: List<AutoEqEntry> = emptyList(),
    val suggestedAutoEqEntry: AutoEqEntry? = null,
    val autoEqClipped: Boolean = false,
    val autoEqUseTenBandSliders: Boolean = false,
    val autoEqCatalog: List<IndexEntry> = emptyList(),
    val autoEqLoading: Boolean = false,
    val autoEqRateLimited: Boolean = false,
    val autoEqOffline: Boolean = false,
    val speakerCalEnabled: Boolean = false,
    val speakerCalStrength: Float = 0.7f,
    val speakerMeas31Db: List<Float> = emptyList(),
    val speakerCorrection31Db: List<Float> = emptyList(),
    val isCalibratingSpeaker: Boolean = false,
    val calibrationProgress: Float = 0f,
    val calibrationStatusText: String = "",
    val hearingCurve: PersonalHearingCurve = PersonalHearingCurve(),
    val enhancerConfig: EnhancerConfig = EnhancerConfig(),
    val liveSpectrum31: List<Float> = emptyList(),
    val gainReduction6: List<Float> = emptyList(),
    val diagnostics: DeviceDiagnostics = DeviceDiagnostics(),
    val shizukuState: ShizukuState = ShizukuState.NOT_RUNNING,
    val shizukuPrivilege: String = "",
    val compatibility10Band: Boolean = false,
    val transientMode: Boolean = false,
    val usbBitPerfectEnabled: Boolean = false,
    val musicOnlyTrackingEnabled: Boolean = true,
    val selectedMusicPackages: Set<String> = emptySet(),
    val themeMode: String = "light",
    val seedHex: String = "#006A6A",
    val isSeedFromWallpaper: Boolean = false,
    val showSavePresetDialog: Boolean = false,
    val showRootWarningDialog: Boolean = false,
    val showCalibrationSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val statusBannerMessage: String? = null
)

class EqViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EqRepository(application)
    val shizukuManager = ShizukuManager(application)
    val autoEqRepository = AutoEqRepository(application)
    private val autoEqRepo = AutoEqRepo(application)
    val dspMonitor = DspReferenceMonitor(viewModelScope)

    private val _autoEqIndex = MutableStateFlow<List<AutoEqEntry>>(emptyList())
    private val _suggestedAutoEqEntry = MutableStateFlow<AutoEqEntry?>(null)
    private val _autoEqClipped = MutableStateFlow(false)
    private val _autoEqUseTenBandSliders = MutableStateFlow(false)
    @Volatile
    private var pendingDesiredMode: EngineMode? = null

    val engineController = AudioEngineController(
        context = application,
        shizukuManager = shizukuManager,
        scope = viewModelScope,
        onRouteChanged = { route, devKey, prodName ->
            repository.switchOutputRoute(route, devKey)
            val currentEnh = repository.enhancerConfig.value
            if (currentEnh.profile != AudioProfile.OFF) {
                val adapted = EnhancerDsp.applyProfileForRoute(currentEnh, currentEnh.profile, route)
                repository.updateEnhancerConfig { adapted }
                val spec = EnhancerDsp.resolveEndpointSpec(currentEnh.profile, route, adapted.deepBassEnabled)
                repository.updateCurrentRouteProfile { it.copy(limiterCeilingDb = spec.peakCeilingDb) }
            }
            if (route == OutputRoute.SPEAKER) {
                _suggestedAutoEqEntry.value = null
            } else if (!prodName.isNullOrBlank()) {
                val savedHpId = repository.currentRouteProfile.value.headphoneProfileId
                if (savedHpId == "none") {
                    val matched = autoEqRepository.matchDevice(_autoEqIndex.value, prodName)
                    _suggestedAutoEqEntry.value = matched
                } else {
                    _suggestedAutoEqEntry.value = null
                }
            }
        }
    )

    private val _autoEqCatalog = MutableStateFlow<List<IndexEntry>>(emptyList())
    private val _autoEqLoading = MutableStateFlow(false)
    private val _autoEqRateLimited = MutableStateFlow(false)
    private val _autoEqOffline = MutableStateFlow(false)

    private val _speakerMeas31 = MutableStateFlow<List<Float>>(emptyList())
    private val _speakerCorr31 = MutableStateFlow<List<Float>>(emptyList())
    private val _isCalibratingSpeaker = MutableStateFlow(false)
    private val _calibrationProgress = MutableStateFlow(0f)
    private val _calibrationStatus = MutableStateFlow("")

    private val _showSavePresetDialog = MutableStateFlow(false)
    private val _showRootWarningDialog = MutableStateFlow(false)
    private val _showCalibrationSheet = MutableStateFlow(false)
    private val _showSettingsSheet = MutableStateFlow(false)

    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _isEqEnabled = MutableStateFlow(true)
    private val _isAbCompare = MutableStateFlow(false)
    private val _editingRightChannel = MutableStateFlow(false)
    private val _intensity = MutableStateFlow(1.0f)

    private val flow1A = combine(
        repository.engineMode,
        repository.masterEnabled,
        repository.activeRoute,
        repository.activeDeviceKey
    ) { mode, enabled, route, devKey ->
        Intermediate1A(mode, enabled, route, devKey)
    }

    private val flow1B = combine(
        repository.presets,
        repository.currentRouteProfile,
        repository.hearingCurve,
        repository.enhancerConfig,
        repository.customHeadphoneProfiles
    ) { presets, profile, hearing, enhancer, customHp ->
        Intermediate1B(presets, profile, hearing, enhancer, customHp)
    }

    private val flow1C = combine(
        _isEqEnabled,
        _isAbCompare,
        _editingRightChannel,
        _intensity
    ) { eq, ab, er, int ->
        Intermediate1C(eq, ab, er, int)
    }

    private val flowP1 = combine(flow1A, flow1B, flow1C) { a, b, c ->
        val bands = EqConstants.TEN_BAND_FREQUENCIES_HZ.indices.map { i ->
            EqBand(
                index = i,
                frequencyHz = EqConstants.TEN_BAND_FREQUENCIES_HZ[i],
                label = EqConstants.TEN_BAND_LABELS[i],
                gainDb = b.profile.bandGainsDb.getOrElse(i) { 0f }
            )
        }
        val rightBands = EqConstants.TEN_BAND_FREQUENCIES_HZ.indices.map { i ->
            EqBand(
                index = i,
                frequencyHz = EqConstants.TEN_BAND_FREQUENCIES_HZ[i],
                label = EqConstants.TEN_BAND_LABELS[i],
                gainDb = b.profile.rightBandGainsDb.getOrElse(i) { 0f }
            )
        }
        PartialUiState1(
            engineMode = a.engineMode,
            masterEnabled = a.masterEnabled,
            activeRoute = a.activeRoute,
            activeDeviceKey = a.activeDeviceKey,
            presets = b.presets,
            profile = b.profile,
            bands = bands,
            rightBands = rightBands,
            hearingCurve = b.hearingCurve,
            enhancerConfig = b.enhancerConfig,
            isEqEnabled = c.isEqEnabled,
            isAbCompare = c.isAbCompare,
            editingRightChannel = c.editingRightChannel,
            intensity = c.intensity,
            customHeadphoneProfiles = b.customHeadphoneProfiles
        )
    }

    private val flowP2 = combine(
        engineController.diagnostics,
        engineController.liveSpectrum31,
        engineController.gainReduction6,
        shizukuManager.state,
        shizukuManager.privilegeSummary
    ) { diag, spec, gr, sState, sPriv ->
        PartialUiState2(
            diagnostics = diag,
            liveSpectrum31 = spec,
            gainReduction6 = gr,
            shizukuState = sState,
            shizukuPrivilege = sPriv
        )
    }

    private val flow3A = combine(
        repository.selectedMusicPackages,
        repository.compatibility10Band,
        repository.transientMode,
        repository.usbBitPerfectEnabled
    ) { pkgs, compat10, trans, usb ->
        Intermediate3A(pkgs, compat10, trans, usb)
    }

    private val flow3B = combine(
        repository.themeMode,
        repository.seedHex,
        repository.wallpaperExtractActive,
        repository.musicOnlyTracking
    ) { theme, seed, wall, musicOnly ->
        Intermediate3B(theme, seed, wall, musicOnly)
    }

    private val flowP3 = combine(flow3A, flow3B) { a, b ->
        PartialUiState3(
            selectedMusicPackages = a.selectedMusicPackages,
            compatibility10Band = a.compatibility10Band,
            transientMode = a.transientMode,
            usbBitPerfectEnabled = a.usbBitPerfectEnabled,
            musicOnlyTrackingEnabled = b.musicOnlyTrackingEnabled,
            themeMode = b.themeMode,
            seedHex = b.seedHex,
            isSeedFromWallpaper = b.isSeedFromWallpaper
        )
    }

    private val flow4A = combine(
        _autoEqCatalog,
        _autoEqLoading,
        _autoEqRateLimited,
        _autoEqOffline
    ) { cat, loading, rateLim, offline ->
        Intermediate4A(cat, loading, rateLim, offline)
    }

    private val flow4B = combine(
        _speakerMeas31,
        _speakerCorr31,
        _isCalibratingSpeaker,
        _calibrationProgress
    ) { meas, corr, isCal, prog ->
        Intermediate4B(meas, corr, isCal, prog)
    }

    private val flow4C = combine(
        _autoEqIndex,
        _suggestedAutoEqEntry,
        _autoEqClipped,
        _autoEqUseTenBandSliders
    ) { idx, sug, clip, useSliders ->
        Intermediate4C(idx, sug, clip, useSliders)
    }

    private val flowP4 = combine(flow4A, flow4B, flow4C) { a, b, c ->
        PartialUiState4(
            autoEqCatalog = a.autoEqCatalog,
            autoEqLoading = a.autoEqLoading,
            autoEqRateLimited = a.autoEqRateLimited,
            autoEqOffline = a.autoEqOffline,
            speakerMeas31Db = b.speakerMeas31Db,
            speakerCorrection31Db = b.speakerCorrection31Db,
            isCalibratingSpeaker = b.isCalibratingSpeaker,
            calibrationProgress = b.calibrationProgress,
            autoEqIndex = c.autoEqIndex,
            suggestedAutoEqEntry = c.suggestedAutoEqEntry,
            autoEqClipped = c.autoEqClipped,
            autoEqUseTenBandSliders = c.autoEqUseTenBandSliders
        )
    }

    private val flow5A = combine(
        _calibrationStatus,
        _showSavePresetDialog,
        _showRootWarningDialog
    ) { calStat, saveD, rootD ->
        Intermediate5A(calStat, saveD, rootD)
    }

    private val flow5B = combine(
        _showCalibrationSheet,
        _showSettingsSheet,
        _statusBanner
    ) { calS, setS, banner ->
        Intermediate5B(calS, setS, banner)
    }

    private val flowP5 = combine(flow5A, flow5B) { a, b ->
        PartialUiState5(
            calibrationStatusText = a.calibrationStatusText,
            showSavePresetDialog = a.showSavePresetDialog,
            showRootWarningDialog = a.showRootWarningDialog,
            showCalibrationSheet = b.showCalibrationSheet,
            showSettingsSheet = b.showSettingsSheet,
            statusBannerMessage = b.statusBannerMessage
        )
    }

    val uiState: StateFlow<EqUiState> = combine(
        flowP1,
        flowP2,
        flowP3,
        flowP4,
        flowP5
    ) { p1, p2, p3, p4, p5 ->
        val allHp = EqConstants.BUILT_IN_HEADPHONE_PROFILES + p1.customHeadphoneProfiles
        val isHeadphoneOrBtRoute = p1.activeRoute != OutputRoute.SPEAKER
        val selHp = if (isHeadphoneOrBtRoute) {
            allHp.find { it.id == p1.profile.headphoneProfileId }
        } else {
            null
        }
        val trimmed = if (selHp != null && selHp.peqFilters.isNotEmpty()) {
            AutoEq.computeTrimmedDb(selHp.peqFilters, p1.profile.headphoneFullRange).toFloat()
        } else {
            0f
        }
        val activePresetName = p1.presets.find { it.id == p1.profile.presetId }?.name ?: "Flat"
        val sliderGains = if (p1.editingRightChannel) p1.rightBands.map { it.gainDb } else p1.bands.map { it.gainDb }
        val tilt31 = AutoEq.computeTiltCurve31(
            bassDb = p1.profile.tiltBassDb.toDouble(),
            trebleDb = p1.profile.tiltTrebleDb.toDouble()
        )
        val contour10 = if (p1.profile.iso226ContourEnabled) {
            Dsp.contour(10.0 / 15.0)
        } else {
            DoubleArray(10)
        }
        val hpCorr31 = if (isHeadphoneOrBtRoute && selHp != null && selHp.id != "none") {
            Dsp.computeHeadphoneCorrection31(
                profile = selHp,
                strength = p1.profile.headphoneCorrectionStrength.toDouble(),
                fullRange = p1.profile.headphoneFullRange,
                tiltBassDb = 0.0,
                tiltTrebleDb = 0.0
            )
        } else {
            DoubleArray(31)
        }
        val enh31 = EnhancerDsp.computeCombinedEnhancer31(
            config = p1.enhancerConfig,
            isSpeaker = p1.activeRoute == OutputRoute.SPEAKER,
            sampleRate = p2.diagnostics.hardwareSampleRateHz.toDouble()
        )
        val deepShift31 = EnhancerDsp.computeDeepBassShiftDelta31(
            userGains10 = sliderGains.map { (it * p1.intensity).toDouble() }.toDoubleArray(),
            deepBassEnabled = p1.enhancerConfig.deepBassEnabled
        )
        val baseWithTone10 = EqConstants.FREQ_10_BAND.indices.map { i ->
            val eqGain = if (p1.isEqEnabled) sliderGains.getOrElse(i) { 0f } * p1.intensity else 0f
            val fHz = EqConstants.FREQ_10_BAND[i].toDouble()
            val nearestIdx = EqConstants.BAND_FREQUENCIES_31.indices.minByOrNull {
                kotlin.math.abs(EqConstants.BAND_FREQUENCIES_31[it] - fHz)
            } ?: 0
            val tiltAtBand = tilt31.getOrElse(nearestIdx) { 0.0 }.toFloat()
            val hpAtBand = hpCorr31.getOrElse(nearestIdx) { 0.0 }.toFloat()
            val contourAtBand = contour10.getOrElse(i) { 0.0 }.toFloat()
            eqGain + tiltAtBand + hpAtBand + contourAtBand
        }
        val effectiveGains = if (!p1.masterEnabled || p1.isAbCompare) {
            List(10) { 0f }
        } else {
            EqConstants.FREQ_10_BAND.indices.map { i ->
                val fHz = EqConstants.FREQ_10_BAND[i].toDouble()
                val nearestIdx = EqConstants.BAND_FREQUENCIES_31.indices.minByOrNull {
                    kotlin.math.abs(EqConstants.BAND_FREQUENCIES_31[it] - fHz)
                } ?: 0
                val enhOffset = (enh31.getOrElse(nearestIdx) { 0.0 } + deepShift31.getOrElse(nearestIdx) { 0.0 }).toFloat()
                (baseWithTone10.getOrElse(i) { 0f } + enhOffset).coerceIn(-12.0f, 12.0f)
            }
        }
        val computedCeilingDb = if (!p1.masterEnabled || p1.isAbCompare) {
            0.0f
        } else {
            val maxPositiveGain = (effectiveGains.maxOrNull() ?: 0f).coerceAtLeast(0f)
            val virtualBassLoad = if (p1.enhancerConfig.virtualBassEnabled) 0.7f else 0f
            (maxPositiveGain * 0.52f + virtualBassLoad + trimmed * 0.35f)
                .coerceIn(0f, 9.0f)
        }

        EqUiState(
            engineMode = p1.engineMode,
            operatingMode = p2.diagnostics.operatingMode,
            masterEnabled = p1.masterEnabled,
            activeRoute = p1.activeRoute,
            activeDeviceKey = p1.activeDeviceKey,
            connectedProductName = p2.diagnostics.connectedProductName,
            currentPresetId = p1.profile.presetId,
            activePresetName = activePresetName,
            presets = p1.presets,
            bands = p1.bands,
            rightBands = p1.rightBands,
            isEqEnabled = p1.isEqEnabled,
            isAbCompare = p1.isAbCompare,
            editingRightChannel = p1.editingRightChannel,
            intensity = p1.intensity,
            ceilingDb = computedCeilingDb,
            effectiveGainsDb = effectiveGains,
            splitLREnabled = p1.profile.splitLREnabled,
            balanceDb = p1.profile.balanceDb,
            preampDb = p1.profile.preampDb,
            autoGainEnabled = p1.profile.autoGainEnabled,
            limiterEnabled = p1.profile.limiterEnabled,
            limiterCeilingDb = p1.profile.limiterCeilingDb,
            highPassHz = p1.profile.subBassHighPassHz,
            iso226Enabled = p1.profile.iso226ContourEnabled,
            midSideWidth = p1.profile.midSideWidth,
            vocalPresenceBoost = p1.profile.vocalPresenceBoost,
            headphoneProfileId = p1.profile.headphoneProfileId,
            headphoneStrength = p1.profile.headphoneCorrectionStrength,
            headphoneFullRange = p1.profile.headphoneFullRange,
            headphoneTrimmedDb = trimmed,
            tiltBassDb = p1.profile.tiltBassDb,
            tiltTrebleDb = p1.profile.tiltTrebleDb,
            headphoneProfiles = allHp,
            autoEqIndex = p4.autoEqIndex,
            suggestedAutoEqEntry = p4.suggestedAutoEqEntry,
            autoEqClipped = p4.autoEqClipped,
            autoEqUseTenBandSliders = p4.autoEqUseTenBandSliders,
            autoEqCatalog = p4.autoEqCatalog,
            autoEqLoading = p4.autoEqLoading,
            autoEqRateLimited = p4.autoEqRateLimited,
            autoEqOffline = p4.autoEqOffline,
            speakerCalEnabled = p1.profile.speakerCalibrationEnabled,
            speakerCalStrength = p1.profile.speakerCalStrength,
            speakerMeas31Db = p4.speakerMeas31Db,
            speakerCorrection31Db = p4.speakerCorrection31Db,
            isCalibratingSpeaker = p4.isCalibratingSpeaker,
            calibrationProgress = p4.calibrationProgress,
            calibrationStatusText = p5.calibrationStatusText,
            hearingCurve = p1.hearingCurve,
            enhancerConfig = p1.enhancerConfig,
            liveSpectrum31 = p2.liveSpectrum31,
            gainReduction6 = p2.gainReduction6,
            diagnostics = p2.diagnostics,
            shizukuState = p2.shizukuState,
            shizukuPrivilege = p2.shizukuPrivilege,
            compatibility10Band = p3.compatibility10Band,
            transientMode = p3.transientMode,
            usbBitPerfectEnabled = p3.usbBitPerfectEnabled,
            musicOnlyTrackingEnabled = p3.musicOnlyTrackingEnabled,
            selectedMusicPackages = p3.selectedMusicPackages,
            themeMode = p3.themeMode,
            seedHex = p3.seedHex,
            isSeedFromWallpaper = p3.isSeedFromWallpaper,
            showSavePresetDialog = p5.showSavePresetDialog,
            showRootWarningDialog = p5.showRootWarningDialog,
            showCalibrationSheet = p5.showCalibrationSheet,
            showSettingsSheet = p5.showSettingsSheet,
            statusBannerMessage = p5.statusBannerMessage
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EqUiState())

    init {
        loadStoredSpeakerCalibration(repository.activeRoute.value)
        refreshAutoEqCatalog(forceRefresh = false)

        viewModelScope.launch(Dispatchers.IO) {
            val bundledIndex = autoEqRepository.loadBundledIndex()
            if (bundledIndex.isNotEmpty()) {
                _autoEqIndex.value = bundledIndex
            }
            val fullIndex = autoEqRepository.loadIndex(forceRefresh = true)
            if (fullIndex.isNotEmpty()) {
                _autoEqIndex.value = fullIndex
            }
            val prodName = engineController.diagnostics.value.connectedProductName
            val route = repository.activeRoute.value
            if (route != OutputRoute.SPEAKER && !prodName.isNullOrBlank()) {
                val savedHpId = repository.currentRouteProfile.value.headphoneProfileId
                if (savedHpId == "none") {
                    _suggestedAutoEqEntry.value = autoEqRepository.matchDevice(_autoEqIndex.value, prodName)
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            shizukuManager.refreshStatus()
            if (repository.engineMode.value == EngineMode.ROOT) {
                val rootOk = shizukuManager.verifyRootAccess()
                if (!rootOk) {
                    val fallback = if (shizukuManager.state.value == ShizukuState.READY) {
                        EngineMode.ADB_SHIZUKU
                    } else {
                        EngineMode.LEGACY
                    }
                    repository.setEngineMode(fallback)
                }
            } else if (shizukuManager.state.value == ShizukuState.READY &&
                repository.engineMode.value != EngineMode.ADB_SHIZUKU
            ) {
                if (shizukuManager.isShizukuRunningAsRoot()) {
                    repository.setEngineMode(EngineMode.ROOT)
                } else {
                    repository.setEngineMode(EngineMode.ADB_SHIZUKU)
                }
            }
            engineController.triggerImmediateSessionSync()
        }

        viewModelScope.launch {
            var previousShizukuState = shizukuManager.state.value
            shizukuManager.state.collect { newState ->
                if (newState == ShizukuState.READY && previousShizukuState != ShizukuState.READY) {
                    val wantRoot = pendingDesiredMode == EngineMode.ROOT ||
                        repository.engineMode.value == EngineMode.ROOT ||
                        shizukuManager.isShizukuRunningAsRoot()
                    if (wantRoot && shizukuManager.verifyRootAccess()) {
                        pendingDesiredMode = null
                        repository.setEngineMode(EngineMode.ROOT)
                        shizukuManager.ensureBoundAndGrantPermissions()
                        engineController.triggerImmediateSessionSync()
                        _statusBanner.value = "Shizuku Root (${shizukuManager.privilege()}) applied — Root engine active"
                    } else {
                        pendingDesiredMode = null
                        repository.setEngineMode(EngineMode.ADB_SHIZUKU)
                        shizukuManager.ensureBoundAndGrantPermissions()
                        engineController.triggerImmediateSessionSync()
                        _statusBanner.value = "Shizuku permission granted — ${shizukuManager.privilege()} active"
                    }
                }
                previousShizukuState = newState
            }
        }

        viewModelScope.launch {
            uiState.collect { state ->
                val allHp = repository.getAllHeadphoneProfiles()
                val selHp = if (state.activeRoute != OutputRoute.SPEAKER) {
                    allHp.find { it.id == state.headphoneProfileId }
                } else {
                    null
                }
                val effectiveBands = if (state.isEqEnabled) state.bands.map { it.gainDb * state.intensity } else List(state.bands.size) { 0f }
                val effectiveRightBands = if (state.isEqEnabled) state.rightBands.map { it.gainDb * state.intensity } else List(state.rightBands.size) { 0f }
                engineController.applyDspState(
                    mode = state.engineMode,
                    masterEnabled = state.masterEnabled,
                    bandGainsDb = effectiveBands,
                    rightBandGainsDb = effectiveRightBands,
                    splitLREnabled = state.splitLREnabled,
                    balanceDb = state.balanceDb,
                    preampDb = state.preampDb,
                    autoGainEnabled = state.autoGainEnabled,
                    limiterEnabled = state.limiterEnabled,
                    limiterCeilingDb = state.limiterCeilingDb,
                    subBassHighPassHz = state.highPassHz,
                    iso226ContourEnabled = state.iso226Enabled,
                    midSideWidth = state.midSideWidth,
                    vocalPresenceBoost = state.vocalPresenceBoost,
                    headphoneProfile = selHp,
                    headphoneCorrectionStrength = state.headphoneStrength,
                    headphoneFullRange = state.headphoneFullRange,
                    tiltBassDb = state.tiltBassDb,
                    tiltTrebleDb = state.tiltTrebleDb,
                    speakerCalibrationEnabled = state.speakerCalEnabled,
                    speakerCalStrength = state.speakerCalStrength,
                    speakerCorrection31Db = state.speakerCorrection31Db,
                    hearingCurve = state.hearingCurve,
                    enhancerConfig = state.enhancerConfig,
                    compatibility10Band = state.compatibility10Band,
                    transientMode = state.transientMode,
                    usbBitPerfectEnabled = state.usbBitPerfectEnabled,
                    activeRoute = state.activeRoute,
                    musicOnlyTracking = state.musicOnlyTrackingEnabled
                )
                EqualizerNotificationHelper.updateOrCancelPersistentNotification(
                    context = getApplication(),
                    masterEnabled = state.masterEnabled,
                    presetName = state.activePresetName,
                    profileTitle = "",
                    routeLabel = state.activeRoute.label,
                    engineShortTitle = state.engineMode.shortTitle,
                    musicSummary = if (state.musicOnlyTrackingEnabled) {
                        state.diagnostics.trackedMusicSummary
                    } else {
                        "All Audio Sessions"
                    }
                )
            }
        }
    }

    private fun loadStoredSpeakerCalibration(route: OutputRoute) {
        val stored = repository.loadSpeakerCalibration(route.id)
        if (stored != null) {
            _speakerMeas31.value = stored.first
            _speakerCorr31.value = stored.second
        } else {
            _speakerMeas31.value = emptyList()
            _speakerCorr31.value = emptyList()
        }
    }

    fun toggleMaster(enabled: Boolean) {
        repository.setMasterEnabled(enabled)
    }

    fun selectPreset(preset: EqPreset) {
        repository.applyPreset(preset)
        repository.updateCurrentRouteProfile {
            it.copy(
                preampDb = preset.recommendedPreampDb,
                subBassHighPassHz = preset.recommendedHighPassHz
            )
        }
    }

    fun updateBandGain(index: Int, gainDb: Float, isRight: Boolean = false) {
        repository.updateBandGain(index, gainDb, editRightChannel = isRight)
    }

    fun flattenBands() {
        repository.flattenBands()
    }

    fun updatePreamp(preampDb: Float) {
        repository.updateCurrentRouteProfile { it.copy(preampDb = preampDb.coerceIn(-12f, 6f)) }
    }

    fun toggleAutoGain(enabled: Boolean) {
        repository.updateCurrentRouteProfile { it.copy(autoGainEnabled = enabled) }
    }

    fun toggleLimiter(enabled: Boolean) {
        repository.updateCurrentRouteProfile { it.copy(limiterEnabled = enabled) }
    }

    fun updateLimiterCeiling(ceilingDb: Float) {
        repository.updateCurrentRouteProfile { it.copy(limiterCeilingDb = ceilingDb.coerceIn(-6f, 0f)) }
    }

    fun updateHighPassHz(cutoffHz: Float) {
        repository.updateCurrentRouteProfile { it.copy(subBassHighPassHz = cutoffHz.coerceIn(0f, 200f)) }
    }

    fun toggleIso226(enabled: Boolean) {
        repository.updateCurrentRouteProfile { it.copy(iso226ContourEnabled = enabled) }
    }

    fun updateMidSideWidth(width: Float) {
        repository.updateCurrentRouteProfile { it.copy(midSideWidth = width.coerceIn(0.5f, 2.0f)) }
    }

    fun updateVocalPresence(boost: Float) {
        repository.updateCurrentRouteProfile { it.copy(vocalPresenceBoost = boost.coerceIn(0f, 1f)) }
    }

    fun setSplitLREnabled(enabled: Boolean) {
        repository.updateCurrentRouteProfile {
            it.copy(
                splitLREnabled = enabled,
                rightBandGainsDb = if (enabled && it.rightBandGainsDb.all { g -> g == 0f }) it.bandGainsDb else it.rightBandGainsDb
            )
        }
    }

    fun updateBalanceDb(balanceDb: Float) {
        repository.updateCurrentRouteProfile { it.copy(balanceDb = balanceDb.coerceIn(-6f, 6f)) }
    }

    fun setAutoEqUseTenBandSliders(enabled: Boolean) {
        _autoEqUseTenBandSliders.value = enabled
    }

    fun dismissSuggestedAutoEq() {
        _suggestedAutoEqEntry.value = null
    }

    fun clearAutoEqProfile() {
        _autoEqClipped.value = false
        repository.updateCurrentRouteProfile {
            it.copy(
                headphoneProfileId = "none",
                preampDb = 0f
            )
        }
        _statusBanner.value = "Cleared AutoEq headphone profile"
    }

    fun applyAutoEqEntry(
        entry: AutoEqEntry,
        useTenBandSliders: Boolean = _autoEqUseTenBandSliders.value
    ) {
        if (repository.activeRoute.value == OutputRoute.SPEAKER) {
            _statusBanner.value = "AutoEq is only for headphones/earphones, not phone speakers."
            return
        }
        _suggestedAutoEqEntry.value = null
        viewModelScope.launch {
            _autoEqLoading.value = true
            try {
                if (useTenBandSliders) {
                    val fixedText = autoEqRepository.fetch(entry, AutoEqKind.FIXED_BAND)
                    val p = AutoEqParser.parseParametric(fixedText)
                    val t = AutoEqMapper.fixedBandToTenBand(p)
                    _autoEqClipped.value = t.clipped
                    val gainsFloat = t.gainsDb.map { it.toFloat().coerceIn(-12f, 12f) }
                    val safeId = "autoeq_fb_" + autoEqRepository.normalize(entry.name).take(24)
                    val profile = HeadphoneProfile(
                        id = safeId,
                        name = entry.name,
                        source = entry.source,
                        rig = entry.form,
                        earphoneCategory = categoryFromForm(entry.form),
                        peqFilters = emptyList(),
                        graphicPoints = emptyList(),
                        correction31Db = List(31) { 0f }
                    )
                    repository.registerCustomHeadphoneProfile(profile)
                    repository.updateCurrentRouteProfile {
                        it.copy(
                            headphoneProfileId = safeId,
                            bandGainsDb = gainsFloat,
                            rightBandGainsDb = gainsFloat,
                            preampDb = t.preampDb.toFloat().coerceIn(-12f, 6f)
                        )
                    }
                    val clipNote = if (t.clipped) " (±12 dB band limit clamped)" else ""
                    _statusBanner.value = "Applied ${entry.name} (${entry.source}) FixedBandEQ to 10 sliders · Preamp ${String.format("%.1f", t.preampDb)} dB$clipNote"
                } else {
                    val paramText = autoEqRepository.fetch(entry, AutoEqKind.PARAMETRIC)
                    val p = AutoEqParser.parseParametric(paramText)
                    val safePreamp = AutoEqMapper.safePreampDb(p)
                    val dpBands = AutoEqMapper.toDynamicsBands(p)
                    _autoEqClipped.value = p.filters.any { kotlin.math.abs(AutoEqMapper.responseDb(p, it.fc)) > AutoEqMapper.MAX_GAIN_DB }
                    val safeId = "autoeq_peq_" + autoEqRepository.normalize(entry.name).take(24)
                    val profile = HeadphoneProfile(
                        id = safeId,
                        name = entry.name,
                        source = entry.source,
                        rig = entry.form,
                        earphoneCategory = categoryFromForm(entry.form),
                        peqFilters = p.filters.map { it.toPeqFilter() },
                        graphicPoints = emptyList(),
                        correction31Db = dpBands.map { it.gainDb },
                        rawText = paramText
                    )
                    repository.registerCustomHeadphoneProfile(profile)
                    repository.updateCurrentRouteProfile {
                        it.copy(
                            headphoneProfileId = safeId,
                            preampDb = safePreamp.toFloat().coerceIn(-12f, 6f)
                        )
                    }
                    val opMode = engineController.diagnostics.value.operatingMode
                    val modeDesc = when {
                        repository.engineMode.value == EngineMode.ROOT -> "Root Exact Biquads"
                        opMode == OperatingMode.LEGACY_TIER1_EQ -> "Legacy 5-Band Native EQ (Low accuracy)"
                        else -> "31-Band DynamicsProcessing"
                    }
                    _statusBanner.value = "Applied ${entry.name} (${entry.source}) via $modeDesc · Preamp ${String.format("%.1f", safePreamp)} dB"
                }
            } catch (e: Exception) {
                _statusBanner.value = "Could not apply AutoEq for ${entry.name}: ${e.message ?: "parse error"}"
            } finally {
                _autoEqLoading.value = false
            }
        }
    }

    private fun categoryFromForm(form: String): String {
        val lower = form.lowercase()
        return when {
            lower.contains("in-ear") || lower.contains("iem") -> "Earphones"
            lower.contains("earbud") || lower.contains("tws") -> "Earbuds"
            else -> "Headphones"
        }
    }

    fun selectHeadphoneProfile(profileId: String) {
        if (profileId == "none") {
            clearAutoEqProfile()
            return
        }
        if (repository.activeRoute.value == OutputRoute.SPEAKER) {
            _statusBanner.value = "AutoEq is disabled on speaker output."
            return
        }
        val allHp = repository.getAllHeadphoneProfiles()
        val sel = allHp.find { it.id == profileId }
        if (sel != null) {
            val safePreamp = if (sel.peqFilters.isNotEmpty()) {
                val autoProf = AutoEqMapper.fromPeqFilters(sel.peqFilters)
                AutoEqMapper.safePreampDb(autoProf).toFloat().coerceIn(-12f, 6f)
            } else {
                repository.currentRouteProfile.value.preampDb
            }
            repository.updateCurrentRouteProfile {
                it.copy(
                    headphoneProfileId = profileId,
                    preampDb = safePreamp
                )
            }
            _statusBanner.value = "Applied AutoEq: ${sel.name} (${sel.source}) · Preamp ${String.format("%.1f", safePreamp)} dB"
        }
    }

    fun updateHeadphoneStrength(strength: Float) {
        repository.updateCurrentRouteProfile { it.copy(headphoneCorrectionStrength = strength.coerceIn(0f, 1f)) }
    }

    fun setHeadphoneFullRange(enabled: Boolean) {
        repository.updateCurrentRouteProfile { it.copy(headphoneFullRange = enabled) }
    }

    fun updateTiltBassDb(db: Float) {
        repository.updateCurrentRouteProfile { it.copy(tiltBassDb = db.coerceIn(-6f, 6f)) }
    }

    fun updateTiltTrebleDb(db: Float) {
        repository.updateCurrentRouteProfile { it.copy(tiltTrebleDb = db.coerceIn(-4f, 4f)) }
    }

    fun toggleSpeakerCalibration(enabled: Boolean) {
        repository.updateCurrentRouteProfile { it.copy(speakerCalibrationEnabled = enabled) }
    }

    fun updateSpeakerCalStrength(strength: Float) {
        repository.updateCurrentRouteProfile { it.copy(speakerCalStrength = strength.coerceIn(0f, 1f)) }
    }

    fun runSpeakerSelfCalibration() {
        if (_isCalibratingSpeaker.value) return
        _isCalibratingSpeaker.value = true
        _calibrationProgress.value = 0.05f
        _calibrationStatus.value = "Starting log sweep calibration…"

        viewModelScope.launch {
            val res = dspMonitor.runSpeakerSelfCalibration(getApplication()) { prog, status ->
                _calibrationProgress.value = prog
                _calibrationStatus.value = status
            }
            _isCalibratingSpeaker.value = false
            if (res.success) {
                _speakerMeas31.value = res.measured31Db
                _speakerCorr31.value = res.correction31Db
                val route = repository.activeRoute.value
                repository.saveSpeakerCalibration(route.id, res.measured31Db, res.correction31Db)
                repository.updateCurrentRouteProfile { it.copy(speakerCalibrationEnabled = true) }
                _statusBanner.value = "Speaker calibrated: ${res.statusMessage}"
            } else {
                _statusBanner.value = res.statusMessage
            }
        }
    }

    fun resetSpeakerCalibration() {
        val route = repository.activeRoute.value
        repository.clearSpeakerCalibration(route.id)
        _speakerMeas31.value = emptyList()
        _speakerCorr31.value = emptyList()
        repository.updateCurrentRouteProfile { it.copy(speakerCalibrationEnabled = false) }
    }

    fun setHearingCurveEnabled(enabled: Boolean) {
        val current = repository.hearingCurve.value
        repository.updateHearingCurve(current.copy(enabled = enabled))
    }

    fun updateHearingCurveBand(isRight: Boolean, index: Int, boostDb: Float) {
        val current = repository.hearingCurve.value
        val clamped = boostDb.coerceIn(-6f, 12f)
        if (isRight) {
            val list = current.rightEarBoostDb.toMutableList()
            if (index in list.indices) {
                list[index] = clamped
                repository.updateHearingCurve(current.copy(rightEarBoostDb = list))
            }
        } else {
            val list = current.leftEarBoostDb.toMutableList()
            if (index in list.indices) {
                list[index] = clamped
                repository.updateHearingCurve(current.copy(leftEarBoostDb = list))
            }
        }
    }

    fun playHearingTestTone(index: Int, isRight: Boolean) {
        val freqs = EqConstants.HEARING_TEST_FREQUENCIES_HZ
        if (index in freqs.indices) {
            dspMonitor.playHearingTone(freqs[index], isRight)
        }
    }

    fun selectAudioProfile(profile: AudioProfile) {
        val route = repository.activeRoute.value
        repository.updateEnhancerConfig { cur ->
            EnhancerDsp.applyProfileForRoute(cur, profile, route)
        }
        val spec = EnhancerDsp.resolveEndpointSpec(
            profile = profile,
            route = route,
            deepBassOverride = repository.enhancerConfig.value.deepBassEnabled
        )
        repository.updateCurrentRouteProfile { it.copy(limiterCeilingDb = spec.peakCeilingDb) }
        if (profile != AudioProfile.OFF) {
            _statusBanner.value = "${profile.label} profile adapted for ${spec.endpointName} (PEAK=${spec.peakQ8}, VOLMAX=${spec.volmaxQ4})"
        }
    }

    fun setAudioProfile(profile: AudioProfile = AudioProfile.OFF) {
        selectAudioProfile(profile)
    }

    fun setDeepBassEnabled(enabled: Boolean) {
        repository.updateEnhancerConfig { cur ->
            val nextBoost = if (enabled && cur.bassBoostDb < 2.5f) 4.5f else cur.bassBoostDb
            cur.copy(
                deepBassEnabled = enabled,
                bassEnabled = if (enabled) true else cur.bassEnabled,
                bassBoostDb = nextBoost
            )
        }
    }

    fun selectIeqPreset(preset: IeqPreset) {
        repository.updateEnhancerConfig { it.copy(ieqPreset = preset) }
    }

    fun setIeqPreset(preset: IeqPreset) {
        selectIeqPreset(preset)
    }

    fun setIeqAmount(amount: Int) {
        repository.updateEnhancerConfig { it.copy(ieqAmount = amount.coerceIn(0, 16)) }
    }

    fun updateIeqAmount(amount: Int) {
        setIeqAmount(amount)
    }

    fun setDialogueEnabled(enabled: Boolean) {
        repository.updateEnhancerConfig { it.copy(dialogueEnhancerEnabled = enabled) }
    }

    fun setDialogueEnhancerEnabled(enabled: Boolean) {
        setDialogueEnabled(enabled)
    }

    fun setDialogueAmount(amount: Int) {
        repository.updateEnhancerConfig { it.copy(dialogueAmount = amount.coerceIn(0, 16)) }
    }

    fun updateDialogueAmount(amount: Int) {
        setDialogueAmount(amount)
    }

    fun setBassEnabled(enabled: Boolean) {
        repository.updateEnhancerConfig { cur ->
            val nextDb = if (enabled && cur.bassBoostDb < 0.5f) 3.5f else cur.bassBoostDb
            cur.copy(
                bassEnabled = enabled,
                bassBoostDb = nextDb
            )
        }
    }

    fun setBassEnhancerEnabled(enabled: Boolean) {
        setBassEnabled(enabled)
    }

    fun setBassBoostDb(db: Float) {
        val clamped = db.coerceIn(0f, 6f)
        repository.updateEnhancerConfig { cur ->
            cur.copy(
                bassEnabled = clamped > 0.05f,
                bassBoostDb = clamped
            )
        }
    }

    fun setVirtualBassEnabled(enabled: Boolean) {
        repository.updateEnhancerConfig { cur ->
            val nextStr = if (enabled && cur.virtualBassStrength < 0.05f) 0.55f else cur.virtualBassStrength
            cur.copy(
                virtualBassEnabled = enabled,
                virtualBassStrength = nextStr
            )
        }
    }

    fun setVirtualBassStrength(strength: Float) {
        val clamped = strength.coerceIn(0f, 1f)
        repository.updateEnhancerConfig { cur ->
            cur.copy(
                virtualBassEnabled = clamped > 0.02f,
                virtualBassStrength = clamped
            )
        }
    }

    fun setSurroundEnabled(enabled: Boolean) {
        repository.updateEnhancerConfig { cur ->
            val nextWidth = if (enabled && cur.surroundWidth < 0.05f) 0.45f else cur.surroundWidth
            cur.copy(
                surroundEnabled = enabled,
                surroundWidth = nextWidth
            )
        }
    }

    fun setSurroundWidth(width: Float) {
        val clamped = width.coerceIn(0f, 1f)
        repository.updateEnhancerConfig { cur ->
            cur.copy(
                surroundEnabled = clamped > 0.02f,
                surroundWidth = clamped
            )
        }
    }

    fun resetEnhancers() {
        repository.updateEnhancerConfig {
            EnhancerConfig()
        }
    }

    fun refreshAutoEqCatalog(forceRefresh: Boolean = false) {
        if (_autoEqLoading.value) return
        _autoEqLoading.value = true
        viewModelScope.launch {
            val res = autoEqRepo.loadIndex(forceRefresh)
            _autoEqLoading.value = false
            _autoEqRateLimited.value = res.isRateLimited
            _autoEqOffline.value = res.fromCache
            if (res.entries.isNotEmpty()) {
                _autoEqCatalog.value = res.entries
            }
        }
    }

    fun selectAutoEqCatalogEntry(entry: IndexEntry) {
        applyAutoEqEntry(
            AutoEqEntry(
                name = entry.name,
                source = entry.source ?: "oratory1990",
                form = entry.rig ?: "over-ear",
                path = entry.relPath
            ),
            useTenBandSliders = _autoEqUseTenBandSliders.value
        )
    }

    fun importCustomAutoEqText(name: String, rawText: String) {
        val cleanName = name.trim().ifEmpty { "Imported PEQ" }
        val peq = AutoEq.parsePeq(rawText)
        val geq = if (peq.isEmpty()) AutoEq.parseGraphicEq(rawText) else emptyList()
        if (peq.isEmpty() && geq.isEmpty()) {
            _statusBanner.value = "No valid ParametricEQ or GraphicEQ lines found."
            return
        }
        val safeId = "import_" + System.currentTimeMillis().toString().takeLast(6)
        val corr31 = if (peq.isNotEmpty()) {
            AutoEq.peqToCorrection31(peq).map { it.toFloat() }
        } else {
            AutoEq.graphicToCorrection31(geq).map { it.toFloat() }
        }
        val safePreamp = if (peq.isNotEmpty()) {
            val autoProf = AutoEqMapper.fromPeqFilters(peq, peq.preamp)
            AutoEqMapper.safePreampDb(autoProf).toFloat().coerceIn(-12f, 6f)
        } else {
            repository.currentRouteProfile.value.preampDb
        }
        val profile = HeadphoneProfile(
            id = safeId,
            name = cleanName,
            source = "Custom Import",
            rig = "User Text",
            peqFilters = peq,
            graphicPoints = geq,
            correction31Db = corr31,
            isApproximation = peq.isEmpty(),
            license = "User Content"
        )
        repository.registerCustomHeadphoneProfile(profile)
        repository.updateCurrentRouteProfile {
            it.copy(
                headphoneProfileId = profile.id,
                preampDb = safePreamp
            )
        }
        _statusBanner.value = "Imported & applied: $cleanName (Preamp ${String.format("%.1f", safePreamp)} dB)"
    }

    fun exportActiveCorrectionText(): String {
        val s = uiState.value
        val selHp = s.headphoneProfiles.find { it.id == s.headphoneProfileId }
        return if (selHp != null && selHp.peqFilters.isNotEmpty()) {
            AutoEq.exportToParametricEqText(
                profileName = selHp.name,
                preampDb = s.preampDb.toDouble(),
                filters = selHp.peqFilters
            )
        } else {
            val combined = Dsp.compute31BandTargets(
                userGains10 = DoubleArray(10) { s.bands[it].gainDb.toDouble() },
                cal31 = DoubleArray(31) { s.speakerCorrection31Db.getOrElse(it) { 0f }.toDouble() },
                hpFc = s.highPassHz.toDouble(),
                iso226On = s.iso226Enabled,
                volIdx = 10,
                volMax = 15
            )
            AutoEq.export31ToGraphicEqText(combined)
        }
    }

    fun selectMode(mode: EngineMode) {
        pendingDesiredMode = mode
        when (mode) {
            EngineMode.ROOT -> {
                if (!repository.rootWarningAccepted.value && !shizukuManager.isRootAvailable()) {
                    _showRootWarningDialog.value = true
                } else {
                    verifyAndActivateRootMode()
                }
            }
            EngineMode.ADB_SHIZUKU -> {
                if (!shizukuManager.isShizukuRunningAsRoot()) {
                    shizukuManager.clearRootVerification()
                }
                repository.setEngineMode(EngineMode.ADB_SHIZUKU)
                shizukuManager.refreshStatus()
                when (shizukuManager.state.value) {
                    ShizukuState.READY -> {
                        shizukuManager.ensureBoundAndGrantPermissions()
                        engineController.triggerImmediateSessionSync()
                        _statusBanner.value = "ADB / Shizuku applied — ${shizukuManager.privilege()} active"
                    }
                    ShizukuState.NEEDS_PERMISSION,
                    ShizukuState.DENIED_PERMANENTLY -> {
                        _statusBanner.value = "Applying Shizuku permission — please grant prompt"
                        shizukuManager.requestPermission()
                        engineController.triggerImmediateSessionSync()
                    }
                    ShizukuState.NOT_RUNNING -> {
                        _statusBanner.value = "Shizuku service is not running — tap 'Open Shizuku App' to start it"
                        engineController.triggerImmediateSessionSync()
                    }
                    ShizukuState.NOT_INSTALLED -> {
                        _statusBanner.value = "Shizuku is not installed — tap 'Install Shizuku' or grant DUMP via ADB"
                        engineController.triggerImmediateSessionSync()
                    }
                    else -> {
                        engineController.triggerImmediateSessionSync()
                    }
                }
            }
            EngineMode.LEGACY -> {
                pendingDesiredMode = null
                shizukuManager.clearRootVerification()
                repository.setEngineMode(EngineMode.LEGACY)
                engineController.triggerImmediateSessionSync()
                _statusBanner.value = "Legacy mode active"
            }
        }
    }

    fun confirmRootWarning() {
        repository.setRootWarningAccepted(true)
        _showRootWarningDialog.value = false
        verifyAndActivateRootMode()
    }

    private fun verifyAndActivateRootMode() {
        _statusBanner.value = "Applying Root (su) permission…"
        viewModelScope.launch(Dispatchers.IO) {
            shizukuManager.refreshStatus()
            if (shizukuManager.state.value == ShizukuState.NEEDS_PERMISSION ||
                shizukuManager.state.value == ShizukuState.DENIED_PERMANENTLY
            ) {
                shizukuManager.requestPermission()
            }
            val rootGranted = shizukuManager.verifyRootAccess()
            if (rootGranted) {
                pendingDesiredMode = null
                repository.setEngineMode(EngineMode.ROOT)
                shizukuManager.ensureBoundAndGrantPermissions()
                engineController.triggerImmediateSessionSync()
                _statusBanner.value = "Root (${shizukuManager.privilege()}) applied — Root engine active"
            } else if (shizukuManager.state.value == ShizukuState.NEEDS_PERMISSION) {
                repository.setEngineMode(EngineMode.ROOT)
                _statusBanner.value = "Waiting for Shizuku / Root (su) permission prompt…"
            } else {
                val fallback = if (shizukuManager.state.value == ShizukuState.READY) {
                    EngineMode.ADB_SHIZUKU
                } else {
                    EngineMode.LEGACY
                }
                repository.setEngineMode(fallback)
                engineController.triggerImmediateSessionSync()
                _statusBanner.value = "Root (su) permission not granted — using ${fallback.shortTitle} (${shizukuManager.privilege()})"
            }
        }
    }

    fun dismissRootWarningAndFallback() {
        pendingDesiredMode = null
        _showRootWarningDialog.value = false
        shizukuManager.clearRootVerification()
        val fallback = if (shizukuManager.state.value == ShizukuState.READY) {
            EngineMode.ADB_SHIZUKU
        } else {
            EngineMode.LEGACY
        }
        repository.setEngineMode(fallback)
        engineController.triggerImmediateSessionSync()
    }

    fun handleShizukuAction() {
        shizukuManager.refreshStatus()
        if (repository.engineMode.value == EngineMode.ROOT && !shizukuManager.isRootAvailable()) {
            verifyAndActivateRootMode()
            return
        }
        when (shizukuManager.state.value) {
            ShizukuState.NEEDS_PERMISSION,
            ShizukuState.DENIED_PERMANENTLY -> {
                _statusBanner.value = "Applying Shizuku permission — please grant prompt"
                shizukuManager.requestPermission()
            }
            ShizukuState.NOT_RUNNING -> {
                _statusBanner.value = "Opening Shizuku app…"
                shizukuManager.launchShizukuApp()
            }
            ShizukuState.NOT_INSTALLED -> {
                _statusBanner.value = "Opening Shizuku download page…"
                shizukuManager.openPlayStoreForShizuku()
            }
            ShizukuState.READY -> {
                if (shizukuManager.isShizukuRunningAsRoot() && repository.engineMode.value == EngineMode.ROOT) {
                    shizukuManager.verifyRootAccess()
                    shizukuManager.ensureBoundAndGrantPermissions()
                    engineController.triggerImmediateSessionSync()
                    _statusBanner.value = "Shizuku Root (${shizukuManager.privilege()}) active & synced"
                } else {
                    repository.setEngineMode(EngineMode.ADB_SHIZUKU)
                    shizukuManager.ensureBoundAndGrantPermissions()
                    engineController.triggerImmediateSessionSync()
                    _statusBanner.value = "Shizuku (${shizukuManager.privilege()}) connected & active"
                }
            }
            else -> shizukuManager.refreshStatus()
        }
    }

    fun toggleMusicPackage(pkg: String) {
        repository.toggleMusicPackage(pkg)
        engineController.triggerImmediateSessionSync()
    }

    fun addCustomMusicPackage(pkg: String) {
        val trimmed = pkg.trim().lowercase()
        if (trimmed.isNotEmpty()) {
            val current = repository.selectedMusicPackages.value.toMutableSet()
            current.add(trimmed)
            repository.setSelectedMusicPackages(current)
            engineController.triggerImmediateSessionSync()
            _statusBanner.value = "Added $trimmed to music packages whitelist"
        }
    }

    fun setCompatibility10Band(enabled: Boolean) {
        repository.setCompatibility10Band(enabled)
    }

    fun setTransientMode(enabled: Boolean) {
        repository.setTransientMode(enabled)
    }

    fun setUsbBitPerfectEnabled(enabled: Boolean) {
        repository.setUsbBitPerfectEnabled(enabled)
    }

    fun setMusicOnlyTrackingEnabled(enabled: Boolean) {
        repository.setMusicOnlyTracking(enabled)
        engineController.triggerImmediateSessionSync()
    }

    fun openNotificationAccessSettings() {
        MusicAppTracker.openNotificationAccessSettings(getApplication())
    }

    fun refreshNotificationAndSessions() {
        MusicAppTracker.isNotificationListenerEnabled(getApplication())
        engineController.triggerImmediateSessionSync()
        val s = uiState.value
        EqualizerNotificationHelper.updateOrCancelPersistentNotification(
            context = getApplication(),
            masterEnabled = s.masterEnabled,
            presetName = s.activePresetName,
            profileTitle = "",
            routeLabel = s.activeRoute.label,
            engineShortTitle = s.engineMode.shortTitle,
            musicSummary = if (s.musicOnlyTrackingEnabled) {
                s.diagnostics.trackedMusicSummary
            } else {
                "All Audio Sessions"
            }
        )
    }

    fun setThemeMode(mode: String) {
        repository.setThemeMode(mode)
    }

    fun setSeedHex(hex: String, fromWallpaper: Boolean = false) {
        repository.setSeedHex(hex, fromWallpaper)
    }

    fun openSavePresetDialog() {
        _showSavePresetDialog.value = true
    }

    fun dismissSavePresetDialog() {
        _showSavePresetDialog.value = false
    }

    fun saveCustomPreset(name: String) {
        val s = uiState.value
        repository.saveNewCustomPreset(
            name = name,
            gainsDb = s.bands.map { it.gainDb },
            preampDb = s.preampDb,
            highPassHz = s.highPassHz
        )
        _showSavePresetDialog.value = false
    }

    fun openCalibrationSheet() {
        _showCalibrationSheet.value = true
    }

    fun dismissCalibrationSheet() {
        _showCalibrationSheet.value = false
    }

    fun openSettingsSheet() {
        _showSettingsSheet.value = true
    }

    fun dismissSettingsSheet() {
        _showSettingsSheet.value = false
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun attachExternalAudioSession(sessionId: Int) {
        engineController.attachExternalAudioSession(sessionId)
    }

    fun setMasterEnabled(enabled: Boolean) {
        toggleMaster(enabled)
    }

    fun selectPreset(id: String) {
        repository.presets.value.firstOrNull { it.id == id }?.let { selectPreset(it) }
    }

    fun deleteCustomPreset(presetId: String) {
        repository.deleteCustomPreset(presetId)
    }

    fun setEqEnabled(enabled: Boolean) {
        _isEqEnabled.value = enabled
    }

    fun setEditingRightChannel(isRight: Boolean) {
        _editingRightChannel.value = isRight
    }

    fun setIntensity(value: Float) {
        _intensity.value = value.coerceIn(0f, 2f)
    }

    fun setTiltBassDb(value: Float) {
        updateTiltBassDb(value)
    }

    fun setTiltTrebleDb(value: Float) {
        updateTiltTrebleDb(value)
    }

    fun setIso226Enabled(enabled: Boolean) {
        toggleIso226(enabled)
    }

    fun setAbCompare(enabled: Boolean) {
        _isAbCompare.value = enabled
    }

    fun setShowSettingsSheet(show: Boolean) {
        _showSettingsSheet.value = show
    }

    fun setShowSavePresetDialog(show: Boolean) {
        _showSavePresetDialog.value = show
    }

    fun setShowCalibrationSheet(show: Boolean) {
        _showCalibrationSheet.value = show
    }

    fun dismissStatusBanner() {
        dismissBanner()
    }

    override fun onCleared() {
        super.onCleared()
        engineController.release()
        dspMonitor.release()
    }

    private data class Intermediate1A(
        val engineMode: EngineMode,
        val masterEnabled: Boolean,
        val activeRoute: OutputRoute,
        val activeDeviceKey: String
    )

    private data class Intermediate1B(
        val presets: List<EqPreset>,
        val profile: RouteMemoryProfile,
        val hearingCurve: PersonalHearingCurve,
        val enhancerConfig: EnhancerConfig,
        val customHeadphoneProfiles: List<HeadphoneProfile> = emptyList()
    )

    private data class Intermediate1C(
        val isEqEnabled: Boolean,
        val isAbCompare: Boolean,
        val editingRightChannel: Boolean,
        val intensity: Float
    )

    private data class Intermediate3A(
        val selectedMusicPackages: Set<String>,
        val compatibility10Band: Boolean,
        val transientMode: Boolean,
        val usbBitPerfectEnabled: Boolean
    )

    private data class Intermediate3B(
        val themeMode: String,
        val seedHex: String,
        val isSeedFromWallpaper: Boolean,
        val musicOnlyTrackingEnabled: Boolean
    )

    private data class Intermediate4A(
        val autoEqCatalog: List<IndexEntry>,
        val autoEqLoading: Boolean,
        val autoEqRateLimited: Boolean,
        val autoEqOffline: Boolean
    )

    private data class Intermediate4B(
        val speakerMeas31Db: List<Float>,
        val speakerCorrection31Db: List<Float>,
        val isCalibratingSpeaker: Boolean,
        val calibrationProgress: Float
    )

    private data class Intermediate4C(
        val autoEqIndex: List<AutoEqEntry>,
        val suggestedAutoEqEntry: AutoEqEntry?,
        val autoEqClipped: Boolean,
        val autoEqUseTenBandSliders: Boolean
    )

    private data class Intermediate5A(
        val calibrationStatusText: String,
        val showSavePresetDialog: Boolean,
        val showRootWarningDialog: Boolean
    )

    private data class Intermediate5B(
        val showCalibrationSheet: Boolean,
        val showSettingsSheet: Boolean,
        val statusBannerMessage: String?
    )

    private data class PartialUiState1(
        val engineMode: EngineMode,
        val masterEnabled: Boolean,
        val activeRoute: OutputRoute,
        val activeDeviceKey: String,
        val presets: List<EqPreset>,
        val profile: RouteMemoryProfile,
        val bands: List<EqBand>,
        val rightBands: List<EqBand>,
        val hearingCurve: PersonalHearingCurve,
        val enhancerConfig: EnhancerConfig,
        val isEqEnabled: Boolean,
        val isAbCompare: Boolean,
        val editingRightChannel: Boolean,
        val intensity: Float,
        val customHeadphoneProfiles: List<HeadphoneProfile> = emptyList()
    )

    private data class PartialUiState2(
        val diagnostics: DeviceDiagnostics,
        val liveSpectrum31: List<Float>,
        val gainReduction6: List<Float>,
        val shizukuState: ShizukuState,
        val shizukuPrivilege: String
    )

    private data class PartialUiState3(
        val selectedMusicPackages: Set<String>,
        val compatibility10Band: Boolean,
        val transientMode: Boolean,
        val usbBitPerfectEnabled: Boolean,
        val musicOnlyTrackingEnabled: Boolean,
        val themeMode: String,
        val seedHex: String,
        val isSeedFromWallpaper: Boolean
    )

    private data class PartialUiState4(
        val autoEqCatalog: List<IndexEntry>,
        val autoEqLoading: Boolean,
        val autoEqRateLimited: Boolean,
        val autoEqOffline: Boolean,
        val speakerMeas31Db: List<Float>,
        val speakerCorrection31Db: List<Float>,
        val isCalibratingSpeaker: Boolean,
        val calibrationProgress: Float,
        val autoEqIndex: List<AutoEqEntry>,
        val suggestedAutoEqEntry: AutoEqEntry?,
        val autoEqClipped: Boolean,
        val autoEqUseTenBandSliders: Boolean
    )

    private data class PartialUiState5(
        val calibrationStatusText: String,
        val showSavePresetDialog: Boolean,
        val showRootWarningDialog: Boolean,
        val showCalibrationSheet: Boolean,
        val showSettingsSheet: Boolean,
        val statusBannerMessage: String?
    )
}
