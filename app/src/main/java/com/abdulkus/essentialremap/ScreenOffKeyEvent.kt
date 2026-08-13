package com.abdulkus.essentialremap

internal enum class ScreenOffKeyAction {
    DOWN,
    UP,
}

internal data class ScreenOffKeyEvent(
    val action: ScreenOffKeyAction,
    val eventTimeNanos: Long,
    val downTimeNanos: Long,
    val repeatCount: Int,
    val interactive: Boolean,
)
