package com.aether.x

import android.app.Application
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.security.AppCheckInitializer
import com.aether.x.core.security.NativeIntegrityGuard
import com.aether.x.core.security.SignatureGuard
import com.topjohnwu.superuser.Shell

class AetherXApp : Application() {

    companion object {
        init {
            // Konfigurasi shell root global — harus diset sebelum shell pertama dibuat.
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(10)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        // WAJIB dipanggil PALING PERTAMA, sebelum apa pun lain (bahkan
        // sebelum AppCheckInitializer) — kalau APK ini sudah dimodifikasi
        // dan di-resign ulang dengan kunci lain, app harus force-close
        // sesegera mungkin, sebelum sempat menampilkan splash screen atau
        // menyentuh Firebase/Firestore sama sekali. Lihat SignatureGuard.kt
        // untuk detail & batasan proteksi ini.
        SignatureGuard.verifyOrDie(this)

        // Guard TAMBAHAN yang melengkapi baris di atas: SignatureGuard hanya
        // tahu kalau APK di-resign dengan kunci lain — tidak tahu kalau
        // libaetherxsig.so ITU SENDIRI dipatch langsung (byte instruksi
        // diubah lewat lief/radare2/Ghidra) tanpa perlu resign APK sama
        // sekali. Dipanggil di sini, tepat setelah SignatureGuard, supaya
        // urutan cek tetap: (1) APK resmi? (2) kalau ya, .so-nya belum
        // disunting? Lihat integrityguard.cpp & NativeIntegrityGuard.kt.
        NativeIntegrityGuard.verifyOrDie(this)

        // WAJIB dipanggil SEBELUM Firestore dipakai di mana pun (mis. sebelum
        // LicenseRepository/UserIdRepository melakukan panggilan pertama) —
        // App Check harus terpasang sebelum instance FirebaseFirestore dibuat
        // supaya semua request Firestore sejak awal sudah membawa token App
        // Check. Lihat SECURITY.md dan firestore.rules (fungsi isVerifiedApp()).
        AppCheckInitializer.init(this)
        PrivilegeManager.init(this)
    }
}
