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

}
