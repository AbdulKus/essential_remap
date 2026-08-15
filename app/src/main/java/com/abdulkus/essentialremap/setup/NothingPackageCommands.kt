package com.abdulkus.essentialremap.setup

enum class PackageOperation {
    DISABLE,
    INSTALL_SLEEP_MONITOR,
    RESTORE,
}

object NothingPackageCommands {
    const val ESSENTIAL_SPACE = "com.nothing.ntessentialspace"
    const val ESSENTIAL_RECORDER = "com.nothing.ntessentialrecorder"

    val packages = listOf(ESSENTIAL_SPACE, ESSENTIAL_RECORDER)

    fun commands(operation: PackageOperation): List<String> = packages.map { packageName ->
        when (operation) {
            PackageOperation.DISABLE,
            PackageOperation.INSTALL_SLEEP_MONITOR,
            -> "pm disable-user --user 0 $packageName"
            PackageOperation.RESTORE -> "pm enable --user 0 $packageName"
        }
    }
}

object EssentialKeySetupCommands {
    const val COMMAND_OK = "essential-remap:ok"
    const val ENABLE_RELIABLE_SCREEN_OFF_DISPATCH =
        "settings put secure nt_block_essential_key 1 && echo $COMMAND_OK"
    const val READ_SCREEN_OFF_WAKE_SETTING = "settings get secure nt_block_essential_key"

    fun commands(operation: PackageOperation): List<String> = buildList {
        when (operation) {
            PackageOperation.DISABLE -> {
                addAll(NothingPackageCommands.commands(operation))
            }
            PackageOperation.INSTALL_SLEEP_MONITOR -> {
                // Re-applying package disable is intentional and makes restart/install idempotent.
                addAll(NothingPackageCommands.commands(operation))
                add(ENABLE_RELIABLE_SCREEN_OFF_DISPATCH)
                add(ShellKeyMonitorCommands.INSTALL)
            }
            PackageOperation.RESTORE -> {
                add(ShellKeyMonitorCommands.stop)
                addAll(NothingPackageCommands.commands(operation))
            }
        }
    }

    fun isAllowlisted(command: String): Boolean =
        PackageOperation.entries.any { command in commands(it) }
}
