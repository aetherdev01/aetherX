package com.aether.x.data

import org.json.JSONObject

/**
 * State kuota rewarded-ad untuk SATU fitur (diidentifikasi lewat
 * [featureKey] bebas, mis. "kill_background_apps") pada tanggal tertentu.
 *
 * Dipakai oleh [com.aether.x.core.ads.RewardGate] untuk melacak:
 * - Berapa kali fitur ini sudah dipakai gratis HARI INI (tanpa iklan),
 *   dibandingkan terhadap kuota gratis harian yang ditentukan si pemanggil.
 * - Berapa "kredit" ekstra yang sudah didapat dari menonton rewarded ad
 *   (belum dipakai) — satu kali tonton = satu kredit, dipakai satu-per-satu
 *   setiap kali fitur ini dijalankan setelah kuota gratis habis.
 *
 * [dateKey] memakai format `yyyyMMdd` (zona waktu device) supaya kuota
 * gratis otomatis reset di hari berikutnya tanpa perlu job/scheduler
 * terpisah — cukup dibandingkan terhadap tanggal hari ini saat dibaca (lihat
 * [com.aether.x.core.ads.RewardGate.today]).
 */
data class RewardQuotaState(
    val featureKey: String,
    val dateKey: String,
    val freeUsesToday: Int = 0,
    val extraCredits: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_FEATURE, featureKey)
        put(KEY_DATE, dateKey)
        put(KEY_FREE_USES, freeUsesToday)
        put(KEY_CREDITS, extraCredits)
    }

    companion object {
        private const val KEY_FEATURE = "featureKey"
        private const val KEY_DATE = "dateKey"
        private const val KEY_FREE_USES = "freeUsesToday"
        private const val KEY_CREDITS = "extraCredits"

        fun empty(featureKey: String, dateKey: String) = RewardQuotaState(
            featureKey = featureKey,
            dateKey = dateKey,
        )

        fun fromJson(json: JSONObject): RewardQuotaState = RewardQuotaState(
            featureKey = json.optString(KEY_FEATURE),
            dateKey = json.optString(KEY_DATE),
            freeUsesToday = json.optInt(KEY_FREE_USES, 0),
            extraCredits = json.optInt(KEY_CREDITS, 0),
        )
    }
}

/**
 * Serialisasi/deserialisasi map `featureKey -> RewardQuotaState` ke satu
 * string JSON tunggal, supaya bisa disimpan sebagai satu key DataStore
 * (lihat [AetherXPreferences]) — pola sama persis dengan
 * [GameProfileSerializer], sengaja begitu supaya fitur baru yang mau
 * dipasangi reward-gate nanti tidak perlu key DataStore baru, cukup
 * featureKey string baru di map ini.
 */
object RewardQuotaSerializer {

    fun serialize(states: Map<String, RewardQuotaState>): String {
        val root = JSONObject()
        states.forEach { (featureKey, state) -> root.put(featureKey, state.toJson()) }
        return root.toString()
    }

    fun deserialize(raw: String?): Map<String, RewardQuotaState> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(raw)
            root.keys().asSequence().associateWith { featureKey ->
                RewardQuotaState.fromJson(root.getJSONObject(featureKey))
            }
        } catch (t: Throwable) {
            emptyMap()
        }
    }
}
