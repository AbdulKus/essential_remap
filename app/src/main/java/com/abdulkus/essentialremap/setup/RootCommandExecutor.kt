package com.abdulkus.essentialremap.setup

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Executes only caller-supplied internal setup commands through the device's su implementation. */
object RootCommandExecutor {
    private const val DEFAULT_TIMEOUT_MS = 15_000L
    private const val ROOT_AUTH_TIMEOUT_MS = 30_000L
    private const val MAX_OUTPUT_CHARS = 16_384

    fun requireRoot(timeoutMs: Long = ROOT_AUTH_TIMEOUT_MS) {
        val output = execute("id -u", timeoutMs)
        if (output.lineSequence().none { it.trim() == "0" }) {
            throw IOException("Root access was not granted")
        }
    }

    fun execute(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String {
        require(command.isNotBlank()) { "Root command must not be blank" }
        val process = try {
            ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
        } catch (error: IOException) {
            throw IOException("Root is unavailable: su could not be started", error)
        }

        val output = StringBuilder()
        val readerThread = thread(
            start = true,
            isDaemon = true,
            name = "essential-root-output",
        ) {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            if (output.length < MAX_OUTPUT_CHARS) {
                                val remaining = MAX_OUTPUT_CHARS - output.length
                                output.append(line.take(remaining)).append('\n')
                            }
                        }
                    }
                }
            }
        }

        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroy()
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
                throw IOException("Root command timed out")
            }
            readerThread.join(1_000)
            val text = synchronized(output) { output.toString().trim() }
            if (process.exitValue() != 0) {
                val detail = text.takeLast(2_000).ifBlank { "no output" }
                throw IOException("Root command failed (exit=${process.exitValue()}): $detail")
            }
            return text
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
