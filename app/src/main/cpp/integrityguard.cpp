// integrityguard.cpp
//
// Guard lapis TAMBAHAN: memverifikasi bahwa fungsi-fungsi kritis di dalam
// libaetherxsig.so ITU SENDIRI belum dipatch (byte instruksi diubah) sejak
// APK ini dibuild — melengkapi sigcheck.cpp yang memverifikasi signing cert
// APK, tapi tidak tahu kalau .so-nya sendiri sudah "disunting" langsung.
//
// KENAPA INI PERLU (celah yang ditutup):
// sigcheck.cpp mencegah APK di-resign dengan kunci lain. TAPI kalau orang
// tidak perlu resign — cukup patch instruksi `cmp`/branch di dalam
// libaetherxsig.so memakai lief/radare2/Ghidra (mis. ubah instruksi yang
// membandingkan hash jadi selalu "equal", atau ubah `return match` jadi
// `return true` tanpa peduli isi match) — signature check sigcheck.cpp bisa
// dilewati TANPA menyentuh signing cert APK sama sekali (APK tetap
// ditandatangani dengan kunci asli, resmi, valid). Guard ini menutup celah
// itu dengan memverifikasi byte-byte MESIN dari fungsi-fungsi kritis
// sendiri, bukan cuma hasil eksekusinya.
//
// CARA KERJA:
// 1. Saat build release, kita compile SEKALI, hitung checksum atas byte
//    code aktual dari nativeVerify/nativeVerifyRecheck (sigcheck.cpp) yang
//    ada di section .text hasil compile itu, lalu hardcode checksum-nya di
//    sini (lihat kExpectedChecksum di bawah, encoded dengan pola XOR yang
//    sama seperti sigcheck.cpp — bukan plaintext).
// 2. Saat app berjalan, fungsi ini membaca ULANG byte-byte tersebut
//    langsung dari memory proses yang sedang berjalan (bukan dari file di
//    disk — supaya juga menutup kemungkinan .so di-map dari lokasi lain
//    yang sudah dipatch tapi trik lain menahan file asli tetap utuh),
//    hitung checksum yang sama, dan bandingkan.
// 3. Kalau ada satu byte instruksi saja yang berubah dari hasil compile
//    asli (mis. `cmp w0, #0` diubah jadi `mov w0, #1`), checksum akan beda
//    total dan verifikasi ini gagal -> app force-close (lihat
//    NativeIntegrityGuard.kt).
//
// CATATAN JUJUR SOAL BATASAN (sama seperti sigcheck.cpp): ini BUKAN
// pertahanan sempurna.
// - Orang yang sudah tahu pola ini bisa menghitung ulang checksum yang
//   BENAR untuk versi hasil patch-annya sendiri, lalu patch juga
//   kExpectedChecksum di sini supaya cocok — effort naik (harus paham dua
//   titik, bukan satu), tapi tetap mungkin bagi yang cukup gigih.
// - Frida bisa hook fungsi verifikasi checksum ini juga (sama seperti bisa
//   hook nativeVerify) supaya selalu return true, tanpa perlu patch byte
//   apa pun secara permanen di file .so.
// - Tujuannya tetap MENAIKKAN EFFORT untuk crack casual, melengkapi
//   sigcheck.cpp + validasi server (LicenseRepository/firestore.rules),
//   bukan menggantikannya.
//
// Checksum di bawah dihitung dari BUILD RELEASE SAAT INI. Kalau
// sigcheck.cpp diubah (termasuk cuma reorder/reformat yang mengubah output
// compiler), checksum ini WAJIB dihitung ulang dan diperbarui — lihat
// catatan "REGENERASI CHECKSUM" di bawah, kalau tidak app release akan
// force-close sendiri walau tidak pernah di-tamper siapa pun.

#include <jni.h>
#include <cstdint>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "AetherXIntegrity"
#ifdef AETHERX_DEBUG_LOG
#define GUARD_LOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define GUARD_LOG(...)
#endif

// Deklarasi maju simbol dari sigcheck.cpp — kita ambil ALAMATnya (bukan
// panggil fungsinya) untuk membaca byte code mentahnya langsung dari
// memory proses yang sedang berjalan.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_aether_x_core_security_SignatureGuard_nativeVerify(
        JNIEnv* env, jobject thiz, jbyteArray actualHashBytes);

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aether_x_core_security_SignatureGuard_nativeVerifyRecheck(
        JNIEnv* env, jobject thiz, jbyteArray actualHashBytes);

namespace {

// Berapa byte code dari TITIK AWAL tiap fungsi yang ikut di-checksum.
// Cukup besar untuk mencakup badan fungsi verifikasi & compare (bukan
// seluruh .text) supaya checksum tetap stabil walau linker menaruh fungsi
// lain di sekitarnya secara berbeda antar build, tapi cukup besar untuk
// tetap mendeteksi patch pada instruksi cmp/branch penting di dalamnya.
constexpr size_t kScanLen = 256;

// checksum FNV-1a 64-bit atas kedua fungsi target, digabung jadi satu
// nilai (bukan kriptografis — cukup untuk mendeteksi PERUBAHAN byte tak
// disengaja/disengaja pada region ini, bukan untuk melawan orang yang
// dengan sengaja mau membuat collision; kalau butuh lebih kuat, gunakan
// digest kriptografis, tapi untuk anti-tamper region kecil ini FNV-1a
// sudah cukup dan jauh lebih murah dihitung tiap startup).
uint64_t fnv1a64(const uint8_t* data, size_t len, uint64_t seed) {
    uint64_t hash = seed;
    for (size_t i = 0; i < len; i++) {
        hash ^= data[i];
        hash *= 0x100000001B3ULL;
    }
    return hash;
}

// Checksum asli (dari build release resmi) di-XOR dengan key di bawah —
// pola sama seperti kEncodedHash/kXorKey di sigcheck.cpp, supaya nilai
// aslinya tidak muncul sebagai satu konstanta polos di .rodata.
//
// *** REGENERASI CHECKSUM — WAJIB DILAKUKAN SEBELUM RILIS ***
// Nilai di bawah ini MASIH PLACEHOLDER (kXorKeyChecksum == kEncodedChecksum,
// sengaja dibuat sama supaya keduanya saling meniadakan lewat XOR dan
// decodeExpectedChecksum() menghasilkan 0 — lihat kPlaceholderNotConfigured
// di bawah). ISI dengan nilai asli sebelum build release, dengan langkah:
//   1. Build APK release seperti biasa (sigcheck.cpp final, sudah fix).
//   2. Jalankan app itu SEKALI di device/emulator dengan AETHERX_DEBUG_LOG
//      didefinisikan (tambahkan `-DAETHERX_DEBUG_LOG` di CMAKE_CXX_FLAGS
//      sementara) — logcat tag "AetherXIntegrity" akan mencetak baris
//      "live checksum computed: 0x....".
//   3. Salin nilai hex itu, XOR manual dengan kXorKeyChecksum (pilih key
//      baru bebas, mis. random 64-bit), simpan hasilnya sebagai
//      kEncodedChecksum, dan pastikan kXorKeyChecksum diisi key yang sama
//      dipakai untuk encode.
//   4. HAPUS flag `-DAETHERX_DEBUG_LOG` lagi dari CMakeLists sebelum build
//      release final yang akan didistribusikan — logcat tidak boleh
//      membocorkan checksum ini di build publik.
//   5. Set kPlaceholderNotConfigured = false setelah nilai asli diisi.
constexpr uint64_t kXorKeyChecksum = 0x9F3B7C1E5A2D8064ULL;
constexpr uint64_t kEncodedChecksum = 0x9F3B7C1E5A2D8064ULL; // TODO: ganti setelah regenerasi (langkah di atas)

// Selama placeholder belum diganti (checksum asli belum digenerate dari
// build final), guard ini SENGAJA tidak dijadikan alasan force-close —
// supaya tim tidak tanpa sadar mengunci APK dev/staging dengan checksum
// yang salah. Lihat NativeIntegrityGuard.kt: hasil "not configured" ini
// diperlakukan sebagai skip, bukan sebagai gagal ATAU lolos otomatis.
constexpr bool kPlaceholderNotConfigured = (kXorKeyChecksum == kEncodedChecksum);

uint64_t decodeExpectedChecksum() {
    return kEncodedChecksum ^ kXorKeyChecksum;
}

// Hitung checksum LIVE dari byte code kedua fungsi target, dibaca langsung
// dari memory proses yang sedang berjalan sekarang (bukan dari file di
// disk) lewat alamat simbolnya.
uint64_t computeLiveChecksum() {
    const auto* verifyAddr = reinterpret_cast<const uint8_t*>(
        &Java_com_aether_x_core_security_SignatureGuard_nativeVerify);
    const auto* recheckAddr = reinterpret_cast<const uint8_t*>(
        &Java_com_aether_x_core_security_SignatureGuard_nativeVerifyRecheck);

    uint64_t hash = fnv1a64(verifyAddr, kScanLen, 0xCBF29CE484222325ULL);
    hash = fnv1a64(recheckAddr, kScanLen, hash);

    GUARD_LOG("live checksum computed: 0x%016llx",
              static_cast<unsigned long long>(hash));
    return hash;
}

}  // namespace

// Dipanggil dari NativeIntegrityGuard.kt. Return kode int (bukan boolean)
// supaya sisi Kotlin bisa membedakan tiga kondisi:
//   0 = MISMATCH — byte code fungsi verifikasi sudah berubah, kemungkinan dipatch.
//   1 = MATCH — checksum cocok, fungsi verifikasi masih utuh seperti build resmi.
//   2 = NOT_CONFIGURED — kEncodedChecksum masih placeholder (lihat catatan
//       REGENERASI CHECKSUM di atas), guard ini belum bisa dipakai untuk
//       menegakkan apa pun; Kotlin sebaiknya skip (bukan force-close ATAU
//       anggap valid) sampai nilai asli diisi.
extern "C" JNIEXPORT jint JNICALL
Java_com_aether_x_core_security_NativeIntegrityGuard_nativeVerifyIntegrity(
        JNIEnv* /* env */, jobject /* thiz */) {
    if (kPlaceholderNotConfigured) {
        GUARD_LOG("checksum belum dikonfigurasi — lihat catatan REGENERASI CHECKSUM");
        return 2;
    }
    const uint64_t live = computeLiveChecksum();
    const uint64_t expected = decodeExpectedChecksum();
    const bool match = (live == expected);
    GUARD_LOG("integrity verify result: %d", match);
    return match ? 1 : 0;
}
