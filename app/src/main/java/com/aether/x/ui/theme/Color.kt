package com.aether.x.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// v3.5 — REWORK VISUAL TOTAL. Palet SEBELUM ini secara eksplisit diberi
// komentar "Thor-style" dan "matches Thor's app-source bar" di kode — nama
// variabelnya sendiri pun ada sisa seperti `AccentBlue` yang warnanya
// sebenarnya hijau lime, jejak copy-paste dari app root-tools lain bernama
// Thor tanpa benar-benar disesuaikan ke identitas AetherX sendiri.
//
// Arah baru: "Aether" secara harfiah berarti udara murni/langit atas yang
// bercahaya dalam mitologi Yunani — jadi identitas visualnya dibangun dari
// situ: dasar gelap ber-nuansa BIRU KEHITAMAN (bukan hitam pekat/krem hangat
// generik) dengan SATU aksen cyan luminous yang terasa seperti sinyal/
// voltase/oscilloscope trace — cocok untuk audiens app ini (komunitas root
// Magisk/KernelSU, tweaker performa, gamer) yang literally berinteraksi
// dengan pembacaan tegangan/frekuensi/suhu kernel setiap hari.
//
// PENTING soal nama variabel di bawah: puluhan layar (MembershipScreen,
// PermissionSetupScreen,
// BuildPropScreen, GameProfileScreen, AppManagerScreen, RootMonitorSection,
// UpdateGate, CrosshairSettingsSection, dll — total 13+ file) meng-import
// nama-nama token LAMA ini SECARA LANGSUNG (bukan lewat MaterialTheme.
// colorScheme). Nama-nama itu SENGAJA DIPERTAHANKAN persis sebagai alias ke
// nilai warna BARU di bawah — supaya rework visual ini otomatis menjalar ke
// SEMUA layar tersebut tanpa perlu menyentuh satu-satu (dan tanpa risiko
// gagal compile). Kalau menambah pemakaian warna baru, pakai nama
// `Aether*` di bawah — nama `Accent*`/`Bg*` yang lama jangan dipakai lagi
// di kode BARU (dipertahankan murni demi kompatibilitas mundur).
// ============================================================================

// --- Token BARU (pakai ini untuk kode baru) ---------------------------------

// Permukaan dasar: "ruang malam", bukan hitam pekat netral.
val VoidBg = Color(0xFF0A0E16)
val SurfaceCard = Color(0xFF12161F)
val SurfaceCardAlt = Color(0xFF161C28)
val SurfaceRaised = Color(0xFF1A2030)
val StrokeSubtle = Color(0xFF262E40)

// Aksen utama: "Aether Cyan" — sinyal/voltase, dipakai untuk SEMUA state
// aktif/primer (switch on, progress bar, tombol utama, watermark ikon).
val AetherCyan = Color(0xFF3FE1C9)
val AetherCyanSoft = Color(0xFF8FF2E1)
val AetherCyanContainer = Color(0xFF163832)
val OnAetherCyan = Color(0xFF04211C)

// Aksen kedua: "Aether Violet" — SENGAJA dipisah dan HANYA dipakai untuk
// konteks premium/membership, supaya cyan tetap murni berarti "performa/
// aktif" dan violet murni berarti "premium".
val AetherViolet = Color(0xFF9B8CFB)
val AetherVioletContainer = Color(0xFF241A44)
val OnAetherVioletContainer = Color(0xFFD6CCFF)

// Status: merah tetap merah (konvensi universal), amber untuk peringatan
// menengah (suhu/kuota/membership akan habis).
val AetherRed = Color(0xFFFF6459)
val OnAetherRed = Color(0xFF2B0704)
val AetherAmber = Color(0xFFF2B84E)
val AetherAmberContainer = Color(0xFF3A2C0A)
val OnAetherAmberContainer = Color(0xFFFFD9A0)

// Aksen tambahan (v3.6 — "UI lebih berwarna"): dipakai untuk membedakan
// kategori kartu/tab (biru = info/sistem, hijau = sehat/OK, pink = hiburan/
// game, oranye = aksi/bonus) supaya layar tidak monoton cyan.
val AetherBlue = Color(0xFF5AA9FF)
val AetherBlueContainer = Color(0xFF14243D)
val AetherGreen = Color(0xFF5BE38A)
val AetherGreenContainer = Color(0xFF123222)
val AetherPink = Color(0xFFFF7AB6)
val AetherPinkContainer = Color(0xFF3A1829)
val AetherOrange = Color(0xFFFF9A5C)
val AetherOrangeContainer = Color(0xFF3A2112)

// Teks: putih KEBIRUAN dingin, bukan krem hangat (menghindari palet "warm
// cream + terracotta" yang jadi ciri khas AI-generated design).
val TextPrimary = Color(0xFFEAF0F7)
val TextSecondary = Color(0xFF94A1B8)
val TextMuted = Color(0xFF56637A)

// Skema TERANG (dipakai saat DarkModePref.LIGHT) — cyan digelapkan supaya
// kontras aman di atas putih, tetap satu keluarga warna dengan mode gelap.
val LightBg = Color(0xFFF7F6F8)
val LightSurfaceVariant = Color(0xFFF0EDF2)
val LightOutline = Color(0xFFE2DDE5)
val LightPrimary = Color(0xFF73566F)
val LightPrimaryContainer = Color(0xFFEDE2EC)
val OnLightPrimaryContainer = Color(0xFF39283A)
val LightSecondary = Color(0xFF827487)
val LightSecondaryContainer = Color(0xFFECE7EE)
val LightTextPrimary = Color(0xFF28252B)
val LightTextSecondary = Color(0xFF69636D)

// --- Alias KOMPATIBILITAS MUNDUR (nama lama -> nilai baru) ------------------
// Lihat catatan panjang di atas. JANGAN dihapus tanpa mengaudit ulang 13+
// file yang masih mengimpornya langsung.

val BgVoid = VoidBg
val BgBase = VoidBg
val TextOnCard = TextPrimary

val AccentBlue = AetherCyan
val AccentBlueSoft = AetherCyanSoft
val AccentBlueDim = AetherCyanContainer
val OnAccentBlue = OnAetherCyan
val AccentGreenIconContainer = AetherCyanContainer

val AccentGreen = AetherCyan
val AccentGreenContainer = AetherCyanContainer

val AccentRed = AetherRed

val AccentAmber = AetherAmber
val AccentAmberContainer = AetherAmberContainer

val AccentPurple = AetherViolet
