#pragma once

#include <jni.h>

extern "C" {

// nfgp: turunan device fingerprint hash (HMAC-SHA256) dari identifier
// perangkat mentah — lihat devicefingerprint.h/.cpp untuk kontrak dan
// alasan lengkap, dan DeviceFingerprint.kt untuk sisi pemanggil.
JNIEXPORT jbyteArray JNICALL nfgp(JNIEnv* env, jobject thiz, jbyteArray rawInput);

// nsmc/nsmg/nsmr: monitor CPU/GPU real-time (lihat sysmonitor.h/.cpp untuk
// implementasi, RootSystemMonitor.kt untuk sisi pemanggil). ROOT-ONLY di
// sisi Kotlin (lihat KDoc RootSystemMonitor.kt) — native sendiri tidak
// melakukan pengecekan root, lihat catatan gating di sysmonitor.h.
//
//   nsmc(JNIEnv*, jobject) -> float[] { aggregateLoad, core0, core1, ... }
//     (elemen pertama = agregat, sisanya per-core; nilai -1 = belum ada
//     sampel pembanding/tidak terbaca). Return null kalau /proc/stat sama
//     sekali tidak bisa dibaca.
//   nsmg(JNIEnv*, jobject) -> float[2] { loadPercent, freqMhz } (elemen
//     -1 kalau path sysfs terkait tidak ditemukan). Return null kalau
//     tidak satu pun path GPU dikenal berhasil dibaca.
//   nsmr(JNIEnv*, jobject) -> void, reset delta CPU internal (panggil
//     saat monitor distop lalu dimulai ulang).
JNIEXPORT jfloatArray JNICALL nsmc(JNIEnv* env, jobject thiz);
JNIEXPORT jfloatArray JNICALL nsmg(JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL nsmr(JNIEnv* env, jobject thiz);

// nrmc: baca snapshot RAM (v3.5, lihat rammonitor.h/.cpp/_jni.cpp) —
// dipakai RamMonitor.kt. Beda dari nsmc/nsmg/nsmr di atas: TIDAK
// root-gated, TIDAK punya state delta (/proc/meminfo adalah snapshot
// absolut, bukan cumulative counter seperti /proc/stat).
//   nrmc(JNIEnv*, jobject) -> float[4] { totalKb, availableKb, swapTotalKb, swapFreeKb }
JNIEXPORT jfloatArray JNICALL nrmc(JNIEnv* env, jobject thiz);

}
