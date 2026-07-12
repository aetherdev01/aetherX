// jni_onload.cpp
//
// SATU-SATUNYA tempat yang menghubungkan nama method Kotlin (`external
// fun`) ke fungsi C++ dengan nama PENDEK (nvfy/nvfy2/nvint — lihat
// sigcheck.cpp & integrityguard.cpp) — lewat RegisterNatives(), bukan lewat
// konvensi penamaan panjang `Java_com_aether_x_..._namaMethod` yang
// membuat JVM mencari otomatis berdasarkan nama simbol.
//
// KENAPA INI PERLU: JVM SECARA DEFAULT mencari native method lewat nama
// simbol yang dibentuk dari package+class+method Kotlin (mis.
// `Java_com_aether_x_core_security_SignatureGuard_nativeVerify`). Supaya
// nama simbol C++ bisa PENDEK (nvfy) tanpa membuat JVM gagal menemukannya
// (UnsatisfiedLinkError), pemetaan nama harus didaftarkan MANUAL sekali
// saat library di-load — itulah yang dilakukan JNI_OnLoad di bawah ini.
// Ini murni memindahkan pemetaan nama dari "otomatis lewat simbol panjang"
// jadi "eksplisit lewat tabel pendek di bawah", TIDAK mengubah perilaku
// apa pun di sisi Kotlin (`external fun` di SignatureGuard.kt dan
// NativeIntegrityGuard.kt tetap dideklarasikan persis seperti sebelumnya).
//
// JNI_OnLoad dipanggil OTOMATIS oleh JVM tepat sekali, segera setelah
// `System.loadLibrary("aetherX")` berhasil — tidak perlu dipanggil
// manual dari Kotlin mana pun.

#include <jni.h>
#include <android/log.h>

#include "native_symbols.h"

#define LOG_TAG "AetherXJNI"

namespace {

// Signature JNI method: "(argumen)returnType" — lihat dokumentasi JNI
// untuk kode tipe (B=byte, [B=byte[], I=int, Z=boolean).
const JNINativeMethod kSignatureGuardMethods[] = {
    // fun nativeVerify(actualHashBytes: ByteArray): Boolean
    {"nativeVerify", "([B)Z", reinterpret_cast<void*>(nvfy)},
    // fun nativeVerifyRecheck(actualHashBytes: ByteArray): Boolean
    {"nativeVerifyRecheck", "([B)Z", reinterpret_cast<void*>(nvfy2)},
};

const JNINativeMethod kIntegrityGuardMethods[] = {
    // fun nativeVerifyIntegrity(): Int
    {"nativeVerifyIntegrity", "()I", reinterpret_cast<void*>(nvint)},
};

const JNINativeMethod kAdBlockDetectorMethods[] = {
    // fun nativeDetectVpnInterface(): Boolean
    {"nativeDetectVpnInterface", "()Z", reinterpret_cast<void*>(nvpn)},
    // fun nativeMatchAdBlockDns(dnsServers: Array<String>): Boolean
    {"nativeMatchAdBlockDns", "([Ljava/lang/String;)Z", reinterpret_cast<void*>(ndns)},
    // fun nativeMatchAdBlockModule(moduleListing: String): Boolean
    {"nativeMatchAdBlockModule", "(Ljava/lang/String;)Z", reinterpret_cast<void*>(nmod)},
};

// Mendaftarkan satu tabel method ke satu kelas Kotlin lewat nama binary
// (pakai '/' bukan '.', konvensi JNI). Return false kalau kelasnya tidak
// ketemu atau RegisterNatives gagal — dicek di JNI_OnLoad supaya kegagalan
// silent tidak lolos begitu saja.
bool registerClass(JNIEnv* env, const char* classBinaryName,
                    const JNINativeMethod* methods, int methodCount) {
    jclass clazz = env->FindClass(classBinaryName);
    if (clazz == nullptr) {
        return false;
    }
    bool ok = env->RegisterNatives(clazz, methods, methodCount) == JNI_OK;
    env->DeleteLocalRef(clazz);
    return ok;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    const bool sigOk = registerClass(
        env, "com/aether/x/core/security/SignatureGuard",
        kSignatureGuardMethods,
        sizeof(kSignatureGuardMethods) / sizeof(kSignatureGuardMethods[0]));

    const bool integrityOk = registerClass(
        env, "com/aether/x/core/security/NativeIntegrityGuard",
        kIntegrityGuardMethods,
        sizeof(kIntegrityGuardMethods) / sizeof(kIntegrityGuardMethods[0]));

    // Kalau salah satu gagal didaftarkan, kembalikan JNI_ERR — Kotlin akan
    // melihat ini sebagai kegagalan System.loadLibrary(), dan
    // SignatureGuard/NativeIntegrityGuard sama-sama membungkus pemanggilan
    // native mereka dengan runCatching { }.getOrDefault(false/MISMATCH) —
    // artinya app akan force-close (fail-closed), BUKAN diam-diam
    // menganggap valid. Ini konsisten dengan filosofi "gagal aman" guard
    // keamanan lain di app ini.
    if (!sigOk || !integrityOk) {
        return JNI_ERR;
    }

    // AdBlockDetector (lihat adblockguard.cpp) SENGAJA TIDAK ikut
    // menentukan JNI_ERR di atas — ini BUKAN guard keamanan (cuma sinyal
    // produk untuk fitur deteksi adblock), jadi kegagalan registrasinya
    // TIDAK BOLEH ikut menjatuhkan seluruh library (yang lewat efek
    // samping shared-library ini akan ikut membuat sigOk/integrityOk
    // gagal juga, dan lewat filosofi fail-closed guard keamanan di atas,
    // MEMAKSA APP FORCE-CLOSE — sama sekali tidak proporsional untuk
    // sekadar fitur deteksi adblock yang gagal register). Kalau gagal,
    // cukup dicatat sebagai warning; AdBlockDetector.kt sisi Kotlin sudah
    // membungkus setiap pemanggilan native-nya sendiri dengan
    // runCatching { }.getOrDefault(false) juga, jadi fitur ini otomatis
    // "diam-diam tidak aktif" (bukan crash) kalau baris ini sampai gagal.
    const bool adBlockOk = registerClass(
        env, "com/aether/x/core/ads/AdBlockDetector",
        kAdBlockDetectorMethods,
        sizeof(kAdBlockDetectorMethods) / sizeof(kAdBlockDetectorMethods[0]));
    if (!adBlockOk) {
        __android_log_print(
            ANDROID_LOG_WARN, LOG_TAG,
            "Registrasi AdBlockDetector gagal — fitur deteksi adblock "
            "nonaktif untuk sesi ini, TIDAK memengaruhi guard keamanan lain.");
    }

    return JNI_VERSION_1_6;
}
