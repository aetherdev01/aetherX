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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.AetherXApp
import com.aether.x.data.OnlinePresenceRepository
import com.aether.x.R
import com.aether.x.ui.about.AboutScreen
import com.aether.x.ui.components.AdBlockGate
import com.aether.x.ui.components.AetherBackground
import com.aether.x.ui.components.LocalReduceMotionEnabled
import com.aether.x.ui.membership.MembershipScreen
import com.aether.x.ui.settings.SettingsScreen
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
    var selectedTab by remember { mutableStateOf(MainTab.TWEAK) }

    // Counter event, bukan Boolean, supaya setiap tap Dashboard dianggap
    // sebagai perintah baru—even ketika tab utama memang sudah Dashboard.
    // Ini memungkinkan TweakScreen mereset sub-tab internalnya ke HOME.
    var dashboardRequest by remember { mutableIntStateOf(0) }

    val tweakViewModel: TweakViewModel = viewModel()
    val tweakState by tweakViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val reduceMotion = LocalReduceMotionEnabled.current

    WhatsNewDialog()

    // ── Iklan interstitial: trigger "kembali ke aplikasi" ──────────────────
    // Dipicu saat user kembali ke app setelah ditinggal >= 20 detik (bukan
    // saat cold start, dan tidak pernah memblokir UI). Cooldown + pengecualian
    // member ditegakkan di InterstitialAdGate.
    val lifecycleOwner = LocalLifecycleOwner.current
    val onlinePresence = remember(context.applicationContext) {
        OnlinePresenceRepository()
    }
    val onlineUserCount by onlinePresence.onlineCount.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner, onlinePresence) {
        val presenceObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onlinePresence.setForeground(true)
                Lifecycle.Event.ON_STOP -> onlinePresence.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(presenceObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            onlinePresence.setForeground(true)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(presenceObserver)
            onlinePresence.close()
        }
    }

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

    // v3.6 — semua tab memakai satu warna aksen tema (sebelumnya empat warna
    // berbeda yang terasa terlalu ramai). Dibaca di scope @Composable lalu
    // dipakai sebagai key remember — remember { } tidak boleh membaca token tema.
    val tabAccent = MaterialTheme.colorScheme.primary
    val navBarItems = remember(dashboardLabel, membershipLabel, aboutLabel, settingsLabel, tabAccent) {
        listOf(
            AetherNavItem(Icons.Outlined.SpaceDashboard, dashboardLabel, tabAccent),
            AetherNavItem(Icons.Outlined.WorkspacePremium, membershipLabel, tabAccent),
            AetherNavItem(Icons.Outlined.Info, aboutLabel, tabAccent),
            AetherNavItem(Icons.Outlined.Settings, settingsLabel, tabAccent),
        )
    }

    // Navbar menyembunyikan diri (geser ke bawah) saat konten di-scroll ke
    // bawah dan muncul lagi saat di-scroll ke atas. State ini hanya dibaca di
    // lambda `offset { }` (fase layout), jadi scroll tidak memicu recomposition.
    var navBarHeightPx by remember { mutableFloatStateOf(0f) }
    var navBarOffsetPx by remember { mutableFloatStateOf(0f) }
    // Only toggle composition at the two endpoints. The offset itself stays in
    // the layout lambda so normal scrolling does not recompose the entire screen.
    var navBarVisible by remember { mutableStateOf(true) }
    val navBarScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val nextOffset = (navBarOffsetPx + available.y).coerceIn(-navBarHeightPx, 0f)
                navBarOffsetPx = nextOffset
                if (navBarHeightPx > 0f) {
                    val shouldShow = nextOffset > -navBarHeightPx + 1f
                    if (shouldShow != navBarVisible) navBarVisible = shouldShow
                }
                return Offset.Zero
            }
        }
    }

    // v3.8 — sumber blur navbar: konten tab (SIBLING navbar di dalam Scaffold).

    AdBlockGate(
        isMember = tweakState.isMembershipActive,
        suppress = selectedTab == MainTab.MEMBERSHIP,
        onOpenMembership = { selectedTab = MainTab.MEMBERSHIP },
    )

    AetherBackground {
    Scaffold(
        modifier = Modifier.nestedScroll(navBarScrollConnection),
        containerColor = Color.Transparent,
        bottomBar = {
            // At full hide, emit no bottom-bar content. Scaffold then releases
            // the reserved height too, so there is no empty strip behind the pill.
            if (navBarVisible || navBarHeightPx == 0f) {
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
            }
        },
    ) { padding ->
        // Transisi antar tab: fade + geser tipis searah navigasi. Sengaja TANPA
        // scale — scale memaksa seluruh layar di-raster ulang tiap frame dan
        // terasa berat di perangkat mid-range.
        AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            targetState = selectedTab,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(80)) togetherWith fadeOut(tween(80))
                } else {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> (fullWidth * 0.025f * dir).roundToInt() },
                    ) + fadeIn(tween(125))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> (-fullWidth * 0.02f * dir).roundToInt() },
                        ) + fadeOut(tween(90)))
                }
            },
            label = "mainTabContent",
        ) { tab ->
            when (tab) {
                MainTab.TWEAK -> TweakScreen(
                    modifier = Modifier,
                    contentPadding = padding,
                    viewModel = tweakViewModel,
                    dashboardRequest = dashboardRequest,
                    onlineUserCount = onlineUserCount,
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
