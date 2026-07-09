package com.aether.x.core.booster

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Sumber kebenaran TUNGGAL untuk [GameBoosterSession] yang sedang aktif —
 * dipegang sebagai singleton proses (bukan disimpan ke DataStore, karena
 * sesi ini murni RUNTIME, tidak perlu persist antar proses/reboot) supaya
 * [com.aether.x.core.overlay.GameBoosterOverlayService] (yang menjalankan
 * sesi & floating sidebar) dan [com.aether.x.ui.booster.GameBoosterScreen]
 * (layar penuh dari drawer, yang bisa dibuka BERSAMAAN dengan sesi yang
 * sedang berjalan kalau pengguna kembali ke AetherX tanpa menutup game)
 * SELALU melihat & memodifikasi state sesi yang SAMA PERSIS — tidak ada
 * dua salinan state yang bisa bentrok.
 */
object GameBoosterSessionHolder {

    private val _session = MutableStateFlow<GameBoosterSession?>(null)
    val session: StateFlow<GameBoosterSession?> = _session.asStateFlow()

    fun start(session: GameBoosterSession) {
        _session.value = session
    }

    fun update(transform: (GameBoosterSession) -> GameBoosterSession) {
        _session.update { current -> current?.let(transform) }
    }

    fun clear() {
        _session.value = null
    }
}
