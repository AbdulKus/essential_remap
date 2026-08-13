package com.abdulkus.essentialremap

/**
 * Keeps a complete Essential Key press that began while the display was non-interactive.
 * Nothing OS can wake the display between DOWN and UP, so the matching UP must not be rejected
 * just because it is already marked interactive.
 */
internal class ScreenOffKeyEventFilter(
    private val startedAtNanos: Long,
) {
    private var pendingDownTimeNanos: Long? = null

    fun accept(event: ScreenOffKeyEvent): Boolean {
        if (event.eventTimeNanos < startedAtNanos) return false
        return when (event.action) {
            ScreenOffKeyAction.DOWN -> {
                if (event.interactive || event.repeatCount != 0) return false
                pendingDownTimeNanos = event.downTimeNanos
                true
            }
            ScreenOffKeyAction.UP -> {
                if (pendingDownTimeNanos != event.downTimeNanos) return false
                pendingDownTimeNanos = null
                true
            }
        }
    }
}
