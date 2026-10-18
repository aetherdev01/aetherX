package com.aether.x.ui.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.AetherXApp
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.DeviceId
import com.aether.x.data.DeviceRegistry
import com.aether.x.data.FcmTokenRepository
import com.aether.x.data.MaintenanceRepository
import com.aether.x.data.UserIdRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stages are tied to real startup work; no artificial progress delays. */
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
    val statusText: String = "Menyiapkan AetherX...",
    /** Only a failure to resolve/register identity blocks a first-time launch. */
    val failed: Boolean = false,
    val finished: Boolean = false,
)

private const val TAG = "SplashViewModel"
private const val IDENTITY_TIMEOUT_MILLIS = 15_000L
private const val STARTUP_STEP_TIMEOUT_MILLIS = 7_000L
private const val BACKGROUND_STEP_TIMEOUT_MILLIS = 10_000L

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
            // Returning users do not wait on network round-trips. Cache is the
            // fast path; all writes/syncs continue in the application scope.
            val cachedUserId = preferences.getSyncedUserId()
            if (cachedUserId != null) {
                finishFastPath(cachedUserId)
                return@launch
            }

            emit(SplashStage.RESOLVING_IDENTITY, 0.12f, "Menghubungkan ke server...")
            val resolution = withTimeoutOrNull(IDENTITY_TIMEOUT_MILLIS) {
                userIdRepository.resolveUserIdentity {
                    emit(
                        SplashStage.REGISTERING_DEVICE,
                        0.42f,
                        "Mendaftarkan Device ID...",
                    )
                }
            }

            if (resolution == null) {
                Log.w(TAG, "Resolusi identitas gagal/timeout; kemungkinan server tidak tersedia")
                _state.value = _state.value.copy(
                    failed = true,
                    statusText = "Koneksi gagal — periksa internet",
                )
                return@launch
            }

            // A reinstall/cleared app data can have no local cache while the
            // Device ID is still registered on the server. Treat it as returning.
            if (resolution.alreadyRegistered) {
                finishFastPath(resolution.userId)
                return@launch
            }

            // New device: UserIdRepository has just created devices/{deviceId}
            // and cached the new ID, so we can immediately sync its FCM token.
            emit(
                SplashStage.SYNCING_NOTIFICATIONS,
                0.72f,
                "Menyinkronkan token notifikasi...",
            )
            withTimeoutOrNull(STARTUP_STEP_TIMEOUT_MILLIS) {
                FcmTokenRepository.syncTokenToFirestore(getApplication())
            }

            emit(SplashStage.CHECKING_MAINTENANCE, 0.90f, "Memeriksa status server...")
            withTimeoutOrNull(STARTUP_STEP_TIMEOUT_MILLIS) {
                maintenanceRepository.fetchOnce()
            }

            emit(SplashStage.DONE, 1f, "Siap")
            _state.value = _state.value.copy(finished = true)
        }
    }

    /**
     * For registered devices, show the brand splash only for its minimum
     * animation time. Server writes run independently and survive navigation.
     */
    private fun finishFastPath(userId: String) {
        val app = getApplication<Application>()
        AetherXApp.launchInAppScope {
            launch {
                withTimeoutOrNull(BACKGROUND_STEP_TIMEOUT_MILLIS) {
                    deviceRegistry.recordDeviceLogin(userId)
                }
                withTimeoutOrNull(BACKGROUND_STEP_TIMEOUT_MILLIS) {
                    FcmTokenRepository.syncTokenToFirestore(app)
                }
            }
            launch {
                withTimeoutOrNull(BACKGROUND_STEP_TIMEOUT_MILLIS) {
                    maintenanceRepository.fetchOnce()
                }
            }
        }

        _state.value = SplashUiState(
            stage = SplashStage.DONE,
            progress = 1f,
            statusText = "Siap",
            finished = true,
        )
    }

    private fun emit(stage: SplashStage, progress: Float, text: String) {
        _state.value = _state.value.copy(
            stage = stage,
            progress = progress,
            statusText = text,
        )
    }
}
