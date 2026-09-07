package com.abdulkus.essentialremap.monitor;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import static org.junit.Assert.*;

public class MonitorGestureClassifierTest {
    private static final class Clock implements MonitorGestureClassifier.Scheduler {
        static final class Task implements Comparable<Task> {
            long at; long order; Runnable run; boolean cancelled;
            public int compareTo(Task other) { int time = Long.compare(at, other.at); return time != 0 ? time : Long.compare(order, other.order); }
        }
        long now = 10_000; long order;
        final PriorityQueue<Task> tasks = new PriorityQueue<>();
        public long nowMs() { return now; }
        public MonitorGestureClassifier.Cancel schedule(long delay, Runnable runnable) {
            Task task = new Task(); task.at = now + delay; task.order = order++; task.run = runnable; tasks.add(task);
            return () -> task.cancelled = true;
        }
        void advance(long delta) {
            long target = now + delta;
            while (!tasks.isEmpty() && tasks.peek().at <= target) {
                Task task = tasks.remove(); now = task.at; if (!task.cancelled) task.run.run();
            }
            now = target;
        }
    }
    private final Clock clock = new Clock();
    private final List<String> actions = new ArrayList<>();
    private final List<Long> groups = new ArrayList<>();
    private final MonitorGestureClassifier classifier = new MonitorGestureClassifier(clock, (kind, down, event) -> {
        if (!kind.equals("DOWN")) { actions.add(kind); groups.add(down); }
    }, 500, 300);
    private long ns(long ms) { return ms * 1_000_000; }

    @Test public void singleUsesReleaseWindow() {
        classifier.down(ns(10_000)); clock.advance(50); classifier.up(ns(10_050));
        clock.advance(299); assertTrue(actions.isEmpty()); clock.advance(1);
        assertEquals(List.of("SINGLE"), actions);
    }
    @Test public void secondDownCancelsSingleEvenWhenItsReleaseIsAfterWindow() {
        classifier.down(ns(10_000)); clock.advance(50); classifier.up(ns(10_050));
        clock.advance(250); classifier.down(ns(10_300)); clock.advance(200); classifier.up(ns(10_500));
        clock.advance(1000); assertEquals(List.of("DOUBLE"), actions);
        assertEquals(List.of(ns(10_000)), groups);
    }
    @Test public void bufferedShortPressCannotTurnIntoHold() {
        clock.now = 11_000; // Input was buffered; both timestamps still describe a 60ms tap.
        classifier.down(ns(10_000)); classifier.up(ns(10_060)); clock.advance(1000);
        assertEquals(List.of("SINGLE"), actions);
    }
    @Test public void bufferedLongPressUsesPhysicalDuration() {
        clock.now = 11_000;
        classifier.down(ns(10_000)); classifier.up(ns(10_580)); clock.advance(1000);
        assertEquals(List.of("LONG"), actions);
    }
    @Test public void bufferedDoubleKeepsBothPhysicalTimes() {
        clock.now = 11_000;
        classifier.down(ns(10_000)); classifier.up(ns(10_050));
        classifier.down(ns(10_200)); classifier.up(ns(10_240)); clock.advance(1000);
        assertEquals(List.of("DOUBLE"), actions);
    }
    @Test public void physicallySeparateBufferedTapsRemainTwoSingles() {
        clock.now = 11_000;
        classifier.down(ns(10_000)); classifier.up(ns(10_050));
        classifier.down(ns(10_600)); classifier.up(ns(10_650)); clock.advance(1000);
        assertEquals(List.of("SINGLE", "SINGLE"), actions);
    }
    @Test public void liveHoldFiresBeforeReleaseExactlyOnce() {
        classifier.down(ns(10_000)); clock.advance(500);
        assertEquals(List.of("LONG"), actions);
        clock.advance(5000); classifier.up(ns(15_500)); clock.advance(1000);
        assertEquals(List.of("LONG"), actions);
    }
    @Test public void secondPressHeldDoesNotAlsoFireFirstSingle() {
        classifier.down(ns(10_000)); classifier.up(ns(10_000));
        clock.advance(200); classifier.down(ns(10_200)); clock.advance(500);
        classifier.up(ns(10_700)); clock.advance(1000);
        assertEquals(List.of("LONG"), actions);
    }
    @Test public void inputResetCancelsEveryPendingAction() {
        classifier.down(ns(10_000)); classifier.reset(); clock.advance(1000);
        assertTrue(actions.isEmpty());
        classifier.down(ns(11_000)); classifier.up(ns(11_000)); classifier.reset(); clock.advance(1000);
        assertTrue(actions.isEmpty());
    }
    @Test public void missingReleaseDoesNotBreakNextPress() {
        classifier.down(ns(10_000)); clock.advance(1000);
        classifier.down(ns(11_000)); classifier.up(ns(11_000)); clock.advance(1000);
        assertEquals(List.of("LONG", "SINGLE"), actions);
    }
}
