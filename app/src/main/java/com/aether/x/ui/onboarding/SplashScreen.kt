package com.aether.x.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.DeviceId
import com.aether.x.data.UserIdRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Splash screen di dalam aplikasi (tampil setelah splash sistem Android 12+).
 *
 * Menggantikan layar "Siapkan Akses" lama: alih-alih menunggu pengguna menekan
 * tombol "Minta Izin" satu per satu, layar ini otomatis memicu semua dialog
 * izin yang dibutuhkan (Shizuku, root/su, tulis pengaturan sistem, overlay,
 * notifikasi) begitu aplikasi dibuka, lalu lanjut sendiri ke panduan/main
 * setelah proses selesai — tanpa memblokir pengguna kalau salah satu izin
 * ditolak atau tidak tersedia di perangkatnya.
 *
 * Sejak rework ini, splash juga melakukan koneksi database yang SUNGGUHAN:
 * memanggil [UserIdRepository.resolveUserId] (Firestore) di sini, bukan lagi
 * ditunda sampai TweakScreen pertama kali dibuka. Progres yang ditampilkan
 * ("Menyambungkan ke database…") jadi cerminan proses nyata, bukan delay
 * kosmetik — kalau device sudah pernah dapat ID pengguna, panggilan ini
 * langsung selesai dari cache lokal; kalau belum, splash benar-benar
 * menunggu Firestore (dibatasi timeout supaya tidak menggantung selamanya
 * saat offline). Hasilnya sudah tersimpan di [AetherXPreferences] begitu
 * berhasil, jadi TweakScreen tinggal membacanya tanpa perlu resolve ulang.
 */
@Composable
fun SplashScreen(
    onDone: () -> Unit,
) {
    val context = LocalContext.current

    var statusLabel by remember { mutableStateOf(context.getString(R.string.splash_status_checking)) }
    var finished by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        PrivilegeManager.refreshSupportingPermissions(context)
    }

    // Saat kembali dari layar pengaturan sistem (overlay / write settings),
    // cek ulang status begitu activity kembali ke foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PrivilegeManager.refreshSupportingPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        statusLabel = context.getString(R.string.splash_status_checking)

        // 1. Shizuku & root — otomatis memicu dialog/prompt kalau memungkinkan.
        statusLabel = context.getString(R.string.splash_status_access)
        PrivilegeManager.autoRequestAccess()

        // 2. Izin pendukung: overlay & tulis pengaturan sistem dibuka otomatis
        //    lewat halaman sistem kalau belum aktif, notifikasi lewat dialog
        //    runtime standar Android 13+.
        statusLabel = context.getString(R.string.splash_status_permissions)
        PrivilegeManager.refreshSupportingPermissions(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PrivilegeManager.status.value.notificationsGranted
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            delay(400)
        }

        if (!PrivilegeManager.status.value.overlayGranted) {
            PrivilegeManager.requestOverlayPermission(context)
            delay(600)
        }

        if (!PrivilegeManager.status.value.writeSettingsGranted) {
            PrivilegeManager.requestWriteSettings(context)
            delay(600)
        }

        // 3. Koneksi database (Firestore) — betulan menunggu resolveUserId(),
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

        // Beri sedikit jeda supaya splash tidak berkedip sekilas di perangkat cepat,
        // lalu lanjut apapun hasil izinnya — pengguna tetap bisa membuka ulang
        // izin yang terlewat lewat menu "Kelola Akses" di halaman utama.
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
    Scaffold(containerColor = Color(0xFF0A0A0C)) { padding ->
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
                color = Color.White,
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )

            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 28.dp)
                    .size(28.dp),
                color = Color(0xFF7FA8FF),
                strokeWidth = 2.5.dp,
            )
        }
    }
}
