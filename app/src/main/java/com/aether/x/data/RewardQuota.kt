package com.aether.x.data

import org.json.JSONObject

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
