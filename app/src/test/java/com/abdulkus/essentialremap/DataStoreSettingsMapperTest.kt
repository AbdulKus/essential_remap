package com.abdulkus.essentialremap

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.abdulkus.essentialremap.data.preferencesToSettings
import com.abdulkus.essentialremap.domain.ConfiguredAction
import com.abdulkus.essentialremap.domain.HapticStrength
import com.abdulkus.essentialremap.domain.PressAction
import com.abdulkus.essentialremap.domain.RequestMethod
import com.abdulkus.essentialremap.domain.SoundMode
import com.abdulkus.essentialremap.domain.SystemAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DataStoreSettingsMapperTest {
    @Test
    fun storedValuesMapToTypedSettings() {
        val preferences = mutablePreferencesOf(
            booleanPreferencesKey("key_present") to true,
            intPreferencesKey("key_code") to 0,
            intPreferencesKey("scan_code") to 703,
            stringPreferencesKey("SINGLE_method") to "POST",
            stringPreferencesKey("SINGLE_url") to "http://192.168.1.5/hook",
            stringPreferencesKey("SINGLE_haptic") to "STRONG",
            booleanPreferencesKey("DOUBLE_run_while_locked") to true,
            booleanPreferencesKey("remapping_enabled") to false,
            stringPreferencesKey("base_url") to "http://home-automation.local",
        )

        val settings = preferencesToSettings(preferences)

        assertEquals(703, settings.mappedKey?.scanCode)
        val single = settings.actions.getValue(PressAction.SINGLE) as ConfiguredAction.Http
        assertEquals(RequestMethod.POST, single.method)
        assertEquals("http://home-automation.local", single.baseUrl)
        assertEquals(HapticStrength.STRONG, settings.hapticStrength)
        assertEquals(true, settings.runWhileLocked.getValue(PressAction.DOUBLE))
        assertEquals(false, settings.runWhileLocked.getValue(PressAction.SINGLE))
        assertEquals(false, settings.remappingEnabled)
        assertEquals(
            ConfiguredAction.PerformSystemAction(SystemAction.CIRCLE_TO_SEARCH),
            settings.actions.getValue(PressAction.DOUBLE),
        )
        assertNotNull(settings.results)
    }

    @Test
    fun typedActionValuesMapFromPreferences() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("DOUBLE_action_type") to "LAUNCH_APP",
            stringPreferencesKey("DOUBLE_action_value") to "com.example.camera",
            stringPreferencesKey("DOUBLE_action_label") to "Camera",
            stringPreferencesKey("SINGLE_action_type") to "HTTP",
            stringPreferencesKey("SINGLE_action_value") to "/single",
            stringPreferencesKey("SINGLE_http_base_url") to "https://single.example",
        )

        val settings = preferencesToSettings(preferences)

        assertEquals(
            ConfiguredAction.LaunchApp("com.example.camera", "Camera"),
            settings.actions.getValue(PressAction.DOUBLE),
        )
        assertEquals(
            ConfiguredAction.Http(baseUrl = "https://single.example", endpoint = "/single"),
            settings.actions.getValue(PressAction.SINGLE),
        )
    }

    @Test
    fun legacyToggleSilentActionMigratesIntoSoundMode() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("LONG_action_type") to "TOGGLE_SILENT",
        )

        val settings = preferencesToSettings(preferences)

        assertEquals(
            ConfiguredAction.SetSoundMode(SoundMode.TOGGLE_SILENT_NORMAL),
            settings.actions.getValue(PressAction.LONG),
        )
    }
}
