package com.abdulkus.essentialremap

import android.app.Application
import com.abdulkus.essentialremap.data.DataStoreSettingsRepository
import com.abdulkus.essentialremap.data.SettingsRepository
import com.abdulkus.essentialremap.haptics.AndroidHapticEngine
import com.abdulkus.essentialremap.haptics.HapticEngine
import com.abdulkus.essentialremap.platform.LaunchableAppsReader
import com.abdulkus.essentialremap.platform.TorchController
import com.abdulkus.essentialremap.setup.EssentialKeySetupCoordinator

class EssentialKeyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val torchController = TorchController(this)
        container = AppContainer(
            repository = DataStoreSettingsRepository(this),
            hapticEngine = AndroidHapticEngine(this),
            torchController = torchController,
            setupCoordinator = EssentialKeySetupCoordinator(this),
            launchableAppsReader = LaunchableAppsReader(this),
        )
    }
}

data class AppContainer(
    val repository: SettingsRepository,
    val hapticEngine: HapticEngine,
    val torchController: TorchController,
    val setupCoordinator: EssentialKeySetupCoordinator,
    val launchableAppsReader: LaunchableAppsReader,
)
