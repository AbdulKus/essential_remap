package com.abdulkus.essentialremap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abdulkus.essentialremap.data.SettingsRepository
import com.abdulkus.essentialremap.domain.ActionKind
import com.abdulkus.essentialremap.domain.AppSettings
import com.abdulkus.essentialremap.domain.ConfiguredAction
import com.abdulkus.essentialremap.domain.HapticStrength
import com.abdulkus.essentialremap.domain.PressAction
import com.abdulkus.essentialremap.domain.RequestMethod
import com.abdulkus.essentialremap.domain.SoundMode
import com.abdulkus.essentialremap.domain.SystemAction
import com.abdulkus.essentialremap.haptics.HapticEngine
import com.abdulkus.essentialremap.haptics.HapticResult
import com.abdulkus.essentialremap.platform.AccessibilityStatus
import com.abdulkus.essentialremap.platform.LaunchableApp
import com.abdulkus.essentialremap.platform.LaunchableAppsReader
import com.abdulkus.essentialremap.setup.EssentialKeySetupController
import com.abdulkus.essentialremap.setup.EssentialKeySetupCoordinator
import com.abdulkus.essentialremap.setup.EssentialKeySetupState
import com.abdulkus.essentialremap.setup.NothingPackageStatus
import com.abdulkus.essentialremap.setup.PackageOperation
import java.net.URI
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapperUiState(
    val settings: AppSettings = AppSettings(),
    val draftHapticStrength: HapticStrength = HapticStrength.MEDIUM,
    val draftActions: Map<PressAction, ConfiguredAction> = AppSettings.defaultActions(),
    val draftRunWhileLocked: Map<PressAction, Boolean> = AppSettings.defaultRunWhileLocked(),
    val serviceEnabled: Boolean = false,
    val competingServices: List<String> = emptyList(),
    val setup: EssentialKeySetupState = EssentialKeySetupState(),
    val launchableApps: List<LaunchableApp> = emptyList(),
    val notificationPolicyAccess: Boolean = false,
    val developerOptionsEnabled: Boolean = false,
    val baseUrlErrors: Map<PressAction, String> = emptyMap(),
    val validationErrors: Map<PressAction, String> = emptyMap(),
    val initialized: Boolean = false,
    val saving: Boolean = false,
) {
    val dirty: Boolean get() =
        draftHapticStrength != settings.hapticStrength ||
            draftActions != settings.actions ||
            draftRunWhileLocked != settings.runWhileLocked

    val keyReleased: Boolean get() = setup.packageStatus == NothingPackageStatus.DISABLED
    val readyToMap: Boolean get() =
        keyReleased && serviceEnabled && competingServices.isEmpty()
}

class MapperViewModel(
    private val repository: SettingsRepository,
    private val hapticEngine: HapticEngine,
    private val setupCoordinator: EssentialKeySetupController,
    launchableApps: List<LaunchableApp> = emptyList(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MapperUiState(launchableApps = launchableApps),
    )
    val uiState: StateFlow<MapperUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update { current ->
                    val preserveDraft = current.initialized && current.dirty
                    current.copy(
                        settings = settings,
                        draftHapticStrength = if (preserveDraft) current.draftHapticStrength else settings.hapticStrength,
                        draftActions = if (preserveDraft) current.draftActions else settings.actions,
                        draftRunWhileLocked = if (preserveDraft) {
                            current.draftRunWhileLocked
                        } else {
                            settings.runWhileLocked
                        },
                        initialized = true,
                    )
                }
            }
        }
        viewModelScope.launch {
            setupCoordinator.state.collect { setup ->
                _uiState.update { it.copy(setup = setup) }
            }
        }
    }

    fun refreshSetup() = setupCoordinator.refresh()

    fun showMessage(message: String) {
        _messages.tryEmit(message)
    }

    fun updateAccessibilityStatus(status: AccessibilityStatus) {
        _uiState.update {
            it.copy(
                serviceEnabled = status.serviceEnabled,
                competingServices = status.competingKeyServices,
            )
        }
        if (!status.serviceEnabled && _uiState.value.settings.learning) cancelLearning()
    }

    fun updateNotificationPolicyAccess(granted: Boolean) {
        _uiState.update { it.copy(notificationPolicyAccess = granted) }
    }

    fun updateDeveloperOptionsStatus(enabled: Boolean) {
        _uiState.update { it.copy(developerOptionsEnabled = enabled) }
    }

    fun startPackageSetup(operation: PackageOperation) {
        setupCoordinator.start(operation)
    }

    fun submitPairingCode(code: String) = setupCoordinator.submitPairingCode(code)

    fun cancelPackageSetup() = setupCoordinator.cancel()

    fun diagnosticReport(): String = buildString {
        append(setupCoordinator.diagnosticReport().trimEnd())
        appendLine()
        appendLine("--- Current app state ---")
        appendLine(
            "Setup: phase=${_uiState.value.setup.phase} " +
                "operation=${_uiState.value.setup.operation} " +
                "package=${_uiState.value.setup.packageStatus} " +
                "screenOff=${_uiState.value.setup.screenOffAccessGranted}",
        )
        appendLine("Accessibility: enabled=${_uiState.value.serviceEnabled}")
        appendLine("Competing key services: ${_uiState.value.competingServices.joinToString().ifBlank { "none" }}")
    }

    fun clearDiagnostics() = setupCoordinator.clearDiagnostics()

    fun updateActionKind(gesture: PressAction, kind: ActionKind) {
        val current = _uiState.value.draftActions.getValue(gesture)
        val replacement: ConfiguredAction = when (kind) {
            ActionKind.NONE -> ConfiguredAction.None
            ActionKind.HTTP -> current as? ConfiguredAction.Http ?: ConfiguredAction.Http()
            ActionKind.FLASHLIGHT -> ConfiguredAction.Flashlight
            ActionKind.SOUND_MODE -> current as? ConfiguredAction.SetSoundMode
                ?: ConfiguredAction.SetSoundMode()
            ActionKind.TOGGLE_SILENT ->
                ConfiguredAction.SetSoundMode(SoundMode.TOGGLE_SILENT_NORMAL)
            ActionKind.LAUNCH_APP -> current as? ConfiguredAction.LaunchApp
                ?: _uiState.value.launchableApps.firstOrNull()?.let {
                    ConfiguredAction.LaunchApp(it.packageName, it.label)
                }
                ?: ConfiguredAction.LaunchApp()
            ActionKind.OPEN_URL -> current as? ConfiguredAction.OpenUrl ?: ConfiguredAction.OpenUrl()
            ActionKind.SYSTEM -> current as? ConfiguredAction.PerformSystemAction
                ?: ConfiguredAction.PerformSystemAction()
        }
        updateAction(gesture, replacement)
    }

    fun updateHttpMethod(gesture: PressAction, method: RequestMethod) {
        val current = _uiState.value.draftActions[gesture] as? ConfiguredAction.Http ?: return
        updateAction(gesture, current.copy(method = method))
    }

    fun updateHttpBaseUrl(gesture: PressAction, baseUrl: String) {
        val current = _uiState.value.draftActions[gesture] as? ConfiguredAction.Http ?: return
        updateAction(gesture, current.copy(baseUrl = baseUrl))
    }

    fun updateActionValue(gesture: PressAction, value: String) {
        when (val current = _uiState.value.draftActions.getValue(gesture)) {
            is ConfiguredAction.Http -> updateAction(gesture, current.copy(endpoint = value))
            is ConfiguredAction.OpenUrl -> updateAction(gesture, current.copy(url = value))
            else -> Unit
        }
    }

    fun updateSoundMode(gesture: PressAction, mode: SoundMode) {
        updateAction(gesture, ConfiguredAction.SetSoundMode(mode))
    }

    fun updateSystemAction(gesture: PressAction, action: SystemAction) {
        updateAction(gesture, ConfiguredAction.PerformSystemAction(action))
    }

    fun updateLaunchApp(gesture: PressAction, app: LaunchableApp) {
        updateAction(gesture, ConfiguredAction.LaunchApp(app.packageName, app.label))
    }

    fun updateHaptic(strength: HapticStrength) {
        _uiState.update { it.copy(draftHapticStrength = strength) }
    }

    fun updateRunWhileLocked(gesture: PressAction, enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                draftRunWhileLocked = current.draftRunWhileLocked + (gesture to enabled),
            )
        }
    }

    fun setRemappingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setRemappingEnabled(enabled) }
                .onFailure { _messages.emit("Could not change remapping state") }
        }
    }

    fun save() {
        val state = _uiState.value
        val baseUrlErrors = state.draftActions.mapNotNull { (gesture, action) ->
            (action as? ConfiguredAction.Http)?.let { http ->
                validateBaseUrl(http.baseUrl)?.let { gesture to it }
            }
        }.toMap()
        val errors = state.draftActions.mapNotNull { (gesture, action) ->
            validateAction(action)?.let { gesture to it }
        }.toMap()
        if (baseUrlErrors.isNotEmpty() || errors.isNotEmpty()) {
            _uiState.update { it.copy(baseUrlErrors = baseUrlErrors, validationErrors = errors) }
            _messages.tryEmit("Fix the highlighted actions before saving")
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    saving = true,
                    baseUrlErrors = emptyMap(),
                    validationErrors = emptyMap(),
                )
            }
            runCatching {
                repository.saveConfiguration(
                    hapticStrength = _uiState.value.draftHapticStrength,
                    actions = _uiState.value.draftActions,
                    runWhileLocked = _uiState.value.draftRunWhileLocked,
                )
            }
                .onSuccess { _messages.emit("Actions saved") }
                .onFailure { _messages.emit("Could not save actions") }
            _uiState.update { it.copy(saving = false) }
        }
    }

    fun startLearning() {
        if (!_uiState.value.keyReleased) {
            _messages.tryEmit("Release the Essential Key first")
            return
        }
        if (!_uiState.value.serviceEnabled) {
            _messages.tryEmit("Enable the accessibility service first")
            return
        }
        viewModelScope.launch { repository.setLearning(true) }
    }

    fun cancelLearning() {
        viewModelScope.launch { repository.setLearning(false) }
    }

    fun previewHaptic(strength: HapticStrength) {
        when (hapticEngine.perform(strength)) {
            HapticResult.PLAYED,
            HapticResult.OFF,
            -> Unit
            HapticResult.UNAVAILABLE -> _messages.tryEmit("This device has no vibrator")
            HapticResult.SYSTEM_DISABLED -> _messages.tryEmit("Enable touch feedback in system settings")
            HapticResult.FAILED -> _messages.tryEmit("Haptic feedback could not be played")
        }
    }

    private fun updateAction(gesture: PressAction, action: ConfiguredAction) {
        _uiState.update { current ->
            current.copy(
                draftActions = current.draftActions + (gesture to action),
                baseUrlErrors = current.baseUrlErrors - gesture,
                validationErrors = current.validationErrors - gesture,
            )
        }
    }

    internal fun validateBaseUrl(value: String): String? {
        if (value.isBlank()) return "Enter the HTTP base URL"
        return validateAbsoluteUrl(value)
    }

    internal fun validateAction(action: ConfiguredAction): String? = when (action) {
        ConfiguredAction.None,
        ConfiguredAction.Flashlight,
        ConfiguredAction.ToggleSilent,
        is ConfiguredAction.SetSoundMode,
        is ConfiguredAction.PerformSystemAction,
        -> null
        is ConfiguredAction.Http -> validateEndpoint(action.endpoint)
        is ConfiguredAction.LaunchApp -> if (action.packageName.isBlank()) "Choose an app" else null
        is ConfiguredAction.OpenUrl -> validateAbsoluteUrl(action.url)
    }

    internal fun validateEndpoint(value: String): String? {
        if (value.isBlank()) return "Enter a path or complete URL"
        if (value.startsWith("/")) return null
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return "Start paths with / or enter a complete URL"
        }
        return validateAbsoluteUrl(value)
    }

    private fun validateAbsoluteUrl(value: String): String? {
        if (value.isBlank()) return "Enter a complete URL"
        return try {
            val uri = URI(value.trim())
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                "Use a complete http:// or https:// URL"
            } else {
                null
            }
        } catch (_: Exception) {
            "Enter a valid URL"
        }
    }

    class Factory(
        private val repository: SettingsRepository,
        private val hapticEngine: HapticEngine,
        private val setupCoordinator: EssentialKeySetupCoordinator,
        private val launchableAppsReader: LaunchableAppsReader,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MapperViewModel(
                repository,
                hapticEngine,
                setupCoordinator,
                launchableAppsReader.read(),
            ) as T
    }
}
