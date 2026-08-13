package com.abdulkus.essentialremap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeyEventGateTest {
    @Test
    fun duplicateDownFromSecondSourceIsCollapsed() {
        val gate = PhysicalKeyEventGate()

        assertEquals(PhysicalKeyEventGate.DownResult.NEW, gate.onDown(10_000_000L))
        assertEquals(
            PhysicalKeyEventGate.DownResult.ACTIVE_DUPLICATE,
            gate.onDown(10_000_800L),
        )
        assertTrue(gate.onUp(10_001_000L))
        assertFalse(gate.onUp(10_000_000L))
    }

    @Test
    fun delayedDuplicateAfterFirstUpDoesNotBecomeAnotherPress() {
        val gate = PhysicalKeyEventGate()

        assertEquals(PhysicalKeyEventGate.DownResult.NEW, gate.onDown(20_000_000L))
        assertTrue(gate.onUp(20_000_000L))
        assertEquals(
            PhysicalKeyEventGate.DownResult.COMPLETED_DUPLICATE,
            gate.onDown(20_001_000L),
        )
        assertFalse(gate.onUp(20_001_000L))
    }

    @Test
    fun aRealSecondPressHasItsOwnDownTime() {
        val gate = PhysicalKeyEventGate()

        gate.onDown(30_000_000L)
        gate.onUp(30_000_000L)

        assertEquals(PhysicalKeyEventGate.DownResult.NEW, gate.onDown(80_000_000L))
        assertTrue(gate.onUp(80_000_000L))
    }
}
