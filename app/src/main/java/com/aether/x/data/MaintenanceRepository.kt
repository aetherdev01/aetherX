package com.aether.x.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Status mode maintenance/pemeliharaan aplikasi, dibaca dari dokumen tunggal
 * `config/maintenance` di Firestore. [enabled] = false berarti aplikasi
 * berjalan normal; [enabled] = true berarti seluruh layar harus tertutup
 * dialog blocking (lihat [com.aether.x.ui.maintenance.MaintenanceGate]) yang
 * hanya bisa "dibuka" dengan menghubungi admin lewat Telegram — TIDAK bisa
 * di-dismiss dengan tombol back atau tap di luar dialog.
 *
 * Skema dokumen `config/maintenance`:
 * ```
 * enabled: boolean       // true = mode maintenance aktif, tampilkan dialog blocking
 * title: string          // judul dialog, mis. "Sedang Pemeliharaan"
 * message: string        // deskripsi/alasan maintenance yang ditampilkan ke pengguna
 * updatedAt: timestamp   // kapan terakhir diubah (server timestamp)
 * ```
 *
 * Dokumen ini HANYA ditulis oleh bot Telegram (service account, bypass
 * firestore.rules) — lihat menu "🛠️ Maintenance" di bot.js. Client Android
 * tidak pernah menulis ke dokumen ini sama sekali, murni membaca.
 */
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

    /**
     * Memantau `config/maintenance` SECARA REALTIME lewat `addSnapshotListener`
     * — begitu admin toggle maintenance ON/OFF atau ubah pesan lewat bot
     * Telegram, perubahan itu langsung didorong ke seluruh instance aplikasi
     * yang sedang terbuka tanpa perlu refresh/restart manual.
     *
     * Kalau dokumen belum pernah dibuat sama sekali (mis. instalasi baru bot
     * yang belum pernah dipakai toggle maintenance-nya), ATAU terjadi error
     * (offline, dsb), dianggap [MAINTENANCE_OFF] — supaya aplikasi TIDAK
     * pernah macet terkunci akibat dokumen konfigurasi belum ada atau
     * jaringan bermasalah. Mode maintenance hanya aktif kalau server EKSPLISIT
     * bilang `enabled = true`.
     */
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
