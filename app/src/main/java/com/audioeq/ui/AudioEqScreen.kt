package com.audioeq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioeq.model.EngineMode
import com.audioeq.model.OutputRoute
import com.audioeq.ui.components.AutoEqCard
import com.audioeq.ui.components.EnhancerCard
import com.audioeq.ui.components.EqualizerCard
import com.audioeq.ui.components.PresetsCard
import com.audioeq.ui.components.RootWarningDialog
import com.audioeq.ui.components.SavePresetDialog
import com.audioeq.ui.components.SettingsAndDiagnosticsSheet
import com.audioeq.ui.components.SoundCalibrationSheet
import com.audioeq.ui.components.StatusBanner
import com.audioeq.ui.components.ToneCard
import com.audioeq.ui.components.TopBarHeader
import com.audioeq.ui.theme.LocalMonetPalette
import com.audioeq.ui.theme.SpaceGroteskFontFamily

@Composable
fun AudioEqScreen(
    uiState: EqUiState,
    viewModel: EqViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonetPalette.current

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            palette.sLow,
            MaterialTheme.colorScheme.background
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.setShowSavePresetDialog(true) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Save preset",
                        tint = palette.onSecondaryC
                    )
                },
                text = {
                    Text(
                        text = "Save preset",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = palette.onSecondaryC
                    )
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = palette.secondaryC,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 10.dp
                ),
                modifier = Modifier
                    .padding(bottom = 8.dp, end = 4.dp)
                    .testTag("save_preset_fab")
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 88.dp)
            ) {
                item {
                    TopBarHeader(
                        masterEnabled = uiState.masterEnabled,
                        activePresetName = uiState.activePresetName,
                        selectedMode = uiState.engineMode,
                        shizukuState = uiState.shizukuState,
                        shizukuPrivilege = uiState.shizukuPrivilege,
                        rootAvailable = uiState.diagnostics.rootAvailable,
                        onToggleMaster = viewModel::setMasterEnabled,
                        onOpenSettings = { viewModel.setShowSettingsSheet(true) }
                    )
                }

                if (uiState.statusBannerMessage != null) {
                    item {
                        StatusBanner(
                            message = uiState.statusBannerMessage,
                            onDismiss = viewModel::dismissStatusBanner
                        )
                    }
                }

                item {
                    PresetsCard(
                        masterEnabled = uiState.masterEnabled,
                        presets = uiState.presets,
                        selectedPresetId = uiState.currentPresetId,
                        activePresetName = uiState.activePresetName,
                        onSelectPreset = { preset -> viewModel.selectPreset(preset) },
                        onDeleteCustomPreset = { presetId -> viewModel.deleteCustomPreset(presetId) }
                    )
                }

                item {
                    val sliderGains = if (uiState.editingRightChannel) {
                        uiState.rightBands.map { it.gainDb }
                    } else {
                        uiState.bands.map { it.gainDb }
                    }
                    EqualizerCard(
                        masterEnabled = uiState.masterEnabled,
                        isEnabled = uiState.isEqEnabled,
                        isAbCompare = uiState.isAbCompare,
                        usbBitPerfectActive = uiState.usbBitPerfectEnabled,
                        intensity = uiState.intensity,
                        activeRoute = uiState.activeRoute,
                        ceilingDb = uiState.ceilingDb,
                        sliderGainsDb = sliderGains,
                        effectiveGainsDb = uiState.effectiveGainsDb,
                        splitLREnabled = uiState.splitLREnabled,
                        editingRightChannel = uiState.editingRightChannel,
                        onToggleEnabled = { viewModel.setEqEnabled(it) },
                        onSelectEditingChannel = { viewModel.setEditingRightChannel(it) },
                        onBandGainChange = { index, gain ->
                            viewModel.updateBandGain(index, gain, isRight = uiState.editingRightChannel)
                        }
                    )
                }

                item {
                    ToneCard(
                        masterEnabled = uiState.masterEnabled,
                        tiltBassDb = uiState.tiltBassDb,
                        tiltTrebleDb = uiState.tiltTrebleDb,
                        intensity = uiState.intensity,
                        volumeContourEnabled = uiState.iso226Enabled,
                        isAbCompare = uiState.isAbCompare,
                        onTiltBassChange = viewModel::setTiltBassDb,
                        onTiltTrebleChange = viewModel::setTiltTrebleDb,
                        onIntensityChange = viewModel::setIntensity,
                        onToggleVolumeContour = viewModel::setIso226Enabled,
                        onToggleAbCompare = viewModel::setAbCompare
                    )
                }

                item {
                    EnhancerCard(
                        config = uiState.enhancerConfig,
                        activeRoute = uiState.activeRoute,
                        enabled = uiState.masterEnabled,
                        onSelectProfile = viewModel::selectAudioProfile,
                        onToggleDeepBass = viewModel::setDeepBassEnabled,
                        onToggleBass = viewModel::setBassEnabled,
                        onBassBoostChange = viewModel::setBassBoostDb,
                        onToggleVirtualBass = viewModel::setVirtualBassEnabled,
                        onVirtualBassStrengthChange = viewModel::setVirtualBassStrength,
                        onSelectIeqPreset = viewModel::selectIeqPreset,
                        onIeqAmountChange = viewModel::setIeqAmount,
                        onToggleDialogue = viewModel::setDialogueEnabled,
                        onDialogueAmountChange = viewModel::setDialogueAmount,
                        onToggleSurround = viewModel::setSurroundEnabled,
                        onSurroundWidthChange = viewModel::setSurroundWidth,
                        onResetAll = viewModel::resetEnhancers
                    )
                }

                item {
                    AutoEqCard(
                        masterEnabled = uiState.masterEnabled,
                        activeRoute = uiState.activeRoute,
                        connectedProductName = uiState.connectedProductName,
                        engineMode = uiState.engineMode,
                        operatingMode = uiState.operatingMode,
                        headphoneProfiles = uiState.headphoneProfiles,
                        selectedHeadphoneProfileId = uiState.headphoneProfileId,
                        preampDb = uiState.preampDb,
                        autoEqIndex = uiState.autoEqIndex,
                        suggestedAutoEqEntry = uiState.suggestedAutoEqEntry,
                        autoEqClipped = uiState.autoEqClipped,
                        autoEqUseTenBandSliders = uiState.autoEqUseTenBandSliders,
                        autoEqLoading = uiState.autoEqLoading,
                        onApplyAutoEqEntry = { entry -> viewModel.applyAutoEqEntry(entry) },
                        onToggleUseTenBandSliders = viewModel::setAutoEqUseTenBandSliders,
                        onClearAutoEq = viewModel::clearAutoEqProfile,
                        onDismissSuggestion = viewModel::dismissSuggestedAutoEq,
                        onRefreshDatabase = viewModel::refreshAutoEqDatabase
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (uiState.showSavePresetDialog) {
        SavePresetDialog(
            currentPreampDb = uiState.preampDb,
            currentHighPassHz = uiState.highPassHz,
            onDismiss = { viewModel.setShowSavePresetDialog(false) },
            onSave = { name -> viewModel.saveCustomPreset(name) }
        )
    }

    if (uiState.showRootWarningDialog) {
        RootWarningDialog(
            onAccept = viewModel::confirmRootWarning,
            onCancelAndFallback = viewModel::dismissRootWarningAndFallback
        )
    }

    if (uiState.showCalibrationSheet) {
        SoundCalibrationSheet(
            activeRoute = uiState.activeRoute,
            isLegacyTier1 = uiState.engineMode == EngineMode.LEGACY,
            headphoneProfiles = uiState.headphoneProfiles,
            selectedHeadphoneProfileId = uiState.headphoneProfileId,
            headphoneStrength = uiState.headphoneStrength,
            headphoneFullRange = uiState.headphoneFullRange,
            headphoneTrimmedDb = uiState.headphoneTrimmedDb,
            tiltBassDb = uiState.tiltBassDb,
            tiltTrebleDb = uiState.tiltTrebleDb,
            autoEqCatalog = uiState.autoEqCatalog,
            autoEqLoading = uiState.autoEqLoading,
            autoEqRateLimited = uiState.autoEqRateLimited,
            autoEqOffline = uiState.autoEqOffline,
            speakerCalEnabled = uiState.speakerCalEnabled,
            speakerCalStrength = uiState.speakerCalStrength,
            speakerMeas31Db = uiState.speakerMeas31Db,
            speakerCorrection31Db = uiState.speakerCorrection31Db,
            isCalibratingSpeaker = uiState.isCalibratingSpeaker,
            calibrationProgress = uiState.calibrationProgress,
            calibrationStatusText = uiState.calibrationStatusText,
            hearingCurve = uiState.hearingCurve,
            connectedProductName = uiState.connectedProductName,
            onSelectHeadphoneProfile = viewModel::selectHeadphoneProfile,
            onSelectAutoEqEntry = viewModel::selectAutoEqCatalogEntry,
            onRefreshAutoEqCatalog = { viewModel.refreshAutoEqCatalog(forceRefresh = true) },
            onHeadphoneStrengthChange = viewModel::updateHeadphoneStrength,
            onToggleHeadphoneFullRange = viewModel::setHeadphoneFullRange,
            onTiltBassChange = viewModel::updateTiltBassDb,
            onTiltTrebleChange = viewModel::updateTiltTrebleDb,
            onImportCustomText = viewModel::importCustomAutoEqText,
            onExportActiveText = viewModel::exportActiveCorrectionText,
            onToggleSpeakerCalibration = viewModel::toggleSpeakerCalibration,
            onSpeakerStrengthChange = viewModel::updateSpeakerCalStrength,
            onRunSpeakerCalibration = viewModel::runSpeakerSelfCalibration,
            onResetSpeakerCalibration = viewModel::resetSpeakerCalibration,
            onToggleHearingCurve = viewModel::setHearingCurveEnabled,
            onUpdateHearingBand = viewModel::updateHearingCurveBand,
            onPlayHearingTone = viewModel::playHearingTestTone,
            onDismiss = { viewModel.setShowCalibrationSheet(false) }
        )
    }

    if (uiState.showSettingsSheet) {
        SettingsAndDiagnosticsSheet(
            selectedMode = uiState.engineMode,
            shizukuState = uiState.shizukuState,
            shizukuPrivilege = uiState.shizukuPrivilege,
            diagnostics = uiState.diagnostics,
            splitLREnabled = uiState.splitLREnabled,
            balanceDb = uiState.balanceDb,
            usbBitPerfectEnabled = uiState.usbBitPerfectEnabled,
            compatibility10Band = uiState.compatibility10Band,
            transientMode = uiState.transientMode,
            themeMode = uiState.themeMode,
            seedHex = uiState.seedHex,
            musicOnlyTrackingEnabled = uiState.musicOnlyTrackingEnabled,
            selectedMusicPackages = uiState.selectedMusicPackages,
            onSelectMode = viewModel::selectMode,
            onShizukuAction = viewModel::handleShizukuAction,
            onToggleSplitLR = viewModel::setSplitLREnabled,
            onBalanceChange = viewModel::updateBalanceDb,
            onToggleUsbBitPerfect = viewModel::setUsbBitPerfectEnabled,
            onToggleCompatibility10Band = viewModel::setCompatibility10Band,
            onToggleTransientMode = viewModel::setTransientMode,
            onToggleMusicOnlyTracking = viewModel::setMusicOnlyTrackingEnabled,
            onToggleMusicPackage = viewModel::toggleMusicPackage,
            onAddCustomMusicPackage = viewModel::addCustomMusicPackage,
            onOpenNotificationAccess = viewModel::openNotificationAccessSettings,
            onSelectThemeMode = viewModel::setThemeMode,
            onSelectSeedHex = viewModel::setSeedHex,
            onDismiss = { viewModel.setShowSettingsSheet(false) }
        )
    }
}
