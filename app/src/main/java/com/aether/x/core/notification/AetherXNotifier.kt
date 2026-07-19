package com.aether.x.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aether.x.MainActivity
import com.aether.x.R

object AetherXNotifier {

    enum class NotificationKind(
        val channelId: String,
        val channelNameRes: Int,
        val channelDescRes: Int,
        val importance: Int,
        val notificationId: Int,
    ) {
        UPDATE(
            channelId = "aetherx_update_alerts",
            channelNameRes = R.string.notif_channel_update_name,
            channelDescRes = R.string.notif_channel_update_desc,

            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = 1001,
        ),
        MAINTENANCE(
            channelId = "aetherx_maintenance_alerts",
            channelNameRes = R.string.notif_channel_maintenance_name,
            channelDescRes = R.string.notif_channel_maintenance_desc,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = 1002,
        ),

        MEMBERSHIP(
            channelId = "aetherx_membership_alerts",
            channelNameRes = R.string.notif_channel_membership_name,
            channelDescRes = R.string.notif_channel_membership_desc,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = 1005,
        ),
        GENERAL(
            channelId = "aetherx_general_alerts",
            channelNameRes = R.string.notif_channel_general_name,
            channelDescRes = R.string.notif_channel_general_desc,

            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = 1003,
        ),

        FEATURE_TOGGLE(
            channelId = "aetherx_feature_toggle_alerts",
            channelNameRes = R.string.notif_channel_feature_toggle_name,
            channelDescRes = R.string.notif_channel_feature_toggle_desc,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = 1004,
        ),
    }

    private fun ensureChannel(context: Context, kind: NotificationKind) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            kind.channelId,
            context.getString(kind.channelNameRes),
            kind.importance,
        ).apply {
            description = context.getString(kind.channelDescRes)
            enableVibration(true)
            vibrationPattern = DEFAULT_VIBRATION_PATTERN
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(context: Context): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notify(
        context: Context,
        kind: NotificationKind,
        title: String,
        text: String,
        bigText: String? = null,
        ongoing: Boolean = false,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context, kind)

        val contentIntent = PendingIntent.getActivity(
            context,
            kind.notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, kind.channelId)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setContentIntent(contentIntent)
            .setPriority(importanceToPriority(kind.importance))

            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(DEFAULT_VIBRATION_PATTERN)

        if (!bigText.isNullOrBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }

        val notification: Notification = builder.build()
        NotificationManagerCompat.from(context).notify(kind.notificationId, notification)
    }

    fun notifyFeatureToggled(context: Context, featureName: String, enabled: Boolean) {
        notify(
            context = context,
            kind = NotificationKind.FEATURE_TOGGLE,
            title = featureName,
            text = context.getString(
                if (enabled) R.string.notif_feature_toggle_enabled else R.string.notif_feature_toggle_disabled,
            ),
        )
    }

    fun cancel(context: Context, kind: NotificationKind) {
        NotificationManagerCompat.from(context).cancel(kind.notificationId)
    }

    private fun importanceToPriority(importance: Int): Int = when (importance) {
        NotificationManager.IMPORTANCE_HIGH -> NotificationCompat.PRIORITY_HIGH
        NotificationManager.IMPORTANCE_LOW -> NotificationCompat.PRIORITY_LOW
        NotificationManager.IMPORTANCE_MIN -> NotificationCompat.PRIORITY_MIN
        else -> NotificationCompat.PRIORITY_DEFAULT
    }

    private val DEFAULT_VIBRATION_PATTERN = longArrayOf(0, 200, 100, 200)
}
