package com.audioeq.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.audioeq.model.EngineMode
import com.audioeq.model.HeadphoneProfile
import com.audioeq.model.LegacyGlobalMixMode
import com.audioeq.model.OperatingMode
import com.audioeq.model.OutputRoute
import com.audioeq.model.PersonalHearingCurve
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

data class EngineRenderParams(
    val masterEnabled: Boolean = true,
    val bandGains10L: DoubleArray = DoubleArray(10),
    val bandGains10R: DoubleArray = DoubleArray(10),
    val splitLREnabled: Boolean = false,
    val balanceDb: Double = 0.0,
    val preampDb: Double = 0.0,
    val autoGainEnabled: Boolean = false,
    val limiterEnabled: Boolean = true,
    val limiterCeilingDb: Double = -0.3,
    val highPassHz: Double = 0.0,
    val iso226Enabled: Boolean = false,
    val headphoneProfile: HeadphoneProfile? = null,
    val headphoneStrength: Double = 0.8,
    val headphoneFullRange: Boolean = false,
    val tiltBassDb: Double = 0.0,
    val tiltTrebleDb: Double = 0.0,
    val speakerCalEnabled: Boolean = false,
    val speakerCalStrength: Double = 0.7,
    val speakerCorrection31Db: DoubleArray = DoubleArray(31),
    val hearingCurve: PersonalHearingCurve = PersonalHearingCurve(),
    val enhancerConfig: EnhancerConfig = EnhancerConfig(),
    val midSideWidth: Float = 1.0f,
    val vocalPresenceBoost: Float = 0.0f,
    val compatibility10Band: Boolean = false,
    val transientMode: Boolean = false,
    val usbBitPerfectEnabled: Boolean = false,
    val abxBypassCurve: Boolean = false,
    val musicOnlyTracking: Boolean = true,
    val hardwareSampleRateHz: Int = 48000,
    val legacyGlobalMixMode: LegacyGlobalMixMode = LegacyGlobalMixMode.AUTO
)

class MasterEqEngine(
    private val context: Context,
    private val onRouteOrDeviceChanged: (OutputRoute, String, String?) -> Unit,
    private val onSessionsChanged: (List<Int>, OperatingMode) -> Unit
) {

    companion object {
        val EFFECT_TYPE_HARDWARE_DSP: UUID = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")
        val EFFECT_UUID_IMPL_SW: UUID = UUID.fromString("f22224d7-a540-4161-9c9a-e53a43681a9c")
        const val EFFECT_PARAM_CP_VALUES: Int = 5
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val dspThread = HandlerThread("EqDspWorker").apply { start() }
    private val dspHandler = Handler(dspThread.looper)

    private val dirty = AtomicBoolean(false)
    private val latestParams = AtomicReference(EngineRenderParams())
    private val requestedEngineMode = AtomicReference(EngineMode.LEGACY)

    private val dpSessions = ConcurrentHashMap<Int, DynamicsProcessing>()
    private val dpBandCounts = ConcurrentHashMap<Int, Int>()
    private val desiredSessionEnabled = ConcurrentHashMap<Int, Boolean>()
    private val eqSessions = ConcurrentHashMap<Int, Equalizer>()
    private val bassBoostSessions = ConcurrentHashMap<Int, BassBoost>()
    private val loudnessSessions = ConcurrentHashMap<Int, LoudnessEnhancer>()
    private val virtualizerSessions = ConcurrentHashMap<Int, Virtualizer>()
    private val vendorBassBoostSessions = ConcurrentHashMap<Int, AudioEffect>()
    private var vendorSetParamMethod: java.lang.reflect.Method? = null
    private val hardwareEffectSessions = ConcurrentHashMap<Int, AudioEffect>()
    private val lastAppliedL = ConcurrentHashMap<Int, FloatArray>()
    private val lastAppliedR = ConcurrentHashMap<Int, FloatArray>()
    private val lastAppliedPreamp = ConcurrentHashMap<Int, Float>()
    private val lastAppliedMbcKey = ConcurrentHashMap<Int, Int>()
    private val lastAppliedLimiterKey = ConcurrentHashMap<Int, Int>()
    private val lastAppliedVirtKey = ConcurrentHashMap<Int, Int>()
    private val lastAppliedEqKey = ConcurrentHashMap<Int, Int>()
    private val lastAppliedBassKey = ConcurrentHashMap<Int, Int>()
    private val lastAppliedLoudnessMb = ConcurrentHashMap<Int, Int>()
    private val knownSessions = ConcurrentHashMap.newKeySet<Int>()

    private val consecutiveErrors = AtomicInteger(0)
    private val tickScheduled = AtomicBoolean(false)

    @Volatile
    var avgBinderWriteCostMs: Double = 0.35
        private set

    @Volatile
    var activeOperatingMode: OperatingMode = OperatingMode.LEGACY_TIER2_DP
        private set

    @Volatile
    var currentBandCount: Int = 31
        private set

    @Volatile
    var usbBitPerfectActive: Boolean = false
        private set

    @Volatile
    var hardwareEffectSupported: Boolean = false
        private set

    @Volatile
    var vendorOffloadEqualizerDetected: Boolean = false
        private set

    private val tickRunnable = object : Runnable {
        override fun run() {
            tickScheduled.set(false)
            if (!dirty.getAndSet(false)) return
            val t0 = SystemClock.elapsedRealtimeNanos()
            try {
                applyParamsOnWorker(latestParams.get())
                consecutiveErrors.set(0)
            } catch (_: Throwable) {
                val errCount = consecutiveErrors.incrementAndGet()
                if (errCount >= 3) {
                    rebuildAllSessionsInternal()
                    consecutiveErrors.set(0)
                }
            }
            val dtMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
            avgBinderWriteCostMs = 0.8 * avgBinderWriteCostMs + 0.2 * dtMs
            if (dirty.get() && tickScheduled.compareAndSet(false, true)) {
                dspHandler.postDelayed(this, 16L)
            }
        }
    }

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val sid = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (sid <= 0) return
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
            val contentType = intent.getIntExtra(
                AudioEffect.EXTRA_CONTENT_TYPE,
                AudioEffect.CONTENT_TYPE_MUSIC
            )
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    val cleanedPkg = pkg?.trim()?.takeIf { it.isNotEmpty() }
                    if (cleanedPkg == ctx.packageName || cleanedPkg?.startsWith("com.audioeq") == true) {
                        return
                    }
                    val isMusicApp = MusicAppTracker.onBroadcastSessionOpened(
                        context = ctx,
                        sessionId = sid,
                        packageName = cleanedPkg,
                        contentType = contentType
                    )
                    val isLegacy = requestedEngineMode.get() == EngineMode.LEGACY
                    if (!isLegacy && latestParams.get().musicOnlyTracking && !isMusicApp) {
                        return
                    }
                    knownSessions.add(sid)
                    dspHandler.post {
                        attachSessionInternal(sid)
                        applyParamsOnWorker(latestParams.get())
                        notifySessionsUpdated()
                    }
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    MusicAppTracker.onBroadcastSessionClosed(ctx, sid)
                    knownSessions.remove(sid)
                    dspHandler.post {
                        detachSessionInternal(sid)
                        applyParamsOnWorker(latestParams.get())
                        notifySessionsUpdated()
                    }
                }
            }
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList< android.media.AudioPlaybackConfiguration>?) {
            if (configs != null) {
                MusicAppTracker.updatePlaybackConfigurations(context, configs)
                scheduleCoalescedUpdate()
            }
        }
    }

    private val volumeAndBtReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                "android.media.VOLUME_CHANGED_ACTION" -> {
                    if (latestParams.get().iso226Enabled) {
                        scheduleCoalescedUpdate()
                    }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    dspHandler.post {
                        applyTransientMuteRamp()
                    }
                }
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            inspectOutputDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            inspectOutputDevices()
        }
    }

    init {
        hardwareEffectSupported = detectHardwareEffect()
        vendorOffloadEqualizerDetected = detectVendorOffloadEqualizer()
        registerReceiversAndCallbacks()
        knownSessions.add(0)
        dspHandler.post {
            attachSessionInternal(0)
            inspectOutputDevices()
            applyParamsOnWorker(latestParams.get())
            notifySessionsUpdated()
        }
    }

    private fun detectHardwareEffect(): Boolean {
        return try {
            val descriptors = AudioEffect.queryEffects() ?: return false
            descriptors.any { desc ->
                desc.type == EFFECT_TYPE_HARDWARE_DSP ||
                    desc.uuid == EFFECT_UUID_IMPL_SW
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun detectVendorOffloadEqualizer(): Boolean {
        return try {
            val descriptors = AudioEffect.queryEffects() ?: return false
            val eqDescs = descriptors.filter { it.type == AudioEffect.EFFECT_TYPE_EQUALIZER }
            if (eqDescs.isEmpty()) return false
            eqDescs.any { desc ->
                val impl = desc.implementor.orEmpty().lowercase(Locale.US)
                val name = desc.name.orEmpty().lowercase(Locale.US)
                impl.contains("qualcomm") ||
                    impl.contains("qti") ||
                    impl.contains("xiaomi") ||
                    impl.contains("dolby") ||
                    impl.contains("dirac") ||
                    impl.contains("mediatek") ||
                    impl.contains("mtk") ||
                    impl.contains("samsung") ||
                    impl.contains("oppo") ||
                    impl.contains("vivo") ||
                    impl.contains("oneplus") ||
                    name.contains("offload")
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun shouldUseNativeEqForSession(sid: Int, params: EngineRenderParams = latestParams.get()): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        if (sid != 0) return false
        if (requestedEngineMode.get() != EngineMode.LEGACY) return false
        return when (params.legacyGlobalMixMode) {
            LegacyGlobalMixMode.OFFLOAD_EQ -> true
            LegacyGlobalMixMode.DYNAMICS_PROCESSING -> false
            LegacyGlobalMixMode.AUTO -> false
        }
    }

    private fun registerReceiversAndCallbacks() {
        try {
            val filter = IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            }
            ContextCompat.registerReceiver(
                context,
                sessionReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {
        }

        try {
            val vFilter = IntentFilter().apply {
                addAction("android.media.VOLUME_CHANGED_ACTION")
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            }
            ContextCompat.registerReceiver(
                context,
                volumeAndBtReceiver,
                vFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {
        }

        try {
            audioManager.registerAudioDeviceCallback(deviceCallback, dspHandler)
        } catch (_: Exception) {
        }

        try {
            audioManager.registerAudioPlaybackCallback(playbackCallback, dspHandler)
            MusicAppTracker.updatePlaybackConfigurations(context, audioManager.activePlaybackConfigurations)
        } catch (_: Exception) {
        }
    }

    fun inspectOutputDevices() {
        try {
            val outs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val types = outs.map { it.type }
            val route = OutputRoute.fromDeviceTypes(types)
            val activeDev = outs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            } ?: outs.firstOrNull()

            val prodName = activeDev?.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    activeDev?.address?.takeIf { it.isNotBlank() }
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }

            val deviceKey = when (route) {
                OutputRoute.BLUETOOTH -> "bt:${addr ?: prodName ?: "default"}"
                OutputRoute.USB_DAC -> "usb:${prodName ?: "dac"}"
                OutputRoute.HEADPHONES -> "wired:3.5mm"
                OutputRoute.SPEAKER -> "speaker:builtin"
            }

            updateUsbBitPerfectRouting(route, activeDev, latestParams.get().usbBitPerfectEnabled)
            onRouteOrDeviceChanged(route, deviceKey, prodName)
        } catch (_: Exception) {
            onRouteOrDeviceChanged(OutputRoute.SPEAKER, "speaker:builtin", null)
        }
    }

    private fun updateUsbBitPerfectRouting(
        route: OutputRoute,
        usbDevice: AudioDeviceInfo?,
        wantBitPerfect: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            route == OutputRoute.USB_DAC &&
            usbDevice != null
        ) {
            try {
                val attr = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                if (wantBitPerfect) {
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(48000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                    audioManager.setPreferredMixerAttributes(
                        attr,
                        usbDevice,
                        android.media.AudioMixerAttributes.Builder(format)
                            .setMixerBehavior(android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT)
                            .build()
                    )
                    usbBitPerfectActive = true
                } else {
                    audioManager.clearPreferredMixerAttributes(attr, usbDevice)
                    usbBitPerfectActive = false
                }
            } catch (_: Throwable) {
                usbBitPerfectActive = false
            }
        } else {
            usbBitPerfectActive = false
        }
    }

    fun isUsbBitPerfectSupportedOnDevice(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return try {
            val outs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val usb = outs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE
            } ?: return false
            val attr = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .build()
            audioManager.getSupportedMixerAttributes(usb).isNotEmpty() || attr.usage == android.media.AudioAttributes.USAGE_MEDIA
        } catch (_: Throwable) {
            false
        }
    }

    @Volatile
    private var lastPrivilegeReady: Boolean = true

    fun setEngineMode(
        mode: EngineMode,
        privilegeReady: Boolean
    ) {
        lastPrivilegeReady = privilegeReady
        val prevMode = requestedEngineMode.getAndSet(mode)
        dspHandler.post {
            val wantNative0 = shouldUseNativeEqForSession(0)
            val hasDp0 = dpSessions.containsKey(0)
            val hasEq0 = eqSessions.containsKey(0)
            if (prevMode != mode && ((wantNative0 && hasDp0) || (!wantNative0 && hasEq0))) {
                detachSessionInternal(0)
                attachSessionInternal(0)
            }
            updateActiveOperatingMode()
            applyParamsOnWorker(latestParams.get())
            notifySessionsUpdated()
        }
    }

    private fun updateActiveOperatingMode() {
        val mode = requestedEngineMode.get()
        activeOperatingMode = when (mode) {
            EngineMode.ROOT -> if (lastPrivilegeReady) OperatingMode.ROOT_GLOBAL else OperatingMode.SUSPENDED_PRIVILEGED
            EngineMode.ADB_SHIZUKU -> if (lastPrivilegeReady) OperatingMode.SHIZUKU_GLOBAL else OperatingMode.SUSPENDED_PRIVILEGED
            EngineMode.LEGACY -> {
                if (dpSessions.isNotEmpty()) {
                    OperatingMode.LEGACY_TIER2_DP
                } else if (eqSessions.isNotEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    OperatingMode.LEGACY_TIER1_EQ
                } else {
                    OperatingMode.LEGACY_TIER2_DP
                }
            }
        }
    }

    fun syncDiscoveredSessions(discoveredSids: Collection<Int>) {
        dspHandler.post {
            val mode = requestedEngineMode.get()
            val all = HashSet<Int>()
            all.addAll(discoveredSids)
            if (mode == EngineMode.LEGACY) {
                all.addAll(MusicAppTracker.getAllBroadcastSessionIds())
            } else if (latestParams.get().musicOnlyTracking) {
                all.addAll(MusicAppTracker.getVerifiedMusicSessionIds())
            }
            all.add(0)
            for (sid in all) {
                knownSessions.add(sid)
                if (!dpSessions.containsKey(sid) && !eqSessions.containsKey(sid)) {
                    attachSessionInternal(sid)
                }
            }
            val toRemove = knownSessions.filter { it != 0 && it !in all }
            for (sid in toRemove) {
                knownSessions.remove(sid)
                detachSessionInternal(sid)
            }
            updateActiveOperatingMode()
            applyParamsOnWorker(latestParams.get())
            notifySessionsUpdated()
        }
    }

    fun attachSession(sid: Int) {
        if (sid <= 0) return
        MusicAppTracker.onExternalSessionOpened(context, sid, null)
        dspHandler.post {
            knownSessions.add(sid)
            if (!dpSessions.containsKey(sid) && !eqSessions.containsKey(sid)) {
                attachSessionInternal(sid)
            }
            updateActiveOperatingMode()
            applyParamsOnWorker(latestParams.get())
            notifySessionsUpdated()
        }
    }

    fun updateParams(params: EngineRenderParams) {
        val prev = latestParams.getAndSet(params)
        if (prev.compatibility10Band != params.compatibility10Band ||
            prev.transientMode != params.transientMode ||
            prev.legacyGlobalMixMode != params.legacyGlobalMixMode
        ) {
            dspHandler.post {
                rebuildAllSessionsInternal()
                applyParamsOnWorker(params)
                notifySessionsUpdated()
            }
            return
        }
        if (prev.usbBitPerfectEnabled != params.usbBitPerfectEnabled) {
            dspHandler.post { inspectOutputDevices() }
        }
        scheduleCoalescedUpdate()
    }

    private fun scheduleCoalescedUpdate() {
        dirty.set(true)
        if (tickScheduled.compareAndSet(false, true)) {
            dspHandler.postDelayed(tickRunnable, 16L)
        }
    }

    private fun attachSessionInternal(sid: Int) {
        if (sid < 0) return
        if (dpSessions.containsKey(sid) || eqSessions.containsKey(sid)) return
        val params = latestParams.get()
        val preferNativeEq = shouldUseNativeEqForSession(sid, params)

        val attachedWithControl = if (preferNativeEq) {
            tryAttachNativeEqSession(sid, params, requireControl = true) ||
                tryAttachDpSession(sid, params, requireControl = true)
        } else {
            tryAttachDpSession(sid, params, requireControl = true) ||
                tryAttachNativeEqSession(sid, params, requireControl = true)
        }

        if (!attachedWithControl) {
            // Soft fallback so ControlStatusListener can recover once conflicting system FX releases control
            if (preferNativeEq) {
                tryAttachNativeEqSession(sid, params, requireControl = false) ||
                    tryAttachDpSession(sid, params, requireControl = false)
            } else {
                tryAttachDpSession(sid, params, requireControl = false) ||
                    tryAttachNativeEqSession(sid, params, requireControl = false)
            }
        }
        updateActiveOperatingMode()
    }

    private fun tryAttachDpSession(
        sid: Int,
        params: EngineRenderParams,
        requireControl: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val candidates = if (params.compatibility10Band) intArrayOf(10) else intArrayOf(31, 20, 10)
        val priorities = intArrayOf(Int.MAX_VALUE, 100_000, 1000, 0)
        val variant = if (params.transientMode) {
            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION
        } else {
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION
        }

        for (bands in candidates) {
            val cfg = runCatching {
                val cfgBuilder = DynamicsProcessing.Config.Builder(
                    variant,
                    2,
                    true,
                    bands,
                    true,
                    6,
                    false,
                    0,
                    true
                )
                if (params.transientMode) {
                    runCatching { cfgBuilder.setPreferredFrameDuration(5.0f) }
                }
                cfgBuilder.build()
            }.getOrNull() ?: continue

            for (prio in priorities) {
                var dp: DynamicsProcessing? = null
                try {
                    dp = runCatching { DynamicsProcessing(prio, sid, cfg) }.getOrNull() ?: continue
                    val hasCtrl = runCatching { dp.hasControl() }.getOrDefault(false)
                    if (requireControl && !hasCtrl) {
                        runCatching { dp.release() }
                        continue
                    }

                    dp.setControlStatusListener { _, controlGranted ->
                        if (controlGranted) {
                            dspHandler.post {
                                lastAppliedL.remove(sid)
                                lastAppliedR.remove(sid)
                                lastAppliedPreamp.remove(sid)
                                lastAppliedMbcKey.remove(sid)
                                lastAppliedLimiterKey.remove(sid)
                                scheduleCoalescedUpdate()
                            }
                        }
                    }
                    dp.setEnableStatusListener { _, isEnabled ->
                        if (!isEnabled && desiredSessionEnabled[sid] == true) {
                            dspHandler.post {
                                runCatching { dpSessions[sid]?.enabled = true }
                                scheduleCoalescedUpdate()
                            }
                        }
                    }

                    for (b in 0 until 6) {
                        val mbcBand = DynamicsProcessing.MbcBand(
                            false,
                            Dsp.MBC_CUTOFFS[b],
                            Dsp.MBC_ATTACK_MS[b],
                            Dsp.MBC_RELEASE_MS[b],
                            1.0f,
                            0.0f,
                            6.0f,
                            -90.0f,
                            1.0f,
                            0.0f,
                            0.0f
                        )
                        dp.setMbcBandAllChannelsTo(b, mbcBand)
                    }

                    desiredSessionEnabled[sid] = params.masterEnabled
                    if (hasCtrl) {
                        dp.enabled = params.masterEnabled
                    }
                    dpSessions[sid] = dp
                    dpBandCounts[sid] = bands
                    currentBandCount = dpBandCounts[0] ?: bands
                    lastAppliedL[sid] = FloatArray(bands) { Float.NaN }
                    lastAppliedR[sid] = FloatArray(bands) { Float.NaN }
                    lastAppliedPreamp.remove(sid)
                    lastAppliedMbcKey.remove(sid)
                    lastAppliedLimiterKey.remove(sid)
                    return true
                } catch (_: Throwable) {
                    runCatching { dp?.release() }
                }
            }
        }
        return false
    }

    private fun tryAttachNativeEqSession(
        sid: Int,
        params: EngineRenderParams,
        requireControl: Boolean
    ): Boolean {
        val priorities = intArrayOf(Int.MAX_VALUE, 100_000, 1000, 0)
        for (prio in priorities) {
            var eq: Equalizer? = null
            try {
                eq = runCatching { Equalizer(prio, sid) }.getOrNull() ?: continue
                val hasCtrl = runCatching { eq.hasControl() }.getOrDefault(false)
                if (requireControl && !hasCtrl) {
                    runCatching { eq.release() }
                    continue
                }
                val nBands = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
                if (nBands <= 0) {
                    runCatching { eq.release() }
                    continue
                }

                eq.setControlStatusListener { _, controlGranted ->
                    if (controlGranted) {
                        dspHandler.post {
                            lastAppliedEqKey.remove(sid)
                            lastAppliedBassKey.remove(sid)
                            lastAppliedLoudnessMb.remove(sid)
                            scheduleCoalescedUpdate()
                        }
                    }
                }
                eq.setEnableStatusListener { _, isEnabled ->
                    if (!isEnabled && desiredSessionEnabled[sid] == true) {
                        dspHandler.post {
                            runCatching { eqSessions[sid]?.enabled = true }
                            scheduleCoalescedUpdate()
                        }
                    }
                }

                desiredSessionEnabled[sid] = params.masterEnabled
                if (hasCtrl) {
                    eq.enabled = params.masterEnabled
                }
                eqSessions[sid] = eq

                runCatching {
                    var bb: BassBoost? = null
                    for (bPrio in priorities) {
                        val cand = runCatching { BassBoost(bPrio, sid) }.getOrNull() ?: continue
                        if (runCatching { cand.hasControl() }.getOrDefault(false)) {
                            bb = cand
                            break
                        }
                        runCatching { cand.release() }
                    }
                    if (bb != null) {
                        bb.enabled = false
                        bassBoostSessions[sid] = bb
                    }
                }

                runCatching {
                    val le = LoudnessEnhancer(sid)
                    if (runCatching { le.hasControl() }.getOrDefault(true)) {
                        le.enabled = false
                        loudnessSessions[sid] = le
                    } else {
                        runCatching { le.release() }
                    }
                }

                tryAttachHardwareEffectSession(sid)
                return true
            } catch (_: Throwable) {
                runCatching { eq?.release() }
            }
        }
        return false
    }

    private fun tryAttachHardwareEffectSession(sid: Int) {
        try {
            val descriptors = AudioEffect.queryEffects()
            val ctor = AudioEffect::class.java.getConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }
            if (vendorSetParamMethod == null) {
                vendorSetParamMethod = AudioEffect::class.java.getMethod(
                    "setParameter",
                    Int::class.javaPrimitiveType,
                    ByteArray::class.java
                ).apply { isAccessible = true }
            }
            if (descriptors?.any { it.uuid == EnhancerMath.UUID_BASS_BOOST_MZ } == true) {
                val vBb = ctor.newInstance(
                    AudioEffect.EFFECT_TYPE_BASS_BOOST,
                    EnhancerMath.UUID_BASS_BOOST_MZ,
                    0,
                    sid
                )
                vendorBassBoostSessions[sid] = vBb
            }
            if (hardwareEffectSupported) {
                val fx = ctor.newInstance(EFFECT_TYPE_HARDWARE_DSP, EFFECT_UUID_IMPL_SW, 0, sid)
                hardwareEffectSessions[sid] = fx
            }
        } catch (_: Throwable) {
        }
    }

    private fun detachSessionInternal(sid: Int) {
        dpSessions.remove(sid)?.let {
            try {
                it.release()
            } catch (_: Throwable) {
            }
        }
        dpBandCounts.remove(sid)
        desiredSessionEnabled.remove(sid)
        eqSessions.remove(sid)?.let {
            try {
                it.release()
            } catch (_: Throwable) {
            }
        }
        bassBoostSessions.remove(sid)?.let {
            try {
                it.release()
            } catch (_: Throwable) {
            }
        }
        loudnessSessions.remove(sid)?.let {
            try {
                it.release()
            } catch (_: Throwable) {
            }
        }
        virtualizerSessions.remove(sid)?.let {
            try {
                it.release()
            } catch (_: Throwable) {
            }
        }
        vendorBassBoostSessions.remove(sid)?.let {
            try {
                it.release()
            } catch (_: Throwable) {
            }
        }
        hardwareEffectSessions.remove(sid)?.let {
            try {
                it.release()
            } catch (_: Throwable) {
            }
        }
        lastAppliedL.remove(sid)
        lastAppliedR.remove(sid)
        lastAppliedPreamp.remove(sid)
        lastAppliedMbcKey.remove(sid)
        lastAppliedLimiterKey.remove(sid)
        lastAppliedVirtKey.remove(sid)
        lastAppliedEqKey.remove(sid)
        lastAppliedBassKey.remove(sid)
        lastAppliedLoudnessMb.remove(sid)
    }

    private fun rebuildAllSessionsInternal() {
        val currentSids = ArrayList(knownSessions)
        for (sid in currentSids) {
            detachSessionInternal(sid)
        }
        for (sid in currentSids) {
            attachSessionInternal(sid)
        }
    }

    private fun applyTransientMuteRamp() {
        for ((_, dp) in dpSessions) {
            try {
                dp.setInputGainAllChannelsTo(-24.0f)
            } catch (_: Throwable) {
            }
        }
        dspHandler.postDelayed({
            scheduleCoalescedUpdate()
        }, 120L)
    }

    private fun map31ToSessionBands(
        target31: DoubleArray,
        bandCount: Int,
        effectiveEnabled: Boolean
    ): Pair<FloatArray, FloatArray> {
        return when (bandCount) {
            31 -> {
                Dsp.RENDER_CUT to FloatArray(31) { k ->
                    if (!effectiveEnabled) 0.0f else target31[k].toFloat()
                }
            }
            10 -> {
                Dsp.FALLBACK_10_CUT to FloatArray(10) { k ->
                    if (!effectiveEnabled) 0.0f
                    else {
                        val nearest31 = Dsp.F_DP.indices.minByOrNull { abs(ln(Dsp.F_DP[it]) - ln(Dsp.F_USER[k])) } ?: 0
                        target31[nearest31].toFloat()
                    }
                }
            }
            else -> {
                val cutoffs = FloatArray(bandCount)
                val gains = FloatArray(bandCount)
                for (k in 0 until bandCount) {
                    val renderIdx = if (bandCount <= 1) 0 else ((k.toDouble() / (bandCount - 1)) * 30.0).roundToInt().coerceIn(0, 30)
                    cutoffs[k] = Dsp.RENDER_CUT[renderIdx]
                    gains[k] = if (!effectiveEnabled) 0.0f else target31[renderIdx].toFloat()
                }
                cutoffs to gains
            }
        }
    }

    private fun applyParamsOnWorker(params: EngineRenderParams) {
        val isSuspended = usbBitPerfectActive || params.abxBypassCurve
        val effectiveEnabled = params.masterEnabled && !isSuspended

        val volIdx = try {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        } catch (_: Exception) {
            10
        }
        val volMax = try {
            max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        } catch (_: Exception) {
            15
        }

        val activeRoute = try {
            detectCurrentRoute(audioManager)
        } catch (_: Throwable) {
            OutputRoute.SPEAKER
        }
        val isSpeakerRoute = (activeRoute == OutputRoute.SPEAKER)
        val effectiveHpProfile = params.headphoneProfile

        val calCurve = DoubleArray(31)
        val hpCurve = Dsp.computeHeadphoneCorrection31(
            profile = effectiveHpProfile,
            strength = params.headphoneStrength,
            fullRange = params.headphoneFullRange,
            tiltBassDb = params.tiltBassDb,
            tiltTrebleDb = params.tiltTrebleDb
        )
        for (i in 0 until 31) {
            calCurve[i] += hpCurve[i]
        }
        if (params.speakerCalEnabled && isSpeakerRoute && params.speakerCorrection31Db.size == 31) {
            for (i in 0 until 31) {
                calCurve[i] += params.speakerCorrection31Db[i] * params.speakerCalStrength.coerceIn(0.0, 1.0)
            }
        }

        val cfg = params.enhancerConfig
        val endpointSpec = EnhancerDsp.resolveEndpointSpec(
            profile = cfg.profile,
            route = activeRoute,
            deepBassOverride = cfg.deepBassEnabled
        )
        val sampleRateHz = params.hardwareSampleRateHz.coerceIn(44100, 192000).toDouble()

        val enhancerCurve31 = EnhancerDsp.computeCombinedEnhancer31(
            config = cfg,
            isSpeaker = isSpeakerRoute,
            sampleRate = sampleRateHz
        )
        for (i in 0 until 31) {
            calCurve[i] = (calCurve[i] + enhancerCurve31[i]).coerceIn(-15.0, 15.0)
        }

        val hearingL31 = interpolateHearingTo31(params.hearingCurve, isRight = false)
        val hearingR31 = interpolateHearingTo31(params.hearingCurve, isRight = true)
        val rawRight10 = if (params.splitLREnabled) params.bandGains10R else params.bandGains10L
        val deepBassShiftL31 = EnhancerDsp.computeDeepBassShiftDelta31(params.bandGains10L, cfg.deepBassEnabled)
        val deepBassShiftR31 = EnhancerDsp.computeDeepBassShiftDelta31(rawRight10, cfg.deepBassEnabled)

        val calL = DoubleArray(31) {
            (calCurve[it] + hearingL31[it] + deepBassShiftL31[it]).coerceIn(-15.0, 15.0)
        }
        val calR = DoubleArray(31) {
            (calCurve[it] + hearingR31[it] + deepBassShiftR31[it]).coerceIn(-15.0, 15.0)
        }

        val targetL31 = Dsp.compute31BandTargets(
            userGains10 = params.bandGains10L,
            cal31 = calL,
            hpFc = params.highPassHz,
            iso226On = params.iso226Enabled,
            volIdx = volIdx,
            volMax = volMax
        )
        val targetR31 = Dsp.compute31BandTargets(
            userGains10 = rawRight10,
            cal31 = calR,
            hpFc = params.highPassHz,
            iso226On = params.iso226Enabled,
            volIdx = volIdx,
            volMax = volMax
        )

        val bal = params.balanceDb.coerceIn(-6.0, 6.0)
        val trimL = if (bal > 0.0) -bal else 0.0
        val trimR = if (bal < 0.0) bal else 0.0
        val hasActiveAutoEq = effectiveHpProfile != null &&
            effectiveHpProfile.id != "none" &&
            (effectiveHpProfile.peqFilters.isNotEmpty() ||
                effectiveHpProfile.graphicPoints.isNotEmpty() ||
                effectiveHpProfile.correction31Db.any { abs(it) > 0.01f })
        val autoEqSafePreamp = if (
            effectiveHpProfile != null &&
            effectiveHpProfile.id != "none" &&
            effectiveHpProfile.peqFilters.isNotEmpty()
        ) {
            val autoProf = AutoEqMapper.fromPeqFilters(effectiveHpProfile.peqFilters)
            AutoEqMapper.safePreampDb(autoProf) * params.headphoneStrength.coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val avgPinkGain = 0.5 * (Dsp.pinkGainDb(targetL31) + Dsp.pinkGainDb(targetR31))
        val isFixedBandAutoEq = effectiveHpProfile != null && effectiveHpProfile.id.startsWith("autoeq_fb_")
        val isAutoPopulatedPreamp = (hasActiveAutoEq && abs(params.preampDb - autoEqSafePreamp) < 0.35) ||
            (isFixedBandAutoEq && params.preampDb < -0.5)
        val basePreamp = if (params.limiterEnabled) {
            val manualPreamp = if (isAutoPopulatedPreamp) 0.0 else params.preampDb.coerceIn(-12.0, 6.0)
            val loudnessMakeup = if (params.autoGainEnabled) {
                (-avgPinkGain).coerceIn(-6.0, 6.0)
            } else if (avgPinkGain < -0.1) {
                (-avgPinkGain).coerceIn(0.0, 4.5)
            } else {
                0.0
            }
            (manualPreamp + loudnessMakeup).coerceIn(-12.0, 6.0)
        } else {
            val nonLimPreamp = kotlin.math.min(params.preampDb.coerceIn(-12.0, 6.0), autoEqSafePreamp)
            val loudnessMakeup = if (params.autoGainEnabled && avgPinkGain < -0.1) {
                (-avgPinkGain).coerceIn(0.0, 3.0)
            } else {
                0.0
            }
            (nonLimPreamp + loudnessMakeup).coerceIn(-12.0, 6.0)
        }
        val volmaxMakeup = if (cfg.profile != AudioProfile.OFF) {
            endpointSpec.volmaxMakeupDb.toDouble().coerceAtLeast(0.0)
        } else {
            0.0
        }

        val leftInputGain = if (effectiveEnabled) (basePreamp + trimL + volmaxMakeup).coerceIn(-12.0, 6.0).toFloat() else 0.0f
        val rightInputGain = if (effectiveEnabled) (basePreamp + trimR + volmaxMakeup).coerceIn(-12.0, 6.0).toFloat() else 0.0f

        val hasDialogueMbc = cfg.dialogueEnhancerEnabled
        val deFactor = if (cfg.dialogueEnhancerEnabled) {
            cfg.dialogueAmount.coerceIn(0, 16) / 16.0f
        } else 0f

        val currentMode = requestedEngineMode.get()
        val hasGlobalSession = dpSessions.containsKey(0) || eqSessions.containsKey(0)

        val effectiveLimiterCeilingDb = if (isSpeakerRoute) {
            minOf(params.limiterCeilingDb.toFloat(), -0.3f).coerceIn(-3.0f, -0.1f)
        } else {
            params.limiterCeilingDb.toFloat().coerceIn(-3.0f, -0.1f)
        }

        fun hasAttachedControllingSession(): Boolean {
            return knownSessions.any { otherSid ->
                otherSid > 0 && (
                    dpSessions[otherSid]?.let { runCatching { it.hasControl() }.getOrDefault(false) } == true ||
                        eqSessions[otherSid]?.let { runCatching { it.hasControl() }.getOrDefault(false) } == true
                    )
            }
        }

        fun resolveSessionEnabled(sid: Int): Boolean {
            if (!effectiveEnabled) return false
            if (currentMode == EngineMode.LEGACY) {
                if (sid > 0) return true
                return !hasAttachedControllingSession() || MusicAppTracker.hasActiveNonSessionMediaPlayback()
            }
            if (!params.musicOnlyTracking && hasGlobalSession) {
                return sid == 0
            }
            return sid > 0
        }

        for ((sid, dp) in dpSessions) {
            try {
                val sessionEnabled = resolveSessionEnabled(sid)
                desiredSessionEnabled[sid] = sessionEnabled
                if (!runCatching { dp.hasControl() }.getOrDefault(false)) {
                    lastAppliedL.remove(sid)
                    lastAppliedR.remove(sid)
                    lastAppliedPreamp.remove(sid)
                    continue
                }
                if (dp.enabled != sessionEnabled) {
                    dp.enabled = sessionEnabled
                }
                val prevPre = lastAppliedPreamp[sid]
                val preKey = leftInputGain * 100f + rightInputGain
                if (prevPre == null || abs(prevPre - preKey) >= 0.02f) {
                    if (!params.splitLREnabled && abs(leftInputGain - rightInputGain) < 0.01f) {
                        dp.setInputGainAllChannelsTo(leftInputGain)
                    } else {
                        dp.setInputGainbyChannel(0, leftInputGain)
                        dp.setInputGainbyChannel(1, rightInputGain)
                    }
                    lastAppliedPreamp[sid] = preKey
                }

                val sessionBands = dpBandCounts[sid] ?: if (params.compatibility10Band) 10 else 31
                val (bandCutoffs, effL) = map31ToSessionBands(targetL31, sessionBands, effectiveEnabled)
                val (_, effR) = map31ToSessionBands(targetR31, sessionBands, effectiveEnabled)

                val prevL = lastAppliedL[sid]?.takeIf { it.size == sessionBands }
                    ?: FloatArray(sessionBands) { Float.NaN }.also { lastAppliedL[sid] = it }
                val prevR = lastAppliedR[sid]?.takeIf { it.size == sessionBands }
                    ?: FloatArray(sessionBands) { Float.NaN }.also { lastAppliedR[sid] = it }

                for (k in 0 until sessionBands) {
                    if (!params.splitLREnabled) {
                        if (prevL[k].isNaN() || abs(prevL[k] - effL[k]) >= 0.05f ||
                            prevR[k].isNaN() || abs(prevR[k] - effL[k]) >= 0.05f
                        ) {
                            val band = DynamicsProcessing.EqBand(true, bandCutoffs[k], effL[k])
                            dp.setPreEqBandAllChannelsTo(k, band)
                            prevL[k] = effL[k]
                            prevR[k] = effL[k]
                        }
                    } else {
                        if (prevL[k].isNaN() || abs(prevL[k] - effL[k]) >= 0.05f) {
                            val band = DynamicsProcessing.EqBand(true, bandCutoffs[k], effL[k])
                            dp.setPreEqBandByChannelIndex(0, k, band)
                            prevL[k] = effL[k]
                        }
                        if (prevR[k].isNaN() || abs(prevR[k] - effR[k]) >= 0.05f) {
                            val band = DynamicsProcessing.EqBand(true, bandCutoffs[k], effR[k])
                            dp.setPreEqBandByChannelIndex(1, k, band)
                            prevR[k] = effR[k]
                        }
                    }
                }

                val dialogueActive = sessionEnabled && hasDialogueMbc
                val mbcKey = if (dialogueActive) (cfg.dialogueAmount + 100) else 0
                if (lastAppliedMbcKey[sid] != mbcKey) {
                    for (b in 0 until 6) {
                        val isBandActive = dialogueActive && b in 2..3
                        val mbcBand = if (isBandActive) {
                            DynamicsProcessing.MbcBand(
                                true,
                                Dsp.MBC_CUTOFFS[b],
                                2.5f,
                                55.0f,
                                1.0f + (0.5f * deFactor),
                                -1.5f,
                                8.0f,
                                -75.0f,
                                1.0f,
                                0.0f,
                                0.8f * deFactor
                            )
                        } else {
                            DynamicsProcessing.MbcBand(
                                false,
                                Dsp.MBC_CUTOFFS[b],
                                Dsp.MBC_ATTACK_MS[b],
                                Dsp.MBC_RELEASE_MS[b],
                                1.0f,
                                0.0f,
                                6.0f,
                                -90.0f,
                                1.0f,
                                0.0f,
                                0.0f
                            )
                        }
                        dp.setMbcBandAllChannelsTo(b, mbcBand)
                    }
                    lastAppliedMbcKey[sid] = mbcKey
                }

                val limActive = params.limiterEnabled && sessionEnabled
                val limKey = (if (limActive) 1 else 0) * 10000 + (effectiveLimiterCeilingDb * 100f).roundToInt()
                if (lastAppliedLimiterKey[sid] != limKey) {
                    val lim = DynamicsProcessing.Limiter(
                        true,
                        limActive,
                        0,
                        Dsp.LIM_ATTACK_MS,
                        Dsp.LIM_RELEASE_MS,
                        6.0f,
                        effectiveLimiterCeilingDb,
                        0.0f
                    )
                    dp.setLimiterAllChannelsTo(lim)
                    lastAppliedLimiterKey[sid] = limKey
                }
            } catch (_: Throwable) {
            }
        }

        val surroundContribution = if (cfg.surroundEnabled) cfg.surroundWidth.coerceIn(0f, 1f) else 0f
        val manualWidthContribution = (params.midSideWidth - 1.0f).coerceIn(0f, 1f)
        val combinedVirtNorm = maxOf(surroundContribution, manualWidthContribution)
        val virtStrength = (combinedVirtNorm * 1000f).roundToInt().coerceIn(0, 1000)

        if (virtStrength > 0 && effectiveEnabled) {
            for (sid in dpSessions.keys + eqSessions.keys) {
                if (!resolveSessionEnabled(sid)) continue
                if (!virtualizerSessions.containsKey(sid)) {
                    runCatching {
                        virtualizerSessions[sid] = Virtualizer(0, sid)
                    }
                }
            }
        }

        for ((sid, virt) in virtualizerSessions) {
            try {
                val sessionEnabled = resolveSessionEnabled(sid)
                val wantVirt = sessionEnabled && virtStrength > 0
                val virtKey = (if (wantVirt) virtStrength else -1) * 10 + (if (isSpeakerRoute) 1 else 0)
                if (lastAppliedVirtKey[sid] == virtKey) continue
                lastAppliedVirtKey[sid] = virtKey

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && wantVirt) {
                    val vMode = if (isSpeakerRoute) {
                        Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL
                    } else {
                        Virtualizer.VIRTUALIZATION_MODE_BINAURAL
                    }
                    try {
                        virt.forceVirtualizationMode(vMode)
                    } catch (_: Throwable) {
                    }
                }
                if (virt.strengthSupported) {
                    virt.setStrength(if (wantVirt) virtStrength.toShort() else 0)
                } else {
                    virt.setStrength(if (wantVirt) 1000 else 0)
                }
                if (virt.enabled != wantVirt) {
                    virt.enabled = wantVirt
                }
            } catch (_: Throwable) {
            }
        }

        for ((_, vFx) in vendorBassBoostSessions) {
            try {
                if (vFx.enabled) vFx.enabled = false
            } catch (_: Throwable) {
            }
        }

        for ((sid, le) in loudnessSessions) {
            if (dpSessions.containsKey(sid) || !eqSessions.containsKey(sid)) {
                try {
                    if (le.enabled) le.enabled = false
                    if (lastAppliedLoudnessMb[sid] != 0) {
                        le.setTargetGain(0)
                        lastAppliedLoudnessMb[sid] = 0
                    }
                } catch (_: Throwable) {
                }
            }
        }

        if (hardwareEffectSessions.isNotEmpty()) {
            val geq20Db = EnhancerDsp.interpolate10To20Logarithmic(
                gains10 = params.bandGains10L,
                deepBass = endpointSpec.deepBassActive
            )
            val autoProf = if (
                effectiveHpProfile != null &&
                effectiveHpProfile.id != "none" &&
                effectiveHpProfile.peqFilters.isNotEmpty()
            ) {
                AutoEqMapper.fromPeqFilters(effectiveHpProfile.peqFilters)
            } else {
                null
            }
            val freqs20 = if (endpointSpec.deepBassActive) {
                EnhancerDsp.IEQ_FREQS_20_DEEP_BASS
            } else {
                EnhancerDsp.IEQ_FREQS_20
            }
            val mu = (cfg.ieqAmount.coerceIn(0, 16) / 16.0)
            val ieqTargetsQ4 = cfg.ieqPreset.targetsQ4
            val combined20Db = DoubleArray(20) { k ->
                val ieqDb = if (cfg.ieqPreset != IeqPreset.OFF) (ieqTargetsQ4[k] / 16.0) * mu else 0.0
                val fHz = freqs20[k].coerceAtLeast(20).toDouble()
                val autoEqDb = if (autoProf != null) {
                    AutoEqMapper.responseDb(autoProf, fHz) * params.headphoneStrength.coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                (geq20Db[k] + ieqDb + autoEqDb).coerceIn(-12.0, 12.0)
            }
            val gains20Q4 = if (effectiveEnabled) {
                EnhancerDsp.toQ4RegisterValues(combined20Db)
            } else {
                IntArray(20) { 0 }
            }
            val payload = EnhancerDsp.buildHardwareRegisterPayload(
                gains20Q4 = gains20Q4,
                ieqPresetIndex = cfg.ieqPreset.ordinal,
                deepBassEnabled = endpointSpec.deepBassActive,
                peakQ8 = endpointSpec.peakQ8,
                volmaxQ4 = endpointSpec.volmaxQ4
            )
            for ((sid, hwFx) in hardwareEffectSessions) {
                try {
                    val dpHandlesSession = dpSessions.containsKey(sid)
                    val eqHandlesSession = eqSessions.containsKey(sid)
                    val sessionEnabled = if (dpHandlesSession || eqHandlesSession) false else resolveSessionEnabled(sid)
                    if (hwFx.enabled != sessionEnabled) {
                        hwFx.enabled = sessionEnabled
                    }
                    if (sessionEnabled) {
                        val setParamMethod = vendorSetParamMethod ?: AudioEffect::class.java.getMethod(
                            "setParameter",
                            Int::class.javaPrimitiveType,
                            ByteArray::class.java
                        ).apply { isAccessible = true }.also { vendorSetParamMethod = it }
                        setParamMethod.invoke(hwFx, EFFECT_PARAM_CP_VALUES, payload)
                    }
                } catch (_: Throwable) {
                }
            }
        }

        val desiredBassStrength = if (effectiveEnabled && (cfg.bassEnabled || cfg.deepBassEnabled || params.tiltBassDb > 0.5 || endpointSpec.virtualBassAuto)) {
            val rawBass = (if (cfg.bassEnabled) cfg.bassBoostDb * 105f else 0f) +
                (if (cfg.deepBassEnabled) 180f else 0f) +
                (if (params.tiltBassDb > 0.5) (params.tiltBassDb * 45.0).toFloat() else 0f) +
                (if (cfg.profile != AudioProfile.OFF && endpointSpec.virtualBassAuto) 120f else 0f)
            rawBass.roundToInt().coerceIn(0, 1000)
        } else {
            0
        }

        for ((sid, eq) in eqSessions) {
            val dpHandlesSession = dpSessions.containsKey(sid)
            val sessionEnabled = if (dpHandlesSession) false else resolveSessionEnabled(sid)
            desiredSessionEnabled[sid] = sessionEnabled

            runCatching {
                if (!eq.hasControl()) {
                    lastAppliedEqKey.remove(sid)
                    return@runCatching
                }
                if (eq.enabled != sessionEnabled) {
                    eq.enabled = sessionEnabled
                }
                val nBands = eq.numberOfBands.toInt()
                if (nBands <= 0) return@runCatching
                val range = runCatching { eq.bandLevelRange }.getOrElse { shortArrayOf(-1500, 1500) }
                val minMb = range.getOrElse(0) { (-1500).toShort() }.toInt()
                val maxMb = range.getOrElse(1) { 1500.toShort() }.toInt()

                val defaultCentersHz = doubleArrayOf(60.0, 230.0, 910.0, 3600.0, 14000.0)
                val bandFreqRanges = (0 until nBands).map { b ->
                    runCatching {
                        val r = eq.getBandFreqRange(b.toShort())
                        val lo = (r[0] / 1000.0).coerceAtLeast(10.0)
                        val hi = (r[1] / 1000.0).coerceAtLeast(lo + 1.0)
                        lo to hi
                    }.getOrElse {
                        val centerHz = runCatching { eq.getCenterFreq(b.toShort()) / 1000.0 }
                            .getOrElse { defaultCentersHz.getOrElse(b) { 1000.0 } }
                            .coerceAtLeast(20.0)
                        (centerHz * 0.707) to (centerHz * 1.414)
                    }
                }
                val mappedMb = AutoEq.mapCorrection31ToNativeBands(
                    correction31Db = targetL31,
                    nativeBandsHz = bandFreqRanges,
                    strength = 1.0
                )
                val negativePreampMb = if (leftInputGain < -0.05f) (leftInputGain * 100f).roundToInt() else 0
                var eqKey = if (sessionEnabled) 1 else 0
                val finalBandMbs = IntArray(nBands) { b ->
                    val mb = if (sessionEnabled) (mappedMb[b] + negativePreampMb).coerceIn(minMb, maxMb) else 0
                    eqKey = 31 * eqKey + mb
                    mb
                }
                if (lastAppliedEqKey[sid] != eqKey) {
                    for (b in 0 until nBands) {
                        eq.setBandLevel(b.toShort(), finalBandMbs[b].toShort())
                    }
                    lastAppliedEqKey[sid] = eqKey
                }
            }

            bassBoostSessions[sid]?.let { bb ->
                runCatching {
                    if (!bb.hasControl()) {
                        lastAppliedBassKey.remove(sid)
                        return@runCatching
                    }
                    val wantBass = sessionEnabled && desiredBassStrength > 0
                    val bassKey = if (wantBass) desiredBassStrength else -1
                    if (lastAppliedBassKey[sid] != bassKey) {
                        if (bb.strengthSupported) {
                            bb.setStrength(if (wantBass) desiredBassStrength.toShort() else 0)
                        }
                        if (bb.enabled != wantBass) {
                            bb.enabled = wantBass
                        }
                        lastAppliedBassKey[sid] = bassKey
                    }
                }
            }

            loudnessSessions[sid]?.let { le ->
                runCatching {
                    if (!le.hasControl()) {
                        lastAppliedLoudnessMb.remove(sid)
                        return@runCatching
                    }
                    val targetGainMb = if (sessionEnabled) {
                        val posPreampMb = if (leftInputGain > 0.05f) (leftInputGain * 100f).roundToInt() else 0
                        val compMb = if (avgPinkGain < -0.1) ((-avgPinkGain).coerceIn(0.0, 4.5) * 100.0).roundToInt() else 0
                        val enhMb = if (cfg.dialogueEnhancerEnabled) (cfg.dialogueAmount * 6) else 0
                        (posPreampMb + maxOf(compMb, enhMb)).coerceIn(0, 800)
                    } else 0
                    val wantLoudness = sessionEnabled && targetGainMb > 0
                    val loudKey = if (wantLoudness) targetGainMb else 0
                    if (lastAppliedLoudnessMb[sid] != loudKey) {
                        le.setTargetGain(loudKey)
                        if (le.enabled != wantLoudness) {
                            le.enabled = wantLoudness
                        }
                        lastAppliedLoudnessMb[sid] = loudKey
                    }
                }
            }
        }
    }

    private fun interpolateHearingTo31(curve: PersonalHearingCurve, isRight: Boolean): DoubleArray {
        if (!curve.enabled) return DoubleArray(31)
        val boosts = if (isRight) curve.rightEarBoostDb else curve.leftEarBoostDb
        if (boosts.size < 6 || boosts.all { abs(it) < 0.05f }) return DoubleArray(31)
        val pts = curve.frequenciesHz.indices.map { i ->
            curve.frequenciesHz[i].toDouble() to boosts[i].toDouble().coerceIn(-6.0, 12.0)
        }
        val xs = DoubleArray(pts.size) { ln(pts[it].first) }
        val ys = DoubleArray(pts.size) { pts[it].second }
        return DoubleArray(31) { k ->
            val x = ln(Dsp.F_DP[k])
            when {
                x <= xs.first() -> ys.first()
                x >= xs.last() -> ys.last()
                else -> {
                    var idx = xs.binarySearch(x)
                    if (idx < 0) idx = -idx - 2
                    val i = idx.coerceIn(0, xs.size - 2)
                    val t = (x - xs[i]) / (xs[i + 1] - xs[i])
                    ys[i] + t * (ys[i + 1] - ys[i])
                }
            }
        }
    }

    private fun notifySessionsUpdated() {
        val activeList = (dpSessions.keys + eqSessions.keys).distinct().sorted()
        onSessionsChanged(activeList, activeOperatingMode)
    }

    fun getActiveSessionIds(): List<Int> = (dpSessions.keys + eqSessions.keys).distinct().sorted()

    fun getActiveVirtualizerCount(): Int = virtualizerSessions.size

    fun getActiveHardwareEffectCount(): Int = hardwareEffectSessions.size

    fun hasSession0Control(): Boolean {
        val dp0 = dpSessions[0]
        if (dp0 != null && runCatching { dp0.hasControl() }.getOrDefault(false)) return true
        val eq0 = eqSessions[0]
        if (eq0 != null && runCatching { eq0.hasControl() }.getOrDefault(false)) return true
        return false
    }

    fun getLegacyEngineSummary(): String {
        val dp0 = dpSessions[0]
        val eq0 = eqSessions[0]
        val dpBroadcast = dpSessions.keys.count { it > 0 }
        val eqBroadcast = eqSessions.keys.count { it > 0 }
        val mixPart = when {
            dp0 != null -> {
                val bands = dpBandCounts[0] ?: 31
                val ctrl = runCatching { dp0.hasControl() }.getOrDefault(false)
                if (ctrl) "Mix 0: DP $bands-Band (OK)" else "Mix 0: DP $bands-Band (No Control)"
            }
            eq0 != null -> {
                val bands = runCatching { eq0.numberOfBands.toInt() }.getOrDefault(5)
                val ctrl = runCatching { eq0.hasControl() }.getOrDefault(false)
                if (ctrl) "Mix 0: Native ${bands}-Band (OK)" else "Mix 0: Native ${bands}-Band (Blocked)"
            }
            else -> "Mix 0: Unavailable"
        }
        val sessionPart = if (dpBroadcast + eqBroadcast > 0) {
            " · +${dpBroadcast + eqBroadcast} App Session(s)"
        } else {
            ""
        }
        return mixPart + sessionPart
    }

    fun release() {
        try {
            context.unregisterReceiver(sessionReceiver)
        } catch (_: Exception) {
        }
        try {
            context.unregisterReceiver(volumeAndBtReceiver)
        } catch (_: Exception) {
        }
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {
        }
        try {
            audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        } catch (_: Exception) {
        }
        val sids = ArrayList(knownSessions)
        for (sid in sids) {
            detachSessionInternal(sid)
        }
        dspThread.quitSafely()
    }
}
