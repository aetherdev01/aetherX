package com.aether.x.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.DeviceId
import com.aether.x.data.UserIdRepository
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.BgVoid
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Splash screen di dalam aplikasi (tampil setelah splash sistem Android 12+).
 *
 * REWORK: splash TIDAK LAGI memicu dialog/prompt izin apapun (Shizuku, root,
 * overlay, tulis pengaturan sistem, notifikasi). Sebelumnya semua dialog itu
 * ditembakkan bertubi-tubi secara diam-diam di sini, yang justru membuat
 * pengguna kaget dengan banyak popup sistem beruntun tanpa konteks. Sekarang
 * splash murni loading singkat: baca status root/Shizuku yang SUDAH ada
 * secara silent (tanpa dialog) dan sambungkan ke database. Permintaan izin
 * yang sesungguhnya (dengan penjelasan & kontrol per-izin) dipindah ke
 * [com.aether.x.ui.onboarding.PermissionSetupScreen] sebagai langkah
 * tersendiri langsung setelah Splash (Guide sudah dihapus dari alur),
 * supaya pengguna tahu APA yang diminta dan KENAPA sebelum dialog sistem
 * muncul.
 *
 * Koneksi database (Firestore) tetap dilakukan di sini: memanggil
 * [UserIdRepository.resolveUserId] SUNGGUHAN, bukan delay kosmetik — kalau
 * device sudah pernah dapat ID pengguna, panggilan ini langsung selesai dari
 * cache lokal; kalau belum, splash menunggu Firestore (dibatasi timeout
 * supaya tidak menggantung selamanya saat offline). Hasilnya tersimpan di
 * [AetherXPreferences] begitu berhasil, jadi TweakScreen tinggal membacanya
 * tanpa perlu resolve ulang.
 */
@Composable
fun SplashScreen(
    onDone: () -> Unit,
) {
    val context = LocalContext.current

    var statusLabel by remember { mutableStateOf(context.getString(R.string.splash_status_checking)) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 1. Cek status akses yang SUDAH ada secara silent — tidak memicu
        //    dialog/prompt apapun. Ini hanya membaca kondisi terkini supaya
        //    layar-layar berikutnya (Permission, Main) mulai dengan status
        //    yang akurat, bukan menunda-nunda meminta izin baru.
        statusLabel = context.getString(R.string.splash_status_checking)
        PrivilegeManager.refreshAll()
        PrivilegeManager.refreshSupportingPermissions(context)

        // 2. Koneksi database (Firestore) — betulan menunggu resolveUserId(),
        //    bukan cuma delay kosmetik. Dibatasi timeout supaya splash tetap
        //    lanjut kalau perangkat offline; TweakScreen akan otomatis coba
        //    lagi lewat retryResolveUserIdIfMissing() begitu koneksi pulih.
        statusLabel = context.getString(R.string.splash_status_database)
        val preferences = AetherXPreferences(context)
        val deviceId = DeviceId.read(context)
        val userIdRepository = UserIdRepository(preferences, deviceId)
        withTimeoutOrNull(8_000L) {
            userIdRepository.resolveUserId()
        }

        // Beri sedikit jeda supaya splash tidak berkedip sekilas di perangkat cepat.
        statusLabel = context.getString(R.string.splash_status_ready)
        delay(350)
        finished = true
    }

    LaunchedEffect(finished) {
        if (finished) onDone()
    }

    SplashScreenContent(statusLabel = statusLabel)
}

@Composable
private fun SplashScreenContent(statusLabel: String) {
    Scaffold(containerColor = BgVoid) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )

            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 28.dp)
                    .size(28.dp),
                color = AccentBlue,
                strokeWidth = 2.5.dp,
            )
        }
    }
}
