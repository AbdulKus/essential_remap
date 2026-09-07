package com.abdulkus.essentialremap.monitor;

/** Runs next to the input reader. Transport latency never determines the gesture. */
public final class MonitorGestureClassifier {
    public interface Cancel { void cancel(); }
    public interface Scheduler {
        long nowMs();
        Cancel schedule(long delayMs, Runnable task);
    }
    public interface Listener {
        void emit(String kind, long sequenceDownNs, long eventNs);
    }
    private final Scheduler scheduler;
    private final Listener listener;
    private final long longMs;
    private final long doubleMs;
    private long downNs;
    private long sequenceNs;
    private long firstUpNs;
    private boolean pressed;
    private boolean longFired;
    private boolean waitingSecond;
    private Cancel hold;
    private Cancel single;

    public MonitorGestureClassifier(Scheduler scheduler, Listener listener, long longMs, long doubleMs) {
        this.scheduler = scheduler;
        this.listener = listener;
        this.longMs = longMs;
        this.doubleMs = doubleMs;
    }

    public synchronized void down(long timeNs) {
        if (pressed && downNs == timeNs) return;
        if (pressed) reset(); // Missing release must not poison subsequent presses.
        if (waitingSecond && timeNs - firstUpNs > doubleMs * 1_000_000L) {
            fireSingle();
        }
        if (!waitingSecond) sequenceNs = timeNs;
        cancelSingle(); // A second DOWN inside the window wins over the single timer.
        pressed = true;
        longFired = false;
        downNs = timeNs;
        listener.emit("DOWN", sequenceNs, timeNs);
        // Give an already buffered UP a chance to be read before an overdue timer runs.
        hold = scheduler.schedule(Math.max(25, timeNs / 1_000_000L + longMs - scheduler.nowMs()), () -> {
            synchronized (MonitorGestureClassifier.this) {
                if (pressed && !longFired) fireLong(downNs + longMs * 1_000_000L);
            }
        });
    }

    public synchronized void up(long timeNs) {
        if (!pressed || timeNs < downNs) return;
        cancelHold();
        pressed = false;
        if (!longFired && timeNs - downNs >= longMs * 1_000_000L) fireLong(timeNs);
        if (longFired) return;
        if (waitingSecond) {
            waitingSecond = false;
            listener.emit("DOUBLE", sequenceNs, timeNs);
        } else {
            waitingSecond = true;
            firstUpNs = timeNs;
            single = scheduler.schedule(Math.max(25, timeNs / 1_000_000L + doubleMs - scheduler.nowMs()), () -> {
                synchronized (MonitorGestureClassifier.this) {
                    if (waitingSecond && !pressed) fireSingle();
                }
            });
        }
    }

    private void fireSingle() {
        cancelSingle();
        waitingSecond = false;
        listener.emit("SINGLE", sequenceNs, firstUpNs);
    }

    private void fireLong(long timeNs) {
        longFired = true;
        waitingSecond = false;
        cancelSingle();
        listener.emit("LONG", sequenceNs, timeNs);
    }

    public synchronized void reset() {
        cancelHold();
        cancelSingle();
        pressed = false;
        longFired = false;
        waitingSecond = false;
        downNs = sequenceNs = firstUpNs = 0;
    }

    private void cancelHold() { if (hold != null) hold.cancel(); hold = null; }
    private void cancelSingle() { if (single != null) single.cancel(); single = null; }
}
