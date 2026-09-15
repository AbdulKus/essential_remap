package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.setup.SetupAccessMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupAccessModeTest {
    @Test
    fun missingOrUnknownStoredModeDefaultsToNonRoot() {
        assertEquals(SetupAccessMode.NON_ROOT, SetupAccessMode.fromStored(null))
        assertEquals(SetupAccessMode.NON_ROOT, SetupAccessMode.fromStored("UNKNOWN"))
    }

    @Test
    fun storedRootModeIsRestored() {
        assertEquals(SetupAccessMode.ROOT, SetupAccessMode.fromStored("ROOT"))
    }
}
