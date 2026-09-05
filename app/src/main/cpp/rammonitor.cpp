#include "rammonitor.h"

#include <android/log.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#define LOG_TAG "AetherX-RamMonitor"

namespace aetherx::rammonitor {

namespace {

// Cocokkan satu baris /proc/meminfo berformat "Label:    12345 kB" —
// return true + isi *outValueKb kalau label baris ini PERSIS sama dengan
// `label` (dibandingkan sampai panjang label, lalu wajib diikuti ':').
// Pola line format ini konsisten di semua kernel Android/Linux modern,
// tidak seperti /proc/stat yang formatnya sedikit bervariasi antar
// vendor (lihat komentar parseStatLine di sysmonitor.cpp).
bool matchLabelAndParseKb(const char* line, const char* label, float* outValueKb) {
    size_t labelLen = strlen(label);
    if (strncmp(line, label, labelLen) != 0) return false;
    if (line[labelLen] != ':') return false;

    const char* cursor = line + labelLen + 1;
    while (*cursor == ' ' || *cursor == '\t') cursor++;

    char* endPtr = nullptr;
    long long value = strtoll(cursor, &endPtr, 10);
    if (endPtr == cursor) return false;

    *outValueKb = static_cast<float>(value);
    return true;
}

}  // namespace

bool nrmRead(RamSnapshot* out) {
    if (out == nullptr) return false;
    *out = RamSnapshot{};

    FILE* f = fopen("/proc/meminfo", "r");
    if (f == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Gagal membuka /proc/meminfo");
        return false;
    }

    // Empat baris yang dicari — semuanya SELALU ada di /proc/meminfo
    // modern (MemAvailable ditambahkan kernel 3.14, semua device Android
    // yang relevan sekarang jauh di atas versi itu), tapi tetap ditangani
    // per-baris (bukan asumsi urutan/index tetap) supaya tahan kalau ada
    // baris tambahan disisipkan vendor kernel tertentu.
    int foundCount = 0;
    const int kWantedCount = 4;

    char line[256];
    while (foundCount < kWantedCount && fgets(line, sizeof(line), f) != nullptr) {
        if (matchLabelAndParseKb(line, "MemTotal", &out->totalKb)) {
            foundCount++;
        } else if (matchLabelAndParseKb(line, "MemAvailable", &out->availableKb)) {
            foundCount++;
        } else if (matchLabelAndParseKb(line, "SwapTotal", &out->swapTotalKb)) {
            foundCount++;
        } else if (matchLabelAndParseKb(line, "SwapFree", &out->swapFreeKb)) {
            foundCount++;
        }
    }
    fclose(f);

    if (out->totalKb < 0.0f) {
        // MemTotal tidak ada sama sekali = /proc/meminfo device ini tidak
        // wajar/rusak — beda dari MemAvailable dkk yang boleh -1 sendirian.
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "MemTotal tidak ditemukan di /proc/meminfo");
        return false;
    }
    return true;
}

}  // namespace aetherx::rammonitor
