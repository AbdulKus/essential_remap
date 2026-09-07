package com.abdulkus.essentialremap.monitor;

/** Small, versioned protocol shared by the shell process and the app. */
public final class MonitorMessage {
    public static final int REVISION = 9;
    public static final String SOCKET = "com.abdulkus.essentialremap.monitor.v9";
    public static final long MAX_AGE_MS = 2_000;
    public final String session;
    public final long number;
    public final String kind;
    public final long downNs;
    public final long eventNs;
    public final long emittedElapsedMs;

    public MonitorMessage(String session, long number, String kind, long downNs, long eventNs, long emittedElapsedMs) {
        this.session = session;
        this.number = number;
        this.kind = kind;
        this.downNs = downNs;
        this.eventNs = eventNs;
        this.emittedElapsedMs = emittedElapsedMs;
    }

    public boolean isGesture() { return kind.equals("SINGLE") || kind.equals("DOUBLE") || kind.equals("LONG"); }

    public boolean fresh(long elapsedMs, long uptimeMs) {
        long transit = elapsedMs - emittedElapsedMs;
        long inputAge = uptimeMs - eventNs / 1_000_000L;
        return transit >= 0 && transit <= MAX_AGE_MS &&
            (kind.equals("READY") || kind.equals("RESET") || (inputAge >= -100 && inputAge <= MAX_AGE_MS));
    }

    public String encode() {
        return REVISION + " " + session + " " + number + " " + kind + " " + downNs + " " + eventNs + " " + emittedElapsedMs;
    }

    public static MonitorMessage parse(String line) {
        if (line == null || line.length() > 240) throw new IllegalArgumentException("message length");
        String[] fields = line.split(" ", -1);
        if (fields.length != 7 || !fields[0].equals(Integer.toString(REVISION)) ||
            !fields[1].matches("[a-f0-9]{32}") || !fields[3].matches("DOWN|SINGLE|DOUBLE|LONG|READY|RESET")) {
            throw new IllegalArgumentException("protocol");
        }
        MonitorMessage message = new MonitorMessage(fields[1], Long.parseLong(fields[2]), fields[3],
            Long.parseLong(fields[4]), Long.parseLong(fields[5]), Long.parseLong(fields[6]));
        if (message.number <= 0 || message.downNs < 0 || message.eventNs < message.downNs ||
            message.emittedElapsedMs < 0 ||
            ((!message.kind.equals("READY") && !message.kind.equals("RESET")) && message.downNs == 0)) {
            throw new IllegalArgumentException("values");
        }
        return message;
    }
}
