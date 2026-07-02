package com.aether.x.ui.membership

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.R
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

/**
 * Menampung seluruh state & logika layar Membership — dipisah dari tab
 * Pengaturan (lihat MEMBERSHIP di [com.aether.x.ui.main.MainScreen]) supaya
 * jadi tab tersendiri di bottom navigation, terpisah dari pengaturan umum
 * aplikasi. Logikanya sama persis dengan yang sebelumnya ada di
 * `SettingsViewModel` — hanya dipindahkan ke sini.
 *
 * Sengaja BUKAN gerbang wajib: aplikasi (termasuk semua tweak di tab Tweak)
 * tetap 100% bisa dipakai tanpa lisensi aktif sama sekali. Layar ini murni
 * status/badge + form aktivasi opsional, mirip menu "Upgrade ke Premium" di
 * banyak aplikasi umum — bukan blocking screen sebelum masuk aplikasi.
 *
 * Penegakan sesungguhnya (satu kode = satu device, tidak bisa dipakai ulang
 * di device lain) tetap ada di [LicenseRepository] dan firestore.rules.
 *
 * STATUS SEKARANG REALTIME: dulu status membership hanya dicek SEKALI lewat
 * [LicenseRepository.revalidate] saat tab ini pertama dibuka — kalau admin
 * mengubah lisensi (revoke / perpanjang) lewat bot Telegram SAAT aplikasi
 * sedang terbuka, badge di layar ini tidak berubah sampai pengguna menutup
 * lalu membuka ulang aplikasi. Sekarang [observeLicenseStatus] berlangganan
 * [LicenseRepository.observe] (Firestore `addSnapshotListener`) sehingga
 * perubahan dari server didorong langsung ke UI tanpa refresh manual.
 */
class MembershipViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)
    private val licenseRepository = LicenseRepository(application)

    /** Device ID (ANDROID_ID) perangkat ini — ditampilkan di tab Membership
     *  begitu langganan aktif, supaya pengguna tahu identitas perangkat yang
     *  terkunci ke lisensinya. Nilainya sama persis dengan yang dipakai
     *  [LicenseRepository]/[com.aether.x.data.UserIdRepository] untuk
     *  penguncian lisensi & pemulihan userId setelah uninstall/install ulang. */
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

    init {
        // Amati licenseKey yang tersimpan di preferences. distinctUntilChanged
        // supaya listener Firestore tidak dibuat ulang tiap kali field LAIN di
        // preferences berubah (mis. slider tweak) — hanya saat licenseKey itu
        // sendiri berubah (aktivasi baru / logout / lisensi dihapus dari cache).
        // collectLatest otomatis membatalkan listener lama sebelum memulai yang
        // baru, jadi tidak ada listener Firestore yang menumpuk/bocor.
        viewModelScope.launch {
            preferences.preferences
                .map { it.licenseKey }
                .distinctUntilChanged()
                .collectLatest { key -> observeLicenseStatus(key) }
        }
    }

    /**
     * Berlangganan status lisensi [key] secara realtime. Kalau [key] null
     * (belum pernah aktivasi / baru saja logout), langsung INACTIVE tanpa ke
     * jaringan. Fungsi ini "menggantung" di collectLatest sampai licenseKey
     * berubah lagi (lihat pemanggil di `init`) — selama itu, setiap perubahan
     * dari Firestore (revoke, perpanjangan expiresAt, dsb) langsung
     * memperbarui `_status`/`_expiresAtMillis`.
     */
    private suspend fun observeLicenseStatus(key: String?) {
        if (key.isNullOrBlank()) {
            _status.value = MembershipUiStatus.INACTIVE
            _expiresAtMillis.value = null
            return
        }

        _status.value = MembershipUiStatus.CHECKING
        licenseRepository.observe(key).collectLatest { result ->
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
                }
                LicenseResult.Revoked, LicenseResult.BoundToOtherDevice, LicenseResult.NotFound -> {
                    preferences.clearLicenseCache()
                    _status.value = MembershipUiStatus.INACTIVE
                    _expiresAtMillis.value = null
                }
                LicenseResult.NetworkError -> {
                    // Offline/listener sempat error: percaya cache lokal
                    // terakhir apa adanya (kalau sempat tersimpan sebagai
                    // Valid sebelumnya) daripada memaksa tampil INACTIVE
                    // hanya karena satu event gagal — listener Firestore
                    // otomatis mencoba lagi begitu koneksi pulih.
                    val cachedExpiry = preferences.preferences.map { it.licenseExpiresAtMillis }.first()
                    _status.value = if (cachedExpiry != null && cachedExpiry > System.currentTimeMillis()) {
                        MembershipUiStatus.ACTIVE
                    } else {
                        MembershipUiStatus.INACTIVE
                    }
                    _expiresAtMillis.value = cachedExpiry
                }
            }
        }
    }

    /**
     * "Logout" dari membership perangkat ini: menghapus cache lisensi lokal
     * supaya kartu status kembali ke INACTIVE dan form aktivasi kode muncul
     * lagi. TIDAK menghapus dokumen `licenses/{key}` di server maupun
     * melepas ikatan device-nya — kode itu tetap terkunci ke device ini di
     * Firestore (sesuai firestore.rules, hanya bot Telegram/admin yang bisa
     * mencabut ikatan). Kalau pengguna mengetik ulang kode yang sama nanti,
     * [LicenseRepository.activate] akan langsung mengenalinya sebagai device
     * yang sama dan mengaktifkannya kembali tanpa error "sudah dipakai
     * perangkat lain".
     */
    fun logout() {
        viewModelScope.launch {
            preferences.clearLicenseCache()
        }
    }

    fun setKeyInput(value: String) {
        // Format lisensi sekarang bebas: huruf besar/kecil apa pun dan angka,
        // tidak lagi dipaksa mengikuti pola "AETX-XXXX-XXXX-XXXX" empat blok
        // saja. Satu-satunya normalisasi yang tetap dilakukan adalah membuang
        // spasi di awal/akhir saat submit (lihat [activate]), supaya kode
        // yang di-copy-paste dengan spasi tambahan tetap valid.
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
        viewModelScope.launch {
            when (val result = licenseRepository.activate(key)) {
                is LicenseResult.Valid -> {
                    preferences.setLicenseCache(key, result.expiresAtMillis)
                    _status.value = MembershipUiStatus.ACTIVE
                    _expiresAtMillis.value = result.expiresAtMillis
                    _keyInput.value = ""
                }
                is LicenseResult.Expired -> {
                    _errorMessage.value = appString(R.string.membership_key_error_expired)
                }
                LicenseResult.Revoked -> {
                    _errorMessage.value = appString(R.string.membership_key_error_revoked)
                }
                LicenseResult.BoundToOtherDevice -> {
                    _errorMessage.value = appString(R.string.membership_key_error_bound)
                }
                LicenseResult.NotFound -> {
                    _errorMessage.value = appString(R.string.membership_key_error_not_found)
                }
                LicenseResult.NetworkError -> {
                    _errorMessage.value = appString(R.string.membership_key_error_network)
                }
            }
            _isSubmitting.value = false
        }
    }

    private fun appString(resId: Int): String = getApplication<Application>().getString(resId)
}
