package com.abdulkus.essentialremap.setup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.abdulkus.essentialremap.EssentialKeyApplication
import com.abdulkus.essentialremap.MainActivity
import com.abdulkus.essentialremap.R
import com.abdulkus.essentialremap.ScreenOffKeyAccess
import com.abdulkus.essentialremap.ui.AppLanguage
import com.abdulkus.essentialremap.ui.UserPreferences
import com.abdulkus.essentialremap.ui.translate

class SleepMonitorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = UserPreferences(context)
        if (!preferences.screenOffEnabled) return

        val configuredAccessMode =
            ScreenOffKeyAccess.configuredAccessMode(context) ?: preferences.setupAccessMode
        ScreenOffKeyAccess.markStopped(context)
        if (configuredAccessMode != SetupAccessMode.ROOT) {
            postReminder(context, preferences.language, rootMode = false)
            return
        }

        val pending = goAsync()
        Thread({
            val diagnostics = SetupDiagnostics(context)
            runCatching {
                diagnostics.log("Boot: starting sleep monitor through root")
                RootCommandExecutor.requireRoot(ROOT_BOOT_AUTH_TIMEOUT_MS)
                val setting = RootCommandExecutor.execute(
                    EssentialKeySetupCommands.ENABLE_RELIABLE_SCREEN_OFF_DISPATCH,
                    ROOT_BOOT_COMMAND_TIMEOUT_MS,
                )
                check(setting.contains(EssentialKeySetupCommands.COMMAND_OK)) { "Root boot setup failed" }
                val output = RootCommandExecutor.execute(
                    ShellKeyMonitorCommands.installAndStart,
                    ROOT_BOOT_MONITOR_TIMEOUT_MS,
                )
                check(output.contains(ShellKeyMonitorCommands.START_CONFIRMATION)) {
                    "Root boot monitor did not confirm startup"
                }
                RootCommandExecutor.execute(
                    ShellKeyMonitorCommands.handoffFilesToShell,
                    ROOT_BOOT_COMMAND_TIMEOUT_MS,
                )
                ScreenOffKeyAccess.markStarted(context, SetupAccessMode.ROOT)
                cancelReminder(context)
                (context.applicationContext as? EssentialKeyApplication)?.container?.shellBridge?.requestConnect()
                diagnostics.log("Boot: root sleep monitor started")
            }.onFailure { error ->
                diagnostics.log("Boot: root auto-start failed: ${error.javaClass.simpleName}: ${error.message}")
                postReminder(context, preferences.language, rootMode = true)
            }
            pending.finish()
        }, "essential-root-boot").apply { isDaemon = true }.start()
    }

    companion object {
        private const val CHANNEL_ID = "essential_remap_sleep_monitor"
        private const val NOTIFICATION_ID = 2054
        private const val ROOT_BOOT_AUTH_TIMEOUT_MS = 4_000L
        private const val ROOT_BOOT_COMMAND_TIMEOUT_MS = 4_000L
        private const val ROOT_BOOT_MONITOR_TIMEOUT_MS = 8_000L

        fun cancelReminder(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        private fun postReminder(context: Context, language: AppLanguage?, rootMode: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return

            val selectedLanguage = language ?: AppLanguage.ENGLISH
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    selectedLanguage.translate("Essential Remap sleep monitor", "Монитор сна Essential Remap"),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val contentIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val title = if (rootMode) {
                selectedLanguage.translate("Root sleep monitor needs attention", "Root-монитор сна требует внимания")
            } else selectedLanguage.translate("Restart the sleep monitor", "Перезапустите монитор сна")
            val shortText = if (rootMode) {
                selectedLanguage.translate("Automatic root start failed after reboot.", "Автозапуск через root после перезагрузки не сработал.")
            } else selectedLanguage.translate(
                "Screen-off Essential Key handling must be reactivated after a phone reboot.",
                "Для работы Essential Key с выключенным экраном требуется повторная активация после перезагрузки.",
            )
            val detail = if (rootMode) {
                selectedLanguage.translate(
                    "Open Essential Remap and tap Restart. ADB is not required; make sure your root manager still allows Essential Remap.",
                    "Откройте Essential Remap и нажмите «Перезапуск». ADB не нужен; проверьте root-разрешение Essential Remap.",
                )
            } else selectedLanguage.translate(
                "Open Essential Remap, enable Wireless debugging, then tap Restart for the sleep monitor.",
                "Откройте Essential Remap, включите Wireless debugging и нажмите «Перезапуск» у монитора сна.",
            )
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(shortText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
