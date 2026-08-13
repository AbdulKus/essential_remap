package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.domain.PressAction
import com.abdulkus.essentialremap.domain.ScreenOffAfterWakePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOffAfterWakePolicyTest {
    private val enabled = mapOf(PressAction.SINGLE to true)

    @Test
    fun turnsScreenOffOnlyWhenTheSamePressStartedWithScreenOff() {
        assertTrue(
            ScreenOffAfterWakePolicy.shouldTurnOff(
                action = PressAction.SINGLE,
                startedScreenOff = true,
                turnScreenOffAfterWake = enabled,
            ),
        )
        assertFalse(
            ScreenOffAfterWakePolicy.shouldTurnOff(
                action = PressAction.SINGLE,
                startedScreenOff = false,
                turnScreenOffAfterWake = enabled,
            ),
        )
    }

    @Test
    fun settingIsIndependentForEachGesture() {
        assertFalse(
            ScreenOffAfterWakePolicy.shouldTurnOff(
                action = PressAction.DOUBLE,
                startedScreenOff = true,
                turnScreenOffAfterWake = enabled,
            ),
        )
    }
}
