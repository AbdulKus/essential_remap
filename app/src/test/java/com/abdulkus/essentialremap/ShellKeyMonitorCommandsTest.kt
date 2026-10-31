package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.monitor.ShellMonitorMain
import com.abdulkus.essentialremap.setup.ShellKeyMonitorCommands
import java.nio.file.Files
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellKeyMonitorCommandsTest {
    @Test
    fun stateRevisionMatchesInstallerRevision() {
        assertEquals(ShellKeyMonitorCommands.REVISION, ShellMonitorMain.STATE_REVISION)
    }

    @Test
    fun installerPayloadDecodesToExactScriptAndTransportLength() {
        val installer = ShellKeyMonitorCommands.installSessionScript
        val encoded = installer.substringAfter("<<'ESSENTIAL_REMAP_MONITOR_EOF'\n")
            .substringBefore("\nESSENTIAL_REMAP_MONITOR_EOF").replace("\n", "")
        assertEquals(ShellKeyMonitorCommands.scriptForTesting(), String(Base64.getDecoder().decode(encoded)))
        val length = Regex("dd bs=1 count=(\\d+)").find(ShellKeyMonitorCommands.INSTALL_SERVICE)!!.groupValues[1].toInt()
        assertEquals(installer.toByteArray(Charsets.UTF_8).size, length)
    }

    @Test
    fun generatedInstallerAndMonitorAreValidShell() {
        for (source in listOf(ShellKeyMonitorCommands.scriptForTesting(), ShellKeyMonitorCommands.installSessionScript)) {
            val process = ProcessBuilder("/bin/sh", "-n").redirectErrorStream(true).start()
            process.outputStream.bufferedWriter().use { it.write(source) }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.waitFor())
        }
    }

    @Test
    fun detachedLauncherExecsTheMonitorInsteadOfKeepingAShellSupervisor() {
        val script = ShellKeyMonitorCommands.scriptForTesting()
        assertTrue(script.contains("setsid /system/bin/sh"))
        val runBlock = script.substringAfter("          run)\n").substringBefore("          stop)\n")
        assertTrue(runBlock.contains("app_uid="))
        assertTrue(runBlock.contains("exec /system/bin/app_process"))
        assertFalse(runBlock.contains("restarts="))
        assertFalse(runBlock.contains("helper_pid="))
    }

    @Test
    fun recursiveCleanupStopsChildrenThenParentsWithoutClobberingPid() {
        val directory = Files.createTempDirectory("monitor-cleanup").toFile()
        try {
            for ((pid, children) in mapOf(100 to "200", 200 to "300", 300 to "")) {
                val file = directory.resolve("proc/$pid/task/$pid/children")
                file.parentFile.mkdirs()
                file.writeText("$children\n")
            }
            val trace = directory.resolve("trace")
            val kill = directory.resolve("kill")
            kill.writeText("#!/bin/sh\nprintf '%s\\n' \"${'$'}*\" >> '${trace.path}'\n")
            kill.setExecutable(true)
            val function = ShellKeyMonitorCommands.scriptForTesting().substringAfter("kill_tree() (")
                .substringBefore("\n)\n")
                .replace("/proc/", "${directory.path}/proc/")
                .replace("/system/bin/kill", kill.path)
            val process = ProcessBuilder("/bin/sh").redirectErrorStream(true).start()
            process.outputStream.bufferedWriter().use { it.write("kill_tree() ($function\n)\nkill_tree 100 TERM\n") }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.waitFor())
            assertEquals(listOf("-TERM 300", "-TERM 200", "-TERM 100"), trace.readLines())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun stoppingBeforeMonitorInstallationIsAnIdempotentSuccess() {
        val directory = Files.createTempDirectory("monitor-not-installed").toFile()
        try {
            val pid = directory.resolve("key-monitor.pid").apply { writeText("123") }
            val state = directory.resolve("key-monitor.state").apply { writeText("old revision") }

            repeat(2) {
                val (exit, output) = runHostStop(directory)
                assertEquals(output, 0, exit)
                assertTrue(output, output.contains(ShellKeyMonitorCommands.STOP_OK))
                assertFalse(pid.exists())
                assertFalse(state.exists())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun stoppingAnInstalledMonitorStillInvokesItsScript() {
        val directory = Files.createTempDirectory("monitor-installed").toFile()
        try {
            directory.resolve("key-monitor.sh").writeText(
                "#!/bin/sh\n" +
                    "test \"\$1\" = stop || exit 8\n" +
                    "echo essential-remap:shell-monitor-stopped\n",
            )
            val (exit, output) = runHostStop(directory)
            assertEquals(output, 0, exit)
            assertEquals(ShellKeyMonitorCommands.STOP_OK, output.trim())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun anInstalledButBrokenMonitorDoesNotReportSuccessfulCleanup() {
        val directory = Files.createTempDirectory("monitor-broken").toFile()
        try {
            directory.resolve("key-monitor.sh").writeText("#!/bin/sh\necho stop-failed\nexit 2\n")
            val (exit, output) = runHostStop(directory)
            assertEquals(2, exit)
            assertFalse(output.contains(ShellKeyMonitorCommands.STOP_OK))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun runHostStop(directory: java.io.File): Pair<Int, String> {
        val command = ShellKeyMonitorCommands.stop
            .replace("/data/local/tmp/essential_remap", directory.absolutePath)
            .replace("/system/bin/sh", "/bin/sh")
            .replace("/system/bin/pidof", "/bin/false") // Simulate no orphaned helper.
            .replace("/system/bin/sleep", "/bin/sleep")
            .replace("/system/bin/kill", "/bin/kill")
            .replace("/system/bin/rm", "/bin/rm")
        val process = ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }

}
