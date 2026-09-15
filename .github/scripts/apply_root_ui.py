from pathlib import Path
import re

PATH = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt"
p = Path(PATH)
text = p.read_text()


def replace_once(old, new):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match, got {count}: {old[:120]!r}")
    text = text.replace(old, new, 1)


def sub_once(pattern, replacement):
    global text
    text2, count = re.subn(pattern, lambda _: replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one regex match, got {count}: {pattern[:120]!r}")
    text = text2


replace_once(
    "import com.abdulkus.essentialremap.setup.SetupPhase\n",
    "import com.abdulkus.essentialremap.setup.SetupPhase\nimport com.abdulkus.essentialremap.setup.SetupAccessMode\n",
)

# Persisted onboarding mode state lives next to screen-off mode.
replace_once(
    '''    var screenOffEnabled by rememberSaveable { mutableStateOf(preferences.screenOffEnabled) }
    val setScreenOffEnabled: (Boolean) -> Unit = { enabled ->
        preferences.screenOffEnabled = enabled
        screenOffEnabled = enabled
    }
    val language = AppLanguage.fromCode(languageCode)''',
    '''    var screenOffEnabled by rememberSaveable { mutableStateOf(preferences.screenOffEnabled) }
    val setScreenOffEnabled: (Boolean) -> Unit = { enabled ->
        preferences.screenOffEnabled = enabled
        screenOffEnabled = enabled
    }
    var setupAccessMode by rememberSaveable { mutableStateOf(preferences.setupAccessMode) }
    val setSetupAccessMode: (SetupAccessMode) -> Unit = { mode ->
        preferences.setupAccessMode = mode
        setupAccessMode = mode
    }
    val language = AppLanguage.fromCode(languageCode)''',
)
replace_once(
    '''            screenOffEnabled = screenOffEnabled,
            setScreenOffEnabled = setScreenOffEnabled,
            openSetupVideo = openSetupVideo,''',
    '''            screenOffEnabled = screenOffEnabled,
            setScreenOffEnabled = setScreenOffEnabled,
            setupAccessMode = setupAccessMode,
            setSetupAccessMode = setSetupAccessMode,
            openSetupVideo = openSetupVideo,''',
)
replace_once(
    '''        screenOffEnabled = screenOffEnabled,
        setScreenOffEnabled = setScreenOffEnabled,
        initiallyOpenSettings = initiallyOpenSettings,''',
    '''        screenOffEnabled = screenOffEnabled,
        setScreenOffEnabled = setScreenOffEnabled,
        setupAccessMode = setupAccessMode,
        initiallyOpenSettings = initiallyOpenSettings,''',
)

# Rebuild onboarding page routing so privilege choice is immediately after update check.
onboarding = '''@Composable
private fun OnboardingScreen(
    language: AppLanguage,
    state: MapperUiState,
    screenOffEnabled: Boolean,
    setScreenOffEnabled: (Boolean) -> Unit,
    setupAccessMode: SetupAccessMode,
    setSetupAccessMode: (SetupAccessMode) -> Unit,
    openSetupVideo: () -> Unit,
    updateState: UpdatePromptState,
    checkForUpdates: () -> Unit,
    downloadUpdate: (GitHubRelease) -> Unit,
    installUpdate: (DownloadedUpdate) -> Unit,
    dismissUpdate: () -> Unit,
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
    val needsUsbStep = screenOffEnabled && setupAccessMode == SetupAccessMode.NON_ROOT
    val basePage = if (needsUsbStep) 4 else 3
    val accessibilityPage = basePage + 1
    val sleepPage = accessibilityPage + 1
    val readyPage = if (screenOffEnabled) sleepPage + 1 else accessibilityPage + 1
    val pageCount = readyPage + 1
    if (page >= pageCount) page = pageCount - 1

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
                Text("${page + 1}/$pageCount", fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(36.dp))
            when {
                page == 0 -> UpdateCheckStep(
                    language,
                    updateState,
                    checkForUpdates,
                    downloadUpdate,
                    installUpdate,
                    dismissUpdate,
                )
                page == 1 -> AccessModeChoiceStep(language, setupAccessMode, setSetupAccessMode)
                page == 2 -> ModeChoiceStep(
                    language,
                    setupAccessMode,
                    screenOffEnabled,
                    setScreenOffEnabled,
                    openSetupVideo,
                )
                needsUsbStep && page == 3 -> UsbDebuggingStep(language, state)
                page == basePage -> BaseSetupStep(
                    language,
                    state,
                    setupAccessMode,
                    pairingCode,
                    { pairingCode = it.filter(Char::isDigit).take(6) },
                    beginPackageSetup,
                    submitPairingCode,
                    cancelPackageSetup,
                    openDeveloperOptions,
                    copyText,
                    copyDiagnostics,
                    clearDiagnostics,
                    page.toString().padStart(2, '0'),
                )
                page == accessibilityPage -> AccessibilityStep(
                    language,
                    state,
                    openAccessibilitySettings,
                    accessibilityPage.toString().padStart(2, '0'),
                )
                screenOffEnabled && page == sleepPage -> SleepSetupStep(
                    language,
                    state,
                    setupAccessMode,
                    pairingCode,
                    { pairingCode = it.filter(Char::isDigit).take(6) },
                    beginPackageSetup,
                    submitPairingCode,
                    cancelPackageSetup,
                    copyText,
                    copyDiagnostics,
                    clearDiagnostics,
                    page.toString().padStart(2, '0'),
                )
                else -> ReadyStep(
                    language,
                    screenOffEnabled,
                    setupAccessMode,
                    page.toString().padStart(2, '0'),
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (page > 0) {
                    OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) {
                        Text(language.t("BACK", "НАЗАД"))
                    }
                }
                Button(
                    onClick = { if (page == pageCount - 1) finish() else page++ },
                    enabled = when {
                        page == 0 -> true
                        page == 1 -> true
                        page == 2 -> true
                        needsUsbStep && page == 3 -> state.usbDebuggingEnabled
                        page == basePage -> keyReleased
                        page == accessibilityPage -> serviceReady
                        screenOffEnabled && page == sleepPage -> screenOffReady
                        else -> true
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text(if (page == pageCount - 1) language.t("DONE", "ГОТОВО") else language.t("NEXT", "ДАЛЕЕ"))
                }
            }
        }
    }
}

'''
sub_once(
    r'''@Composable\nprivate fun OnboardingScreen\(.*?\n@Composable\nprivate fun UpdateCheckStep\(''',
    onboarding + "@Composable\nprivate fun UpdateCheckStep(",
)

# Access mode choice and screen mode choice.
choices = '''@Composable
private fun AccessModeChoiceStep(
    language: AppLanguage,
    setupAccessMode: SetupAccessMode,
    setSetupAccessMode: (SetupAccessMode) -> Unit,
) {
    StepHeading(
        "01",
        language.t("Choose access mode", "Выберите режим доступа"),
        language.t(
            "Both modes use the same Essential Key monitor. Root removes ADB requirements and can restore the monitor automatically after reboot.",
            "Оба режима используют один и тот же монитор Essential Key. Root убирает требования ADB и позволяет автоматически запускать монитор после перезагрузки.",
        ),
    )
    Spacer(Modifier.height(22.dp))
    ModeOption(
        language = language,
        selected = setupAccessMode == SetupAccessMode.NON_ROOT,
        title = language.t("NON-ROOT", "БЕЗ ROOT"),
        detail = language.t(
            "No root required. Setup uses local Wireless ADB. Screen-off mode needs USB debugging left enabled and a monitor restart after reboot.",
            "Root не нужен. Настройка идёт через локальный Wireless ADB. Для работы с выключенным экраном USB-отладку нужно оставить включённой, а после перезагрузки перезапустить монитор.",
        ),
        onClick = { setSetupAccessMode(SetupAccessMode.NON_ROOT) },
    )
    Spacer(Modifier.height(10.dp))
    ModeOption(
        language = language,
        selected = setupAccessMode == SetupAccessMode.ROOT,
        title = "ROOT",
        detail = language.t(
            "Uses su from Magisk, KernelSU, APatch or another root manager. ADB is not required and the sleep monitor auto-starts after reboot.",
            "Использует su из Magisk, KernelSU, APatch или другого менеджера root. ADB не нужен, монитор сна запускается после перезагрузки автоматически.",
        ),
        onClick = { setSetupAccessMode(SetupAccessMode.ROOT) },
    )
    if (setupAccessMode == SetupAccessMode.ROOT) {
        Spacer(Modifier.height(14.dp))
        Text(
            language.t(
                "When Android asks for root, grant Essential Remap persistent access so automatic startup after reboot can work.",
                "Когда Android запросит root, выдайте Essential Remap постоянное разрешение, чтобы автозапуск после перезагрузки работал.",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ModeChoiceStep(
    language: AppLanguage,
    setupAccessMode: SetupAccessMode,
    screenOffEnabled: Boolean,
    setScreenOffEnabled: (Boolean) -> Unit,
    openSetupVideo: () -> Unit,
) {
    val rootMode = setupAccessMode == SetupAccessMode.ROOT
    StepHeading(
        "02",
        language.t("Choose button mode", "Выберите режим работы"),
        language.t(
            "You can change screen-off handling later in Settings.",
            "Работу с выключенным экраном можно изменить позже в настройках.",
        ),
    )
    Spacer(Modifier.height(22.dp))
    ModeOption(
        language = language,
        selected = !screenOffEnabled,
        title = language.t("Screen on", "Только включённый экран"),
        detail = language.t(
            "Simpler setup. No background input monitor is installed.",
            "Более простая настройка. Фоновый монитор нажатий не устанавливается.",
        ),
        onClick = { setScreenOffEnabled(false) },
    )
    Spacer(Modifier.height(10.dp))
    ModeOption(
        language = language,
        selected = screenOffEnabled,
        title = language.t("Screen on + off", "Включённый + выключенный экран"),
        detail = if (rootMode) {
            language.t(
                "Adds the root sleep monitor. It starts automatically after reboot and does not require ADB to stay enabled.",
                "Добавляет root-монитор сна. Он автоматически запускается после перезагрузки и не требует включённого ADB.",
            )
        } else {
            language.t(
                "Adds the shell sleep monitor. Keep USB debugging enabled and restart the monitor after every phone reboot.",
                "Добавляет shell-монитор сна. Оставьте USB-отладку включённой и перезапускайте монитор после каждой перезагрузки телефона.",
            )
        },
        onClick = { setScreenOffEnabled(true) },
    )
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = openSetupVideo, modifier = Modifier.fillMaxWidth()) {
        Text(language.t("VIDEO SETUP GUIDE", "ВИДЕО ИНСТРУКЦИЯ ПО НАСТРОЙКЕ"), textAlign = TextAlign.Center)
    }
}

'''
sub_once(
    r'''@Composable\nprivate fun ModeChoiceStep\(.*?\n@Composable\nprivate fun ModeOption\(''',
    choices + "@Composable\nprivate fun ModeOption(",
)
replace_once(
    '''private fun UsbDebuggingStep(language: AppLanguage, state: MapperUiState) {
    val context = LocalContext.current
    StepHeading(
        "02",''',
    '''private fun UsbDebuggingStep(language: AppLanguage, state: MapperUiState) {
    val context = LocalContext.current
    StepHeading(
        "03",''',
)

# Base setup uses either su or local Wireless ADB.
base = '''@Composable
private fun BaseSetupStep(
    language: AppLanguage,
    state: MapperUiState,
    setupAccessMode: SetupAccessMode,
    pairingCode: String,
    changePairingCode: (String) -> Unit,
    beginPackageSetup: (PackageOperation) -> Unit,
    submitPairingCode: (String) -> Unit,
    cancelPackageSetup: () -> Unit,
    openDeveloperOptions: () -> Unit,
    copyText: (String) -> Unit,
    copyDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit,
    stepNumber: String,
) {
    val rootMode = setupAccessMode == SetupAccessMode.ROOT
    StepHeading(
        stepNumber,
        language.t("Release Essential Key", "Освободите Essential Key"),
        if (rootMode) {
            language.t(
                "Essential Remap uses root to disable the two Nothing components that own the button. Their data is kept and the change can be restored later.",
                "Essential Remap через root отключит два компонента Nothing, которые забирают кнопку. Их данные сохранятся, а изменение можно будет откатить.",
            )
        } else {
            language.t(
                "Essential Remap disables the two Nothing components through local Wireless ADB. Their data is kept and the change can be restored later.",
                "Essential Remap через локальный Wireless ADB отключит два компонента Nothing, которые забирают кнопку. Их данные сохранятся, а изменение можно будет откатить.",
            )
        },
    )
    Spacer(Modifier.height(22.dp))
    StatusCard(
        success = state.setup.packageStatus == NothingPackageStatus.DISABLED,
        title = packageStatusTitle(language, state.setup.packageStatus),
        detail = language.t("Essential Space and Essential Recorder", "Essential Space и Essential Recorder"),
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
    if (state.setup.packageStatus != NothingPackageStatus.DISABLED && !state.setup.busy) {
        if (rootMode) {
            RootSetupGuide(language)
        } else {
            AdbSetupGuide(
                language = language,
                developerOptionsEnabled = state.developerOptionsEnabled,
                openDeveloperOptions = openDeveloperOptions,
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { beginPackageSetup(PackageOperation.DISABLE) },
            enabled = rootMode || state.developerOptionsEnabled,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) { Text(language.t("RELEASE KEY", "ОСВОБОДИТЬ КНОПКУ")) }
    }
    if (!rootMode) ManualCommands(language, copyText, includeSleepMonitor = false)
}

@Composable
private fun RootSetupGuide(language: AppLanguage) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("ROOT / SU", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(
                language.t(
                    "Tap the button below and approve Essential Remap in your root manager. Developer options, Wireless debugging and a computer are not required.",
                    "Нажмите кнопку ниже и разрешите Essential Remap в менеджере root. Режим разработчика, Wireless debugging и компьютер не нужны.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

'''
sub_once(
    r'''@Composable\nprivate fun BaseSetupStep\(.*?\n@Composable\nprivate fun AdbSetupGuide\(''',
    base + "@Composable\nprivate fun AdbSetupGuide(",
)

# Sleep-monitor and ready pages explain lifecycle differences.
sleep_ready = '''@Composable
private fun SleepSetupStep(
    language: AppLanguage,
    state: MapperUiState,
    setupAccessMode: SetupAccessMode,
    pairingCode: String,
    changePairingCode: (String) -> Unit,
    beginPackageSetup: (PackageOperation) -> Unit,
    submitPairingCode: (String) -> Unit,
    cancelPackageSetup: () -> Unit,
    copyText: (String) -> Unit,
    copyDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit,
    stepNumber: String,
) {
    val rootMode = setupAccessMode == SetupAccessMode.ROOT
    StepHeading(
        stepNumber,
        language.t("Enable screen-off handling", "Включите работу с выключенным экраном"),
        if (rootMode) {
            language.t(
                "The monitor runs with root privileges, listens only for Essential Key and does not hold a wake lock. Essential Remap starts it automatically after reboot.",
                "Монитор работает с root-правами, слушает только Essential Key и не удерживает процессор активным. Essential Remap автоматически запускает его после перезагрузки.",
            )
        } else {
            language.t(
                "The monitor runs with Android shell privileges and listens only for Essential Key. It does not hold a wake lock. After reboot it must be restarted.",
                "Монитор работает с правами Android shell и слушает только Essential Key. Он не удерживает процессор активным. После перезагрузки его нужно перезапустить.",
            )
        },
    )
    Spacer(Modifier.height(22.dp))
    StatusCard(
        success = state.setup.screenOffAccessGranted,
        title = if (state.setup.screenOffAccessGranted) {
            language.t("Sleep monitor is running", "Монитор сна работает")
        } else {
            language.t("Sleep monitor is not running", "Монитор сна не запущен")
        },
        detail = if (rootMode) {
            language.t(
                "ADB may stay completely off. Keep a persistent root grant so automatic startup after reboot can work.",
                "ADB можно полностью выключить. Оставьте постоянное root-разрешение для автозапуска после перезагрузки.",
            )
        } else {
            language.t(
                "Keep USB debugging enabled so Android does not stop the monitor when Wi-Fi disconnects. Restart it after reboot.",
                "Оставьте USB-отладку включённой, чтобы Android не останавливал монитор при отключении Wi-Fi. После перезагрузки монитор нужно перезапустить.",
            )
        },
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
    if (!state.setup.screenOffAccessGranted && !state.setup.busy) {
        Button(
            onClick = { beginPackageSetup(PackageOperation.INSTALL_SLEEP_MONITOR) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) { Text(language.t("INSTALL SLEEP MONITOR", "УСТАНОВИТЬ МОНИТОР СНА"), textAlign = TextAlign.Center) }
    }
    BatteryAccessCard(language)
    if (!rootMode) ManualCommands(language, copyText, includeSleepMonitor = true)
}

@Composable
private fun ReadyStep(
    language: AppLanguage,
    screenOffEnabled: Boolean,
    setupAccessMode: SetupAccessMode,
    stepNumber: String,
) {
    val rootMode = setupAccessMode == SetupAccessMode.ROOT
    StepHeading(
        stepNumber,
        language.t("Ready", "Готово"),
        if (screenOffEnabled && rootMode) {
            language.t(
                "Essential Key is ready with the display on and off. Root mode restores the sleep monitor automatically after reboot; ADB can stay off.",
                "Essential Key готова к работе с включённым и выключенным экраном. Root-режим автоматически восстанавливает монитор после перезагрузки; ADB можно оставить выключенным.",
            )
        } else if (screenOffEnabled) {
            language.t(
                "Essential Key is ready with the display on and off. After reboot, restart the sleep monitor from Settings.",
                "Essential Key готова к работе с включённым и выключенным экраном. После перезагрузки перезапустите монитор сна в настройках.",
            )
        } else {
            language.t(
                "Essential Key is ready while the display is on. You can enable screen-off handling later in Settings.",
                "Essential Key готова к работе при включённом экране. Работу с выключенным экраном можно включить позже в настройках.",
            )
        },
    )
    Spacer(Modifier.height(22.dp))
    StatusCard(true, language.t("Single press", "Одно нажатие"), language.t("Voice assistant", "Голосовой помощник"))
    Spacer(Modifier.height(10.dp))
    StatusCard(true, language.t("Double press", "Двойное нажатие"), "Circle to Search")
    Spacer(Modifier.height(10.dp))
    StatusCard(true, language.t("Long press", "Удержание"), language.t("Flashlight", "Фонарик"))
}

'''
sub_once(
    r'''@Composable\nprivate fun SleepSetupStep\(.*?\n@Composable\nprivate fun StepHeading\(''',
    sleep_ready + "@Composable\nprivate fun StepHeading(",
)

# Home and Settings know which monitor lifecycle is active.
replace_once(
    '''    screenOffEnabled: Boolean,
    setScreenOffEnabled: (Boolean) -> Unit,
    initiallyOpenSettings: Boolean,''',
    '''    screenOffEnabled: Boolean,
    setScreenOffEnabled: (Boolean) -> Unit,
    setupAccessMode: SetupAccessMode,
    initiallyOpenSettings: Boolean,''',
)
replace_once(
    '''            screenOffEnabled,
            setScreenOffEnabled,
            changeLanguage,''',
    '''            screenOffEnabled,
            setScreenOffEnabled,
            setupAccessMode,
            changeLanguage,''',
)
replace_once(
    '''    screenOffEnabled: Boolean,
    setScreenOffEnabled: (Boolean) -> Unit,
    changeLanguage: (AppLanguage) -> Unit,''',
    '''    screenOffEnabled: Boolean,
    setScreenOffEnabled: (Boolean) -> Unit,
    setupAccessMode: SetupAccessMode,
    changeLanguage: (AppLanguage) -> Unit,''',
)
replace_once(
    '''                            language.t(
                                "Keep USB debugging enabled when turning Wi-Fi off. No cable is needed. Restart the monitor after a phone reboot.",
                                "При отключении Wi-Fi оставьте «Отладку по USB» включённой. Кабель не нужен. После перезагрузки телефона перезапустите монитор.",
                            ),''',
    '''                            if (setupAccessMode == SetupAccessMode.ROOT) {
                                language.t(
                                    "Root mode: ADB may stay off and the monitor auto-starts after reboot.",
                                    "Root-режим: ADB можно оставить выключенным, монитор запускается автоматически после перезагрузки.",
                                )
                            } else {
                                language.t(
                                    "Keep USB debugging enabled when turning Wi-Fi off. No cable is needed. Restart the monitor after a phone reboot.",
                                    "При отключении Wi-Fi оставьте «Отладку по USB» включённой. Кабель не нужен. После перезагрузки телефона перезапустите монитор.",
                                )
                            },''',
)
replace_once(
    '''                    ManualCommands(language, copyText, includeSleepMonitor = screenOffEnabled)
                    HorizontalDivider()
                    SectionLabel(language.t("BUTTON", "КНОПКА"))''',
    '''                    if (setupAccessMode == SetupAccessMode.NON_ROOT) {
                        ManualCommands(language, copyText, includeSleepMonitor = screenOffEnabled)
                    }
                    HorizontalDivider()
                    SectionLabel(language.t("SETUP ACCESS", "РЕЖИМ ДОСТУПА"))
                    SettingsRow(
                        language.t("Privilege mode", "Режим прав"),
                        if (setupAccessMode == SetupAccessMode.ROOT) {
                            language.t("ROOT · no ADB required", "ROOT · ADB не требуется")
                        } else {
                            language.t("NON-ROOT · local Wireless ADB", "БЕЗ ROOT · локальный Wireless ADB")
                        },
                    ) { dismiss(); runSetupAgain() }
                    HorizontalDivider()
                    SectionLabel(language.t("BUTTON", "КНОПКА"))''',
)
replace_once(
    '''                        subtitle = if (screenOffEnabled) {
                            language.t(
                                "Uses the sleep monitor; restart it after a phone reboot",
                                "Использует монитор сна; после перезагрузки телефона нужен перезапуск",
                            )
                        } else {''',
    '''                        subtitle = if (screenOffEnabled && setupAccessMode == SetupAccessMode.ROOT) {
                            language.t(
                                "Uses the root sleep monitor; automatic restart after reboot",
                                "Использует root-монитор сна; автозапуск после перезагрузки",
                            )
                        } else if (screenOffEnabled) {
                            language.t(
                                "Uses the shell sleep monitor; restart it after a phone reboot",
                                "Использует shell-монитор сна; после перезагрузки нужен перезапуск",
                            )
                        } else {''',
)
replace_once(
    '''private fun setupPhaseTitle(language: AppLanguage, phase: SetupPhase): String = when (phase) {
    SetupPhase.IDLE -> language.t("Ready", "Готово")
    SetupPhase.DISCOVERING ->''',
    '''private fun setupPhaseTitle(language: AppLanguage, phase: SetupPhase): String = when (phase) {
    SetupPhase.IDLE -> language.t("Ready", "Готово")
    SetupPhase.REQUESTING_ROOT -> language.t("Requesting root access", "Запрос root-доступа")
    SetupPhase.DISCOVERING ->''',
)

p.write_text(text)
Path(".github/scripts/apply_root_ui.py").unlink()
Path(".github/workflows/apply-root-ui.yml").unlink()
