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
import com.abdulkus.essentialremap.MainActivity
import com.abdulkus.essentialremap.R
import com.abdulkus.essentialremap.ScreenOffKeyAccess
import com.abdulkus.essentialremap.ui.AppLanguage
import com.abdulkus.essentialremap.ui.UserPreferences

class SleepMonitorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = UserPreferences(context)
        if (!preferences.screenOffEnabled) return

        // The shell UID process never survives reboot. Clear the stale marker before notifying.
        ScreenOffKeyAccess.markStopped(context)
        postReminder(context, preferences.language)
    }

    companion object {
        private const val CHANNEL_ID = "essential_remap_sleep_monitor"
        private const val NOTIFICATION_ID = 2054

        fun cancelReminder(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        private fun postReminder(context: Context, language: AppLanguage?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val russian = language == AppLanguage.RUSSIAN
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    if (russian) "Монитор сна Essential Remap" else "Essential Remap sleep monitor",
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
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(
                        if (russian) "Перезапустите монитор сна" else "Restart the sleep monitor",
                    )
                    .setContentText(
                        if (russian) {
                            "Для работы Essential Key с выключенным экраном требуется повторная активация после перезагрузки."
                        } else {
                            "Screen-off Essential Key handling must be reactivated after a phone reboot."
                        },
                    )
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            if (russian) {
                                "Откройте Essential Remap, включите Wireless debugging и нажмите «Перезапуск» у монитора сна."
                            } else {
                                "Open Essential Remap, enable Wireless debugging, then tap Restart for the sleep monitor."
                            },
                        ),
                    )
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
