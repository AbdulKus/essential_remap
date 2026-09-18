package com.abdulkus.essentialremap

import android.content.ComponentName
import android.content.Context
import java.net.Socket
import java.net.InetSocketAddress
import com.abdulkus.essentialremap.monitor.MonitorTransport
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.abdulkus.essentialremap.monitor.MonitorMessage
import com.abdulkus.essentialremap.setup.SetupDiagnostics
import com.abdulkus.essentialremap.ui.UserPreferences
import com.abdulkus.essentialremap.ui.AppLanguage
import com.abdulkus.essentialremap.ui.translate
import java.io.PrintWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One blocking local socket; reconnect only at activation, a real disconnect, or a user probe. */
class ShellMonitorBridge(context: Context, private val diagnostics: SetupDiagnostics) {
    private val appContext = context.applicationContext
    private val preferences = UserPreferences(appContext)
    private val credentials = appContext.getSharedPreferences("shell_monitor_transport", Context.MODE_PRIVATE)
    private data class Endpoint(val port: Int, val secret: String)
    @Volatile private var endpoint: Endpoint? = if (credentials.getInt("boot", -2) ==
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)) {
        val port = credentials.getInt("port", 0)
        val secret = credentials.getString("secret", null)
        if (port in 1024..65535 && MonitorTransport.validSecret(secret)) Endpoint(port, secret!!) else null
    } else null
    private val receipts = appContext.getSharedPreferences("shell_monitor_receipts", Context.MODE_PRIVATE)
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connecting = AtomicBoolean(false)
    private val commandReplies = ConcurrentHashMap<String, CompletableDeferred<Result<Unit>>>()
    private val forceStopReplies = ConcurrentHashMap<String, CompletableDeferred<Result<String>>>()
    private data class ForegroundTarget(val packageName: String, val userId: Int, val capturedElapsed: Long)
    @Volatile private var prefetchedForeground: ForegroundTarget? = null
    @Volatile private var foregroundRequestId: String? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: PrintWriter? = null
    @Volatile private var lastReadyElapsed = 0L
    @Volatile private var lastReport = "transport=disconnected"
    private var listener: ((MonitorMessage, Boolean) -> Unit)? = null
    private data class Pending(val message: MonitorMessage, val interactive: Boolean)
    private val pending = ArrayDeque<Pending>()
    private val seen = LinkedHashMap<String, Long>()
    private var activeSession: String? = null
    private val handoff = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.abdulkus.essentialremap:input-handoff")
        .apply { setReferenceCounted(false) }

    private companion object {
        val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
        const val UNKNOWN_USER_ID = -1
        const val FOREGROUND_PREFETCH_MAX_AGE_MS = 2_500L
    }

    /** Called exclusively after the manifest DUMP permission and sender checks in the receiver. */
    fun configure(port: Int, secret: String) {
        if (port !in 1024..65535 || !MonitorTransport.validSecret(secret)) return
        val next = Endpoint(port, secret)
        if (endpoint == next) return
        endpoint = next
        credentials.edit().putInt("port", port).putString("secret", secret)
            .putInt("boot", Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)).apply()
        closeSocket()
    }

    fun requestConnect() {
        if (!preferences.screenOffEnabled || endpoint == null) return
        val probedSocket = socket
        val probedWriter = writer
        if (probedSocket != null) {
            val requestedAt = SystemClock.elapsedRealtime()
            scope.launch {
                runCatching { probedWriter?.println("PING") }
                delay(1_500) // Only a requested probe, never a recurring heartbeat.
                if (lastReadyElapsed < requestedAt && socket === probedSocket) {
                    diagnostics.log("Bridge: requested health probe timed out")
                    runCatching { probedSocket.close() }
                }
            }
            return
        }
        if (!connecting.compareAndSet(false, true)) return
        scope.launch {
            var attemptedEndpoint = endpoint
            try {
                var failures = 0
                var reconnecting = false
                while (failures < 3 && preferences.screenOffEnabled) {
                    if (reconnecting) delay(250L shl maxOf(1, failures))
                    reconnecting = true
                    val target = endpoint ?: return@launch
                    attemptedEndpoint = target
                    val candidate = Socket()
                    try {
                        candidate.connect(InetSocketAddress(MonitorTransport.HOST, target.port), 1_000)
                        val channel = MonitorTransport.client(candidate, target.secret)
                        candidate.soTimeout = 2_000
                        socket = candidate
                        val output = channel.output
                        writer = output
                        output.println("PING")
                        channel.input.use { input ->
                            while (true) {
                                val line = MonitorTransport.readLine(input) ?: break
                                when {
                                    line.startsWith("TILE_RESULT ") -> {
                                        receiveCommandReply(line)
                                        continue
                                    }
                                    line.startsWith("FORCE_STOP_RESULT ") -> {
                                        receiveForceStopReply(line)
                                        continue
                                    }
                                    line.startsWith("FOREGROUND_RESULT ") -> {
                                        receiveForegroundReply(line)
                                        continue
                                    }
                                }
                                val message = MonitorMessage.parse(line)
                                if (message.kind == "READY" &&
                                    message.fresh(SystemClock.elapsedRealtime(), SystemClock.uptimeMillis())) {
                                    candidate.soTimeout = 0
                                    // Bound consecutive failures, not the lifetime number of connections.
                                    failures = 0
                                }
                                receive(message, "socket")
                                synchronized(output) {
                                    output.println("ACK ${message.number}")
                                    check(!output.checkError()) { "ACK write failed" }
                                }
                            }
                        }
                    } catch (error: Exception) {
                        diagnostics.log("Bridge: connection failure=${failures + 1} ${error.javaClass.simpleName}: ${error.message}")
                    } finally {
                        failures++
                        runCatching { candidate.close() }
                        if (socket === candidate) {
                            socket = null
                            writer = null
                            failCommandReplies("Sleep monitor disconnected")
                            failForceStopReplies("Sleep monitor disconnected")
                            prefetchedForeground = null
                            foregroundRequestId = null
                            ScreenOffKeyAccess.setRuntimeHealthy(false)
                            diagnostics.log("Bridge: disconnected " +
                                com.abdulkus.essentialremap.setup.AdbLifetimeState.read(appContext))
                            // A transport reconnect is not an input reset. Keep fresh pending DOWNs.
                        }
                    }
                }
            } finally {
                connecting.set(false)
                if (endpoint != attemptedEndpoint) requestConnect()
            }
        }
    }

    suspend fun clickQuickSettingsTile(componentName: String): Result<Unit> {
        if (!preferences.screenOffEnabled) {
            return Result.failure(IllegalStateException("Sleep monitor is disabled"))
        }
        if (!ScreenOffKeyAccess.isGranted(appContext)) {
            return Result.failure(IllegalStateException("Sleep monitor is not running"))
        }
        if (ComponentName.unflattenFromString(componentName) == null) {
            return Result.failure(IllegalArgumentException("Invalid Quick Settings tile component"))
        }
        val activeWriter = writer
            ?: return Result.failure(IllegalStateException("Sleep monitor is disconnected"))
        val requestId = UUID.randomUUID().toString().replace("-", "").take(12)
        val reply = CompletableDeferred<Result<Unit>>()
        commandReplies[requestId] = reply
        return try {
            val sent = synchronized(activeWriter) {
                activeWriter.println("CLICK_TILE $requestId $componentName")
                !activeWriter.checkError()
            }
            if (!sent) {
                Result.failure(IllegalStateException("Could not send command to sleep monitor"))
            } else {
                withTimeoutOrNull(4_000L) { reply.await() }
                    ?: Result.failure(IllegalStateException("Sleep monitor did not answer the tile command"))
            }
        } finally {
            commandReplies.remove(requestId)
        }
    }

    fun prefetchForegroundApp(fallbackPackageName: String?) {
        if (!preferences.screenOffEnabled || !ScreenOffKeyAccess.isGranted(appContext)) return
        val activeWriter = writer ?: return
        val requestId = UUID.randomUUID().toString().replace("-", "").take(12)
        val safeFallback = fallbackPackageName
            ?.takeIf { it.matches(PACKAGE_NAME_PATTERN) }
            ?: "-"
        foregroundRequestId = requestId
        prefetchedForeground = null
        synchronized(activeWriter) {
            activeWriter.println("GET_FOREGROUND $requestId $safeFallback")
            if (activeWriter.checkError()) {
                foregroundRequestId = null
            }
        }
    }

    suspend fun forceStopForegroundApp(foregroundPackageName: String?): Result<String> {
        if (!preferences.screenOffEnabled) {
            return Result.failure(
                IllegalStateException(localized(
                    "Enable the sleep monitor in Essential Remap settings first",
                    "Сначала включите монитор сна в настройках Essential Remap",
                )),
            )
        }
        if (!ScreenOffKeyAccess.isGranted(appContext)) {
            val message = if (ScreenOffKeyAccess.wasConfigured(appContext)) {
                localized(
                    "Restart the sleep monitor in Essential Remap settings after the app update",
                    "Перезапустите монитор сна в настройках Essential Remap после обновления приложения",
                )
            } else {
                localized(
                    "Start the sleep monitor in Essential Remap settings first",
                    "Сначала запустите монитор сна в настройках Essential Remap",
                )
            }
            return Result.failure(IllegalStateException(message))
        }
        var activeWriter = writer
        if (activeWriter == null) {
            requestConnect()
            val connected = withTimeoutOrNull(1_500L) {
                while (writer == null) delay(50L)
                writer
            }
            activeWriter = connected
        }
        val commandWriter = activeWriter
            ?: return Result.failure(IllegalStateException(localized(
                "Sleep monitor is disconnected. Restart it in Essential Remap settings",
                "Монитор сна отключён. Перезапустите его в настройках Essential Remap",
            )))
        val requestId = UUID.randomUUID().toString().replace("-", "").take(12)
        val reply = CompletableDeferred<Result<String>>()
        forceStopReplies[requestId] = reply
        val prefetched = prefetchedForeground
            ?.takeIf { SystemClock.elapsedRealtime() - it.capturedElapsed <= FOREGROUND_PREFETCH_MAX_AGE_MS }
        prefetchedForeground = null
        foregroundRequestId = null
        return try {
            val safePackage = prefetched?.packageName
                ?: foregroundPackageName?.takeIf { it.matches(PACKAGE_NAME_PATTERN) }
                ?: "-"
            val userId = prefetched?.userId ?: UNKNOWN_USER_ID
            val sent = synchronized(commandWriter) {
                commandWriter.println("FORCE_STOP_FOREGROUND $requestId $userId $safePackage")
                !commandWriter.checkError()
            }
            if (!sent) {
                Result.failure(IllegalStateException("Could not send force-stop command"))
            } else {
                withTimeoutOrNull(4_000L) { reply.await() }
                    ?: Result.failure(IllegalStateException("Shell monitor did not answer the force-stop command"))
            }
        } finally {
            forceStopReplies.remove(requestId)
        }
    }

    private fun localized(en: String, ru: String): String =
        (preferences.language ?: AppLanguage.ENGLISH).translate(en, ru)

    private fun receiveCommandReply(line: String) {
        val parts = line.split(' ', limit = 4)
        if (parts.size < 3) return
        val requestId = parts[1]
        val pending = commandReplies.remove(requestId) ?: return
        if (parts[2] == "OK") {
            pending.complete(Result.success(Unit))
        } else {
            val detail = parts.getOrNull(3).orEmpty().ifBlank { "Shell command failed" }
            pending.complete(Result.failure(IllegalStateException(detail)))
        }
    }

    private fun receiveForegroundReply(line: String) {
        val parts = line.split(' ', limit = 6)
        if (parts.size < 3) return
        val requestId = parts[1]
        if (foregroundRequestId != requestId) return
        foregroundRequestId = null
        if (parts[2] != "OK" || parts.size < 5) return
        val userId = parts[3].toIntOrNull() ?: UNKNOWN_USER_ID
        val packageName = parts[4]
        if (!packageName.matches(PACKAGE_NAME_PATTERN)) return
        prefetchedForeground = ForegroundTarget(
            packageName = packageName,
            userId = userId,
            capturedElapsed = SystemClock.elapsedRealtime(),
        )
        diagnostics.log("Bridge: prefetched foreground package=$packageName user=$userId")
    }

    private fun receiveForceStopReply(line: String) {
        val parts = line.split(' ', limit = 5)
        if (parts.size < 3) return
        val requestId = parts[1]
        val pending = forceStopReplies.remove(requestId) ?: return
        if (parts[2] == "OK") {
            val packageName = parts.getOrNull(3)
                ?.takeIf { it.matches(PACKAGE_NAME_PATTERN) }
                ?: "application"
            pending.complete(Result.success(packageName))
        } else {
            val detail = parts.getOrNull(3).orEmpty().ifBlank { "Shell command failed" }
            pending.complete(Result.failure(IllegalStateException(detail)))
        }
    }

    private fun failCommandReplies(message: String) {
        val pending = commandReplies.values.toList()
        commandReplies.clear()
        pending.forEach { it.complete(Result.failure(IllegalStateException(message))) }
    }

    private fun failForceStopReplies(message: String) {
        val pending = forceStopReplies.values.toList()
        forceStopReplies.clear()
        pending.forEach { it.complete(Result.failure(IllegalStateException(message))) }
    }

    fun receive(message: MonitorMessage, transport: String) {
        if (!preferences.screenOffEnabled) return
        val elapsed = SystemClock.elapsedRealtime()
        if (!message.fresh(elapsed, SystemClock.uptimeMillis())) {
            diagnostics.log("Bridge: stale message dropped kind=${message.kind} delayMs=${elapsed - message.emittedElapsedMs}")
            return
        }
        val interactive = power.isInteractive
        if (!interactive && (message.kind == "DOWN" || message.isGesture)) {
            // Protect the handoff before posting to main/settings loading. Never held while idle.
            runCatching { handoff.acquire(5_000L) }
        }
        handler.post {
            if (!message.fresh(SystemClock.elapsedRealtime(), SystemClock.uptimeMillis())) {
                releaseHandoff()
                return@post
            }
            val previous = seen[message.session] ?: 0L
            if (message.number <= previous) {
                diagnostics.log("Bridge: duplicate ignored session=${message.session.take(8)} number=${message.number}")
                if (pending.isEmpty()) releaseHandoff()
                return@post
            }
            if (activeSession != null && activeSession != message.session) {
                if (seen.containsKey(message.session) || (message.kind != "READY" && message.kind != "DOWN")) {
                    diagnostics.log("Bridge: retired or uninitialized session ignored")
                    if (pending.isEmpty()) releaseHandoff()
                    return@post
                }
                resetDelivery()
                listener?.invoke(MonitorMessage(message.session, message.number, "RESET", 0, 0,
                    message.emittedElapsedMs), interactive)
            }
            activeSession = message.session
            seen[message.session] = message.number
            while (seen.size > 4) seen.remove(seen.keys.first())
            lastReport = "transport=$transport session=${message.session.take(8)} number=${message.number} " +
                "kind=${message.kind} latencyMs=${SystemClock.elapsedRealtime() - message.emittedElapsedMs} pending=${pending.size}"
            diagnostics.log("Bridge: $lastReport")
            if (message.kind == "READY") {
                lastReadyElapsed = SystemClock.elapsedRealtime()
                ScreenOffKeyAccess.setRuntimeHealthy(true)
                return@post
            }
            if (message.kind == "RESET") {
                resetDelivery()
                listener?.invoke(message, interactive)
                ScreenOffKeyAccess.setRuntimeHealthy(false)
                return@post
            }
            val entry = Pending(message, interactive)
            if (message.kind == "DOWN") ScreenOffKeyAccess.setRuntimeHealthy(true)
            if (listener != null) {
                deliver(entry)
                releaseHandoff()
            } else {
                if (pending.size >= 16) {
                    pending.clear() // Never retain an orphan UP/gesture after dropping its DOWN.
                    diagnostics.log("Bridge: pending sequence overflow; cleared")
                }
                pending.addLast(entry)
            }
        }
    }

    private fun deliver(entry: Pending) {
        val message = entry.message
        if (!message.fresh(SystemClock.elapsedRealtime(), SystemClock.uptimeMillis())) return
        if (message.isGesture) {
            if (receipts.getString("session", null) == message.session &&
                receipts.getLong("number", 0) >= message.number) return
            // Persist before dispatch: an ACK lost across app death must not execute an action twice.
            if (!receipts.edit().putString("session", message.session).putLong("number", message.number).commit()) {
                diagnostics.log("Bridge: could not persist action receipt; action discarded")
                return
            }
        }
        listener?.invoke(message, entry.interactive)
    }

    fun attach(newListener: (MonitorMessage, Boolean) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        listener = newListener
        while (pending.isNotEmpty()) deliver(pending.removeFirst())
        releaseHandoff()
        requestConnect()
    }

    fun detach(currentListener: (MonitorMessage, Boolean) -> Unit) {
        if (listener === currentListener) listener = null
        resetDelivery()
    }

    fun report(): String = "$lastReport connected=${socket != null} healthy=${ScreenOffKeyAccess.runtimeHealthy}"

    private fun resetDelivery() { pending.clear(); releaseHandoff() }
    private fun releaseHandoff() { runCatching { if (handoff.isHeld) handoff.release() } }
    private fun closeSocket() { runCatching { socket?.close() } }
}
