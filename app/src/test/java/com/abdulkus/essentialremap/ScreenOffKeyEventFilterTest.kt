package com.abdulkus.essentialremap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOffKeyEventFilterTest {
    @Test
    fun forwardsUpThatBecameInteractiveAfterNothingWokeTheDisplay() {
        val filter = ScreenOffKeyEventFilter(startedAtNanos = 1_000L)

        assertTrue(filter.accept(event(ScreenOffKeyAction.DOWN, interactive = false)))
        assertTrue(filter.accept(event(ScreenOffKeyAction.UP, interactive = true, eventTime = 1_200L)))
    }

    @Test
    fun forwardsCompletePressWhenDisplayStaysOff() {
        val filter = ScreenOffKeyEventFilter(startedAtNanos = 1_000L)

        assertTrue(filter.accept(event(ScreenOffKeyAction.DOWN, interactive = false)))
        assertTrue(filter.accept(event(ScreenOffKeyAction.UP, interactive = false, eventTime = 1_200L)))
    }

    @Test
    fun ignoresPressThatStartedWhileDisplayWasInteractive() {
        val filter = ScreenOffKeyEventFilter(startedAtNanos = 1_000L)

        assertFalse(filter.accept(event(ScreenOffKeyAction.DOWN, interactive = true)))
        assertFalse(filter.accept(event(ScreenOffKeyAction.UP, interactive = true, eventTime = 1_200L)))
    }

    @Test
    fun ignoresBufferedEventFromBeforeReaderStarted() {
        val filter = ScreenOffKeyEventFilter(startedAtNanos = 2_000L)

        assertFalse(filter.accept(event(ScreenOffKeyAction.DOWN, interactive = false)))
    }

    private fun event(
        action: ScreenOffKeyAction,
        interactive: Boolean,
        eventTime: Long = 1_100L,
    ) = ScreenOffKeyEvent(
        action = action,
        eventTimeNanos = eventTime,
        downTimeNanos = 1_100L,
        repeatCount = 0,
        interactive = interactive,
    )
}
