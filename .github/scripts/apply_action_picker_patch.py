from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1))


# MapperScreen: wire Activity and Quick Settings tile pickers into the existing gesture action flow.
mapper = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt"
replace_once(
    mapper,
    "import com.abdulkus.essentialremap.platform.LaunchableApp\n",
    "import com.abdulkus.essentialremap.platform.LaunchableActivity\n"
    "import com.abdulkus.essentialremap.platform.LaunchableApp\n"
    "import com.abdulkus.essentialremap.platform.QuickSettingsTileInfo\n",
)
replace_once(
    mapper,
    "        updateLaunchApp = viewModel::updateLaunchApp,\n"
    "        updateRunWhileLocked = viewModel::updateRunWhileLocked,\n",
    "        updateLaunchApp = viewModel::updateLaunchApp,\n"
    "        updateLaunchActivity = viewModel::updateLaunchActivity,\n"
    "        updateQuickSettingsTile = viewModel::updateQuickSettingsTile,\n"
    "        updateRunWhileLocked = viewModel::updateRunWhileLocked,\n",
)
replace_once(
    mapper,
    "    updateLaunchApp: (PressAction, LaunchableApp) -> Unit,\n"
    "    updateRunWhileLocked: (PressAction, Boolean) -> Unit,\n",
    "    updateLaunchApp: (PressAction, LaunchableApp) -> Unit,\n"
    "    updateLaunchActivity: (PressAction, LaunchableActivity) -> Unit,\n"
    "    updateQuickSettingsTile: (PressAction, QuickSettingsTileInfo) -> Unit,\n"
    "    updateRunWhileLocked: (PressAction, Boolean) -> Unit,\n",
)
replace_once(
    mapper,
    "    var actionGesture by remember { mutableStateOf<PressAction?>(null) }\n"
    "    var appGesture by remember { mutableStateOf<PressAction?>(null) }\n"
    "    var urlGesture by remember { mutableStateOf<PressAction?>(null) }\n",
    "    var actionGesture by remember { mutableStateOf<PressAction?>(null) }\n"
    "    var appGesture by remember { mutableStateOf<PressAction?>(null) }\n"
    "    var activityGesture by remember { mutableStateOf<PressAction?>(null) }\n"
    "    var quickSettingsTileGesture by remember { mutableStateOf<PressAction?>(null) }\n"
    "    var urlGesture by remember { mutableStateOf<PressAction?>(null) }\n",
)
replace_once(
    mapper,
    "            chooseApp = { actionGesture = null; appGesture = gesture },\n"
    "            chooseUrl = { actionGesture = null; urlGesture = gesture },\n",
    "            chooseApp = { actionGesture = null; appGesture = gesture },\n"
    "            chooseActivity = { actionGesture = null; activityGesture = gesture },\n"
    "            chooseQuickSettingsTile = { actionGesture = null; quickSettingsTileGesture = gesture },\n"
    "            chooseUrl = { actionGesture = null; urlGesture = gesture },\n",
)
replace_once(
    mapper,
    "    appGesture?.let { gesture ->\n"
    "        AppPickerDialog(language, state.launchableApps, { appGesture = null }) {\n"
    "            updateLaunchApp(gesture, it)\n"
    "            appGesture = null\n"
    "        }\n"
    "    }\n"
    "    urlGesture?.let { gesture ->\n",
    "    appGesture?.let { gesture ->\n"
    "        AppPickerDialog(language, state.launchableApps, { appGesture = null }) {\n"
    "            updateLaunchApp(gesture, it)\n"
    "            appGesture = null\n"
    "        }\n"
    "    }\n"
    "    activityGesture?.let { gesture ->\n"
    "        AppActivityPickerDialog(language, state.launchableApps, { activityGesture = null }) {\n"
    "            updateLaunchActivity(gesture, it)\n"
    "            activityGesture = null\n"
    "        }\n"
    "    }\n"
    "    quickSettingsTileGesture?.let { gesture ->\n"
    "        QuickSettingsTilePickerDialog(language, { quickSettingsTileGesture = null }) {\n"
    "            updateQuickSettingsTile(gesture, it)\n"
    "            quickSettingsTileGesture = null\n"
    "        }\n"
    "    }\n"
    "    urlGesture?.let { gesture ->\n",
)
replace_once(
    mapper,
    "    chooseApp: () -> Unit,\n"
    "    chooseUrl: () -> Unit,\n",
    "    chooseApp: () -> Unit,\n"
    "    chooseActivity: () -> Unit,\n"
    "    chooseQuickSettingsTile: () -> Unit,\n"
    "    chooseUrl: () -> Unit,\n",
)
replace_once(
    mapper,
    "        ActionOption(language.t(\"Launch an app\", \"Запустить приложение\"), run = chooseApp),\n"
    "        ActionOption(\"Circle to Search\",",
    "        ActionOption(language.t(\"Launch an app\", \"Запустить приложение\"), run = chooseApp),\n"
    "        ActionOption(language.t(\"Launch Activity\", \"Запуск Activity\"), run = chooseActivity),\n"
    "        ActionOption(language.t(\"Quick Settings tile\", \"Плитка Quick Settings\"), run = chooseQuickSettingsTile),\n"
    "        ActionOption(\"Circle to Search\",",
)
replace_once(
    mapper,
    "    is ConfiguredAction.LaunchApp -> label.ifBlank { language.t(\"Choose an app\", \"Выберите приложение\") }\n"
    "    is ConfiguredAction.OpenUrl ->",
    "    is ConfiguredAction.LaunchApp -> label.ifBlank {\n"
    "        if (componentName.isBlank()) language.t(\"Choose an app\", \"Выберите приложение\")\n"
    "        else language.t(\"Launch Activity\", \"Запуск Activity\")\n"
    "    }\n"
    "    is ConfiguredAction.QuickSettingsTile -> label.ifBlank { language.t(\"Quick Settings tile\", \"Плитка Quick Settings\") }\n"
    "    is ConfiguredAction.OpenUrl ->",
)
replace_once(
    mapper,
    "            \"Accessibility lets the app receive the Essential Key while Android is in use. Essential Remap does not read screen content.\",\n"
    "            \"Специальные возможности позволяют приложению получать нажатия Essential Key во время работы Android. Essential Remap не читает содержимое экрана.\",\n",
    "            \"Accessibility receives the Essential Key while Android is in use. Screen content is inspected only when a configured Quick Settings tile must be found and clicked.\",\n"
    "            \"Специальные возможности получают нажатия Essential Key во время работы Android. Содержимое экрана анализируется только при поиске и нажатии настроенной плитки Quick Settings.\",\n",
)

# MainActivity: remove the separate floating Activity-action overlay.
main_activity = "app/src/main/java/com/abdulkus/essentialremap/MainActivity.kt"
replace_once(
    main_activity,
    "import com.abdulkus.essentialremap.ui.AppActivityActionPicker\n",
    "",
)
replace_once(
    main_activity,
    "                    if (userPreferences.onboardingComplete) {\n"
    "                        AppActivityActionPicker(\n"
    "                            viewModel = viewModel,\n"
    "                            language = userPreferences.language ?: AppLanguage.ENGLISH,\n"
    "                        )\n"
    "                        InAppPromptHost(\n",
    "                    if (userPreferences.onboardingComplete) {\n"
    "                        InAppPromptHost(\n",
)

# ViewModel: support tile selection and validation.
view_model = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperViewModel.kt"
replace_once(
    view_model,
    "import com.abdulkus.essentialremap.platform.LaunchableAppsReader\n",
    "import com.abdulkus.essentialremap.platform.LaunchableAppsReader\n"
    "import com.abdulkus.essentialremap.platform.QuickSettingsTileInfo\n",
)
replace_once(
    view_model,
    "            ActionKind.LAUNCH_APP -> current as? ConfiguredAction.LaunchApp\n"
    "                ?: _uiState.value.launchableApps.firstOrNull()?.let {\n"
    "                    ConfiguredAction.LaunchApp(it.packageName, it.label)\n"
    "                }\n"
    "                ?: ConfiguredAction.LaunchApp()\n"
    "            ActionKind.OPEN_URL ->",
    "            ActionKind.LAUNCH_APP -> current as? ConfiguredAction.LaunchApp\n"
    "                ?: _uiState.value.launchableApps.firstOrNull()?.let {\n"
    "                    ConfiguredAction.LaunchApp(it.packageName, it.label)\n"
    "                }\n"
    "                ?: ConfiguredAction.LaunchApp()\n"
    "            ActionKind.QUICK_SETTINGS_TILE -> current as? ConfiguredAction.QuickSettingsTile\n"
    "                ?: ConfiguredAction.QuickSettingsTile()\n"
    "            ActionKind.OPEN_URL ->",
)
replace_once(
    view_model,
    "    fun updateHaptic(strength: HapticStrength) {\n",
    "    fun updateQuickSettingsTile(gesture: PressAction, tile: QuickSettingsTileInfo) {\n"
    "        updateAction(\n"
    "            gesture,\n"
    "            ConfiguredAction.QuickSettingsTile(\n"
    "                componentName = tile.componentName,\n"
    "                label = tile.tileLabel,\n"
    "                appLabel = tile.appLabel,\n"
    "            ),\n"
    "        )\n"
    "    }\n\n"
    "    fun updateHaptic(strength: HapticStrength) {\n",
)
replace_once(
    view_model,
    "        is ConfiguredAction.LaunchApp -> if (action.packageName.isBlank()) \"Choose an app\" else null\n"
    "        is ConfiguredAction.OpenUrl ->",
    "        is ConfiguredAction.LaunchApp -> if (action.packageName.isBlank()) \"Choose an app\" else null\n"
    "        is ConfiguredAction.QuickSettingsTile -> if (action.componentName.isBlank()) \"Choose a Quick Settings tile\" else null\n"
    "        is ConfiguredAction.OpenUrl ->",
)

# ActionExecutor delegates the accessibility-only tile operation back to the service.
executor = "app/src/main/java/com/abdulkus/essentialremap/ActionExecutor.kt"
replace_once(
    executor,
    "        performGlobalAction: (Int) -> Boolean,\n"
    "        performNavigationHandleLongPress: () -> Boolean,\n",
    "        performGlobalAction: (Int) -> Boolean,\n"
    "        performNavigationHandleLongPress: () -> Boolean,\n"
    "        performQuickSettingsTile: suspend (ConfiguredAction.QuickSettingsTile) -> ActionExecutionResult,\n",
)
replace_once(
    executor,
    "        is ConfiguredAction.OpenUrl -> withContext(Dispatchers.Main.immediate) {\n",
    "        is ConfiguredAction.QuickSettingsTile -> performQuickSettingsTile(action)\n"
    "        is ConfiguredAction.OpenUrl -> withContext(Dispatchers.Main.immediate) {\n",
)

# Accessibility runtime: expand Quick Settings, find the selected tile in SystemUI, click it, collapse the shade.
service = "app/src/main/java/com/abdulkus/essentialremap/KeyAccessibilityService.kt"
replace_once(
    service,
    "import android.view.accessibility.AccessibilityEvent\n",
    "import android.view.accessibility.AccessibilityEvent\n"
    "import android.view.accessibility.AccessibilityNodeInfo\n",
)
replace_once(
    service,
    "import kotlinx.coroutines.flow.collectLatest\n"
    "import kotlinx.coroutines.launch\n",
    "import kotlinx.coroutines.delay\n"
    "import kotlinx.coroutines.flow.collectLatest\n"
    "import kotlinx.coroutines.launch\n",
)
replace_once(
    service,
    "                    performGlobalAction = ::performGlobalAction,\n"
    "                    performNavigationHandleLongPress = ::performNavigationHandleLongPress,\n",
    "                    performGlobalAction = ::performGlobalAction,\n"
    "                    performNavigationHandleLongPress = ::performNavigationHandleLongPress,\n"
    "                    performQuickSettingsTile = ::performQuickSettingsTile,\n",
)
insert_before = "    /**\n     * Fallback for ROMs that block direct voice-interaction access."
quick_settings_impl = '''    private suspend fun performQuickSettingsTile(\n        action: ConfiguredAction.QuickSettingsTile,\n    ): ActionExecutionResult = withContext(Dispatchers.Main.immediate) {\n        if (!powerManager.isInteractive) {\n            return@withContext ActionExecutionResult(false, \"Quick Settings tile requires the display to be on\")\n        }\n        if (keyguardManager.isKeyguardLocked) {\n            return@withContext ActionExecutionResult(false, \"Unlock the phone to use a Quick Settings tile\")\n        }\n        if (!performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) {\n            return@withContext ActionExecutionResult(false, \"Android rejected opening Quick Settings\")\n        }\n\n        var clicked = false\n        for (attempt in 0 until QUICK_SETTINGS_FIND_ATTEMPTS) {\n            delay(if (attempt == 0) 140L else QUICK_SETTINGS_FIND_DELAY_MS)\n            val node = findQuickSettingsTile(action.label.ifBlank { action.appLabel })\n            if (node != null && clickNodeOrParent(node)) {\n                clicked = true\n                break\n            }\n        }\n        if (!clicked) {\n            performGlobalAction(GLOBAL_ACTION_BACK)\n            return@withContext ActionExecutionResult(\n                false,\n                \"Quick Settings tile is not visible. Add it to the shade first.\",\n            )\n        }\n\n        delay(220L)\n        if (findQuickSettingsTile(action.label.ifBlank { action.appLabel }) != null) {\n            performGlobalAction(GLOBAL_ACTION_BACK)\n        }\n        ActionExecutionResult(true, \"Quick Settings tile: ${action.label}\")\n    }\n\n    private fun findQuickSettingsTile(label: String): AccessibilityNodeInfo? {\n        val target = normalizeAccessibilityText(label)\n        if (target.isBlank()) return null\n        val root = rootInActiveWindow ?: return null\n        return findNode(root, target)\n    }\n\n    private fun findNode(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {\n        val values = buildList {\n            node.text?.toString()?.let(::add)\n            node.contentDescription?.toString()?.let(::add)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n                node.stateDescription?.toString()?.let(::add)\n            }\n        }\n        if (values.any { normalizeAccessibilityText(it).contains(target) }) return node\n        for (index in 0 until node.childCount) {\n            val child = node.getChild(index) ?: continue\n            findNode(child, target)?.let { return it }\n        }\n        return null\n    }\n\n    private fun clickNodeOrParent(start: AccessibilityNodeInfo): Boolean {\n        var node: AccessibilityNodeInfo? = start\n        repeat(QUICK_SETTINGS_PARENT_DEPTH) {\n            val current = node ?: return false\n            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true\n            node = current.parent\n        }\n        return false\n    }\n\n    private fun normalizeAccessibilityText(value: String): String =\n        value.lowercase().replace(Regex(\"\\\\s+\"), \" \").trim()\n\n'''
replace_once(service, insert_before, quick_settings_impl + insert_before)
replace_once(
    service,
    "        const val NAVIGATION_LONG_PRESS_MS = 700L\n"
    "        const val WAKE_LOCK_TIMEOUT_MS = 5_000L\n",
    "        const val NAVIGATION_LONG_PRESS_MS = 700L\n"
    "        const val QUICK_SETTINGS_FIND_ATTEMPTS = 12\n"
    "        const val QUICK_SETTINGS_FIND_DELAY_MS = 80L\n"
    "        const val QUICK_SETTINGS_PARENT_DEPTH = 7\n"
    "        const val WAKE_LOCK_TIMEOUT_MS = 5_000L\n",
)

# Accessibility declaration and privacy description now match the optional SystemUI-tree use.
config = "app/src/main/res/xml/accessibility_service_config.xml"
replace_once(config, 'android:canRetrieveWindowContent="false"', 'android:canRetrieveWindowContent="true"')
strings = "app/src/main/res/values/strings.xml"
replace_once(
    strings,
    "It cannot retrieve screen content, type text, or collect accessibility data.",
    "It retrieves the SystemUI accessibility tree only while a configured Quick Settings tile is being located; it does not type text or collect accessibility data.",
)

# Mapper persistence regression coverage for the new tile action.
test = "app/src/test/java/com/abdulkus/essentialremap/DataStoreSettingsMapperTest.kt"
replace_once(
    test,
    "    @Test\n    fun legacyToggleSilentActionMigratesIntoSoundMode() {\n",
    "    @Test\n"
    "    fun quickSettingsTileMapsFromPreferences() {\n"
    "        val preferences = mutablePreferencesOf(\n"
    "            stringPreferencesKey(\"SINGLE_action_type\") to \"QUICK_SETTINGS_TILE\",\n"
    "            stringPreferencesKey(\"SINGLE_action_value\") to \"com.example.vpn/.VpnTileService\",\n"
    "            stringPreferencesKey(\"SINGLE_action_label\") to \"VPN\",\n"
    "            stringPreferencesKey(\"SINGLE_action_app_label\") to \"Example VPN\",\n"
    "        )\n\n"
    "        val settings = preferencesToSettings(preferences)\n\n"
    "        assertEquals(\n"
    "            ConfiguredAction.QuickSettingsTile(\n"
    "                componentName = \"com.example.vpn/.VpnTileService\",\n"
    "                label = \"VPN\",\n"
    "                appLabel = \"Example VPN\",\n"
    "            ),\n"
    "            settings.actions.getValue(PressAction.SINGLE),\n"
    "        )\n"
    "    }\n\n"
    "    @Test\n    fun legacyToggleSilentActionMigratesIntoSoundMode() {\n",
)
