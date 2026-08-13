package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.setup.NothingPackageCommands
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
}
