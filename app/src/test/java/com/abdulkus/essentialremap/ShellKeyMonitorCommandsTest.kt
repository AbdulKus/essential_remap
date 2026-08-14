package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.setup.ShellKeyMonitorCommands
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellKeyMonitorCommandsTest {
    @Test
    fun monitorReadsOnlyTheGpioEssentialKeyAndTakesNoWakeLock() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertTrue(script.contains("/system/bin/getevent -t"))
        assertTrue(script.contains("\"gpio-keys\""))
        assertTrue(script.contains("0001:00fa"))
        assertTrue(script.contains("00000001"))
        assertTrue(script.contains("00000000"))
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
    fun monitorUsesDetachedBlockingInputAndCleansUpChildProcesses() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertFalse(script.contains("mkfifo"))
        assertFalse(script.contains("key-events.pipe"))
        assertTrue(script.contains("2>&1 | while IFS= read -r line"))
        assertTrue(script.contains("command -v setsid"))
        assertTrue(script.contains("logcat -b system -d"))
        assertFalse(script.contains("logcat -b system -v brief -T 1"))
        assertTrue(script.contains("/children"))
        assertTrue(script.contains("kill \"${'$'}child_pid\""))
    }

    @Test
    fun monitorConvertsGeteventNanosToWindowManagerMillisOnlyForInteractiveLookup() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertTrue(script.contains("event_time_ms=${'$'}((event_time / 1000000))"))
        assertTrue(script.contains("resolve_interactive \"${'$'}event_time_ms\""))
        assertTrue(script.contains("downTime=${'$'}target_down_time_ms"))
        assertTrue(script.contains("active_down_time=\"${'$'}event_time\""))
        assertTrue(script.contains("send_event 0 \"${'$'}event_time\" \"${'$'}event_time\""))
    }

    @Test
    fun monitorReportsItsRawInputStateToAppDiagnostics() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertTrue(script.contains("--es monitor_status"))
        assertTrue(script.contains("eventTimeMs=${'$'}event_time_ms"))
        assertTrue(script.contains("false screen-off"))
        assertTrue(script.contains("true screen-off-release"))
        assertTrue(script.contains("state=unresolved"))
        assertTrue(script.contains("event-broadcast action="))
    }

    @Test
    fun installerStreamsPayloadThroughAHereDocument() {
        val installer = ShellKeyMonitorCommands.installSessionScript
        val revision = ShellKeyMonitorCommands.REVISION

        assertTrue(installer.contains("ESSENTIAL_REMAP_MONITOR_EOF"))
        assertTrue(installer.contains("/system/bin/base64 -d"))
        assertTrue(installer.contains("key-monitor.sh.new"))
        assertTrue(installer.contains("/system/bin/sh -n"))
        assertTrue(installer.contains("MONITOR_REVISION=$revision"))
        assertTrue(installer.contains("/system/bin/mv -f"))
        assertTrue(installer.endsWith("exit\n"))
        assertTrue(ShellKeyMonitorCommands.INSTALL.length < 100)
        assertTrue(ShellKeyMonitorCommands.INSTALL_SERVICE.startsWith("exec:"))
        assertFalse(ShellKeyMonitorCommands.INSTALL_SERVICE.startsWith("shell:"))
        assertTrue(ShellKeyMonitorCommands.START_CONFIRMATION.startsWith(ShellKeyMonitorCommands.START_OK))
        assertTrue(ShellKeyMonitorCommands.RUNNING_CONFIRMATION.startsWith(ShellKeyMonitorCommands.RUNNING))
        assertTrue(ShellKeyMonitorCommands.START_CONFIRMATION.endsWith("revision=$revision"))
        assertTrue(ShellKeyMonitorCommands.RUNNING_CONFIRMATION.endsWith("revision=$revision"))

        val payloadLines = installer
            .substringAfter("<<'ESSENTIAL_REMAP_MONITOR_EOF'\n")
            .substringBefore("\nESSENTIAL_REMAP_MONITOR_EOF")
            .lineSequence()
            .toList()
        assertTrue(payloadLines.isNotEmpty())
        assertTrue(payloadLines.all { it.length <= 76 })
    }
}
