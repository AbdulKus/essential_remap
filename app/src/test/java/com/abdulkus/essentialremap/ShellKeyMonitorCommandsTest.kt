package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.setup.ShellKeyMonitorCommands
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellKeyMonitorCommandsTest {
    @Test
    fun monitorReadsOnlyTheGpioEssentialKeyAndTakesNoIdleWakeLock() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertTrue(script.contains("/system/bin/getevent -t"))
        assertTrue(script.contains("\"gpio-keys\""))
        assertTrue(script.contains("0001:00fa"))
        assertTrue(script.contains("00000001"))
        assertTrue(script.contains("00000000"))
        assertTrue(script.contains("mWakefulness="))
        assertFalse(script.contains("WindowManager:D"))
        assertFalse(script.contains("scanCode=250"))
        assertFalse(script.contains("wake_lock", ignoreCase = true))
        assertFalse(script.contains("while true"))
    }

    @Test
    fun monitorKillsEveryOlderRevisionBeforeStarting() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertTrue(script.contains("for proc_dir in /proc/[0-9]*"))
        assertTrue(script.contains("is_monitor_pid"))
        assertTrue(script.contains("stop_all_monitors"))
        assertTrue(script.contains("kill_tree"))
        assertTrue(script.contains("/children"))
        assertTrue(script.contains("/system/bin/kill -9"))
        assertTrue(script.contains("monitor_count"))
        assertTrue(script.contains("count=1"))
    }

    @Test
    fun monitorUsesProtectedExplicitReceiverAndPersistentState() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertTrue(script.contains("com.abdulkus.essentialremap/.ShellKeyEventReceiver"))
        assertTrue(script.contains("key-monitor.pid"))
        assertTrue(script.contains("key-monitor.state"))
        assertTrue(script.contains("revision=${'$'}MONITOR_REVISION pid=${'$'}${'$'} input=${'$'}INPUT_DEVICE"))
    }

    @Test
    fun monitorUsesDetachedBlockingInputAndCleansUpChildProcesses() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertFalse(script.contains("mkfifo"))
        assertFalse(script.contains("key-events.pipe"))
        assertTrue(script.contains("2>&1 | while IFS= read -r line"))
        assertTrue(script.contains("command -v setsid"))
        assertTrue(script.contains("kill_tree \"${'$'}child_pid\""))
    }

    @Test
    fun monitorBuildsMillisWithoutOverflowingShellArithmetic() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertTrue(script.contains("event_times_from_getevent"))
        assertTrue(script.contains("event_millis_fraction=\"${'$'}{event_micros%???}\""))
        assertTrue(script.contains("eventTimeMs=${'$'}event_time_ms"))
        assertFalse(script.contains("event_time / 1000000"))
        assertTrue(script.contains("active_down_time=\"${'$'}event_time\""))
        assertTrue(script.contains("send_event 0 \"${'$'}event_time\" \"${'$'}event_time\""))
    }

    @Test
    fun monitorReportsItsRawInputStateToAppDiagnostics() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertTrue(script.contains("--es monitor_status"))
        assertTrue(script.contains("false screen-off"))
        assertTrue(script.contains("false screen-off-release"))
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
