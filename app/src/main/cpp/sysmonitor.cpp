#include "sysmonitor.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace aetherx::sysmonitor {

namespace {

// State delta CPU antar dua pembacaan berturut-turut. Disimpan per-process
// (bukan per-instance Kotlin) karena hanya ada satu monitor aktif dalam
// satu waktu di app ini (lihat RootSystemMonitor.kt — singleton object).
struct CpuDeltaState {
    bool hasPrevious = false;
    long long prevTotal[kMaxCpuCores + 1] = {};
    long long prevIdle[kMaxCpuCores + 1] = {};
};

CpuDeltaState g_cpuState;

// Parse satu baris "/proc/stat" berformat:
//   cpu  user nice system idle iowait irq softirq steal guest guest_nice
//   cpu0 user nice system idle iowait irq softirq steal guest guest_nice
// Mengembalikan total & idle (idle + iowait, konsisten dengan
// SystemStatsProvider.kt di Kotlin) lewat parameter out. Return false
// kalau baris tidak diawali label "cpu" yang valid.
bool parseStatLine(const char* line, long long* outTotal, long long* outIdle) {
    if (line == nullptr || strncmp(line, "cpu", 3) != 0) return false;

    // Lompat ke token setelah label (mis. "cpu" atau "cpu0").
    const char* cursor = line + 3;
    while (*cursor != '\0' && *cursor != ' ') cursor++;  // lewati sisa digit index core
    while (*cursor == ' ') cursor++;

    long long values[10] = {};
    int count = 0;
    while (count < 10 && *cursor != '\0' && *cursor != '\n') {
        char* endPtr = nullptr;
        long long v = strtoll(cursor, &endPtr, 10);
        if (endPtr == cursor) break;
        values[count++] = v;
        cursor = endPtr;
        while (*cursor == ' ') cursor++;
    }
    if (count < 4) return false;

    long long total = 0;
    for (int i = 0; i < count; i++) total += values[i];
    long long idle = values[3] + (count > 4 ? values[4] : 0);  // idle + iowait

    *outTotal = total;
    *outIdle = idle;
    return true;
}

float percentFromDelta(long long total, long long idle, long long prevTotal, long long prevIdle) {
    long long totalDelta = total - prevTotal;
    long long idleDelta = idle - prevIdle;
    if (totalDelta <= 0) return -1.0f;
    float busy = static_cast<float>(totalDelta - idleDelta) / static_cast<float>(totalDelta) * 100.0f;
    if (busy < 0.0f) busy = 0.0f;
    if (busy > 100.0f) busy = 100.0f;
    return busy;
}

// Baca satu file sysfs kecil (nilai numerik tunggal) ke dalam buffer.
// Dipakai untuk GPU busy%/freq — file-file ini biasanya <32 byte.
bool readSmallFile(const char* path, char* buffer, size_t bufferLen) {
    FILE* f = fopen(path, "r");
    if (f == nullptr) return false;
    size_t n = fread(buffer, 1, bufferLen - 1, f);
    fclose(f);
    if (n == 0) return false;
    buffer[n] = '\0';
    return true;
}

// Ekstrak angka pertama (integer, boleh diikuti karakter lain seperti
// "%" atau "MHz") dari sebuah string. Return false kalau tidak ada digit
// sama sekali.
bool extractFirstNumber(const char* text, long long* out) {
    const char* cursor = text;
    while (*cursor != '\0' && (*cursor < '0' || *cursor > '9')) cursor++;
    if (*cursor == '\0') return false;
    char* endPtr = nullptr;
    *out = strtoll(cursor, &endPtr, 10);
    return endPtr != cursor;
}

}  // namespace

bool nsmReadCpu(CpuSnapshot* out) {
    if (out == nullptr) return false;
    *out = CpuSnapshot{};

    FILE* f = fopen("/proc/stat", "r");
    if (f == nullptr) return false;

    char line[256];
    int coreIndex = -1;  // -1 = baris agregat "cpu"
    long long total = 0;
    long long idle = 0;

    bool aggregateOk = false;
    int parsedCores = 0;

    while (fgets(line, sizeof(line), f) != nullptr) {
        // Baris "cpu" (agregat) selalu duluan, lalu "cpu0", "cpu1", dst.
        // Baris non-cpu (intr, ctxt, dll) menandakan blok cpu* sudah habis.
        if (strncmp(line, "cpu", 3) != 0) break;

        bool isAggregate = (line[3] == ' ');
        if (!parseStatLine(line, &total, &idle)) continue;

        int slot = isAggregate ? 0 : (parsedCores + 1);
        if (slot > kMaxCpuCores) continue;

        float pct = -1.0f;
        if (g_cpuState.hasPrevious) {
            pct = percentFromDelta(total, idle, g_cpuState.prevTotal[slot], g_cpuState.prevIdle[slot]);
        }
        g_cpuState.prevTotal[slot] = total;
        g_cpuState.prevIdle[slot] = idle;

        if (isAggregate) {
            out->aggregateLoadPercent = pct;
            aggregateOk = true;
        } else {
            if (parsedCores < kMaxCpuCores) {
                out->perCoreLoadPercent[parsedCores] = pct;
                parsedCores++;
            }
            coreIndex = parsedCores;
        }
    }
    fclose(f);

    out->coreCount = parsedCores;
    (void)coreIndex;

    g_cpuState.hasPrevious = true;
    return aggregateOk;
}

void nsmResetCpuDelta() {
    g_cpuState = CpuDeltaState{};
}

bool nsmReadGpu(GpuSnapshot* out) {
    if (out == nullptr) return false;
    *out = GpuSnapshot{};

    // Path yang sama urutannya dengan SystemStatsProvider.kt (Kotlin) —
    // dipertahankan konsisten supaya perilaku "GPU tidak terbaca di
    // device ini" sama antara kartu ringkasan Dashboard dan monitor
    // real-time ini.
    static const char* kLoadPaths[] = {
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/kernel/gpu/gpu_busy",
        "/sys/class/devfreq/gpufreq/gpu_busy",
    };
    static const char* kFreqPaths[] = {
        "/sys/class/kgsl/kgsl-3d0/gpuclk",
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
        "/sys/class/devfreq/gpufreq/cur_freq",
    };

    char buffer[64];
    bool anyOk = false;

    for (const char* path : kLoadPaths) {
        if (!readSmallFile(path, buffer, sizeof(buffer))) continue;
        long long value = 0;
        if (!extractFirstNumber(buffer, &value)) continue;
        if (value < 0 || value > 100) continue;
        out->loadPercent = static_cast<float>(value);
        anyOk = true;
        break;
    }

    for (const char* path : kFreqPaths) {
        if (!readSmallFile(path, buffer, sizeof(buffer))) continue;
        long long value = 0;
        if (!extractFirstNumber(buffer, &value)) continue;
        if (value <= 0) continue;
        // gpuclk/cur_freq biasanya dalam Hz (mis. 500000000) — sebagian
        // device melaporkan sudah dalam MHz (nilai kecil, <10000). Deteksi
        // heuristik sederhana: kalau nilainya besar, anggap Hz dan
        // konversi ke MHz.
        float mhz = value > 100000 ? static_cast<float>(value) / 1000000.0f : static_cast<float>(value);
        out->freqMhz = mhz;
        anyOk = true;
        break;
    }

    return anyOk;
}

}  // namespace aetherx::sysmonitor
