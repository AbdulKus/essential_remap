package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.domain.PressAction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.PriorityQueue

class GestureClassifierTest {
    @Test
    fun singlePressFiresAfterDoublePressWindow() {
        val scheduler = FakeScheduler()
        val actions = mutableListOf<PressAction>()
        val classifier = GestureClassifier(scheduler, actions::add)

        classifier.onKeyDown(0)
        scheduler.advanceBy(100)
        classifier.onKeyUp(false)
        scheduler.advanceBy(299)
        assertEquals(emptyList<PressAction>(), actions)
        scheduler.advanceBy(1)
        assertEquals(listOf(PressAction.SINGLE), actions)
    }

    @Test
    fun twoShortPressesFireDoubleOnly() {
        val scheduler = FakeScheduler()
        val actions = mutableListOf<PressAction>()
        val classifier = GestureClassifier(scheduler, actions::add)

        classifier.onKeyDown(0)
        classifier.onKeyUp(false)
        scheduler.advanceBy(100)
        classifier.onKeyDown(0)
        classifier.onKeyUp(false)
        scheduler.advanceBy(500)

        assertEquals(listOf(PressAction.DOUBLE), actions)
    }

    @Test
    fun holdFiresLongOnceAndIgnoresRepeatsAndRelease() {
        val scheduler = FakeScheduler()
        val actions = mutableListOf<PressAction>()
        val classifier = GestureClassifier(scheduler, actions::add)

        classifier.onKeyDown(0)
        classifier.onKeyDown(1)
        classifier.onKeyDown(2)
        scheduler.advanceBy(600)
        classifier.onKeyUp(false)
        scheduler.advanceBy(500)

        assertEquals(listOf(PressAction.LONG), actions)
    }

    @Test
    fun canceledPressDoesNotFire() {
        val scheduler = FakeScheduler()
        val actions = mutableListOf<PressAction>()
        val classifier = GestureClassifier(scheduler, actions::add)

        classifier.onKeyDown(0)
        classifier.onKeyUp(true)
        scheduler.advanceBy(1_000)

        assertEquals(emptyList<PressAction>(), actions)
    }

    private class FakeScheduler : GestureClassifier.Scheduler {
        private data class Task(val at: Long, val order: Long, val action: () -> Unit, var canceled: Boolean = false)

        private var now = 0L
        private var order = 0L
        private val tasks = PriorityQueue<Task>(compareBy<Task> { it.at }.thenBy { it.order })

        override fun schedule(delayMs: Long, task: () -> Unit): GestureClassifier.Cancellable {
            val scheduled = Task(now + delayMs, order++, task)
            tasks += scheduled
            return object : GestureClassifier.Cancellable {
                override fun cancel() {
                    scheduled.canceled = true
                }
            }
        }

        fun advanceBy(duration: Long) {
            val target = now + duration
            while (tasks.isNotEmpty() && tasks.peek().at <= target) {
                val task = tasks.remove()
                now = task.at
                if (!task.canceled) task.action()
            }
            now = target
        }
    }
}
