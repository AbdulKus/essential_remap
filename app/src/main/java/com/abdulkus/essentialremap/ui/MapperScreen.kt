package com.abdulkus.essentialremap.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdulkus.essentialremap.domain.ActionKind
import com.abdulkus.essentialremap.domain.ConfiguredAction
import com.abdulkus.essentialremap.domain.HapticStrength
import com.abdulkus.essentialremap.domain.PressAction
import com.abdulkus.essentialremap.domain.RequestMethod
import com.abdulkus.essentialremap.domain.SoundMode
import com.abdulkus.essentialremap.domain.SystemAction
import com.abdulkus.essentialremap.platform.LaunchableApp
import com.abdulkus.essentialremap.setup.NothingPackageStatus
import com.abdulkus.essentialremap.setup.PackageOperation
import com.abdulkus.essentialremap.setup.SetupPhase
import com.abdulkus.essentialremap.setup.ShellKeyMonitorCommands

@Composable
fun EssentialRemapApp(
    viewModel: MapperViewModel,
    preferences: UserPreferences,
    openAccessibilitySettings: () -> Unit,
    openNotificationPolicySettings: () -> Unit,
    openDeveloperOptions: () -> Unit,
    openAssistantSettings: () -> Unit,
    openAppInfo: () -> Unit,
    openDonate: () -> Unit,
    beginPackageSetup: (PackageOperation) -> Unit,
    copyText: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var languageCode by rememberSaveable { mutableStateOf(preferences.language?.code) }
    var onboardingComplete by rememberSaveable {
        mutableStateOf(preferences.onboardingComplete)
    }
    val language = AppLanguage.fromCode(languageCode)

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    if (language == null) {
        LanguageScreen { selected ->
            preferences.language = selected
            languageCode = selected.code
        }
        return
    }

    if (!onboardingComplete) {
        OnboardingScreen(
            language = language,
            state = state,
            openAccessibilitySettings = openAccessibilitySettings,
            openDeveloperOptions = openDeveloperOptions,
            beginPackageSetup = beginPackageSetup,
            submitPairingCode = viewModel::submitPairingCode,
            cancelPackageSetup = viewModel::cancelPackageSetup,
            copyText = copyText,
            copyDiagnostics = { copyText(viewModel.diagnosticReport()) },
            clearDiagnostics = viewModel::clearDiagnostics,
            finish = {
                preferences.onboardingComplete = true
                onboardingComplete = true
            },
        )
        return
    }

    HomeScreen(
        language = language,
        state = state,
        snackbar = snackbar,
        openAccessibilitySettings = openAccessibilitySettings,
        openNotificationPolicySettings = openNotificationPolicySettings,
        openDeveloperOptions = openDeveloperOptions,
        openAssistantSettings = openAssistantSettings,
        openAppInfo = openAppInfo,
        openDonate = openDonate,
        beginPackageSetup = beginPackageSetup,
        submitPairingCode = viewModel::submitPairingCode,
        cancelPackageSetup = viewModel::cancelPackageSetup,
        copyText = copyText,
        copyDiagnostics = { copyText(viewModel.diagnosticReport()) },
        clearDiagnostics = viewModel::clearDiagnostics,
        updateActionKind = viewModel::updateActionKind,
        updateHttpBaseUrl = viewModel::updateHttpBaseUrl,
        updateHttpMethod = viewModel::updateHttpMethod,
        updateActionValue = viewModel::updateActionValue,
        updateSoundMode = viewModel::updateSoundMode,
        updateSystemAction = viewModel::updateSystemAction,
        updateLaunchApp = viewModel::updateLaunchApp,
        updateRunWhileLocked = viewModel::updateRunWhileLocked,
        setRemappingEnabled = viewModel::setRemappingEnabled,
        updateHaptic = viewModel::updateHaptic,
        previewHaptic = viewModel::previewHaptic,
        save = viewModel::save,
        changeLanguage = { selected ->
            preferences.language = selected
            languageCode = selected.code
        },
        runSetupAgain = {
            preferences.onboardingComplete = false
            onboardingComplete = false
        },
    )
}

@Composable
private fun LanguageScreen(select: (AppLanguage) -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFD71920))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F2EF))
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center).widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EssentialMark()
                Spacer(Modifier.height(28.dp))
                Text(
                    "CHOOSE LANGUAGE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    "ВЫБЕРИТЕ ЯЗЫК",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF62625E),
                    modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
                )
                LanguageButton("Русский", "RU") { select(AppLanguage.RUSSIAN) }
                Spacer(Modifier.height(12.dp))
                LanguageButton("English", "EN") { select(AppLanguage.ENGLISH) }
            }
        }
    }
}

@Composable
private fun LanguageButton(label: String, code: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Color.Black, shape = CircleShape) {
                Text(
                    code,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Text(label, modifier = Modifier.padding(start = 16.dp).weight(1f), fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun EssentialMark() {
    Surface(color = Color.Black, shape = RoundedCornerShape(24.dp), modifier = Modifier.size(82.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(30.dp).background(Color(0xFFD71920), CircleShape))
            Box(Modifier.size(10.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
private fun OnboardingScreen(
    language: AppLanguage,
    state: MapperUiState,
    openAccessibilitySettings: () -> Unit,
    openDeveloperOptions: () -> Unit,
    beginPackageSetup: (PackageOperation) -> Unit,
    submitPairingCode: (String) -> Unit,
    cancelPackageSetup: () -> Unit,
    copyText: (String) -> Unit,
    copyDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit,
    finish: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(0) }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    val keyReleased = state.setup.packageStatus == NothingPackageStatus.DISABLED
    val screenOffReady = state.setup.screenOffAccessGranted
    val serviceReady = state.serviceEnabled && state.competingServices.isEmpty()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniMark()
                Text(
                    "ESSENTIAL REMAP",
                    modifier = Modifier.padding(start = 12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.weight(1f))
                Text("${page + 1}/3", fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(42.dp))
            when (page) {
                0 -> ReleaseKeyStep(
                    language,
                    state,
                    pairingCode,
                    { pairingCode = it.filter(Char::isDigit).take(6) },
                    beginPackageSetup,
                    submitPairingCode,
                    cancelPackageSetup,
                    openDeveloperOptions,
                    copyText,
                    copyDiagnostics,
                    clearDiagnostics,
                )
                1 -> AccessibilityStep(language, state, openAccessibilitySettings)
                else -> ReadyStep(language)
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (page > 0) {
                    OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) {
                        Text(language.t("BACK", "НАЗАД"))
                    }
                }
                Button(
                    onClick = { if (page == 2) finish() else page++ },
                    enabled = when (page) {
                        0 -> keyReleased && screenOffReady
                        1 -> serviceReady
                        else -> true
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text(if (page == 2) language.t("DONE", "ГОТОВО") else language.t("NEXT", "ДАЛЕЕ"))
                }
            }
        }
    }
}

@Composable
private fun ReleaseKeyStep(
    language: AppLanguage,
    state: MapperUiState,
    pairingCode: String,
    changePairingCode: (String) -> Unit,
    beginPackageSetup: (PackageOperation) -> Unit,
    submitPairingCode: (String) -> Unit,
    cancelPackageSetup: () -> Unit,
    openDeveloperOptions: () -> Unit,
    copyText: (String) -> Unit,
    copyDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit,
) {
    StepHeading(
        "01",
        language.t("Release the key", "Освободите кнопку"),
        language.t(
            "Essential Space must stop intercepting the button. Its data is not deleted and the change is reversible.",
            "Essential Space должен перестать перехватывать кнопку. Данные не удаляются, изменение обратимо.",
        ),
    )
    Spacer(Modifier.height(24.dp))
    StatusCard(
        success = state.setup.packageStatus == NothingPackageStatus.DISABLED,
        title = packageStatusTitle(language, state.setup.packageStatus),
        detail = language.t(
            "Nothing packages: Essential Space + Essential Recorder",
            "Пакеты Nothing: Essential Space + Essential Recorder",
        ),
    )
    Spacer(Modifier.height(10.dp))
    StatusCard(
        success = state.setup.screenOffAccessGranted,
        title = if (state.setup.screenOffAccessGranted) {
            language.t("Sleep monitor is running", "Монитор сна работает")
        } else {
            language.t("Sleep monitor needs setup", "Нужно запустить монитор сна")
        },
        detail = language.t(
            "Shell UID, scan code 250 only, no idle wake lock",
            "UID shell, только scan code 250, без удержания процессора",
        ),
    )
    Spacer(Modifier.height(14.dp))
    if (state.setup.busy || state.setup.phase == SetupPhase.ERROR || state.setup.phase == SetupPhase.COMPLETE) {
        SetupProgress(
            language,
            state,
            pairingCode,
            changePairingCode,
            submitPairingCode,
            cancelPackageSetup,
            copyDiagnostics,
            clearDiagnostics,
        )
    }
    if ((state.setup.packageStatus != NothingPackageStatus.DISABLED ||
            !state.setup.screenOffAccessGranted) && !state.setup.busy
    ) {
        Button(
            onClick = { beginPackageSetup(PackageOperation.DISABLE) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(
                if (state.setup.packageStatus == NothingPackageStatus.DISABLED) {
                    language.t("START SLEEP MONITOR", "ЗАПУСТИТЬ МОНИТОР СНА")
                } else {
                    language.t("SET UP WITH WIRELESS ADB", "НАСТРОИТЬ ЧЕРЕЗ WIRELESS ADB")
                },
            )
        }
        TextButton(onClick = openDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
            Text(language.t("Open developer options", "Открыть настройки разработчика"))
        }
    }
    ManualCommands(language, copyText)
}

@Composable
private fun AccessibilityStep(
    language: AppLanguage,
    state: MapperUiState,
    openAccessibilitySettings: () -> Unit,
) {
    StepHeading(
        "02",
        language.t("Allow key listener", "Разрешите перехват кнопки"),
        language.t(
            "Android exposes global hardware keys through Accessibility Service. Essential Remap consumes only scan code 250 and can reproduce a navigation-handle hold only for Circle to Search. It cannot read screen content and does not send data.",
            "Android даёт глобальный доступ к аппаратным клавишам через службу специальных возможностей. Essential Remap обрабатывает только scan code 250 и может воспроизвести удержание нижней полоски только для Circle to Search. Приложение не читает экран и не отправляет данные.",
        ),
    )
    Spacer(Modifier.height(24.dp))
    StatusCard(
        success = state.serviceEnabled,
        title = if (state.serviceEnabled) language.t("Listener is enabled", "Служба включена")
        else language.t("Listener is disabled", "Служба выключена"),
        detail = language.t("Hardware key events only", "Только события аппаратной кнопки"),
    )
    if (state.competingServices.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        WarningCard(
            language.t(
                "Another key-filtering service is active: ${state.competingServices.joinToString()}. Disable its Essential Key mapping to prevent conflicts.",
                "Активна другая служба перехвата клавиш: ${state.competingServices.joinToString()}. Отключите в ней переназначение Essential Key.",
            ),
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = openAccessibilitySettings,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) { Text(language.t("OPEN ACCESSIBILITY SETTINGS", "ОТКРЫТЬ СПЕЦИАЛЬНЫЕ ВОЗМОЖНОСТИ")) }
}

@Composable
private fun ReadyStep(language: AppLanguage) {
    StepHeading(
        "03",
        language.t("Ready", "Готово"),
        language.t(
            "The button now works with the display on or off. Change any mapping on the next screen.",
            "Теперь кнопка работает с включённым и погашенным экраном. Любое действие можно поменять на следующем экране.",
        ),
    )
    Spacer(Modifier.height(24.dp))
    StatusCard(true, language.t("Single press", "Одно нажатие"), language.t("Voice assistant", "Голосовой помощник"))
    Spacer(Modifier.height(10.dp))
    StatusCard(true, language.t("Double press", "Двойное нажатие"), "Circle to Search")
    Spacer(Modifier.height(10.dp))
    StatusCard(true, language.t("Long press", "Удержание"), language.t("Flashlight", "Фонарик"))
}

@Composable
private fun StepHeading(number: String, title: String, detail: String) {
    Text(number, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp), lineHeight = 21.sp)
}

@Composable
private fun SetupProgress(
    language: AppLanguage,
    state: MapperUiState,
    pairingCode: String,
    changePairingCode: (String) -> Unit,
    submitPairingCode: (String) -> Unit,
    cancelPackageSetup: () -> Unit,
    copyDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            if (state.setup.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(
                setupPhaseTitle(language, state.setup.phase),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = if (state.setup.busy) 12.dp else 0.dp),
            )
            state.setup.message?.takeIf { state.setup.phase == SetupPhase.ERROR }?.let {
                Text(
                    it.takeLast(800),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (state.setup.phase == SetupPhase.WAITING_FOR_CODE) {
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = changePairingCode,
                    label = { Text(language.t("6-digit pairing code", "6-значный код сопряжения")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Button(
                    onClick = { submitPairingCode(pairingCode) },
                    enabled = pairingCode.length == 6,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) { Text(language.t("SUBMIT CODE", "ОТПРАВИТЬ КОД")) }
            }
            if (state.setup.busy) {
                TextButton(onClick = cancelPackageSetup) { Text(language.t("Cancel", "Отмена")) }
            }
            if (state.setup.phase == SetupPhase.ERROR) {
                Text(
                    language.t(
                        "Copy the diagnostic log after reproducing the error.",
                        "После появления ошибки скопируйте журнал диагностики.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                DiagnosticsActions(language, copyDiagnostics, clearDiagnostics)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun DiagnosticsActions(
    language: AppLanguage,
    copyDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit,
) {
    var cleared by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                cleared = false
                copyDiagnostics()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                language.t("COPY DIAGNOSTIC LOG", "СКОПИРОВАТЬ ЖУРНАЛ"),
                textAlign = TextAlign.Center,
            )
        }
        OutlinedButton(
            onClick = {
                clearDiagnostics()
                cleared = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                language.t("CLEAR DIAGNOSTIC LOG", "ОЧИСТИТЬ ЖУРНАЛ"),
                textAlign = TextAlign.Center,
            )
        }
    }
    if (cleared) {
        Text(
            language.t("Diagnostic log cleared", "Журнал диагностики очищен"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ManualCommands(language: AppLanguage, copyText: (String) -> Unit) {
    val commands = remember { ShellKeyMonitorCommands.manualAdbCommands() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(language.t("Manual ADB commands", "Команды ADB вручную"))
    }
    if (expanded) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(14.dp)) {
                Text(commands, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { copyText(commands) }) { Text(language.t("COPY", "КОПИРОВАТЬ")) }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    language: AppLanguage,
    state: MapperUiState,
    snackbar: SnackbarHostState,
    openAccessibilitySettings: () -> Unit,
    openNotificationPolicySettings: () -> Unit,
    openDeveloperOptions: () -> Unit,
    openAssistantSettings: () -> Unit,
    openAppInfo: () -> Unit,
    openDonate: () -> Unit,
    beginPackageSetup: (PackageOperation) -> Unit,
    submitPairingCode: (String) -> Unit,
    cancelPackageSetup: () -> Unit,
    copyText: (String) -> Unit,
    copyDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit,
    updateActionKind: (PressAction, ActionKind) -> Unit,
    updateHttpBaseUrl: (PressAction, String) -> Unit,
    updateHttpMethod: (PressAction, RequestMethod) -> Unit,
    updateActionValue: (PressAction, String) -> Unit,
    updateSoundMode: (PressAction, SoundMode) -> Unit,
    updateSystemAction: (PressAction, SystemAction) -> Unit,
    updateLaunchApp: (PressAction, LaunchableApp) -> Unit,
    updateRunWhileLocked: (PressAction, Boolean) -> Unit,
    setRemappingEnabled: (Boolean) -> Unit,
    updateHaptic: (HapticStrength) -> Unit,
    previewHaptic: (HapticStrength) -> Unit,
    save: () -> Unit,
    changeLanguage: (AppLanguage) -> Unit,
    runSetupAgain: () -> Unit,
) {
    var actionGesture by remember { mutableStateOf<PressAction?>(null) }
    var appGesture by remember { mutableStateOf<PressAction?>(null) }
    var urlGesture by remember { mutableStateOf<PressAction?>(null) }
    var httpGesture by remember { mutableStateOf<PressAction?>(null) }
    var soundGesture by remember { mutableStateOf<PressAction?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val setupReady = state.keyReleased && state.serviceEnabled && state.competingServices.isEmpty()
    val ready = setupReady && state.settings.remappingEnabled

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.dirty) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = save,
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentPadding = PaddingValues(vertical = 15.dp),
                    ) {
                        if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(language.t("SAVE CHANGES", "СОХРАНИТЬ ИЗМЕНЕНИЯ"))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniMark()
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("ESSENTIAL REMAP", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(
                            when {
                                ready -> language.t("ACTIVE", "АКТИВНО")
                                setupReady -> language.t("PAUSED", "ПРИОСТАНОВЛЕНО")
                                else -> language.t("SETUP REQUIRED", "НУЖНА НАСТРОЙКА")
                            },
                            color = when {
                                ready -> Color(0xFF2E7D32)
                                setupReady -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.error
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Default.Settings, contentDescription = language.t("Settings", "Настройки"))
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(language.t("BUTTON ACTIONS", "ДЕЙСТВИЯ КНОПКИ"), style = MaterialTheme.typography.labelLarge)
            }
            if (!setupReady) {
                item {
                    WarningCard(language.t(
                        "The listener or Nothing package setup is inactive. Open settings to repair it.",
                        "Служба перехвата или настройка пакетов Nothing неактивна. Откройте настройки.",
                    ), action = { settingsOpen = true })
                }
            }
            items(PressAction.entries) { gesture ->
                GestureCard(
                    language = language,
                    gesture = gesture,
                    action = state.draftActions.getValue(gesture),
                    runWhileLocked = state.draftRunWhileLocked[gesture] == true,
                    onClick = { actionGesture = gesture },
                    onRunWhileLockedChanged = { updateRunWhileLocked(gesture, it) },
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                HapticCard(language, state.draftHapticStrength, updateHaptic, previewHaptic)
            }
        }
    }

    actionGesture?.let { gesture ->
        ActionChooserDialog(
            language = language,
            dismiss = { actionGesture = null },
            chooseApp = { actionGesture = null; appGesture = gesture },
            chooseUrl = { actionGesture = null; urlGesture = gesture },
            chooseHttp = { actionGesture = null; httpGesture = gesture },
            chooseSound = { actionGesture = null; soundGesture = gesture },
            chooseKind = { kind -> updateActionKind(gesture, kind); actionGesture = null },
            chooseSystem = { action -> updateSystemAction(gesture, action); actionGesture = null },
        )
    }
    appGesture?.let { gesture ->
        AppPickerDialog(language, state.launchableApps, { appGesture = null }) {
            updateLaunchApp(gesture, it)
            appGesture = null
        }
    }
    urlGesture?.let { gesture ->
        UrlEditorDialog(
            language,
            state.draftActions[gesture] as? ConfiguredAction.OpenUrl,
            { urlGesture = null },
        ) { value ->
            updateActionKind(gesture, ActionKind.OPEN_URL)
            updateActionValue(gesture, value)
            urlGesture = null
        }
    }
    httpGesture?.let { gesture ->
        HttpEditorDialog(
            language,
            state.draftActions[gesture] as? ConfiguredAction.Http,
            { httpGesture = null },
        ) { base, method, endpoint ->
            updateActionKind(gesture, ActionKind.HTTP)
            updateHttpBaseUrl(gesture, base)
            updateHttpMethod(gesture, method)
            updateActionValue(gesture, endpoint)
            httpGesture = null
        }
    }
    soundGesture?.let { gesture ->
        SoundPickerDialog(language, { soundGesture = null }) {
            updateSoundMode(gesture, it)
            soundGesture = null
        }
    }
    if (settingsOpen) {
        SettingsDialog(
            language,
            state,
            dismiss = { settingsOpen = false },
            openAccessibilitySettings,
            openNotificationPolicySettings,
            openAssistantSettings,
            openAppInfo,
            openDonate,
            beginPackageSetup,
            submitPairingCode,
            cancelPackageSetup,
            copyText,
            copyDiagnostics,
            clearDiagnostics,
            changeLanguage,
            runSetupAgain,
            setRemappingEnabled,
        )
    }
}

@Composable
private fun GestureCard(
    language: AppLanguage,
    gesture: PressAction,
    action: ConfiguredAction,
    runWhileLocked: Boolean,
    onClick: () -> Unit,
    onRunWhileLockedChanged: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onClick).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = Color.Black, shape = CircleShape, modifier = Modifier.size(50.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(gesture.glyph(), color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(gesture.title(language), fontWeight = FontWeight.SemiBold)
                    Text(action.summary(language), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRunWhileLockedChanged(!runWhileLocked) }
                    .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        language.t("Run while locked", "Работать на блокировке"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        language.t("Includes screen off", "Включая погашенный экран"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Checkbox(checked = runWhileLocked, onCheckedChange = null)
            }        }
    }
}

@Composable
private fun HapticCard(
    language: AppLanguage,
    value: HapticStrength,
    update: (HapticStrength) -> Unit,
    preview: (HapticStrength) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Text(language.t("HAPTIC FEEDBACK", "ВИБРООТКЛИК"), modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HapticStrength.entries.forEach { strength ->
                    val selected = strength == value
                    if (selected) Button(onClick = { preview(strength) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(strength.title(language), maxLines = 1)
                    } else OutlinedButton(onClick = { update(strength) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(strength.title(language), maxLines = 1)
                    }
                }
            }
        }
    }
}

private data class ActionOption(
    val title: String,
    val subtitle: String? = null,
    val run: () -> Unit,
)

@Composable
private fun ActionChooserDialog(
    language: AppLanguage,
    dismiss: () -> Unit,
    chooseApp: () -> Unit,
    chooseUrl: () -> Unit,
    chooseHttp: () -> Unit,
    chooseSound: () -> Unit,
    chooseKind: (ActionKind) -> Unit,
    chooseSystem: (SystemAction) -> Unit,
) {
    val options = listOf(
        ActionOption(language.t("Launch an app", "Запустить приложение"), run = chooseApp),
        ActionOption("Circle to Search", language.t("Google + Hold handle to search", "Google + удержание полоски для поиска")) { chooseSystem(SystemAction.CIRCLE_TO_SEARCH) },
        ActionOption(language.t("Voice assistant", "Голосовой помощник")) { chooseSystem(SystemAction.ASSISTANT) },
        ActionOption(language.t("Flashlight", "Фонарик")) { chooseKind(ActionKind.FLASHLIGHT) },
        ActionOption(language.t("Take screenshot", "Снимок экрана")) { chooseSystem(SystemAction.SCREENSHOT) },
        ActionOption(language.t("Open camera", "Открыть камеру")) { chooseSystem(SystemAction.CAMERA) },
        ActionOption(language.t("Notifications", "Уведомления")) { chooseSystem(SystemAction.NOTIFICATIONS) },
        ActionOption(language.t("Quick Settings", "Быстрые настройки")) { chooseSystem(SystemAction.QUICK_SETTINGS) },
        ActionOption(language.t("Lock screen", "Заблокировать экран")) { chooseSystem(SystemAction.LOCK_SCREEN) },
        ActionOption(language.t("Power menu", "Меню питания")) { chooseSystem(SystemAction.POWER_MENU) },
        ActionOption(language.t("Sound mode", "Режим звука"), run = chooseSound),
        ActionOption(language.t("Play / pause media", "Пауза / воспроизведение")) { chooseSystem(SystemAction.MEDIA_PLAY_PAUSE) },
        ActionOption(language.t("Next track", "Следующий трек")) { chooseSystem(SystemAction.MEDIA_NEXT) },
        ActionOption(language.t("Previous track", "Предыдущий трек")) { chooseSystem(SystemAction.MEDIA_PREVIOUS) },
        ActionOption(language.t("Back", "Назад")) { chooseSystem(SystemAction.BACK) },
        ActionOption(language.t("Home", "Домой")) { chooseSystem(SystemAction.HOME) },
        ActionOption(language.t("Recent apps", "Недавние приложения")) { chooseSystem(SystemAction.RECENTS) },
        ActionOption(language.t("Open link / deep link", "Открыть ссылку / deep link"), run = chooseUrl),
        ActionOption(language.t("HTTP request", "HTTP-запрос"), language.t("For Tasker, Home Assistant and webhooks", "Для Tasker, Home Assistant и вебхуков"), chooseHttp),
        ActionOption(language.t("No action", "Ничего")) { chooseKind(ActionKind.NONE) },
    )
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
            Column {
                DialogHeader(language.t("Choose action", "Выберите действие"), dismiss)
                LazyColumn(contentPadding = PaddingValues(bottom = 14.dp)) {
                    items(options) { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { option.run() }.padding(horizontal = 20.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(option.title, fontWeight = FontWeight.Medium)
                                option.subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerDialog(
    language: AppLanguage,
    apps: List<LaunchableApp>,
    dismiss: () -> Unit,
    select: (LaunchableApp) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
        }
    }
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
            Column {
                DialogHeader(language.t("Choose app", "Выберите приложение"), dismiss)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(language.t("Search", "Поиск")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { select(app) }.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.packageName)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(app.label)
                                Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val packageManager = LocalContext.current.packageManager
    val bitmap = remember(packageName) {
        runCatching { packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
    else Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Apps, contentDescription = null) }
    }
}

@Composable
private fun UrlEditorDialog(
    language: AppLanguage,
    current: ConfiguredAction.OpenUrl?,
    dismiss: () -> Unit,
    apply: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(current?.url.orEmpty()) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(language.t("Open link", "Открыть ссылку")) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("URL / intent://") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { apply(value.trim()) }, enabled = value.isNotBlank()) { Text(language.t("APPLY", "ПРИМЕНИТЬ")) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(language.t("Cancel", "Отмена")) } },
    )
}

@Composable
private fun HttpEditorDialog(
    language: AppLanguage,
    current: ConfiguredAction.Http?,
    dismiss: () -> Unit,
    apply: (String, RequestMethod, String) -> Unit,
) {
    var base by rememberSaveable { mutableStateOf(current?.baseUrl ?: ConfiguredAction.Http.DEFAULT_BASE_URL) }
    var endpoint by rememberSaveable { mutableStateOf(current?.endpoint.orEmpty()) }
    var method by rememberSaveable { mutableStateOf(current?.method ?: RequestMethod.GET) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(language.t("HTTP request", "HTTP-запрос")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(base, { base = it }, label = { Text(language.t("Base URL", "Базовый URL")) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RequestMethod.entries.forEach { item ->
                        if (item == method) Button(onClick = { method = item }, modifier = Modifier.weight(1f)) { Text(item.name) }
                        else OutlinedButton(onClick = { method = item }, modifier = Modifier.weight(1f)) { Text(item.name) }
                    }
                }
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text(language.t("Path or full URL", "Путь или полный URL")) }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { apply(base.trim(), method, endpoint.trim()) }, enabled = base.isNotBlank() && endpoint.isNotBlank()) { Text(language.t("APPLY", "ПРИМЕНИТЬ")) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(language.t("Cancel", "Отмена")) } },
    )
}

@Composable
private fun SoundPickerDialog(language: AppLanguage, dismiss: () -> Unit, select: (SoundMode) -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(language.t("Sound mode", "Режим звука")) },
        text = {
            Column {
                SoundMode.entries.forEach { mode ->
                    Text(
                        mode.title(language),
                        modifier = Modifier.fillMaxWidth().clickable { select(mode) }.padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(language.t("Cancel", "Отмена")) } },
    )
}

@Composable
private fun SettingsDialog(
    language: AppLanguage,
    state: MapperUiState,
    dismiss: () -> Unit,
    openAccessibilitySettings: () -> Unit,
    openNotificationPolicySettings: () -> Unit,
    openAssistantSettings: () -> Unit,
    openAppInfo: () -> Unit,
    openDonate: () -> Unit,
    beginPackageSetup: (PackageOperation) -> Unit,
    submitPairingCode: (String) -> Unit,
    cancelPackageSetup: () -> Unit,
    copyText: (String) -> Unit,
    copyDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit,
    changeLanguage: (AppLanguage) -> Unit,
    runSetupAgain: () -> Unit,
    setRemappingEnabled: (Boolean) -> Unit,
) {
    var pairingCode by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column {
                DialogHeader(language.t("Settings", "Настройки"), dismiss)
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionLabel(language.t("KEY STATUS", "СОСТОЯНИЕ КНОПКИ"))
                    StatusCard(
                        state.setup.packageStatus == NothingPackageStatus.DISABLED,
                        packageStatusTitle(language, state.setup.packageStatus),
                        language.t("Essential Space package state", "Состояние пакетов Essential Space"),
                    )
                    StatusCard(
                        state.setup.screenOffAccessGranted,
                        if (state.setup.screenOffAccessGranted) {
                            language.t("Sleep monitor is running", "Монитор сна работает")
                        } else {
                            language.t("Sleep monitor needs restart", "Нужно запустить монитор сна")
                        },
                        language.t(
                            "No idle wake lock; restart it here after a phone reboot",
                            "Без удержания процессора; после перезагрузки запустите здесь снова",
                        ),
                    )
                    if (state.setup.busy || state.setup.phase == SetupPhase.ERROR) {
                        SetupProgress(
                            language,
                            state,
                            pairingCode,
                            { pairingCode = it.filter(Char::isDigit).take(6) },
                            submitPairingCode,
                            cancelPackageSetup,
                            copyDiagnostics,
                            clearDiagnostics,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.setup.packageStatus == NothingPackageStatus.DISABLED) {
                            OutlinedButton(onClick = { beginPackageSetup(PackageOperation.RESTORE) }, modifier = Modifier.weight(1f)) {
                                Text(language.t("RESTORE SPACE", "ВЕРНУТЬ SPACE"), textAlign = TextAlign.Center)
                            }
                            Button(onClick = { beginPackageSetup(PackageOperation.DISABLE) }, modifier = Modifier.weight(1f)) {
                                Text(
                                    if (state.setup.screenOffAccessGranted) {
                                        language.t("RESTART MONITOR", "ПЕРЕЗАПУСТИТЬ")
                                    } else {
                                        language.t("START MONITOR", "ЗАПУСТИТЬ")
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            Button(onClick = { beginPackageSetup(PackageOperation.DISABLE) }, modifier = Modifier.fillMaxWidth()) {
                                Text(language.t("RELEASE KEY", "ОСВОБОДИТЬ"))
                            }
                        }
                    }
                    ManualCommands(language, copyText)
                    HorizontalDivider()
                    SectionLabel(language.t("BUTTON", "КНОПКА"))
                    SettingsSwitchRow(
                        title = language.t("Handle Essential Key", "Обрабатывать Essential Key"),
                        subtitle = if (state.settings.remappingEnabled) {
                            language.t("Configured actions are active", "Назначенные действия выполняются")
                        } else {
                            language.t("Button actions are paused", "Действия кнопки приостановлены")
                        },
                        checked = state.settings.remappingEnabled,
                        onCheckedChange = setRemappingEnabled,
                    )
                    HorizontalDivider()
                    SectionLabel(language.t("PERMISSIONS", "РАЗРЕШЕНИЯ"))
                    SettingsRow(
                        language.t("Accessibility listener", "Служба специальных возможностей"),
                        if (state.serviceEnabled) language.t("Enabled", "Включена") else language.t("Disabled", "Выключена"),
                        openAccessibilitySettings,
                    )
                    SettingsRow(language.t("Default assistant", "Помощник по умолчанию"), "Google / Gemini", openAssistantSettings)
                    SettingsRow(language.t("Do Not Disturb access", "Доступ к режиму «Не беспокоить»"), language.t("Needed only for Silent mode", "Нужен только для беззвучного режима"), openNotificationPolicySettings)
                    HorizontalDivider()
                    SectionLabel(language.t("LANGUAGE", "ЯЗЫК"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageChoice("RU", language == AppLanguage.RUSSIAN, Modifier.weight(1f)) { changeLanguage(AppLanguage.RUSSIAN) }
                        LanguageChoice("EN", language == AppLanguage.ENGLISH, Modifier.weight(1f)) { changeLanguage(AppLanguage.ENGLISH) }
                    }
                    HorizontalDivider()
                    SectionLabel(language.t("APP", "ПРИЛОЖЕНИЕ"))
                    SettingsRow(language.t("Run setup again", "Повторить первоначальную настройку"), null) { dismiss(); runSetupAgain() }
                    SettingsRow(language.t("Android app info", "Информация о приложении"), null, openAppInfo)
                    HorizontalDivider()
                    SectionLabel(language.t("DIAGNOSTICS", "ДИАГНОСТИКА"))
                    Text(
                        language.t(
                            "For a screen-off failure: clear the log, let the phone fully sleep, press Essential Key once, unlock with Power, then copy the log. Pairing codes, private keys and action URLs are never recorded.",
                            "Если кнопка не работает во сне: очистите журнал, дождитесь полного засыпания, один раз нажмите Essential Key, разблокируйте телефон кнопкой питания и скопируйте журнал. Коды сопряжения, закрытые ключи и адреса действий не записываются.",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    DiagnosticsActions(language, copyDiagnostics, clearDiagnostics)
                    WarningCard(language.t(
                        "Restore Essential Space before uninstalling. Removing this app alone does not re-enable Nothing's packages.",
                        "Перед удалением верните Essential Space. Удаление приложения само по себе не включит пакеты Nothing.",
                    ))
                    SettingsRow("Donate", "github.com/AbdulKus/donate", openDonate)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, modifier: Modifier, click: () -> Unit) {
    if (selected) Button(onClick = click, modifier = modifier) { Text(label) }
    else OutlinedButton(onClick = click, modifier = modifier) { Text(label) }
}

@Composable
private fun DialogHeader(title: String, dismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = dismiss) { Icon(Icons.Default.Close, contentDescription = null) }
    }
    HorizontalDivider()
}

@Composable
private fun StatusCard(success: Boolean, title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = if (success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, shape = CircleShape, modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (success) Icons.Default.Check else Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WarningCard(text: String, action: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = if (action != null) Modifier.fillMaxWidth().clickable(onClick = action) else Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Text(text, modifier = Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.bodySmall)
            if (action != null) Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, click: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun MiniMark() {
    Surface(color = Color.Black, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(14.dp).background(Color(0xFFD71920), CircleShape))
            Box(Modifier.size(5.dp).background(Color.White, CircleShape))
        }
    }
}

private fun AppLanguage.t(en: String, ru: String): String = if (this == AppLanguage.RUSSIAN) ru else en

private fun PressAction.glyph(): String = when (this) {
    PressAction.SINGLE -> "1×"
    PressAction.DOUBLE -> "2×"
    PressAction.LONG -> "H"
}

private fun PressAction.title(language: AppLanguage): String = when (this) {
    PressAction.SINGLE -> language.t("Single press", "Одно нажатие")
    PressAction.DOUBLE -> language.t("Double press", "Двойное нажатие")
    PressAction.LONG -> language.t("Press and hold", "Нажать и удерживать")
}

private fun ConfiguredAction.summary(language: AppLanguage): String = when (this) {
    ConfiguredAction.None -> language.t("No action", "Ничего")
    ConfiguredAction.Flashlight -> language.t("Flashlight", "Фонарик")
    ConfiguredAction.ToggleSilent -> language.t("Toggle silent / normal", "Без звука / обычный")
    is ConfiguredAction.Http -> "${method.name} ${endpoint.ifBlank { language.t("request", "запрос") }}"
    is ConfiguredAction.SetSoundMode -> mode.title(language)
    is ConfiguredAction.LaunchApp -> label.ifBlank { language.t("Choose an app", "Выберите приложение") }
    is ConfiguredAction.OpenUrl -> url.ifBlank { language.t("Open link", "Открыть ссылку") }
    is ConfiguredAction.PerformSystemAction -> action.title(language)
}

private fun SystemAction.title(language: AppLanguage): String = when (this) {
    SystemAction.CIRCLE_TO_SEARCH -> "Circle to Search"
    SystemAction.SCREENSHOT -> language.t("Take screenshot", "Снимок экрана")
    SystemAction.LOCK_SCREEN -> language.t("Lock screen", "Заблокировать экран")
    SystemAction.POWER_MENU -> language.t("Power menu", "Меню питания")
    SystemAction.NOTIFICATIONS -> language.t("Notifications", "Уведомления")
    SystemAction.QUICK_SETTINGS -> language.t("Quick Settings", "Быстрые настройки")
    SystemAction.HOME -> language.t("Home", "Домой")
    SystemAction.BACK -> language.t("Back", "Назад")
    SystemAction.RECENTS -> language.t("Recent apps", "Недавние приложения")
    SystemAction.MEDIA_PLAY_PAUSE -> language.t("Play / pause", "Пауза / воспроизведение")
    SystemAction.MEDIA_NEXT -> language.t("Next track", "Следующий трек")
    SystemAction.MEDIA_PREVIOUS -> language.t("Previous track", "Предыдущий трек")
    SystemAction.CAMERA -> language.t("Camera", "Камера")
    SystemAction.ASSISTANT -> language.t("Voice assistant", "Голосовой помощник")
}

private fun SoundMode.title(language: AppLanguage): String = when (this) {
    SoundMode.NORMAL -> language.t("Normal", "Обычный")
    SoundMode.VIBRATE -> language.t("Vibrate", "Вибрация")
    SoundMode.SILENT -> language.t("Silent", "Без звука")
    SoundMode.TOGGLE_SILENT_NORMAL -> language.t("Toggle silent / normal", "Без звука / обычный")
}

private fun HapticStrength.title(language: AppLanguage): String = when (this) {
    HapticStrength.OFF -> language.t("Off", "Выкл")
    HapticStrength.LIGHT -> language.t("Light", "Слабый")
    HapticStrength.MEDIUM -> language.t("Medium", "Средний")
    HapticStrength.STRONG -> language.t("Strong", "Сильный")
}

private fun packageStatusTitle(language: AppLanguage, status: NothingPackageStatus): String = when (status) {
    NothingPackageStatus.DISABLED -> language.t("Essential Key is released", "Essential Key освобождена")
    NothingPackageStatus.ENABLED -> language.t("Essential Space owns the key", "Кнопку перехватывает Essential Space")
    NothingPackageStatus.PARTIAL -> language.t("Only partially released", "Кнопка освобождена частично")
    NothingPackageStatus.UNSUPPORTED -> language.t("Nothing packages not found", "Пакеты Nothing не найдены")
    NothingPackageStatus.UNKNOWN -> language.t("Checking package status", "Проверка пакетов")
}

private fun setupPhaseTitle(language: AppLanguage, phase: SetupPhase): String = when (phase) {
    SetupPhase.IDLE -> language.t("Ready", "Готово")
    SetupPhase.DISCOVERING -> language.t("Finding Wireless ADB", "Поиск Wireless ADB")
    SetupPhase.WAITING_FOR_CODE -> language.t("Enter the pairing code", "Введите код сопряжения")
    SetupPhase.PAIRING -> language.t("Pairing with Android", "Сопряжение с Android")
    SetupPhase.CONNECTING -> language.t("Connecting", "Подключение")
    SetupPhase.APPLYING -> language.t("Applying package state", "Применение настроек пакетов")
    SetupPhase.COMPLETE -> language.t("Setup complete", "Настройка завершена")
    SetupPhase.ERROR -> language.t("Setup failed", "Ошибка настройки")
}
