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
// `System.loadLibrary("aetherxsig")` berhasil — tidak perlu dipanggil
// manual dari Kotlin mana pun.

#include <jni.h>

#include "native_symbols.h"

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

    return JNI_VERSION_1_6;
}
