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
    fun installerStreamsPayloadThroughAHereDocument() {
        val installer = ShellKeyMonitorCommands.installSessionScript

        assertTrue(installer.contains("ESSENTIAL_REMAP_MONITOR_EOF"))
        assertTrue(installer.contains("/system/bin/base64 -d"))
        assertTrue(installer.endsWith("exit\n"))
        assertTrue(ShellKeyMonitorCommands.INSTALL.length < 100)
    }
}
