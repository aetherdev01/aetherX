# Infrastruktur Reward Ads (Unity Ads)

Fondasi generik "buka/pakai lagi dengan nonton rewarded ad" — BELUM dipasang
ke fitur mana pun. Lihat KDoc `RewardGate` untuk contoh pemasangan ke fitur
nyata nanti.

## File yang terlibat

| File | Peran |
|---|---|
| `data/RewardQuota.kt` | Model state kuota (per `featureKey`) + serializer JSON |
| `data/AetherXPreferences.kt` | `getRewardQuota()` / `setRewardQuota()` — persist ke DataStore |
| `core/ads/RewardedAdManager.kt` | Interface network-agnostic + `NoOpRewardedAdManager` (default aman) |
| `core/ads/UnityRewardedAdManager.kt` | Implementasi Unity Ads sungguhan |
| `core/ads/RewardGate.kt` | Logika inti: cek member → cek kuota gratis → minta tonton iklan |
| `AetherXApp.kt` | Singleton `rewardedAdManager` + `initialize()` saat startup |

## Langkah yang MASIH PERLU dilakukan sebelum dipakai di fitur nyata

1. **Tambahkan dependency Gradle** (project export ini tidak menyertakan
   file `build.gradle` untuk diedit langsung):
   ```kotlin
   // app/build.gradle.kts
   implementation("com.unity3d.ads:unity-ads:4.+")
   ```

2. **Isi kredensial** di `UnityRewardedAdManager.kt`:
   - `GAME_ID` — dari Unity Ads Dashboard, per platform (Android).
   - `PLACEMENT_ID` — buat ad unit rewarded khusus, disarankan satu
     placement terpisah per fitur kalau nanti reward-gate dipasang di lebih
     dari satu tempat (supaya fill-rate/eCPM tiap fitur bisa dipantau
     terpisah).

3. **Pasang ke fitur nyata** — panggil `RewardGate.checkAccess()` /
   `consumeUse()` / `watchAdForCredit()` dari ViewModel fitur yang mau
   dibatasi kuota gratis + rewarded ad (lihat contoh lengkap di KDoc
   `RewardGate`). `isMember` diambil dari
   `MembershipViewModel.status.value == MembershipUiStatus.ACTIVE` — member
   SELALU dapat `RewardGateResult.Allowed` tanpa iklan sama sekali.

4. **Instansiasi `RewardGate` di ViewModel fitur**, mis.:
   ```kotlin
   private val rewardGate = RewardGate(
       preferences = AetherXPreferences(application),
       adManager = AetherXApp.rewardedAdManager,
   )
   ```

## Yang SUDAH otomatis benar tanpa langkah tambahan

- Kalau `GAME_ID`/`PLACEMENT_ID` belum diisi: `preload()`/`show()` di
  `UnityRewardedAdManager` otomatis no-op, `RewardGate.watchAdForCredit()`
  akan selalu mengembalikan `AdNotReady` — TIDAK CRASH, fitur yang sudah
  dipasangi gate tetap bisa dipakai lewat kuota gratis hariannya.
- Kuota gratis reset otomatis tiap hari (per zona waktu device), tidak
  perlu job/scheduler terpisah — lihat `RewardGate.today()`.
- Member tidak pernah melihat iklan sama sekali, di fitur mana pun yang
  nanti dipasangi gate ini — ditegakkan di satu titik (`RewardGate`), bukan
  perlu diulang tiap fitur.
