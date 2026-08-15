package com.abdulkus.essentialremap.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    @Test
    fun newerVersionComparisonHandlesVPrefixAndMissingSegments() {
        assertTrue(UpdatePolicy.isNewerVersion("v0.1.23", "0.1.22"))
        assertTrue(UpdatePolicy.isNewerVersion("1.0.1", "1.0"))
        assertFalse(UpdatePolicy.isNewerVersion("v0.1.22", "0.1.22"))
        assertFalse(UpdatePolicy.isNewerVersion("0.1.21", "0.1.22"))
        assertFalse(UpdatePolicy.isNewerVersion("not-a-version", "0.1.22"))
    }

    @Test
    fun supportPromptShowsOnSecondThenEveryFiveConfiguredLaunches() {
        assertFalse(UpdatePolicy.shouldShowSupportPrompt(1))
        assertTrue(UpdatePolicy.shouldShowSupportPrompt(2))
        assertFalse(UpdatePolicy.shouldShowSupportPrompt(3))
        assertFalse(UpdatePolicy.shouldShowSupportPrompt(6))
        assertTrue(UpdatePolicy.shouldShowSupportPrompt(7))
        assertTrue(UpdatePolicy.shouldShowSupportPrompt(12))
        assertTrue(UpdatePolicy.shouldShowSupportPrompt(17))
    }
}
