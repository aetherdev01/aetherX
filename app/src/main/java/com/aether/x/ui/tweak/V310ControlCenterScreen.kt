package com.aether.x.ui.tweak

import android.os.Build
import android.net.Uri
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.aether.x.data.GameProfileSerializer
import com.aether.x.data.TweakHistoryRepository
import com.aether.x.data.TweakHistoryEntry
import com.aether.x.ui.components.SectionCard
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.aether.x.core.locale.str
import com.aether.x.R

/**
 * v3.10 utility center kept intentionally focused: device compatibility,
 * local tweak history, and game-profile backup/restore.
 */
@Composable
fun V310ControlCenterScreen(
    modifier: Modifier = Modifier,
    onOpenMonitor: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context.applicationContext) { AetherXPreferences(context.applicationContext) }
    val privilege by PrivilegeManager.status.collectAsStateWithLifecycle()
    val historyRepository = remember(context.applicationContext) { TweakHistoryRepository(context.applicationContext) }
    var history by remember { mutableStateOf(historyRepository.read()) }
    var importExportMessage by remember { mutableStateOf<String?>(null) }

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
                            ?: error(str(R.string.v310_error_open_target))
                    }.isSuccess
                }
                importExportMessage = if (written) str(R.string.v310_control_center_profil_berhasil_diekspor, profiles.gameProfiles.size) else str(R.string.v310_control_center_ekspor_gagal_coba_pilih_lokasi)
                historyRepository.record(str(R.string.v310_control_center_ekspor_profil), str(R.string.v310_control_center_profil, profiles.gameProfiles.size), written)
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
                            ?: error(str(R.string.v310_error_file_unreadable))
                        val json = JSONObject(raw)
                        val parsed = GameProfileSerializer.deserialize(json.toString())
                        if (parsed.isEmpty() && json.length() > 0) error(str(R.string.v310_error_profile_format))
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
                    importExportMessage = str(R.string.v310_control_center_impor_selesai_profil_diproses, profiles.size)
                    historyRepository.record(str(R.string.v310_control_center_impor_profil), str(R.string.v310_control_center_profil, profiles.size), true)
                } catch (error: Throwable) {
                    importExportMessage = str(R.string.v310_control_center_impor_gagal, error.message ?: str(R.string.v310_control_center_format_file_tidak_valid))
                    historyRepository.record(str(R.string.v310_control_center_impor_profil), error.message ?: str(R.string.v310_control_center_format_file_tidak_valid_2), false)
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
                text = str(R.string.v310_control_center_pusat_kontrol_aetherx),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = str(R.string.v310_control_center_v_kompatibilitas_riwayat_dan_cadangan, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = str(R.string.v310_control_center_kompatibilitas_perangkat), watermarkIcon = Icons.Outlined.Devices) {
            CompatibilityRow(str(R.string.v310_control_center_perangkat), "${Build.MANUFACTURER} ${Build.MODEL}")
            CompatibilityRow("SoC", "$socManufacturer · $socModel")
            CompatibilityRow("Android", str(R.string.v310_control_center_api, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
            CompatibilityRow("ABI", Build.SUPPORTED_ABIS.joinToString(", "))
            CompatibilityRow("Root", if (privilege.rootGranted) str(R.string.v310_control_center_diizinkan) else if (privilege.rootAvailable == true) str(R.string.v310_control_center_terdeteksi_belum_diizinkan) else str(R.string.advanced_hardware_metrics_card_tidak_tersedia))
            CompatibilityRow("CPU frequency interface", if (cpuFreqPath.exists()) str(R.string.v310_control_center_terdeteksi) else str(R.string.v310_control_center_belum_terbaca))
            CompatibilityRow("GPU devfreq node", gpuNodes.firstOrNull() ?: str(R.string.v310_control_center_tidak_terdeteksi_akses_dibatasi))
            Text(str(R.string.v310_control_center_status_interface_dibaca_dari_file), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onOpenMonitor, modifier = Modifier.fillMaxWidth(), enabled = privilege.rootGranted) {
                Text(if (privilege.rootGranted) str(R.string.v310_control_center_buka_monitor_hardware_lanjutan) else str(R.string.v310_control_center_monitor_memerlukan_izin_root))
            }
        }

        SectionCard(title = str(R.string.v310_control_center_riwayat_tweak), watermarkIcon = Icons.Outlined.History) {
            Text(str(R.string.v310_control_center_riwayat_lokal_menyimpan_perubahan_yang), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (history.isEmpty()) {
                Text(str(R.string.v310_control_center_belum_ada_riwayat_perubahan), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            ) { Text(str(R.string.v310_control_center_hapus_riwayat)) }
        }

        SectionCard(title = str(R.string.v310_control_center_cadangan_profil_game), watermarkIcon = Icons.Outlined.Backup) {
            Text(str(R.string.v310_control_center_cadangkan_atau_pindahkan_profil_game), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportProfiles.launch("AetherX_game_profiles.json") },
                    modifier = Modifier.weight(1f),
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(str(R.string.v310_control_center_ekspor))
                }
                Button(
                    onClick = { importProfiles.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.weight(1f),
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(str(R.string.v310_control_center_impor))
                }
            }
            importExportMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
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

