package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.data.SettingsRepository
import com.abdulkus.essentialremap.domain.AppSettings
import com.abdulkus.essentialremap.domain.ConfiguredAction
import com.abdulkus.essentialremap.domain.HapticStrength
import com.abdulkus.essentialremap.domain.KeyIdentity
import com.abdulkus.essentialremap.domain.PressAction
import com.abdulkus.essentialremap.haptics.HapticEngine
import com.abdulkus.essentialremap.haptics.HapticResult
import com.abdulkus.essentialremap.platform.AccessibilityStatus
import com.abdulkus.essentialremap.setup.EssentialKeySetupController
import com.abdulkus.essentialremap.setup.EssentialKeySetupState
import com.abdulkus.essentialremap.setup.NothingPackageStatus
import com.abdulkus.essentialremap.setup.PackageOperation
import com.abdulkus.essentialremap.ui.MapperViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapperViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun editsRemainDraftUntilSave() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = MapperViewModel(repository, FakeHapticEngine(), FakeSetup())
        advanceUntilIdle()

        viewModel.updateActionValue(PressAction.SINGLE, "http://192.168.1.10/hook")
        assertTrue(viewModel.uiState.value.dirty)
        assertEquals(
            "/toggle-light",
            (repository.state.value.actions.getValue(PressAction.SINGLE) as ConfiguredAction.Http).endpoint,
        )

        viewModel.save()
        advanceUntilIdle()
        assertEquals(
            "http://192.168.1.10/hook",
            (repository.state.value.actions.getValue(PressAction.SINGLE) as ConfiguredAction.Http).endpoint,
        )
        assertFalse(viewModel.uiState.value.dirty)
    }

    @Test
    fun invalidUrlIsNotSaved() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = MapperViewModel(repository, FakeHapticEngine(), FakeSetup())
        advanceUntilIdle()

        viewModel.updateActionValue(PressAction.LONG, "not a url")
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.validationErrors.containsKey(PressAction.LONG))
        assertEquals(
            "/toggle-fan",
            (repository.state.value.actions.getValue(PressAction.LONG) as ConfiguredAction.Http).endpoint,
        )
    }

    @Test
    fun invalidGestureBaseUrlIsNotSaved() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = MapperViewModel(repository, FakeHapticEngine(), FakeSetup())
        advanceUntilIdle()

        viewModel.updateHttpBaseUrl(PressAction.DOUBLE, "not a url")
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.baseUrlErrors.containsKey(PressAction.DOUBLE))
        assertEquals(
            ConfiguredAction.Http.DEFAULT_BASE_URL,
            (repository.state.value.actions.getValue(PressAction.DOUBLE) as ConfiguredAction.Http).baseUrl,
        )
    }

    @Test
    fun perGestureBaseUrlAndHapticRemainDraftUntilSave() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = MapperViewModel(repository, FakeHapticEngine(), FakeSetup())
        advanceUntilIdle()

        viewModel.updateHttpBaseUrl(PressAction.SINGLE, "http://192.168.1.20")
        viewModel.updateHaptic(HapticStrength.STRONG)

        assertEquals(
            ConfiguredAction.Http.DEFAULT_BASE_URL,
            (repository.state.value.actions.getValue(PressAction.SINGLE) as ConfiguredAction.Http).baseUrl,
        )
        assertEquals(HapticStrength.MEDIUM, repository.state.value.hapticStrength)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            "http://192.168.1.20",
            (repository.state.value.actions.getValue(PressAction.SINGLE) as ConfiguredAction.Http).baseUrl,
        )
        assertEquals(
            ConfiguredAction.Http.DEFAULT_BASE_URL,
            (repository.state.value.actions.getValue(PressAction.DOUBLE) as ConfiguredAction.Http).baseUrl,
        )
        assertEquals(HapticStrength.STRONG, repository.state.value.hapticStrength)
        assertFalse(viewModel.uiState.value.dirty)
    }

    @Test
    fun keyDetectionRequiresReleasedPackageAndAccessibility() = runTest(dispatcher) {
        val setup = FakeSetup()
        val viewModel = MapperViewModel(FakeRepository(), FakeHapticEngine(), setup)
        advanceUntilIdle()

        viewModel.updateAccessibilityStatus(
            AccessibilityStatus(serviceEnabled = true, competingKeyServices = emptyList()),
        )
        assertFalse(viewModel.uiState.value.readyToMap)

        setup.flow.value = EssentialKeySetupState(packageStatus = NothingPackageStatus.DISABLED)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.readyToMap)
    }

    private class FakeRepository : SettingsRepository {
        val state = MutableStateFlow(
            AppSettings(
                actions = mapOf(
                    PressAction.SINGLE to ConfiguredAction.Http(endpoint = "/toggle-light"),
                    PressAction.DOUBLE to ConfiguredAction.Http(endpoint = "/preset-ac"),
                    PressAction.LONG to ConfiguredAction.Http(endpoint = "/toggle-fan"),
                ),
            ),
        )
        override val settings = state
        override suspend fun saveConfiguration(
            hapticStrength: HapticStrength,
            actions: Map<PressAction, ConfiguredAction>,
        ) {
            state.value = state.value.copy(
                hapticStrength = hapticStrength,
                actions = actions,
            )
        }

        override suspend fun setLearning(learning: Boolean) {
            state.value = state.value.copy(learning = learning)
        }

        override suspend fun saveMappedKey(identity: KeyIdentity) {
            state.value = state.value.copy(mappedKey = identity, learning = false)
        }

        override suspend fun saveResult(action: PressAction, result: String) {
            state.value = state.value.copy(results = state.value.results + (action to result))
        }
    }

    private class FakeHapticEngine : HapticEngine {
        override fun perform(strength: HapticStrength) = HapticResult.PLAYED
    }

    private class FakeSetup : EssentialKeySetupController {
        val flow = MutableStateFlow(EssentialKeySetupState())
        override val state = flow
        override fun refresh() = Unit
        override fun start(operation: PackageOperation) = Unit
        override fun submitPairingCode(code: String) = Unit
        override fun cancel() = Unit
        override fun diagnosticReport() = ""
        override fun clearDiagnostics() = Unit
    }
}
