from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text()


def write(path: str, text: str) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(text)


def replace(path: str, old: str, new: str, count: int = 1) -> None:
    text = read(path)
    actual = text.count(old)
    if actual < count:
        raise SystemExit(f"{path}: expected {count} occurrence(s), found {actual}: {old[:120]!r}")
    write(path, text.replace(old, new, count))


def regex_replace(path: str, pattern: str, replacement: str, count: int = 1) -> None:
    text = read(path)
    new_text, n = re.subn(pattern, replacement, text, count=count, flags=re.S)
    if n != count:
        raise SystemExit(f"{path}: regex expected {count} replacement(s), got {n}: {pattern[:100]!r}")
    write(path, new_text)


# ---------------------------------------------------------------------------
# UI preferences: persist the global screen-off mode and migrate existing users.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/com/abdulkus/essentialremap/ui/UserPreferences.kt",
    r'''package com.abdulkus.essentialremap.ui

import android.content.Context
import com.abdulkus.essentialremap.ScreenOffKeyAccess

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    RUSSIAN("ru");

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }
    }
}

class UserPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "essential_remap_ui",
        Context.MODE_PRIVATE,
    )

    var language: AppLanguage?
        get() = AppLanguage.fromCode(preferences.getString(KEY_LANGUAGE, null))
        set(value) {
            preferences.edit().putString(KEY_LANGUAGE, value?.code).apply()
        }

    var onboardingComplete: Boolean
        get() = preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()
        }

    var screenOffEnabled: Boolean
        get() {
            if (!preferences.contains(KEY_SCREEN_OFF_ENABLED)) {
                // 0.1.20 and older had no global switch. Preserve screen-off behavior only for
                // installations that had actually started the shell monitor before upgrading.
                val migrated = onboardingComplete && ScreenOffKeyAccess.wasConfigured(appContext)
                preferences.edit().putBoolean(KEY_SCREEN_OFF_ENABLED, migrated).apply()
                return migrated
            }
            return preferences.getBoolean(KEY_SCREEN_OFF_ENABLED, false)
        }
        set(value) {
            preferences.edit().putBoolean(KEY_SCREEN_OFF_ENABLED, value).apply()
        }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_SCREEN_OFF_ENABLED = "screen_off_enabled"
    }
}
''',
)

# Keep a legacy configured marker available for the one-time preference migration.
replace(
    "app/src/main/java/com/abdulkus/essentialremap/ScreenOffKeyAccess.kt",
    "    fun markStarted(context: Context) {\n",
    "    fun wasConfigured(context: Context): Boolean =\n        preferences(context).getBoolean(KEY_STARTED, false)\n\n    fun markStarted(context: Context) {\n",
)

# ---------------------------------------------------------------------------
# Setup operations: base key release is separate from the optional sleep monitor.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/com/abdulkus/essentialremap/setup/NothingPackageCommands.kt",
    r'''package com.abdulkus.essentialremap.setup

enum class PackageOperation {
    DISABLE,
    INSTALL_SLEEP_MONITOR,
    RESTORE,
}

object NothingPackageCommands {
    const val ESSENTIAL_SPACE = "com.nothing.ntessentialspace"
    const val ESSENTIAL_RECORDER = "com.nothing.ntessentialrecorder"

    val packages = listOf(ESSENTIAL_SPACE, ESSENTIAL_RECORDER)

    fun commands(operation: PackageOperation): List<String> = packages.map { packageName ->
        when (operation) {
            PackageOperation.DISABLE,
            PackageOperation.INSTALL_SLEEP_MONITOR,
            -> "pm disable-user --user 0 $packageName"
            PackageOperation.RESTORE -> "pm enable --user 0 $packageName"
        }
    }
}

object EssentialKeySetupCommands {
    const val COMMAND_OK = "essential-remap:ok"
    const val ENABLE_RELIABLE_SCREEN_OFF_DISPATCH =
        "settings put secure nt_block_essential_key 1 && echo $COMMAND_OK"
    const val READ_SCREEN_OFF_WAKE_SETTING = "settings get secure nt_block_essential_key"

    fun commands(operation: PackageOperation): List<String> = buildList {
        when (operation) {
            PackageOperation.DISABLE -> {
                addAll(NothingPackageCommands.commands(operation))
            }
            PackageOperation.INSTALL_SLEEP_MONITOR -> {
                // Re-applying package disable is intentional and makes restart/install idempotent.
                addAll(NothingPackageCommands.commands(operation))
                add(ENABLE_RELIABLE_SCREEN_OFF_DISPATCH)
                add(ShellKeyMonitorCommands.INSTALL)
            }
            PackageOperation.RESTORE -> {
                add(ShellKeyMonitorCommands.stop)
                addAll(NothingPackageCommands.commands(operation))
            }
        }
    }

    fun isAllowlisted(command: String): Boolean =
        PackageOperation.entries.any { command in commands(it) }
}
''',
)

# Preserve rev8. Only make the manual command list mode-aware.
regex_replace(
    "app/src/main/java/com/abdulkus/essentialremap/setup/ShellKeyMonitorCommands.kt",
    r'''    fun manualAdbCommands\(\): String = listOf\(\n        "adb shell pm disable-user --user 0 \$\{NothingPackageCommands\.ESSENTIAL_SPACE\}",\n        "adb shell pm disable-user --user 0 \$\{NothingPackageCommands\.ESSENTIAL_RECORDER\}",\n        "adb shell settings put secure nt_block_essential_key 1",\n        "adb shell \\"\$installAndStart\\"",\n    \)\.joinToString\("\\n"\)''',
    r'''    fun manualAdbCommands(includeSleepMonitor: Boolean = true): String = buildList {
        add("adb shell pm disable-user --user 0 ${NothingPackageCommands.ESSENTIAL_SPACE}")
        add("adb shell pm disable-user --user 0 ${NothingPackageCommands.ESSENTIAL_RECORDER}")
        if (includeSleepMonitor) {
            add("adb shell settings put secure nt_block_essential_key 1")
            add("adb shell \"$installAndStart\"")
        }
    }.joinToString("\n")''',
)

# ---------------------------------------------------------------------------
# ADB identity: expose whether an existing trusted identity can be attempted first.
# ---------------------------------------------------------------------------
replace(
    "app/src/main/java/com/abdulkus/essentialremap/setup/LocalAdbConnectionManager.kt",
    '    override fun getDeviceName(): String = "Essential Remap"\n}\n',
    '''    override fun getDeviceName(): String = "Essential Remap"\n\n    companion object {\n        fun hasStoredIdentity(context: Context): Boolean =\n            EncryptedAdbIdentityStore(context.applicationContext).hasIdentity()\n    }\n}\n''',
)
replace(
    "app/src/main/java/com/abdulkus/essentialremap/setup/LocalAdbConnectionManager.kt",
    "    fun loadOrCreate(): AdbIdentity {\n",
    "    fun hasIdentity(): Boolean = identityFile.isFile && identityFile.length() > 0L\n\n    fun loadOrCreate(): AdbIdentity {\n",
)

# ---------------------------------------------------------------------------
# Boot reminder for users who opted into screen-off handling.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/com/abdulkus/essentialremap/setup/SleepMonitorBootReceiver.kt",
    r'''package com.abdulkus.essentialremap.setup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.abdulkus.essentialremap.MainActivity
import com.abdulkus.essentialremap.R
import com.abdulkus.essentialremap.ScreenOffKeyAccess
import com.abdulkus.essentialremap.ui.AppLanguage
import com.abdulkus.essentialremap.ui.UserPreferences

class SleepMonitorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = UserPreferences(context)
        if (!preferences.screenOffEnabled) return

        // The shell UID process never survives reboot. Clear the stale marker before notifying.
        ScreenOffKeyAccess.markStopped(context)
        postReminder(context, preferences.language)
    }

    companion object {
        private const val CHANNEL_ID = "essential_remap_sleep_monitor"
        private const val NOTIFICATION_ID = 2054

        fun cancelReminder(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        private fun postReminder(context: Context, language: AppLanguage?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val russian = language == AppLanguage.RUSSIAN
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    if (russian) "Монитор сна Essential Remap" else "Essential Remap sleep monitor",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val contentIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(
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
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
''',
)

# Gate stale shell broadcasts when the global screen-off option is disabled.
replace(
    "app/src/main/java/com/abdulkus/essentialremap/ShellKeyEventReceiver.kt",
    "import com.abdulkus.essentialremap.setup.SetupDiagnostics\n",
    "import com.abdulkus.essentialremap.setup.SetupDiagnostics\nimport com.abdulkus.essentialremap.ui.UserPreferences\n",
)
replace(
    "app/src/main/java/com/abdulkus/essentialremap/ShellKeyEventReceiver.kt",
    '''        if (intent.action != ACTION) {\n            diagnostics.log("Runtime receiver: rejected unexpected intent action=${intent.action}")\n            return\n        }\n''',
    '''        if (intent.action != ACTION) {\n            diagnostics.log("Runtime receiver: rejected unexpected intent action=${intent.action}")\n            return\n        }\n        if (!UserPreferences(context).screenOffEnabled) {\n            diagnostics.log("Runtime receiver: ignored because screen-off handling is disabled")\n            return\n        }\n''',
)

# Manifest: receive normal post-unlock BOOT_COMPLETED and register the reminder receiver.
replace(
    "app/src/main/AndroidManifest.xml",
    '    <uses-permission android:name="android.permission.WAKE_LOCK" />\n',
    '    <uses-permission android:name="android.permission.WAKE_LOCK" />\n    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n',
)
replace(
    "app/src/main/AndroidManifest.xml",
    '''        <receiver\n            android:name=".setup.PairingCodeReceiver"\n            android:exported="false" />\n''',
    '''        <receiver\n            android:name=".setup.PairingCodeReceiver"\n            android:exported="false" />\n\n        <receiver\n            android:name=".setup.SleepMonitorBootReceiver"\n            android:enabled="true"\n            android:exported="true">\n            <intent-filter>\n                <action android:name="android.intent.action.BOOT_COMPLETED" />\n            </intent-filter>\n        </receiver>\n''',
)

# ---------------------------------------------------------------------------
# MainActivity: coordinator owns settings routing; add video instruction and deep-link to Settings dialog.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/com/abdulkus/essentialremap/MainActivity.kt",
    r'''package com.abdulkus.essentialremap

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.abdulkus.essentialremap.platform.AccessibilityStatusReader
import com.abdulkus.essentialremap.setup.PackageOperation
import com.abdulkus.essentialremap.ui.AppLanguage
import com.abdulkus.essentialremap.ui.EssentialRemapApp
import com.abdulkus.essentialremap.ui.EssentialRemapTheme
import com.abdulkus.essentialremap.ui.MapperViewModel
import com.abdulkus.essentialremap.ui.UserPreferences

class MainActivity : ComponentActivity() {
    private val container get() = (application as EssentialKeyApplication).container
    private val viewModel: MapperViewModel by viewModels {
        MapperViewModel.Factory(
            container.repository,
            container.hapticEngine,
            container.setupCoordinator,
            container.launchableAppsReader,
        )
    }
    private val accessibilityStatusReader by lazy { AccessibilityStatusReader(this) }
    private val userPreferences by lazy { UserPreferences(this) }
    private var pendingPackageOperation: PackageOperation? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val operation = pendingPackageOperation.also { pendingPackageOperation = null }
        if (operation != null) {
            if (!granted) {
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
            startWirelessSetup(operation)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initiallyOpenSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        setContent {
            EssentialRemapTheme {
                EssentialRemapApp(
                    viewModel = viewModel,
                    preferences = userPreferences,
                    initiallyOpenSettings = initiallyOpenSettings,
                    openAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    openNotificationPolicySettings = ::openNotificationPolicySettings,
                    openDeveloperOptions = {
                        startActivity(developerOptionsIntent())
                    },
                    openAssistantSettings = ::openAssistantSettings,
                    openAppInfo = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            },
                        )
                    },
                    openDonate = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AbdulKus/donate")))
                    },
                    openSetupVideo = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/")))
                    },
                    beginPackageSetup = ::beginPackageSetup,
                    copyText = ::copyToClipboard,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSetup()
        viewModel.updateAccessibilityStatus(accessibilityStatusReader.read())
        val notificationManager = getSystemService(NotificationManager::class.java)
        viewModel.updateNotificationPolicyAccess(notificationManager.isNotificationPolicyAccessGranted)
        viewModel.updateDeveloperOptionsStatus(
            Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
        )
    }

    private fun beginPackageSetup(operation: PackageOperation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPackageOperation = operation
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startWirelessSetup(operation)
    }

    private fun startWirelessSetup(operation: PackageOperation) {
        // The coordinator first tries the persisted ADB identity. It opens Wireless debugging
        // settings only when the service is unavailable or pairing is actually required.
        viewModel.startPackageSetup(operation)
    }

    private fun developerOptionsIntent() =
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).putExtra(
            SETTINGS_FRAGMENT_ARGUMENT_KEY,
            WIRELESS_DEBUGGING_PREFERENCE_KEY,
        )

    private fun openAssistantSettings() {
        val voiceSettings = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        val intent = if (voiceSettings.resolveActivity(packageManager) != null) {
            voiceSettings
        } else {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
        startActivity(intent)
    }

    private fun openNotificationPolicySettings() {
        val detailIntent = Intent(
            ACTION_NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS,
            Uri.parse("package:$packageName"),
        ).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        if (detailIntent.resolveActivity(packageManager) != null &&
            runCatching { startActivity(detailIntent) }.isSuccess
        ) {
            return
        }
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    private fun copyToClipboard(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText("Essential Remap", text),
        )
        val message = if (userPreferences.language == AppLanguage.RUSSIAN) "Скопировано" else "Copied"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "open_settings"
        private const val ACTION_NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS =
            "android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS"
        private const val SETTINGS_FRAGMENT_ARGUMENT_KEY = ":settings:fragment_args_key"
        private const val WIRELESS_DEBUGGING_PREFERENCE_KEY = "toggle_adb_wireless"
    }
}
''',
)

# ---------------------------------------------------------------------------
# Coordinator: saved-key first, settings-assisted waiting, pairing only as fallback.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/setup/EssentialKeySetupCoordinator.kt"
replace(path, "import android.os.Build\n", "import android.os.Build\nimport android.provider.Settings\n")
replace(
    path,
    "    WAITING_FOR_CODE,\n",
    "    WAITING_FOR_WIRELESS_DEBUGGING,\n    WAITING_FOR_CODE,\n",
)
replace(
    path,
    "        SetupPhase.DISCOVERING,\n        SetupPhase.WAITING_FOR_CODE,\n",
    "        SetupPhase.DISCOVERING,\n        SetupPhase.WAITING_FOR_WIRELESS_DEBUGGING,\n        SetupPhase.WAITING_FOR_CODE,\n",
)

regex_replace(
    path,
    r'''    override fun start\(operation: PackageOperation\) \{.*?\n    \}\n\n    override fun submitPairingCode''',
    r'''    override fun start(operation: PackageOperation) {
        setupJob?.cancel()
        pairingCode = CompletableDeferred()
        _state.value = EssentialKeySetupState(
            packageStatus = statusReader.read(),
            screenOffAccessGranted = ScreenOffKeyAccess.isGranted(appContext),
            phase = SetupPhase.DISCOVERING,
            operation = operation,
            message = text(
                "Connecting to Wireless debugging…",
                "Подключаемся к Wireless debugging…",
            ),
        )
        diagnostics.log("--- Setup started: operation=$operation packageStatus=${_state.value.packageStatus} ---")
        setupJob = scope.launch {
            runCatching {
                val existingManager = connectUsingStoredIdentity()
                if (existingManager != null) {
                    diagnostics.log("Using previously paired ADB identity")
                    applyConnectedOperation(existingManager, operation)
                } else {
                    pairThenApply(operation)
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) return@onFailure
                diagnostics.log("Setup failed: ${error.fullDescription()}")
                _state.value = _state.value.copy(
                    packageStatus = statusReader.read(),
                    screenOffAccessGranted = ScreenOffKeyAccess.isGranted(appContext),
                    phase = SetupPhase.ERROR,
                    message = friendlyError(error),
                )
                postResultNotification(text("Setup failed", "Ошибка настройки"), friendlyError(error))
            }
        }
    }

    override fun submitPairingCode''',
)

# Replace the old pair-first applyOperation with separated pair/apply functions.
regex_replace(
    path,
    r'''    private suspend fun applyOperation\(\n        pairingEndpoint: AdbEndpoint,\n        code: String,\n        operation: PackageOperation,\n    \) \{.*?\n    \}\n\n    private fun pairWithFallback''',
    r'''    private suspend fun pairThenApply(operation: PackageOperation) {
        openWirelessDebuggingSettings()
        _state.value = _state.value.copy(
            phase = SetupPhase.WAITING_FOR_CODE,
            message = text(
                "Choose “Pair device with pairing code” and enter the six-digit code here.",
                "Выберите «Сопряжение с помощью кода» и введите сюда шестизначный код.",
            ),
        )
        postPairingNotification()
        val code = withTimeout(PAIRING_TIMEOUT_MS) { pairingCode.await() }
        diagnostics.log("Discovering live pairing endpoint after code submission")
        val pairingEndpoint = discoverAdbEndpoint(
            AdbMdns.SERVICE_TYPE_TLS_PAIRING,
            LIVE_PAIRING_DISCOVERY_TIMEOUT_MS,
        )
        _state.value = _state.value.copy(
            phase = SetupPhase.PAIRING,
            message = text("Pairing with Android", "Сопряжение с Android"),
        )
        postProgressNotification()
        pairWithFallback(pairingEndpoint, code)
        _state.value = _state.value.copy(
            phase = SetupPhase.CONNECTING,
            message = text("Connecting with the saved key", "Подключаемся по сохранённому ключу"),
        )
        postProgressNotification()
        delay(CONNECTION_AFTER_PAIR_DELAY_MS)
        applyConnectedOperation(connectWithRetry(), operation)
    }

    private suspend fun connectUsingStoredIdentity(): LocalAdbConnectionManager? {
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
        diagnostics.log("Wireless debugging did not become connectable with stored key; pairing fallback")
        return null
    }

    private suspend fun applyConnectedOperation(
        connectedManager: LocalAdbConnectionManager,
        operation: PackageOperation,
    ) {
        try {
            diagnostics.log("ADB connection established for operation=$operation")
            _state.value = _state.value.copy(
                phase = SetupPhase.APPLYING,
                message = when (operation) {
                    PackageOperation.DISABLE -> text("Releasing Essential Key", "Освобождаем Essential Key")
                    PackageOperation.INSTALL_SLEEP_MONITOR -> text("Starting sleep monitor", "Запускаем монитор сна")
                    PackageOperation.RESTORE -> text("Restoring Essential Space", "Откатываем к Essential Space")
                },
            )
            postProgressNotification()
            EssentialKeySetupCommands.commands(operation).forEach { command ->
                diagnostics.log("Executing allowlisted command: $command")
                val output = executeAllowlisted(connectedManager, command)
                diagnostics.log("Command output: ${output.take(MAX_LOG_OUTPUT_CHARS)}")
            }
            if (operation == PackageOperation.RESTORE) {
                ScreenOffKeyAccess.markStopped(appContext)
            }
            val packageStatus = verifyPackageState(connectedManager, operation)
            val screenOffAccessGranted = when (operation) {
                PackageOperation.INSTALL_SLEEP_MONITOR -> {
                    verifyScreenOffAccess(connectedManager)
                    ScreenOffKeyAccess.markStarted(appContext)
                    SleepMonitorBootReceiver.cancelReminder(appContext)
                    true
                }
                PackageOperation.DISABLE -> ScreenOffKeyAccess.isGranted(appContext)
                PackageOperation.RESTORE -> false
            }
            diagnostics.log("Operation verification succeeded: status=$packageStatus screenOff=$screenOffAccessGranted")
            _state.value = EssentialKeySetupState(
                packageStatus = packageStatus,
                screenOffAccessGranted = screenOffAccessGranted,
                phase = SetupPhase.COMPLETE,
                operation = operation,
                message = when (operation) {
                    PackageOperation.DISABLE -> text(
                        "Essential Key released. Wireless debugging can be turned off.",
                        "Essential Key освобождена. Wireless debugging можно выключить.",
                    )
                    PackageOperation.INSTALL_SLEEP_MONITOR -> text(
                        "Sleep monitor started. Wireless debugging can be turned off.",
                        "Монитор сна запущен. Wireless debugging можно выключить.",
                    )
                    PackageOperation.RESTORE -> text(
                        "Essential Space restored. Wireless debugging can be turned off.",
                        "Essential Space восстановлен. Wireless debugging можно выключить.",
                    )
                },
            )
            val successMessage = _state.value.message.orEmpty()
            ScreenOffKeyAccess.notifyChanged()
            postResultNotification(text("Setup complete", "Настройка завершена"), successMessage)
            returnToApp()
        } finally {
            runCatching { connectedManager.disconnect() }
        }
    }

    private fun pairWithFallback''',
)

# Make connectWithRetry configurable so a saved-key probe can be fast.
replace(
    path,
    "    private suspend fun connectWithRetry(): LocalAdbConnectionManager {\n        var lastError: Throwable? = null\n        repeat(CONNECTION_ATTEMPTS) { attempt ->\n",
    "    private suspend fun connectWithRetry(\n        attempts: Int = CONNECTION_ATTEMPTS,\n        discoveryTimeoutMs: Long = CONNECTION_DISCOVERY_TIMEOUT_MS,\n    ): LocalAdbConnectionManager {\n        var lastError: Throwable? = null\n        repeat(attempts) { attempt ->\n",
)
replace(
    path,
    '            diagnostics.log("Connect discovery attempt ${attempt + 1}/$CONNECTION_ATTEMPTS")\n',
    '            diagnostics.log("Connect discovery attempt ${attempt + 1}/$attempts")\n',
)
replace(
    path,
    "                    CONNECTION_DISCOVERY_TIMEOUT_MS,\n",
    "                    discoveryTimeoutMs,\n",
    count=1,
)
replace(
    path,
    '            "Could not connect to Android’s Wireless debugging service after pairing. " +\n                "Keep Wireless debugging enabled and try again. ${lastError?.message.orEmpty()}",\n',
    '            "Could not connect to Android Wireless debugging. " +\n                "Keep Wireless debugging enabled and try again. ${lastError?.message.orEmpty()}",\n',
)

# Package verification: INSTALL_SLEEP_MONITOR also expects disabled Nothing packages.
replace(
    path,
    '        val flag = if (operation == PackageOperation.DISABLE) "-d" else "-e"\n',
    '        val flag = if (operation == PackageOperation.RESTORE) "-e" else "-d"\n',
)
replace(
    path,
    '''        return if (operation == PackageOperation.DISABLE) {\n            NothingPackageStatus.DISABLED\n        } else {\n            NothingPackageStatus.ENABLED\n        }\n''',
    '''        return if (operation == PackageOperation.RESTORE) {\n            NothingPackageStatus.ENABLED\n        } else {\n            NothingPackageStatus.DISABLED\n        }\n''',
)

# Helpers for routing to Wireless debugging and detecting a revoked identity.
replace(
    path,
    "    private fun Throwable.fullDescription(): String =\n",
    r'''    private fun openWirelessDebuggingSettings() {
        val direct = Intent(ACTION_WIRELESS_DEBUGGING_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(SETTINGS_FRAGMENT_ARGUMENT_KEY, WIRELESS_DEBUGGING_PREFERENCE_KEY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val intent = if (direct.resolveActivity(appContext.packageManager) != null) direct else fallback
        runCatching { appContext.startActivity(intent) }
            .onFailure { diagnostics.log("Could not open Wireless debugging settings: ${it.fullDescription()}") }
    }

    private fun isAuthorizationFailure(error: Throwable): Boolean =
        generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .any { message ->
                message.contains("auth", ignoreCase = true) ||
                    message.contains("unauthor", ignoreCase = true) ||
                    message.contains("certificate", ignoreCase = true)
            }

    private fun Throwable.fullDescription(): String =
''',
)

# Friendlier timeout text now covers both discovery and pairing.
replace(
    path,
    '''        error is kotlinx.coroutines.TimeoutCancellationException ->\n            text(\n                "Pairing timed out. Keep the pairing dialog open and try again.",\n                "Время сопряжения истекло. Не закрывайте окно с кодом и попробуйте снова.",\n            )\n''',
    '''        error is kotlinx.coroutines.TimeoutCancellationException ->\n            text(\n                "Wireless debugging timed out. Keep it enabled and try again.",\n                "Время ожидания Wireless debugging истекло. Оставьте его включённым и попробуйте снова.",\n            )\n''',
)

# Constants for saved-key reconnect and settings routing.
replace(
    path,
    "        private const val CONNECTION_AFTER_PAIR_DELAY_MS = 1_500L\n",
    '''        private const val CONNECTION_AFTER_PAIR_DELAY_MS = 1_500L\n        private const val SAVED_KEY_INITIAL_DISCOVERY_TIMEOUT_MS = 2_500L\n        private const val SAVED_KEY_RETRY_DISCOVERY_TIMEOUT_MS = 1_500L\n        private const val SAVED_KEY_WAIT_ATTEMPTS = 12\n        private const val SAVED_KEY_WAIT_DELAY_MS = 750L\n''',
)
replace(
    path,
    "        private const val MAX_LOG_OUTPUT_CHARS = 2_000\n",
    '''        private const val MAX_LOG_OUTPUT_CHARS = 2_000\n        private const val ACTION_WIRELESS_DEBUGGING_SETTINGS = "android.settings.WIRELESS_DEBUGGING_SETTINGS"\n        private const val SETTINGS_FRAGMENT_ARGUMENT_KEY = ":settings:fragment_args_key"\n        private const val WIRELESS_DEBUGGING_PREFERENCE_KEY = "toggle_adb_wireless"\n''',
)

# ---------------------------------------------------------------------------
# Mapper UI: new onboarding modes, global screen-off switch, wider settings, cleaner copy.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt"
replace(path, "import androidx.compose.ui.window.Dialog\n", "import androidx.compose.ui.window.Dialog\nimport androidx.compose.ui.window.DialogProperties\n")

# App root signature and local persisted mode state.
replace(
    path,
    "    preferences: UserPreferences,\n    openAccessibilitySettings: () -> Unit,\n",
    "    preferences: UserPreferences,\n    initiallyOpenSettings: Boolean,\n    openAccessibilitySettings: () -> Unit,\n",
)
replace(path, "    openDonate: () -> Unit,\n", "    openDonate: () -> Unit,\n    openSetupVideo: () -> Unit,\n", count=1)
replace(
    path,
    '''    var onboardingComplete by rememberSaveable {\n        mutableStateOf(preferences.onboardingComplete)\n    }\n    val language = AppLanguage.fromCode(languageCode)\n''',
    '''    var onboardingComplete by rememberSaveable {\n        mutableStateOf(preferences.onboardingComplete)\n    }\n    var screenOffEnabled by rememberSaveable { mutableStateOf(preferences.screenOffEnabled) }\n    val setScreenOffEnabled: (Boolean) -> Unit = { enabled ->\n        preferences.screenOffEnabled = enabled\n        screenOffEnabled = enabled\n    }\n    val language = AppLanguage.fromCode(languageCode)\n''',
)
replace(
    path,
    "            language = language,\n            state = state,\n            openAccessibilitySettings = openAccessibilitySettings,\n",
    "            language = language,\n            state = state,\n            screenOffEnabled = screenOffEnabled,\n            setScreenOffEnabled = setScreenOffEnabled,\n            openSetupVideo = openSetupVideo,\n            openAccessibilitySettings = openAccessibilitySettings,\n",
    count=1,
)
replace(
    path,
    "        language = language,\n        state = state,\n        snackbar = snackbar,\n",
    "        language = language,\n        state = state,\n        snackbar = snackbar,\n        screenOffEnabled = screenOffEnabled,\n        setScreenOffEnabled = setScreenOffEnabled,\n        initiallyOpenSettings = initiallyOpenSettings,\n",
    count=1,
)

# Replace onboarding functions through ReadyStep. StepHeading remains shared.
regex_replace(
    path,
    r'''@Composable\nprivate fun OnboardingScreen\(.*?\n@Composable\nprivate fun StepHeading''',
    r'''@Composable
private fun OnboardingScreen(
    language: AppLanguage,
    state: MapperUiState,
    screenOffEnabled: Boolean,
    setScreenOffEnabled: (Boolean) -> Unit,
    openSetupVideo: () -> Unit,
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
    val pageCount = if (screenOffEnabled) 5 else 4
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
                page == 0 -> ModeChoiceStep(language, screenOffEnabled, setScreenOffEnabled, openSetupVideo)
                page == 1 -> BaseSetupStep(
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
                page == 2 -> AccessibilityStep(language, state, openAccessibilitySettings)
                screenOffEnabled && page == 3 -> SleepSetupStep(
                    language,
                    state,
                    pairingCode,
                    { pairingCode = it.filter(Char::isDigit).take(6) },
                    beginPackageSetup,
                    submitPairingCode,
                    cancelPackageSetup,
                    copyText,
                    copyDiagnostics,
                    clearDiagnostics,
                )
                else -> ReadyStep(language, screenOffEnabled)
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
                        page == 1 -> keyReleased
                        page == 2 -> serviceReady
                        screenOffEnabled && page == 3 -> screenOffReady
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

@Composable
private fun ModeChoiceStep(
    language: AppLanguage,
    screenOffEnabled: Boolean,
    setScreenOffEnabled: (Boolean) -> Unit,
    openSetupVideo: () -> Unit,
) {
    StepHeading(
        "00",
        language.t("Choose setup mode", "Выберите режим работы"),
        language.t(
            "You can change this later in Settings.",
            "Это можно изменить позже в настройках.",
        ),
    )
    Spacer(Modifier.height(22.dp))
    ModeOption(
        language = language,
        selected = !screenOffEnabled,
        title = language.t("Screen on", "Только включённый экран"),
        detail = language.t(
            "Simpler setup. Nothing needs to be reactivated after a phone reboot.",
            "Проще настройка. После перезагрузки телефона ничего повторно активировать не нужно.",
        ),
        onClick = { setScreenOffEnabled(false) },
    )
    Spacer(Modifier.height(10.dp))
    ModeOption(
        language = language,
        selected = screenOffEnabled,
        title = language.t("Screen on + off", "Включённый + выключенный экран"),
        detail = language.t(
            "Adds the sleep monitor. It must be restarted through Wireless debugging after every phone reboot.",
            "Добавляет монитор сна. После каждой перезагрузки телефона его нужно перезапустить через Wireless debugging.",
        ),
        onClick = { setScreenOffEnabled(true) },
    )
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = openSetupVideo, modifier = Modifier.fillMaxWidth()) {
        Text(language.t("VIDEO SETUP GUIDE", "ВИДЕО ИНСТРУКЦИЯ ПО НАСТРОЙКЕ"), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ModeOption(
    language: AppLanguage,
    selected: Boolean,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BaseSetupStep(
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
        language.t("Release Essential Key", "Освободите Essential Key"),
        language.t(
            "Essential Remap disables the two Nothing components that currently own the button. Their data is kept and the change can be restored later.",
            "Essential Remap отключит два компонента Nothing, которые сейчас забирают кнопку. Их данные сохранятся, а изменение можно будет откатить.",
        ),
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
        Button(
            onClick = { beginPackageSetup(PackageOperation.DISABLE) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) { Text(language.t("RELEASE KEY", "ОСВОБОДИТЬ КНОПКУ")) }
        TextButton(onClick = openDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
            Text(language.t("Open developer options", "Открыть настройки разработчика"))
        }
    }
    ManualCommands(language, copyText, includeSleepMonitor = false)
}

@Composable
private fun AccessibilityStep(
    language: AppLanguage,
    state: MapperUiState,
    openAccessibilitySettings: () -> Unit,
) {
    StepHeading(
        "02",
        language.t("Enable Essential Remap", "Включите Essential Remap"),
        language.t(
            "Accessibility lets the app receive the Essential Key while Android is in use. Essential Remap does not read screen content.",
            "Специальные возможности позволяют приложению получать нажатия Essential Key во время работы Android. Essential Remap не читает содержимое экрана.",
        ),
    )
    Spacer(Modifier.height(22.dp))
    StatusCard(
        success = state.serviceEnabled,
        title = if (state.serviceEnabled) language.t("Service enabled", "Служба включена")
        else language.t("Service disabled", "Служба выключена"),
        detail = language.t("Required for remapping", "Нужно для переназначения кнопки"),
    )
    if (state.competingServices.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        WarningCard(
            language.t(
                "Another key remapper is active: ${state.competingServices.joinToString()}. Disable its Essential Key rule to avoid duplicate actions.",
                "Активен другой переназначатель кнопок: ${state.competingServices.joinToString()}. Отключите в нём правило Essential Key, чтобы действия не дублировались.",
            ),
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = openAccessibilitySettings,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) { Text(language.t("OPEN ACCESSIBILITY", "ОТКРЫТЬ СПЕЦИАЛЬНЫЕ ВОЗМОЖНОСТИ"), textAlign = TextAlign.Center) }
}

@Composable
private fun SleepSetupStep(
    language: AppLanguage,
    state: MapperUiState,
    pairingCode: String,
    changePairingCode: (String) -> Unit,
    beginPackageSetup: (PackageOperation) -> Unit,
    submitPairingCode: (String) -> Unit,
    cancelPackageSetup: () -> Unit,
    copyText: (String) -> Unit,
    copyDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit,
) {
    StepHeading(
        "03",
        language.t("Enable screen-off handling", "Включите работу с выключенным экраном"),
        language.t(
            "The sleep monitor runs with Android's shell privileges and listens only for Essential Key. It does not hold a wake lock. After a phone reboot it must be restarted.",
            "Монитор сна запускается с правами Android shell и слушает только Essential Key. Он не удерживает процессор активным. После перезагрузки телефона его нужно перезапустить.",
        ),
    )
    Spacer(Modifier.height(22.dp))
    StatusCard(
        success = state.setup.screenOffAccessGranted,
        title = if (state.setup.screenOffAccessGranted) {
            language.t("Sleep monitor is running", "Монитор сна работает")
        } else {
            language.t("Sleep monitor is not running", "Монитор сна не запущен")
        },
        detail = language.t("Required only with the display off", "Нужен только при выключенном экране"),
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
    ManualCommands(language, copyText, includeSleepMonitor = true)
}

@Composable
private fun ReadyStep(language: AppLanguage, screenOffEnabled: Boolean) {
    StepHeading(
        if (screenOffEnabled) "04" else "03",
        language.t("Ready", "Готово"),
        if (screenOffEnabled) {
            language.t(
                "Essential Key is ready with the display on and off. After a phone reboot, restart the sleep monitor from Settings.",
                "Essential Key готова к работе с включённым и выключенным экраном. После перезагрузки телефона перезапустите монитор сна в настройках.",
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

@Composable
private fun StepHeading''',
)

# Setup error copy and compact log buttons.
replace(
    path,
    '''                        "Copy the diagnostic log after reproducing the error.",\n                        "После появления ошибки скопируйте журнал диагностики.",\n''',
    '''                        "Copy the log if the problem repeats.",\n                        "Если ошибка повторяется, скопируйте лог.",\n''',
)
regex_replace(
    path,
    r'''@Composable\nprivate fun DiagnosticsActions\(.*?\n\}\n\n@Composable\nprivate fun ManualCommands''',
    r'''@Composable
private fun DiagnosticsActions(
    language: AppLanguage,
    copyDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit,
) {
    var cleared by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                cleared = false
                copyDiagnostics()
            },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        ) { Text(language.t("COPY LOG", "КОПИРОВАТЬ ЛОГ"), textAlign = TextAlign.Center) }
        OutlinedButton(
            onClick = {
                clearDiagnostics()
                cleared = true
            },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        ) { Text(language.t("CLEAR LOG", "ОЧИСТИТЬ ЛОГ"), textAlign = TextAlign.Center) }
    }
    if (cleared) {
        Text(
            language.t("Log cleared", "Лог очищен"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ManualCommands''',
)
replace(
    path,
    '''private fun ManualCommands(language: AppLanguage, copyText: (String) -> Unit) {\n    val commands = remember { ShellKeyMonitorCommands.manualAdbCommands() }\n''',
    '''private fun ManualCommands(\n    language: AppLanguage,\n    copyText: (String) -> Unit,\n    includeSleepMonitor: Boolean,\n) {\n    val commands = remember(includeSleepMonitor) { ShellKeyMonitorCommands.manualAdbCommands(includeSleepMonitor) }\n''',
)

# Home screen receives global mode and can deep-link directly to the wider settings dialog.
replace(
    path,
    "    state: MapperUiState,\n    snackbar: SnackbarHostState,\n",
    "    state: MapperUiState,\n    snackbar: SnackbarHostState,\n    screenOffEnabled: Boolean,\n    setScreenOffEnabled: (Boolean) -> Unit,\n    initiallyOpenSettings: Boolean,\n",
    count=1,
)
replace(
    path,
    "    var settingsOpen by rememberSaveable { mutableStateOf(false) }\n",
    "    var settingsOpen by rememberSaveable { mutableStateOf(initiallyOpenSettings) }\n",
)
replace(
    path,
    "                    runWhileLocked = state.draftRunWhileLocked[gesture] == true,\n                    onClick = { actionGesture = gesture },\n",
    "                    runWhileLocked = state.draftRunWhileLocked[gesture] == true,\n                    screenOffEnabled = screenOffEnabled,\n                    onClick = { actionGesture = gesture },\n",
)
replace(
    path,
    "            clearDiagnostics,\n            changeLanguage,\n",
    "            clearDiagnostics,\n            screenOffEnabled,\n            setScreenOffEnabled,\n            changeLanguage,\n",
    count=1,
)
replace(
    path,
    "    runWhileLocked: Boolean,\n    onClick: () -> Unit,\n",
    "    runWhileLocked: Boolean,\n    screenOffEnabled: Boolean,\n    onClick: () -> Unit,\n",
)
replace(
    path,
    '''                    Text(\n                        language.t("Includes screen off", "Включая погашенный экран"),\n                        color = MaterialTheme.colorScheme.onSurfaceVariant,\n                        style = MaterialTheme.typography.bodySmall,\n                    )\n''',
    '''                    Text(\n                        if (screenOffEnabled) {\n                            language.t("Lock screen and display off", "Блокировка и выключенный экран")\n                        } else {\n                            language.t("While the lock screen is visible", "Пока экран блокировки виден")\n                        },\n                        color = MaterialTheme.colorScheme.onSurfaceVariant,\n                        style = MaterialTheme.typography.bodySmall,\n                    )\n''',
)

# Replace SettingsDialog wholesale.
regex_replace(
    path,
    r'''@Composable\nprivate fun SettingsDialog\(.*?\n\}\n\n@Composable\nprivate fun LanguageChoice''',
    r'''@Composable
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
    screenOffEnabled: Boolean,
    setScreenOffEnabled: (Boolean) -> Unit,
    changeLanguage: (AppLanguage) -> Unit,
    runSetupAgain: () -> Unit,
    setRemappingEnabled: (Boolean) -> Unit,
) {
    var pairingCode by rememberSaveable { mutableStateOf("") }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f).widthIn(max = 680.dp),
        ) {
            Column {
                DialogHeader(language.t("Settings", "Настройки"), dismiss)
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionLabel(language.t("KEY STATUS", "СОСТОЯНИЕ КНОПКИ"))
                    StatusCard(
                        state.setup.packageStatus == NothingPackageStatus.DISABLED,
                        packageStatusTitle(language, state.setup.packageStatus),
                        language.t("Essential Space and Essential Recorder", "Essential Space и Essential Recorder"),
                    )
                    if (screenOffEnabled) {
                        StatusCard(
                            state.setup.screenOffAccessGranted,
                            if (state.setup.screenOffAccessGranted) {
                                language.t("Sleep monitor is running", "Монитор сна работает")
                            } else {
                                language.t("Sleep monitor needs restart", "Монитор сна нужно перезапустить")
                            },
                            language.t(
                                "Required with the display off; restart after every phone reboot",
                                "Нужен при выключенном экране; после перезагрузки телефона требуется перезапуск",
                            ),
                        )
                    }
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
                    if (!state.setup.busy) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.setup.packageStatus == NothingPackageStatus.DISABLED) {
                                OutlinedButton(
                                    onClick = { beginPackageSetup(PackageOperation.RESTORE) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                ) { Text(language.t("RESTORE", "ОТКАТИТЬ"), maxLines = 1, textAlign = TextAlign.Center) }
                                if (screenOffEnabled) {
                                    Button(
                                        onClick = { beginPackageSetup(PackageOperation.INSTALL_SLEEP_MONITOR) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                    ) {
                                        Text(
                                            if (state.setup.screenOffAccessGranted) {
                                                language.t("RESTART", "ПЕРЕЗАПУСК")
                                            } else {
                                                language.t("START", "ЗАПУСТИТЬ")
                                            },
                                            maxLines = 1,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { beginPackageSetup(PackageOperation.DISABLE) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(language.t("RELEASE KEY", "ОСВОБОДИТЬ КНОПКУ"), textAlign = TextAlign.Center) }
                            }
                        }
                    }
                    ManualCommands(language, copyText, includeSleepMonitor = screenOffEnabled)
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
                    SettingsSwitchRow(
                        title = language.t("Work with screen off", "Работать на выкл. экране"),
                        subtitle = if (screenOffEnabled) {
                            language.t(
                                "Uses the sleep monitor; restart it after a phone reboot",
                                "Использует монитор сна; после перезагрузки телефона нужен перезапуск",
                            )
                        } else {
                            language.t("Only while the display is on", "Только при включённом экране")
                        },
                        checked = screenOffEnabled,
                        onCheckedChange = setScreenOffEnabled,
                    )
                    HorizontalDivider()
                    SectionLabel(language.t("PERMISSIONS", "РАЗРЕШЕНИЯ"))
                    SettingsRow(
                        language.t("Accessibility", "Специальные возможности"),
                        if (state.serviceEnabled) language.t("Enabled", "Включены") else language.t("Disabled", "Выключены"),
                        openAccessibilitySettings,
                    )
                    SettingsRow(language.t("Default assistant", "Помощник по умолчанию"), "Google / Gemini", openAssistantSettings)
                    SettingsRow(
                        language.t("Do Not Disturb access", "Доступ к «Не беспокоить»"),
                        language.t("Only needed for Silent mode", "Нужен только для беззвучного режима"),
                        openNotificationPolicySettings,
                    )
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
                    DangerWarningCard(
                        language.t(
                            "Restore Essential Space before uninstalling Essential Remap. Uninstalling the APK alone does not re-enable Nothing's components.",
                            "Перед удалением Essential Remap нажмите «Откатить», чтобы вернуть Essential Space. Простое удаление APK не включит компоненты Nothing обратно.",
                        ),
                    )
                    SettingsRow("Donate", "github.com/AbdulKus/donate", openDonate)
                    DiagnosticsActions(language, copyDiagnostics, clearDiagnostics)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun LanguageChoice''',
)

# Add explicit red danger card and make status text use remaining width.
replace(
    path,
    "            Column(Modifier.padding(start = 12.dp)) {\n                Text(title, fontWeight = FontWeight.SemiBold)\n",
    "            Column(Modifier.padding(start = 12.dp).weight(1f)) {\n                Text(title, fontWeight = FontWeight.SemiBold)\n",
)
replace(
    path,
    "@Composable\nprivate fun SettingsRow(title: String, subtitle: String?, click: () -> Unit) {\n",
    r'''@Composable
private fun DangerWarningCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Text(text, modifier = Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, click: () -> Unit) {
''',
)

# Any old 2-argument ManualCommands calls are a bug after this migration.
if re.search(r"ManualCommands\(language, copyText\)", read(path)):
    raise SystemExit("MapperScreen.kt: old ManualCommands call remains")

# ---------------------------------------------------------------------------
# Version bump. Workflow APP_VERSION is updated separately by the connector.
# ---------------------------------------------------------------------------
replace(
    "app/build.gradle.kts",
    '        versionCode = 21\n        versionName = "0.1.20"\n',
    '        versionCode = 22\n        versionName = "0.1.21"\n',
)

# ---------------------------------------------------------------------------
# Safety guards.
# ---------------------------------------------------------------------------
assert "const val REVISION = 8" in read("app/src/main/java/com/abdulkus/essentialremap/setup/ShellKeyMonitorCommands.kt")
assert "INSTALL_SLEEP_MONITOR" in read("app/src/main/java/com/abdulkus/essentialremap/setup/NothingPackageCommands.kt")
assert "screen_off_enabled" in read("app/src/main/java/com/abdulkus/essentialremap/ui/UserPreferences.kt")
assert "BOOT_COMPLETED" in read("app/src/main/AndroidManifest.xml")
assert "VIDEO SETUP GUIDE" in read("app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt")
assert "Работать на выкл. экране" in read("app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt")
assert "DIAGNOSTICS\", \"ДИАГНОСТИКА" not in read("app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt")
print("setup/ui polish migration applied")
