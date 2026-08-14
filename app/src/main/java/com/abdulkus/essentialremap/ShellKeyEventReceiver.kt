package com.abdulkus.essentialremap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.abdulkus.essentialremap.setup.SetupDiagnostics

/** Receives only explicit, DUMP-protected events emitted by the ADB shell monitor. */
class ShellKeyEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val diagnostics = (context.applicationContext as? EssentialKeyApplication)
            ?.container?.diagnostics ?: SetupDiagnostics(context)
        if (intent.action != ACTION) {
            diagnostics.log("Runtime receiver: rejected unexpected intent action=${intent.action}")
            return
        }
        val senderUid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            sentFromUid
        } else {
            null
        }
        if (!ShellKeyEventSenderPolicy.isAllowed(senderUid)) {
            diagnostics.log("Runtime receiver: rejected sender uid=$senderUid")
            return
        }
        val action = ScreenOffKeyAction.entries.getOrNull(intent.getIntExtra(EXTRA_ACTION, -1))
        if (action == null) {
            diagnostics.log("Runtime receiver: rejected invalid action extra")
            return
        }
        val event = ScreenOffKeyEvent(
            action = action,
            eventTimeNanos = intent.getLongExtra(EXTRA_EVENT_TIME, -1L),
            downTimeNanos = intent.getLongExtra(EXTRA_DOWN_TIME, -1L),
            repeatCount = intent.getIntExtra(EXTRA_REPEAT_COUNT, -1),
            interactive = intent.getBooleanExtra(EXTRA_INTERACTIVE, true),
        )
        if (event.eventTimeNanos <= 0L || event.downTimeNanos <= 0L || event.repeatCount < 0) {
            diagnostics.log(
                "Runtime receiver: rejected malformed $action eventTime=${event.eventTimeNanos} " +
                    "downTime=${event.downTimeNanos} repeat=${event.repeatCount}",
            )
            return
        }
        val delivery = ShellKeyEventBus.publish(event)
        diagnostics.log(
            "Runtime receiver: accepted source=shell uid=${ShellKeyEventSenderPolicy.label(senderUid)} " +
                "action=$action interactive=${event.interactive} repeat=${event.repeatCount} " +
                "downTime=${event.downTimeNanos} delivery=$delivery",
        )
    }

    companion object {
        const val ACTION = "com.abdulkus.essentialremap.SHELL_KEY_EVENT"
        private const val EXTRA_ACTION = "action"
        private const val EXTRA_EVENT_TIME = "event_time"
        private const val EXTRA_DOWN_TIME = "down_time"
        private const val EXTRA_REPEAT_COUNT = "repeat_count"
        private const val EXTRA_INTERACTIVE = "interactive"
    }
}

/**
 * Android 16 may return -1 for an `am broadcast` sender even though the manifest permission check
 * has already authenticated shell. A concrete, non-shell UID is still rejected defensively.
 */
internal object ShellKeyEventSenderPolicy {
    private const val UNAVAILABLE_UID = -1

    fun isAllowed(uid: Int?): Boolean =
        uid == null || uid == UNAVAILABLE_UID || uid == Process.SHELL_UID

    fun label(uid: Int?): String = when (uid) {
        null, UNAVAILABLE_UID -> "unavailable(DUMP-protected)"
        else -> uid.toString()
    }
}

internal object ShellKeyEventBus {
    enum class Delivery { LISTENER, QUEUED, QUEUED_AFTER_DROP }

    private const val MAX_PENDING_EVENTS = 8
    private val pending = ArrayDeque<ScreenOffKeyEvent>()
    private var listener: ((ScreenOffKeyEvent) -> Unit)? = null

    @Synchronized
    fun attach(newListener: (ScreenOffKeyEvent) -> Unit): Int {
        listener = newListener
        val pendingCount = pending.size
        while (pending.isNotEmpty()) newListener(pending.removeFirst())
        return pendingCount
    }

    @Synchronized
    fun detach(currentListener: (ScreenOffKeyEvent) -> Unit) {
        if (listener === currentListener) listener = null
    }

    @Synchronized
    fun publish(event: ScreenOffKeyEvent): Delivery {
        listener?.let {
            it(event)
            return Delivery.LISTENER
        }
        val dropped = pending.size == MAX_PENDING_EVENTS
        if (dropped) pending.removeFirst()
        pending.addLast(event)
        return if (dropped) Delivery.QUEUED_AFTER_DROP else Delivery.QUEUED
    }
}
