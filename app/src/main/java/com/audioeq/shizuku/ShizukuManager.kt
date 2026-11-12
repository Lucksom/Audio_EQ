package com.audioeq.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

enum class ShizukuState {
    NOT_INSTALLED,
    NOT_RUNNING,
    UNSUPPORTED_VERSION,
    NEEDS_PERMISSION,
    DENIED_PERMANENTLY,
    READY
}

object ShizukuManager {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4242

    private var appContext: Context? = null

    private val _state = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val _privilegeSummary = MutableStateFlow("none")
    val privilegeSummary: StateFlow<String> = _privilegeSummary.asStateFlow()

    @Volatile
    private var rootAccessVerified: Boolean = false

    @Volatile
    private var workingSuBinary: String? = null

    @Volatile
    private var dumpGrantAttempted: Boolean = false

    @Volatile
    private var onStateChangedListener: ((ShizukuState) -> Unit)? = null

    private val suBinaryCandidates = listOf(
        "su",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/debug_ramdisk/su",
        "/su/bin/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su"
    )

    operator fun invoke(context: Context): ShizukuManager {
        init(context)
        return this
    }

    fun setOnStateChangedListener(listener: ((ShizukuState) -> Unit)?) {
        onStateChangedListener = listener
    }

    fun refreshStatus() = refresh()

    fun isRootAvailable(): Boolean = rootAccessVerified || isShizukuRunningAsRoot()

    fun isShizukuRunningAsRoot(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
            Shizuku.getUid() == 0
    } catch (_: Throwable) {
        false
    }

    fun verifyRootAccess(timeoutMs: Long = 10_000L): Boolean {
        refresh()
        if (isShizukuRunningAsRoot()) {
            rootAccessVerified = true
            persistRootHint(true)
            _privilegeSummary.value = privilege()
            onStateChangedListener?.invoke(_state.value)
            return true
        }

        val suDirect = runSuIdCheck(timeoutMs)
        if (suDirect) {
            rootAccessVerified = true
            persistRootHint(true)
            grantDumpViaSuAsync()
            _privilegeSummary.value = privilege()
            onStateChangedListener?.invoke(_state.value)
            return true
        }

        val shizukuSu = try {
            if (_state.value == ShizukuState.READY) {
                val idOut = execIfBound("id").orEmpty()
                if (idOut.contains("uid=0")) {
                    true
                } else {
                    val suOut = execIfBound("su -c id").orEmpty()
                    suOut.contains("uid=0")
                }
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }

        if (shizukuSu) {
            rootAccessVerified = true
            persistRootHint(true)
        }
        _privilegeSummary.value = privilege()
        onStateChangedListener?.invoke(_state.value)
        return shizukuSu
    }

    private fun persistRootHint(granted: Boolean) {
        val ctx = appContext ?: return
        runCatching {
            ctx.getSharedPreferences("audio_eq_priv_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("root_verified_hint", granted)
                .apply()
        }
    }

    fun hasPreviousRootGrant(): Boolean {
        val ctx = appContext ?: return false
        return runCatching {
            ctx.getSharedPreferences("audio_eq_priv_prefs", Context.MODE_PRIVATE)
                .getBoolean("root_verified_hint", false)
        }.getOrDefault(false)
    }

    private fun runSuProcess(bin: String, command: String, useDashC: Boolean, timeoutMs: Long): Pair<Int, String>? {
        return try {
            val pb = if (useDashC) {
                ProcessBuilder(bin, "-c", command)
            } else {
                ProcessBuilder(bin)
            }
            pb.redirectErrorStream(true)
            val process = pb.start()
            runCatching {
                if (!useDashC) {
                    process.outputStream.bufferedWriter().use { w ->
                        w.write(command)
                        w.newLine()
                        w.write("exit")
                        w.newLine()
                        w.flush()
                    }
                } else {
                    process.outputStream.close()
                }
            }
            val outputHolder = StringBuilder()
            val readerThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { r ->
                        outputHolder.append(r.readText())
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                readerThread.join(150)
                null
            } else {
                readerThread.join(300)
                process.exitValue() to outputHolder.toString()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun runSuIdCheck(timeoutMs: Long): Boolean {
        val perBinTimeout = timeoutMs.coerceIn(1500L, 10_000L)
        val ordered = buildList {
            workingSuBinary?.let { add(it) }
            addAll(suBinaryCandidates)
        }.distinct()

        for (bin in ordered) {
            if (bin.startsWith("/") && !java.io.File(bin).exists()) continue
            val dashC = runSuProcess(bin, "id", useDashC = true, timeoutMs = perBinTimeout)
            if (dashC == null) {
                // Timed out waiting on su prompt or IO; avoid double-prompting another su variant
                continue
            }
            if (dashC.first == 0 && dashC.second.contains("uid=0")) {
                workingSuBinary = bin
                return true
            }
            if (dashC.first != 0) {
                val interactive = runSuProcess(bin, "id", useDashC = false, timeoutMs = perBinTimeout)
                if (interactive != null && interactive.first == 0 && interactive.second.contains("uid=0")) {
                    workingSuBinary = bin
                    return true
                }
            }
        }
        return false
    }

    fun clearRootVerification() {
        rootAccessVerified = false
        _privilegeSummary.value = privilege()
    }

    private fun execSuCommand(command: String): String? {
        val bin = workingSuBinary ?: "su"
        val res = runSuProcess(bin, command, useDashC = true, timeoutMs = 2500L)
            ?: runSuProcess(bin, command, useDashC = false, timeoutMs = 2500L)
            ?: return null
        return if (res.first == 0) res.second else null
    }

    private fun execPrivilegedCommand(command: String): String? {
        if (rootAccessVerified) {
            if (isShizukuRunningAsRoot()) {
                val out = execIfBound(command)
                if (!out.isNullOrBlank()) return out
            }
            val suOut = execSuCommand(command)
            if (!suOut.isNullOrBlank()) return suOut
            val shizukuSuOut = execIfBound("su -c $command")
            if (!shizukuSuOut.isNullOrBlank()) return shizukuSuOut
        }
        return execIfBound(command) ?: if (rootAccessVerified) execSuCommand(command) else null
    }

    private fun execDirectDumpsys(serviceName: String): String? {
        val ctx = appContext ?: return null
        if (ctx.checkSelfPermission(android.Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val proc = ProcessBuilder("/system/bin/dumpsys", serviceName)
                .redirectErrorStream(true)
                .start()
            runCatching { proc.outputStream.close() }
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val finished = proc.waitFor(2000L, TimeUnit.MILLISECONDS)
            if (finished && out.isNotBlank() && !out.contains("Permission Denial", ignoreCase = true)) {
                out
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun discoverAudioFlingerSessions(musicOnly: Boolean = true): List<Int> {
        val ctx = appContext
        val audioOutput = execPrivilegedCommand("dumpsys audio") ?: execDirectDumpsys("audio")
        val flingerOutput = execPrivilegedCommand("dumpsys media.audio_flinger") ?: execDirectDumpsys("media.audio_flinger")

        if (ctx != null && musicOnly) {
            val musicSessions = com.audioeq.audio.MusicAppTracker.parseMusicSessionsFromDumpsys(
                context = ctx,
                audioDumpsys = audioOutput,
                flingerDumpsys = flingerOutput
            )
            com.audioeq.audio.MusicAppTracker.updatePrivilegedMusicSessions(ctx, musicSessions)
            return musicSessions.map { it.sessionId }.distinct()
        }
        if (ctx != null) {
            com.audioeq.audio.MusicAppTracker.updatePrivilegedMusicSessions(ctx, emptyList())
        }

        val combined = (audioOutput.orEmpty() + "\n" + flingerOutput.orEmpty())
        if (combined.isBlank()) return emptyList()
        val sessionRegex = Regex("""(?:session(?:Id)?|Session)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
        return sessionRegex.findAll(combined)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .toList()
    }

    fun readBluetoothCodecSummary(): String? {
        val output = execPrivilegedCommand("dumpsys bluetooth_manager") ?: execDirectDumpsys("bluetooth_manager") ?: return null
        val codecRegex = Regex("""(?:codecConfig|mCodecConfig|mCodecType|Selected codec)\s*[:=]?\s*([^\n,]+)""", RegexOption.IGNORE_CASE)
        val match = codecRegex.find(output)
        return match?.groupValues?.get(1)?.trim() ?: "Bluetooth (A2DP)"
    }

    fun readHardwareSampleRateHz(): Int {
        val flingerOutput = execPrivilegedCommand("dumpsys media.audio_flinger") ?: execDirectDumpsys("media.audio_flinger") ?: return 48000
        val regex = Regex("""Sample rate\s*[:=]?\s*(\d{4,6})\s*Hz""", RegexOption.IGNORE_CASE)
        val parsed = regex.find(flingerOutput)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return when (parsed) {
            44100, 48000, 88200, 96000, 192000 -> parsed
            else -> 48000
        }
    }

    fun resolveSystemEqConflictIfRoot(): Boolean {
        if (!isRootAvailable()) return false
        val cmd = "setprop persist.audio.system_eq.enable false"
        if (isShizukuRunningAsRoot()) {
            execIfBound(cmd)
            return true
        }
        return execSuCommand("$cmd && echo ok")?.contains("ok") == true
    }

    @Volatile private var service: IUserService? = null
    @Volatile private var pending: CompletableDeferred<IUserService>? = null

    private val onBinderReceived = Shizuku.OnBinderReceivedListener {
        refresh()
    }

    private val onBinderDead = Shizuku.OnBinderDeadListener {
        service = null
        dumpGrantAttempted = false
        refresh()
    }

    private val onPermissionResult =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    dumpGrantAttempted = false
                    if (isShizukuRunningAsRoot()) {
                        rootAccessVerified = true
                    }
                    refresh()
                    tryGrantDumpPermissionAsync()
                    onStateChangedListener?.invoke(ShizukuState.READY)
                } else {
                    val deniedPerm = runCatching {
                        Shizuku.shouldShowRequestPermissionRationale()
                    }.getOrDefault(false)
                    _state.value = if (deniedPerm) ShizukuState.DENIED_PERMANENTLY else ShizukuState.NEEDS_PERMISSION
                    _privilegeSummary.value = privilege()
                    onStateChangedListener?.invoke(_state.value)
                }
            }
        }

    fun init(context: Context) {
        appContext = context.applicationContext
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
            Shizuku.addBinderDeadListener(onBinderDead)
            Shizuku.addRequestPermissionResultListener(onPermissionResult)
        }
        refresh()
    }

    fun release() {
        runCatching {
            Shizuku.removeBinderReceivedListener(onBinderReceived)
            Shizuku.removeBinderDeadListener(onBinderDead)
            Shizuku.removeRequestPermissionResultListener(onPermissionResult)
        }
    }

    fun isShizukuAppInstalled(context: Context? = appContext): Boolean {
        val ctx = context ?: return false
        return try {
            ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    fun refresh() {
        val prevState = _state.value
        val prevPriv = _privilegeSummary.value
        val newState = try {
            when {
                !Shizuku.pingBinder() ->
                    if (isShizukuAppInstalled()) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
                Shizuku.isPreV11() || Shizuku.getVersion() < 11 -> ShizukuState.UNSUPPORTED_VERSION
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
                Shizuku.shouldShowRequestPermissionRationale() -> ShizukuState.DENIED_PERMANENTLY
                else -> ShizukuState.NEEDS_PERMISSION
            }
        } catch (_: Throwable) {
            if (isShizukuAppInstalled()) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        }
        if (newState == ShizukuState.READY && isShizukuRunningAsRoot()) {
            rootAccessVerified = true
            persistRootHint(true)
        }
        val newPriv = privilege()
        _state.value = newState
        _privilegeSummary.value = newPriv
        if (newState == ShizukuState.READY) {
            if (service == null) bind()
            if (!dumpGrantAttempted) {
                tryGrantDumpPermissionAsync()
            }
        }
        if (prevState != newState || prevPriv != newPriv) {
            onStateChangedListener?.invoke(newState)
        }
    }

    fun privilege(): String = try {
        val shizukuReady = Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        when {
            shizukuReady && Shizuku.getUid() == 0 -> "Shizuku Root (su uid=0)"
            rootAccessVerified -> "Root (su uid=0)"
            shizukuReady -> "Shizuku Shell (adb uid=2000)"
            Shizuku.pingBinder() -> "Permission needed"
            isShizukuAppInstalled() -> "Shizuku stopped"
            else -> "none"
        }
    } catch (_: Throwable) {
        if (rootAccessVerified) "Root (su uid=0)" else "none"
    }

    fun requestPermission() {
        refresh()
        runCatching {
            when (_state.value) {
                ShizukuState.NEEDS_PERMISSION -> {
                    if (Shizuku.pingBinder()) {
                        Shizuku.requestPermission(REQUEST_CODE)
                    } else {
                        openShizukuApp()
                    }
                }
                ShizukuState.DENIED_PERMANENTLY -> {
                    openShizukuApp()
                }
                ShizukuState.NOT_RUNNING -> openShizukuApp()
                ShizukuState.NOT_INSTALLED -> openShizukuDownloadPage()
                ShizukuState.READY -> {
                    if (service == null) bind()
                    tryGrantDumpPermissionAsync()
                    _privilegeSummary.value = privilege()
                    onStateChangedListener?.invoke(ShizukuState.READY)
                }
                else -> Unit
            }
        }
    }

    fun ensureBoundAndGrantPermissions() {
        refresh()
        if (_state.value == ShizukuState.READY) {
            if (service == null) bind()
            tryGrantDumpPermissionAsync()
            _privilegeSummary.value = privilege()
            onStateChangedListener?.invoke(ShizukuState.READY)
        } else if (rootAccessVerified) {
            grantDumpViaSuAsync()
        }
    }

    private fun tryGrantDumpPermissionAsync() {
        val ctx = appContext ?: return
        if (_state.value != ShizukuState.READY) return
        dumpGrantAttempted = true
        Thread {
            runCatching {
                val pkg = ctx.packageName
                execIfBound("pm grant $pkg android.permission.DUMP")
                execIfBound("pm grant $pkg android.permission.PACKAGE_USAGE_STATS")
                _privilegeSummary.value = privilege()
                onStateChangedListener?.invoke(_state.value)
            }
        }.start()
    }

    private fun grantDumpViaSuAsync() {
        val ctx = appContext ?: return
        if (!rootAccessVerified) return
        val hasDump = ctx.checkSelfPermission(android.Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED
        if (hasDump) return
        Thread {
            runCatching {
                val pkg = ctx.packageName
                execSuCommand("pm grant $pkg android.permission.DUMP")
                execSuCommand("pm grant $pkg android.permission.PACKAGE_USAGE_STATS")
                _privilegeSummary.value = privilege()
                onStateChangedListener?.invoke(_state.value)
            }
        }.start()
    }

    fun openShizukuApp() {
        val ctx = appContext ?: return
        val intent = ctx.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        runCatching { ctx.startActivity(intent) }
    }

    fun openShizukuDownloadPage() {
        val ctx = appContext ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    fun launchShizukuApp() = openShizukuApp()
    fun openPlayStoreForShizuku() = openShizukuDownloadPage()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (!binder.pingBinder()) return
            val s = IUserService.Stub.asInterface(binder)
            service = s
            pending?.complete(s)
            _privilegeSummary.value = privilege()
            onStateChangedListener?.invoke(_state.value)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            _privilegeSummary.value = privilege()
        }
    }

    private fun userServiceArgs(): Shizuku.UserServiceArgs? {
        val ctx = appContext ?: return null
        val pm = ctx.packageManager
        val pkg = ctx.packageName
        val version = PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(pkg, 0)).toInt()
        return Shizuku.UserServiceArgs(ComponentName(pkg, ShizukuUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(false)
            .version(version)
    }

    private fun bind() {
        if (_state.value != ShizukuState.READY) return
        val args = userServiceArgs() ?: return
        runCatching { Shizuku.bindUserService(args, connection) }
    }

    fun unbind() {
        val args = userServiceArgs() ?: return
        runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null
    }

    private fun execViaShizukuNewProcess(command: String): String? {
        if (_state.value != ShizukuState.READY) return null
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val proc = method.invoke(null, arrayOf("sh", "-c", command), null, null) as? Process
                ?: return null
            runCatching { proc.outputStream.close() }
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val finished = proc.waitFor(2000, TimeUnit.MILLISECONDS)
            if (finished && proc.exitValue() == 0) out else null
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun exec(command: String): Result<String> = runCatching {
        check(_state.value == ShizukuState.READY) { "Shizuku is not ready: ${_state.value}" }
        val direct = withContext(Dispatchers.IO) { execIfBound(command) }
        if (direct != null) return@runCatching direct
        val s = service ?: CompletableDeferred<IUserService>().let { d ->
            pending = d
            bind()
            withTimeout(5_000) { d.await() }
        }
        val out = withContext(Dispatchers.IO) { s.exec(command) }
        if (out.startsWith("ERROR")) {
            throw IllegalStateException(out)
        }
        out
    }

    fun execIfBound(command: String): String? {
        if (_state.value != ShizukuState.READY) return null
        var s = service
        if (s == null) {
            bind()
            var waited = 0
            while (service == null && waited < 500) {
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    break
                }
                waited += 50
            }
            s = service
        }
        if (s != null) {
            try {
                val out = s.exec(command)
                if (!out.startsWith("ERROR")) {
                    return out
                }
            } catch (_: Throwable) {
            }
        }
        return execViaShizukuNewProcess(command)
    }
}
