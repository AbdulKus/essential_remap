package com.abdulkus.essentialremap.platform

import android.content.Context
import android.content.Intent

data class LaunchableApp(
    val packageName: String,
    val label: String,
)

class LaunchableAppsReader(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun read(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { info ->
                LaunchableApp(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(packageManager).toString(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
