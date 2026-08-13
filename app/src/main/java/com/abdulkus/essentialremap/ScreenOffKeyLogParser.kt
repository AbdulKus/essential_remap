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

/** Parses only Nothing's Essential Key entries emitted by WindowManager. */
internal object ScreenOffKeyLogParser {
    private val actionPattern = Regex("action=(ACTION_DOWN|ACTION_UP)")
    private val scanCodePattern = Regex("scanCode=(\\d+)")
    private val eventTimePattern = Regex("eventTime=(\\d+)")
    private val downTimePattern = Regex("downTime=(\\d+)")
    private val repeatCountPattern = Regex("repeatCount=(\\d+)")
    private val interactivePattern = Regex("interactive=(true|false)")

    fun parse(line: String): ScreenOffKeyEvent? {
        if (!line.contains("interceptKeyBeforeQueueing")) return null
        if (scanCodePattern.find(line)?.groupValues?.get(1)?.toIntOrNull() != ESSENTIAL_SCAN_CODE) {
            return null
        }
        val action = when (actionPattern.find(line)?.groupValues?.get(1)) {
            "ACTION_DOWN" -> ScreenOffKeyAction.DOWN
            "ACTION_UP" -> ScreenOffKeyAction.UP
            else -> return null
        }
        return ScreenOffKeyEvent(
            action = action,
            eventTimeNanos = eventTimePattern.find(line)?.groupValues?.get(1)?.toLongOrNull()
                ?: return null,
            downTimeNanos = downTimePattern.find(line)?.groupValues?.get(1)?.toLongOrNull()
                ?: return null,
            repeatCount = repeatCountPattern.find(line)?.groupValues?.get(1)?.toIntOrNull()
                ?: return null,
            interactive = interactivePattern.find(line)?.groupValues?.get(1)?.toBooleanStrictOrNull()
                ?: return null,
        )
    }

    private const val ESSENTIAL_SCAN_CODE = 250
}
