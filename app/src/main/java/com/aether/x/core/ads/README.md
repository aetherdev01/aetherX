# Infrastruktur Ads (Unity Ads) — Rewarded + Interstitial

Dua sistem iklan terpisah, keduanya khusus pengguna FREE (non-member) dan
keduanya SENGAJA didesain "tidak mengganggu":

- **Rewarded** (`RewardGate`) — fondasi generik "buka/pakai lagi dengan
  nonton iklan", BELUM dipasang ke fitur mana pun. Lihat KDoc `RewardGate`
  untuk contoh pemasangan.
- **Interstitial** (`InterstitialAdGate`) — SUDAH dipasang di satu titik:
  `TweakViewModel.onKillBackgroundAppsChange`, tampil SETELAH aksi kill
  background apps selesai (bukan sebelum/memblokir), dibatasi cooldown 1
  menit antar tampilan.

## File yang terlibat

| File | Peran |
|---|---|
| `data/RewardQuota.kt` | Model state kuota rewarded (per `featureKey`) + serializer JSON |
| `data/AetherXPreferences.kt` | `getRewardQuota()`/`setRewardQuota()` (DataStore) + `AppPreferences.isMembershipActive` (cache lokal status membership, dipakai kedua sistem iklan) |
| `core/ads/RewardedAdManager.kt` | Interface network-agnostic rewarded + `NoOpRewardedAdManager` (default aman) |
| `core/ads/UnityRewardedAdManager.kt` | Implementasi Unity Ads rewarded. `GAME_ID = "6091240"`, `PLACEMENT_ID = "Rewarded_Android"` |
| `core/ads/RewardGate.kt` | Logika rewarded: cek member → cek kuota gratis → minta tonton iklan |
| `core/ads/InterstitialAdManager.kt` | Interface network-agnostic interstitial + `NoOpInterstitialAdManager` |
| `core/ads/UnityInterstitialAdManager.kt` | Implementasi Unity Ads interstitial. `GAME_ID = "6091240"`, `PLACEMENT_ID = "Interstitial_Android"` |
| `core/ads/InterstitialAdGate.kt` | Logika interstitial: skip member, cooldown 1 menit, tidak pernah blokir aksi |
| `AetherXApp.kt` | Singleton `rewardedAdManager`, `interstitialAdManager`, `interstitialAdGate` + init di `MainActivity.onCreate` |

## Kredensial (SUDAH diisi)

Kedua `GAME_ID` di `UnityRewardedAdManager` dan `UnityInterstitialAdManager`
memakai Game ID Android project ini (satu Game ID berlaku untuk seluruh
platform Android, bukan per placement — beda dari `PLACEMENT_ID` yang
memang satu per ad unit).

**Kalau muncul error seperti "game id belum didefinisikan" / ad unit tidak
ditemukan** padahal `GAME_ID`/`PLACEMENT_ID` di kode sudah benar, itu
hampir pasti bukan bug kode — cek di **Unity Ads Dashboard**
(dashboard.unity.com -> project -> Monetization -> Ad Units):
1. `6091240` benar Game ID **Android** (bukan Game ID iOS project ini, kalau ada).
2. Ad unit `Rewarded_Android` dan `Interstitial_Android` statusnya **Live** (bukan draft).
3. Package name Android di dashboard cocok dengan `applicationId` app ini.
4. Kalau project/ad unit baru dibuat, tunggu propagasi (bisa sampai beberapa jam).

Set `testMode` di `AetherXApp` (mengikuti `BuildConfig.DEBUG`) ke `true`
sementara untuk debug fill-rate/pipeline tanpa memakai inventory asli.

## Cara pasang RewardGate ke fitur baru (BELUM dilakukan)

1. Instansiasi di ViewModel fitur:
   ```kotlin
   private val rewardGate = RewardGate(
       preferences = AetherXPreferences(application),
       adManager = AetherXApp.rewardedAdManager,
   )
   ```
2. Panggil `checkAccess()` / `consumeUse()` / `watchAdForCredit()` — lihat
   contoh lengkap di KDoc `RewardGate`. `isMember` didapat dari
   `preferences.preferences.first().isMembershipActive` (cache lokal, cepat)
   atau `MembershipViewModel.status.value == MembershipUiStatus.ACTIVE`
   (kalau ViewModel itu kebetulan sudah ada di scope yang sama).

## Cara pasang InterstitialAdGate ke titik baru

```kotlin
// Setelah aksi sekali-jalan selesai, di titik transisi natural:
val isMember = preferences.preferences.first().isMembershipActive
AetherXApp.interstitialAdGate.maybeShow(activity, isMember = isMember)
```
`activity` didapat dari Composable pemanggil (`LocalContext.current as?
Activity`) dan dilempar sebagai parameter transient ke fungsi ViewModel —
JANGAN disimpan sebagai field ViewModel (leak). Lihat pemasangan nyata di
`TweakViewModel.onKillBackgroundAppsChange` + `TweakScreen.kt`.

## Yang SUDAH otomatis benar tanpa langkah tambahan

- Member (`isMembershipActive` true / `MembershipUiStatus.ACTIVE`) tidak
  pernah melihat iklan sama sekali, di kedua sistem, ditegakkan di satu
  titik masing-masing (`RewardGate` / `InterstitialAdGate`).
- Kalau GAME_ID/PLACEMENT_ID salah atau ad unit belum live: `preload()`/
  `show()` gagal dengan aman (log warning, TIDAK CRASH) — fitur yang
  dipasangi gate tetap berfungsi normal, cuma tanpa iklan.
- Kuota gratis rewarded reset otomatis tiap hari (per zona waktu device).
- Interstitial tidak pernah memblokir/menunda aksi yang memicunya, dan
  dibatasi cooldown 1 menit supaya tidak muncul beruntun.
