package com.aether.x.ui.main

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.AetherXApp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.aether.x.ui.components.LocalAetherHazeState
import com.aether.x.R
import com.aether.x.ui.about.AboutScreen
import com.aether.x.ui.membership.MembershipScreen
import com.aether.x.ui.settings.SettingsScreen
import com.aether.x.ui.theme.AetherAmber
import com.aether.x.ui.theme.AetherBlue
import com.aether.x.ui.theme.AetherCyan
import com.aether.x.ui.theme.AetherViolet
import com.aether.x.ui.tweak.TweakScreen
import com.aether.x.ui.tweak.TweakViewModel
import com.aether.x.ui.whatsnew.WhatsNewDialog
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class MainTab { TWEAK, MEMBERSHIP, ABOUT, SETTINGS }

@Composable
fun MainScreen(
    onManageAccess: () -> Unit,
) {
    val hazeState = rememberHazeState()
    var selectedTab by remember { mutableStateOf(MainTab.TWEAK) }

    // Counter event, bukan Boolean, supaya setiap tap Dashboard dianggap
    // sebagai perintah baru—even ketika tab utama memang sudah Dashboard.
    // Ini memungkinkan TweakScreen mereset sub-tab internalnya ke HOME.
    var dashboardRequest by remember { mutableIntStateOf(0) }

    val tweakViewModel: TweakViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Iklan interstitial: trigger "kembali ke aplikasi" ──────────────────
    // Dipicu saat user kembali ke app setelah ditinggal >= 20 detik (bukan
    // saat cold start, dan tidak pernah memblokir UI). Cooldown + pengecualian
    // member ditegakkan di InterstitialAdGate.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var stoppedAt = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stoppedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val away = if (stoppedAt == 0L) 0L else System.currentTimeMillis() - stoppedAt
                    val activity = context as? Activity
                    if (activity != null && away > 0L) {
                        scope.launch {
                            AetherXApp.interstitialAdGate.onReturnToApp(
                                activity = activity,
                                isMember = tweakViewModel.state.value.isMembershipActive,
                                awayMillis = away,
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val navItems = remember {
        listOf(MainTab.TWEAK, MainTab.MEMBERSHIP, MainTab.ABOUT, MainTab.SETTINGS)
    }

    // stringResource() harus dipanggil di scope @Composable biasa, lalu dipakai
    // sebagai key remember supaya list tidak dibangun ulang di tiap recompose.
    val dashboardLabel = stringResource(R.string.nav_bottom_dashboard)
    val membershipLabel = stringResource(R.string.nav_membership)
    val aboutLabel = stringResource(R.string.nav_about)
    val settingsLabel = stringResource(R.string.nav_settings)

    // Tiap tab punya warna aksen sendiri (cyan / violet / biru / amber) —
    // dipakai pill capsule dan ikon aktif supaya tab bar berwarna.
    val navBarItems = remember(dashboardLabel, membershipLabel, aboutLabel, settingsLabel) {
        listOf(
            AetherNavItem(Icons.Outlined.SpaceDashboard, dashboardLabel, AetherCyan),
            AetherNavItem(Icons.Outlined.WorkspacePremium, membershipLabel, AetherViolet),
            AetherNavItem(Icons.Outlined.Info, aboutLabel, AetherBlue),
            AetherNavItem(Icons.Outlined.Settings, settingsLabel, AetherAmber),
        )
    }

    // Navbar menyembunyikan diri (geser ke bawah) saat konten di-scroll ke
    // bawah dan muncul lagi saat di-scroll ke atas. State ini hanya dibaca di
    // lambda `offset { }` (fase layout), jadi scroll tidak memicu recomposition.
    var navBarHeightPx by remember { mutableFloatStateOf(0f) }
    var navBarOffsetPx by remember { mutableFloatStateOf(0f) }
    val navBarScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val newOffset = navBarOffsetPx + available.y
                navBarOffsetPx = newOffset.coerceIn(-navBarHeightPx, 0f)
                return Offset.Zero
            }
        }
    }

    CompositionLocalProvider(LocalAetherHazeState provides hazeState) {
        WhatsNewDialog()
        Scaffold(
        modifier = Modifier.nestedScroll(navBarScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AetherBottomNavBar(
                items = navBarItems,
                selectedIndex = navItems.indexOf(selectedTab),
                onSelect = { index ->
                    val tab = navItems[index]
                    val changed = tab != selectedTab
                    selectedTab = tab
                    if (tab == MainTab.TWEAK) {
                        dashboardRequest++
                    }
                    if (changed) {
                        val activity = context as? Activity
                        if (activity != null) {
                            scope.launch {
                                AetherXApp.interstitialAdGate.onNavigation(
                                    activity = activity,
                                    isMember = tweakViewModel.state.value.isMembershipActive,
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .onSizeChanged { navBarHeightPx = it.height.toFloat() }
                    .offset { IntOffset(x = 0, y = -navBarOffsetPx.roundToInt()) },
            )
        },
    ) { padding ->
        // Transisi antar tab: fade + geser tipis searah navigasi. Sengaja TANPA
        // scale — scale memaksa seluruh layar di-raster ulang tiap frame dan
        // terasa berat di perangkat mid-range.
        AnimatedContent(
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            targetState = selectedTab,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.05f * dir).roundToInt() },
                ) + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> (-fullWidth * 0.03f * dir).roundToInt() },
                    ) + fadeOut(tween(140)))
            },
            label = "mainTabContent",
        ) { tab ->
            when (tab) {
                MainTab.TWEAK -> TweakScreen(
                    modifier = Modifier,
                    contentPadding = padding,
                    viewModel = tweakViewModel,
                    dashboardRequest = dashboardRequest,
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
}
