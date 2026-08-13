package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.domain.ActionFeedbackPolicy
import com.abdulkus.essentialremap.domain.ConfiguredAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionFeedbackPolicyTest {
    @Test
    fun noActionDoesNotProduceHapticFeedback() {
        assertFalse(ActionFeedbackPolicy.shouldPerformHaptic(ConfiguredAction.None))
    }

    @Test
    fun configuredActionStillProducesHapticFeedback() {
        assertTrue(ActionFeedbackPolicy.shouldPerformHaptic(ConfiguredAction.Flashlight))
    }
}
