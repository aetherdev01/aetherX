package com.aether.x.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aether.x.core.ads.AdBlockGuard
import com.aether.x.ui.theme.AccentAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.aether.x.core.locale.str
import com.aether.x.R

/**
 * v3.6 — dialog "Pemblokir Iklan Terdeteksi" untuk pengguna GRATIS.
 *
 * App gratis ini dibiayai iklan, jadi kalau domain iklan diblokir di level
 * jaringan (DNS pribadi/hosts/VPN pemblokir iklan), dialog ini menutup
 * layar sampai pemblokir dimatikan untuk AetherX atau user berlangganan
 * Membership (yang bebas iklan). Dialog TIDAK bisa ditutup lewat back/tap
 * di luar; jalan keluarnya hanya "Cek Ulang" (setelah memutus blokir) atau
 * "Lihat Membership". Saat tab Membership aktif ([suppress]) dialog
 * disembunyikan supaya user tetap bisa berlangganan/aktivasi lisensi.
 *
 * Pengecekan diulang tiap app kembali ke foreground (ON_RESUME), jadi user
 * yang baru mematikan pemblokir lewat pengaturan sistem langsung lolos.
 */
@Composable
fun AdBlockGate(
    isMember: Boolean,
    suppress: Boolean,
    onOpenMembership: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val memberNow by rememberUpdatedState(isMember)

    var blocked by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var recheckTick by remember { mutableIntStateOf(0) }

    suspend fun runCheck() {
        if (memberNow) {
            blocked = false
            return
        }
        checking = true
        val result = AdBlockGuard.check()
        checking = false
        blocked = result.blocked && !memberNow
    }

    // Jeda singkat di awal supaya status membership dari cache sempat termuat
    // sebelum memutuskan apakah user perlu dicek.
    LaunchedEffect(recheckTick) {
        delay(1_500)
        runCheck()
    }

    LaunchedEffect(isMember) {
        if (isMember) blocked = false
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recheckTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!blocked || isMember || suppress) return

    PopupDialog(
        onDismissRequest = onOpenMembership,
        icon = Icons.Outlined.Block,
        iconTint = AccentAmber,
        title = str(R.string.ad_block_gate_pemblokir_iklan_terdeteksi),
        message = str(R.string.ad_block_gate_aetherx_versi_gratis_didukung_iklan) +
            str(R.string.ad_block_gate_dns_pribadi_hosts_atau_vpn) +
            str(R.string.ad_block_gate_membership_pro_agar_bebas_iklan),
        confirmLabel = if (checking) str(R.string.setup_status_checking) else str(R.string.ad_block_gate_cek_ulang),
        onConfirm = {
            if (!checking) {
                scope.launch {
                    runCheck()
                    if (blocked) {
                        context.showAetherToast(str(R.string.ad_block_gate_pemblokir_iklan_masih_aktif))
                    }
                }
            }
        },
        dismissLabel = str(R.string.ad_block_gate_lihat_membership),
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
    )
}
