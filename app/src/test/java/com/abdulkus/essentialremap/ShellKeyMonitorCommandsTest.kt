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
    fun detachedSupervisorCanRestartASeparateMonitorSession() {
        val script = ShellKeyMonitorCommands.scriptForTesting()
        assertTrue(script.contains("/system/bin/setsid -d /system/bin/sh") && script.contains(" supervise \"\$app_uid\""))
        val superviseBlock = script.substringAfter("  supervise)\n").substringBefore("  run)\n")
        assertTrue(superviseBlock.contains("wait \"\$helper_pid\""))
        assertTrue(superviseBlock.contains("/system/bin/setsid -d /system/bin/sh") && superviseBlock.contains(" run \"\$app_uid\""))
        assertTrue(superviseBlock.contains("failures"))
        assertTrue(superviseBlock.contains("monitor crash loop exhausted"))
        assertFalse(superviseBlock.contains("while true"))
        val runBlock = script.substringAfter("  run)\n").substringBefore("  stop)\n")
        assertTrue(runBlock.contains("exec /system/bin/app_process"))
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
}
