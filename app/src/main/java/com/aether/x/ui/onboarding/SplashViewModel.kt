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
import com.aether.x.core.locale.str
import com.aether.x.R

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
    val detail: String = str(R.string.splash_waiting),
)

private fun initialSteps() = listOf(
    SplashProgressStep(CHECK_SERVER, str(R.string.splash_menghubungkan_ke_server)),
    SplashProgressStep(VERIFY_DEVICE, str(R.string.splash_memverifikasi_device_id)),
    SplashProgressStep(REGISTER_DEVICE, str(R.string.splash_mendaftarkan_device_id)),
    SplashProgressStep(RECORD_DEVICE, str(R.string.splash_memperbarui_status_perangkat)),
    SplashProgressStep(FETCH_TOKEN, str(R.string.splash_mengambil_token_notifikasi)),
    SplashProgressStep(SYNC_TOKEN, str(R.string.splash_menyinkronkan_token_ke_server)),
)

data class SplashUiState(
    val steps: List<SplashProgressStep> = initialSteps(),
    val progress: Float = 0f,
    val statusText: String = str(R.string.splash_connecting_to_server_dots),
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
            updateStep(CHECK_SERVER, SplashStepStatus.ACTIVE, str(R.string.splash_meminta_konfigurasi_awal_dari_firestore))
            setStatus(str(R.string.splash_menghubungkan_ke_server_2))
            val initialServerResponse = withTimeoutOrNull(5_000L) {
                maintenanceRepository.fetchOnceChecked()
            }
            if (initialServerResponse != null) {
                updateStep(CHECK_SERVER, SplashStepStatus.COMPLETE, str(R.string.splash_koneksi_server_terkonfirmasi))
            } else {
                failStep(CHECK_SERVER, str(R.string.splash_server_no_response_continue))
            }

            updateStep(VERIFY_DEVICE, SplashStepStatus.ACTIVE, str(R.string.splash_memeriksa_identitas_perangkat_di_server))
            setStatus(str(R.string.splash_memverifikasi_device_id_di_server))

            // Even when a local ID is cached, ask Firestore for the current device record.
            // This makes the visible loading stages reflect real startup network operations.
            val identityTimeout = if (cachedUserId != null) 6_000L else IDENTITY_TIMEOUT_MILLIS
            val resolution = withTimeoutOrNull(identityTimeout) {
                userIdRepository.resolveUserIdentity(
                    forceRemoteCheck = true,
                    onCheckingDevice = {
                        updateStep(VERIFY_DEVICE, SplashStepStatus.ACTIVE, str(R.string.splash_mencari_device_id_di_database))
                        setStatus(str(R.string.splash_memverifikasi_device_id_di_server))
                    },
                    onRegisteringDevice = {
                        updateStep(VERIFY_DEVICE, SplashStepStatus.COMPLETE, str(R.string.splash_device_id_belum_ditemukan))
                        updateStep(REGISTER_DEVICE, SplashStepStatus.ACTIVE, str(R.string.splash_membuat_identitas_dan_mendaftarkan_peran))
                        setStatus(str(R.string.splash_mendaftarkan_device_id_2))
                    },
                )
            }

            val userId: String
            if (resolution == null) {
                Log.w(TAG, "Verifikasi/registrasi identitas gagal atau timeout")
                failActiveStep(str(R.string.splash_server_tidak_merespons_atau_registrasi))

                if (cachedUserId == null) {
                    _state.value = _state.value.copy(
                        failed = true,
                        statusText = str(R.string.splash_verifikasi_perangkat_gagal_periksa_konek),
                    )
                    return@launch
                }

                // Preserve app access for returning users when the server is temporarily
                // unavailable. Pending network tasks are clearly marked rather than shown
                // as successful; the application also attempts FCM sync in its app scope.
                val registrationStep = _state.value.steps.first { it.id == REGISTER_DEVICE }
                if (registrationStep.status == SplashStepStatus.PENDING) {
                    updateStep(REGISTER_DEVICE, SplashStepStatus.SKIPPED, str(R.string.splash_registration_skipped_using_saved_id))
                }
                listOf(RECORD_DEVICE, FETCH_TOKEN, SYNC_TOKEN).forEach { id ->
                    val step = _state.value.steps.first { it.id == id }
                    if (step.status == SplashStepStatus.PENDING) {
                        updateStep(id, SplashStepStatus.SKIPPED, str(R.string.splash_ditunda_karena_server_tidak_tersedia))
                    }
                }
                _state.value = _state.value.copy(
                    statusText = str(R.string.splash_server_tidak_tersedia_memakai_id),
                    finished = true,
                    failed = false,
                )
                return@launch
            } else {
                userId = resolution.userId
                if (resolution.alreadyRegistered) {
                    updateStep(VERIFY_DEVICE, SplashStepStatus.COMPLETE, str(R.string.splash_device_id_ditemukan_di_server))
                    updateStep(REGISTER_DEVICE, SplashStepStatus.SKIPPED, str(R.string.splash_device_already_registered_no_new_id))
                } else {
                    updateStep(VERIFY_DEVICE, SplashStepStatus.COMPLETE, str(R.string.splash_pemeriksaan_server_berhasil))
                    updateStep(REGISTER_DEVICE, SplashStepStatus.COMPLETE, str(R.string.splash_device_id_berhasil_didaftarkan))
                }
            }

            updateStep(RECORD_DEVICE, SplashStepStatus.ACTIVE, str(R.string.splash_mengirim_status_login_perangkat))
            setStatus(str(R.string.splash_memperbarui_status_perangkat_2))
            val loginRecorded = withTimeoutOrNull(STEP_TIMEOUT_MILLIS) {
                deviceRegistry.recordDeviceLogin(userId)
            }
            if (loginRecorded == true) {
                updateStep(RECORD_DEVICE, SplashStepStatus.COMPLETE, str(R.string.splash_status_perangkat_tersimpan_di_server))
            } else {
                failStep(RECORD_DEVICE, str(R.string.splash_gagal_memperbarui_status_perangkat))
            }

            setStatus(str(R.string.splash_menyinkronkan_notifikasi))
            val tokenSynced = withTimeoutOrNull(STEP_TIMEOUT_MILLIS) {
                FcmTokenRepository.syncTokenToFirestore(
                    getApplication(),
                    onStage = { stage ->
                        when (stage) {
                            FcmTokenSyncStage.FETCHING_TOKEN -> {
                                updateStep(FETCH_TOKEN, SplashStepStatus.ACTIVE, str(R.string.splash_meminta_token_dari_firebase_messaging))
                                setStatus(str(R.string.splash_mengambil_token_notifikasi_2))
                            }
                            FcmTokenSyncStage.WRITING_TOKEN -> {
                                updateStep(FETCH_TOKEN, SplashStepStatus.COMPLETE, str(R.string.splash_token_fcm_berhasil_didapatkan))
                                updateStep(SYNC_TOKEN, SplashStepStatus.ACTIVE, str(R.string.splash_mengirim_token_ke_dokumen_perangkat))
                                setStatus(str(R.string.splash_menyinkronkan_token_ke_server_2))
                            }
                        }
                    },
                )
            }
            if (tokenSynced == true) {
                updateStep(SYNC_TOKEN, SplashStepStatus.COMPLETE, str(R.string.splash_token_tersimpan_di_firestore))
            } else {
                failActiveStep(str(R.string.splash_token_belum_berhasil_disinkronkan))
            }

            val hasFailures = _state.value.steps.any { it.status == SplashStepStatus.FAILED }
            _state.value = _state.value.copy(
                statusText = if (hasFailures) str(R.string.splash_sinkronisasi_selesai_dengan_sebagian_pro) else str(R.string.splash_sinkronisasi_selesai),
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
                step.copy(status = SplashStepStatus.PENDING, detail = str(R.string.splash_menunggu))
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
