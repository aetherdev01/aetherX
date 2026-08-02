package com.aether.x.ui.membership

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.R
import com.aether.x.core.notification.AetherXNotifier
import com.aether.x.core.security.AttemptGuardResult
import com.aether.x.core.security.LicenseAttemptGuard
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.DeviceId
import com.aether.x.data.LicenseRepository
import com.aether.x.data.LicenseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class MembershipUiStatus { CHECKING, INACTIVE, ACTIVE, EXPIRED }

enum class ActivationStage { CHECKING_GUARD, CONNECTING, VERIFYING }

class MembershipViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)
    private val licenseRepository = LicenseRepository(application)

    private val attemptGuard = LicenseAttemptGuard(preferences)

    val deviceId: String = DeviceId.read(application)

    private val _status = MutableStateFlow(MembershipUiStatus.CHECKING)
    val status: StateFlow<MembershipUiStatus> = _status.asStateFlow()

    private val _expiresAtMillis = MutableStateFlow<Long?>(null)
    val expiresAtMillis: StateFlow<Long?> = _expiresAtMillis.asStateFlow()

    private val _keyInput = MutableStateFlow("")
    val keyInput: StateFlow<String> = _keyInput.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _activationStage = MutableStateFlow(ActivationStage.CHECKING_GUARD)
    val activationStage: StateFlow<ActivationStage> = _activationStage.asStateFlow()

    init {

        viewModelScope.launch {
            preferences.preferences
                .map { it.licenseKey }
                .distinctUntilChanged()
                .collectLatest { key -> observeLicenseStatus(key) }
        }
    }

    private suspend fun observeLicenseStatus(key: String?) {
        if (key.isNullOrBlank()) {
            _status.value = MembershipUiStatus.INACTIVE
            _expiresAtMillis.value = null
            return
        }

        _status.value = MembershipUiStatus.CHECKING
        licenseRepository.observe(key).collectLatest { result ->

            val previousStatus = _status.value
            when (result) {
                is LicenseResult.Valid -> {
                    preferences.setLicenseCache(key, result.expiresAtMillis)
                    _status.value = MembershipUiStatus.ACTIVE
                    _expiresAtMillis.value = result.expiresAtMillis
                }
                is LicenseResult.Expired -> {
                    preferences.clearLicenseCache()
                    _status.value = MembershipUiStatus.EXPIRED
                    _expiresAtMillis.value = result.expiredAtMillis
                    if (previousStatus == MembershipUiStatus.ACTIVE) {
                        notifyLicenseChanged(R.string.notif_license_expired_text)
                    }
                }
                LicenseResult.Revoked, LicenseResult.BoundToOtherDevice, LicenseResult.NotFound -> {
                    preferences.clearLicenseCache()
                    _status.value = MembershipUiStatus.INACTIVE
                    _expiresAtMillis.value = null
                    if (previousStatus == MembershipUiStatus.ACTIVE) {
                        notifyLicenseChanged(R.string.notif_license_revoked_text)
                    }
                }
                LicenseResult.NetworkError -> {

                    val cachedExpiry = preferences.preferences.map { it.licenseExpiresAtMillis }.first()
                    _status.value = if (cachedExpiry != null && cachedExpiry > System.currentTimeMillis()) {
                        MembershipUiStatus.ACTIVE
                    } else {
                        MembershipUiStatus.INACTIVE
                    }
                    _expiresAtMillis.value = cachedExpiry
                }
                is LicenseResult.RateLimited -> {
                    // Jalur observe() memanggil revalidateLicense yang TIDAK
                    // dikenai rate limit server (lihat KDoc
                    // revalidateLicense.ts) — kalau tetap muncul di sini,
                    // kemungkinan besar transient (mis. proxy/NAT berbagi
                    // App Check dengan device lain yang sedang brute force).
                    // Jangan ubah status yang sudah ada (biarkan status
                    // ACTIVE lama tetap dipercaya sampai polling berikutnya
                    // berhasil), supaya pengguna sah tidak tiba-tiba
                    // ter-downgrade ke INACTIVE akibat gangguan sesaat ini.
                }
            }
        }
    }

    private fun notifyLicenseChanged(textRes: Int) {
        val app = getApplication<Application>()
        AetherXNotifier.notify(
            context = app,
            kind = AetherXNotifier.NotificationKind.GENERAL,
            title = app.getString(R.string.notif_license_changed_title),
            text = app.getString(textRes),
        )
    }

    fun logout() {
        viewModelScope.launch {
            preferences.clearLicenseCache()
        }
    }

    fun setKeyInput(value: String) {

        _keyInput.value = value
        _errorMessage.value = null
    }

    fun activate() {
        val key = _keyInput.value.trim()
        if (key.isEmpty()) {
            _errorMessage.value = appString(R.string.membership_key_error_empty)
            return
        }
        _errorMessage.value = null
        _isSubmitting.value = true
        _activationStage.value = ActivationStage.CHECKING_GUARD
        viewModelScope.launch {

            when (val guardCheck = attemptGuard.checkBeforeAttempt()) {
                is AttemptGuardResult.Locked -> {
                    _errorMessage.value = appString(R.string.membership_key_error_locked)
                        .format(guardCheck.remainingSeconds)
                    _isSubmitting.value = false
                    return@launch
                }
                AttemptGuardResult.Allowed -> Unit
            }

            _activationStage.value = ActivationStage.CONNECTING

            when (val result = licenseRepository.activate(key)) {
                is LicenseResult.Valid -> {

                    _activationStage.value = ActivationStage.VERIFYING
                    preferences.setLicenseCache(key, result.expiresAtMillis)
                    attemptGuard.recordSuccess()
                    _status.value = MembershipUiStatus.ACTIVE
                    _expiresAtMillis.value = result.expiresAtMillis
                    _keyInput.value = ""
                }
                is LicenseResult.Expired -> {
                    _activationStage.value = ActivationStage.VERIFYING
                    attemptGuard.recordFailure()
                    _errorMessage.value = appString(R.string.membership_key_error_expired)
                }
                LicenseResult.Revoked -> {
                    _activationStage.value = ActivationStage.VERIFYING
                    attemptGuard.recordFailure()
                    _errorMessage.value = appString(R.string.membership_key_error_revoked)
                }
                LicenseResult.BoundToOtherDevice -> {
                    _activationStage.value = ActivationStage.VERIFYING
                    attemptGuard.recordFailure()
                    _errorMessage.value = appString(R.string.membership_key_error_bound)
                }
                LicenseResult.NotFound -> {

                    _activationStage.value = ActivationStage.VERIFYING
                    attemptGuard.recordFailure()
                    _errorMessage.value = appString(R.string.membership_key_error_not_found)
                }
                LicenseResult.NetworkError -> {

                    _errorMessage.value = appString(R.string.membership_key_error_network)
                }
                is LicenseResult.RateLimited -> {
                    // Rate limit SERVER-SIDE (lihat rateLimiter.ts) — beda
                    // dari attemptGuard.checkBeforeAttempt() di atas yang
                    // hanya lapisan client (UX cepat, bisa di-bypass).
                    // Kalau server yang menolak, itu berarti guard client
                    // entah tidak sinkron atau dilewati — tetap tampilkan
                    // pesan yang sama ke pengguna untuk konsistensi, dan
                    // catat sebagai percobaan gagal juga di guard client
                    // supaya kedua lapisan sinkron kembali.
                    attemptGuard.recordFailure()
                    _errorMessage.value = appString(R.string.membership_key_error_locked)
                        .format(result.remainingSeconds)
                }
            }
            _isSubmitting.value = false
        }
    }

    private fun appString(resId: Int): String = getApplication<Application>().getString(resId)
}
