package com.abdulkus.essentialremap

/** Collapses the same physical press when Accessibility and the shell bridge both report it. */
internal class PhysicalKeyEventGate(
    private val toleranceNanos: Long = DEFAULT_TOLERANCE_NANOS,
) {
    enum class DownResult { NEW, ACTIVE_DUPLICATE, COMPLETED_DUPLICATE }

    private var activeDownTimeNanos: Long? = null
    private var completedDownTimeNanos: Long? = null

    fun onDown(downTimeNanos: Long): DownResult {
        if (matches(activeDownTimeNanos, downTimeNanos)) return DownResult.ACTIVE_DUPLICATE
        if (matches(completedDownTimeNanos, downTimeNanos)) return DownResult.COMPLETED_DUPLICATE
        activeDownTimeNanos = downTimeNanos
        return DownResult.NEW
    }

    fun onUp(downTimeNanos: Long): Boolean {
        if (!matches(activeDownTimeNanos, downTimeNanos)) return false
        completedDownTimeNanos = activeDownTimeNanos
        activeDownTimeNanos = null
        return true
    }

    fun reset() {
        activeDownTimeNanos = null
        completedDownTimeNanos = null
    }

    private fun matches(value: Long?, candidate: Long): Boolean =
        value != null && kotlin.math.abs(value - candidate) <= toleranceNanos

    private companion object {
        const val DEFAULT_TOLERANCE_NANOS = 2_000_000L
    }
}
