from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


mapper = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt"
main = "app/src/main/java/com/abdulkus/essentialremap/MainActivity.kt"
gradle = "app/build.gradle.kts"
workflow = ".github/workflows/android.yml"

replace_once(
    mapper,
    "import com.abdulkus.essentialremap.setup.ShellKeyMonitorCommands\n",
    "import com.abdulkus.essentialremap.setup.ShellKeyMonitorCommands\n"
    "import com.abdulkus.essentialremap.update.DownloadedUpdate\n"
    "import com.abdulkus.essentialremap.update.GitHubRelease\n"
    "import com.abdulkus.essentialremap.update.UpdatePromptState\n",
)

replace_once(
    mapper,
    "    openDonate: () -> Unit,\n    checkForUpdates: () -> Unit,\n    openSetupVideo: () -> Unit,\n",
    "    openDonate: () -> Unit,\n"
    "    updateState: UpdatePromptState,\n"
    "    checkForUpdates: () -> Unit,\n"
    "    downloadUpdate: (GitHubRelease) -> Unit,\n"
    "    installUpdate: (DownloadedUpdate) -> Unit,\n"
    "    dismissUpdate: () -> Unit,\n"
    "    openSetupVideo: () -> Unit,\n",
)

replace_once(
    mapper,
    "            openSetupVideo = openSetupVideo,\n            openAccessibilitySettings = openAccessibilitySettings,\n",
    "            openSetupVideo = openSetupVideo,\n"
    "            updateState = updateState,\n"
    "            checkForUpdates = checkForUpdates,\n"
    "            downloadUpdate = downloadUpdate,\n"
    "            installUpdate = installUpdate,\n"
    "            dismissUpdate = dismissUpdate,\n"
    "            openAccessibilitySettings = openAccessibilitySettings,\n",
)

replace_once(
    mapper,
    "    openSetupVideo: () -> Unit,\n    openAccessibilitySettings: () -> Unit,\n",
    "    openSetupVideo: () -> Unit,\n"
    "    updateState: UpdatePromptState,\n"
    "    checkForUpdates: () -> Unit,\n"
    "    downloadUpdate: (GitHubRelease) -> Unit,\n"
    "    installUpdate: (DownloadedUpdate) -> Unit,\n"
    "    dismissUpdate: () -> Unit,\n"
    "    openAccessibilitySettings: () -> Unit,\n",
)

replace_once(mapper, "    val pageCount = if (screenOffEnabled) 5 else 4\n", "    val pageCount = if (screenOffEnabled) 6 else 5\n")

replace_once(
    mapper,
    "                page == 0 -> ModeChoiceStep(language, screenOffEnabled, setScreenOffEnabled, openSetupVideo)\n                page == 1 -> BaseSetupStep(\n",
    "                page == 0 -> UpdateCheckStep(\n"
    "                    language,\n"
    "                    updateState,\n"
    "                    checkForUpdates,\n"
    "                    downloadUpdate,\n"
    "                    installUpdate,\n"
    "                    dismissUpdate,\n"
    "                )\n"
    "                page == 1 -> ModeChoiceStep(language, screenOffEnabled, setScreenOffEnabled, openSetupVideo)\n"
    "                page == 2 -> BaseSetupStep(\n",
)
replace_once(mapper, "                page == 2 -> AccessibilityStep(language, state, openAccessibilitySettings)\n", "                page == 3 -> AccessibilityStep(language, state, openAccessibilitySettings)\n")
replace_once(mapper, "                screenOffEnabled && page == 3 -> SleepSetupStep(\n", "                screenOffEnabled && page == 4 -> SleepSetupStep(\n")

replace_once(
    mapper,
    "                        page == 0 -> true\n                        page == 1 -> keyReleased\n                        page == 2 -> serviceReady\n                        screenOffEnabled && page == 3 -> screenOffReady\n",
    "                        page == 0 -> true\n"
    "                        page == 1 -> true\n"
    "                        page == 2 -> keyReleased\n"
    "                        page == 3 -> serviceReady\n"
    "                        screenOffEnabled && page == 4 -> screenOffReady\n",
)

update_step = r'''@Composable
private fun UpdateCheckStep(
    language: AppLanguage,
    updateState: UpdatePromptState,
    checkForUpdates: () -> Unit,
    downloadUpdate: (GitHubRelease) -> Unit,
    installUpdate: (DownloadedUpdate) -> Unit,
    dismissUpdate: () -> Unit,
) {
    LaunchedEffect(Unit) { checkForUpdates() }
    StepHeading(
        "00",
        language.t("Check for updates", "Проверьте обновления"),
        language.t(
            "Before setup, make sure you are using the latest Essential Remap. You can continue without updating if needed.",
            "Перед настройкой убедитесь, что установлена последняя версия Essential Remap. При необходимости можно продолжить без обновления.",
        ),
    )
    Spacer(Modifier.height(22.dp))
    when (updateState) {
        UpdatePromptState.Checking -> {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(language.t("Checking for updates", "Проверяем обновления"), fontWeight = FontWeight.SemiBold)
                        Text(
                            language.t("Looking at the latest GitHub release…", "Проверяем последний релиз на GitHub…"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        UpdatePromptState.None -> {
            StatusCard(
                true,
                language.t("Latest version installed", "Установлена последняя версия"),
                language.t("You can continue with setup.", "Можно продолжать настройку."),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = checkForUpdates, modifier = Modifier.fillMaxWidth()) {
                Text(language.t("CHECK AGAIN", "ПРОВЕРИТЬ ЕЩЁ РАЗ"))
            }
        }
        UpdatePromptState.Dismissed -> {
            StatusCard(
                true,
                language.t("Update skipped", "Обновление пропущено"),
                language.t("You can continue with setup and update later from Settings.", "Можно продолжить настройку и обновиться позже из настроек."),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = checkForUpdates, modifier = Modifier.fillMaxWidth()) {
                Text(language.t("CHECK AGAIN", "ПРОВЕРИТЬ ЕЩЁ РАЗ"))
            }
        }
        is UpdatePromptState.Available -> {
            val release = updateState.release
            WarningCard(
                text = language.t(
                    "Update ${release.version} is available",
                    "Доступно обновление ${release.version}",
                ),
                detail = release.title,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { downloadUpdate(release) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(language.t("DOWNLOAD UPDATE", "СКАЧАТЬ ОБНОВЛЕНИЕ"))
            }
            TextButton(onClick = dismissUpdate, modifier = Modifier.fillMaxWidth()) {
                Text(language.t("CONTINUE WITHOUT UPDATE", "ПРОДОЛЖИТЬ БЕЗ ОБНОВЛЕНИЯ"))
            }
        }
        is UpdatePromptState.Downloading -> {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(language.t("Downloading update", "Скачиваем обновление"), fontWeight = FontWeight.SemiBold)
                        Text(
                            updateState.progressPercent?.let { "$it%" }
                                ?: language.t("Downloading…", "Скачивание…"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        is UpdatePromptState.Ready -> {
            StatusCard(
                true,
                language.t("Update downloaded", "Обновление скачано"),
                language.t("Install it before continuing setup.", "Установите его перед продолжением настройки."),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { installUpdate(updateState.update) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(language.t("INSTALL UPDATE", "УСТАНОВИТЬ ОБНОВЛЕНИЕ"))
            }
        }
        is UpdatePromptState.Error -> {
            WarningCard(
                text = language.t("Update download failed", "Не удалось скачать обновление"),
                detail = language.t("Check your connection and try again.", "Проверьте интернет и попробуйте ещё раз."),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { downloadUpdate(updateState.release) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(language.t("RETRY", "ПОВТОРИТЬ"))
            }
            TextButton(onClick = dismissUpdate, modifier = Modifier.fillMaxWidth()) {
                Text(language.t("CONTINUE WITHOUT UPDATE", "ПРОДОЛЖИТЬ БЕЗ ОБНОВЛЕНИЯ"))
            }
        }
    }
}

'''
replace_once(mapper, "@Composable\nprivate fun ModeChoiceStep(\n", update_step + "@Composable\nprivate fun ModeChoiceStep(\n")

replace_once(
    mapper,
    "    StepHeading(\n        \"00\",\n        language.t(\"Choose setup mode\", \"Выберите режим работы\"),\n",
    "    StepHeading(\n        \"01\",\n        language.t(\"Choose setup mode\", \"Выберите режим работы\"),\n",
)
replace_once(
    mapper,
    "    StepHeading(\n        \"01\",\n        language.t(\"Release Essential Key\", \"Освободите Essential Key\"),\n",
    "    StepHeading(\n        \"02\",\n        language.t(\"Release Essential Key\", \"Освободите Essential Key\"),\n",
)
replace_once(
    mapper,
    "    StepHeading(\n        \"02\",\n        language.t(\"Enable Essential Remap\", \"Включите Essential Remap\"),\n",
    "    StepHeading(\n        \"03\",\n        language.t(\"Enable Essential Remap\", \"Включите Essential Remap\"),\n",
)
replace_once(
    mapper,
    "    StepHeading(\n        \"03\",\n        language.t(\"Enable screen-off handling\", \"Включите работу с выключенным экраном\"),\n",
    "    StepHeading(\n        \"04\",\n        language.t(\"Enable screen-off handling\", \"Включите работу с выключенным экраном\"),\n",
)
replace_once(mapper, "        if (screenOffEnabled) \"04\" else \"03\",\n", "        if (screenOffEnabled) \"05\" else \"04\",\n")

replace_once(
    main,
    "                        openDonate = ::openDonate,\n                        checkForUpdates = { startUpdateCheck(showResult = true) },\n                        openSetupVideo = {\n",
    "                        openDonate = ::openDonate,\n"
    "                        updateState = updateState,\n"
    "                        checkForUpdates = { startUpdateCheck(showResult = userPreferences.onboardingComplete) },\n"
    "                        downloadUpdate = ::downloadUpdate,\n"
    "                        installUpdate = ::installUpdate,\n"
    "                        dismissUpdate = { updatePromptState.value = UpdatePromptState.Dismissed },\n"
    "                        openSetupVideo = {\n",
)

old_prompt = '''                    InAppPromptHost(
                        language = userPreferences.language ?: AppLanguage.ENGLISH,
                        updateState = updateState,
                        showSupportPrompt = supportVisible,
                        onDownloadUpdate = ::downloadUpdate,
                        onInstallUpdate = ::installUpdate,
                        onDismissUpdate = { updatePromptState.value = UpdatePromptState.Dismissed },
                        onDonate = {
                            showSupportPrompt.value = false
                            openDonate()
                        },
                        onDismissSupport = { showSupportPrompt.value = false },
                    )
'''
new_prompt = '''                    if (userPreferences.onboardingComplete) {
                        InAppPromptHost(
                            language = userPreferences.language ?: AppLanguage.ENGLISH,
                            updateState = updateState,
                            showSupportPrompt = supportVisible,
                            onDownloadUpdate = ::downloadUpdate,
                            onInstallUpdate = ::installUpdate,
                            onDismissUpdate = { updatePromptState.value = UpdatePromptState.Dismissed },
                            onDonate = {
                                showSupportPrompt.value = false
                                openDonate()
                            },
                            onDismissSupport = { showSupportPrompt.value = false },
                        )
                    }
'''
replace_once(main, old_prompt, new_prompt)

replace_once(gradle, '        versionCode = 29\n        versionName = "0.1.28"\n', '        versionCode = 30\n        versionName = "0.1.30"\n')
replace_once(workflow, '  APP_VERSION: 0.1.29\n', '  APP_VERSION: 0.1.30\n')
