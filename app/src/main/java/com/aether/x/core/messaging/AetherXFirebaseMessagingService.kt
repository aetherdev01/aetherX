package com.aether.x.core.messaging

import android.util.Log
import com.aether.x.core.notification.AetherXNotifier
import com.aether.x.data.FcmTokenRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AetherXFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            FcmTokenRepository.syncTokenToFirestore(applicationContext, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val kind = data["kind"].orEmpty()

        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.title.orEmpty()
        val text = data["text"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.body.orEmpty()
        val bigText = data["bigText"]?.takeIf { it.isNotBlank() }

        if (title.isBlank() && text.isBlank()) {
            Log.w(TAG, "Pesan FCM diterima tanpa title maupun text yang bisa ditampilkan, diabaikan.")
            return
        }

        val notificationKind = when (kind) {
            "maintenance" -> AetherXNotifier.NotificationKind.MAINTENANCE
            "update" -> AetherXNotifier.NotificationKind.UPDATE
            "membership" -> AetherXNotifier.NotificationKind.MEMBERSHIP
            else -> AetherXNotifier.NotificationKind.GENERAL
        }

        AetherXNotifier.notify(
            context = applicationContext,
            kind = notificationKind,
            title = title,
            text = text,
            bigText = bigText,
        )
    }

    private companion object {
        const val TAG = "AetherXFcmService"
    }
}
