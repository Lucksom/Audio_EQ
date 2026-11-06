package com.audioeq.data

import android.content.Context
import android.content.SharedPreferences
import com.audioeq.audio.AudioProfile
import com.audioeq.audio.AutoEq
import com.audioeq.audio.EnhancerConfig
import com.audioeq.model.EngineMode
import com.audioeq.model.EqConstants
import com.audioeq.model.EqPreset
import com.audioeq.model.HeadphoneProfile
import com.audioeq.model.OutputRoute
import com.audioeq.model.PersonalHearingCurve
import com.audioeq.model.RouteMemoryProfile
import com.audioeq.ui.theme.MonetEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class EqRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("audio_eq_preferences_v5", Context.MODE_PRIVATE)
    private val dbHelper = EqualizerDatabaseHelper(context.applicationContext)

    private val _engineMode = MutableStateFlow(loadEngineMode())
    val engineMode: StateFlow<EngineMode> = _engineMode.asStateFlow()

    private val _masterEnabled = MutableStateFlow(prefs.getBoolean(KEY_MASTER_ENABLED, true))
    val masterEnabled: StateFlow<Boolean> = _masterEnabled.asStateFlow()

    private val _activeRoute = MutableStateFlow(loadActiveRoute())
    val activeRoute: StateFlow<OutputRoute> = _activeRoute.asStateFlow()

    private val _activeDeviceKey = MutableStateFlow(prefs.getString(KEY_ACTIVE_DEVICE_KEY, "speaker:builtin") ?: "speaker:builtin")
    val activeDeviceKey: StateFlow<String> = _activeDeviceKey.asStateFlow()

    private val _presets = MutableStateFlow(dbHelper.loadAllPresets())
    val presets: StateFlow<List<EqPreset>> = _presets.asStateFlow()

    private val _currentRouteProfile = MutableStateFlow(
        dbHelper.loadRouteProfile(_activeRoute.value, _activeDeviceKey.value)
    )
    val currentRouteProfile: StateFlow<RouteMemoryProfile> = _currentRouteProfile.asStateFlow()

    private val _hearingCurve = MutableStateFlow(dbHelper.loadHearingCurve())
    val hearingCurve: StateFlow<PersonalHearingCurve> = _hearingCurve.asStateFlow()

    private val _enhancerConfig = MutableStateFlow(dbHelper.loadEnhancerConfig())
    val enhancerConfig: StateFlow<EnhancerConfig> = _enhancerConfig.asStateFlow()

    private val _customHeadphoneProfiles = MutableStateFlow<List<HeadphoneProfile>>(loadCustomHeadphoneProfiles())
    val customHeadphoneProfiles: StateFlow<List<HeadphoneProfile>> = _customHeadphoneProfiles.asStateFlow()

    private val _rootWarningAccepted = MutableStateFlow(
        prefs.getBoolean(KEY_ROOT_WARNING_ACCEPTED, false)
    )
    val rootWarningAccepted: StateFlow<Boolean> = _rootWarningAccepted.asStateFlow()

    private val _selectedMusicPackages = MutableStateFlow<Set<String>>(loadSelectedMusicPackages())
    val selectedMusicPackages: StateFlow<Set<String>> = _selectedMusicPackages.asStateFlow()

    private val _compatibility10Band = MutableStateFlow(
        prefs.getBoolean(KEY_COMPAT_10_BAND, false)
    )
    val compatibility10Band: StateFlow<Boolean> = _compatibility10Band.asStateFlow()

    private val _transientMode = MutableStateFlow(
        prefs.getBoolean(KEY_TRANSIENT_MODE, false)
    )
    val transientMode: StateFlow<Boolean> = _transientMode.asStateFlow()

    private val _usbBitPerfectEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_USB_BIT_PERFECT, false)
    )
    val usbBitPerfectEnabled: StateFlow<Boolean> = _usbBitPerfectEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, "light") ?: "light"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _seedHex = MutableStateFlow(
        prefs.getString(KEY_SEED_HEX, MonetEngine.DEFAULT_SEED_HEX) ?: MonetEngine.DEFAULT_SEED_HEX
    )
    val seedHex: StateFlow<String> = _seedHex.asStateFlow()

    private val _wallpaperExtractActive = MutableStateFlow(
        prefs.getBoolean(KEY_WALLPAPER_EXTRACT, false)
    )
    val wallpaperExtractActive: StateFlow<Boolean> = _wallpaperExtractActive.asStateFlow()

    private val _musicOnlyTracking = MutableStateFlow(
        prefs.getBoolean(KEY_MUSIC_ONLY_TRACKING, true)
    )
    val musicOnlyTracking: StateFlow<Boolean> = _musicOnlyTracking.asStateFlow()

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        when (key) {
            KEY_MASTER_ENABLED -> _masterEnabled.value = sp.getBoolean(KEY_MASTER_ENABLED, true)
            KEY_MUSIC_ONLY_TRACKING -> _musicOnlyTracking.value = sp.getBoolean(KEY_MUSIC_ONLY_TRACKING, true)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(_selectedMusicPackages.value)
    }

    companion object {
        private const val KEY_ENGINE_MODE = "engine_mode"
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_ACTIVE_ROUTE = "active_route"
        private const val KEY_ACTIVE_DEVICE_KEY = "active_device_key"
        private const val KEY_ROOT_WARNING_ACCEPTED = "root_warning_accepted"
        private const val KEY_SELECTED_MUSIC_PACKAGES = "selected_music_packages"
        private const val KEY_COMPAT_10_BAND = "compat_10_band"
        private const val KEY_TRANSIENT_MODE = "transient_mode"
        private const val KEY_USB_BIT_PERFECT = "usb_bit_perfect"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SEED_HEX = "seed_hex_v2"
        private const val KEY_WALLPAPER_EXTRACT = "wallpaper_extract"
        private const val KEY_MUSIC_ONLY_TRACKING = "music_only_tracking"
    }

    private fun loadEngineMode(): EngineMode {
        val raw = prefs.getString(KEY_ENGINE_MODE, EngineMode.LEGACY.id)
        return EngineMode.fromId(raw)
    }

    private fun loadActiveRoute(): OutputRoute {
        val raw = prefs.getString(KEY_ACTIVE_ROUTE, OutputRoute.SPEAKER.id)
        return OutputRoute.fromId(raw)
    }

    fun setEngineMode(mode: EngineMode) {
        prefs.edit().putString(KEY_ENGINE_MODE, mode.id).apply()
        dbHelper.setSetting(KEY_ENGINE_MODE, mode.id)
        _engineMode.value = mode
    }

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        dbHelper.setSetting(KEY_MASTER_ENABLED, enabled.toString())
        _masterEnabled.value = enabled
    }

    fun setMusicOnlyTracking(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MUSIC_ONLY_TRACKING, enabled).apply()
        dbHelper.setSetting(KEY_MUSIC_ONLY_TRACKING, enabled.toString())
        _musicOnlyTracking.value = enabled
    }

    fun setRootWarningAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_ROOT_WARNING_ACCEPTED, accepted).apply()
        _rootWarningAccepted.value = accepted
    }

    fun setSelectedMusicPackages(pkgs: Set<String>) {
        _selectedMusicPackages.value = pkgs
        prefs.edit().putStringSet(KEY_SELECTED_MUSIC_PACKAGES, pkgs).apply()
        com.audioeq.audio.MusicAppTracker.setSelectedMusicPackages(pkgs)
    }

    fun toggleMusicPackage(pkg: String) {
        val current = _selectedMusicPackages.value.toMutableSet()
        if (current.contains(pkg)) {
            current.remove(pkg)
        } else {
            current.add(pkg)
        }
        setSelectedMusicPackages(current)
    }

    private fun loadSelectedMusicPackages(): Set<String> {
        val stored = prefs.getStringSet(KEY_SELECTED_MUSIC_PACKAGES, null)
        if (stored != null) return stored
        return com.audioeq.audio.MusicAppTracker.POPULAR_MUSIC_PACKAGES.map { it.first }.toSet()
    }

    fun setCompatibility10Band(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COMPAT_10_BAND, enabled).apply()
        _compatibility10Band.value = enabled
    }

    fun setTransientMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRANSIENT_MODE, enabled).apply()
        _transientMode.value = enabled
    }

    fun setUsbBitPerfectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USB_BIT_PERFECT, enabled).apply()
        _usbBitPerfectEnabled.value = enabled
    }

    fun setThemeMode(mode: String) {
        val normalized = mode.lowercase().takeIf { it in setOf("light", "dark", "system") } ?: "light"
        prefs.edit().putString(KEY_THEME_MODE, normalized).apply()
        dbHelper.setSetting(KEY_THEME_MODE, normalized)
        _themeMode.value = normalized
    }

    fun setSeedHex(hex: String, fromWallpaper: Boolean = false) {
        val normalized = if (hex.startsWith("#")) hex else "#$hex"
        prefs.edit()
            .putString(KEY_SEED_HEX, normalized)
            .putBoolean(KEY_WALLPAPER_EXTRACT, fromWallpaper)
            .apply()
        dbHelper.setSetting(KEY_SEED_HEX, normalized)
        _seedHex.value = normalized
        _wallpaperExtractActive.value = fromWallpaper
    }

    fun switchOutputRoute(route: OutputRoute, deviceKey: String = route.id) {
        prefs.edit()
            .putString(KEY_ACTIVE_ROUTE, route.id)
            .putString(KEY_ACTIVE_DEVICE_KEY, deviceKey)
            .apply()
        _activeRoute.value = route
        _activeDeviceKey.value = deviceKey
        _currentRouteProfile.value = dbHelper.loadRouteProfile(route, deviceKey)
    }

    fun updateCurrentRouteProfile(transform: (RouteMemoryProfile) -> RouteMemoryProfile) {
        val updated = transform(_currentRouteProfile.value)
        dbHelper.saveRouteProfile(updated)
        _currentRouteProfile.value = updated
    }

    fun updateEnhancerConfig(transform: (EnhancerConfig) -> EnhancerConfig) {
        val updated = transform(_enhancerConfig.value)
        dbHelper.saveEnhancerConfig(updated)
        _enhancerConfig.value = updated
    }

    fun updateBandGain(index: Int, gainDb: Float, editRightChannel: Boolean = false) {
        if (index !in 0 until 10) return
        val clamped = gainDb.coerceIn(EqConstants.MIN_GAIN_DB, EqConstants.MAX_GAIN_DB)
        updateCurrentRouteProfile { current ->
            if (editRightChannel && current.splitLREnabled) {
                val r = current.rightBandGainsDb.toMutableList()
                r[index] = clamped
                current.copy(presetId = "custom", rightBandGainsDb = r)
            } else {
                val l = current.bandGainsDb.toMutableList()
                l[index] = clamped
                val r = if (!current.splitLREnabled) l else current.rightBandGainsDb
                current.copy(presetId = "custom", bandGainsDb = l, rightBandGainsDb = r)
            }
        }
    }

    fun applyPreset(preset: EqPreset) {
        updateCurrentRouteProfile { current ->
            current.copy(
                presetId = preset.id,
                bandGainsDb = preset.gainsDb.take(10),
                rightBandGainsDb = if (!current.splitLREnabled) preset.gainsDb.take(10) else current.rightBandGainsDb
            )
        }
    }

    fun flattenBands() {
        val flatPreset = EqConstants.BUILT_IN_PRESETS.first()
        updateCurrentRouteProfile { current ->
            current.copy(
                presetId = flatPreset.id,
                bandGainsDb = List(10) { 0f },
                rightBandGainsDb = List(10) { 0f }
            )
        }
    }

    fun saveNewCustomPreset(
        name: String,
        gainsDb: List<Float>,
        preampDb: Float,
        highPassHz: Float
    ): EqPreset {
        val cleanName = name.trim().ifEmpty { "User Curve" }
        val newPreset = EqPreset(
            id = "user_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            gainsDb = gainsDb.take(10),
            isCustom = true,
            recommendedPreampDb = preampDb,
            recommendedHighPassHz = highPassHz
        )
        dbHelper.saveCustomPreset(newPreset)
        _presets.value = dbHelper.loadAllPresets()
        applyPreset(newPreset)
        return newPreset
    }

    fun deleteCustomPreset(presetId: String) {
        dbHelper.deleteCustomPreset(presetId)
        _presets.value = dbHelper.loadAllPresets()
        if (_currentRouteProfile.value.presetId == presetId) {
            flattenBands()
        }
    }

    fun registerCustomHeadphoneProfile(profile: HeadphoneProfile) {
        val existing = _customHeadphoneProfiles.value.filterNot { it.id == profile.id }
        val updated = existing + profile
        _customHeadphoneProfiles.value = updated
        persistCustomHeadphoneProfiles(updated)
    }

    private fun loadCustomHeadphoneProfiles(): List<HeadphoneProfile> {
        val json = prefs.getString("custom_headphone_profiles_json", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<HeadphoneProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val source = obj.optString("source", "Custom")
                val rig = obj.optString("rig", "HMS II.3")
                val corrArr = obj.optJSONArray("correction31Db")
                val corrList = if (corrArr != null) {
                    List(corrArr.length()) { corrArr.getDouble(it).toFloat() }
                } else List(31) { 0f }
                val raw = obj.optString("rawText", null)
                val peq = if (!raw.isNullOrBlank()) AutoEq.parsePeq(raw) else emptyList()
                val geq = if (!raw.isNullOrBlank() && peq.isEmpty()) AutoEq.parseGraphicEq(raw) else emptyList()
                list.add(
                    HeadphoneProfile(
                        id = id,
                        name = name,
                        source = source,
                        rig = rig,
                        peqFilters = peq,
                        graphicPoints = geq,
                        correction31Db = corrList,
                        rawText = raw
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistCustomHeadphoneProfiles(profiles: List<HeadphoneProfile>) {
        try {
            val arr = org.json.JSONArray()
            for (p in profiles) {
                val obj = org.json.JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("source", p.source)
                obj.put("rig", p.rig)
                val corrArr = org.json.JSONArray()
                p.correction31Db.forEach { corrArr.put(it.toDouble()) }
                obj.put("correction31Db", corrArr)
                if (p.rawText != null) obj.put("rawText", p.rawText)
                arr.put(obj)
            }
            prefs.edit().putString("custom_headphone_profiles_json", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun getAllHeadphoneProfiles(): List<HeadphoneProfile> {
        return EqConstants.BUILT_IN_HEADPHONE_PROFILES + _customHeadphoneProfiles.value
    }

    fun saveSpeakerCalibration(routeId: String, measured31Db: List<Float>, correction31Db: List<Float>) {
        dbHelper.saveSpeakerCalibration(routeId, measured31Db, correction31Db)
    }

    fun loadSpeakerCalibration(routeId: String): Pair<List<Float>, List<Float>>? {
        return dbHelper.loadSpeakerCalibration(routeId)
    }

    fun clearSpeakerCalibration(routeId: String) {
        dbHelper.deleteSpeakerCalibration(routeId)
    }

    fun updateHearingCurve(curve: PersonalHearingCurve) {
        dbHelper.saveHearingCurve(curve)
        _hearingCurve.value = curve
    }
}
