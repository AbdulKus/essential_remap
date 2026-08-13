package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.domain.ActionUrlResolver
import com.abdulkus.essentialremap.domain.AppSettings
import com.abdulkus.essentialremap.domain.ConfiguredAction
import com.abdulkus.essentialremap.domain.PressAction
import com.abdulkus.essentialremap.domain.SystemAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionUrlResolverTest {
    @Test
    fun defaultsMatchUsefulEssentialKeyActions() {
        val settings = AppSettings()

        assertEquals(
            ConfiguredAction.PerformSystemAction(SystemAction.ASSISTANT),
            settings.actions.getValue(PressAction.SINGLE),
        )
        assertEquals(
            ConfiguredAction.PerformSystemAction(SystemAction.CIRCLE_TO_SEARCH),
            settings.actions.getValue(PressAction.DOUBLE),
        )
        assertEquals(
            ConfiguredAction.Flashlight,
            settings.actions.getValue(PressAction.LONG),
        )
    }

    @Test
    fun relativePathIsCombinedWithBaseUrl() {
        assertEquals(
            "http://192.168.0.108/toggle-light",
            ActionUrlResolver.resolve("http://192.168.0.108/", "/toggle-light"),
        )
    }

    @Test
    fun completeUrlOverridesBaseUrl() {
        assertEquals(
            "http://192.168.1.10/custom",
            ActionUrlResolver.resolve(
                "http://192.168.0.108",
                "http://192.168.1.10/custom",
            ),
        )
    }
}
