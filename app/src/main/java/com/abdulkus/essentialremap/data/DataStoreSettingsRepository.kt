package com.abdulkus.essentialremap.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.abdulkus.essentialremap.domain.ActionKind
import com.abdulkus.essentialremap.domain.AppSettings
import com.abdulkus.essentialremap.domain.ConfiguredAction
import com.abdulkus.essentialremap.domain.HapticStrength
import com.abdulkus.essentialremap.domain.KeyIdentity
import com.abdulkus.essentialremap.domain.PressAction
import com.abdulkus.essentialremap.domain.RequestMethod
import com.abdulkus.essentialremap.domain.SoundMode
import com.abdulkus.essentialremap.domain.SystemAction
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val STORE_NAME = "essential_key_settings"
private const val LEGACY_STORE_NAME = "essential_key"

private val Context.settingsDataStore by preferencesDataStore(
    name = STORE_NAME,
    produceMigrations = { context -> listOf(LegacySettingsMigration(context)) },
)

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::preferencesToSettings)

    override suspend fun saveConfiguration(
        hapticStrength: HapticStrength,
        actions: Map<PressAction, ConfiguredAction>,
        runWhileLocked: Map<PressAction, Boolean>,
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.COMMON_HAPTIC] = hapticStrength.name
            PressAction.entries.forEach { action ->
                writeAction(preferences, action, actions.getValue(action))
                preferences[Keys.runWhileLocked(action)] = runWhileLocked[action] == true
            }
        }
    }

    override suspend fun setLearning(learning: Boolean) {
        dataStore.edit { it[Keys.LEARNING] = learning }
    }

    override suspend fun saveMappedKey(identity: KeyIdentity) {
        dataStore.edit { preferences ->
            preferences[Keys.KEY_PRESENT] = true
            preferences[Keys.KEY_CODE] = identity.keyCode
            preferences[Keys.SCAN_CODE] = identity.scanCode
            preferences[Keys.SOURCE] = identity.source
            preferences[Keys.DEVICE_ID] = identity.deviceId
            preferences[Keys.DESCRIPTOR] = identity.descriptor
            preferences[Keys.VENDOR_ID] = identity.vendorId
            preferences[Keys.PRODUCT_ID] = identity.productId
            preferences[Keys.LEARNING] = false
        }
    }

    override suspend fun saveResult(action: PressAction, result: String) {
        dataStore.edit { it[Keys.result(action)] = result }
    }
}

private fun writeAction(
    preferences: androidx.datastore.preferences.core.MutablePreferences,
    gesture: PressAction,
    action: ConfiguredAction,
) {
    preferences[Keys.actionType(gesture)] = action.kind.name
    preferences.remove(Keys.actionValue(gesture))
    preferences.remove(Keys.actionLabel(gesture))
    preferences.remove(Keys.method(gesture))
    preferences.remove(Keys.url(gesture))
    preferences.remove(Keys.actionBaseUrl(gesture))
    when (action) {
        ConfiguredAction.None,
        ConfiguredAction.Flashlight,
        ConfiguredAction.ToggleSilent,
        -> Unit
        is ConfiguredAction.Http -> {
            preferences[Keys.actionBaseUrl(gesture)] = action.baseUrl.trim().trimEnd('/')
            preferences[Keys.method(gesture)] = action.method.name
            preferences[Keys.actionValue(gesture)] = action.endpoint.trim()
        }
        is ConfiguredAction.LaunchApp -> {
            preferences[Keys.actionValue(gesture)] = action.packageName
            preferences[Keys.actionLabel(gesture)] = action.label
        }
        is ConfiguredAction.OpenUrl -> {
            preferences[Keys.actionValue(gesture)] = action.url.trim()
        }
        is ConfiguredAction.PerformSystemAction -> {
            preferences[Keys.actionValue(gesture)] = action.action.name
        }
        is ConfiguredAction.SetSoundMode -> {
            preferences[Keys.actionValue(gesture)] = action.mode.name
        }
    }
}

internal fun preferencesToSettings(preferences: Preferences): AppSettings {
    val mappedKey = if (preferences[Keys.KEY_PRESENT] == true) {
        KeyIdentity(
            keyCode = preferences[Keys.KEY_CODE] ?: 0,
            scanCode = preferences[Keys.SCAN_CODE] ?: 0,
            source = preferences[Keys.SOURCE] ?: 0,
            deviceId = preferences[Keys.DEVICE_ID] ?: -1,
            descriptor = preferences[Keys.DESCRIPTOR].orEmpty(),
            vendorId = preferences[Keys.VENDOR_ID] ?: 0,
            productId = preferences[Keys.PRODUCT_ID] ?: 0,
        )
    } else {
        null
    }
    val defaults = AppSettings.defaultActions()
    val actions = PressAction.entries.associateWith { gesture ->
        readAction(preferences, gesture, defaults.getValue(gesture))
    }
    val runWhileLocked = PressAction.entries.associateWith { gesture ->
        preferences[Keys.runWhileLocked(gesture)] ?: false
    }
    val results = PressAction.entries.associateWith { preferences[Keys.result(it)] }
    val commonHaptic = (
        preferences[Keys.COMMON_HAPTIC] ?: preferences[Keys.haptic(PressAction.SINGLE)]
        ).toEnumOrDefault(HapticStrength.MEDIUM)
    return AppSettings(
        mappedKey = mappedKey,
        hapticStrength = commonHaptic,
        actions = actions,
        runWhileLocked = runWhileLocked,
        results = results,
        learning = preferences[Keys.LEARNING] ?: false,
    )
}

private fun readAction(
    preferences: Preferences,
    gesture: PressAction,
    default: ConfiguredAction,
): ConfiguredAction {
    val defaultHttp = default as? ConfiguredAction.Http
    val baseUrl = preferences[Keys.actionBaseUrl(gesture)]
        ?: preferences[Keys.BASE_URL]
        ?: defaultHttp?.baseUrl
        ?: ConfiguredAction.Http.DEFAULT_BASE_URL
    val storedType = preferences[Keys.actionType(gesture)]
    if (storedType == null) {
        val legacyUrl = preferences[Keys.url(gesture)] ?: return default
        return ConfiguredAction.Http(
            baseUrl = baseUrl,
            method = preferences[Keys.method(gesture)].toEnumOrDefault(RequestMethod.GET),
            endpoint = legacyUrl,
        )
    }
    val value = preferences[Keys.actionValue(gesture)].orEmpty()
    return when (storedType.toEnumOrDefault(ActionKind.NONE)) {
        ActionKind.NONE -> ConfiguredAction.None
        ActionKind.HTTP -> ConfiguredAction.Http(
            baseUrl = baseUrl,
            method = preferences[Keys.method(gesture)].toEnumOrDefault(RequestMethod.GET),
            endpoint = value,
        )
        ActionKind.FLASHLIGHT -> ConfiguredAction.Flashlight
        ActionKind.SOUND_MODE -> ConfiguredAction.SetSoundMode(value.toEnumOrDefault(SoundMode.SILENT))
        ActionKind.TOGGLE_SILENT -> ConfiguredAction.SetSoundMode(SoundMode.TOGGLE_SILENT_NORMAL)
        ActionKind.LAUNCH_APP -> ConfiguredAction.LaunchApp(
            packageName = value,
            label = preferences[Keys.actionLabel(gesture)].orEmpty(),
        )
        ActionKind.OPEN_URL -> ConfiguredAction.OpenUrl(value)
        ActionKind.SYSTEM -> ConfiguredAction.PerformSystemAction(
            value.toEnumOrDefault(SystemAction.SCREENSHOT),
        )
    }
}

private object Keys {
    val MIGRATED = booleanPreferencesKey("legacy_migrated")
    val BASE_URL = stringPreferencesKey("base_url")
    val COMMON_HAPTIC = stringPreferencesKey("common_haptic")
    val LEARNING = booleanPreferencesKey("learning")
    val KEY_PRESENT = booleanPreferencesKey("key_present")
    val KEY_CODE = intPreferencesKey("key_code")
    val SCAN_CODE = intPreferencesKey("scan_code")
    val SOURCE = intPreferencesKey("source")
    val DEVICE_ID = intPreferencesKey("device_id")
    val DESCRIPTOR = stringPreferencesKey("descriptor")
    val VENDOR_ID = intPreferencesKey("vendor_id")
    val PRODUCT_ID = intPreferencesKey("product_id")
    fun actionType(action: PressAction) = stringPreferencesKey("${action.name}_action_type")
    fun actionValue(action: PressAction) = stringPreferencesKey("${action.name}_action_value")
    fun actionLabel(action: PressAction) = stringPreferencesKey("${action.name}_action_label")
    fun actionBaseUrl(action: PressAction) = stringPreferencesKey("${action.name}_http_base_url")
    fun runWhileLocked(action: PressAction) = booleanPreferencesKey("${action.name}_run_while_locked")
    fun method(action: PressAction) = stringPreferencesKey("${action.name}_method")
    fun url(action: PressAction) = stringPreferencesKey("${action.name}_url")
    fun haptic(action: PressAction) = stringPreferencesKey("${action.name}_haptic")
    fun result(action: PressAction) = stringPreferencesKey("${action.name}_result")
}

private class LegacySettingsMigration(context: Context) : DataMigration<Preferences> {
    private val legacy = context.getSharedPreferences(LEGACY_STORE_NAME, Context.MODE_PRIVATE)

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[Keys.MIGRATED] != true && legacy.all.isNotEmpty()

    override suspend fun migrate(currentData: Preferences): Preferences {
        val migrated = currentData.toMutablePreferences()
        migrated[Keys.MIGRATED] = true
        copyBoolean(migrated, "learning", Keys.LEARNING)
        copyBoolean(migrated, "key_present", Keys.KEY_PRESENT)
        copyInt(migrated, "key_code", Keys.KEY_CODE)
        copyInt(migrated, "scan_code", Keys.SCAN_CODE)
        copyInt(migrated, "source", Keys.SOURCE)
        copyInt(migrated, "device_id", Keys.DEVICE_ID)
        copyString(migrated, "descriptor", Keys.DESCRIPTOR)
        copyInt(migrated, "vendor_id", Keys.VENDOR_ID)
        copyInt(migrated, "product_id", Keys.PRODUCT_ID)
        PressAction.entries.forEach { action ->
            copyString(migrated, "${action.name}_method", Keys.method(action))
            copyString(migrated, "${action.name}_url", Keys.url(action))
            copyString(migrated, "${action.name}_result", Keys.result(action))
        }
        return migrated
    }

    override suspend fun cleanUp() = Unit

    private fun copyBoolean(target: androidx.datastore.preferences.core.MutablePreferences, old: String, new: Preferences.Key<Boolean>) {
        if (legacy.contains(old)) target[new] = legacy.getBoolean(old, false)
    }

    private fun copyInt(target: androidx.datastore.preferences.core.MutablePreferences, old: String, new: Preferences.Key<Int>) {
        if (legacy.contains(old)) target[new] = legacy.getInt(old, 0)
    }

    private fun copyString(target: androidx.datastore.preferences.core.MutablePreferences, old: String, new: Preferences.Key<String>) {
        legacy.getString(old, null)?.let { target[new] = it }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
