package com.abdulkus.essentialremap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureActionGateTest {
    @Test fun delayedShellGestureCannotRepeatAnAccessibilityAction() {
        val gate = GestureActionGate()
        assertTrue(gate.claim(1_000_000_000, 1_000_000_000))
        assertFalse(gate.claim(1_000_123_000, 1_000_123_000))
        assertTrue(gate.claim(2_000_000_000, 2_000_000_000))
    }

    @Test fun eitherPhysicalPressOfADoubleGestureIsAlreadyConsumed() {
        val gate = GestureActionGate()
        assertTrue(gate.claim(1_000_000_000, 1_200_000_000))
        assertFalse(gate.claim(1_200_100_000, 1_200_100_000))
        assertFalse(gate.claim(1_000_100_000, 1_000_100_000))
    }
}
