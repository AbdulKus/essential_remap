package com.abdulkus.essentialremap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger

internal class ScreenOffKeyMonitorClient(
    context: Context,
    private val onEvent: (ScreenOffKeyEvent) -> Unit,
) {
    private val appContext = context.applicationContext
    private var remote: Messenger? = null
    private var binding = false
    private var bound = false
    private var wanted = false
    private val callback = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what != ScreenOffKeyMonitorProtocol.EVENT) return@Handler false
            val data = message.data
            val action = ScreenOffKeyAction.entries.getOrNull(
                data.getInt(ScreenOffKeyMonitorProtocol.ACTION, -1),
            ) ?: return@Handler true
            onEvent(
                ScreenOffKeyEvent(
                    action = action,
                    eventTimeNanos = data.getLong(ScreenOffKeyMonitorProtocol.EVENT_TIME),
                    downTimeNanos = data.getLong(ScreenOffKeyMonitorProtocol.DOWN_TIME),
                    repeatCount = data.getInt(ScreenOffKeyMonitorProtocol.REPEAT_COUNT),
                    interactive = data.getBoolean(ScreenOffKeyMonitorProtocol.INTERACTIVE),
                ),
            )
            true
        },
    )
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binding = false
            bound = true
            remote = service?.let(::Messenger)
            if (wanted) sendStart() else unbind()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            bound = false
        }

        override fun onBindingDied(name: ComponentName?) {
            remote = null
            bound = false
            binding = false
            if (wanted) bind()
        }

        override fun onNullBinding(name: ComponentName?) {
            remote = null
            bound = false
            binding = false
        }
    }

    fun start() {
        wanted = true
        if (bound) {
            sendStart()
        } else if (!binding) {
            bind()
        }
    }

    fun stop() {
        wanted = false
        runCatching { remote?.send(Message.obtain(null, ScreenOffKeyMonitorProtocol.STOP)) }
        unbind()
    }

    fun close() = stop()

    private fun bind() {
        binding = true
        val connected = runCatching {
            appContext.bindService(
                Intent(appContext, ScreenOffKeyMonitorService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        if (!connected) binding = false
    }

    private fun sendStart() {
        val message = Message.obtain(null, ScreenOffKeyMonitorProtocol.START).apply {
            replyTo = callback
        }
        if (runCatching { remote?.send(message) }.isFailure) {
            remote = null
            bound = false
        }
    }

    private fun unbind() {
        if (binding || bound) runCatching { appContext.unbindService(connection) }
        remote = null
        binding = false
        bound = false
    }
}
