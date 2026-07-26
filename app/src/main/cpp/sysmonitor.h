#pragma once

// sysmonitor.h — kontrak untuk sysmonitor.cpp.
//
// LATAR BELAKANG: sebelumnya baca CPU load (SystemStatsProvider.kt) dan
// GPU load (juga SystemStatsProvider.kt) dilakukan di Kotlin lewat
// RandomAccessFile/File biasa. Itu sudah cukup untuk kartu ringkasan di
// Dashboard, tapi TIDAK cukup untuk monitor real-time bergrafik: sampling
// tiap 200-500ms dari Kotlin lewat File I/O (buka/tutup file descriptor
// berulang, alokasi String/regex split tiap baca) membebani GC dan jadi
// jitter di garis grafik. Modul ini membaca ulang /proc/stat DAN sysfs GPU
// langsung dari native dengan file descriptor yang dibuka sekali lalu
// di-pread berulang (lihat sysmonitor.cpp), jauh lebih murah untuk polling
// rate tinggi.
//
// GATING ROOT: modul ini SENGAJA tidak melakukan pengecekan root sama
// sekali di native — itu tetap tanggung jawab Kotlin (lihat
// RootSystemMonitor.kt, hanya dipanggil saat
// PrivilegeStatus.activeBackend == PrivilegeBackend.ROOT). Alasannya:
// path yang dibaca (/proc/stat per-core, /sys/class/kgsl/kgsl-3d0/*)
// sebagian sudah world-readable di banyak device walau tanpa root, jadi
// tidak ada gunanya modul ini menolak baca tanpa root — justru kalau
// device kebetulan mengizinkan baca tanpa root, fitur ini tetap harus
// TIDAK aktif di UI non-root supaya perilaku konsisten di semua device
// (bukan root-only di sebagian device dan bukan di device lain).
//
// THREAD-SAFETY: seluruh fungsi di bawah TIDAK thread-safe terhadap
// pemanggilan dari lebih dari satu thread Kotlin secara bersamaan pada
// instance yang sama (nsmOpen/nsmReadCpu/nsmReadGpu/nsmClose harus
// dipanggil berurutan dari satu thread, biasanya dari satu coroutine
// polling loop). Ini keputusan sengaja: mengunci tiap baca menambah
// overhead yang justru ingin dihindari modul ini, dan pemanggil (Kotlin)
// sudah secara alami single-threaded lewat satu Flow/loop polling.

#include <cstddef>
#include <cstdint>

namespace aetherx::sysmonitor {

// Jumlah core CPU maksimum yang didukung satu pemanggilan nsmReadCpu.
// Device dengan core lebih banyak dari ini akan dipotong (truncate),
// bukan gagal — nilai ini jauh di atas jumlah core SoC mobile saat ini
// (biasanya 8), diberi ruang lebih supaya aman untuk beberapa tahun ke
// depan.
inline constexpr int kMaxCpuCores = 16;

// Snapshot delta CPU load per-core dalam persen (0-100), dihitung dari
// selisih dua pembacaan /proc/stat berturut-turut (bukan rata-rata sejak
// boot). Index 0 = agregat "cpu" (semua core), index 1..N = "cpu0".."cpuN-1".
struct CpuSnapshot {
    int coreCount = 0;                    // jumlah core individual (tidak termasuk agregat)
    float aggregateLoadPercent = -1.0f;    // -1 = belum ada sampel pembanding (baca pertama)
    float perCoreLoadPercent[kMaxCpuCores] = {};
};

// Snapshot GPU: load percent (0-100) dan frekuensi saat ini dalam MHz.
// Salah satu/kedua bisa -1 kalau path sysfs tidak ditemukan/tidak
// terbaca di device ini (bukan error fatal, sisi Kotlin menampilkan
// "-" untuk nilai -1).
struct GpuSnapshot {
    float loadPercent = -1.0f;
    float freqMhz = -1.0f;
};

// nsmReadCpu: baca /proc/stat SEKALI, hitung delta terhadap pembacaan
// sebelumnya (state delta disimpan di dalam sysmonitor.cpp, per-process,
// bukan di objek Kotlin) dan isi `out`. Baca pertama setelah proses start
// selalu mengembalikan aggregateLoadPercent/perCoreLoadPercent = -1
// (belum ada pembanding) — pemanggil sebaiknya membuang sampel pertama.
// Return false kalau /proc/stat tidak bisa dibuka/diparse sama sekali.
bool nsmReadCpu(CpuSnapshot* out);

// nsmReadGpu: baca sysfs GPU (kgsl/devfreq, path yang sama dengan yang
// sudah dicoba SystemStatsProvider.kt di Kotlin) dan isi `out`. Return
// false hanya kalau TIDAK SATU PUN path dikenal berhasil dibaca sama
// sekali (device tidak didukung) — kalau load terbaca tapi freq tidak
// (atau sebaliknya), tetap return true dengan field yang gagal diisi -1.
bool nsmReadGpu(GpuSnapshot* out);

// nsmResetCpuDelta: reset state delta CPU internal (dipanggil saat
// monitor di-stop lalu di-start ulang, supaya baca pertama setelah
// restart tidak memakai delta basi dari sesi sebelumnya).
void nsmResetCpuDelta();

}  // namespace aetherx::sysmonitor
