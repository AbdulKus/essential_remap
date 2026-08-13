package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.domain.PressAction

class GestureClassifier(
    private val scheduler: Scheduler,
    private val onAction: (PressAction) -> Unit,
    private val longPressMs: Long = 600,
    private val doublePressMs: Long = 300,
) {
    interface Cancellable {
        fun cancel()
    }

    interface Scheduler {
        fun schedule(delayMs: Long, task: () -> Unit): Cancellable
    }

    private var pressed = false
    private var longFired = false
    private var waitingForSecond = false
    private var longTask: Cancellable? = null
    private var singleTask: Cancellable? = null

    fun onKeyDown(repeatCount: Int) {
        if (repeatCount != 0 || pressed) return
        pressed = true
        longFired = false
        longTask?.cancel()
        longTask = scheduler.schedule(longPressMs) {
            if (pressed) {
                longFired = true
                waitingForSecond = false
                singleTask?.cancel()
                singleTask = null
                onAction(PressAction.LONG)
            }
        }
    }

    fun onKeyUp(canceled: Boolean) {
        if (!pressed) return
        pressed = false
        longTask?.cancel()
        longTask = null

        if (canceled) {
            resetPendingShortPress()
            return
        }
        if (longFired) return

        if (waitingForSecond) {
            waitingForSecond = false
            singleTask?.cancel()
            singleTask = null
            onAction(PressAction.DOUBLE)
        } else {
            waitingForSecond = true
            singleTask = scheduler.schedule(doublePressMs) {
                if (waitingForSecond) {
                    waitingForSecond = false
                    onAction(PressAction.SINGLE)
                }
            }
        }
    }

    fun reset() {
        pressed = false
        longFired = false
        longTask?.cancel()
        longTask = null
        resetPendingShortPress()
    }

    private fun resetPendingShortPress() {
        waitingForSecond = false
        singleTask?.cancel()
        singleTask = null
    }
}
