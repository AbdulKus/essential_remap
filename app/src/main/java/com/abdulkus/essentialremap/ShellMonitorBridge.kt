package com.abdulkus.essentialremap

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
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
    private val receipts = appContext.getSharedPreferences("shell_monitor_receipts", Context.MODE_PRIVATE)
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connecting = AtomicBoolean(false)
    @Volatile private var socket: LocalSocket? = null
    @Volatile private var writer: PrintWriter? = null
    @Volatile private var lastReadyElapsed = 0L
    @Volatile private var lastReport = "transport=disconnected"
    private var listener: ((MonitorMessage, Boolean) -> Unit)? = null
    private data class Pending(val message: MonitorMessage, val interactive: Boolean)
    private val pending = ArrayDeque<Pending>()
    private val seen = LinkedHashMap<String, Long>()
    private val handoff = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.abdulkus.essentialremap:input-handoff")
        .apply { setReferenceCounted(false) }

    fun requestConnect() {
        if (!preferences.screenOffEnabled) return
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
            try {
                repeat(3) { attempt ->
                    if (attempt > 0) delay(250L shl attempt)
                    val candidate = LocalSocket()
                    try {
                        candidate.connect(LocalSocketAddress(MonitorMessage.SOCKET, LocalSocketAddress.Namespace.ABSTRACT))
                        check(candidate.peerCredentials.uid == android.os.Process.SHELL_UID) { "Unexpected peer UID" }
                        candidate.soTimeout = 2_000
                        socket = candidate
                        val output = PrintWriter(candidate.outputStream.bufferedWriter(Charsets.UTF_8), true)
                        writer = output
                        output.println("PING")
                        candidate.inputStream.bufferedReader(Charsets.UTF_8).use { input ->
                            while (true) {
                                val line = input.readLine() ?: break
                                val message = MonitorMessage.parse(line)
                                if (message.kind == "READY") candidate.soTimeout = 0
                                receive(message, "socket")
                                output.println("ACK ${message.number}")
                                check(!output.checkError()) { "ACK write failed" }
                            }
                        }
                    } catch (error: Exception) {
                        diagnostics.log("Bridge: connection attempt=${attempt + 1} ${error.javaClass.simpleName}")
                    } finally {
                        runCatching { candidate.close() }
                        if (socket === candidate) {
                            socket = null
                            writer = null
                            ScreenOffKeyAccess.setRuntimeHealthy(false)
                            handler.post { resetDelivery() }
                        }
                    }
                }
            } finally {
                connecting.set(false)
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
                return@post
            }
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
