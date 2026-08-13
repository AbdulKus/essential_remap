package com.abdulkus.essentialremap.setup

enum class PackageOperation {
    DISABLE,
    RESTORE,
}

object NothingPackageCommands {
    const val ESSENTIAL_SPACE = "com.nothing.ntessentialspace"
    const val ESSENTIAL_RECORDER = "com.nothing.ntessentialrecorder"

    val packages = listOf(ESSENTIAL_SPACE, ESSENTIAL_RECORDER)

    fun commands(operation: PackageOperation): List<String> = packages.map { packageName ->
        when (operation) {
            PackageOperation.DISABLE -> "pm disable-user --user 0 $packageName"
            PackageOperation.RESTORE -> "pm enable --user 0 $packageName"
        }
    }
}

object EssentialKeySetupCommands {
    const val APP_PACKAGE = "com.abdulkus.essentialremap"
    const val COMMAND_OK = "essential-remap:ok"
    const val GRANT_READ_LOGS =
        "pm grant $APP_PACKAGE android.permission.READ_LOGS && echo $COMMAND_OK"
    const val BLOCK_SCREEN_OFF_WAKE =
        "settings put secure nt_block_essential_key 1 && echo $COMMAND_OK"
    const val READ_SCREEN_OFF_WAKE_SETTING = "settings get secure nt_block_essential_key"

    fun commands(operation: PackageOperation): List<String> = buildList {
        addAll(NothingPackageCommands.commands(operation))
        if (operation == PackageOperation.DISABLE) {
            add(GRANT_READ_LOGS)
            add(BLOCK_SCREEN_OFF_WAKE)
        }
    }

    fun isAllowlisted(command: String): Boolean =
        PackageOperation.entries.any { command in commands(it) }
}
