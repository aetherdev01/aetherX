package com.aether.x.core.monitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.roundToInt

/** Ringkas dan lokal: menyimpan sesi serta statistik agregat tanpa database tambahan. */
data class GameSessionSample(
    val timestampMs: Long,
    val fps: Int?,
    val cpuPercent: Int?,
    val gpuPercent: Int?,
    val gpuFreqMhz: Float?,
    val temperatureC: Float?,
    val ramUsedMb: Long?,
)

data class GameSession(
    val id: String,
    val packageName: String,
    val gameName: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val samples: List<GameSessionSample> = emptyList(),
    val sampleCount: Int = 0,
    val currentFps: Int? = null,
    val fpsCount: Int = 0,
    val fpsSum: Long = 0L,
    val minFps: Int? = null,
    val maxFps: Int? = null,
    val lowFpsCount: Int = 0,
    val currentCpuPercent: Int? = null,
    val cpuCount: Int = 0,
    val cpuSum: Long = 0L,
    val maxCpuPercent: Int? = null,
    val currentGpuPercent: Int? = null,
    val gpuCount: Int = 0,
    val gpuSum: Long = 0L,
    val maxGpuPercent: Int? = null,
    val currentGpuFreqMhz: Float? = null,
    val temperatureStartC: Float? = null,
    val currentTemperatureC: Float? = null,
    val maxTemperatureC: Float? = null,
    val currentRamUsedMb: Long? = null,
    val ramCount: Int = 0,
    val ramSumMb: Long = 0L,
    val maxRamUsedMb: Long? = null,
) {
    val averageFps: Int? get() = if (fpsCount > 0) (fpsSum.toDouble() / fpsCount).roundToInt() else null
    val averageCpuPercent: Int? get() = if (cpuCount > 0) (cpuSum.toDouble() / cpuCount).roundToInt() else null
    val averageGpuPercent: Int? get() = if (gpuCount > 0) (gpuSum.toDouble() / gpuCount).roundToInt() else null
    val averageRamUsedMb: Long? get() = if (ramCount > 0) ramSumMb / ramCount else null
    val temperatureRiseC: Float? get() = if (temperatureStartC != null && maxTemperatureC != null) maxTemperatureC - temperatureStartC else null
    val durationMs: Long get() = ((endedAtMs ?: System.currentTimeMillis()) - startedAtMs).coerceAtLeast(0L)
}

object GameSessionRepository {
    private const val PREFS_NAME = "aetherx_game_monitor"
    private const val KEY_SESSIONS = "sessions_json"
    private const val MAX_SESSIONS = 20
    private const val MAX_GRAPH_POINTS_PER_SESSION = 180 // ~30 menit terakhir per sesi
    private const val GRAPH_SAMPLE_INTERVAL_MS = 5_000L
    private const val PERSIST_INTERVAL_MS = 15_000L

    private val mutableSessions = MutableStateFlow<List<GameSession>>(emptyList())
    val sessions: StateFlow<List<GameSession>> = mutableSessions.asStateFlow()

    @Volatile private var initialized = false
    private var preferences: android.content.SharedPreferences? = null
    private var lastPersistAtMs = 0L

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences = prefs
        val now = System.currentTimeMillis()
        val loaded = runCatching {
            val array = JSONArray(prefs.getString(KEY_SESSIONS, "[]") ?: "[]")
            (0 until array.length()).mapNotNull { index ->
                runCatching { decodeSession(array.getJSONObject(index)) }.getOrNull()
            }.map { session ->
                // Jika OS menghentikan proses saat game masih berjalan, sesi lama ditutup
                // saat dibuka lagi, sehingga tidak ada sesi aktif palsu yang menggantung.
                if (session.endedAtMs == null) session.copy(endedAtMs = now) else session
            }.sortedByDescending { it.startedAtMs }.take(MAX_SESSIONS)
        }.getOrDefault(emptyList())
        mutableSessions.value = loaded
        initialized = true
        if (loaded.any { it.endedAtMs == now }) persist(now)
    }

    val activeSession: GameSession? get() = mutableSessions.value.firstOrNull { it.endedAtMs == null }

    @Synchronized
    fun startSession(context: Context, packageName: String, gameName: String, now: Long = System.currentTimeMillis()) {
        initialize(context)
        finishActiveSession(now)
        val session = GameSession(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            gameName = gameName.ifBlank { packageName },
            startedAtMs = now,
        )
        mutableSessions.value = (listOf(session) + mutableSessions.value).take(MAX_SESSIONS)
        persist(now)
    }

    @Synchronized
    fun recordSample(sample: GameSessionSample) {
        val list = mutableSessions.value
        val activeIndex = list.indexOfFirst { it.endedAtMs == null }
        if (activeIndex < 0) return
        val previous = list[activeIndex]
        val fps = sample.fps?.takeIf { it in 0..360 }
        val cpu = sample.cpuPercent?.takeIf { it in 0..100 }
        val gpu = sample.gpuPercent?.takeIf { it in 0..100 }
        val temp = sample.temperatureC?.takeIf { it in 10f..100f }
        val ram = sample.ramUsedMb?.takeIf { it > 0L }

        val nextPoint = sample.copy(fps = fps, cpuPercent = cpu, gpuPercent = gpu, temperatureC = temp, ramUsedMb = ram)
        val addGraphPoint = previous.samples.isEmpty() || sample.timestampMs - previous.samples.last().timestampMs >= GRAPH_SAMPLE_INTERVAL_MS
        val graph = if (addGraphPoint) (previous.samples + nextPoint).takeLast(MAX_GRAPH_POINTS_PER_SESSION) else previous.samples

        val updated = previous.copy(
            samples = graph,
            sampleCount = previous.sampleCount + 1,
            currentFps = fps,
            fpsCount = previous.fpsCount + if (fps != null) 1 else 0,
            fpsSum = previous.fpsSum + (fps?.toLong() ?: 0L),
            minFps = if (fps != null) minOf(previous.minFps ?: fps, fps) else previous.minFps,
            maxFps = if (fps != null) maxOf(previous.maxFps ?: fps, fps) else previous.maxFps,
            lowFpsCount = previous.lowFpsCount + if (fps != null && fps < 30) 1 else 0,
            currentCpuPercent = cpu,
            cpuCount = previous.cpuCount + if (cpu != null) 1 else 0,
            cpuSum = previous.cpuSum + (cpu?.toLong() ?: 0L),
            maxCpuPercent = if (cpu != null) maxOf(previous.maxCpuPercent ?: cpu, cpu) else previous.maxCpuPercent,
            currentGpuPercent = gpu,
            gpuCount = previous.gpuCount + if (gpu != null) 1 else 0,
            gpuSum = previous.gpuSum + (gpu?.toLong() ?: 0L),
            maxGpuPercent = if (gpu != null) maxOf(previous.maxGpuPercent ?: gpu, gpu) else previous.maxGpuPercent,
            currentGpuFreqMhz = sample.gpuFreqMhz?.takeIf { it > 0f },
            temperatureStartC = previous.temperatureStartC ?: temp,
            currentTemperatureC = temp,
            maxTemperatureC = if (temp != null) maxOf(previous.maxTemperatureC ?: temp, temp) else previous.maxTemperatureC,
            currentRamUsedMb = ram,
            ramCount = previous.ramCount + if (ram != null) 1 else 0,
            ramSumMb = previous.ramSumMb + (ram ?: 0L),
            maxRamUsedMb = if (ram != null) maxOf(previous.maxRamUsedMb ?: ram, ram) else previous.maxRamUsedMb,
        )
        mutableSessions.value = list.toMutableList().also { it[activeIndex] = updated }
        if (sample.timestampMs - lastPersistAtMs >= PERSIST_INTERVAL_MS) persist(sample.timestampMs)
    }

    @Synchronized
    fun finishActiveSession(now: Long = System.currentTimeMillis()) {
        val list = mutableSessions.value
        val activeIndex = list.indexOfFirst { it.endedAtMs == null }
        if (activeIndex < 0) return
        mutableSessions.value = list.toMutableList().also { it[activeIndex] = it[activeIndex].copy(endedAtMs = now) }
        persist(now)
    }

    @Synchronized
    fun clearHistory() {
        mutableSessions.value = emptyList()
        persist(System.currentTimeMillis())
    }

    @Synchronized
    private fun persist(now: Long) {
        val array = JSONArray()
        mutableSessions.value.take(MAX_SESSIONS).forEach { array.put(encodeSession(it)) }
        preferences?.edit()?.putString(KEY_SESSIONS, array.toString())?.apply()
        lastPersistAtMs = now
    }

    private fun encodeSession(s: GameSession) = JSONObject().apply {
        put("id", s.id); put("package", s.packageName); put("name", s.gameName)
        put("start", s.startedAtMs); putNullable("end", s.endedAtMs)
        put("samples", JSONArray().apply { s.samples.forEach { point -> put(encodeSample(point)) } })
        put("sampleCount", s.sampleCount); putNullable("currentFps", s.currentFps)
        put("fpsCount", s.fpsCount); put("fpsSum", s.fpsSum); putNullable("minFps", s.minFps)
        putNullable("maxFps", s.maxFps); put("lowFps", s.lowFpsCount)
        putNullable("currentCpu", s.currentCpuPercent); put("cpuCount", s.cpuCount); put("cpuSum", s.cpuSum); putNullable("maxCpu", s.maxCpuPercent)
        putNullable("currentGpu", s.currentGpuPercent); put("gpuCount", s.gpuCount); put("gpuSum", s.gpuSum); putNullable("maxGpu", s.maxGpuPercent)
        putNullable("gpuFreq", s.currentGpuFreqMhz); putNullable("tempStart", s.temperatureStartC)
        putNullable("currentTemp", s.currentTemperatureC); putNullable("maxTemp", s.maxTemperatureC)
        putNullable("currentRam", s.currentRamUsedMb); put("ramCount", s.ramCount); put("ramSum", s.ramSumMb); putNullable("maxRam", s.maxRamUsedMb)
    }

    private fun encodeSample(p: GameSessionSample) = JSONObject().apply {
        put("t", p.timestampMs); putNullable("fps", p.fps); putNullable("cpu", p.cpuPercent)
        putNullable("gpu", p.gpuPercent); putNullable("gpuFreq", p.gpuFreqMhz)
        putNullable("temp", p.temperatureC); putNullable("ram", p.ramUsedMb)
    }

    private fun decodeSession(o: JSONObject): GameSession {
        val points = o.optJSONArray("samples") ?: JSONArray()
        return GameSession(
            id = o.optString("id", UUID.randomUUID().toString()), packageName = o.optString("package"),
            gameName = o.optString("name", o.optString("package")), startedAtMs = o.optLong("start"),
            endedAtMs = o.longOrNull("end"),
            samples = (0 until points.length()).mapNotNull { i -> runCatching { decodeSample(points.getJSONObject(i)) }.getOrNull() },
            sampleCount = o.optInt("sampleCount"), currentFps = o.intOrNull("currentFps"),
            fpsCount = o.optInt("fpsCount"), fpsSum = o.optLong("fpsSum"), minFps = o.intOrNull("minFps"),
            maxFps = o.intOrNull("maxFps"), lowFpsCount = o.optInt("lowFps"),
            currentCpuPercent = o.intOrNull("currentCpu"), cpuCount = o.optInt("cpuCount"), cpuSum = o.optLong("cpuSum"), maxCpuPercent = o.intOrNull("maxCpu"),
            currentGpuPercent = o.intOrNull("currentGpu"), gpuCount = o.optInt("gpuCount"), gpuSum = o.optLong("gpuSum"), maxGpuPercent = o.intOrNull("maxGpu"),
            currentGpuFreqMhz = o.floatOrNull("gpuFreq"), temperatureStartC = o.floatOrNull("tempStart"),
            currentTemperatureC = o.floatOrNull("currentTemp"), maxTemperatureC = o.floatOrNull("maxTemp"),
            currentRamUsedMb = o.longOrNull("currentRam"), ramCount = o.optInt("ramCount"), ramSumMb = o.optLong("ramSum"), maxRamUsedMb = o.longOrNull("maxRam"),
        )
    }

    private fun decodeSample(o: JSONObject) = GameSessionSample(
        timestampMs = o.optLong("t"), fps = o.intOrNull("fps"), cpuPercent = o.intOrNull("cpu"),
        gpuPercent = o.intOrNull("gpu"), gpuFreqMhz = o.floatOrNull("gpuFreq"),
        temperatureC = o.floatOrNull("temp"), ramUsedMb = o.longOrNull("ram"),
    )

    private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)
    private fun JSONObject.floatOrNull(key: String): Float? = if (isNull(key)) null else optDouble(key).toFloat()
}
