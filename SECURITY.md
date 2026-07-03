# Keamanan Sistem Lisensi AetherX

Dokumen ini menjelaskan **kenapa lisensi bisa dibobol dalam <3 menit**, apa
yang sudah dibenahi di kode ini, dan **langkah manual yang WAJIB kamu
lakukan** di Firebase Console / Play Console supaya perbaikannya benar-benar
aktif (tidak bisa diotomasi lewat kode saja).

---

## 1. Akar masalah: `firestore.rules` lama

```js
match /licenses/{key} {
  allow get: if true;   // <-- INI CELAHNYA
  ...
}
```

`allow get: if true` artinya **siapa pun bisa mengakses Firestore REST API
secara langsung** (bukan lewat app Android sama sekali) untuk cek
`GET https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents/licenses/{key}`
tanpa autentikasi apa pun, tanpa rate limit, tanpa perlu app terinstall.

Temanmu kemungkinan besar menjalankan script yang mem-brute-force banyak
kombinasi kode secara paralel (ratusan/ribuan request per detik) langsung ke
endpoint itu. Begitu satu tebakan memberi respons `200` (dokumen ada) alih-alih
`404`, dia tahu itu kode valid — tanpa pernah menyentuh UI aplikasi sampai
tahap terakhir (aktivasi). Tidak ada yang menghentikannya karena:

- Tidak ada App Check → request dari script biasa diterima sama seperti dari app asli.
- Tidak ada rate limit di rules maupun infrastruktur lain.
- Token lisensi 7 karakter (`lib/tokenGenerator.js`) — cukup besar secara
  matematis (54⁷ ≈ 2×10¹² kombinasi), tapi kalau jumlah lisensi aktif yang
  beredar sedikit dan proses `get`-nya bisa dipanggil sebebas-bebasnya tanpa
  batas kecepatan, brute force paralel skala besar tetap punya peluang
  menemukan salah satu kode valid jauh lebih cepat dari yang terasa aman.

---

## 2. Apa yang sudah dibenahi di kode ini

### a) `firestore.rules` — App Check wajib di semua operasi `licenses/{key}`
- `allow get: if true` **dihapus**, diganti `allow get: if isVerifiedApp()`.
- `allow update` (aktivasi) juga mensyaratkan `isVerifiedApp()`.
- Validasi `deviceId` diperketat: dulu cuma "string non-kosong", sekarang
  harus persis format `ANDROID_ID` asli (regex 16 karakter hex lowercase).
- Koleksi `devices/{deviceId}` ikut diperketat dengan `isVerifiedApp()` juga
  (dulu juga `allow get: if true`, exposure serupa meski tidak membocorkan
  kode lisensi itu sendiri).
- `config/maintenance` dan `config/update` SENGAJA dibiarkan publik (`if
  true`) karena harus tetap terbaca walau App Check belum siap saat splash
  screen — risikonya rendah (cuma info versi/maintenance, bukan data sensitif).

### b) App Check (Play Integrity) di sisi Android
- `app/build.gradle.kts`: ditambahkan dependency
  `com.google.firebase:firebase-appcheck-playintegrity`.
- `AetherXApp.kt`: memanggil `AppCheckInitializer.init(this)` di `onCreate()`,
  SEBELUM Firestore dipakai di mana pun.
- `core/security/AppCheckInitializer.kt` (baru): memasang provider Play
  Integrity. Efeknya, Firestore rules bisa membedakan request dari APK asli
  (signature rilis terdaftar, lolos verifikasi Google Play) vs request dari
  script/curl/APK modifikasi — dan menolak yang kedua.

### c) Guard lapis kedua: rate limit di sisi client
- `core/security/LicenseAttemptGuard.kt` (baru): membatasi kecepatan
  percobaan aktivasi DARI DALAM app asli itu sendiri (App Check tidak
  mencegah pengguna app asli mengetik-coba banyak kode manual).
  - Setelah 3 percobaan gagal berturut-turut dalam 10 menit → lockout 30 detik,
    naik eksponensial (30d → 60d → 120d → ... maks 1 jam) tiap kelipatan
    berikutnya.
  - State dipersist ke DataStore, jadi tidak bisa direset dengan force-close app.
  - Percobaan yang gagal karena `NetworkError` (offline/App Check belum siap)
    TIDAK dihitung sebagai percobaan gagal.
- Diintegrasikan ke `MembershipViewModel.activate()`.

---

## 3. LANGKAH MANUAL WAJIB (tidak bisa diotomasi dari kode)

Perbaikan di atas **tidak akan aktif** sampai kamu melakukan ini secara
berurutan:

### Langkah 1 — Aktifkan Play Integrity API
1. Buka [Play Console](https://play.google.com/console) → pilih app AetherX.
2. Menu **App integrity** → pastikan **Play Integrity API** aktif untuk
   `com.aether.x`.
3. App harus sudah pernah diupload minimal sekali ke Play Console (App
   Signing) supaya Google tahu signature rilis mana yang "asli".

### Langkah 2 — Daftarkan app ke Firebase App Check
1. [Firebase Console](https://console.firebase.google.com) → project AetherX
   → **Build → App Check**.
2. Pilih app Android `com.aether.x` → aktifkan provider **Play Integrity**.
3. **Mode awal: "Unenforced" / Monitor dulu**, JANGAN langsung "Enforced".
   Ini penting supaya kamu bisa lihat metrik berapa banyak request app asli
   yang lolos sebelum benar-benar mengunci akses.

### Langkah 3 — Build & pasang APK rilis baru (dengan App Check aktif), lalu pantau
1. Build APK release dengan signing config yang sama seperti biasa.
2. Pasang di device asli, coba buka tab Membership.
3. Di Firebase Console → App Check → lihat metrik "Verified requests" — harus
   naik seiring device asli membuka app.
4. Tunggu minimal 24-48 jam supaya App Check punya cukup data metrik dari
   pengguna asli (supaya nanti tidak salah kunci mereka sendiri).

### Langkah 4 — Deploy `firestore.rules` yang baru
Setelah App Check terpasang & termonitor dengan baik di langkah 3:
```bash
firebase deploy --only firestore:rules
```
Atau paste isi `firestore.rules` manual lewat Firebase Console → Firestore →
Rules → Publish.

### Langkah 5 — Pindahkan App Check ke mode "Enforced"
Firebase Console → App Check → pilih Firestore → ubah dari
"Unenforced/Monitor" ke **"Enforced"**. Sejak titik ini, SEMUA request
Firestore tanpa token App Check valid akan ditolak — termasuk brute-force
langsung ke REST API.

### Langkah 6 — Tambahkan string resource yang hilang
Kode ini memanggil `R.string.membership_key_error_locked` (pesan saat
lockout aktif) — file `strings.xml` tidak ikut dalam project yang diupload,
jadi tambahkan manual, misalnya:
```xml
<string name="membership_key_error_locked">Terlalu banyak percobaan gagal. Coba lagi dalam %d detik.</string>
```

---

## 4. WAJIB SEGERA: rotasi credential yang ikut ter-upload

File `serviceAccountKey.json` dan `aetherx.jks` ikut ada di dalam zip yang
kamu upload ke sini. Kalau file-file ini juga pernah ter-commit ke Git
publik, ter-upload ke cloud storage publik, atau dibagikan ke luar lewat
jalur yang tidak kamu kontrol penuh:

1. **`serviceAccountKey.json`** — Firebase Console → Project Settings →
   Service accounts → **Generate new private key**, lalu HAPUS/revoke key
   lama di tab yang sama. Key lama yang bocor punya akses ADMIN PENUH ke
   Firestore (bypass semua rules), jadi ini prioritas tertinggi.
2. **`aetherx.jks`** (signing key APK) — kalau passphrase/keystore ini bocor,
   orang lain bisa menandatangani APK modifikasi dengan signature yang sama
   persis seperti app aslimu, yang berarti App Check + Play Integrity **tidak
   akan bisa membedakan** APK asli vs APK modifikasi tsb. Kalau ragu keystore
   ini pernah bocor ke luar kendalimu, ini kasus serius — App Signing key
   rotation di Play Console jauh lebih rumit (perlu proses "Key Upgrade"
   lewat Play Console, atau publish app baru dengan applicationId berbeda
   kalau app belum pakai Play App Signing). Amankan dulu, jangan sebar file
   ini lagi ke mana pun termasuk chat/zip berikutnya.

Kedua file ini **tidak disertakan** di paket hasil perbaikan yang saya berikan.

### File `.env` yang juga ikut ter-upload — SEGERA rotasi ini juga

File `.env` di dalam zip yang kamu upload berisi **credential live, bukan
contoh**:

- `TELEGRAM_BOT_TOKEN` — token asli bot Telegram kamu. Siapa pun yang punya
  ini bisa mengendalikan bot (generate lisensi tak terbatas, revoke,
  maintenance mode, dst) mengatasnamakan bot kamu. **Rotasi lewat
  @BotFather → `/revoke` atau `/token` untuk bot ini, lalu update `.env` lokal
  dengan token baru.**
- `UPSTASH_REDIS_REST_TOKEN` — token akses penuh ke database Redis platform
  lisensi Next.js kamu (`aether-app-weld.vercel.app`). **Rotasi lewat Upstash
  Console → database → Reset token/Regenerate.**
- `ADMIN_TELEGRAM_ID` tidak rahasia (cuma ID numerik user Telegram), tapi
  tetap ikut terhapus dari paket ini bersama file `.env` lainnya untuk jaga-jaga.

File ini **tidak disertakan** di paket hasil perbaikan. Buat ulang `.env`
kamu secara lokal dari `.env.example` dengan credential BARU setelah rotasi
di atas selesai — jangan pakai ulang nilai lama.

---

## 5. Kenapa ini cukup (dan batasannya)

| Lapis | Menahan apa | Tidak menahan apa |
|---|---|---|
| App Check (Play Integrity) | Script/curl langsung ke REST API, APK hasil modifikasi dengan signature berbeda | Device rooted yang berhasil mem-bypass Play Integrity attestation sepenuhnya (sangat sulit, tapi secara teori tidak nol) |
| `firestore.rules` diperketat | Enumerasi `get`, deviceId palsu berformat sembarangan | Serangan dari dalam app asli oleh pengguna sah yang sengaja mencoba banyak kode manual |
| `LicenseAttemptGuard` | Percobaan manual berulang cepat dari dalam app asli | Reinstall app berulang kali untuk reset DataStore (mitigasi: state device juga tercatat di `devices/{deviceId}` server-side untuk audit, meski tidak dipakai untuk block otomatis di versi ini) |

Kombinasi ketiganya menaikkan biaya serangan dari "script Python 3 menit"
menjadi butuh: APK bersertifikat asli + berhasil lolos Play Integrity +
tunduk pada rate limit sisi client. Ini standar industri untuk kasus seperti
ini (mirip cara Google Play sendiri melindungi in-app purchase validation).
