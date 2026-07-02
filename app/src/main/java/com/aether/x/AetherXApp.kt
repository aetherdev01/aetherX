package com.aether.x

import android.app.Application
import com.aether.x.core.permission.PrivilegeManager
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
        PrivilegeManager.init(this)
    }
}
