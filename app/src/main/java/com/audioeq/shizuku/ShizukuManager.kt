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
    private var dumpGrantAttempted: Boolean = false

    @Volatile
    private var onStateChangedListener: ((ShizukuState) -> Unit)? = null

    operator fun invoke(context: Context): ShizukuManager {
        init(context)
        return this
    }

    fun setOnStateChangedListener(listener: ((ShizukuState) -> Unit)?) {
        onStateChangedListener = listener
    }

    fun refreshStatus() = refresh()

    fun isRootAvailable(): Boolean = rootAccessVerified

    fun verifyRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val finished = process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                rootAccessVerified = false
                return false
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val verified = process.exitValue() == 0 && output.contains("uid=0")
            rootAccessVerified = verified
            verified
        } catch (_: Throwable) {
            rootAccessVerified = false
            false
        }
    }

    fun clearRootVerification() {
        rootAccessVerified = false
    }

    private fun execPrivilegedCommand(command: String): String? {
        return execIfBound(command) ?: runCatching {
            if (rootAccessVerified) {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val finished = process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (finished && process.exitValue() == 0) {
                    process.inputStream.bufferedReader().use { it.readText() }
                } else {
                    process.destroyForcibly()
                    null
                }
            } else null
        }.getOrNull()
    }

    fun discoverAudioFlingerSessions(musicOnly: Boolean = true): List<Int> {
        val ctx = appContext
        val audioOutput = execPrivilegedCommand("dumpsys audio")
        val flingerOutput = execPrivilegedCommand("dumpsys media.audio_flinger")

        if (ctx != null && musicOnly) {
            val musicSessions = com.audioeq.audio.MusicAppTracker.parseMusicSessionsFromDumpsys(
                context = ctx,
                audioDumpsys = audioOutput,
                flingerDumpsys = flingerOutput
            )
            com.audioeq.audio.MusicAppTracker.updatePrivilegedMusicSessions(ctx, musicSessions)
            return musicSessions.map { it.sessionId }.distinct()
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
        val output = execIfBound("dumpsys bluetooth_manager") ?: runCatching {
            if (rootAccessVerified) {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys bluetooth_manager"))
                val finished = process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (finished && process.exitValue() == 0) {
                    process.inputStream.bufferedReader().use { it.readText() }
                } else {
                    process.destroyForcibly()
                    null
                }
            } else null
        }.getOrNull() ?: return null

        val codecRegex = Regex("""(?:codecConfig|mCodecConfig|mCodecType|Selected codec)\s*[:=]?\s*([^\n,]+)""", RegexOption.IGNORE_CASE)
        val match = codecRegex.find(output)
        return match?.groupValues?.get(1)?.trim() ?: "Bluetooth (A2DP)"
    }

    fun readHardwareSampleRateHz(): Int {
        val flingerOutput = execPrivilegedCommand("dumpsys media.audio_flinger") ?: return 48000
        val regex = Regex("""Sample rate\s*[:=]?\s*(\d{4,6})\s*Hz""", RegexOption.IGNORE_CASE)
        val parsed = regex.find(flingerOutput)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return when (parsed) {
            44100, 48000, 88200, 96000, 192000 -> parsed
            else -> 48000
        }
    }

    fun resolveSystemEqConflictIfRoot(): Boolean {
        if (!rootAccessVerified) return false
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "setprop persist.audio.system_eq.enable false")
            )
            val finished = process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: Throwable) {
            false
        }
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
                    refresh()
                    tryGrantDumpPermissionAsync()
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
        val newState = try {
            when {
                !Shizuku.pingBinder() ->
                    if (isShizukuAppInstalled()) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
                Shizuku.isPreV11() || Shizuku.getVersion() < 11 -> ShizukuState.UNSUPPORTED_VERSION
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
                else -> ShizukuState.NEEDS_PERMISSION
            }
        } catch (_: Throwable) {
            if (isShizukuAppInstalled()) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        }
        _state.value = newState
        _privilegeSummary.value = privilege()
        if (newState == ShizukuState.READY) {
            if (service == null) bind()
            if (!dumpGrantAttempted) {
                tryGrantDumpPermissionAsync()
            }
        }
        if (prevState != newState) {
            onStateChangedListener?.invoke(newState)
        }
    }

    fun privilege(): String = try {
        when {
            !Shizuku.pingBinder() -> "none"
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "ungranted"
            Shizuku.getUid() == 0 -> "adb (su)"
            else -> "adb"
        }
    } catch (_: Throwable) {
        "none"
    }

    fun requestPermission() {
        refresh()
        runCatching {
            when (_state.value) {
                ShizukuState.NEEDS_PERMISSION,
                ShizukuState.DENIED_PERMANENTLY -> {
                    if (Shizuku.pingBinder()) {
                        Shizuku.requestPermission(REQUEST_CODE)
                    } else {
                        openShizukuApp()
                    }
                }
                ShizukuState.NOT_RUNNING -> openShizukuApp()
                ShizukuState.NOT_INSTALLED -> openShizukuDownloadPage()
                ShizukuState.READY -> {
                    if (service == null) bind()
                    tryGrantDumpPermissionAsync()
                    onStateChangedListener?.invoke(ShizukuState.READY)
                }
                else -> Unit
            }
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
            onStateChangedListener?.invoke(_state.value)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
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
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
            out.takeIf { it.isNotBlank() }
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
        val s = service
        if (s != null) {
            try {
                val out = s.exec(command)
                if (!out.startsWith("ERROR") && out.isNotBlank()) {
                    return out
                }
            } catch (_: Throwable) {
            }
        } else {
            bind()
        }
        return execViaShizukuNewProcess(command)
    }
}
