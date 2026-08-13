package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.domain.LockScreenExecutionPolicy
import com.abdulkus.essentialremap.domain.PressAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenExecutionPolicyTest {
    @Test
    fun unlockedActionsAlwaysRun() {
        assertTrue(
            LockScreenExecutionPolicy.shouldRun(
                action = PressAction.SINGLE,
                startedWhileLocked = false,
                runWhileLocked = emptyMap(),
            ),
        )
    }

    @Test
    fun lockedActionsRequireTheirOwnOption() {
        val enabled = mapOf(
            PressAction.SINGLE to false,
            PressAction.DOUBLE to true,
            PressAction.LONG to false,
        )

        assertFalse(
            LockScreenExecutionPolicy.shouldRun(
                action = PressAction.SINGLE,
                startedWhileLocked = true,
                runWhileLocked = enabled,
            ),
        )
        assertTrue(
            LockScreenExecutionPolicy.shouldRun(
                action = PressAction.DOUBLE,
                startedWhileLocked = true,
                runWhileLocked = enabled,
            ),
        )
    }
}
