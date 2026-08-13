package com.abdulkus.essentialremap.domain

enum class PressAction {
    SINGLE,
    DOUBLE,
    LONG,
}

enum class RequestMethod {
    GET,
    POST,
}

enum class HapticStrength {
    OFF,
    LIGHT,
    MEDIUM,
    STRONG,
}

enum class SoundMode {
    NORMAL,
    VIBRATE,
    SILENT,
    TOGGLE_SILENT_NORMAL,
}

enum class SystemAction {
    CIRCLE_TO_SEARCH,
    SCREENSHOT,
    LOCK_SCREEN,
    POWER_MENU,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    HOME,
    BACK,
    RECENTS,
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    CAMERA,
    ASSISTANT,
}

enum class ActionKind {
    NONE,
    HTTP,
    FLASHLIGHT,
    SOUND_MODE,
    TOGGLE_SILENT,
    LAUNCH_APP,
    OPEN_URL,
    SYSTEM,
}

sealed interface ConfiguredAction {
    val kind: ActionKind

    data object None : ConfiguredAction {
        override val kind = ActionKind.NONE
    }

    data class Http(
        val baseUrl: String = DEFAULT_BASE_URL,
        val method: RequestMethod = RequestMethod.GET,
        val endpoint: String = "",
    ) : ConfiguredAction {
        override val kind = ActionKind.HTTP

        companion object {
            const val DEFAULT_BASE_URL = "http://192.168.0.108"
        }
    }

    data object Flashlight : ConfiguredAction {
        override val kind = ActionKind.FLASHLIGHT
    }

    data class SetSoundMode(val mode: SoundMode = SoundMode.SILENT) : ConfiguredAction {
        override val kind = ActionKind.SOUND_MODE
    }

    data object ToggleSilent : ConfiguredAction {
        override val kind = ActionKind.TOGGLE_SILENT
    }

    data class LaunchApp(
        val packageName: String = "",
        val label: String = "",
    ) : ConfiguredAction {
        override val kind = ActionKind.LAUNCH_APP
    }

    data class OpenUrl(val url: String = "") : ConfiguredAction {
        override val kind = ActionKind.OPEN_URL
    }

    data class PerformSystemAction(
        val action: SystemAction = SystemAction.SCREENSHOT,
    ) : ConfiguredAction {
        override val kind = ActionKind.SYSTEM
    }
}

data class HttpRequestSettings(
    val method: RequestMethod = RequestMethod.GET,
    val url: String = "",
)

data class KeyIdentity(
    val keyCode: Int,
    val scanCode: Int,
    val source: Int,
    val deviceId: Int,
    val descriptor: String,
    val vendorId: Int,
    val productId: Int,
) {
    fun matches(other: KeyIdentity): Boolean {
        if (keyCode != other.keyCode) return false
        if (scanCode != 0 || other.scanCode != 0) {
            return scanCode == other.scanCode && source == other.source
        }
        if (descriptor.isNotBlank() && other.descriptor.isNotBlank()) {
            return descriptor == other.descriptor && source == other.source
        }
        return deviceId == other.deviceId && source == other.source
    }

}

data class AppSettings(
    val remappingEnabled: Boolean = true,
    val mappedKey: KeyIdentity? = null,
    val hapticStrength: HapticStrength = HapticStrength.MEDIUM,
    val actions: Map<PressAction, ConfiguredAction> = defaultActions(),
    val runWhileLocked: Map<PressAction, Boolean> = defaultRunWhileLocked(),
    val turnScreenOffAfterWake: Map<PressAction, Boolean> = defaultTurnScreenOffAfterWake(),
    val results: Map<PressAction, String?> = PressAction.entries.associateWith { null },
    val learning: Boolean = false,
) {
    companion object {
        fun defaultActions(): Map<PressAction, ConfiguredAction> =
            mapOf(
                PressAction.SINGLE to ConfiguredAction.PerformSystemAction(SystemAction.ASSISTANT),
                PressAction.DOUBLE to ConfiguredAction.PerformSystemAction(SystemAction.CIRCLE_TO_SEARCH),
                PressAction.LONG to ConfiguredAction.Flashlight,
            )

        fun defaultRunWhileLocked(): Map<PressAction, Boolean> =
            PressAction.entries.associateWith { false }

        fun defaultTurnScreenOffAfterWake(): Map<PressAction, Boolean> =
            PressAction.entries.associateWith { false }
    }
}

object ActionFeedbackPolicy {
    fun shouldPerformHaptic(action: ConfiguredAction): Boolean = action != ConfiguredAction.None
}

object LockScreenExecutionPolicy {
    fun shouldRun(
        action: PressAction,
        startedWhileLocked: Boolean,
        runWhileLocked: Map<PressAction, Boolean>,
    ): Boolean = !startedWhileLocked || runWhileLocked[action] == true
}

object ScreenOffAfterWakePolicy {
    fun shouldTurnOff(
        action: PressAction,
        startedScreenOff: Boolean,
        turnScreenOffAfterWake: Map<PressAction, Boolean>,
    ): Boolean = startedScreenOff && turnScreenOffAfterWake[action] == true
}

object ActionUrlResolver {
    fun resolve(baseUrl: String, endpoint: String): String {
        val trimmedEndpoint = endpoint.trim()
        if (trimmedEndpoint.isBlank()) return ""
        if (trimmedEndpoint.startsWith("http://") || trimmedEndpoint.startsWith("https://")) {
            return trimmedEndpoint
        }
        return "${baseUrl.trim().trimEnd('/')}/${trimmedEndpoint.trimStart('/')}"
    }
}
