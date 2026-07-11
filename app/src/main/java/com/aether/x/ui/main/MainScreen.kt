package com.aether.x.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.ui.about.AboutScreen
import com.aether.x.ui.membership.MembershipScreen
import com.aether.x.ui.settings.SettingsScreen
import com.aether.x.ui.tweak.TweakScreen
import com.aether.x.ui.tweak.TweakViewModel

private enum class MainTab { TWEAK, MEMBERSHIP, ABOUT, SETTINGS }

@Composable
fun MainScreen(
    onManageAccess: () -> Unit,
    onNavigateToGameBooster: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(MainTab.TWEAK) }

    val tweakViewModel: TweakViewModel = viewModel()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 0.dp,
            ) {
                // RILIS v2.0 (lihat perintah rework — "di tab Tweak itu
                // harusnya Textnya Dashboard Bukan Tweak, ganti beserta
                // ikonnya"): label & ikon tab bottom-nav paling kiri diganti
                // dari "Tweak" (Icons.Outlined.Tune) jadi "Dashboard"
                // (Icons.Outlined.SpaceDashboard — SAMA PERSIS dengan ikon
                // item drawer "Dashboard" di dalam TweakScreen, supaya
                // konsisten secara visual).
                //
                // PENTING — TIDAK mengubah string nav_tweak: string itu
                // JUGA dipakai untuk label item drawer KEDUA di dalam
                // TweakScreen ("Tweak", sub-tab kontrol tweak — BEDA dari
                // sub-tab "Dashboard" pertama, lihat TweakDrawerContent).
                // Mengubah nilai nav_tweak langsung akan membuat DUA item
                // drawer sama-sama bertuliskan "Dashboard" — kesalahan
                // yang sama persis dengan "percobaan rename sebelumnya...
                // DIBATALKAN karena salah interpretasi" (lihat komentar
                // riwayat di strings.xml dekat nav_tweak/nav_dashboard).
                // Jadi dipakai string BARU (nav_bottom_dashboard) khusus
                // label tab bottom-nav ini, tidak menyentuh nav_tweak sama
                // sekali — MainTab.TWEAK (nama enum) & TweakScreen (nama
                // composable/route) TIDAK diganti, murni perubahan teks+ikon
                // yang tampil ke pengguna.
                NavigationBarItem(
                    selected = selectedTab == MainTab.TWEAK,
                    onClick = { selectedTab = MainTab.TWEAK },
                    icon = { Icon(Icons.Outlined.SpaceDashboard, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_bottom_dashboard)) },
                    colors = aetherNavColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.MEMBERSHIP,
                    onClick = { selectedTab = MainTab.MEMBERSHIP },
                    icon = { Icon(Icons.Outlined.WorkspacePremium, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_membership)) },
                    colors = aetherNavColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.ABOUT,
                    onClick = { selectedTab = MainTab.ABOUT },
                    icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_about)) },
                    colors = aetherNavColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.SETTINGS,
                    onClick = { selectedTab = MainTab.SETTINGS },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = aetherNavColors(),
                )
            }
        },
    ) { padding ->
        when (selectedTab) {
            MainTab.TWEAK -> TweakScreen(
                modifier = Modifier,
                contentPadding = padding,
                viewModel = tweakViewModel,
                onNavigateToGameBooster = onNavigateToGameBooster,
            )
            MainTab.MEMBERSHIP -> MembershipScreen(
                modifier = Modifier,
                contentPadding = padding,
            )
            MainTab.ABOUT -> AboutScreen(
                modifier = Modifier,
                contentPadding = padding,
            )
            MainTab.SETTINGS -> SettingsScreen(
                modifier = Modifier,
                contentPadding = padding,
                onManageAccess = onManageAccess,
            )
        }
    }
}

@Composable
private fun aetherNavColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
)
