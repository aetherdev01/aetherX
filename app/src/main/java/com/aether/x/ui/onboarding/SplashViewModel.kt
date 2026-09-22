package com.aether.x.ui.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.DeviceId
import com.aether.x.data.DeviceRegistry
import com.aether.x.data.FcmTokenRepository
import com.aether.x.data.MaintenanceRepository
import com.aether.x.data.UserIdRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tahap startup nyata yang dijalankan splash — tiap tahap merepresentasikan
 * satu panggilan jaringan/Firebase sungguhan, bukan delay simulasi.
 */
enum class SplashStage {
    RESOLVING_IDENTITY,
    REGISTERING_DEVICE,
    SYNCING_NOTIFICATIONS,
    CHECKING_MAINTENANCE,
    DONE,
}

data class SplashUiState(
    val stage: SplashStage = SplashStage.RESOLVING_IDENTITY,
    val progress: Float = 0f,
    val statusText: String = "",
    /** true kalau tahap identity gagal total (offline) — splash berhenti dan menampilkan retry. */
    val failed: Boolean = false,
    val finished: Boolean = false,
)

private const val TAG = "SplashViewModel"
private const val STEP_TIMEOUT_MILLIS = 15_000L

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
            // User ID sudah ke-cache lokal (AetherXPreferences.getSyncedUserId)
            // artinya device ini SUDAH PERNAH resolve+register sebelumnya —
            // bukan pendaftaran baru. Untuk kasus ini splash tidak perlu
            // menampilkan tahap "Mendaftarkan perangkat...": itu di sini
            // cuma nge-update lastLoginAt, jadi dijalankan diam-diam di
            // background tanpa memblokir/menunda progress splash.
            val isReturningUser = preferences.getSyncedUserId() != null

            // 1) Resolusi/registrasi user ID — panggilan Firestore nyata ke
            // devices/{deviceId} lewat UserIdRepository (baca-atau-alokasikan
            // dengan retry+backoff sudah ada di dalamnya).
            emit(SplashStage.RESOLVING_IDENTITY, 0.15f, "Menghubungkan ke server...")
            val userId = withTimeoutOrNull(STEP_TIMEOUT_MILLIS) { userIdRepository.resolveUserId() }

            if (userId == null) {
                Log.w(TAG, "resolveUserId gagal/timeout — kemungkinan offline")
                _state.value = _state.value.copy(
                    failed = true,
                    statusText = "Tidak bisa terhubung ke server. Periksa koneksi internet.",
                )
                return@launch
            }

            // 2) Catat login device ke Firestore (firstLoginAt/lastLoginAt).
            // User baru: ini pendaftaran sungguhan, jadi ditunggu & ditampilkan.
            // User lama (sudah pernah login): jalan di background saja, splash
            // langsung lanjut ke tahap berikutnya tanpa loading tambahan.
            if (isReturningUser) {
                viewModelScope.launch { deviceRegistry.recordDeviceLogin(userId) }
            } else {
                emit(SplashStage.REGISTERING_DEVICE, 0.45f, "Mendaftarkan perangkat...")
                withTimeoutOrNull(STEP_TIMEOUT_MILLIS) { deviceRegistry.recordDeviceLogin(userId) }
            }

            // 3) Sinkronkan token FCM ke Firestore supaya notifikasi
            // maintenance/update/membership bisa diterima device ini.
            emit(SplashStage.SYNCING_NOTIFICATIONS, 0.70f, "Menyinkronkan notifikasi...")
            withTimeoutOrNull(STEP_TIMEOUT_MILLIS) {
                FcmTokenRepository.syncTokenToFirestore(getApplication())
            }

            // 4) Cek sekali status maintenance sebelum masuk ke app — kalau
            // dokumen config/maintenance sedang enabled, MaintenanceGate di
            // MainActivity yang akan menampilkan dialognya; di sini splash
            // hanya memastikan datanya sempat diambil dulu.
            emit(SplashStage.CHECKING_MAINTENANCE, 0.90f, "Memeriksa status layanan...")
            withTimeoutOrNull(STEP_TIMEOUT_MILLIS) { maintenanceRepository.fetchOnce() }

            emit(SplashStage.DONE, 1.00f, "Selesai.")
            _state.value = _state.value.copy(finished = true)
        }
    }

    private fun emit(stage: SplashStage, progress: Float, text: String) {
        _state.value = _state.value.copy(stage = stage, progress = progress, statusText = text)
    }
}
