# Setup Firebase Cloud Messaging (FCM) — AetherX

Fitur ini membuat notifikasi maintenance/update/membership tetap sampai ke
pengguna **walau aplikasi Android sedang tidak dibuka/di-background/ditutup
total**, berbeda dari mekanisme lama (`addSnapshotListener` Firestore) yang
hanya realtime selagi aplikasi punya proses hidup.

## Apa yang sudah otomatis (tidak perlu langkah tambahan)

- **Service account**: sisi bot (Cloudflare Worker) memakai ulang secret
  `FIREBASE_SERVICE_ACCOUNT` yang SUDAH ada (dipakai FirestoreClient) — TIDAK
  perlu secret baru. Service account Firebase Admin default sudah otomatis
  mendapat izin scope `firebase.messaging`, jadi tidak perlu pengaturan IAM
  tambahan di Google Cloud Console.
- **Dependency Android**: `firebase-messaging` sudah ditambahkan ke
  `gradle/libs.versions.toml` dan `app/build.gradle.kts`. Cukup sync Gradle.
- **Subscribe topic**: setiap install otomatis subscribe ke topic
  `maintenance`, `update`, `membership`, `general` saat app pertama kali
  dibuka (lihat `AetherXApp.onCreate` -> `FcmTokenRepository.subscribeToDefaultTopics()`).

## Langkah manual yang WAJIB dilakukan

### 1. Deploy ulang bot Cloudflare Worker

```bash
wrangler deploy
```

Tidak ada secret/env var baru yang perlu ditambahkan — `lib/fcmClient.js`
memakai `FIREBASE_SERVICE_ACCOUNT` yang sama dengan Firestore.

### 2. Update Firestore rules (field `fcmToken` di `devices/{deviceId}`)

Client Android sekarang menulis dua field baru ke dokumen
`devices/{deviceId}` lewat `update()`: `fcmToken` dan `fcmTokenUpdatedAt`.
Pastikan `firestore.rules` (dikelola terpisah, tidak ada di repo ini)
mengizinkan **update** field ini oleh pemilik device — pola yang sama
seperti field `licenseActive`/`licenseExpiresAt`/`lastLoginAt` yang sudah
ada. Field ini BUKAN data sensitif (token FCM tidak bisa dipakai untuk
membaca/menulis data), jadi tidak butuh aturan proteksi ketat.

Contoh potongan rule (sesuaikan dengan rule `devices` yang sudah ada):

```
match /devices/{deviceId} {
  allow update: if isVerifiedApp() &&
    request.resource.data.diff(resource.data).affectedKeys()
      .hasOnly(['licenseActive', 'licenseExpiresAt', 'lastLoginAt', 'userId', 'fcmToken', 'fcmTokenUpdatedAt']);
}
```

### 3. Build & pasang ulang aplikasi Android

APK/AAB perlu di-build ulang supaya `AetherXFirebaseMessagingService` dan
`FcmTokenRepository` ikut ter-bundle. Tidak ada perubahan pada
`google-services.json` yang diperlukan — file yang sudah ada sudah cukup.

## Cara pakai (setelah deploy)

Dari bot Telegram admin:

- **Maintenance**: `/maintenance` → toggle "Aktifkan" — push otomatis
  terkirim ke topic `maintenance` begitu status diubah jadi aktif.
- **Update versi**: `/update` → "Publish Versi Baru" → isi wizard sampai
  selesai — push otomatis terkirim ke topic `update`.
- **Membership/promo/pengumuman bebas**: `/broadcast <judul> | <pesan>` —
  push manual ke topic `membership`, tanpa menyimpan apa pun ke Firestore.
  Contoh:
  ```
  /broadcast Promo Membership | Diskon 20% perpanjangan khusus minggu ini!
  ```

Semua pengiriman push bersifat **best-effort**: kalau FCM gagal (mis. offline
sesaat), perubahan data (maintenance/update) tetap tersimpan ke Firestore
seperti biasa — hanya notifikasi pushnya yang gagal, dan bot akan melaporkan
kegagalan itu ke chat admin supaya bisa dicoba ulang manual lewat
`/broadcast` kalau perlu.

## Kirim ke satu device tertentu (opsional, belum ada command siap pakai)

Token FCM per-device tersimpan di `devices/{deviceId}.fcmToken`. Untuk kasus
mis. "ingatkan device X kalau lisensinya mau habis", bot bisa:

```js
const device = await firestore.getDocument("devices", deviceId);
if (device?.fcmToken) {
  await fcm.sendToToken(device.fcmToken, "membership", "Lisensi Segera Berakhir", "...");
}
```

Belum ada command Telegram siap pakai untuk ini — tinggal tambahkan kalau
dibutuhkan, memakai `fcm.sendToToken(...)` yang sudah tersedia di
`lib/fcmClient.js`.
