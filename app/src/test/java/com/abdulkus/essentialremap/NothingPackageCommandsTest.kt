package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.setup.NothingPackageCommands
import com.abdulkus.essentialremap.setup.EssentialKeySetupCommands
import com.abdulkus.essentialremap.setup.PackageOperation
import com.abdulkus.essentialremap.setup.ShellKeyMonitorCommands
import org.junit.Assert.assertEquals
import org.junit.Test

class NothingPackageCommandsTest {
    @Test
    fun disableCommandsAreStrictlyAllowlisted() {
        assertEquals(
            listOf(
                "pm disable-user --user 0 com.nothing.ntessentialspace",
                "pm disable-user --user 0 com.nothing.ntessentialrecorder",
            ),
            NothingPackageCommands.commands(PackageOperation.DISABLE),
        )
    }

    @Test
    fun restoreCommandsAreStrictlyAllowlisted() {
        assertEquals(
            listOf(
                "pm enable --user 0 com.nothing.ntessentialspace",
                "pm enable --user 0 com.nothing.ntessentialrecorder",
            ),
            NothingPackageCommands.commands(PackageOperation.RESTORE),
        )
    }

    @Test
    fun releaseSetupAllowsOemWakeAndStartsShellMonitor() {
        assertEquals(
            listOf(
                "pm disable-user --user 0 com.nothing.ntessentialspace",
                "pm disable-user --user 0 com.nothing.ntessentialrecorder",
                "settings put secure nt_block_essential_key 0 && echo essential-remap:ok",
                ShellKeyMonitorCommands.installAndStart,
            ),
            EssentialKeySetupCommands.commands(PackageOperation.DISABLE),
        )
    }

    @Test
    fun arbitraryShellCommandsAreRejectedByAllowlist() {
        assertEquals(false, EssentialKeySetupCommands.isAllowlisted("settings list secure"))
        assertEquals(true, EssentialKeySetupCommands.isAllowlisted(ShellKeyMonitorCommands.installAndStart))
    }

    @Test
    fun restoringSpaceStopsMonitorBeforeEnablingPackages() {
        assertEquals(
            listOf(
                ShellKeyMonitorCommands.stop,
                "pm enable --user 0 com.nothing.ntessentialspace",
                "pm enable --user 0 com.nothing.ntessentialrecorder",
            ),
            EssentialKeySetupCommands.commands(PackageOperation.RESTORE),
        )
    }
}
