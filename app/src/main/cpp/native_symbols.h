#pragma once

#include <jni.h>

extern "C" {

JNIEXPORT jboolean JNICALL nvfy(JNIEnv* env, jobject thiz, jbyteArray actualHashBytes);
JNIEXPORT jboolean JNICALL nvfy2(JNIEnv* env, jobject thiz, jbyteArray actualHashBytes);

JNIEXPORT jint JNICALL nvint(JNIEnv* env, jobject thiz);

// nvpn (deteksi VPN via getifaddrs) SUDAH DIHAPUS — lihat catatan di
// adblockguard.cpp kenapa deteksi VPN pindah sepenuhnya ke
// ConnectivityManager/NetworkCapabilities di Kotlin (AdBlockDetector.kt).
JNIEXPORT jboolean JNICALL ndns(JNIEnv* env, jobject thiz, jobjectArray dnsServers);
JNIEXPORT jboolean JNICALL nmod(JNIEnv* env, jobject thiz, jstring moduleListing);

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

}
