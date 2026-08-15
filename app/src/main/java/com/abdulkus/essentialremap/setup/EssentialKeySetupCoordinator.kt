package com.abdulkus.essentialremap.setup

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.abdulkus.essentialremap.MainActivity
import com.abdulkus.essentialremap.R
import com.abdulkus.essentialremap.ScreenOffKeyAccess
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.android.AdbMdns
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class SetupPhase {
    IDLE,
    DISCOVERING,
    WAITING_FOR_WIRELESS_DEBUGGING,
    WAITING_FOR_CODE,
    PAIRING,
    CONNECTING,
    APPLYING,
    COMPLETE,
    ERROR,
}

data class EssentialKeySetupState(
    val packageStatus: NothingPackageStatus = NothingPackageStatus.UNKNOWN,
    val screenOffAccessGranted: Boolean = false,
    val phase: SetupPhase = SetupPhase.IDLE,
    val operation: PackageOperation? = null,
    val message: String? = null,
) {
    val busy: Boolean get() = phase in setOf(
        SetupPhase.DISCOVERING,
        SetupPhase.WAITING_FOR_WIRELESS_DEBUGGING,
        SetupPhase.WAITING_FOR_CODE,
        SetupPhase.PAIRING,
        SetupPhase.CONNECTING,
        SetupPhase.APPLYING,
    )
}

interface EssentialKeySetupController {
    val state: StateFlow<EssentialKeySetupState>
    fun refresh()
    fun start(operation: PackageOperation)
    fun submitPairingCode(code: String)
    fun cancel()
    fun diagnosticReport(): String
    fun clearDiagnostics()
}

class EssentialKeySetupCoordinator(
    context: Context,
    private val diagnostics: SetupDiagnostics = SetupDiagnostics(context),
) : EssentialKeySetupController {
    private val appContext = context.applicationContext
    private val statusReader = NothingPackageStatusReader(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val _state = MutableStateFlow(
        EssentialKeySetupState(
            packageStatus = statusReader.read(),
            screenOffAccessGranted = ScreenOffKeyAccess.isGranted(appContext),
        ),
    )
    override val state: StateFlow<EssentialKeySetupState> = _state.asStateFlow()

    private var setupJob: Job? = null
    private var pairingCode = CompletableDeferred<String>()

    init {
        createNotificationChannel()
        diagnostics.log("Coordinator created; packageStatus=${_state.value.packageStatus}")
    }

    override fun refresh() {
        _state.value = _state.value.copy(
            packageStatus = statusReader.read(),
            screenOffAccessGranted = ScreenOffKeyAccess.isGranted(appContext),
        )
    }

    override fun start(operation: PackageOperation) {
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

    override fun submitPairingCode(code: String) {
        val normalized = code.filter(Char::isDigit)
        if (normalized.length != 6) {
            _state.value = _state.value.copy(
                message = text("Pairing code must contain six digits", "Код должен содержать шесть цифр"),
            )
            postPairingNotification()
            return
        }
        if (!pairingCode.isCompleted) {
            diagnostics.log("Six-digit pairing code received from UI/notification")
            pairingCode.complete(normalized)
            _state.value = _state.value.copy(
                message = text("Pairing code received. Completing setup…", "Код получен. Завершаем настройку…"),
            )
            postProgressNotification()
        }
    }

    override fun cancel() {
        diagnostics.log("Setup cancelled by user")
        setupJob?.cancel()
        notificationManager.cancel(NOTIFICATION_ID)
        _state.value = EssentialKeySetupState(
            packageStatus = statusReader.read(),
            screenOffAccessGranted = ScreenOffKeyAccess.isGranted(appContext),
        )
    }

    override fun diagnosticReport(): String = diagnostics.report()

    override fun clearDiagnostics() = diagnostics.clear()

    private suspend fun discoverAdbEndpoint(serviceType: String, timeoutMs: Long): AdbEndpoint {
        diagnostics.log("mDNS discovery started: service=$serviceType timeoutMs=$timeoutMs")
        val discoveredEndpoint = CompletableDeferred<AdbEndpoint>()
        val discovery = AdbMdns(
            appContext,
            serviceType,
        ) { host, port ->
            if (host != null && port > 0 && !discoveredEndpoint.isCompleted) {
                diagnostics.log("mDNS resolved: service=$serviceType host=${host.hostAddress} port=$port")
                discoveredEndpoint.complete(AdbEndpoint(host.hostAddress.orEmpty(), port))
            }
        }
        discovery.start()
        return try {
            withTimeout(timeoutMs) { discoveredEndpoint.await() }
        } finally {
            discovery.stop()
            diagnostics.log("mDNS discovery stopped: service=$serviceType")
        }
    }

    private suspend fun pairThenApply(operation: PackageOperation) {
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
        diagnostics.log("Wireless debugging did not become connectable with stored key")
        throw IOException(
            "Wireless debugging is not available with the saved key. Turn it on and try again.",
        )
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

    private fun pairWithFallback(endpoint: AdbEndpoint, code: String) {
        var lastError: Throwable? = null
        val hosts = listOf(endpoint.host, LOOPBACK_HOST)
            .filter(String::isNotBlank)
            .distinct()
        hosts.forEach { host ->
            diagnostics.log("Pair attempt: host=$host port=${endpoint.port}")
            try {
                LocalAdbConnectionManager(appContext).pair(host, endpoint.port, code)
                diagnostics.log("Pair succeeded: host=$host port=${endpoint.port}")
                return
            } catch (error: Throwable) {
                lastError = error
                diagnostics.log("Pair failed: host=$host port=${endpoint.port} ${error.fullDescription()}")
            }
        }
        throw IOException("Pairing failed on all discovered hosts: ${lastError?.fullDescription().orEmpty()}", lastError)
    }

    private fun executeAllowlisted(
        manager: LocalAdbConnectionManager,
        command: String,
    ): String {
        require(EssentialKeySetupCommands.isAllowlisted(command)) {
            "Command is not allowlisted"
        }
        if (command == ShellKeyMonitorCommands.INSTALL) {
            return executeMonitorInstall(manager)
        }
        return readShellOutput(manager, command) { output ->
            when (command) {
                EssentialKeySetupCommands.ENABLE_RELIABLE_SCREEN_OFF_DISPATCH ->
                    output.contains(EssentialKeySetupCommands.COMMAND_OK)
                ShellKeyMonitorCommands.stop ->
                    output.contains(ShellKeyMonitorCommands.STOP_OK)
                else -> output.contains("new state:", ignoreCase = true)
            }
        }.trim()
    }

    private fun executeMonitorInstall(manager: LocalAdbConnectionManager): String {
        diagnostics.log("Opening raw ADB installer: service=${ShellKeyMonitorCommands.INSTALL_SERVICE}")
        val stream = manager.openStream(ShellKeyMonitorCommands.INSTALL_SERVICE)
        try {
            val payload = ShellKeyMonitorCommands.installSessionScript.toByteArray(StandardCharsets.UTF_8)
            diagnostics.log(
                "Sending monitor installer: bytes=${payload.size} chunkBytes=$SHELL_WRITE_CHUNK_BYTES " +
                    "expected=${ShellKeyMonitorCommands.START_CONFIRMATION}",
            )
            val output = stream.openOutputStream()
            var offset = 0
            while (offset < payload.size) {
                val count = minOf(SHELL_WRITE_CHUNK_BYTES, payload.size - offset)
                output.write(payload, offset, count)
                offset += count
            }
            output.flush()
            diagnostics.log("Monitor installer sent; waiting for exact revision confirmation")
            return collectShellOutput(
                stream = stream,
                description = "install shell key monitor",
            ) { it.contains(ShellKeyMonitorCommands.START_CONFIRMATION) }.trim()
        } finally {
            runCatching { stream.close() }
        }
    }

    private fun verifyScreenOffAccess(manager: LocalAdbConnectionManager): Boolean {
        val settingOutput = readShellOutput(
            manager,
            EssentialKeySetupCommands.READ_SCREEN_OFF_WAKE_SETTING,
        ) { it.lineSequence().any { line -> line.trim() == "1" } }
        if (settingOutput.lineSequence().none { it.trim() == "1" }) {
            error("Android did not block the OEM Essential Key screen-off wake path")
        }
        val monitorOutput = readShellOutput(
            manager,
            ShellKeyMonitorCommands.status,
        ) { it.contains(ShellKeyMonitorCommands.RUNNING_CONFIRMATION) }
        if (!monitorOutput.contains(ShellKeyMonitorCommands.RUNNING_CONFIRMATION)) {
            error("Android did not keep the shell key monitor running")
        }
        diagnostics.log("Screen-off access verified: shell monitor running, nt_block_essential_key=1")
        return true
    }

    private suspend fun connectWithRetry(
        attempts: Int = CONNECTION_ATTEMPTS,
        discoveryTimeoutMs: Long = CONNECTION_DISCOVERY_TIMEOUT_MS,
    ): LocalAdbConnectionManager {
        var lastError: Throwable? = null
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(CONNECTION_RETRY_DELAY_MS)
            diagnostics.log("Connect discovery attempt ${attempt + 1}/$attempts")
            val endpoint = try {
                discoverAdbEndpoint(
                    AdbMdns.SERVICE_TYPE_TLS_CONNECT,
                    discoveryTimeoutMs,
                )
            } catch (error: Throwable) {
                lastError = error
                diagnostics.log("Connect discovery failed: ${error.fullDescription()}")
                return@repeat
            }
            val hosts = listOf(endpoint.host, LOOPBACK_HOST)
                .filter(String::isNotBlank)
                .distinct()
            for (host in hosts) {
                val manager = LocalAdbConnectionManager(appContext)
                diagnostics.log("Connect attempt: host=$host port=${endpoint.port}")
                try {
                    if (manager.connect(host, endpoint.port)) {
                        diagnostics.log("Connect succeeded: host=$host port=${endpoint.port}")
                        return manager
                    }
                    diagnostics.log("Connect returned false: host=$host port=${endpoint.port}")
                } catch (error: Throwable) {
                    lastError = connectionErrorWithCause(manager, host, endpoint.port, error)
                    diagnostics.log("Connect failed: ${lastError?.fullDescription()}")
                    runCatching { manager.disconnect() }
                }
            }
        }
        throw IOException(
            "Could not connect to Android Wireless debugging. " +
                "Keep Wireless debugging enabled and try again. ${lastError?.message.orEmpty()}",
            lastError,
        )
    }

    private fun connectionErrorWithCause(
        manager: LocalAdbConnectionManager,
        host: String,
        port: Int,
        error: Throwable,
    ): Throwable {
        val internalCause = runCatching {
            val connection = manager.adbConnection ?: return@runCatching null
            connection.javaClass.getDeclaredField("mConnectionException").run {
                isAccessible = true
                get(connection) as? Throwable
            }
        }.getOrNull()
        val details = generateSequence(internalCause ?: error) { it.cause }
            .mapNotNull { cause ->
                cause.message?.takeIf(String::isNotBlank)?.let {
                    "${cause.javaClass.simpleName}: $it"
                }
            }
            .distinct()
            .joinToString(" → ")
        return IOException(
            "$host:$port — ${details.ifBlank { error.javaClass.simpleName }}",
            internalCause ?: error,
        )
    }

    private fun openWirelessDebuggingSettings() {
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
        generateSequence(this) { it.cause }
            .map { cause ->
                val message = cause.message?.takeIf(String::isNotBlank)
                if (message == null) cause.javaClass.name else "${cause.javaClass.name}: $message"
            }
            .distinct()
            .joinToString(" → ")

    private fun verifyPackageState(
        manager: LocalAdbConnectionManager,
        operation: PackageOperation,
    ): NothingPackageStatus {
        val flag = if (operation == PackageOperation.RESTORE) "-e" else "-d"
        val verified = NothingPackageCommands.packages.all { packageName ->
            val command = "pm list packages $flag $packageName"
            val expected = "package:$packageName"
            diagnostics.log("Verifying package: command=$command")
            val output = readShellOutput(manager, command) { it.contains(expected) }
            diagnostics.log("Verification output: ${output.take(MAX_LOG_OUTPUT_CHARS)}")
            output.contains(expected)
        }
        if (!verified) error("Android could not verify both Nothing packages")
        return if (operation == PackageOperation.RESTORE) {
            NothingPackageStatus.ENABLED
        } else {
            NothingPackageStatus.DISABLED
        }
    }

    private fun readShellOutput(
        manager: LocalAdbConnectionManager,
        command: String,
        complete: (String) -> Boolean,
    ): String {
        val stream = manager.openStream("shell:$command")
        return try {
            collectShellOutput(stream, command, complete)
        } finally {
            runCatching { stream.close() }
        }
    }

    private fun collectShellOutput(
        stream: AdbStream,
        description: String,
        complete: (String) -> Boolean,
    ): String {
        val output = StringBuilder()
        val deadline = SystemClock.elapsedRealtime() + COMMAND_TIMEOUT_MS
        val input = stream.openInputStream()
        val buffer = ByteArray(2048)
        while (SystemClock.elapsedRealtime() < deadline) {
            val available = try {
                input.available()
            } catch (error: IOException) {
                if (stream.isClosed) break else throw error
            }
            if (available > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, available))
                if (count > 0) {
                    output.append(String(buffer, 0, count, StandardCharsets.UTF_8))
                    if (complete(output.toString())) return output.toString()
                }
            } else if (stream.isClosed) {
                break
            } else {
                Thread.sleep(SHELL_POLL_INTERVAL_MS)
            }
        }
        if (!complete(output.toString())) {
            diagnostics.log(
                "Shell step did not confirm success: step=$description " +
                    "output=${output.toString().takeLast(MAX_LOG_OUTPUT_CHARS)}",
            )
            val details = output.toString().trim().takeLast(MAX_LOG_OUTPUT_CHARS)
                .ifBlank { "no command output" }
            error("ADB step failed: $description. $details")
        }
        return output.toString()
    }

    @SuppressLint("MissingPermission")
    private fun postProgressNotification() {
        if (!canPostNotifications()) return
        notificationManager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(text("Essential Remap setup", "Настройка Essential Remap"))
                .setContentText(_state.value.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(_state.value.message))
                .setOngoing(true)
                .setProgress(0, 0, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun postPairingNotification() {
        if (!canPostNotifications()) return
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel(text("Six-digit pairing code", "Шестизначный код сопряжения"))
            .build()
        val replyIntent = Intent(appContext, PairingCodeReceiver::class.java)
        val replyPendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyAction = NotificationCompat.Action.Builder(
            0,
            text("Enter pairing code", "Ввести код сопряжения"),
            replyPendingIntent,
        ).addRemoteInput(remoteInput).build()
        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text("Essential Remap setup", "Настройка Essential Remap"))
            .setContentText(_state.value.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(_state.value.message))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(replyAction)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun postResultNotification(title: String, message: String) {
        if (!canPostNotifications()) return
        val contentIntent = PendingIntent.getActivity(
            appContext,
            1,
            Intent(appContext, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notificationManager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun returnToApp() {
        runCatching {
            appContext.startActivity(
                Intent(appContext, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                text("Wireless debugging setup", "Настройка Wireless debugging"),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = text(
                    "Accepts the local ADB pairing code during Essential Remap setup",
                    "Принимает локальный код сопряжения ADB при настройке Essential Remap",
                )
            },
        )
    }

    private fun friendlyError(error: Throwable): String = when {
        error is kotlinx.coroutines.TimeoutCancellationException ->
            text(
                "Wireless debugging timed out. Keep it enabled and try again.",
                "Время ожидания Wireless debugging истекло. Оставьте его включённым и попробуйте снова.",
            )
        error.message?.contains("authentication", ignoreCase = true) == true ->
            text(
                "Android rejected the ADB identity. Remove old paired devices and try again.",
                "Android отклонил ADB-ключ. Удалите старые сопряжённые устройства и попробуйте снова.",
            )
        else -> error.message ?: error.javaClass.simpleName
    }

    private fun text(english: String, russian: String): String {
        val language = appContext.getSharedPreferences("essential_remap_ui", Context.MODE_PRIVATE)
            .getString("language", "en")
        return if (language == "ru") russian else english
    }

    private data class AdbEndpoint(
        val host: String,
        val port: Int,
    )

    companion object {
        const val REMOTE_INPUT_KEY = "adb_pairing_code"
        private const val CHANNEL_ID = "essential_key_adb_setup"
        private const val NOTIFICATION_ID = 2053
        private const val PAIRING_TIMEOUT_MS = 120_000L
        private const val LIVE_PAIRING_DISCOVERY_TIMEOUT_MS = 15_000L
        private const val CONNECTION_ATTEMPTS = 5
        private const val CONNECTION_DISCOVERY_TIMEOUT_MS = 5_000L
        private const val CONNECTION_RETRY_DELAY_MS = 1_000L
        private const val CONNECTION_AFTER_PAIR_DELAY_MS = 1_500L
        private const val SAVED_KEY_INITIAL_DISCOVERY_TIMEOUT_MS = 2_500L
        private const val SAVED_KEY_RETRY_DISCOVERY_TIMEOUT_MS = 1_500L
        private const val SAVED_KEY_WAIT_ATTEMPTS = 12
        private const val SAVED_KEY_WAIT_DELAY_MS = 750L
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val COMMAND_TIMEOUT_MS = 15_000L
        private const val SHELL_POLL_INTERVAL_MS = 25L
        private const val SHELL_WRITE_CHUNK_BYTES = 1_024
        private const val MAX_LOG_OUTPUT_CHARS = 2_000
        private const val ACTION_WIRELESS_DEBUGGING_SETTINGS = "android.settings.WIRELESS_DEBUGGING_SETTINGS"
        private const val SETTINGS_FRAGMENT_ARGUMENT_KEY = ":settings:fragment_args_key"
        private const val WIRELESS_DEBUGGING_PREFERENCE_KEY = "toggle_adb_wireless"
    }
}
