package com.audioeq.shizuku

import android.content.Context
import androidx.annotation.Keep
import kotlin.system.exitProcess

@Keep
class ShizukuUserService() : IUserService.Stub() {

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }

    override fun exec(command: String): String = try {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        runCatching { process.outputStream.close() }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        if (code == 0) output else "ERROR($code): $output"
    } catch (t: Throwable) {
        "ERROR: ${t.message}"
    }
}
