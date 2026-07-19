package com.aether.x.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class MaintenanceStatus(
    val enabled: Boolean,
    val title: String,
    val message: String,
)

private val MAINTENANCE_OFF = MaintenanceStatus(
    enabled = false,
    title = "",
    message = "",
)

class MaintenanceRepository {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val docRef by lazy { firestore.collection("config").document("maintenance") }

    fun observe(): Flow<MaintenanceStatus> = callbackFlow {
        val registration = docRef.addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(MAINTENANCE_OFF)
                return@addSnapshotListener
            }
            val enabled = snapshot.getBoolean("enabled") ?: false
            if (!enabled) {
                trySend(MAINTENANCE_OFF)
                return@addSnapshotListener
            }
            trySend(
                MaintenanceStatus(
                    enabled = true,
                    title = snapshot.getString("title").orEmpty(),
                    message = snapshot.getString("message").orEmpty(),
                ),
            )
        }
        awaitClose { registration.remove() }
    }
}
