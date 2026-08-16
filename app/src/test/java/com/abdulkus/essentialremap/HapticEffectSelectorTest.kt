package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.domain.HapticStrength
import com.abdulkus.essentialremap.haptics.HapticEffectSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticEffectSelectorTest {
    @Test
    fun strengthsMapToDistinctProfiles() {
        val light = HapticEffectSelector.profile(HapticStrength.LIGHT)
        val medium = HapticEffectSelector.profile(HapticStrength.MEDIUM)
        val strong = HapticEffectSelector.profile(HapticStrength.STRONG)

        assertEquals(12L, light.durationMs)
        assertEquals(40, light.amplitude)
        assertEquals(30L, medium.durationMs)
        assertEquals(150, medium.amplitude)
        assertEquals(55L, strong.durationMs)
        assertEquals(255, strong.amplitude)

        assertTrue(light.durationMs < medium.durationMs)
        assertTrue(medium.durationMs < strong.durationMs)
        assertTrue(light.amplitude < medium.amplitude)
        assertTrue(medium.amplitude < strong.amplitude)
    }

    @Test(expected = IllegalStateException::class)
    fun offHasNoEffect() {
        HapticEffectSelector.profile(HapticStrength.OFF)
    }
}
