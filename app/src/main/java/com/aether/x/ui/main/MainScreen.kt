package com.aether.x.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.ui.about.AboutScreen
import com.aether.x.ui.membership.MembershipScreen
import com.aether.x.ui.settings.SettingsScreen
import com.aether.x.ui.tweak.TweakScreen
import com.aether.x.ui.tweak.TweakViewModel
import com.aether.x.ui.whatsnew.WhatsNewDialog
import kotlin.math.roundToInt

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

    // Navbar ala iOS 26: menyembunyikan diri (geser ke bawah) saat konten
    // di-scroll ke bawah, dan muncul lagi saat di-scroll ke atas. Tinggi bar
    // diukur otomatis lewat onSizeChanged, jadi tidak perlu angka hardcode
    // yang bisa basi kalau tampilan navbar berubah lagi nanti.
    var navBarHeightPx by remember { mutableStateOf(0f) }
    var navBarOffsetPx by remember { mutableStateOf(0f) }
    val navBarScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val newOffset = navBarOffsetPx + available.y
                navBarOffsetPx = newOffset.coerceIn(-navBarHeightPx, 0f)
                return Offset.Zero
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(navBarScrollConnection),
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
                modifier = Modifier
                    .onSizeChanged { navBarHeightPx = it.height.toFloat() }
                    .offset { IntOffset(x = 0, y = -navBarOffsetPx.roundToInt()) },
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
