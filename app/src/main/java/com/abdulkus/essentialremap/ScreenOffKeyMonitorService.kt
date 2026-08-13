package com.abdulkus.essentialremap

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger

/**
 * Hosts the filtered logcat reader in a fresh process. READ_LOGS maps to Android's supplemental
 * `log` group, and supplemental groups are fixed when a process starts, not when `pm grant` runs.
 */
class ScreenOffKeyMonitorService : Service() {
    private var callback: Messenger? = null
    private lateinit var reader: ScreenOffKeyLogReader
    private val incoming = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                ScreenOffKeyMonitorProtocol.START -> {
                    callback = message.replyTo
                    reader.start()
                    true
                }
                ScreenOffKeyMonitorProtocol.STOP -> {
                    reader.stop()
                    callback = null
                    true
                }
                else -> false
            }
        },
    )

    override fun onCreate() {
        super.onCreate()
        reader = ScreenOffKeyLogReader(::sendEvent)
    }

    override fun onBind(intent: Intent?): IBinder = incoming.binder

    override fun onUnbind(intent: Intent?): Boolean {
        reader.stop()
        callback = null
        return false
    }

    override fun onDestroy() {
        reader.close()
        callback = null
        super.onDestroy()
    }

    private fun sendEvent(event: ScreenOffKeyEvent) {
        val target = callback ?: return
        val message = Message.obtain(null, ScreenOffKeyMonitorProtocol.EVENT).apply {
            data = Bundle().apply {
                putInt(ScreenOffKeyMonitorProtocol.ACTION, event.action.ordinal)
                putLong(ScreenOffKeyMonitorProtocol.EVENT_TIME, event.eventTimeNanos)
                putLong(ScreenOffKeyMonitorProtocol.DOWN_TIME, event.downTimeNanos)
                putInt(ScreenOffKeyMonitorProtocol.REPEAT_COUNT, event.repeatCount)
                putBoolean(ScreenOffKeyMonitorProtocol.INTERACTIVE, event.interactive)
            }
        }
        runCatching { target.send(message) }
            .onFailure {
                callback = null
                reader.stop()
            }
    }
}

internal object ScreenOffKeyMonitorProtocol {
    const val START = 1
    const val STOP = 2
    const val EVENT = 3
    const val ACTION = "action"
    const val EVENT_TIME = "event_time"
    const val DOWN_TIME = "down_time"
    const val REPEAT_COUNT = "repeat_count"
    const val INTERACTIVE = "interactive"
}
