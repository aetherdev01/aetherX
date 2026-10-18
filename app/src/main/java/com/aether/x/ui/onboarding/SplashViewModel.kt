package com.aether.x.ui.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.DeviceId
import com.aether.x.data.DeviceRegistry
import com.aether.x.data.FcmTokenRepository
import com.aether.x.data.FcmTokenSyncStage
import com.aether.x.data.MaintenanceRepository
import com.aether.x.data.UserIdRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SplashViewModel"
private const val IDENTITY_TIMEOUT_MILLIS = 20_000L
private const val STEP_TIMEOUT_MILLIS = 7_000L

private const val VERIFY_DEVICE = "verify_device"
private const val REGISTER_DEVICE = "register_device"
private const val RECORD_DEVICE = "record_device"
private const val FETCH_TOKEN = "fetch_token"
private const val SYNC_TOKEN = "sync_token"
private const val CHECK_SERVER = "check_server"

enum class SplashStepStatus { PENDING, ACTIVE, COMPLETE, SKIPPED, FAILED }

data class SplashProgressStep(
    val id: String,
    val title: String,
    val status: SplashStepStatus = SplashStepStatus.PENDING,
    val detail: String = "Menunggu",
)

private fun initialSteps() = listOf(
    SplashProgressStep(CHECK_SERVER, "Menghubungkan ke server"),
    SplashProgressStep(VERIFY_DEVICE, "Memverifikasi Device ID"),
    SplashProgressStep(REGISTER_DEVICE, "Mendaftarkan Device ID"),
    SplashProgressStep(RECORD_DEVICE, "Memperbarui status perangkat"),
    SplashProgressStep(FETCH_TOKEN, "Mengambil token notifikasi"),
    SplashProgressStep(SYNC_TOKEN, "Menyinkronkan token ke server"),
)

data class SplashUiState(
    val steps: List<SplashProgressStep> = initialSteps(),
    val progress: Float = 0f,
    val statusText: String = "Menghubungkan ke server...",
    val failed: Boolean = false,
    val finished: Boolean = false,
)

class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)
    private val deviceId = DeviceId.read(application)
    private val userIdRepository = UserIdRepository(preferences, deviceId)
    private val deviceRegistry = DeviceRegistry(application)
    private val maintenanceRepository = MaintenanceRepository()

    private val _state = MutableStateFlow(SplashUiState())
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    init {
        runStartupSequence()
    }

    fun retry() {
        _state.value = SplashUiState()
        runStartupSequence()
    }

    private fun runStartupSequence() {
        viewModelScope.launch {
            val cachedUserId = preferences.getSyncedUserId()

            // This server-only read makes the first splash stage a real network request,
            // not just a label shown while the UI waits locally.
            updateStep(CHECK_SERVER, SplashStepStatus.ACTIVE, "Meminta konfigurasi awal dari Firestore...")
            setStatus("Menghubungkan ke server...")
            val initialServerResponse = withTimeoutOrNull(5_000L) {
                maintenanceRepository.fetchOnceChecked()
            }
            if (initialServerResponse != null) {
                updateStep(CHECK_SERVER, SplashStepStatus.COMPLETE, "Koneksi server terkonfirmasi")
            } else {
                failStep(CHECK_SERVER, "Server belum merespons; proses lain tetap dicoba")
            }

            updateStep(VERIFY_DEVICE, SplashStepStatus.ACTIVE, "Memeriksa identitas perangkat di server...")
            setStatus("Memverifikasi Device ID di server...")

            // Even when a local ID is cached, ask Firestore for the current device record.
            // This makes the visible loading stages reflect real startup network operations.
            val identityTimeout = if (cachedUserId != null) 6_000L else IDENTITY_TIMEOUT_MILLIS
            val resolution = withTimeoutOrNull(identityTimeout) {
                userIdRepository.resolveUserIdentity(
                    forceRemoteCheck = true,
                    onCheckingDevice = {
                        updateStep(VERIFY_DEVICE, SplashStepStatus.ACTIVE, "Mencari Device ID di database...")
                        setStatus("Memverifikasi Device ID di server...")
                    },
                    onRegisteringDevice = {
                        updateStep(VERIFY_DEVICE, SplashStepStatus.COMPLETE, "Device ID belum ditemukan")
                        updateStep(REGISTER_DEVICE, SplashStepStatus.ACTIVE, "Membuat identitas dan mendaftarkan perangkat...")
                        setStatus("Mendaftarkan Device ID...")
                    },
                )
            }

            val userId: String
            if (resolution == null) {
                Log.w(TAG, "Verifikasi/registrasi identitas gagal atau timeout")
                failActiveStep("Server tidak merespons atau registrasi ditolak")

                if (cachedUserId == null) {
                    _state.value = _state.value.copy(
                        failed = true,
                        statusText = "Verifikasi perangkat gagal. Periksa koneksi lalu coba lagi.",
                    )
                    return@launch
                }

                // Preserve app access for returning users when the server is temporarily
                // unavailable. Pending network tasks are clearly marked rather than shown
                // as successful; the application also attempts FCM sync in its app scope.
                val registrationStep = _state.value.steps.first { it.id == REGISTER_DEVICE }
                if (registrationStep.status == SplashStepStatus.PENDING) {
                    updateStep(REGISTER_DEVICE, SplashStepStatus.SKIPPED, "Dilewati sementara; memakai ID lokal tersimpan")
                }
                listOf(RECORD_DEVICE, FETCH_TOKEN, SYNC_TOKEN).forEach { id ->
                    val step = _state.value.steps.first { it.id == id }
                    if (step.status == SplashStepStatus.PENDING) {
                        updateStep(id, SplashStepStatus.SKIPPED, "Ditunda karena server tidak tersedia")
                    }
                }
                _state.value = _state.value.copy(
                    statusText = "Server tidak tersedia — memakai ID tersimpan",
                    finished = true,
                    failed = false,
                )
                return@launch
            } else {
                userId = resolution.userId
                if (resolution.alreadyRegistered) {
                    updateStep(VERIFY_DEVICE, SplashStepStatus.COMPLETE, "Device ID ditemukan di server")
                    updateStep(REGISTER_DEVICE, SplashStepStatus.SKIPPED, "Sudah terdaftar; tidak membuat ID baru")
                } else {
                    updateStep(VERIFY_DEVICE, SplashStepStatus.COMPLETE, "Pemeriksaan server berhasil")
                    updateStep(REGISTER_DEVICE, SplashStepStatus.COMPLETE, "Device ID berhasil didaftarkan")
                }
            }

            updateStep(RECORD_DEVICE, SplashStepStatus.ACTIVE, "Mengirim status login perangkat...")
            setStatus("Memperbarui status perangkat...")
            val loginRecorded = withTimeoutOrNull(STEP_TIMEOUT_MILLIS) {
                deviceRegistry.recordDeviceLogin(userId)
            }
            if (loginRecorded == true) {
                updateStep(RECORD_DEVICE, SplashStepStatus.COMPLETE, "Status perangkat tersimpan di server")
            } else {
                failStep(RECORD_DEVICE, "Gagal memperbarui status perangkat")
            }

            setStatus("Menyinkronkan notifikasi...")
            val tokenSynced = withTimeoutOrNull(STEP_TIMEOUT_MILLIS) {
                FcmTokenRepository.syncTokenToFirestore(
                    getApplication(),
                    onStage = { stage ->
                        when (stage) {
                            FcmTokenSyncStage.FETCHING_TOKEN -> {
                                updateStep(FETCH_TOKEN, SplashStepStatus.ACTIVE, "Meminta token dari Firebase Messaging...")
                                setStatus("Mengambil token notifikasi...")
                            }
                            FcmTokenSyncStage.WRITING_TOKEN -> {
                                updateStep(FETCH_TOKEN, SplashStepStatus.COMPLETE, "Token FCM berhasil didapatkan")
                                updateStep(SYNC_TOKEN, SplashStepStatus.ACTIVE, "Mengirim token ke dokumen perangkat...")
                                setStatus("Menyinkronkan token ke server...")
                            }
                        }
                    },
                )
            }
            if (tokenSynced == true) {
                updateStep(SYNC_TOKEN, SplashStepStatus.COMPLETE, "Token tersimpan di Firestore")
            } else {
                failActiveStep("Token belum berhasil disinkronkan")
            }

            val hasFailures = _state.value.steps.any { it.status == SplashStepStatus.FAILED }
            _state.value = _state.value.copy(
                statusText = if (hasFailures) "Sinkronisasi selesai dengan sebagian proses tertunda" else "Sinkronisasi selesai",
                finished = true,
                failed = false,
            )
        }
    }

    private fun setStatus(message: String) {
        _state.value = _state.value.copy(statusText = message)
    }

    private fun updateStep(id: String, status: SplashStepStatus, detail: String) {
        val current = _state.value
        val next = current.steps.map { step ->
            if (step.id == id) step.copy(status = status, detail = detail)
            else if (status == SplashStepStatus.ACTIVE && step.status == SplashStepStatus.ACTIVE) {
                step.copy(status = SplashStepStatus.PENDING, detail = "Menunggu")
            } else step
        }
        val completed = next.count {
            it.status == SplashStepStatus.COMPLETE ||
                it.status == SplashStepStatus.SKIPPED ||
                it.status == SplashStepStatus.FAILED
        }
        val active = next.any { it.status == SplashStepStatus.ACTIVE }
        val progress = ((completed.toFloat() + if (active) 0.35f else 0f) / next.size.toFloat()).coerceIn(0f, 1f)
        _state.value = current.copy(steps = next, progress = progress)
    }

    private fun failStep(id: String, detail: String) {
        updateStep(id, SplashStepStatus.FAILED, detail)
    }

    private fun failActiveStep(detail: String) {
        val active = _state.value.steps.firstOrNull { it.status == SplashStepStatus.ACTIVE }
        if (active != null) failStep(active.id, detail)
    }
}
