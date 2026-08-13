package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.domain.KeyIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyIdentityTest {
    @Test
    fun unknownKeysUseScanCodeAndSource() {
        val mapped = identity(keyCode = 0, scanCode = 703, source = 0x101)

        assertTrue(mapped.matches(identity(keyCode = 0, scanCode = 703, source = 0x101, deviceId = 9)))
        assertFalse(mapped.matches(identity(keyCode = 0, scanCode = 704, source = 0x101)))
        assertFalse(mapped.matches(identity(keyCode = 0, scanCode = 703, source = 0x201)))
    }

    @Test
    fun unknownZeroScanCodeFallsBackToDescriptor() {
        val mapped = identity(keyCode = 0, scanCode = 0, descriptor = "nothing-key", deviceId = 2)

        assertTrue(mapped.matches(identity(keyCode = 0, scanCode = 0, descriptor = "nothing-key", deviceId = 7)))
        assertFalse(mapped.matches(identity(keyCode = 0, scanCode = 0, descriptor = "other", deviceId = 2)))
    }

    private fun identity(
        keyCode: Int,
        scanCode: Int,
        source: Int = 0x101,
        deviceId: Int = 2,
        descriptor: String = "device",
    ) = KeyIdentity(keyCode, scanCode, source, deviceId, descriptor, 1, 2)
}
