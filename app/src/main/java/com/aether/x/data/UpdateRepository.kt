package com.aether.x.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val description: String,
    val downloadUrl: String,
    val mandatory: Boolean,
    val disabled: Boolean,
)

private val UPDATE_NONE = UpdateInfo(
    latestVersionCode = 0,
    latestVersionName = "",
    description = "",
    downloadUrl = "",
    mandatory = false,
    disabled = false,
)

class UpdateRepository {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val docRef by lazy { firestore.collection("config").document("update") }

    fun observe(): Flow<UpdateInfo> = callbackFlow {
        val registration = docRef.addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(UPDATE_NONE)
                return@addSnapshotListener
            }
            val disabled = snapshot.getBoolean("disabled") ?: false
            if (disabled) {
                trySend(UPDATE_NONE)
                return@addSnapshotListener
            }
            trySend(
                UpdateInfo(
                    latestVersionCode = (snapshot.getLong("latestVersionCode") ?: 0L).toInt(),
                    latestVersionName = snapshot.getString("latestVersionName").orEmpty(),
                    description = snapshot.getString("description").orEmpty(),
                    downloadUrl = snapshot.getString("downloadUrl").orEmpty(),
                    mandatory = snapshot.getBoolean("mandatory") ?: false,
                    disabled = false,
                ),
            )
        }
        awaitClose { registration.remove() }
    }
}
