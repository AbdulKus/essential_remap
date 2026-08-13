package com.abdulkus.essentialremap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process

/** Receives only explicit, DUMP-protected events emitted by the ADB shell monitor. */
class ShellKeyEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            sentFromUid != Process.SHELL_UID
        ) {
            return
        }
        val action = ScreenOffKeyAction.entries.getOrNull(intent.getIntExtra(EXTRA_ACTION, -1))
            ?: return
        val event = ScreenOffKeyEvent(
            action = action,
            eventTimeNanos = intent.getLongExtra(EXTRA_EVENT_TIME, -1L),
            downTimeNanos = intent.getLongExtra(EXTRA_DOWN_TIME, -1L),
            repeatCount = intent.getIntExtra(EXTRA_REPEAT_COUNT, -1),
            interactive = intent.getBooleanExtra(EXTRA_INTERACTIVE, true),
        )
        if (event.eventTimeNanos <= 0L || event.downTimeNanos <= 0L || event.repeatCount < 0) return
        ShellKeyEventBus.publish(event)
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

internal object ShellKeyEventBus {
    private const val MAX_PENDING_EVENTS = 8
    private val pending = ArrayDeque<ScreenOffKeyEvent>()
    private var listener: ((ScreenOffKeyEvent) -> Unit)? = null

    @Synchronized
    fun attach(newListener: (ScreenOffKeyEvent) -> Unit) {
        listener = newListener
        while (pending.isNotEmpty()) newListener(pending.removeFirst())
    }

    @Synchronized
    fun detach(currentListener: (ScreenOffKeyEvent) -> Unit) {
        if (listener === currentListener) listener = null
    }

    @Synchronized
    fun publish(event: ScreenOffKeyEvent) {
        listener?.invoke(event) ?: run {
            if (pending.size == MAX_PENDING_EVENTS) pending.removeFirst()
            pending.addLast(event)
        }
    }
}
