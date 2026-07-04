# Kernel Manager (khusus Root)

Section baca-tulis nilai kernel MENTAH — beda dari toggle "mode" bernama
yang sudah ada di section "Root" pada TweakScreen (CPU Governor terbatas,
GPU Performance Mode, dst.). Dua sistem ini independen dan boleh dipakai
bersamaan.

## Cakupan

- **CPU per-core**: frekuensi min/max + governor, untuk SETIAP core yang
  terdeteksi (bukan satu kontrol untuk semua core).
- **GPU**: frekuensi min/max + governor, lewat jalur devfreq yang
  terdeteksi otomatis (Adreno `kgsl-3d0` atau Mali generik).
- **Thermal**: suhu semua zona termal (`/sys/class/thermal/thermal_zone*`),
  di-poll tiap 2.5 detik selama section terlihat.
- **Info kernel**: versi (`uname -r`).

## File

| File | Peran |
|---|---|
| `core/kernel/KernelModels.kt` | Data class `CpuCoreInfo`, `GpuInfo`, `ThermalZoneInfo`, `KernelSnapshot` |
| `core/kernel/KernelInfoReader.kt` | BACA sysfs (satu panggilan shell per fungsi, bukan per file, untuk efisiensi) |
| `data/KernelManagerRepository.kt` | TULIS sysfs (set frekuensi/governor per core & GPU) |
| `ui/tweak/KernelManagerViewModel.kt` | State + polling thermal (berhenti otomatis saat ViewModel di-clear) |
| `ui/tweak/KernelManagerSection.kt` | Composable, dipasang di `TweakScreen` dalam blok `if (activeBackend == ROOT)` yang sama dengan section "Root" |

## Kenapa khusus Root (bukan Shizuku)

Menulis ke `/sys/devices/system/cpu/*/cpufreq/*` dan
`/sys/class/devfreq/*` (atau `/sys/class/kgsl/kgsl-3d0/devfreq`) butuh
permission yang TIDAK diberikan lewat Shizuku/adb shell biasa di sebagian
besar perangkat — konsisten dengan alasan section "Root" yang sudah ada
disembunyikan untuk backend Shizuku/NONE.

## Kenapa CPU/GPU tidak di-poll otomatis (beda dari thermal)

Frekuensi CPU/GPU berubah sangat cepat mengikuti beban (bisa berubah tiap
beberapa milidetik) — polling otomatis akan membuat angka "bergetar" terus
dan sulit dibaca/diinteraksi (slider akan terus "melompat" kalau di-refresh
saat pengguna sedang menggeser). CPU/GPU hanya dibaca ulang saat: section
pertama dibuka, tombol refresh manual ditekan, atau setelah apply
frekuensi/governor berhasil (untuk konfirmasi nilai yang BENAR-BENAR
tersimpan, yang bisa berbeda dari yang diminta kalau kernel menolak
sebagian).

## Kenapa frekuensi pakai slider index, bukan nilai bebas

Frekuensi kernel hanya boleh salah satu dari step yang benar-benar
didukung chipset (dibaca dari `scaling_available_frequencies` /
`available_frequencies`), yang TIDAK linear (mis. 300000, 576000, 748800
kHz — jarak antar step tidak sama). Slider dibuat pada domain INDEX ke
daftar itu (bukan domain KHz linear), supaya setiap posisi slider dijamin
jatuh tepat di step yang valid.

## Keterbatasan yang diketahui

- Belum menyimpan/menerapkan ulang setelan setelah reboot (tidak ada
  boot-persist script) — beda dari tweak lain yang menyimpan preferensi
  lewat DataStore, karena kernel manager ini murni membaca/menulis
  langsung ke sysfs saat itu juga. Kalau dibutuhkan, bisa ditambahkan lewat
  service init.d/late_start (di luar cakupan implementasi ini).
