package com.abdulkus.essentialremap

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdulkus.essentialremap.monitor.MonitorMessage
import com.abdulkus.essentialremap.ui.UserPreferences
import java.io.FileInputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
        val shell = instrumentation.uiAutomation.executeShellCommand(
            "/system/bin/env CLASSPATH=$classpath /system/bin/app_process /system/bin " +
                "com.abdulkus.essentialremap.SocketProbeMain ${context.applicationInfo.uid} ${if (reconnect) "reconnect" else "normal"}",
        )
        try {
            if (reconnect) {
                app.container.shellBridge.requestConnect()
                Thread.sleep(1_100) // Simulate loading service settings across the transport failure.
            }
            instrumentation.runOnMainSync { app.container.shellBridge.attach(listener) }
            assertTrue("No gesture arrived through the shell socket\n${app.container.diagnostics.report()}",
                delivered.await(10, TimeUnit.SECONDS))
            val result = FileInputStream(shell.fileDescriptor).bufferedReader().use { it.readText() }
            instrumentation.waitForIdleSync()
            assertTrue(result, result.contains("PROBE_OK"))
            assertEquals(listOf("DOWN", "SINGLE"), received.toList())
        } finally {
            instrumentation.runOnMainSync { app.container.shellBridge.detach(listener) }
            shell.close()
        }
    }
}
