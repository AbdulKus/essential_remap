package com.abdulkus.essentialremap

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ShellKeyEventBusTest {
    private val received = mutableListOf<ScreenOffKeyEvent>()
    private val listener: (ScreenOffKeyEvent) -> Unit = received::add

    @After
    fun tearDown() {
        ShellKeyEventBus.detach(listener)
    }

    @Test
    fun eventQueuedBeforeServiceSettingsLoadIsDeliveredAfterAttach() {
        val down = event(ScreenOffKeyAction.DOWN, downTime = 100L)
        val up = event(ScreenOffKeyAction.UP, downTime = 100L)

        assertEquals(ShellKeyEventBus.Delivery.QUEUED, ShellKeyEventBus.publish(down))
        assertEquals(ShellKeyEventBus.Delivery.QUEUED, ShellKeyEventBus.publish(up))

        assertEquals(2, ShellKeyEventBus.attach(listener))
        assertEquals(listOf(down, up), received)
    }

    @Test
    fun attachedServiceReceivesEventImmediately() {
        assertEquals(0, ShellKeyEventBus.attach(listener))
        val down = event(ScreenOffKeyAction.DOWN, downTime = 200L)

        assertEquals(ShellKeyEventBus.Delivery.LISTENER, ShellKeyEventBus.publish(down))
        assertEquals(listOf(down), received)
    }

    private fun event(action: ScreenOffKeyAction, downTime: Long) = ScreenOffKeyEvent(
        action = action,
        eventTimeNanos = downTime + 1,
        downTimeNanos = downTime,
        repeatCount = 0,
        interactive = action == ScreenOffKeyAction.UP,
    )
}
