package com.aether.x

import android.app.Application
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.security.AppCheckInitializer
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
        // WAJIB dipanggil SEBELUM Firestore dipakai di mana pun (mis. sebelum
        // LicenseRepository/UserIdRepository melakukan panggilan pertama) —
        // App Check harus terpasang sebelum instance FirebaseFirestore dibuat
        // supaya semua request Firestore sejak awal sudah membawa token App
        // Check. Lihat SECURITY.md dan firestore.rules (fungsi isVerifiedApp()).
        AppCheckInitializer.init(this)
        PrivilegeManager.init(this)
    }
}
