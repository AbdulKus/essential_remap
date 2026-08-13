package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.domain.HapticStrength
import com.abdulkus.essentialremap.haptics.HapticEffectSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticEffectSelectorTest {
    @Test
    fun strengthsMapToDistinctProfiles() {
        val light = HapticEffectSelector.profile(HapticStrength.LIGHT)
        val medium = HapticEffectSelector.profile(HapticStrength.MEDIUM)
        val strong = HapticEffectSelector.profile(HapticStrength.STRONG)

        assertEquals(18L, light.durationMs)
        assertEquals(70, light.amplitude)
        assertEquals(32L, medium.durationMs)
        assertEquals(150, medium.amplitude)
        assertEquals(50L, strong.durationMs)
        assertEquals(255, strong.amplitude)
    }

    @Test(expected = IllegalStateException::class)
    fun offHasNoEffect() {
        HapticEffectSelector.profile(HapticStrength.OFF)
    }
}
