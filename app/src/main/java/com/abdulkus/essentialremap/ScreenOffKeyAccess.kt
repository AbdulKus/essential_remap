package com.abdulkus.essentialremap

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ScreenOffKeyAccess {
    const val BLOCK_SETTING = "nt_block_essential_key"

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    /**
     * A shell process cannot survive a reboot. BOOT_COUNT makes the UI stop claiming that sleep
     * handling is ready as soon as the device has restarted.
     */
    fun isGranted(context: Context): Boolean {
        val preferences = preferences(context)
        return preferences.getBoolean(KEY_STARTED, false) &&
            preferences.getInt(KEY_BOOT_COUNT, -1) == bootCount(context)
    }

    fun markStarted(context: Context) {
        preferences(context).edit()
            .putBoolean(KEY_STARTED, true)
            .putInt(KEY_BOOT_COUNT, bootCount(context))
            .apply()
        notifyChanged()
    }

    fun markStopped(context: Context) {
        preferences(context).edit().clear().apply()
        notifyChanged()
    }

    fun notifyChanged() {
        mutableChanges.tryEmit(Unit)
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    private fun bootCount(context: Context): Int = Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.BOOT_COUNT,
        -1,
    )

    private const val PREFERENCES = "shell_key_monitor"
    private const val KEY_STARTED = "started"
    private const val KEY_BOOT_COUNT = "boot_count"
}
