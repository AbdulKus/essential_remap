from pathlib import Path


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    Path(path).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Missing patch target: {label}")
    return text.replace(old, new, 1)


def replace_between(text, start_marker, end_marker, new_block, label):
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"Missing start marker: {label}")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"Missing end marker: {label}")
    return text[:start] + new_block + text[end:]


# ---------------------------------------------------------------------------
# MapperScreen: 10-language UI, precise setup warnings, 2x5 language buttons,
# and manual update check entry.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt"
s = read(path)

s = s.replace(
    "    openDonate: () -> Unit,\n",
    "    openDonate: () -> Unit,\n    checkForUpdates: () -> Unit,\n",
)

s = replace_once(
    s,
    "        openDonate = openDonate,\n        beginPackageSetup = beginPackageSetup,",
    "        openDonate = openDonate,\n        checkForUpdates = checkForUpdates,\n        beginPackageSetup = beginPackageSetup,",
    "pass checkForUpdates to HomeScreen",
)

language_block = '''@Composable
private fun LanguageScreen(select: (AppLanguage) -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFD71920))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F2EF))
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EssentialMark()
                Spacer(Modifier.height(24.dp))
                Text(
                    "CHOOSE LANGUAGE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    "Choose your language · Выберите язык",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF62625E),
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                )
                AppLanguage.entries.chunked(2).forEach { rowLanguages ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowLanguages.forEach { item ->
                            LanguageButton(
                                language = item,
                                modifier = Modifier.weight(1f),
                            ) { select(item) }
                        }
                        if (rowLanguages.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun LanguageButton(
    language: AppLanguage,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Color.Black, shape = CircleShape) {
                Text(
                    language.shortLabel,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                )
            }
            Text(
                language.nativeName,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                fontSize = 13.sp,
            )
        }
    }
}

'''
s = replace_between(
    s,
    "@Composable\nprivate fun LanguageScreen",
    "@Composable\nprivate fun EssentialMark",
    language_block,
    "language chooser",
)

s = replace_once(
    s,
    "            openDonate,\n            beginPackageSetup,",
    "            openDonate,\n            checkForUpdates,\n            beginPackageSetup,",
    "pass checkForUpdates to SettingsDialog",
)

old_warning = '''            if (!setupReady) {
                item {
                    WarningCard(language.t(
                        "The listener or Nothing package setup is inactive. Open settings to repair it.",
                        "Служба перехвата или настройка пакетов Nothing неактивна. Откройте настройки.",
                    ), action = { settingsOpen = true })
                }
            }
'''
new_warning = '''            when {
                !state.keyReleased -> item {
                    WarningCard(
                        text = language.t(
                            "Essential Key is still owned by Nothing",
                            "Essential Key всё ещё перехватывает Nothing",
                        ),
                        detail = language.t(
                            "Release the key in Essential Remap settings.",
                            "Освободите кнопку в настройках Essential Remap.",
                        ),
                        actionLabel = language.t("FIX", "ИСПРАВИТЬ"),
                        action = { settingsOpen = true },
                    )
                }
                !state.serviceEnabled -> item {
                    WarningCard(
                        text = language.t(
                            "Accessibility service is disabled",
                            "Служба специальных возможностей выключена",
                        ),
                        detail = language.t(
                            "Enable Essential Remap in Accessibility settings.",
                            "Включите Essential Remap в настройках специальных возможностей.",
                        ),
                        actionLabel = language.t("FIX", "ИСПРАВИТЬ"),
                        action = openAccessibilitySettings,
                    )
                }
                state.competingServices.isNotEmpty() -> item {
                    WarningCard(
                        text = language.t(
                            "Another key remapper is active",
                            "Активен другой переназначатель кнопок",
                        ),
                        detail = language.t(
                            "Disable the competing key service in Accessibility settings.",
                            "Отключите конфликтующую службу в настройках специальных возможностей.",
                        ),
                        actionLabel = language.t("FIX", "ИСПРАВИТЬ"),
                        action = openAccessibilitySettings,
                    )
                }
            }
'''
s = replace_once(s, old_warning, new_warning, "precise home warning")

old_lang_settings = '''                    SectionLabel(language.t("LANGUAGE", "ЯЗЫК"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageChoice("RU", language == AppLanguage.RUSSIAN, Modifier.weight(1f)) { changeLanguage(AppLanguage.RUSSIAN) }
                        LanguageChoice("EN", language == AppLanguage.ENGLISH, Modifier.weight(1f)) { changeLanguage(AppLanguage.ENGLISH) }
                    }
'''
new_lang_settings = '''                    SectionLabel(language.t("LANGUAGE", "ЯЗЫК"))
                    AppLanguage.entries.chunked(5).forEach { rowLanguages ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rowLanguages.forEach { item ->
                                LanguageChoice(
                                    item.shortLabel,
                                    language == item,
                                    Modifier.weight(1f),
                                ) { changeLanguage(item) }
                            }
                        }
                    }
'''
s = replace_once(s, old_lang_settings, new_lang_settings, "2x5 settings language grid")

s = replace_once(
    s,
    '''                    SectionLabel(language.t("APP", "ПРИЛОЖЕНИЕ"))
                    SettingsRow(language.t("Run setup again", "Повторить первоначальную настройку"), null) { dismiss(); runSetupAgain() }
''',
    '''                    SectionLabel(language.t("APP", "ПРИЛОЖЕНИЕ"))
                    SettingsRow(language.t("Check for updates", "Проверить обновления"), null, checkForUpdates)
                    SettingsRow(language.t("Run setup again", "Повторить первоначальную настройку"), null) { dismiss(); runSetupAgain() }
''',
    "manual update row",
)

s = s.replace(
    '                    SettingsRow("Donate", "github.com/AbdulKus/donate", openDonate)',
    '                    SettingsRow(language.t("Donate", "Поддержать"), "abdulkus.github.io/donate", openDonate)',
)

s = replace_between(
    s,
    "@Composable\nprivate fun LanguageChoice",
    "@Composable\nprivate fun DialogHeader",
    '''@Composable
private fun LanguageChoice(label: String, selected: Boolean, modifier: Modifier, click: () -> Unit) {
    val contentPadding = PaddingValues(horizontal = 2.dp, vertical = 9.dp)
    if (selected) {
        Button(onClick = click, modifier = modifier, contentPadding = contentPadding) {
            Text(label, maxLines = 1, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    } else {
        OutlinedButton(onClick = click, modifier = modifier, contentPadding = contentPadding) {
            Text(label, maxLines = 1, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

''',
    "compact language buttons",
)

s = replace_between(
    s,
    "@Composable\nprivate fun WarningCard",
    "@Composable\nprivate fun DangerWarningCard",
    '''@Composable
private fun WarningCard(
    text: String,
    detail: String? = null,
    actionLabel: String? = null,
    action: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                detail?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }
            if (action != null) {
                TextButton(onClick = action) {
                    Text(actionLabel ?: "FIX", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

''',
    "actionable warning card",
)

s = s.replace(
    "private fun AppLanguage.t(en: String, ru: String): String = if (this == AppLanguage.RUSSIAN) ru else en",
    "private fun AppLanguage.t(en: String, ru: String): String = translate(en, ru)",
)
write(path, s)


# ---------------------------------------------------------------------------
# MainActivity: localized toasts, reliable post-Accessibility refresh and a
# visible manual update-check result.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/MainActivity.kt"
s = read(path)
s = replace_once(
    s,
    "import com.abdulkus.essentialremap.ui.UserPreferences\n",
    "import com.abdulkus.essentialremap.ui.UserPreferences\nimport com.abdulkus.essentialremap.ui.translate\n",
    "MainActivity translation import",
)
s = replace_once(
    s,
    "import kotlinx.coroutines.flow.MutableStateFlow\nimport kotlinx.coroutines.launch\n",
    "import kotlinx.coroutines.flow.MutableStateFlow\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\n",
    "MainActivity delay import",
)

old = '''            if (!granted) {
                val russian = userPreferences.language == AppLanguage.RUSSIAN
                Toast.makeText(
                    this,
                    if (russian) {
                        "Без уведомлений код сопряжения придётся вводить после возврата в приложение"
                    } else {
                        "Without notifications, enter a pairing code after returning to the app"
                    },
                    Toast.LENGTH_LONG,
                ).show()
            }
'''
new = '''            if (!granted) {
                val language = userPreferences.language ?: AppLanguage.ENGLISH
                Toast.makeText(
                    this,
                    language.translate(
                        "Without notifications, enter a pairing code after returning to the app",
                        "Без уведомлений код сопряжения придётся вводить после возврата в приложение",
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
'''
s = replace_once(s, old, new, "localized notification permission toast")

s = replace_once(
    s,
    "                        openDonate = ::openDonate,\n                        openSetupVideo = {",
    "                        openDonate = ::openDonate,\n                        checkForUpdates = { startUpdateCheck(showResult = true) },\n                        openSetupVideo = {",
    "pass manual update callback",
)

old_resume = '''    override fun onResume() {
        super.onResume()
        viewModel.refreshSetup()
        viewModel.updateAccessibilityStatus(accessibilityStatusReader.read())
        val notificationManager = getSystemService(NotificationManager::class.java)
        viewModel.updateNotificationPolicyAccess(notificationManager.isNotificationPolicyAccessGranted)
        viewModel.updateDeveloperOptionsStatus(
            Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
        )
    }
'''
new_resume = '''    override fun onResume() {
        super.onResume()
        refreshRuntimeState()
        // Accessibility settings can report the old enabled-service list for a short moment
        // after returning to the app. Re-read it twice so the home status clears immediately.
        activityScope.launch {
            delay(350)
            refreshRuntimeState()
            delay(850)
            refreshRuntimeState()
        }
    }

    private fun refreshRuntimeState() {
        viewModel.refreshSetup()
        viewModel.updateAccessibilityStatus(accessibilityStatusReader.read())
        val notificationManager = getSystemService(NotificationManager::class.java)
        viewModel.updateNotificationPolicyAccess(notificationManager.isNotificationPolicyAccessGranted)
        viewModel.updateDeveloperOptionsStatus(
            Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
        )
    }
'''
s = replace_once(s, old_resume, new_resume, "reliable runtime status refresh")

old_check = '''    private fun startUpdateCheck() {
        updatePromptState.value = UpdatePromptState.Checking
        activityScope.launch {
            val release = runCatching { updateManager.checkForUpdate() }.getOrNull()
            updatePromptState.value = release?.let { UpdatePromptState.Available(it) } ?: UpdatePromptState.None
        }
    }
'''
new_check = '''    private fun startUpdateCheck(showResult: Boolean = false) {
        updatePromptState.value = UpdatePromptState.Checking
        activityScope.launch {
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { release ->
                    updatePromptState.value = release?.let { UpdatePromptState.Available(it) }
                        ?: UpdatePromptState.None
                    if (showResult && release == null) {
                        val language = userPreferences.language ?: AppLanguage.ENGLISH
                        Toast.makeText(
                            this@MainActivity,
                            language.translate("Latest version installed", "Установлена последняя версия"),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                .onFailure {
                    updatePromptState.value = UpdatePromptState.None
                    if (showResult) {
                        val language = userPreferences.language ?: AppLanguage.ENGLISH
                        Toast.makeText(
                            this@MainActivity,
                            language.translate("Could not check for updates", "Не удалось проверить обновления"),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
        }
    }
'''
s = replace_once(s, old_check, new_check, "manual update feedback")

old_installer = '''            val text = if (userPreferences.language == AppLanguage.RUSSIAN) {
                "Не удалось открыть установщик Android"
            } else {
                "Could not open the Android package installer"
            }
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
'''
new_installer = '''            val language = userPreferences.language ?: AppLanguage.ENGLISH
            val text = language.translate(
                "Could not open the Android package installer",
                "Не удалось открыть установщик Android",
            )
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
'''
s = replace_once(s, old_installer, new_installer, "localized installer toast")

s = replace_once(
    s,
    '        val message = if (userPreferences.language == AppLanguage.RUSSIAN) "Скопировано" else "Copied"',
    '        val language = userPreferences.language ?: AppLanguage.ENGLISH\n        val message = language.translate("Copied", "Скопировано")',
    "localized copy toast",
)
write(path, s)


# ---------------------------------------------------------------------------
# In-app update/support prompts: use the shared 10-language translator.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/update/InAppPrompts.kt"
s = read(path)
s = replace_once(
    s,
    "import com.abdulkus.essentialremap.ui.AppLanguage\n",
    "import com.abdulkus.essentialremap.ui.AppLanguage\nimport com.abdulkus.essentialremap.ui.translate\n",
    "prompt translation import",
)
s = s.replace(
    "private fun AppLanguage.t(en: String, ru: String): String = if (this == AppLanguage.RUSSIAN) ru else en",
    "private fun AppLanguage.t(en: String, ru: String): String = translate(en, ru)",
)
write(path, s)


# ---------------------------------------------------------------------------
# GitHub updater: distinguish an actual HTTP/API failure from 'no new version'.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/update/GitHubUpdateManager.kt"
s = read(path)
s = replace_once(
    s,
    "            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null",
    "            if (connection.responseCode != HttpURLConnection.HTTP_OK) {\n                error(\"GitHub API HTTP ${connection.responseCode}\")\n            }",
    "GitHub API error handling",
)
write(path, s)


# ---------------------------------------------------------------------------
# Setup coordinator: all progress/error notification strings follow app language.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/setup/EssentialKeySetupCoordinator.kt"
s = read(path)
s = replace_once(
    s,
    "import com.abdulkus.essentialremap.ScreenOffKeyAccess\n",
    "import com.abdulkus.essentialremap.ScreenOffKeyAccess\nimport com.abdulkus.essentialremap.ui.AppLanguage\nimport com.abdulkus.essentialremap.ui.translate\n",
    "setup translation imports",
)
old_text = '''    private fun text(english: String, russian: String): String {
        val language = appContext.getSharedPreferences("essential_remap_ui", Context.MODE_PRIVATE)
            .getString("language", "en")
        return if (language == "ru") russian else english
    }
'''
new_text = '''    private fun text(english: String, russian: String): String {
        val code = appContext.getSharedPreferences("essential_remap_ui", Context.MODE_PRIVATE)
            .getString("language", "en")
        val language = AppLanguage.fromCode(code) ?: AppLanguage.ENGLISH
        return language.translate(english, russian)
    }
'''
s = replace_once(s, old_text, new_text, "setup text translator")
write(path, s)


# ---------------------------------------------------------------------------
# Reboot reminder notification: translate for all selected languages.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/setup/SleepMonitorBootReceiver.kt"
s = read(path)
s = replace_once(
    s,
    "import com.abdulkus.essentialremap.ui.UserPreferences\n",
    "import com.abdulkus.essentialremap.ui.UserPreferences\nimport com.abdulkus.essentialremap.ui.translate\n",
    "boot reminder translation import",
)
s = s.replace(
    "            val russian = language == AppLanguage.RUSSIAN\n",
    "            val selectedLanguage = language ?: AppLanguage.ENGLISH\n",
)
s = s.replace(
    '                    if (russian) "Монитор сна Essential Remap" else "Essential Remap sleep monitor",',
    '                    selectedLanguage.translate("Essential Remap sleep monitor", "Монитор сна Essential Remap"),',
)
s = s.replace(
    '''                    .setContentTitle(
                        if (russian) "Перезапустите монитор сна" else "Restart the sleep monitor",
                    )
                    .setContentText(
                        if (russian) {
                            "Для работы Essential Key с выключенным экраном требуется повторная активация после перезагрузки."
                        } else {
                            "Screen-off Essential Key handling must be reactivated after a phone reboot."
                        },
                    )
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            if (russian) {
                                "Откройте Essential Remap, включите Wireless debugging и нажмите «Перезапуск» у монитора сна."
                            } else {
                                "Open Essential Remap, enable Wireless debugging, then tap Restart for the sleep monitor."
                            },
                        ),
                    )
''',
    '''                    .setContentTitle(
                        selectedLanguage.translate("Restart the sleep monitor", "Перезапустите монитор сна"),
                    )
                    .setContentText(
                        selectedLanguage.translate(
                            "Screen-off Essential Key handling must be reactivated after a phone reboot.",
                            "Для работы Essential Key с выключенным экраном требуется повторная активация после перезагрузки.",
                        ),
                    )
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            selectedLanguage.translate(
                                "Open Essential Remap, enable Wireless debugging, then tap Restart for the sleep monitor.",
                                "Откройте Essential Remap, включите Wireless debugging и нажмите «Перезапуск» у монитора сна.",
                            ),
                        ),
                    )
''',
)
if "russian" in s:
    raise SystemExit("SleepMonitorBootReceiver still contains old russian-only selector")
write(path, s)


# ---------------------------------------------------------------------------
# v0.1.23 release metadata and stale README setting.
# ---------------------------------------------------------------------------
path = "app/build.gradle.kts"
s = read(path)
s = replace_once(s, "versionCode = 23", "versionCode = 24", "versionCode 24")
s = replace_once(s, 'versionName = "0.1.22"', 'versionName = "0.1.23"', "versionName 0.1.23")
write(path, s)

path = ".github/workflows/android.yml"
s = read(path)
s = replace_once(s, "APP_VERSION: 0.1.22", "APP_VERSION: 0.1.23", "workflow version 0.1.23")
write(path, s)

path = "README.md"
s = read(path)
s = s.replace("settings put secure nt_block_essential_key 0", "settings put secure nt_block_essential_key 1")
write(path, s)

print("v0.1.23 patch applied successfully")
