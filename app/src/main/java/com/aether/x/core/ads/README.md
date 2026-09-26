# Infrastruktur Ads — Rewarded (Unity Ads) + Interstitial (AdMob)

Dua sistem iklan terpisah, keduanya khusus pengguna FREE (non-member) dan
keduanya SENGAJA didesain "tidak mengganggu":

- **Rewarded** (`RewardGate`) — fondasi generik "buka/pakai lagi dengan
  nonton iklan", BELUM dipasang ke fitur mana pun. Lihat KDoc `RewardGate`
  untuk contoh pemasangan.
- **Interstitial** (`InterstitialAdGate`, provider **AdMob** — v3.5 pindah
  dari Unity Ads, lihat `UnityInterstitialAdManager.kt` yang masih ada di
  repo tapi sudah tidak dipakai, buat referensi/rollback kalau perlu)
  dipasang KONSISTEN di semua
  toggle/aksi tweak lintas `TweakViewModel`, `GameProfileViewModel`,
  `BuildPropViewModel`, `KernelManagerViewModel`, `SettingsViewModel`
  (toggle enable/disable crosshair & FPS monitor), dan `AppManagerViewModel`
  (freeze/unfreeze, force stop, clear cache), tampil SETELAH aksi selesai
  dan berhasil (bukan sebelum/memblokir), dibatasi cooldown 1 menit antar
  tampilan (cooldown dibagi lintas semua titik pemasangan, bukan per fitur).
  Dikecualikan dengan sengaja: sub-slider kosmetik (warna/ukuran/opacity
  crosshair, style FPS monitor), search field, dan seluruh
  `GameBoosterScreenViewModel` karena panelnya berjalan sebagai system
  overlay window di atas game yang sedang dimainkan (bukan `Activity`) —
  memaksa interstitial full-screen di sana akan mengganggu gameplay
  langsung dan tidak ada `Activity` yang bisa dilempar ke `maybeShow()`.

## File yang terlibat

| File | Peran |
|---|---|
| `data/RewardQuota.kt` | Model state kuota rewarded (per `featureKey`) + serializer JSON |
| `data/AetherXPreferences.kt` | `getRewardQuota()`/`setRewardQuota()` (DataStore) + `AppPreferences.isMembershipActive` (cache lokal status membership, dipakai kedua sistem iklan) |
| `core/ads/RewardedAdManager.kt` | Interface network-agnostic rewarded + `NoOpRewardedAdManager` (default aman) |
| `core/ads/UnityRewardedAdManager.kt` | Implementasi Unity Ads rewarded. `GAME_ID = "6091240"`, `PLACEMENT_ID = "Rewarded_Android"` |
| `core/ads/RewardGate.kt` | Logika rewarded: cek member → cek kuota gratis → minta tonton iklan |
| `core/ads/InterstitialAdManager.kt` | Interface network-agnostic interstitial + `NoOpInterstitialAdManager` |
| `core/ads/AdMobInterstitialAdManager.kt` | Implementasi **AdMob** interstitial (aktif). Ad unit ID terenkripsi lewat `SecretStrings` |
| `core/ads/UnityInterstitialAdManager.kt` | Implementasi Unity Ads interstitial — **TIDAK dipakai lagi** sejak v3.5, disimpan untuk rollback |
| `core/ads/InterstitialAdGate.kt` | Logika interstitial: skip member, cooldown 1 menit, tidak pernah blokir aksi |
| `AndroidManifest.xml` | Meta-data `com.google.android.gms.ads.APPLICATION_ID` (App ID AdMob, WAJIB plaintext) |
| `AetherXApp.kt` | Singleton `rewardedAdManager`, `interstitialAdManager`, `interstitialAdGate` + init di `MainActivity.onCreate` |

## Kredensial (SUDAH diisi)

**Rewarded (Unity Ads)** — `GAME_ID` di `UnityRewardedAdManager` memakai
Game ID Android project ini (satu Game ID berlaku untuk seluruh platform
Android, bukan per placement — beda dari `PLACEMENT_ID` yang memang satu
per ad unit).

**Kalau muncul error seperti "game id belum didefinisikan" / ad unit tidak
ditemukan** padahal `GAME_ID`/`PLACEMENT_ID` di kode sudah benar, itu
hampir pasti bukan bug kode — cek di **Unity Ads Dashboard**
(dashboard.unity.com -> project -> Monetization -> Ad Units):
1. `6091240` benar Game ID **Android** (bukan Game ID iOS project ini, kalau ada).
2. Ad unit `Rewarded_Android` statusnya **Live** (bukan draft).
3. Package name Android di dashboard cocok dengan `applicationId` app ini.
4. Kalau project/ad unit baru dibuat, tunggu propagasi (bisa sampai beberapa jam).

**Interstitial (AdMob)** — di `AdMobInterstitialAdManager`:
- App ID: `ca-app-pub-5043818314955328~5157778665` (plaintext di
  `AndroidManifest.xml`, wajib format Google — tidak bisa dienkripsi karena
  dibaca SDK dari manifest sebelum kode Kotlin jalan).
- Ad unit interstitial: `ca-app-pub-5043818314955328/8307695341`
  (terenkripsi lewat `SecretStrings`, sama pola dengan `PLACEMENT_ID` Unity).

Ad unit di atas **masih berstatus "in review"** di AdMob console per
pemasangan ini — ad unit baru SELALU butuh masa peninjauan Google sebelum
mulai serve iklan asli (bisa dari beberapa jam sampai beberapa hari), jadi
`load()` gagal/fill rate nol selama masa itu adalah **normal**, bukan bug.
`AdMobInterstitialAdManager` sudah menangani ini dengan aman: gagal load →
retry otomatis dengan backoff (lihat `scheduleRetry`), tidak pernah crash,
dan `InterstitialAdGate` tidak pernah memblokir aksi walau iklan belum
siap. Sambil menunggu review, `testMode = true` (default di build debug,
lihat `BuildConfig.DEBUG`) memakai ad unit TEST resmi Google
(`ca-app-pub-3940256099942544/1033173712`) supaya pipeline tetap bisa
dicoba dengan fill rate 100% tanpa nunggu ad unit asli lolos review.

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
`TweakViewModel` (hampir semua fungsi `applyAndPersist`), `GameProfileViewModel.updateSelectedProfile`,
`AppManagerViewModel` (`toggleFreeze`/`forceStopApp`/`clearCacheApp`), dan
helper privat `maybeShowAd(activity)` yang direplikasi di tiap ViewModel
tersebut (pola sengaja diduplikasi kecil-kecilan per file, bukan diekstrak
ke kelas dasar bersama, supaya tiap ViewModel tetap independen).

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
