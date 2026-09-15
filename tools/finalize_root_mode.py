from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, got {count} for {old[:80]!r}")
    path.write_text(text.replace(old, new, 1))


root = Path(".")
access = root / "app/src/main/java/com/abdulkus/essentialremap/ScreenOffKeyAccess.kt"
boot = root / "app/src/main/java/com/abdulkus/essentialremap/setup/SleepMonitorBootReceiver.kt"
ui = root / "app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt"

replace_once(
    access,
    '''    fun isGranted(context: Context): Boolean {\n        return runtimeHealthy && isConfiguredForThisBoot(context)\n    }\n''',
    '''    fun isGranted(context: Context): Boolean {\n        return runtimeHealthy && isConfiguredForThisBoot(context)\n    }\n\n    fun isGrantedFor(context: Context, accessMode: SetupAccessMode): Boolean =\n        isGranted(context) && configuredAccessMode(context) == accessMode\n''',
)

replace_once(
    boot,
    '''        val preferences = UserPreferences(context)\n        if (!preferences.screenOffEnabled) return\n\n        ScreenOffKeyAccess.markStopped(context)\n        if (preferences.setupAccessMode != SetupAccessMode.ROOT) {\n''',
    '''        val preferences = UserPreferences(context)\n        if (!preferences.screenOffEnabled) return\n\n        val configuredAccessMode =\n            ScreenOffKeyAccess.configuredAccessMode(context) ?: preferences.setupAccessMode\n        ScreenOffKeyAccess.markStopped(context)\n        if (configuredAccessMode != SetupAccessMode.ROOT) {\n''',
)

replace_once(
    ui,
    '''import androidx.lifecycle.compose.collectAsStateWithLifecycle\nimport com.abdulkus.essentialremap.domain.ActionKind\n''',
    '''import androidx.lifecycle.compose.collectAsStateWithLifecycle\nimport com.abdulkus.essentialremap.ScreenOffKeyAccess\nimport com.abdulkus.essentialremap.domain.ActionKind\n''',
)

replace_once(
    ui,
    '''    val keyReleased = state.setup.packageStatus == NothingPackageStatus.DISABLED\n    val screenOffReady = state.setup.screenOffAccessGranted\n    val serviceReady = state.serviceEnabled && state.competingServices.isEmpty()\n''',
    '''    val keyReleased = state.setup.packageStatus == NothingPackageStatus.DISABLED\n    val screenOffReady = screenOffReadyForMode(state, setupAccessMode)\n    val serviceReady = state.serviceEnabled && state.competingServices.isEmpty()\n''',
)

replace_once(
    ui,
    '''    val rootMode = setupAccessMode == SetupAccessMode.ROOT\n    StepHeading(\n''',
    '''    val rootMode = setupAccessMode == SetupAccessMode.ROOT\n    val monitorReady = screenOffReadyForMode(state, setupAccessMode)\n    StepHeading(\n''',
)

replace_once(
    ui,
    '''    StatusCard(\n        success = state.setup.screenOffAccessGranted,\n        title = if (state.setup.screenOffAccessGranted) {\n            language.t("Sleep monitor is running", "Монитор сна работает")\n        } else {\n            language.t("Sleep monitor is not running", "Монитор сна не запущен")\n        },\n''',
    '''    StatusCard(\n        success = monitorReady,\n        title = if (monitorReady) {\n            language.t("Sleep monitor is running", "Монитор сна работает")\n        } else {\n            language.t("Sleep monitor is not running", "Монитор сна не запущен")\n        },\n''',
)

replace_once(
    ui,
    '''    if (!state.setup.screenOffAccessGranted && !state.setup.busy) {\n        Button(\n''',
    '''    if (!monitorReady && !state.setup.busy) {\n        Button(\n''',
)

replace_once(
    ui,
    '''private fun StepHeading(number: String, title: String, detail: String) {\n    Text(number, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)\n    Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))\n    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp), lineHeight = 21.sp)\n}\n''',
    '''private fun StepHeading(number: String, title: String, detail: String) {\n    Text(number, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)\n    Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))\n    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp), lineHeight = 21.sp)\n}\n\n@Composable\nprivate fun screenOffReadyForMode(state: MapperUiState, setupAccessMode: SetupAccessMode): Boolean {\n    val context = LocalContext.current\n    return state.setup.screenOffAccessGranted &&\n        ScreenOffKeyAccess.configuredAccessMode(context) == setupAccessMode\n}\n''',
)

replace_once(
    ui,
    '''    var soundGesture by remember { mutableStateOf<PressAction?>(null) }\n    var settingsOpen by rememberSaveable { mutableStateOf(initiallyOpenSettings) }\n    val baseSetupReady = state.keyReleased && state.serviceEnabled && state.competingServices.isEmpty()\n    val screenOffReady = !screenOffEnabled || state.setup.screenOffAccessGranted\n''',
    '''    var soundGesture by remember { mutableStateOf<PressAction?>(null) }\n    var settingsOpen by rememberSaveable { mutableStateOf(initiallyOpenSettings) }\n    val monitorReady = screenOffReadyForMode(state, setupAccessMode)\n    val baseSetupReady = state.keyReleased && state.serviceEnabled && state.competingServices.isEmpty()\n    val screenOffReady = !screenOffEnabled || monitorReady\n''',
)

replace_once(
    ui,
    '''                screenOffEnabled && !state.setup.screenOffAccessGranted -> item {\n''',
    '''                screenOffEnabled && !monitorReady -> item {\n''',
)

replace_once(
    ui,
    '''    var pairingCode by rememberSaveable { mutableStateOf("") }\n    Dialog(\n''',
    '''    var pairingCode by rememberSaveable { mutableStateOf("") }\n    val monitorReady = screenOffReadyForMode(state, setupAccessMode)\n    Dialog(\n''',
)

replace_once(
    ui,
    '''                        StatusCard(\n                            state.setup.screenOffAccessGranted,\n                            if (state.setup.screenOffAccessGranted) {\n                                language.t("Sleep monitor is running", "Монитор сна работает")\n                            } else {\n                                language.t("Sleep monitor needs restart", "Монитор сна нужно перезапустить")\n                            },\n''',
    '''                        StatusCard(\n                            monitorReady,\n                            if (monitorReady) {\n                                language.t("Sleep monitor is running", "Монитор сна работает")\n                            } else {\n                                language.t("Sleep monitor needs restart", "Монитор сна нужно перезапустить")\n                            },\n''',
)

replace_once(
    ui,
    '''                                            if (state.setup.screenOffAccessGranted) {\n                                                language.t("RESTART", "ПЕРЕЗАПУСК")\n                                            } else {\n''',
    '''                                            if (monitorReady) {\n                                                language.t("RESTART", "ПЕРЕЗАПУСК")\n                                            } else {\n''',
)

# Remove the temporary patch machinery in the same commit that carries the real fixes.
(root / "tools/finalize_root_mode.py").unlink()
(root / ".github/workflows/finalize-root-mode.yml").unlink()
