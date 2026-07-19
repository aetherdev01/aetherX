package com.aether.x.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager

object AdbPairingNotifier {

    private const val CHANNEL_ID = "aetherx_adb_pairing"
    const val NOTIFICATION_ID = 4103

    const val ACTION_REPLY_CODE = "com.aether.x.action.ADB_PAIRING_REPLY_CODE"
    const val ACTION_CANCEL = "com.aether.x.action.ADB_PAIRING_CANCEL"
    const val REMOTE_INPUT_KEY = "key_pairing_code"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.adb_pairing_notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.adb_pairing_notif_channel_desc)
            enableVibration(true)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return androidx.core.app.ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun cancelPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, AdbPairingReplyReceiver::class.java).setAction(ACTION_CANCEL),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun replyAction(context: Context): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel(context.getString(R.string.adb_pairing_notif_reply_label))
            .build()

        val replyIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            Intent(context, AdbPairingReplyReceiver::class.java).setAction(ACTION_REPLY_CODE),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Action.Builder(
            R.drawable.logo,
            context.getString(R.string.adb_pairing_notif_reply_action),
            replyIntent,
        ).addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun cancelAction(context: Context): NotificationCompat.Action = NotificationCompat.Action.Builder(
        R.drawable.logo,
        context.getString(R.string.adb_pairing_notif_cancel_action),
        cancelPendingIntent(context),
    ).build()

    private fun baseBuilder(context: Context): NotificationCompat.Builder {
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, com.aether.x.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)

    }

    private fun baseBuilderNoContentIntent(context: Context): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(null)

    }

    private fun push(context: Context, notification: Notification) {
        if (!hasNotificationPermission(context)) return
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun showSearching(context: Context) {
        val notification = baseBuilder(context)
            .setContentTitle(context.getString(R.string.adb_pairing_overlay_searching_title))
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(cancelAction(context))
            .build()
        push(context, notification)
    }

    fun showCodeInput(context: Context) {
        val notification = baseBuilderNoContentIntent(context)
            .setContentTitle(context.getString(R.string.adb_pairing_overlay_found_title))
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(replyAction(context))
            .build()
        push(context, notification)
    }

    fun showBusy(context: Context) {
        val hint = context.getString(R.string.adb_pairing_overlay_connecting_hint)
        val notification = baseBuilder(context)
            .setContentTitle(context.getString(R.string.adb_pairing_overlay_connecting_title))
            .setContentText(hint)
            .setStyle(NotificationCompat.BigTextStyle().bigText(hint))
            .setOngoing(true)
            .setAutoCancel(false)
            .setProgress(0, 0, true)
            .build()
        push(context, notification)
    }

    fun showError(context: Context, message: String) {
        val notification = baseBuilderNoContentIntent(context)
            .setContentTitle(context.getString(R.string.adb_pairing_overlay_error_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(replyAction(context))
            .build()
        push(context, notification)
    }

    fun stop(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

class AdbPairingReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AdbPairingNotifier.ACTION_REPLY_CODE -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val code = results?.getCharSequence(AdbPairingNotifier.REMOTE_INPUT_KEY)?.toString()?.trim().orEmpty()
                if (code.length == 6 && code.all { it.isDigit() }) {
                    PrivilegeManager.confirmAutoPairAdbCode(context.applicationContext, code)
                } else {
                    AdbPairingNotifier.showError(
                        context.applicationContext,
                        context.getString(R.string.adb_pairing_notif_invalid_code),
                    )
                }
            }
            AdbPairingNotifier.ACTION_CANCEL -> {
                PrivilegeManager.cancelAutoPairAdb()
                AdbPairingNotifier.stop(context.applicationContext)
            }
        }
    }
}
