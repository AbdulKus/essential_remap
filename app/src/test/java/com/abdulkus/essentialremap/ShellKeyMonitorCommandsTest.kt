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
        assertTrue(script.contains("grep -F 'gpio-keys'"))
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
        assertTrue(script.contains("grep -aF"))
        assertTrue(script.contains("stop_all_monitors"))
        assertTrue(script.contains("kill_tree"))
        assertTrue(script.contains("/children"))
        assertTrue(script.contains("/system/bin/kill -9"))
        assertTrue(script.contains("cleanup complete"))
        assertFalse(script.contains("monitor_count"))
    }

    @Test
    fun monitorUsesRootPidAndPersistentStateInsteadOfCountingPipelineHelpers() {
        val script = ShellKeyMonitorCommands.scriptForTesting()

        assertTrue(script.contains("monitor_is_running"))
        assertTrue(script.contains("key-monitor.pid"))
        assertTrue(script.contains("key-monitor.state"))
        assertTrue(script.contains("revision=${'$'}MONITOR_REVISION pid=${'$'}${'$'} input=${'$'}INPUT_DEVICE"))
        assertTrue(script.contains("monitor_is_running && [ -s \"${'$'}STATE_FILE\" ]"))
        assertTrue(script.contains("count=1"))
        assertFalse(script.contains("[ \"${'$'}(monitor_count)\" -eq 1 ]"))
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
    fun installerStagesPayloadAndKeepsAStageLog() {
        val installer = ShellKeyMonitorCommands.installSessionScript
        val service = ShellKeyMonitorCommands.INSTALL_SERVICE
        val revision = ShellKeyMonitorCommands.REVISION
        val payloadBytes = installer.toByteArray(Charsets.UTF_8).size

        assertTrue(installer.contains("ESSENTIAL_REMAP_MONITOR_EOF"))
        assertTrue(installer.contains("/system/bin/base64 -d"))
        assertTrue(installer.contains("key-monitor.sh.new"))
        assertTrue(installer.contains("/system/bin/sh -n"))
        assertTrue(installer.contains("MONITOR_REVISION=$revision"))
        assertTrue(installer.contains("/system/bin/mv -f"))
        assertTrue(installer.contains("install-monitor.log"))
        assertTrue(installer.contains("stage=decode"))
        assertTrue(installer.contains("stage=validate"))
        assertTrue(installer.contains("stage=start"))
        assertFalse(installer.contains("$revision' $"))

        assertTrue(service.startsWith("exec:"))
        assertFalse(service.startsWith("shell:"))
        assertFalse(service == "exec:/system/bin/sh")
        assertTrue(service.contains("/system/bin/stty raw -echo"))
        assertTrue(service.contains("/system/bin/dd bs=1 count=$payloadBytes"))
        assertTrue(service.contains("of=/data/local/tmp/essential_remap/install-monitor.sh"))
        assertTrue(service.contains("transport stage=dd"))
        assertTrue(service.contains("/system/bin/sh /data/local/tmp/essential_remap/install-monitor.sh"))
        assertTrue(service.contains("installer_status=${'$'}?"))

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
