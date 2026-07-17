package com.aether.x.core.booster

/**
 * Saklar sementara untuk fitur Game Booster — DIMINTA lewat perintah
 * "sekarang sementara jangan tampilkan floating gamebooster nya saat buka
 * game".
 *
 * [autoTriggerOnGameOpenEnabled] HANYA mengontrol jalur OTOMATIS di
 * [com.aether.x.core.monitor.GameProfileMonitorService.handleGameBoosterAutoTrigger]
 * (floating panel muncul SENDIRI begitu app mendeteksi game dikenal
 * dibuka, dari mana pun — bukan lewat AetherX). Jalur MANUAL (pengguna
 * memilih game dari drawer Game Booster AetherX sendiri lewat
 * [com.aether.x.ui.booster.GameBoosterScreen] -> [com.aether.x.ui.booster.GameBoosterSplashActivity])
 * TIDAK terpengaruh sama sekali oleh flag ini — pengguna yang secara
 * eksplisit menekan tombol "mulai boost" tetap mendapat splash + floating
 * panel seperti biasa, karena permintaan "jangan tampilkan saat buka
 * game" merujuk ke kemunculan OTOMATIS yang tidak diminta, bukan alur
 * yang memang sengaja dipicu pengguna sendiri.
 *
 * `false` untuk sementara menonaktifkan auto-trigger TANPA menghapus
 * service/logic-nya sama sekali — tinggal diubah balik ke `true` kapan
 * saja untuk mengaktifkan lagi.
 */
object GameBoosterFeatureFlag {
    var autoTriggerOnGameOpenEnabled: Boolean = false
}
