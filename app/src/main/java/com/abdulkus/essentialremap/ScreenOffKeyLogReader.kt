package com.abdulkus.essentialremap

import android.os.SystemClock
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reads a logcat stream already narrowed to WindowManager's Essential Key messages. Waiting on
 * the pipe does not hold a wake lock; the accessibility service takes one only after a real DOWN.
 */
internal class ScreenOffKeyLogReader(
    private val onEvent: (ScreenOffKeyEvent) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var process: Process? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { readContinuously() }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        process?.destroy()
        process = null
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private suspend fun readContinuously() {
        while (currentCoroutineContext().isActive) {
            val startedAtNanos = SystemClock.uptimeMillis() * NANOS_PER_MILLISECOND
            val eventFilter = ScreenOffKeyEventFilter(startedAtNanos)
            var activeProcess: Process? = null
            try {
                val launchedProcess = ProcessBuilder(LOGCAT_COMMAND)
                    .redirectErrorStream(true)
                    .start()
                activeProcess = launchedProcess
                process = launchedProcess
                launchedProcess.inputStream.bufferedReader().use { reader ->
                    while (currentCoroutineContext().isActive) {
                        val line = reader.readLine() ?: break
                        val event = ScreenOffKeyLogParser.parse(line) ?: continue
                        if (eventFilter.accept(event)) {
                            onEvent(event)
                        }
                    }
                }
            } catch (error: IOException) {
                if (currentCoroutineContext().isActive) {
                    Log.w(TAG, "Screen-off key log reader stopped", error)
                }
            } finally {
                activeProcess?.destroy()
                if (process === activeProcess) process = null
            }
            if (currentCoroutineContext().isActive) delay(RESTART_DELAY_MS)
        }
    }

    private companion object {
        const val TAG = "EssentialKeyLog"
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val RESTART_DELAY_MS = 1_000L
        val LOGCAT_COMMAND = listOf(
            "/system/bin/logcat",
            "-b",
            "system",
            "-v",
            "brief",
            "-T",
            "1",
            "--regex=interceptKeyBeforeQueueing.*scanCode=250",
            "WindowManager:D",
            "*:S",
        )
    }
}
