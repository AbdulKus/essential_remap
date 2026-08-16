from pathlib import Path


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    Path(path).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Missing patch target: {label}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# UI fixes: full-width ADB result card, screen-off readiness, and DND gating.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt"
s = read(path)

s = replace_once(
    s,
    '    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {\n        Column(Modifier.padding(16.dp)) {',
    '    Card(\n        modifier = Modifier.fillMaxWidth(),\n        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),\n    ) {\n        Column(Modifier.padding(16.dp)) {',
    "full width setup progress card",
)

s = replace_once(
    s,
    '    val setupReady = state.keyReleased && state.serviceEnabled && state.competingServices.isEmpty()\n    val ready = setupReady && state.settings.remappingEnabled',
    '    val baseSetupReady = state.keyReleased && state.serviceEnabled && state.competingServices.isEmpty()\n    val screenOffReady = !screenOffEnabled || state.setup.screenOffAccessGranted\n    val setupReady = baseSetupReady && screenOffReady\n    val ready = setupReady && state.settings.remappingEnabled',
    "screen-off contributes to setup readiness",
)

warning_anchor = '''                state.competingServices.isNotEmpty() -> item {
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
'''
warning_replacement = warning_anchor + '''                screenOffEnabled && !state.setup.screenOffAccessGranted -> item {
                    WarningCard(
                        text = language.t(
                            "Sleep monitor is not running",
                            "Монитор сна не запущен",
                        ),
                        detail = language.t(
                            "Restart the sleep monitor to handle Essential Key while the display is off.",
                            "Перезапустите монитор сна для работы Essential Key при выключенном экране.",
                        ),
                        actionLabel = language.t("FIX", "ИСПРАВИТЬ"),
                        action = { beginPackageSetup(PackageOperation.INSTALL_SLEEP_MONITOR) },
                    )
                }
'''
s = replace_once(s, warning_anchor, warning_replacement, "sleep monitor warning")

s = replace_once(
    s,
    '''            chooseSound = { actionGesture = null; soundGesture = gesture },
            chooseKind = { kind -> updateActionKind(gesture, kind); actionGesture = null },''',
    '''            chooseSound = { actionGesture = null; soundGesture = gesture },
            soundModeAllowed = state.notificationPolicyAccess,
            requestSoundModeAccess = {
                actionGesture = null
                openNotificationPolicySettings()
            },
            chooseKind = { kind -> updateActionKind(gesture, kind); actionGesture = null },''',
    "pass sound permission state",
)

s = replace_once(
    s,
    '''    chooseHttp: () -> Unit,
    chooseSound: () -> Unit,
    chooseKind: (ActionKind) -> Unit,''',
    '''    chooseHttp: () -> Unit,
    chooseSound: () -> Unit,
    soundModeAllowed: Boolean,
    requestSoundModeAccess: () -> Unit,
    chooseKind: (ActionKind) -> Unit,''',
    "sound chooser signature",
)

s = replace_once(
    s,
    '        ActionOption(language.t("Sound mode", "Режим звука"), run = chooseSound),',
    '''        ActionOption(
            language.t("Sound mode", "Режим звука"),
            subtitle = if (soundModeAllowed) null else language.t(
                "Grant Do Not Disturb access first",
                "Сначала разрешите доступ к режиму «Не беспокоить»",
            ),
            run = if (soundModeAllowed) chooseSound else requestSoundModeAccess,
        ),''',
    "sound mode permission gate",
)

write(path, s)


# ---------------------------------------------------------------------------
# ADB saved-key reconnect: do not launch Developer Options while Wireless ADB
# is already enabled. Only take the user to Settings when it is actually off,
# or when pairing is genuinely required.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/setup/EssentialKeySetupCoordinator.kt"
s = read(path)

old = '''    private suspend fun connectUsingStoredIdentity(): LocalAdbConnectionManager? {
        if (!LocalAdbConnectionManager.hasStoredIdentity(appContext)) {
            diagnostics.log("No stored ADB identity; pairing is required")
            openWirelessDebuggingSettings()
            return null
        }

        runCatching {
            return connectWithRetry(attempts = 1, discoveryTimeoutMs = SAVED_KEY_INITIAL_DISCOVERY_TIMEOUT_MS)
        }.onFailure {
            diagnostics.log("Saved-key fast connect unavailable: ${it.fullDescription()}")
        }

        _state.value = _state.value.copy(
            phase = SetupPhase.WAITING_FOR_WIRELESS_DEBUGGING,
            message = text(
                "Turn on Wireless debugging. Essential Remap will reconnect automatically.",
                "Включите Wireless debugging. Essential Remap подключится автоматически.",
            ),
        )
        openWirelessDebuggingSettings()
        postProgressNotification()

        repeat(SAVED_KEY_WAIT_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(SAVED_KEY_WAIT_DELAY_MS)
            val result = runCatching {
                connectWithRetry(attempts = 1, discoveryTimeoutMs = SAVED_KEY_RETRY_DISCOVERY_TIMEOUT_MS)
            }
            result.getOrNull()?.let { return it }
            val error = result.exceptionOrNull()
            if (error != null) {
                diagnostics.log("Saved-key retry ${attempt + 1}/$SAVED_KEY_WAIT_ATTEMPTS: ${error.fullDescription()}")
                if (isAuthorizationFailure(error)) {
                    diagnostics.log("Stored ADB identity is no longer authorized; falling back to pairing")
                    return null
                }
            }
        }
        diagnostics.log("Wireless debugging did not become connectable with stored key")
        throw IOException(
            "Wireless debugging is not available with the saved key. Turn it on and try again.",
        )
    }
'''
new = '''    private suspend fun connectUsingStoredIdentity(): LocalAdbConnectionManager? {
        if (!LocalAdbConnectionManager.hasStoredIdentity(appContext)) {
            diagnostics.log("No stored ADB identity; pairing is required")
            return null
        }

        val fastResult = runCatching {
            connectWithRetry(
                attempts = SAVED_KEY_FAST_ATTEMPTS,
                discoveryTimeoutMs = SAVED_KEY_INITIAL_DISCOVERY_TIMEOUT_MS,
            )
        }
        fastResult.getOrNull()?.let { return it }
        fastResult.exceptionOrNull()?.let { error ->
            diagnostics.log("Saved-key fast connect unavailable: ${error.fullDescription()}")
            if (isAuthorizationFailure(error)) {
                diagnostics.log("Stored ADB identity is no longer authorized; pairing is required")
                return null
            }
        }

        val wirelessDebuggingEnabled = isWirelessDebuggingEnabled()
        _state.value = _state.value.copy(
            phase = if (wirelessDebuggingEnabled) SetupPhase.CONNECTING else SetupPhase.WAITING_FOR_WIRELESS_DEBUGGING,
            message = if (wirelessDebuggingEnabled) {
                text(
                    "Wireless debugging is already enabled. Reconnecting with the saved key…",
                    "Wireless debugging уже включён. Переподключаемся по сохранённому ключу…",
                )
            } else {
                text(
                    "Turn on Wireless debugging. Essential Remap will reconnect automatically.",
                    "Включите Wireless debugging. Essential Remap подключится автоматически.",
                )
            },
        )
        if (!wirelessDebuggingEnabled) {
            diagnostics.log("Wireless debugging global setting is off; opening Android settings")
            openWirelessDebuggingSettings()
        } else {
            diagnostics.log("Wireless debugging global setting is already on; staying in Essential Remap")
        }
        postProgressNotification()

        repeat(SAVED_KEY_WAIT_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(SAVED_KEY_WAIT_DELAY_MS)
            val result = runCatching {
                connectWithRetry(attempts = 1, discoveryTimeoutMs = SAVED_KEY_RETRY_DISCOVERY_TIMEOUT_MS)
            }
            result.getOrNull()?.let { return it }
            val error = result.exceptionOrNull()
            if (error != null) {
                diagnostics.log("Saved-key retry ${attempt + 1}/$SAVED_KEY_WAIT_ATTEMPTS: ${error.fullDescription()}")
                if (isAuthorizationFailure(error)) {
                    diagnostics.log("Stored ADB identity is no longer authorized; falling back to pairing")
                    return null
                }
            }
        }
        diagnostics.log("Wireless debugging did not become connectable with stored key")
        throw IOException(
            if (isWirelessDebuggingEnabled()) {
                "Wireless debugging is enabled, but its ADB endpoint was not found. Try again."
            } else {
                "Wireless debugging is not available with the saved key. Turn it on and try again."
            },
        )
    }

    private fun isWirelessDebuggingEnabled(): Boolean = runCatching {
        Settings.Global.getInt(appContext.contentResolver, WIRELESS_DEBUGGING_GLOBAL_SETTING, 0) == 1
    }.onFailure {
        diagnostics.log("Could not read Wireless debugging global setting: ${it.fullDescription()}")
    }.getOrDefault(false)
'''
s = replace_once(s, old, new, "saved ADB reconnect logic")

s = replace_once(
    s,
    '''        private const val SAVED_KEY_INITIAL_DISCOVERY_TIMEOUT_MS = 2_500L
        private const val SAVED_KEY_RETRY_DISCOVERY_TIMEOUT_MS = 1_500L''',
    '''        private const val SAVED_KEY_FAST_ATTEMPTS = 2
        private const val SAVED_KEY_INITIAL_DISCOVERY_TIMEOUT_MS = 2_500L
        private const val SAVED_KEY_RETRY_DISCOVERY_TIMEOUT_MS = 1_500L''',
    "saved key fast attempts constant",
)

s = replace_once(
    s,
    '''        private const val ACTION_WIRELESS_DEBUGGING_SETTINGS = "android.settings.WIRELESS_DEBUGGING_SETTINGS"
        private const val SETTINGS_FRAGMENT_ARGUMENT_KEY = ":settings:fragment_args_key"''',
    '''        private const val ACTION_WIRELESS_DEBUGGING_SETTINGS = "android.settings.WIRELESS_DEBUGGING_SETTINGS"
        private const val WIRELESS_DEBUGGING_GLOBAL_SETTING = "adb_wifi_enabled"
        private const val SETTINGS_FRAGMENT_ARGUMENT_KEY = ":settings:fragment_args_key"''',
    "wireless debugging setting constant",
)

write(path, s)


# ---------------------------------------------------------------------------
# Add translations for the new strings.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/ui/Translations.kt"
s = read(path)
anchor = 'private val translations = mapOf(\n'
insert = '''private val translations = mapOf(
    "Restart the sleep monitor to handle Essential Key while the display is off." to a("Starte den Ruhemodus-Monitor neu, damit Essential Key bei ausgeschaltetem Display funktioniert.", "Redémarrez le moniteur de veille pour utiliser Essential Key lorsque l’écran est éteint.", "Uruchom ponownie monitor uśpienia, aby Essential Key działał przy wyłączonym ekranie.", "Перезапустіть монітор сну, щоб Essential Key працювала при вимкненому екрані.", "Mulai ulang monitor tidur agar Essential Key berfungsi saat layar mati.", "请重新启动休眠监视器，以便在熄屏时使用 Essential Key。", "画面オフ時に Essential Key を使うにはスリープモニターを再起動してください。", "화면이 꺼진 상태에서 Essential Key를 사용하려면 절전 모니터를 다시 시작하세요."),
    "Grant Do Not Disturb access first" to a("Zuerst Zugriff auf „Nicht stören“ erlauben", "Autorisez d’abord l’accès à « Ne pas déranger »", "Najpierw zezwól na dostęp do trybu Nie przeszkadzać", "Спочатку надайте доступ до режиму «Не турбувати»", "Izinkan akses Jangan Ganggu terlebih dahulu", "请先授予“勿扰模式”访问权限", "先に「サイレント モード」へのアクセスを許可してください", "먼저 방해 금지 모드 접근 권한을 허용하세요"),
    "Wireless debugging is already enabled. Reconnecting with the saved key…" to a("Wireless-Debugging ist bereits aktiviert. Verbindung mit dem gespeicherten Schlüssel wird wiederhergestellt…", "Le débogage sans fil est déjà activé. Reconnexion avec la clé enregistrée…", "Debugowanie bezprzewodowe jest już włączone. Ponowne łączenie zapisanym kluczem…", "Wireless debugging уже ввімкнено. Повторне підключення збереженим ключем…", "Wireless debugging sudah aktif. Menyambungkan kembali dengan kunci tersimpan…", "无线调试已开启，正在使用已保存的密钥重新连接…", "ワイヤレスデバッグはすでに有効です。保存済みのキーで再接続しています…", "무선 디버깅이 이미 켜져 있습니다. 저장된 키로 다시 연결하는 중…"),
'''
if anchor not in s:
    raise SystemExit("Missing translations map anchor")
s = s.replace(anchor, insert, 1)
write(path, s)


# ---------------------------------------------------------------------------
# Version bump. The normal release workflow version is bumped separately by
# the connector after this patch lands, because workflow mutation is blocked
# for GITHUB_TOKEN pushes.
# ---------------------------------------------------------------------------
path = "app/build.gradle.kts"
s = read(path)
s = replace_once(s, '        versionCode = 24\n        versionName = "0.1.23"', '        versionCode = 25\n        versionName = "0.1.24"', "app version")
write(path, s)

path = "README.md"
s = read(path)
s = s.replace("0.1.23", "0.1.24")
write(path, s)

print("v0.1.24 patch applied successfully")
