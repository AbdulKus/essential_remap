package com.abdulkus.essentialremap

/** Shared by Accessibility and source-classified gestures when transport ownership changes. */
internal class GestureActionGate {
    private val claimed = ArrayDeque<Long>()

    fun claim(firstDownNs: Long, lastDownNs: Long): Boolean {
        if (firstDownNs <= 0 || lastDownNs <= 0) return false
        if (claimed.any { kotlin.math.abs(it - firstDownNs) <= 2_000_000L ||
                kotlin.math.abs(it - lastDownNs) <= 2_000_000L }) return false
        claimed.addLast(firstDownNs)
        if (lastDownNs != firstDownNs) claimed.addLast(lastDownNs)
        while (claimed.size > 16) claimed.removeFirst()
        return true
    }
}
