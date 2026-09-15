from pathlib import Path
import re


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, got {count}: {old[:90]!r}")
    p.write_text(text.replace(old, new, 1))


def sub_once(path, pattern, replacement):
    p = Path(path)
    text = p.read_text()
    new, count = re.subn(pattern, lambda _: replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one regex match in {path}, got {count}: {pattern[:90]!r}")
    p.write_text(new)


# Persist access mode; existing installs remain NON_ROOT.
prefs = "app/src/main/java/com/abdulkus/essentialremap/ui/UserPreferences.kt"
replace_once(
    prefs,
    "import com.abdulkus.essentialremap.ScreenOffKeyAccess\n",
    "import com.abdulkus.essentialremap.ScreenOffKeyAccess\nimport com.abdulkus.essentialremap.setup.SetupAccessMode\n",
)
replace_once(
    prefs,
    '''    var onboardingComplete: Boolean
        get() = preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()
        }

    var screenOffEnabled: Boolean''',
    '''    var onboardingComplete: Boolean
        get() = preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()
        }

    var setupAccessMode: SetupAccessMode
        get() = SetupAccessMode.fromStored(preferences.getString(KEY_SETUP_ACCESS_MODE, null))
        set(value) {
            preferences.edit().putString(KEY_SETUP_ACCESS_MODE, value.name).apply()
        }

    var screenOffEnabled: Boolean''',
)
replace_once(
    prefs,
    '        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"\n',
    '        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"\n        const val KEY_SETUP_ACCESS_MODE = "setup_access_mode"\n',
)

# Main activity chooses ADB vs su.
main = "app/src/main/java/com/abdulkus/essentialremap/MainActivity.kt"
replace_once(
    main,
    "import com.abdulkus.essentialremap.setup.PackageOperation\n",
    "import com.abdulkus.essentialremap.setup.PackageOperation\nimport com.abdulkus.essentialremap.setup.SetupAccessMode\n",
)
replace_once(main, "            startWirelessSetup(operation)\n", "            startPackageSetup(operation)\n")
sub_once(
    main,
    r'''    private fun beginPackageSetup\(operation: PackageOperation\) \{.*?\n    private fun openWirelessDebuggingSetup\(\) \{''',
    '''    private fun beginPackageSetup(operation: PackageOperation) {
        val accessMode = userPreferences.setupAccessMode
        if (accessMode == SetupAccessMode.NON_ROOT &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPackageOperation = operation
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startPackageSetup(operation)
    }

    private fun startPackageSetup(operation: PackageOperation) {
        viewModel.startPackageSetup(operation, userPreferences.setupAccessMode)
    }

    private fun openWirelessDebuggingSetup() {''',
)

# View model carries the mode into setup and diagnostics.
vm = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperViewModel.kt"
replace_once(
    vm,
    "import com.abdulkus.essentialremap.setup.PackageOperation\n",
    "import com.abdulkus.essentialremap.setup.PackageOperation\nimport com.abdulkus.essentialremap.setup.SetupAccessMode\n",
)
replace_once(
    vm,
    '''    fun startPackageSetup(operation: PackageOperation) {
        setupCoordinator.start(operation)
    }''',
    '''    fun startPackageSetup(operation: PackageOperation, accessMode: SetupAccessMode) {
        setupCoordinator.start(operation, accessMode)
    }''',
)
replace_once(
    vm,
    '                "operation=${_uiState.value.setup.operation} " +\n',
    '                "operation=${_uiState.value.setup.operation} " +\n                "accessMode=${_uiState.value.setup.accessMode} " +\n',
)

# One app_process helper supports Android shell and root. Revision bump replaces old helpers.
monitor = "app/src/main/java/com/abdulkus/essentialremap/monitor/ShellMonitorMain.java"
replace_once(
    monitor,
    "/** app_process entry point, running as shell. No Context, external network, alarms, or idle wake lock. */",
    "/** app_process entry point, running as shell or root. No Context, external network, alarms, or idle wake lock. */",
)
replace_once(monitor, "public static final int STATE_REVISION = 11;", "public static final int STATE_REVISION = 12;")
replace_once(
    monitor,
    '''        if (android.os.Process.myUid() != 2000 || args.length != 2) {
            throw new IllegalArgumentException("Expected shell UID, application UID and monitor directory");
        }''',
    '''        int monitorUid = android.os.Process.myUid();
        if ((monitorUid != 2000 && monitorUid != 0) || args.length != 2) {
            throw new IllegalArgumentException("Expected shell/root UID, application UID and monitor directory");
        }''',
)
replace_once(
    monitor,
    '''                writeFile("key-monitor.state", "revision=" + STATE_REVISION + " pid=" +
                    android.os.Process.myPid() + " input=" + device + " session=" + session);''',
    '''                writeFile("key-monitor.state", "revision=" + STATE_REVISION + " pid=" +
                    android.os.Process.myPid() + " uid=" + android.os.Process.myUid() +
                    " input=" + device + " session=" + session);''',
)
replace_once(
    "app/src/main/java/com/abdulkus/essentialremap/setup/ShellKeyMonitorCommands.kt",
    "const val REVISION = 11",
    "const val REVISION = 12",
)

# Root fallback broadcasts remain explicit, DUMP-protected and transport-secret authenticated.
receiver = "app/src/main/java/com/abdulkus/essentialremap/ShellKeyEventReceiver.kt"
replace_once(
    receiver,
    "/** Receives only explicit, DUMP-protected events emitted by the ADB shell monitor. */",
    "/** Receives only explicit, DUMP-protected events emitted by the privileged monitor. */",
)
replace_once(
    receiver,
    '''    fun isAllowed(uid: Int?): Boolean =
        uid == null || uid == UNAVAILABLE_UID || uid == Process.SHELL_UID''',
    '''    fun isAllowed(uid: Int?): Boolean =
        uid == null || uid == UNAVAILABLE_UID || uid == Process.SHELL_UID || uid == 0''',
)
test = "app/src/test/java/com/abdulkus/essentialremap/ShellKeyEventSenderPolicyTest.kt"
replace_once(
    test,
    '''    fun shellAndPreAndroid14UnavailableSenderAreAccepted() {
        assertTrue(ShellKeyEventSenderPolicy.isAllowed(Process.SHELL_UID))
        assertTrue(ShellKeyEventSenderPolicy.isAllowed(null))
    }''',
    '''    fun shellRootAndPreAndroid14UnavailableSenderAreAccepted() {
        assertTrue(ShellKeyEventSenderPolicy.isAllowed(Process.SHELL_UID))
        assertTrue(ShellKeyEventSenderPolicy.isAllowed(0))
        assertTrue(ShellKeyEventSenderPolicy.isAllowed(null))
    }''',
)

# Coordinator splits only the privileged execution path; operations stay identical.
c = "app/src/main/java/com/abdulkus/essentialremap/setup/EssentialKeySetupCoordinator.kt"
replace_once(c, '''enum class SetupPhase {
    IDLE,
    DISCOVERING,''', '''enum class SetupPhase {
    IDLE,
    REQUESTING_ROOT,
    DISCOVERING,''')
replace_once(
    c,
    '''data class EssentialKeySetupState(
    val packageStatus: NothingPackageStatus = NothingPackageStatus.UNKNOWN,
    val screenOffAccessGranted: Boolean = false,
    val phase: SetupPhase = SetupPhase.IDLE,''',
    '''data class EssentialKeySetupState(
    val packageStatus: NothingPackageStatus = NothingPackageStatus.UNKNOWN,
    val screenOffAccessGranted: Boolean = false,
    val accessMode: SetupAccessMode = SetupAccessMode.NON_ROOT,
    val phase: SetupPhase = SetupPhase.IDLE,''',
)
replace_once(
    c,
    '''    val busy: Boolean get() = phase in setOf(
        SetupPhase.DISCOVERING,''',
    '''    val busy: Boolean get() = phase in setOf(
        SetupPhase.REQUESTING_ROOT,
        SetupPhase.DISCOVERING,''',
)
replace_once(c, "    fun start(operation: PackageOperation)\n", "    fun start(operation: PackageOperation, accessMode: SetupAccessMode)\n")
sub_once(
    c,
    r'''    override fun start\(operation: PackageOperation\) \{.*?\n    override fun submitPairingCode\(code: String\) \{''',
    '''    override fun start(operation: PackageOperation, accessMode: SetupAccessMode) {
        setupJob?.cancel()
        pairingCode = CompletableDeferred()
        _state.value = EssentialKeySetupState(
            packageStatus = statusReader.read(),
            screenOffAccessGranted = ScreenOffKeyAccess.isGranted(appContext),
            accessMode = accessMode,
            phase = if (accessMode == SetupAccessMode.ROOT) SetupPhase.REQUESTING_ROOT else SetupPhase.DISCOVERING,
            operation = operation,
            message = if (accessMode == SetupAccessMode.ROOT) {
                text("Requesting root access…", "Запрашиваем root-доступ…")
            } else {
                text("Connecting to Wireless debugging…", "Подключаемся к Wireless debugging…")
            },
        )
        diagnostics.log("--- Setup started: operation=$operation accessMode=$accessMode packageStatus=${_state.value.packageStatus} ---")
        setupJob = scope.launch {
            runCatching {
                if (accessMode == SetupAccessMode.ROOT) {
                    applyRootOperation(operation)
                } else {
                    val existingManager = connectUsingStoredIdentity()
                    if (existingManager != null) {
                        diagnostics.log("Using previously paired ADB identity")
                        applyConnectedOperation(existingManager, operation)
                    } else {
                        pairThenApply(operation)
                    }
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

    override fun submitPairingCode(code: String) {''',
)
replace_once(
    c,
    '''        _state.value = EssentialKeySetupState(
            packageStatus = statusReader.read(),
            screenOffAccessGranted = ScreenOffKeyAccess.isGranted(appContext),
        )''',
    '''        _state.value = EssentialKeySetupState(
            packageStatus = statusReader.read(),
            screenOffAccessGranted = ScreenOffKeyAccess.isGranted(appContext),
            accessMode = _state.value.accessMode,
        )''',
)
replace_once(
    c,
    '''            _state.value = EssentialKeySetupState(
                packageStatus = packageStatus,
                screenOffAccessGranted = screenOffAccessGranted,
                phase = SetupPhase.COMPLETE,''',
    '''            _state.value = EssentialKeySetupState(
                packageStatus = packageStatus,
                screenOffAccessGranted = screenOffAccessGranted,
                accessMode = SetupAccessMode.NON_ROOT,
                phase = SetupPhase.COMPLETE,''',
)

root_methods = '''    private suspend fun applyRootOperation(operation: PackageOperation) {
        _state.value = _state.value.copy(
            phase = SetupPhase.REQUESTING_ROOT,
            message = text(
                "Allow Essential Remap in your root manager to continue.",
                "Разрешите Essential Remap root-доступ в менеджере root.",
            ),
        )
        postProgressNotification()
        diagnostics.log("Root setup: requesting su access")
        RootCommandExecutor.requireRoot()
        diagnostics.log("Root setup: su access granted")

        _state.value = _state.value.copy(
            phase = SetupPhase.APPLYING,
            message = when (operation) {
                PackageOperation.DISABLE -> text("Releasing Essential Key with root", "Освобождаем Essential Key через root")
                PackageOperation.INSTALL_SLEEP_MONITOR -> text("Starting root sleep monitor", "Запускаем root-монитор сна")
                PackageOperation.RESTORE -> text("Restoring Essential Space with root", "Восстанавливаем Essential Space через root")
            },
        )
        postProgressNotification()

        EssentialKeySetupCommands.commands(operation).forEach { command ->
            diagnostics.log("Executing root setup command: $command")
            val output = executeRootSetupCommand(command)
            diagnostics.log("Root command output: ${output.take(MAX_LOG_OUTPUT_CHARS)}")
        }
        if (operation == PackageOperation.RESTORE) ScreenOffKeyAccess.markStopped(appContext)

        val packageStatus = verifyPackageStateRoot(operation)
        val screenOffAccessGranted = when (operation) {
            PackageOperation.INSTALL_SLEEP_MONITOR -> {
                verifyScreenOffAccessRoot()
                val bridge = (appContext as EssentialKeyApplication).container.shellBridge
                bridge.requestConnect()
                withTimeout(5_000) {
                    while (!ScreenOffKeyAccess.runtimeHealthy) delay(50)
                }
                ScreenOffKeyAccess.markStarted(appContext)
                SleepMonitorBootReceiver.cancelReminder(appContext)
                true
            }
            PackageOperation.DISABLE -> ScreenOffKeyAccess.isGranted(appContext)
            PackageOperation.RESTORE -> false
        }

        diagnostics.log("Root operation verified: status=$packageStatus screenOff=$screenOffAccessGranted")
        _state.value = EssentialKeySetupState(
            packageStatus = packageStatus,
            screenOffAccessGranted = screenOffAccessGranted,
            accessMode = SetupAccessMode.ROOT,
            phase = SetupPhase.COMPLETE,
            operation = operation,
            message = when (operation) {
                PackageOperation.DISABLE -> text(
                    "Essential Key released with root. ADB and Developer options are not required.",
                    "Essential Key освобождена через root. ADB и режим разработчика не требуются.",
                )
                PackageOperation.INSTALL_SLEEP_MONITOR -> text(
                    "Root sleep monitor started. ADB can stay off; it will start automatically after a phone reboot.",
                    "Root-монитор сна запущен. ADB можно оставить выключенным; после перезагрузки он запустится автоматически.",
                )
                PackageOperation.RESTORE -> text(
                    "Essential Space restored. Root is no longer needed for Essential Remap.",
                    "Essential Space восстановлен. Root больше не нужен Essential Remap.",
                )
            },
        )
        val successMessage = _state.value.message.orEmpty()
        ScreenOffKeyAccess.notifyChanged()
        postResultNotification(text("Setup complete", "Настройка завершена"), successMessage)
        returnToApp()
    }

    private fun executeRootSetupCommand(command: String): String {
        require(EssentialKeySetupCommands.isAllowlisted(command)) { "Command is not allowlisted" }
        val actualCommand = if (command == ShellKeyMonitorCommands.INSTALL) ShellKeyMonitorCommands.installAndStart else command
        val output = RootCommandExecutor.execute(
            actualCommand,
            if (command == ShellKeyMonitorCommands.INSTALL) ROOT_MONITOR_TIMEOUT_MS else ROOT_COMMAND_TIMEOUT_MS,
        )
        when (command) {
            EssentialKeySetupCommands.ENABLE_RELIABLE_SCREEN_OFF_DISPATCH ->
                check(output.contains(EssentialKeySetupCommands.COMMAND_OK)) { "Root could not configure screen-off dispatch" }
            ShellKeyMonitorCommands.INSTALL ->
                check(output.contains(ShellKeyMonitorCommands.START_CONFIRMATION)) { "Root sleep monitor did not confirm startup" }
            ShellKeyMonitorCommands.stop ->
                check(output.contains(ShellKeyMonitorCommands.STOP_OK)) { "Root sleep monitor did not confirm shutdown" }
        }
        return output
    }

    private fun verifyScreenOffAccessRoot() {
        val setting = RootCommandExecutor.execute(
            EssentialKeySetupCommands.READ_SCREEN_OFF_WAKE_SETTING,
            ROOT_COMMAND_TIMEOUT_MS,
        )
        check(setting.lineSequence().any { it.trim() == "1" }) {
            "Android did not block the OEM Essential Key screen-off wake path"
        }
        val monitor = RootCommandExecutor.execute(ShellKeyMonitorCommands.status, ROOT_COMMAND_TIMEOUT_MS)
        check(monitor.contains(ShellKeyMonitorCommands.RUNNING_CONFIRMATION)) {
            "Android did not keep the root key monitor running"
        }
        diagnostics.log("Screen-off access verified: root monitor running, nt_block_essential_key=1")
    }

    private fun verifyPackageStateRoot(operation: PackageOperation): NothingPackageStatus {
        val flag = if (operation == PackageOperation.RESTORE) "-e" else "-d"
        NothingPackageCommands.packages.forEach { packageName ->
            val output = RootCommandExecutor.execute("pm list packages $flag $packageName", ROOT_COMMAND_TIMEOUT_MS)
            check(output.lineSequence().any { it.trim() == "package:$packageName" }) {
                "Android could not verify package state for $packageName"
            }
        }
        return if (operation == PackageOperation.RESTORE) NothingPackageStatus.ENABLED else NothingPackageStatus.DISABLED
    }

'''
replace_once(
    c,
    "    private fun pairWithFallback(endpoint: AdbEndpoint, code: String) {\n",
    root_methods + "    private fun pairWithFallback(endpoint: AdbEndpoint, code: String) {\n",
)
replace_once(
    c,
    '''    private fun friendlyError(error: Throwable): String = when {
        error is kotlinx.coroutines.TimeoutCancellationException ->''',
    '''    private fun friendlyError(error: Throwable): String = when {
        _state.value.accessMode == SetupAccessMode.ROOT &&
            (error.message?.contains("root", ignoreCase = true) == true ||
                error.message?.contains("su", ignoreCase = true) == true) ->
            text(
                "Root access failed. Allow Essential Remap in Magisk, KernelSU, APatch or your root manager and try again.",
                "Не удалось получить root. Разрешите Essential Remap в Magisk, KernelSU, APatch или другом менеджере root и попробуйте снова.",
            )
        error is kotlinx.coroutines.TimeoutCancellationException ->''',
)
replace_once(
    c,
    '                text("Wireless debugging setup", "Настройка Wireless debugging"),\n',
    '                text("Essential Remap setup", "Настройка Essential Remap"),\n',
)
replace_once(
    c,
    '''                description = text(
                    "Accepts the local ADB pairing code during Essential Remap setup",
                    "Принимает локальный код сопряжения ADB при настройке Essential Remap",
                )''',
    '''                description = text(
                    "Shows setup progress and accepts a local ADB pairing code in non-root mode",
                    "Показывает ход настройки и принимает локальный код ADB в режиме без root",
                )''',
)
replace_once(
    c,
    "        private const val COMMAND_TIMEOUT_MS = 15_000L\n",
    "        private const val COMMAND_TIMEOUT_MS = 15_000L\n        private const val ROOT_COMMAND_TIMEOUT_MS = 10_000L\n        private const val ROOT_MONITOR_TIMEOUT_MS = 20_000L\n",
)

# Root boot auto-start; non-root retains the current restart reminder.
Path("app/src/main/java/com/abdulkus/essentialremap/setup/SleepMonitorBootReceiver.kt").write_text('''package com.abdulkus.essentialremap.setup

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
import com.abdulkus.essentialremap.EssentialKeyApplication
import com.abdulkus.essentialremap.MainActivity
import com.abdulkus.essentialremap.R
import com.abdulkus.essentialremap.ScreenOffKeyAccess
import com.abdulkus.essentialremap.ui.AppLanguage
import com.abdulkus.essentialremap.ui.UserPreferences
import com.abdulkus.essentialremap.ui.translate

class SleepMonitorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = UserPreferences(context)
        if (!preferences.screenOffEnabled) return

        ScreenOffKeyAccess.markStopped(context)
        if (preferences.setupAccessMode != SetupAccessMode.ROOT) {
            postReminder(context, preferences.language, rootMode = false)
            return
        }

        val pending = goAsync()
        Thread({
            val diagnostics = SetupDiagnostics(context)
            runCatching {
                diagnostics.log("Boot: starting sleep monitor through root")
                RootCommandExecutor.requireRoot(ROOT_BOOT_AUTH_TIMEOUT_MS)
                val setting = RootCommandExecutor.execute(
                    EssentialKeySetupCommands.ENABLE_RELIABLE_SCREEN_OFF_DISPATCH,
                    ROOT_BOOT_COMMAND_TIMEOUT_MS,
                )
                check(setting.contains(EssentialKeySetupCommands.COMMAND_OK)) { "Root boot setup failed" }
                val output = RootCommandExecutor.execute(
                    ShellKeyMonitorCommands.installAndStart,
                    ROOT_BOOT_MONITOR_TIMEOUT_MS,
                )
                check(output.contains(ShellKeyMonitorCommands.START_CONFIRMATION)) {
                    "Root boot monitor did not confirm startup"
                }
                ScreenOffKeyAccess.markStarted(context)
                cancelReminder(context)
                (context.applicationContext as? EssentialKeyApplication)?.container?.shellBridge?.requestConnect()
                diagnostics.log("Boot: root sleep monitor started")
            }.onFailure { error ->
                diagnostics.log("Boot: root auto-start failed: ${error.javaClass.simpleName}: ${error.message}")
                postReminder(context, preferences.language, rootMode = true)
            }
            pending.finish()
        }, "essential-root-boot").apply { isDaemon = true }.start()
    }

    companion object {
        private const val CHANNEL_ID = "essential_remap_sleep_monitor"
        private const val NOTIFICATION_ID = 2054
        private const val ROOT_BOOT_AUTH_TIMEOUT_MS = 4_000L
        private const val ROOT_BOOT_COMMAND_TIMEOUT_MS = 4_000L
        private const val ROOT_BOOT_MONITOR_TIMEOUT_MS = 8_000L

        fun cancelReminder(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        private fun postReminder(context: Context, language: AppLanguage?, rootMode: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return

            val selectedLanguage = language ?: AppLanguage.ENGLISH
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    selectedLanguage.translate("Essential Remap sleep monitor", "Монитор сна Essential Remap"),
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
            val title = if (rootMode) {
                selectedLanguage.translate("Root sleep monitor needs attention", "Root-монитор сна требует внимания")
            } else selectedLanguage.translate("Restart the sleep monitor", "Перезапустите монитор сна")
            val shortText = if (rootMode) {
                selectedLanguage.translate("Automatic root start failed after reboot.", "Автозапуск через root после перезагрузки не сработал.")
            } else selectedLanguage.translate(
                "Screen-off Essential Key handling must be reactivated after a phone reboot.",
                "Для работы Essential Key с выключенным экраном требуется повторная активация после перезагрузки.",
            )
            val detail = if (rootMode) {
                selectedLanguage.translate(
                    "Open Essential Remap and tap Restart. ADB is not required; make sure your root manager still allows Essential Remap.",
                    "Откройте Essential Remap и нажмите «Перезапуск». ADB не нужен; проверьте root-разрешение Essential Remap.",
                )
            } else selectedLanguage.translate(
                "Open Essential Remap, enable Wireless debugging, then tap Restart for the sleep monitor.",
                "Откройте Essential Remap, включите Wireless debugging и нажмите «Перезапуск» у монитора сна.",
            )
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(shortText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
''')

# The workflow will remove both patch helpers after applying.
Path(".github/scripts/apply_root_backend.py").unlink()
Path(".github/workflows/apply-root-mode.yml").unlink()
