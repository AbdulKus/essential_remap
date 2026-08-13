package com.abdulkus.essentialremap.setup

import android.content.Context
import android.content.pm.PackageManager

enum class NothingPackageStatus {
    ENABLED,
    DISABLED,
    PARTIAL,
    UNSUPPORTED,
    UNKNOWN,
}

class NothingPackageStatusReader(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun read(): NothingPackageStatus {
        val states = NothingPackageCommands.packages.map(::readEnabled)
        val present = states.filterNotNull()
        return when {
            present.isEmpty() -> NothingPackageStatus.UNSUPPORTED
            present.size != NothingPackageCommands.packages.size -> NothingPackageStatus.PARTIAL
            present.all { it } -> NothingPackageStatus.ENABLED
            present.none { it } -> NothingPackageStatus.DISABLED
            else -> NothingPackageStatus.PARTIAL
        }
    }

    private fun readEnabled(packageName: String): Boolean? = try {
        when (packageManager.getApplicationEnabledSetting(packageName)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> packageManager.getApplicationInfo(
                packageName,
                PackageManager.MATCH_DISABLED_COMPONENTS,
            ).enabled
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
