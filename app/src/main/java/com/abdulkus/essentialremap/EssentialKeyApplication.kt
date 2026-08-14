package com.abdulkus.essentialremap

import android.app.Application
import com.abdulkus.essentialremap.data.DataStoreSettingsRepository
import com.abdulkus.essentialremap.data.SettingsRepository
import com.abdulkus.essentialremap.haptics.AndroidHapticEngine
import com.abdulkus.essentialremap.haptics.HapticEngine
import com.abdulkus.essentialremap.platform.LaunchableAppsReader
import com.abdulkus.essentialremap.platform.TorchController
import com.abdulkus.essentialremap.setup.EssentialKeySetupCoordinator
import com.abdulkus.essentialremap.setup.SetupDiagnostics

class EssentialKeyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val torchController = TorchController(this)
        val diagnostics = SetupDiagnostics(this)
        diagnostics.log("Runtime: application process started pid=${android.os.Process.myPid()}")
        container = AppContainer(
            repository = DataStoreSettingsRepository(this),
            hapticEngine = AndroidHapticEngine(this),
            torchController = torchController,
            setupCoordinator = EssentialKeySetupCoordinator(this, diagnostics),
            launchableAppsReader = LaunchableAppsReader(this),
            diagnostics = diagnostics,
        )
    }
}

data class AppContainer(
    val repository: SettingsRepository,
    val hapticEngine: HapticEngine,
    val torchController: TorchController,
    val setupCoordinator: EssentialKeySetupCoordinator,
    val launchableAppsReader: LaunchableAppsReader,
    val diagnostics: SetupDiagnostics,
)
