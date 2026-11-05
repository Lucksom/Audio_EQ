package com.audioeq.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.audioeq.audio.AudioProfile
import com.audioeq.audio.EnhancerConfig
import com.audioeq.audio.EnhancerDsp
import com.audioeq.audio.IeqPreset
import com.audioeq.model.EqConstants
import com.audioeq.model.EqPreset
import com.audioeq.model.OutputRoute
import com.audioeq.model.PersonalHearingCurve
import com.audioeq.model.RouteMemoryProfile
import org.json.JSONArray

class EqualizerDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "audio_eq_studio.db"
        const val DATABASE_VERSION = 9
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS presets (
                preset_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                is_custom INTEGER NOT NULL DEFAULT 0,
                gains_json TEXT NOT NULL,
                recommended_preamp_db REAL NOT NULL DEFAULT 0.0,
                recommended_hp_hz REAL NOT NULL DEFAULT 0.0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS route_profiles (
                device_key TEXT PRIMARY KEY,
                route_id TEXT NOT NULL,
                preset_id TEXT NOT NULL,
                gains_json TEXT NOT NULL,
                right_gains_json TEXT NOT NULL DEFAULT '[0,0,0,0,0,0,0,0,0,0]',
                split_lr_enabled INTEGER NOT NULL DEFAULT 0,
                balance_db REAL NOT NULL DEFAULT 0.0,
                preamp_db REAL NOT NULL,
                auto_gain_enabled INTEGER NOT NULL DEFAULT 0,
                limiter_enabled INTEGER NOT NULL DEFAULT 1,
                limiter_ceiling_db REAL NOT NULL DEFAULT -0.3,
                sub_bass_hp_hz REAL NOT NULL DEFAULT 0.0,
                iso226_enabled INTEGER NOT NULL DEFAULT 0,
                mid_side_width REAL NOT NULL DEFAULT 1.0,
                vocal_presence_boost REAL NOT NULL DEFAULT 0.0,
                headphone_profile_id TEXT NOT NULL DEFAULT 'none',
                headphone_strength REAL NOT NULL DEFAULT 0.8,
                headphone_full_range INTEGER NOT NULL DEFAULT 0,
                tilt_bass_db REAL NOT NULL DEFAULT 0.0,
                tilt_treble_db REAL NOT NULL DEFAULT 0.0,
                speaker_cal_enabled INTEGER NOT NULL DEFAULT 0,
                speaker_cal_strength REAL NOT NULL DEFAULT 0.7,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS speaker_calibration (
                route_id TEXT PRIMARY KEY,
                measured_31_json TEXT NOT NULL,
                correction_31_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS hearing_curve (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                enabled INTEGER NOT NULL DEFAULT 0,
                left_boost_6_json TEXT NOT NULL,
                right_boost_6_json TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS enhancer_settings (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                ieq_preset_id TEXT NOT NULL DEFAULT 'ieq_off',
                ieq_amount INTEGER NOT NULL DEFAULT 10,
                dialogue_enabled INTEGER NOT NULL DEFAULT 0,
                dialogue_amount INTEGER NOT NULL DEFAULT 8,
                bass_enabled INTEGER NOT NULL DEFAULT 0,
                bass_boost_db REAL NOT NULL DEFAULT 0.0,
                virtual_bass_enabled INTEGER NOT NULL DEFAULT 0,
                virtual_bass_strength REAL NOT NULL DEFAULT 0.5,
                deep_bass_enabled INTEGER NOT NULL DEFAULT 0,
                surround_enabled INTEGER NOT NULL DEFAULT 0,
                surround_width REAL NOT NULL DEFAULT 0.45,
                audio_profile_id TEXT NOT NULL DEFAULT 'profile_off'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_settings (
                setting_key TEXT PRIMARY KEY,
                setting_value TEXT NOT NULL
            )
            """.trimIndent()
        )

        createAndSeedSpecificationTables(db)
        seedDefaultData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS route_profiles")
            db.execSQL("DROP TABLE IF EXISTS presets")
            db.execSQL("DROP TABLE IF EXISTS app_settings")
            onCreate(db)
            return
        }
        if (oldVersion < 9) {
            db.execSQL("DROP TABLE IF EXISTS movie_profile")
            db.execSQL("DROP TABLE IF EXISTS music_profile")
            db.execSQL("DROP TABLE IF EXISTS stereo_widening")
            db.execSQL("DROP TABLE IF EXISTS reduce_reverb")
            db.execSQL("DROP TABLE IF EXISTS enhancer_settings")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS enhancer_settings (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    ieq_preset_id TEXT NOT NULL DEFAULT 'ieq_off',
                    ieq_amount INTEGER NOT NULL DEFAULT 10,
                    dialogue_enabled INTEGER NOT NULL DEFAULT 0,
                    dialogue_amount INTEGER NOT NULL DEFAULT 8,
                    bass_enabled INTEGER NOT NULL DEFAULT 0,
                    bass_boost_db REAL NOT NULL DEFAULT 0.0,
                    virtual_bass_enabled INTEGER NOT NULL DEFAULT 0,
                    virtual_bass_strength REAL NOT NULL DEFAULT 0.5,
                    deep_bass_enabled INTEGER NOT NULL DEFAULT 0,
                    surround_enabled INTEGER NOT NULL DEFAULT 0,
                    surround_width REAL NOT NULL DEFAULT 0.45,
                    audio_profile_id TEXT NOT NULL DEFAULT 'profile_off'
                )
                """.trimIndent()
            )
            seedDefaultEnhancerSettings(db)
            db.execSQL("DELETE FROM presets WHERE is_custom = 0")
            val now = System.currentTimeMillis()
            EqConstants.BUILT_IN_PRESETS.forEach { preset ->
                val values = ContentValues().apply {
                    put("preset_id", preset.id)
                    put("name", preset.name)
                    put("is_custom", 0)
                    put("gains_json", floatsToJson(preset.gainsDb))
                    put("recommended_preamp_db", preset.recommendedPreampDb)
                    put("recommended_hp_hz", preset.recommendedHighPassHz)
                    put("created_at", now)
                }
                db.insertWithOnConflict("presets", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            createAndSeedSpecificationTables(db)
        }
    }

    private fun createAndSeedSpecificationTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ieq_profiles (
                band_index INTEGER PRIMARY KEY,
                center_freq_hz INTEGER NOT NULL,
                deep_bass_freq_hz INTEGER NOT NULL DEFAULT 0,
                balanced_q4 INTEGER NOT NULL,
                balanced_db REAL NOT NULL,
                warm_q4 INTEGER NOT NULL,
                warm_db REAL NOT NULL,
                detailed_q4 INTEGER NOT NULL,
                detailed_db REAL NOT NULL,
                off_q4 INTEGER NOT NULL,
                off_db REAL NOT NULL
            )
            """.trimIndent()
        )
        try {
            db.execSQL("ALTER TABLE ieq_profiles ADD COLUMN deep_bass_freq_hz INTEGER NOT NULL DEFAULT 0")
        } catch (_: Exception) {
        }
        db.execSQL("DELETE FROM ieq_profiles")
        for (i in 0 until 20) {
            val balQ4 = IeqPreset.BALANCED.targetsQ4[i]
            val warmQ4 = IeqPreset.WARM.targetsQ4[i]
            val detQ4 = IeqPreset.DETAILED.targetsQ4[i]
            val cv = ContentValues().apply {
                put("band_index", i)
                put("center_freq_hz", EnhancerDsp.IEQ_FREQS_20[i])
                put("deep_bass_freq_hz", EnhancerDsp.IEQ_FREQS_20_DEEP_BASS[i])
                put("balanced_q4", balQ4)
                put("balanced_db", balQ4 / 16.0)
                put("warm_q4", warmQ4)
                put("warm_db", warmQ4 / 16.0)
                put("detailed_q4", detQ4)
                put("detailed_db", detQ4 / 16.0)
                put("off_q4", 0)
                put("off_db", 0.0)
            }
            db.insertWithOnConflict("ieq_profiles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dsp_mathematical_models (
                module_id TEXT PRIMARY KEY,
                module_name TEXT NOT NULL,
                domain TEXT NOT NULL,
                governing_equation TEXT NOT NULL,
                constants_json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("DELETE FROM dsp_mathematical_models")
        val modelRows = listOf(
            arrayOf(
                "three_d_audio_hrtf",
                "3D Audio (Spatializer & Virtualizer Engine)",
                "Time / Z-Domain",
                "tau=(r/c)*(theta+sin(theta)); H_c(z): beta=1-exp(-2*pi*850/fs); y_L = G_dir*L + G_cross*H_c(R[n-D]) + G_refl*S",
                """{"r_m":0.0875,"c_mps":343,"shadow_fc_hz":850,"start_band":2,"min_freq_hz":234}"""
            ),
            arrayOf(
                "dynamic_profile_mi",
                "Dynamic Profile (Media Intelligence Real-Time Classifier & Steering)",
                "Time / Frequency Domain",
                "S_dyn=1.0*P_movie*I_virt+0.35*P_music*I_eq; Target=P_music*T_bal+P_speech*T_dlg",
                """{"ieq_preset":"ieq_balanced","ieq_amount_spk":6,"ieq_amount_bt":6,"dialog_amount":4,"peak_spk_q8":240,"peak_hp_q8":256,"volmax_spk":17,"volmax_hp":32}"""
            ),
            arrayOf(
                "deep_bass_dc_shelf",
                "Deep Bass Mode (0 Hz DC Low-Shelf & 10-Band Downward Shift)",
                "Z-Domain Biquad Cascade",
                "H_shelf(z): fc=35Hz, Q=0.7071; Shift: 47->0Hz, 141->47Hz, ..., 1688->1313Hz",
                """{"fc_hz":35.0,"q":0.7071,"shift_bands":10,"volmax_spk":24,"volmax_hp":36,"volmax_bt":32}"""
            ),
            arrayOf(
                "balanced_ieq_20band",
                "Balance (20-Band Balanced IEQ Profile)",
                "Log-Frequency Biquad Cascade",
                "E(k,t)=T_bal(k)-S_bar_in(k,t); DeltaG(k,t)=clamp(mu*E(k,t), -6.0, +6.0)",
                """{"bands":20,"q4_scale":16.0,"mu_bt":0.375,"mu_spk":0.250,"clamp_db":6.0}"""
            )
        )
        for (row in modelRows) {
            val cv = ContentValues().apply {
                put("module_id", row[0])
                put("module_name", row[1])
                put("domain", row[2])
                put("governing_equation", row[3])
                put("constants_json", row[4])
            }
            db.insertWithOnConflict("dsp_mathematical_models", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun seedDefaultEnhancerSettings(db: SQLiteDatabase) {
        val cv = ContentValues().apply {
            put("id", 1)
            put("ieq_preset_id", IeqPreset.OFF.id)
            put("ieq_amount", 10)
            put("dialogue_enabled", 0)
            put("dialogue_amount", 8)
            put("bass_enabled", 0)
            put("bass_boost_db", 0.0f)
            put("virtual_bass_enabled", 0)
            put("virtual_bass_strength", 0.5f)
            put("deep_bass_enabled", 0)
            put("surround_enabled", 0)
            put("surround_width", 0.45f)
            put("audio_profile_id", AudioProfile.OFF.id)
        }
        db.insertWithOnConflict("enhancer_settings", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun seedDefaultData(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        EqConstants.BUILT_IN_PRESETS.forEach { preset ->
            val values = ContentValues().apply {
                put("preset_id", preset.id)
                put("name", preset.name)
                put("is_custom", 0)
                put("gains_json", floatsToJson(preset.gainsDb))
                put("recommended_preamp_db", preset.recommendedPreampDb)
                put("recommended_hp_hz", preset.recommendedHighPassHz)
                put("created_at", now)
            }
            db.insertWithOnConflict("presets", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }

        OutputRoute.entries.forEach { route ->
            val defaultKey = when (route) {
                OutputRoute.SPEAKER -> "speaker:builtin"
                OutputRoute.HEADPHONES -> "wired:3.5mm"
                OutputRoute.BLUETOOTH -> "bt:default"
                OutputRoute.USB_DAC -> "usb:dac"
            }
            insertDefaultRouteRow(db, route, defaultKey, now)
        }

        val hearingCv = ContentValues().apply {
            put("id", 1)
            put("enabled", 0)
            put("left_boost_6_json", floatsToJson(List(6) { 0f }))
            put("right_boost_6_json", floatsToJson(List(6) { 0f }))
        }
        db.insertWithOnConflict("hearing_curve", null, hearingCv, SQLiteDatabase.CONFLICT_REPLACE)

        seedDefaultEnhancerSettings(db)
    }

    private fun insertDefaultRouteRow(
        db: SQLiteDatabase,
        route: OutputRoute,
        deviceKey: String,
        now: Long
    ) {
        val values = ContentValues().apply {
            put("device_key", deviceKey)
            put("route_id", route.id)
            put("preset_id", "flat")
            put("gains_json", floatsToJson(List(10) { 0f }))
            put("right_gains_json", floatsToJson(List(10) { 0f }))
            put("split_lr_enabled", 0)
            put("balance_db", 0.0f)
            put("preamp_db", route.defaultSafeCutDb)
            put("auto_gain_enabled", 0)
            put("limiter_enabled", 1)
            put("limiter_ceiling_db", route.defaultLimiterCeilingDb)
            put("sub_bass_hp_hz", route.defaultHighPassHz)
            put("iso226_enabled", 0)
            put("mid_side_width", 1.0f)
            put("vocal_presence_boost", 0.0f)
            put("headphone_profile_id", "none")
            put("headphone_strength", 0.8f)
            put("headphone_full_range", 0)
            put("tilt_bass_db", 0.0f)
            put("tilt_treble_db", 0.0f)
            put("speaker_cal_enabled", 0)
            put("speaker_cal_strength", 0.7f)
            put("updated_at", now)
        }
        db.insertWithOnConflict("route_profiles", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun loadEnhancerConfig(): EnhancerConfig {
        val db = readableDatabase
        db.rawQuery(
            """
            SELECT ieq_preset_id, ieq_amount,
                   dialogue_enabled, dialogue_amount,
                   bass_enabled, bass_boost_db,
                   virtual_bass_enabled, virtual_bass_strength,
                   deep_bass_enabled, surround_enabled, surround_width,
                   audio_profile_id
            FROM enhancer_settings WHERE id = 1
            """.trimIndent(),
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val preset = IeqPreset.fromId(cursor.getString(0))
                val ieqAmt = cursor.getInt(1).coerceIn(0, 16)
                val dlgOn = cursor.getInt(2) == 1
                val dlgAmt = cursor.getInt(3).coerceIn(0, 16)
                val bassOn = cursor.getInt(4) == 1
                val bassDb = cursor.getFloat(5).coerceIn(0f, 6f)
                val vBassOn = cursor.getInt(6) == 1
                val vBassStr = cursor.getFloat(7).coerceIn(0f, 1f)
                val deepBassOn = cursor.getInt(8) == 1
                val surrOn = cursor.getInt(9) == 1
                val surrWidth = cursor.getFloat(10).coerceIn(0f, 1f)
                val prof = AudioProfile.fromId(cursor.getString(11))
                return EnhancerConfig(
                    profile = prof,
                    bassEnabled = bassOn,
                    bassBoostDb = bassDb,
                    virtualBassEnabled = vBassOn,
                    virtualBassStrength = vBassStr,
                    deepBassEnabled = deepBassOn,
                    ieqPreset = preset,
                    ieqAmount = ieqAmt,
                    dialogueEnhancerEnabled = dlgOn,
                    dialogueAmount = dlgAmt,
                    surroundEnabled = surrOn,
                    surroundWidth = surrWidth
                )
            }
        }
        return EnhancerConfig()
    }

    fun saveEnhancerConfig(config: EnhancerConfig) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", 1)
            put("ieq_preset_id", config.ieqPreset.id)
            put("ieq_amount", config.ieqAmount.coerceIn(0, 16))
            put("dialogue_enabled", if (config.dialogueEnhancerEnabled) 1 else 0)
            put("dialogue_amount", config.dialogueAmount.coerceIn(0, 16))
            put("bass_enabled", if (config.bassEnabled) 1 else 0)
            put("bass_boost_db", config.bassBoostDb.coerceIn(0f, 6f))
            put("virtual_bass_enabled", if (config.virtualBassEnabled) 1 else 0)
            put("virtual_bass_strength", config.virtualBassStrength.coerceIn(0f, 1f))
            put("deep_bass_enabled", if (config.deepBassEnabled) 1 else 0)
            put("surround_enabled", if (config.surroundEnabled) 1 else 0)
            put("surround_width", config.surroundWidth.coerceIn(0f, 1f))
            put("audio_profile_id", config.profile.id)
        }
        db.insertWithOnConflict("enhancer_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadAllPresets(): List<EqPreset> {
        val list = mutableListOf<EqPreset>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT preset_id, name, is_custom, gains_json, recommended_preamp_db, recommended_hp_hz FROM presets ORDER BY is_custom ASC, created_at ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)
                val isCustom = cursor.getInt(2) == 1
                val gains = jsonToFloats(cursor.getString(3), 10)
                val preamp = cursor.getFloat(4)
                val hp = cursor.getFloat(5)
                list.add(
                    EqPreset(
                        id = id,
                        name = name,
                        gainsDb = gains,
                        isCustom = isCustom,
                        recommendedPreampDb = preamp,
                        recommendedHighPassHz = hp
                    )
                )
            }
        }
        return if (list.isEmpty()) EqConstants.BUILT_IN_PRESETS else list
    }

    fun saveCustomPreset(preset: EqPreset) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("preset_id", preset.id)
            put("name", preset.name)
            put("is_custom", 1)
            put("gains_json", floatsToJson(preset.gainsDb))
            put("recommended_preamp_db", preset.recommendedPreampDb)
            put("recommended_hp_hz", preset.recommendedHighPassHz)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("presets", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteCustomPreset(presetId: String) {
        writableDatabase.delete("presets", "preset_id = ? AND is_custom = 1", arrayOf(presetId))
    }

    fun loadRouteProfile(route: OutputRoute, deviceKey: String = route.id): RouteMemoryProfile {
        val db = readableDatabase
        val querySql = """
            SELECT preset_id, gains_json, right_gains_json, split_lr_enabled, balance_db,
                   preamp_db, auto_gain_enabled, limiter_enabled, limiter_ceiling_db,
                   sub_bass_hp_hz, iso226_enabled, mid_side_width, vocal_presence_boost,
                   headphone_profile_id, headphone_strength, headphone_full_range,
                   tilt_bass_db, tilt_treble_db, speaker_cal_enabled, speaker_cal_strength
            FROM route_profiles WHERE device_key = ?
        """.trimIndent()

        db.rawQuery(querySql, arrayOf(deviceKey)).use { cursor ->
            if (cursor.moveToFirst()) {
                return parseRouteCursor(route, deviceKey, cursor)
            }
        }
        val fallbackKey = when (route) {
            OutputRoute.SPEAKER -> "speaker:builtin"
            OutputRoute.HEADPHONES -> "wired:3.5mm"
            OutputRoute.BLUETOOTH -> "bt:default"
            OutputRoute.USB_DAC -> "usb:dac"
        }
        db.rawQuery(querySql, arrayOf(fallbackKey)).use { cursor ->
            if (cursor.moveToFirst()) {
                return parseRouteCursor(route, deviceKey, cursor)
            }
        }
        return RouteMemoryProfile(route = route, deviceKey = deviceKey)
    }

    private fun parseRouteCursor(
        route: OutputRoute,
        deviceKey: String,
        cursor: android.database.Cursor
    ): RouteMemoryProfile {
        return RouteMemoryProfile(
            route = route,
            deviceKey = deviceKey,
            presetId = cursor.getString(0),
            bandGainsDb = jsonToFloats(cursor.getString(1), 10),
            rightBandGainsDb = jsonToFloats(cursor.getString(2), 10),
            splitLREnabled = cursor.getInt(3) == 1,
            balanceDb = cursor.getFloat(4),
            preampDb = cursor.getFloat(5),
            autoGainEnabled = cursor.getInt(6) == 1,
            limiterEnabled = cursor.getInt(7) == 1,
            limiterCeilingDb = cursor.getFloat(8),
            subBassHighPassHz = cursor.getFloat(9),
            iso226ContourEnabled = cursor.getInt(10) == 1,
            midSideWidth = cursor.getFloat(11),
            vocalPresenceBoost = cursor.getFloat(12),
            headphoneProfileId = cursor.getString(13),
            headphoneCorrectionStrength = cursor.getFloat(14),
            headphoneFullRange = cursor.getInt(15) == 1,
            tiltBassDb = cursor.getFloat(16),
            tiltTrebleDb = cursor.getFloat(17),
            speakerCalibrationEnabled = cursor.getInt(18) == 1,
            speakerCalStrength = cursor.getFloat(19)
        )
    }

    fun saveRouteProfile(profile: RouteMemoryProfile) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("device_key", profile.deviceKey)
            put("route_id", profile.route.id)
            put("preset_id", profile.presetId)
            put("gains_json", floatsToJson(profile.bandGainsDb))
            put("right_gains_json", floatsToJson(profile.rightBandGainsDb))
            put("split_lr_enabled", if (profile.splitLREnabled) 1 else 0)
            put("balance_db", profile.balanceDb)
            put("preamp_db", profile.preampDb)
            put("auto_gain_enabled", if (profile.autoGainEnabled) 1 else 0)
            put("limiter_enabled", if (profile.limiterEnabled) 1 else 0)
            put("limiter_ceiling_db", profile.limiterCeilingDb)
            put("sub_bass_hp_hz", profile.subBassHighPassHz)
            put("iso226_enabled", if (profile.iso226ContourEnabled) 1 else 0)
            put("mid_side_width", profile.midSideWidth)
            put("vocal_presence_boost", profile.vocalPresenceBoost)
            put("headphone_profile_id", profile.headphoneProfileId)
            put("headphone_strength", profile.headphoneCorrectionStrength)
            put("headphone_full_range", if (profile.headphoneFullRange) 1 else 0)
            put("tilt_bass_db", profile.tiltBassDb)
            put("tilt_treble_db", profile.tiltTrebleDb)
            put("speaker_cal_enabled", if (profile.speakerCalibrationEnabled) 1 else 0)
            put("speaker_cal_strength", profile.speakerCalStrength)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("route_profiles", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveSpeakerCalibration(
        routeId: String,
        measured31Db: List<Float>,
        correction31Db: List<Float>
    ) {
        val cv = ContentValues().apply {
            put("route_id", routeId)
            put("measured_31_json", floatsToJson(measured31Db))
            put("correction_31_json", floatsToJson(correction31Db))
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("speaker_calibration", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadSpeakerCalibration(routeId: String): Pair<List<Float>, List<Float>>? {
        readableDatabase.rawQuery(
            "SELECT measured_31_json, correction_31_json FROM speaker_calibration WHERE route_id = ?",
            arrayOf(routeId)
        ).use { c ->
            if (c.moveToFirst()) {
                val m = jsonToFloats(c.getString(0), 31)
                val corr = jsonToFloats(c.getString(1), 31)
                return m to corr
            }
        }
        return null
    }

    fun deleteSpeakerCalibration(routeId: String) {
        writableDatabase.delete("speaker_calibration", "route_id = ?", arrayOf(routeId))
    }

    fun loadHearingCurve(): PersonalHearingCurve {
        readableDatabase.rawQuery(
            "SELECT enabled, left_boost_6_json, right_boost_6_json FROM hearing_curve WHERE id = 1",
            null
        ).use { c ->
            if (c.moveToFirst()) {
                return PersonalHearingCurve(
                    enabled = c.getInt(0) == 1,
                    leftEarBoostDb = jsonToFloats(c.getString(1), 6),
                    rightEarBoostDb = jsonToFloats(c.getString(2), 6)
                )
            }
        }
        return PersonalHearingCurve()
    }

    fun saveHearingCurve(curve: PersonalHearingCurve) {
        val cv = ContentValues().apply {
            put("id", 1)
            put("enabled", if (curve.enabled) 1 else 0)
            put("left_boost_6_json", floatsToJson(curve.leftEarBoostDb))
            put("right_boost_6_json", floatsToJson(curve.rightEarBoostDb))
        }
        writableDatabase.insertWithOnConflict("hearing_curve", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSetting(key: String, defaultValue: String): String {
        val db = readableDatabase
        db.rawQuery(
            "SELECT setting_value FROM app_settings WHERE setting_key = ?",
            arrayOf(key)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return defaultValue
    }

    fun setSetting(key: String, value: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("setting_key", key)
            put("setting_value", value)
        }
        db.insertWithOnConflict("app_settings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun floatsToJson(values: List<Float>): String {
        val array = JSONArray()
        values.forEach { array.put(it.toDouble()) }
        return array.toString()
    }

    private fun jsonToFloats(json: String, expectedCount: Int): List<Float> {
        return try {
            val array = JSONArray(json)
            List(expectedCount) { index ->
                if (index < array.length()) array.getDouble(index).toFloat() else 0f
            }
        } catch (_: Exception) {
            List(expectedCount) { 0f }
        }
    }
}
