package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.setup.ShellKeyMonitorCommands
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellKeyMonitorCommandsTest {
    @Test
    fun monitorIsNarrowlyFilteredAndTakesNoWakeLock() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertTrue(script.contains("scanCode=250"))
        assertTrue(script.contains("WindowManager:D"))
        assertTrue(script.contains("interactive=false"))
        assertTrue(script.contains("android.permission").not())
        assertFalse(script.contains("wake_lock", ignoreCase = true))
        assertFalse(script.contains("while true"))
    }

    @Test
    fun monitorUsesProtectedExplicitReceiverAndTracksItsPid() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertTrue(script.contains("com.abdulkus.essentialremap/.ShellKeyEventReceiver"))
        assertTrue(script.contains("key-monitor.pid"))
        assertTrue(script.contains("kill -0"))
    }

    @Test
    fun monitorUsesAnonymousPipeAndCleansUpChildProcesses() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertFalse(script.contains("mkfifo"))
        assertFalse(script.contains("key-events.pipe"))
        assertTrue(script.contains("2>&1 | while IFS= read -r line"))
        assertTrue(script.contains("/children"))
        assertTrue(script.contains("kill \"${'$'}child_pid\""))
    }

    @Test
    fun installerStreamsPayloadThroughAHereDocument() {
        val installer = ShellKeyMonitorCommands.installSessionScript

        assertTrue(installer.contains("ESSENTIAL_REMAP_MONITOR_EOF"))
        assertTrue(installer.contains("/system/bin/base64 -d"))
        assertTrue(installer.contains("key-monitor.sh.new"))
        assertTrue(installer.contains("/system/bin/sh -n"))
        assertTrue(installer.contains("MONITOR_REVISION=3"))
        assertTrue(installer.contains("/system/bin/mv -f"))
        assertTrue(installer.endsWith("exit\n"))
        assertTrue(ShellKeyMonitorCommands.INSTALL.length < 100)
        assertTrue(ShellKeyMonitorCommands.INSTALL_SERVICE.startsWith("exec:"))
        assertFalse(ShellKeyMonitorCommands.INSTALL_SERVICE.startsWith("shell:"))
        assertTrue(ShellKeyMonitorCommands.START_CONFIRMATION.startsWith(ShellKeyMonitorCommands.START_OK))
        assertTrue(ShellKeyMonitorCommands.RUNNING_CONFIRMATION.startsWith(ShellKeyMonitorCommands.RUNNING))
        assertTrue(ShellKeyMonitorCommands.START_CONFIRMATION.endsWith("revision=3"))
        assertTrue(ShellKeyMonitorCommands.RUNNING_CONFIRMATION.endsWith("revision=3"))

        val payloadLines = installer
            .substringAfter("<<'ESSENTIAL_REMAP_MONITOR_EOF'\n")
            .substringBefore("\nESSENTIAL_REMAP_MONITOR_EOF")
            .lineSequence()
            .toList()
        assertTrue(payloadLines.isNotEmpty())
        assertTrue(payloadLines.all { it.length <= 76 })
    }
}
