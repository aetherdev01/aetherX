package com.aether.x.ui.tweak

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.ui.theme.AetherMonoFamily
import com.aether.x.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * v3.6 — Advanced -> Terminal Root. Konsol satu-perintah: ketik perintah,
 * jalankan sebagai root, lihat output. Disediakan untuk pengguna teknis;
 * perintah dijalankan APA ADANYA dengan hak root, jadi hati-hati.
 */
private const val MAX_OUTPUT_CHARS = 20_000

private val QUICK_COMMANDS = listOf(
    "uname -a",
    "getprop ro.build.fingerprint",
    "cat /proc/cpuinfo | head -n 20",
    "dumpsys battery",
    "df -h",
    "free -m",
)

private data class TerminalEntry(val command: String, val output: String, val success: Boolean)

@Composable
fun TerminalScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOfEntries() }
    val scroll = rememberScrollState()

    fun run(command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty() || running) return
        running = true
        scope.launch {
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
            val entry = if (executor == null) {
                TerminalEntry(cmd, "Akses root tidak tersedia.", false)
            } else {
                val result = executor.exec(cmd)
                val text = buildString {
                    append(result.outputText)
                    if (result.errorText.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(result.errorText)
                    }
                }.ifBlank { "(tanpa output)" }
                TerminalEntry(cmd, text.take(MAX_OUTPUT_CHARS), result.success)
            }
            history.add(entry)
            input = ""
            running = false
        }
    }

    LaunchedEffect(history.size) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Terminal Root",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Perintah dijalankan sebagai root",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { history.clear() }, enabled = history.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = "Bersihkan",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            QUICK_COMMANDS.forEach { cmd ->
                Text(
                    text = cmd,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = AetherMonoFamily,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable { input = cmd }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .verticalScroll(scroll)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (history.isEmpty()) {
                Text(
                    text = "Ketik perintah di bawah, atau pilih salah satu contoh di atas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            history.forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "# ${entry.command}",
                        style = TextStyle(fontFamily = AetherMonoFamily, fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = entry.output,
                        style = TextStyle(fontFamily = AetherMonoFamily, fontSize = 12.sp),
                        color = if (entry.success) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("perintah…", fontFamily = AetherMonoFamily) },
            textStyle = TextStyle(fontFamily = AetherMonoFamily, fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { run(input) }),
            trailingIcon = {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { run(input) }, enabled = input.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = "Jalankan",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            shape = MaterialTheme.shapes.large,
        )
    }
}

private fun mutableStateListOfEntries() = androidx.compose.runtime.mutableStateListOf<TerminalEntry>()
