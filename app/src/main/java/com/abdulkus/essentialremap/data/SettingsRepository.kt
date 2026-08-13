package com.abdulkus.essentialremap.data

import com.abdulkus.essentialremap.domain.AppSettings
import com.abdulkus.essentialremap.domain.ConfiguredAction
import com.abdulkus.essentialremap.domain.HapticStrength
import com.abdulkus.essentialremap.domain.KeyIdentity
import com.abdulkus.essentialremap.domain.PressAction
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun saveConfiguration(
        hapticStrength: HapticStrength,
        actions: Map<PressAction, ConfiguredAction>,
    )
    suspend fun setLearning(learning: Boolean)
    suspend fun saveMappedKey(identity: KeyIdentity)
    suspend fun saveResult(action: PressAction, result: String)
}
