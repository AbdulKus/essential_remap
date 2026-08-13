package com.abdulkus.essentialremap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenOffKeyLogParserTest {
    @Test
    fun parsesRealScreenOffDownEvent() {
        val event = ScreenOffKeyLogParser.parse(DOWN_LINE)!!

        assertEquals(ScreenOffKeyAction.DOWN, event.action)
        assertEquals(1_206_055_708_000L, event.eventTimeNanos)
        assertEquals(1_206_055_708_000L, event.downTimeNanos)
        assertEquals(0, event.repeatCount)
        assertFalse(event.interactive)
    }

    @Test
    fun parsesRealScreenOffUpEventAndPreservesDuration() {
        val event = ScreenOffKeyLogParser.parse(UP_LINE)!!

        assertEquals(ScreenOffKeyAction.UP, event.action)
        assertEquals(178_635_000L, event.eventTimeNanos - event.downTimeNanos)
    }

    @Test
    fun ignoresPowerButtonAndOtherScanCodes() {
        assertNull(ScreenOffKeyLogParser.parse(DOWN_LINE.replace("scanCode=250", "scanCode=116")))
        assertNull(ScreenOffKeyLogParser.parse("I/PowerManagerService: Waking up from Dozing"))
    }

    private companion object {
        const val DOWN_LINE =
            "D/WindowManager: interceptKeyBeforeQueueing event=KeyEvent { action=ACTION_DOWN, " +
                "keyCode=KEYCODE_UNKNOWN, scanCode=250, metaState=0, flags=0x8, repeatCount=0, " +
                "eventTime=1206055708000, downTime=1206055708000, deviceId=2, source=0x101, " +
                "displayId=-1 }, interactive=false, keyguardActive=true, policyFlags=2000000"
        const val UP_LINE =
            "D/WindowManager: interceptKeyBeforeQueueing event=KeyEvent { action=ACTION_UP, " +
                "keyCode=KEYCODE_UNKNOWN, scanCode=250, metaState=0, flags=0x8, repeatCount=0, " +
                "eventTime=1206234343000, downTime=1206055708000, deviceId=2, source=0x101, " +
                "displayId=-1 }, interactive=false, keyguardActive=true, policyFlags=2000000"
    }
}
