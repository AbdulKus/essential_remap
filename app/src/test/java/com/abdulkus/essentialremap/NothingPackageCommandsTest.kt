package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.setup.NothingPackageCommands
import com.abdulkus.essentialremap.setup.EssentialKeySetupCommands
import com.abdulkus.essentialremap.setup.PackageOperation
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
    fun releaseSetupAlsoGrantsLogAccessAndBlocksDisplayWake() {
        assertEquals(
            listOf(
                "pm disable-user --user 0 com.nothing.ntessentialspace",
                "pm disable-user --user 0 com.nothing.ntessentialrecorder",
                "pm grant com.abdulkus.essentialremap android.permission.READ_LOGS && echo essential-remap:ok",
                "settings put secure nt_block_essential_key 0 && echo essential-remap:ok",
            ),
            EssentialKeySetupCommands.commands(PackageOperation.DISABLE),
        )
    }

    @Test
    fun arbitraryShellCommandsAreRejectedByAllowlist() {
        assertEquals(false, EssentialKeySetupCommands.isAllowlisted("settings list secure"))
        assertEquals(true, EssentialKeySetupCommands.isAllowlisted(EssentialKeySetupCommands.GRANT_READ_LOGS))
    }
}
