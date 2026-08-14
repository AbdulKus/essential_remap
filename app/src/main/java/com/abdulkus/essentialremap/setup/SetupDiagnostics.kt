package com.abdulkus.essentialremap.setup

import android.content.Context
import android.os.Build
import android.os.Process
import com.abdulkus.essentialremap.ScreenOffKeyAccess
import java.io.File
import java.time.Instant

class SetupDiagnostics(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val lock = Any()

    fun log(message: String) {
        runCatching {
            synchronized(lock) {
                if (file.length() > MAX_BYTES) file.delete()
                file.appendText("${Instant.now()}  $message\n")
            }
        }
    }

    fun report(): String = runCatching {
        synchronized(lock) {
            val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            buildString {
                appendLine("Essential Remap diagnostics")
                appendLine("App: ${packageInfo.versionName} ($versionCode)")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Build: ${Build.FINGERPRINT}")
                appendLine("Process: ${Process.myPid()}")
                appendLine("Nothing packages: ${NothingPackageStatusReader(appContext).read()}")
                appendLine("Screen-off marker: ${ScreenOffKeyAccess.isGranted(appContext)}")
                appendLine("ADB install service: ${ShellKeyMonitorCommands.INSTALL_SERVICE}")
                appendLine("Expected monitor: ${ShellKeyMonitorCommands.RUNNING_CONFIRMATION}")
                appendLine("Stored log bytes: ${file.takeIf(File::exists)?.length() ?: 0}")
                appendLine("Pairing codes and ADB private keys are not logged.")
                appendLine("---")
                append(file.takeIf(File::exists)?.readText().orEmpty())
            }
        }
    }.getOrElse { "Could not read diagnostics: ${it.message}" }

    fun clear() {
        runCatching {
            synchronized(lock) { file.delete() }
        }
    }

    private companion object {
        const val FILE_NAME = "essential_key_setup.log"
        const val MAX_BYTES = 128 * 1024L
    }
}
