package com.abdulkus.essentialremap.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class LaunchableApp(
    val packageName: String,
    val label: String,
)

data class LaunchableActivity(
    val packageName: String,
    val componentName: String,
    val appLabel: String,
    val activityLabel: String,
)

class LaunchableAppsReader(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

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

    @Suppress("DEPRECATION")
    fun readActivities(packageName: String): List<LaunchableActivity> {
        val packageInfo = runCatching {
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        }.getOrNull() ?: return emptyList()
        val appLabel = runCatching {
            packageInfo.applicationInfo?.loadLabel(packageManager)?.toString()
        }.getOrNull().orEmpty().ifBlank { packageName }

        return packageInfo.activities.orEmpty()
            .asSequence()
            .filter { info ->
                info.exported && info.enabled && (
                    info.permission.isNullOrBlank() ||
                        packageManager.checkPermission(info.permission, appContext.packageName) == PackageManager.PERMISSION_GRANTED
                    )
            }
            .map { info ->
                LaunchableActivity(
                    packageName = info.packageName,
                    componentName = ComponentName(info.packageName, info.name).flattenToString(),
                    appLabel = appLabel,
                    activityLabel = info.loadLabel(packageManager).toString().ifBlank { info.name.substringAfterLast('.') },
                )
            }
            .distinctBy { it.componentName }
            .sortedWith(compareBy<LaunchableActivity> { it.activityLabel.lowercase() }.thenBy { it.componentName })
            .toList()
    }
}
