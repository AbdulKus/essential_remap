package com.abdulkus.essentialremap

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
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        if (socket != null) {
            val requestedAt = SystemClock.elapsedRealtime()
            scope.launch {
                runCatching { writer?.println("PING") }
                delay(1_500) // Only a requested probe, never a recurring heartbeat.
                if (lastReadyElapsed < requestedAt && socket != null) {
                    diagnostics.log("Bridge: requested health probe timed out")
                    closeSocket()
                }
            }
            return
        }
        if (!connecting.compareAndSet(false, true)) return
        scope.launch {
            var attemptedEndpoint = endpoint
            try {
                repeat(3) { attempt ->
                    if (attempt > 0) delay(250L shl attempt)
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
                                val message = MonitorMessage.parse(line)
                                if (message.kind == "READY") candidate.soTimeout = 0
                                receive(message, "socket")
                                output.println("ACK ${message.number}")
                                check(!output.checkError()) { "ACK write failed" }
                            }
                        }
                    } catch (error: Exception) {
                        diagnostics.log("Bridge: connection attempt=${attempt + 1} ${error.javaClass.simpleName}: ${error.message}")
                    } finally {
                        runCatching { candidate.close() }
                        if (socket === candidate) {
                            socket = null
                            writer = null
                            ScreenOffKeyAccess.setRuntimeHealthy(false)
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
