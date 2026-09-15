package com.abdulkus.essentialremap

import android.content.Context
import android.provider.Settings
import com.abdulkus.essentialremap.setup.SetupAccessMode
import com.abdulkus.essentialremap.setup.ShellKeyMonitorCommands
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ScreenOffKeyAccess {
    const val BLOCK_SETTING = "nt_block_essential_key"

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    @Volatile var runtimeHealthy: Boolean = false
        private set

    fun setRuntimeHealthy(healthy: Boolean) {
        if (runtimeHealthy == healthy) return
        runtimeHealthy = healthy
        notifyChanged()
    }

    /**
     * A privileged helper cannot survive a reboot. The boot count and script revision keep the UI
     * from claiming sleep handling is ready after a restart or an app update that replaces it.
     */
    fun isGranted(context: Context): Boolean {
        return runtimeHealthy && isConfiguredForThisBoot(context)
    }

    fun isGrantedFor(context: Context, accessMode: SetupAccessMode): Boolean =
        isGranted(context) && configuredAccessMode(context) == accessMode

    fun isConfiguredForThisBoot(context: Context): Boolean {
        val preferences = preferences(context)
        return preferences.getBoolean(KEY_STARTED, false) &&
            preferences.getInt(KEY_BOOT_COUNT, -1) == bootCount(context) &&
            preferences.getInt(KEY_MONITOR_REVISION, -1) == ShellKeyMonitorCommands.REVISION &&
            preferences.getInt(KEY_COMMAND_CAPABILITY_REVISION, -1) == COMMAND_CAPABILITY_REVISION
    }

    fun wasConfigured(context: Context): Boolean =
        preferences(context).getBoolean(KEY_STARTED, false)

    fun configuredAccessMode(context: Context): SetupAccessMode? {
        val preferences = preferences(context)
        if (!preferences.getBoolean(KEY_STARTED, false)) return null
        return SetupAccessMode.fromStored(preferences.getString(KEY_ACCESS_MODE, null))
    }

    fun markStarted(context: Context, accessMode: SetupAccessMode = SetupAccessMode.NON_ROOT) {
        preferences(context).edit()
            .putBoolean(KEY_STARTED, true)
            .putInt(KEY_BOOT_COUNT, bootCount(context))
            .putInt(KEY_MONITOR_REVISION, ShellKeyMonitorCommands.REVISION)
            .putInt(KEY_COMMAND_CAPABILITY_REVISION, COMMAND_CAPABILITY_REVISION)
            .putString(KEY_ACCESS_MODE, accessMode.name)
            .apply()
        notifyChanged()
    }

    fun markStopped(context: Context) {
        runtimeHealthy = false
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
    private const val KEY_MONITOR_REVISION = "monitor_revision"
    private const val KEY_COMMAND_CAPABILITY_REVISION = "command_capability_revision"
    private const val KEY_ACCESS_MODE = "access_mode"
    private const val COMMAND_CAPABILITY_REVISION = 1
}
