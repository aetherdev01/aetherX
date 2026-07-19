package com.aether.x.core.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

object AdbConnectionManager {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val connectMutex = Mutex()

    private lateinit var appContext: Context
    private lateinit var keyManager: AdbKeyManager
    private lateinit var preferences: AdbPreferences
    private lateinit var connection: AetherXAdbConnectionManager

    private val _state = MutableStateFlow<AdbConnectionState>(AdbConnectionState.NotPaired)
    val state: StateFlow<AdbConnectionState> = _state.asStateFlow()

    private var initialized = false

    private var autoPairingJob: Job? = null

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        keyManager = AdbKeyManager(appContext)
        preferences = AdbPreferences(appContext)
        connection = AetherXAdbConnectionManager(keyManager)

        scope.launch {
            val saved = preferences.getSavedHostPort()
            _state.value = if (saved != null) {
                AdbConnectionState.PairedNotConnected
            } else {
                AdbConnectionState.NotPaired
            }
            if (saved != null) {
                autoReconnect()
            }
        }
    }

    suspend fun pair(
        pairingHost: String,
        pairingPort: Int,
        pairingCode: String,
        connectPort: Int,
    ): AdbConnectionState = connectMutex.withLock {
        _state.value = AdbConnectionState.Pairing

        val paired = withContext(Dispatchers.IO) {
            runCatching {
                connection.pair(pairingHost, pairingPort, pairingCode)
            }
        }

        if (paired.isFailure) {
            val failure = AdbConnectionState.Failed(
                reason = AdbFailureReason.PAIRING_CODE_INVALID_OR_EXPIRED,
                detail = paired.exceptionOrNull()?.message ?: "Pairing ke $pairingHost:$pairingPort gagal atau kode kedaluwarsa.",
            )
            _state.value = failure
            return failure
        }

        preferences.saveHostPort(pairingHost, connectPort)
        return connectLocked(pairingHost, connectPort)
    }

    suspend fun connect(host: String, port: Int): AdbConnectionState = connectMutex.withLock {
        connectLocked(host, port)
    }

    fun startAutoPairing(context: Context) {
        autoPairingJob?.cancel()
        _state.value = AdbConnectionState.SearchingForPairing
        val job = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AdbAutoPairingDiscovery.discoverPairingService(context) }
            }
            result.fold(
                onSuccess = { service ->
                    _state.value = AdbConnectionState.PairingFound(service.host, service.port)
                },
                onFailure = { error ->
                    val reason = if (error is TimeoutCancellationException) {
                        AdbFailureReason.AUTO_DISCOVERY_TIMEOUT
                    } else {
                        AdbFailureReason.UNKNOWN
                    }
                    _state.value = AdbConnectionState.Failed(
                        reason = reason,
                        detail = error.message ?: "Tidak menemukan service pairing di jaringan lokal.",
                    )
                },
            )
        }
        autoPairingJob = job
        job.invokeOnCompletion { if (autoPairingJob === job) autoPairingJob = null }
    }

    fun cancelAutoPairing() {
        autoPairingJob?.cancel()
        autoPairingJob = null
        if (_state.value == AdbConnectionState.SearchingForPairing || _state.value is AdbConnectionState.PairingFound) {
            _state.value = AdbConnectionState.NotPaired
        }
    }

    suspend fun confirmAutoPairingCode(
        context: Context,
        pairingCode: String,
    ): AdbConnectionState {
        val found = _state.value as? AdbConnectionState.PairingFound
            ?: return AdbConnectionState.Failed(
                reason = AdbFailureReason.UNKNOWN,
                detail = "Sesi pairing sudah kedaluwarsa, tekan Start lagi.",
            )
        return pairAndAutoConnect(context, found.host, found.port, pairingCode)
    }

    private suspend fun pairAndAutoConnect(
        context: Context,
        pairingHost: String,
        pairingPort: Int,
        pairingCode: String,
    ): AdbConnectionState = connectMutex.withLock {
        _state.value = AdbConnectionState.Pairing

        val paired = withContext(Dispatchers.IO) {
            runCatching { connection.pair(pairingHost, pairingPort, pairingCode) }
        }

        if (paired.isFailure) {
            val failure = AdbConnectionState.Failed(
                reason = AdbFailureReason.PAIRING_CODE_INVALID_OR_EXPIRED,
                detail = paired.exceptionOrNull()?.message ?: "Pairing ke $pairingHost:$pairingPort gagal atau kode kedaluwarsa.",
            )
            _state.value = failure
            return failure
        }

        _state.value = AdbConnectionState.Connecting

        val maxConnectServiceAttempts = 3
        var connectService: AdbAutoPairingDiscovery.DiscoveredService? = null
        for (attempt in 1..maxConnectServiceAttempts) {
            connectService = withContext(Dispatchers.IO) {
                runCatching { AdbAutoPairingDiscovery.discoverConnectService(context, timeoutMs = 6_000) }
            }.getOrNull()
            if (connectService != null) break
        }

        val connectPort = connectService?.port
        if (connectPort == null) {
            val failure = AdbConnectionState.Failed(
                reason = AdbFailureReason.CONNECT_AFTER_PAIRING_FAILED,
                detail = "Pairing berhasil, tapi port koneksi tidak ditemukan otomatis. Coba \"Sambungkan\" lagi.",
            )
            _state.value = failure
            return failure
        }

        preferences.saveHostPort(pairingHost, connectPort)

        val maxConnectAttempts = 3
        var lastResult: AdbConnectionState = AdbConnectionState.Connecting
        for (attempt in 1..maxConnectAttempts) {
            lastResult = connectLocked(pairingHost, connectPort)
            if (lastResult is AdbConnectionState.Connected) return lastResult
            if (attempt < maxConnectAttempts) delay(1_500)
        }

        val failure = AdbConnectionState.Failed(
            reason = AdbFailureReason.CONNECT_AFTER_PAIRING_FAILED,
            detail = "Pairing berhasil, tapi koneksi shell belum bisa dibentuk. Coba \"Sambungkan\" lagi — pairing tidak perlu diulang.",
        )
        _state.value = failure
        return failure
    }

    private suspend fun connectLocked(host: String, port: Int): AdbConnectionState {
        _state.value = AdbConnectionState.Connecting

        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (connection.isConnected) {
                    connection.disconnect()
                }
                connection.connect(host, port, timeoutMs = 10_000)
            }
        }

        val success = result.getOrDefault(false)
        return if (success) {
            preferences.saveHostPort(host, port)
            val connected = AdbConnectionState.Connected
            _state.value = connected
            connected
        } else {

            val exception = result.exceptionOrNull()
            val certificateRejected = exception != null &&
                exception.javaClass.simpleName.contains("PairingRequired", ignoreCase = true)
            val failure = AdbConnectionState.Failed(
                reason = if (certificateRejected) {
                    AdbFailureReason.SHELL_REJECTED_NEEDS_REPAIR
                } else {
                    AdbFailureReason.HOST_UNREACHABLE
                },
                detail = exception?.message ?: "Tidak bisa terhubung ke $host:$port.",
            )
            _state.value = failure
            failure
        }
    }

    fun autoReconnect() {
        scope.launch {
            val saved = preferences.getSavedHostPort() ?: return@launch
            if (_state.value == AdbConnectionState.Connected) return@launch

            val firstAttempt = connectMutex.withLock { connectLocked(saved.first, saved.second) }
            if (firstAttempt is AdbConnectionState.Connected) return@launch

            _state.value = AdbConnectionState.Connecting

            val maxRediscoveryAttempts = 3
            var rediscovered: AdbAutoPairingDiscovery.DiscoveredService? = null
            for (attempt in 1..maxRediscoveryAttempts) {
                rediscovered = withContext(Dispatchers.IO) {
                    runCatching { AdbAutoPairingDiscovery.discoverConnectService(appContext, timeoutMs = 6_000) }
                }.getOrNull()
                if (rediscovered != null) break
            }
            if (rediscovered == null) {

                _state.value = firstAttempt
                return@launch
            }

            if (rediscovered.port == saved.second && rediscovered.host == saved.first) {

                _state.value = firstAttempt
                return@launch
            }
            connectMutex.withLock { connectLocked(rediscovered.host, rediscovered.port) }
        }
    }

    suspend fun execShell(command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val marker = "__AETHERX_EXIT_CODE__"
        val wrapped = "$command; echo \"$marker\$?\""
        val stream: AdbStream = connection.openStream("shell:$wrapped")
        try {
            val output = stream.openInputStream().use { input -> input.readBytes() }.toString(Charsets.UTF_8)
            val markerIndex = output.lastIndexOf(marker)
            if (markerIndex == -1) {

                return@withContext 1 to output
            }
            val exitCodeText = output.substring(markerIndex + marker.length).trim()
            val exitCode = exitCodeText.toIntOrNull() ?: 1
            val cleanOutput = output.substring(0, markerIndex)
            exitCode to cleanOutput
        } finally {
            stream.close()
        }
    }

    fun isConnected(): Boolean = _state.value == AdbConnectionState.Connected && ::connection.isInitialized && connection.isConnected

    fun markStreamFailureAndReconnect() {
        if (_state.value == AdbConnectionState.Connected) {
            _state.value = AdbConnectionState.PairedNotConnected
        }
        autoReconnect()
    }

    fun forgetPairing() {
        autoPairingJob?.cancel()
        autoPairingJob = null
        runCatching { if (::connection.isInitialized) connection.disconnect() }
        keyManager.forgetIdentity()
        _state.value = AdbConnectionState.NotPaired
        scope.launch { preferences.clearHostPort() }
    }

    fun disconnectOnly() {
        runCatching { if (::connection.isInitialized) connection.disconnect() }
        if (_state.value == AdbConnectionState.Connected) {
            _state.value = AdbConnectionState.PairedNotConnected
        }
    }

    private class AetherXAdbConnectionManager(
        keyManager: AdbKeyManager,
    ) : AbsAdbConnectionManager() {

        private val identity = keyManager.getOrCreateIdentity()

        init {
            setApi(Build.VERSION.SDK_INT)
        }

        fun connect(host: String, port: Int, timeoutMs: Long): Boolean {
            setTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            return connect(host, port)
        }

        override fun getPrivateKey(): PrivateKey = identity.privateKey

        override fun getCertificate(): Certificate = identity.certificate

        override fun getDeviceName(): String = "AetherX"
    }
}
