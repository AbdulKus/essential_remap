package com.abdulkus.essentialremap.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.TileService

data class QuickSettingsTileInfo(
    val packageName: String,
    val componentName: String,
    val appLabel: String,
    val tileLabel: String,
)

class QuickSettingsTilesReader(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    fun read(): List<QuickSettingsTileInfo> {
        val intent = Intent(TileService.ACTION_QS_TILE)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }

        return services.asSequence()
            .mapNotNull { resolve ->
                val service = resolve.serviceInfo ?: return@mapNotNull null
                if (!service.enabled || !service.exported) return@mapNotNull null
                val packageName = service.packageName ?: return@mapNotNull null
                val className = service.name ?: return@mapNotNull null
                val appLabel = runCatching {
                    service.applicationInfo.loadLabel(packageManager).toString()
                }.getOrNull().orEmpty().ifBlank { packageName }
                val tileLabel = runCatching { service.loadLabel(packageManager).toString() }
                    .getOrNull().orEmpty().ifBlank { appLabel }
                QuickSettingsTileInfo(
                    packageName = packageName,
                    componentName = ComponentName(packageName, className).flattenToString(),
                    appLabel = appLabel,
                    tileLabel = tileLabel,
                )
            }
            .distinctBy { it.componentName }
            .sortedWith(
                compareBy<QuickSettingsTileInfo> { it.appLabel.lowercase() }
                    .thenBy { it.tileLabel.lowercase() },
            )
            .toList()
    }
}
