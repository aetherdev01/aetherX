package com.aether.x.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Info versi terbaru aplikasi, dibaca dari dokumen tunggal `config/update` di
 * Firestore. Dipublish oleh admin lewat menu "🚀 Update Versi" di bot
 * Telegram (lihat bot.js) setiap kali rilis baru di-publish ke GitHub
 * Releases — BUKAN diisi otomatis oleh proses build/CI.
 *
 * Skema dokumen `config/update`:
 * ```
 * latestVersionCode: integer   // dibandingkan dengan BuildConfig.VERSION_CODE lokal
 * latestVersionName: string    // mis. "1.2.0", ditampilkan apa adanya di dialog
 * description: string          // changelog rilis, ditampilkan apa adanya (boleh multi-baris)
 * downloadUrl: string          // link GitHub Release yang dibuka di browser
 * mandatory: boolean           // disiapkan untuk masa depan; UpdateGate saat ini
 *                               // SELALU menampilkan dialog yang bisa di-dismiss
 *                               // terlepas dari nilai field ini
 * updatedAt: timestamp
 * ```
 *
 * Dokumen ini HANYA ditulis oleh bot Telegram (service account, bypass
 * firestore.rules). Client Android murni membaca.
 */
data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val description: String,
    val downloadUrl: String,
    val mandatory: Boolean,
)

private val UPDATE_NONE = UpdateInfo(
    latestVersionCode = 0,
    latestVersionName = "",
    description = "",
    downloadUrl = "",
    mandatory = false,
)

class UpdateRepository {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val docRef by lazy { firestore.collection("config").document("update") }

    /**
     * Memantau `config/update` SECARA REALTIME lewat `addSnapshotListener` —
     * begitu admin publish versi baru lewat bot Telegram, dialog update bisa
     * langsung muncul di seluruh instance aplikasi yang sedang terbuka tanpa
     * perlu restart, persis seperti [MaintenanceRepository].
     *
     * Kalau dokumen belum pernah dibuat, ATAU terjadi error (offline, dsb),
     * dianggap [UPDATE_NONE] (versionCode 0) — supaya perbandingan dengan
     * versionCode lokal (selalu >= 1) otomatis tidak pernah memicu dialog
     * update secara keliru akibat masalah jaringan/konfigurasi.
     */
    fun observe(): Flow<UpdateInfo> = callbackFlow {
        val registration = docRef.addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
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
                ),
            )
        }
        awaitClose { registration.remove() }
    }
}
