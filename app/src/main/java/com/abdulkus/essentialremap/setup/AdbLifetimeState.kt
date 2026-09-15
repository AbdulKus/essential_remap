package com.abdulkus.essentialremap.setup

import android.content.Context
import android.provider.Settings

/** Read-only snapshot: never enables debugging or changes the user's ADB authorization. */
data class AdbLifetimeState(val usb: Int, val wireless: Int) {
    val usbEnabled: Boolean get() = usb == 1

    companion object {
        fun read(context: Context): AdbLifetimeState {
            fun setting(name: String): Int = runCatching {
                Settings.Global.getInt(context.contentResolver, name, -1)
            }.getOrDefault(-1)
            return AdbLifetimeState(setting(Settings.Global.ADB_ENABLED), setting("adb_wifi_enabled"))
        }
    }
}
