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
