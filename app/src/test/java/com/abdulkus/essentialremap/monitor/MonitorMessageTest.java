package com.abdulkus.essentialremap.monitor;

import org.junit.Test;
import static org.junit.Assert.*;

public class MonitorMessageTest {
    private final MonitorMessage message = new MonitorMessage("0123456789abcdef0123456789abcdef", 2, "SINGLE", 1000000000L, 1100000000L, 5000);
    @Test public void roundTripPreservesIdentityAndPhysicalTimes() {
        MonitorMessage restored = MonitorMessage.parse(message.encode());
        assertEquals(message.encode(), restored.encode()); assertTrue(restored.isGesture());
    }
    @Test public void ignoresEventsDelayedInSuspendEvenWhenUptimeDidNotMove() {
        assertTrue(message.fresh(5100, 1200));
        assertFalse(message.fresh(8000, 1200));
    }
    @Test public void ignoresAlreadyOldInputDespiteFreshTransport() {
        assertFalse(message.fresh(5001, 6000));
    }
    @Test public void rejectsWrongRevisionNegativeTimesUnknownKindsAndTrailingData() {
        String[] invalid = { message.encode().replaceFirst("9 ", "8 "), message.encode() + " x",
            message.encode().replace("SINGLE", "SHELL"), message.encode().replace("1100000000", "-1"),
            message.encode().replace("0123456789abcdef0123456789abcdef", "../bad") };
        for (String line : invalid) {
            try { MonitorMessage.parse(line); fail(line); } catch (IllegalArgumentException expected) { }
        }
    }
}
