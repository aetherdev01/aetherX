package com.aether.x.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.ui.about.AboutScreen
import com.aether.x.ui.membership.MembershipScreen
import com.aether.x.ui.settings.SettingsScreen
import com.aether.x.ui.tweak.TweakScreen
import com.aether.x.ui.tweak.TweakViewModel
import com.aether.x.ui.whatsnew.WhatsNewDialog

private enum class MainTab { TWEAK, MEMBERSHIP, ABOUT, SETTINGS }

@Composable
fun MainScreen(
    onManageAccess: () -> Unit,
    onNavigateToGameBooster: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(MainTab.TWEAK) }

    val tweakViewModel: TweakViewModel = viewModel()

    WhatsNewDialog()

    val navItems = remember {
        listOf(MainTab.TWEAK, MainTab.MEMBERSHIP, MainTab.ABOUT, MainTab.SETTINGS)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AetherBottomNavBar(
                items = listOf(
                    AetherNavItem(Icons.Outlined.SpaceDashboard, stringResource(R.string.nav_bottom_dashboard)),
                    AetherNavItem(Icons.Outlined.WorkspacePremium, stringResource(R.string.nav_membership)),
                    AetherNavItem(Icons.Outlined.Info, stringResource(R.string.nav_about)),
                    AetherNavItem(Icons.Outlined.Settings, stringResource(R.string.nav_settings)),
                ),
                selectedIndex = navItems.indexOf(selectedTab),
                onSelect = { index -> selectedTab = navItems[index] },
            )
        },
    ) { padding ->
        // Transisi antar tab: fade + scale halus (bukan potong langsung),
        // supaya perpindahan konten terasa mulus mengikuti indikator navbar
        // yang meluncur di bawah.
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220))) togetherWith
                    (fadeOut(tween(140)) + scaleOut(targetScale = 1.02f, animationSpec = tween(140)))
            },
            label = "mainTabContent",
        ) { tab ->
            when (tab) {
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
}
