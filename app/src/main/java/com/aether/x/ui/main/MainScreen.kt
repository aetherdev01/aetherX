package com.aether.x.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
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
                NavigationBarItem(
                    selected = selectedTab == MainTab.TWEAK,
                    onClick = { selectedTab = MainTab.TWEAK },
                    icon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_tweak)) },
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
