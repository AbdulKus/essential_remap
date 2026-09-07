package com.abdulkus.essentialremap

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdulkus.essentialremap.monitor.MonitorMessage
import com.abdulkus.essentialremap.ui.UserPreferences
import java.io.FileInputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellMonitorTransportTest {
    @Test
    fun shellSocketAcknowledgesInputDeduplicatesRetriesAndStaysIdle() = runProbe(false)

    @Test
    fun freshPendingDownSurvivesTransportReconnectBeforeServiceAttaches() = runProbe(true)

    @Test
    fun shellTransportWorksWithDisplayOffAndDeviceIdle() = runProbe(false, idle = true)

    private fun runProbe(reconnect: Boolean, idle: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as EssentialKeyApplication
        UserPreferences(context).screenOffEnabled = true
        val received = Collections.synchronizedList(mutableListOf<String>())
        val delivered = CountDownLatch(1)
        val listener: (MonitorMessage, Boolean) -> Unit = { message, _ ->
            if (message.kind != "RESET") received.add(message.kind)
            if (message.isGesture) delivered.countDown()
        }
        val classpath = context.applicationInfo.sourceDir + ":" + instrumentation.context.applicationInfo.sourceDir
        fun command(text: String): String = instrumentation.uiAutomation.executeShellCommand(text).use {
            FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
        val deepWasEnabled = if (idle) command("/system/bin/cmd deviceidle enabled deep").trim() else "1"
        var idleResult = ""
        if (idle) {
            command("/system/bin/dumpsys battery unplug")
            command("/system/bin/input keyevent 223")
            command("/system/bin/cmd deviceidle enable deep")
            idleResult = command("/system/bin/cmd deviceidle force-idle deep")
        }
        val shell = instrumentation.uiAutomation.executeShellCommandRwe(
            "/system/bin/env CLASSPATH=$classpath /system/bin/app_process /system/bin " +
                "com.abdulkus.essentialremap.SocketProbeMain ${context.applicationInfo.uid} ${if (reconnect) "reconnect" else "normal"}",
        )
        shell[1].close() // No stdin is needed; don't keep UiAutomation's input pump alive.
        val output = StringBuffer()
        val ready = CountDownLatch(1)
        val reconnected = CountDownLatch(1)
        val finished = CountDownLatch(1)
        thread(isDaemon = true, name = "probe-stdout") {
            try {
                FileInputStream(shell[0].fileDescriptor).bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        output.append(line).append('\n')
                        if (line == "PROBE_READY") ready.countDown()
                        if (line == "PROBE_RECONNECTED") reconnected.countDown()
                    }
                }
            } catch (_: java.io.IOException) {
                // Cleanup closes the descriptor if the bounded probe times out.
            } finally { finished.countDown() }
        }
        thread(isDaemon = true, name = "probe-stderr") {
            runCatching {
                FileInputStream(shell[2].fileDescriptor).bufferedReader().useLines { lines ->
                    lines.forEach { output.append(it).append('\n') }
                }
            }
        }
        try {
            if (idle) {
                val power = context.getSystemService(android.os.PowerManager::class.java)
                repeat(40) { if (!power.isDeviceIdleMode) Thread.sleep(50) }
                assertTrue("Display must be off for the idle test", !power.isInteractive)
                assertTrue("Device must be in Doze for the idle test: $idleResult; " +
                    command("/system/bin/cmd deviceidle get deep"), power.isDeviceIdleMode)
            }
            val started = ready.await(10, TimeUnit.SECONDS)
            assertTrue("Shell probe did not start: $output", started)
            if (reconnect) {
                app.container.shellBridge.requestConnect()
                val recovered = reconnected.await(10, TimeUnit.SECONDS)
                assertTrue("Shell probe did not reconnect: $output", recovered)
                instrumentation.waitForIdleSync() // DOWN has been queued while no listener was attached.
            }
            instrumentation.runOnMainSync { app.container.shellBridge.attach(listener) }
            val arrived = delivered.await(10, TimeUnit.SECONDS)
            assertTrue("No gesture arrived through the shell socket\n$output\n${app.container.diagnostics.report()}", arrived)
            val exited = finished.await(5, TimeUnit.SECONDS)
            instrumentation.waitForIdleSync()
            assertTrue("Probe did not finish: $output", exited)
            assertTrue(output.toString(), output.contains("PROBE_OK"))
            assertEquals(listOf("DOWN", "SINGLE"), received.toList())
        } finally {
            instrumentation.runOnMainSync { app.container.shellBridge.detach(listener) }
            shell.forEach { runCatching { it.close() } }
            if (idle) {
                command("/system/bin/cmd deviceidle unforce")
                if (deepWasEnabled == "0") command("/system/bin/cmd deviceidle disable deep")
                command("/system/bin/dumpsys battery reset")
                command("/system/bin/input keyevent 224")
            }
        }
    }
}
