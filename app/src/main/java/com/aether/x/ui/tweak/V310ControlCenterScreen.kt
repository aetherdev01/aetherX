package com.aether.x.ui.tweak

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.BuildConfig
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.AppPreferences
import com.aether.x.data.GameProfileSerializer
import com.aether.x.data.TweakHistoryRepository
import com.aether.x.data.TweakHistoryEntry
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.update.UpdateViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * v3.10 utility center. Features already implemented elsewhere in the app are
 * linked here; profile I/O, compatibility checks, haptics, and history are
 * functional, local-first actions.
 */
@Composable
fun V310ControlCenterScreen(
    modifier: Modifier = Modifier,
    onOpenTweaks: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenKernelManager: () -> Unit,
    onOpenTerminal: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context.applicationContext) { AetherXPreferences(context.applicationContext) }
    val appPrefs by preferences.preferences.collectAsState(initial = AppPreferences())
    val privilege by PrivilegeManager.status.collectAsStateWithLifecycle()
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val historyRepository = remember(context.applicationContext) { TweakHistoryRepository(context.applicationContext) }
    val favoritesStore = remember(context.applicationContext) { context.applicationContext.getSharedPreferences("aetherx_quick_actions", 0) }
    var favoriteActions by remember(favoritesStore) {
        mutableStateOf(favoritesStore.getStringSet("favorites", mutableSetOf("profiles", "monitor"))?.toSet() ?: setOf("profiles", "monitor"))
    }
    var history by remember { mutableStateOf(historyRepository.read()) }
    var importExportMessage by remember { mutableStateOf<String?>(null) }
    var blurValue by remember(appPrefs.blurStrength) { mutableFloatStateOf(appPrefs.blurStrength.toFloat()) }

    fun refreshHistory() { history = historyRepository.read() }

    val exportProfiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val profiles = preferences.preferences.first()
                val raw = GameProfileSerializer.serialize(profiles.gameProfiles)
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(raw) }
                            ?: error("Tidak dapat membuka file tujuan")
                    }.isSuccess
                }
                importExportMessage = if (written) "Profil berhasil diekspor (${profiles.gameProfiles.size})" else "Ekspor gagal. Coba pilih lokasi lain."
                historyRepository.record("Ekspor profil", "${profiles.gameProfiles.size} profil", written)
                refreshHistory()
            }
        }
    }

    val importProfiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("File tidak dapat dibaca")
                        val json = JSONObject(raw)
                        val parsed = GameProfileSerializer.deserialize(json.toString())
                        if (parsed.isEmpty() && json.length() > 0) error("Format profil tidak valid")
                        parsed.mapNotNull { (pkg, profile) ->
                            val normalizedPkg = pkg.trim()
                            if (!normalizedPkg.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) null
                            else normalizedPkg to profile.copy(packageName = normalizedPkg)
                        }
                    }
                }
                try {
                    val profiles = result.getOrThrow()
                    withContext(Dispatchers.IO) {
                        profiles.forEach { (_, profile) -> preferences.saveGameProfile(profile) }
                    }
                    importExportMessage = "Impor selesai: ${profiles.size} profil diproses"
                    historyRepository.record("Impor profil", "${profiles.size} profil", true)
                } catch (error: Throwable) {
                    importExportMessage = "Impor gagal: ${error.message ?: "format file tidak valid"}"
                    historyRepository.record("Impor profil", error.message ?: "Format file tidak valid", false)
                }
                refreshHistory()
            }
        }
    }

    val socManufacturer = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() } ?: Build.MANUFACTURER
        } else Build.MANUFACTURER
    }
    val socModel = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() } ?: Build.HARDWARE
        } else Build.HARDWARE
    }
    val cpuFreqPath = remember { File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") }
    val gpuNodes = remember {
        runCatching {
            File("/sys/class/devfreq").listFiles().orEmpty()
                .map { it.name }
                .filter { name -> name.contains("gpu", true) || name.contains("mali", true) || name.contains("kgsl", true) }
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) { refreshHistory() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "AetherX Control Center",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME} · alat baru dan status sistem",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Smart Tweak Recommendations", watermarkIcon = Icons.Outlined.SettingsSuggest) {
            Text("Mulai dari perubahan yang bisa dipantau dan dibatalkan. AetherX tidak akan mengaktifkan tweak secara otomatis hanya berdasarkan model perangkat.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            RecommendationRow(
                title = if (privilege.rootGranted) "Root terdeteksi" else "Root belum tersedia",
                detail = if (privilege.rootGranted) "Pemeriksaan root lolos. Perubahan CPU/GPU tetap perlu didukung kernel." else "Fitur yang menulis parameter kernel memerlukan akses root yang diizinkan.",
                good = privilege.rootGranted,
            )
            RecommendationRow(
                title = "Pilih profil game terlebih dahulu",
                detail = "Profil per game lebih mudah dikelola daripada menerapkan semua tweak secara global.",
                good = true,
            )
            RecommendationRow(
                title = "Hindari memaksa thermal limit",
                detail = "Biarkan pengelolaan suhu perangkat bekerja. Hasil performa dapat berbeda menurut ROM dan kernel.",
                good = false,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenTweaks, modifier = Modifier.weight(1f)) { Text("Buka Tweak") }
                OutlinedButton(onClick = onOpenProfiles, modifier = Modifier.weight(1f)) { Text("Profil Game") }
            }
        }

        SectionCard(title = "Device Compatibility Center", watermarkIcon = Icons.Outlined.Devices) {
            CompatibilityRow("Perangkat", "${Build.MANUFACTURER} ${Build.MODEL}")
            CompatibilityRow("SoC", "$socManufacturer · $socModel")
            CompatibilityRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            CompatibilityRow("ABI", Build.SUPPORTED_ABIS.joinToString(", "))
            CompatibilityRow("Root", if (privilege.rootGranted) "Diizinkan" else if (privilege.rootAvailable == true) "Terdeteksi, belum diizinkan" else "Tidak tersedia")
            CompatibilityRow("CPU frequency interface", if (cpuFreqPath.exists()) "Terdeteksi" else "Belum terbaca")
            CompatibilityRow("GPU devfreq node", gpuNodes.firstOrNull() ?: "Tidak terdeteksi / akses dibatasi")
            Text("Status interface dibaca dari file sistem yang bisa diakses saat ini. ‘Belum terbaca’ tidak selalu berarti hardware tidak mendukungnya.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(title = "Diagnostics & Restore", watermarkIcon = Icons.Outlined.RestartAlt) {
            RecommendationRow(
                title = if (privilege.rootGranted) "Akses sistem siap" else "Akses sistem terbatas",
                detail = if (privilege.rootGranted) "AetherX mendapat izin root untuk fitur yang membutuhkannya." else "Fitur kernel akan tetap nonaktif sampai izin root tersedia.",
                good = privilege.rootGranted,
            )
            RecommendationRow(
                title = "Cadangan profil tersedia",
                detail = "Gunakan ekspor JSON untuk menyimpan profil dan impor untuk memulihkannya setelah instal ulang atau pindah perangkat.",
                good = true,
            )
            Text("Untuk mengembalikan tweak utama ke nilai default, buka halaman Tweak dan gunakan tombol Reset di bagian bawah. Periksa perubahan sebelum menjalankan reset.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOpenTweaks, modifier = Modifier.fillMaxWidth()) { Text("Buka pengaturan Tweak") }
        }

        SectionCard(title = "Advanced Hardware Monitor", watermarkIcon = Icons.Outlined.Memory) {
            Text("Pantau beban CPU per-core dan GPU di halaman monitor. Bagian lanjutan menampilkan metrik yang tersedia tanpa mengarang angka jika kernel membatasi akses.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenMonitor, modifier = Modifier.fillMaxWidth(), enabled = privilege.rootGranted) {
                Text(if (privilege.rootGranted) "Buka Advanced Monitor" else "Memerlukan akses root")
            }
            if (!privilege.rootGranted) {
                Text("Berikan izin root dari pengelola root, lalu buka menu ini lagi.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard(title = "Tweak History", watermarkIcon = Icons.Outlined.History) {
            Text("Riwayat lokal menyimpan perubahan yang dicatat AetherX di perangkat ini. Maksimum 40 entri terakhir.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (history.isEmpty()) {
                Text("Belum ada riwayat perubahan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                history.take(8).forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    HistoryRow(item)
                }
            }
            TextButton(
                onClick = { historyRepository.clear(); refreshHistory() },
                enabled = history.isNotEmpty(),
                modifier = Modifier.align(Alignment.End),
            ) { Text("Hapus riwayat") }
        }

        SectionCard(title = "Export & Import Profiles", watermarkIcon = Icons.Outlined.Backup) {
            Text("Cadangkan atau pindahkan profil game sebagai JSON. File diimpor dengan validasi nama paket sebelum disimpan.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportProfiles.launch("AetherX_game_profiles.json") },
                    modifier = Modifier.weight(1f),
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Ekspor")
                }
                Button(
                    onClick = { importProfiles.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.weight(1f),
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Impor")
                }
            }
            importExportMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }

        SectionCard(title = "Performance Presets", watermarkIcon = Icons.Outlined.Speed) {
            Text("Preset hemat baterai, seimbang, dan performa sudah tersedia di Kernel Manager. Pilihan frekuensi/governor diambil dari nilai yang terdeteksi didukung perangkat.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenKernelManager, enabled = privilege.rootGranted, modifier = Modifier.fillMaxWidth()) {
                Text(if (privilege.rootGranted) "Buka Kernel Manager" else "Memerlukan akses root")
            }
        }

        SectionCard(title = "Favorites & Quick Actions", watermarkIcon = Icons.Outlined.Bolt) {
            Text("Tekan bintang untuk menyimpan aksi favorit. Aksi favorit akan muncul lebih dulu dan pilihan disimpan lokal.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            val actions = listOf(
                V310QuickAction("tweaks", "Tweak", onOpenTweaks),
                V310QuickAction("dashboard", "Dashboard", onOpenDashboard),
                V310QuickAction("profiles", "Profil game", onOpenProfiles),
                V310QuickAction("monitor", "Hardware Monitor", onOpenMonitor, requiresRoot = true),
                V310QuickAction("kernel", "Kernel Manager / Preset", onOpenKernelManager, requiresRoot = true),
                V310QuickAction("terminal", "Command Center", onOpenTerminal, requiresRoot = true),
            ).sortedByDescending { it.id in favoriteActions }
            actions.forEach { action ->
                QuickActionRow(
                    action = action,
                    favorite = action.id in favoriteActions,
                    enabled = !action.requiresRoot || privilege.rootGranted,
                    onOpen = action.onOpen,
                    onToggleFavorite = {
                        val updated = favoriteActions.toMutableSet().apply {
                            if (action.id in this) remove(action.id) else add(action.id)
                        }.toSet()
                        favoriteActions = updated
                        favoritesStore.edit().putStringSet("favorites", updated.toMutableSet()).apply()
                    },
                )
            }
        }

        SectionCard(title = "Command Center", watermarkIcon = Icons.Outlined.Terminal) {
            Text("Terminal root yang sudah ada menyediakan eksekusi perintah dan menampilkan output. Periksa perintah sebelum menjalankannya karena perintah shell dapat mengubah sistem.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenTerminal, modifier = Modifier.fillMaxWidth(), enabled = privilege.rootGranted) {
                Text(if (privilege.rootGranted) "Buka Terminal" else "Memerlukan akses root")
            }
        }

        SectionCard(title = "Dashboard Customization", watermarkIcon = Icons.Outlined.Tune) {
            Text("Urutan kartu Dashboard sudah dapat diatur dengan menahan kartu lalu menggesernya. Lepaskan setelah mencapai posisi yang diinginkan; urutan tersimpan otomatis.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke Dashboard") }
        }

        SectionCard(title = "Update Center", watermarkIcon = Icons.Outlined.Refresh) {
            CompatibilityRow("Versi terpasang", BuildConfig.VERSION_NAME)
            val update = updateState.info
            when {
                update.latestVersionCode <= 0 -> Text("Belum ada informasi update dari server. Periksa kembali saat koneksi tersedia.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                update.latestVersionCode > updateState.currentVersionCode -> {
                    CompatibilityRow("Versi tersedia", update.latestVersionName.ifBlank { "Build ${update.latestVersionCode}" })
                    if (update.description.isNotBlank()) Text(update.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { openExternalUrl(context, update.downloadUrl) },
                        enabled = update.downloadUrl.startsWith("https://"),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Buka unduhan update") }
                }
                else -> Text("Belum ada update baru yang dilaporkan server. Status bisa berubah saat koneksi tersedia.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionCard(title = "UI Motion & Haptics", watermarkIcon = Icons.Outlined.Vibration) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Getaran sentuhan", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Aktifkan atau nonaktifkan respons haptik slider dan kontrol tweak.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = appPrefs.hapticsEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferences.setHapticsEnabled(enabled)
                            historyRepository.record("Pengaturan haptik", if (enabled) "Diaktifkan" else "Dinonaktifkan")
                            refreshHistory()
                        }
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Kurangi animasi", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Transisi tab dan animasi kartu dibuat lebih singkat atau tanpa gerakan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = appPrefs.reduceMotionEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferences.setReduceMotionEnabled(enabled)
                            historyRepository.record("Pengaturan animasi", if (enabled) "Animasi dikurangi" else "Animasi normal")
                            refreshHistory()
                        }
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kekuatan efek Liquid Glass", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("${blurValue.toInt()}% · diterapkan ke kartu dan panel yang memakai efek blur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${blurValue.toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = blurValue,
                onValueChange = { blurValue = it },
                valueRange = 0f..100f,
                onValueChangeFinished = {
                    scope.launch {
                        preferences.setBlurStrength(blurValue.toInt())
                        historyRepository.record("Kekuatan Liquid Glass", "${blurValue.toInt()}%")
                        refreshHistory()
                    }
                },
            )
        }
    }
}

private data class V310QuickAction(
    val id: String,
    val label: String,
    val onOpen: () -> Unit,
    val requiresRoot: Boolean = false,
)

@Composable
private fun QuickActionRow(
    action: V310QuickAction,
    favorite: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(action.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            if (!enabled) Text("Memerlukan root", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onToggleFavorite) {
            androidx.compose.material3.Icon(
                imageVector = if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                contentDescription = if (favorite) "Hapus dari favorit" else "Tambahkan ke favorit",
                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onOpen, enabled = enabled) { Text("Buka") }
    }
}

@Composable
private fun RecommendationRow(title: String, detail: String, good: Boolean) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.material3.Icon(
            imageVector = if (good) Icons.Outlined.CheckCircle else Icons.Outlined.Bolt,
            contentDescription = null,
            tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompatibilityRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HistoryRow(item: TweakHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.Icon(
                imageVector = if (item.success) Icons.Outlined.CheckCircle else Icons.Outlined.RestartAlt,
                contentDescription = null,
                tint = if (item.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
        if (item.detail.isNotBlank()) Text(item.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.timestamp)),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun openExternalUrl(context: android.content.Context, rawUrl: String) {
    if (!rawUrl.startsWith("https://")) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

