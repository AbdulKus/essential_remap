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

    private fun runProbe(reconnect: Boolean) {
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
        val shell = instrumentation.uiAutomation.executeShellCommandRwe(
            "/system/bin/env CLASSPATH=$classpath /system/bin/app_process /system/bin " +
                "com.abdulkus.essentialremap.SocketProbeMain ${context.applicationInfo.uid} ${if (reconnect) "reconnect" else "normal"}",
        )
        val output = StringBuffer()
        val ready = CountDownLatch(1)
        val finished = CountDownLatch(1)
        thread(isDaemon = true, name = "probe-stdout") {
            try {
                FileInputStream(shell[0].fileDescriptor).bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        output.append(line).append('\n')
                        if (line == "PROBE_READY") ready.countDown()
                    }
                }
            } finally { finished.countDown() }
        }
        thread(isDaemon = true, name = "probe-stderr") {
            FileInputStream(shell[2].fileDescriptor).bufferedReader().useLines { lines ->
                lines.forEach { output.append(it).append('\n') }
            }
        }
        try {
            val started = ready.await(10, TimeUnit.SECONDS)
            assertTrue("Shell probe did not start: $output", started)
            if (reconnect) {
                app.container.shellBridge.requestConnect()
                Thread.sleep(1_100) // Simulate loading service settings across the transport failure.
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
        }
    }
}
