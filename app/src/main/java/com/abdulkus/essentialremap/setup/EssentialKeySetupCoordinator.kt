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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.abdulkus.essentialremap.MainActivity
import com.abdulkus.essentialremap.R
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
    WAITING_FOR_CODE,
    PAIRING,
    CONNECTING,
    APPLYING,
    COMPLETE,
    ERROR,
}

data class EssentialKeySetupState(
    val packageStatus: NothingPackageStatus = NothingPackageStatus.UNKNOWN,
    val phase: SetupPhase = SetupPhase.IDLE,
    val operation: PackageOperation? = null,
    val message: String? = null,
) {
    val busy: Boolean get() = phase in setOf(
        SetupPhase.DISCOVERING,
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

class EssentialKeySetupCoordinator(context: Context) : EssentialKeySetupController {
    private val appContext = context.applicationContext
    private val statusReader = NothingPackageStatusReader(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val diagnostics = SetupDiagnostics(appContext)
    private val _state = MutableStateFlow(
        EssentialKeySetupState(packageStatus = statusReader.read()),
    )
    override val state: StateFlow<EssentialKeySetupState> = _state.asStateFlow()

    private var setupJob: Job? = null
    private var pairingCode = CompletableDeferred<String>()

    init {
        createNotificationChannel()
        diagnostics.log("Coordinator created; packageStatus=${_state.value.packageStatus}")
    }

    override fun refresh() {
        _state.value = _state.value.copy(packageStatus = statusReader.read())
    }

    override fun start(operation: PackageOperation) {
        setupJob?.cancel()
        pairingCode = CompletableDeferred()
        _state.value = EssentialKeySetupState(
            packageStatus = statusReader.read(),
            phase = SetupPhase.WAITING_FOR_CODE,
            operation = operation,
            message = text(
                "Open Wireless debugging and choose Pair device with pairing code",
                "Откройте Wireless debugging и выберите сопряжение по коду",
            ),
        )
        diagnostics.log("--- Setup started: operation=$operation packageStatus=${_state.value.packageStatus} ---")
        postPairingNotification()
        setupJob = scope.launch {
            runCatching {
                val code = withTimeout(PAIRING_TIMEOUT_MS) { pairingCode.await() }
                diagnostics.log("Discovering the live pairing endpoint after code submission")
                val pairingEndpoint = discoverAdbEndpoint(
                    AdbMdns.SERVICE_TYPE_TLS_PAIRING,
                    LIVE_PAIRING_DISCOVERY_TIMEOUT_MS,
                )
                applyOperation(pairingEndpoint, code, operation)
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) return@onFailure
                diagnostics.log("Setup failed: ${error.fullDescription()}")
                _state.value = _state.value.copy(
                    packageStatus = statusReader.read(),
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
        _state.value = EssentialKeySetupState(packageStatus = statusReader.read())
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

    private suspend fun applyOperation(
        pairingEndpoint: AdbEndpoint,
        code: String,
        operation: PackageOperation,
    ) {
        var manager: LocalAdbConnectionManager? = null
        try {
            _state.value = _state.value.copy(
                phase = SetupPhase.PAIRING,
                message = text("Pairing with Android", "Сопряжение с Android"),
            )
            postProgressNotification()
            pairWithFallback(pairingEndpoint, code)
            _state.value = _state.value.copy(
                phase = SetupPhase.CONNECTING,
                message = text("Connecting to local ADB", "Подключение к локальному ADB"),
            )
            postProgressNotification()
            diagnostics.log("Pairing succeeded; waiting ${CONNECTION_AFTER_PAIR_DELAY_MS}ms for TLS-connect service")
            delay(CONNECTION_AFTER_PAIR_DELAY_MS)
            val connectedManager = connectWithRetry()
            manager = connectedManager
            diagnostics.log("ADB connection established")
            _state.value = _state.value.copy(
                phase = SetupPhase.APPLYING,
                message = if (operation == PackageOperation.DISABLE) {
                    text("Releasing the Essential Key", "Освобождаем Essential Key")
                } else {
                    text("Restoring Essential Space", "Возвращаем Essential Space")
                },
            )
            postProgressNotification()
            NothingPackageCommands.commands(operation).forEach { command ->
                diagnostics.log("Executing allowlisted command: $command")
                val output = executeAllowlisted(connectedManager, command)
                diagnostics.log("Command output: ${output.take(MAX_LOG_OUTPUT_CHARS)}")
                if (!output.contains("new state:", ignoreCase = true)) {
                    error(output.ifBlank { "Android did not confirm the package change" })
                }
            }
            val packageStatus = verifyPackageState(connectedManager, operation)
            diagnostics.log("Package verification succeeded: status=$packageStatus")
            _state.value = EssentialKeySetupState(
                packageStatus = packageStatus,
                phase = SetupPhase.COMPLETE,
                operation = operation,
                message = if (operation == PackageOperation.DISABLE) {
                    text(
                        "Essential Key released. You can turn off Wireless debugging.",
                        "Essential Key освобождена. Wireless debugging можно выключить.",
                    )
                } else {
                    text(
                        "Essential Space restored. You can turn off Wireless debugging.",
                        "Essential Space восстановлен. Wireless debugging можно выключить.",
                    )
                },
            )
            val successMessage = _state.value.message.orEmpty()
            postResultNotification(text("Setup complete", "Настройка завершена"), successMessage)
            returnToApp()
        } finally {
            runCatching { manager?.disconnect() }
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
        require(command in NothingPackageCommands.commands(PackageOperation.DISABLE) ||
            command in NothingPackageCommands.commands(PackageOperation.RESTORE)) {
            "Command is not allowlisted"
        }
        return readShellOutput(manager, command) {
            it.contains("new state:", ignoreCase = true)
        }.trim()
    }

    private suspend fun connectWithRetry(): LocalAdbConnectionManager {
        var lastError: Throwable? = null
        repeat(CONNECTION_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(CONNECTION_RETRY_DELAY_MS)
            diagnostics.log("Connect discovery attempt ${attempt + 1}/$CONNECTION_ATTEMPTS")
            val endpoint = try {
                discoverAdbEndpoint(
                    AdbMdns.SERVICE_TYPE_TLS_CONNECT,
                    CONNECTION_DISCOVERY_TIMEOUT_MS,
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
            "Could not connect to Android’s Wireless debugging service after pairing. " +
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
        val flag = if (operation == PackageOperation.DISABLE) "-d" else "-e"
        val verified = NothingPackageCommands.packages.all { packageName ->
            val command = "pm list packages $flag $packageName"
            val expected = "package:$packageName"
            diagnostics.log("Verifying package: command=$command")
            val output = readShellOutput(manager, command) { it.contains(expected) }
            diagnostics.log("Verification output: ${output.take(MAX_LOG_OUTPUT_CHARS)}")
            output.contains(expected)
        }
        if (!verified) error("Android could not verify both Nothing packages")
        return if (operation == PackageOperation.DISABLE) {
            NothingPackageStatus.DISABLED
        } else {
            NothingPackageStatus.ENABLED
        }
    }

    private fun readShellOutput(
        manager: LocalAdbConnectionManager,
        command: String,
        complete: (String) -> Boolean,
    ): String {
        val output = StringBuilder()
        val stream = manager.openStream("shell:$command")
        val deadline = SystemClock.elapsedRealtime() + COMMAND_TIMEOUT_MS
        try {
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
        } finally {
            runCatching { stream.close() }
        }
        if (!complete(output.toString())) {
            diagnostics.log(
                "Shell command timed out: command=$command output=${output.toString().take(MAX_LOG_OUTPUT_CHARS)}",
            )
            error(output.toString().ifBlank { "ADB command timed out without confirmation" })
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
                "Pairing timed out. Keep the pairing dialog open and try again.",
                "Время сопряжения истекло. Не закрывайте окно с кодом и попробуйте снова.",
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
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val COMMAND_TIMEOUT_MS = 15_000L
        private const val SHELL_POLL_INTERVAL_MS = 25L
        private const val MAX_LOG_OUTPUT_CHARS = 2_000
    }
}
