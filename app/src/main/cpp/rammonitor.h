#pragma once

// rammonitor.h — kontrak untuk rammonitor.cpp (v3.5, fitur RAM Cleaner).
//
// LATAR BELAKANG: sama alasannya dengan sysmonitor.h (CPU/GPU) — baca
// /proc/meminfo lewat File/BufferedReader biasa di Kotlin cukup untuk
// tampilan sekali lihat, tapi kartu RAM Cleaner ini di-refresh tiap kali
// user membuka Dashboard DAN setelah aksi "Bersihkan RAM" (butuh baca
// SEBELUM dan SESUDAH supaya bisa tampilkan berapa MB yang berhasil
// dibebaskan) — dua kali baca berurutan cepat, native lebih murah.
//
// TIDAK SEPERTI sysmonitor.h (CPU/GPU): modul ini TIDAK root-gated sama
// sekali, di kedua sisi (native maupun kebijakan UI Kotlin) — /proc/meminfo
// adalah ringkasan memori SISTEM (bukan per-proses), sudah world-readable
// di semua versi Android termasuk yang terbaru (beda dari /proc/<pid>/*
// yang mulai dibatasi Android 7+ hidepid). Jadi RamMonitor.kt aman dipakai
// tanpa PrivilegeManager.status check sama sekali.
//
// AKSI "Bersihkan RAM" itu sendiri (drop_caches via root, killBackgroundProcesses
// non-root) SENGAJA TIDAK ada di modul native ini — itu murni orkestrasi
// Kotlin (lihat RamCleanerViewModel.kt), modul ini HANYA baca angka.

#include <cstdint>

namespace aetherx::rammonitor {

// Semua nilai dalam KB (satuan asli /proc/meminfo), float supaya satu
// jalur JNI (jfloatArray) dengan sysmonitor.h — device RAM modern (sampai
// puluhan GB = puluhan juta KB) masih jauh di bawah batas presisi float
// 32-bit (~16.7 juta) MEPET tapi cukup untuk display purposes (dibulatkan
// ke MB/GB di Kotlin, bukan dipakai untuk kalkulasi presisi tinggi).
// -1 pada field manapun berarti baris itu tidak ditemukan di
// /proc/meminfo perangkat ini (jarang, tapi bukan alasan gagal total).
struct RamSnapshot {
    float totalKb = -1.0f;
    float availableKb = -1.0f;   // MemAvailable (estimasi kernel, sudah
                                  // memperhitungkan cache yang bisa di-reclaim
                                  // — metrik "RAM bebas sebenarnya" yang
                                  // benar, BUKAN MemFree mentah yang jauh
                                  // lebih pesimis/tidak representatif).
    float swapTotalKb = -1.0f;   // di kebanyakan device Android ini adalah
                                  // ukuran zRAM, bukan swap partition fisik.
    float swapFreeKb = -1.0f;
};

// nrmRead: baca /proc/meminfo SEKALI (parse baris demi baris, berhenti
// begitu semua field yang dicari sudah ketemu — tidak perlu baca sampai
// akhir file yang bisa berisi 40+ baris). Return false HANYA kalau file
// gagal dibuka sama sekali; kalau terbuka tapi sebagian baris tidak
// ditemukan, field terkait di `out` tetap -1 dan fungsi tetap return true
// (konsisten dengan pola nsmReadGpu di sysmonitor.h).
bool nrmRead(RamSnapshot* out);

}  // namespace aetherx::rammonitor
