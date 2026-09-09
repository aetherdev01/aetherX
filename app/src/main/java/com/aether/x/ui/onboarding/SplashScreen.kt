package com.aether.x.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.DeviceId
import com.aether.x.data.UserIdRepository
import com.aether.x.ui.theme.AetherCyan
import com.aether.x.ui.theme.AetherCyanContainer
import com.aether.x.ui.theme.BgVoid
import com.aether.x.ui.theme.SurfaceCard
import com.aether.x.ui.theme.SurfaceRaised
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var statusLabel by remember { mutableStateOf(context.getString(R.string.splash_status_checking)) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        statusLabel = context.getString(R.string.splash_status_checking)
        PrivilegeManager.refreshAll()
        PrivilegeManager.refreshSupportingPermissions(context)

        statusLabel = context.getString(R.string.splash_status_database)
        val preferences = AetherXPreferences(context)
        val deviceId = DeviceId.read(context)
        val userIdRepository = UserIdRepository(preferences, deviceId)
        withTimeoutOrNull(8_000L) { userIdRepository.resolveUserId() }

        statusLabel = context.getString(R.string.splash_status_ready)
        delay(450)
        finished = true
    }

    LaunchedEffect(finished) {
        if (finished) onDone()
    }

    SplashScreenContent(statusLabel)
}

@Composable
private fun SplashScreenContent(statusLabel: String) {
    val logoScale = remember { Animatable(0.72f) }
    val logoAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val haloScale = remember { Animatable(0.82f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(500, easing = EaseOutCubic))
        logoScale.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 180f))
        haloScale.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 120f))
        delay(130)
        contentAlpha.animateTo(1f, tween(420, easing = EaseOutCubic))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(AetherCyanContainer.copy(alpha = 0.30f), BgVoid, BgVoid),
                    radius = 520f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .scale(haloScale.value)
                .alpha(logoAlpha.value * 0.55f)
                .background(AetherCyan.copy(alpha = 0.08f), CircleShape),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(34.dp),
                color = SurfaceRaised.copy(alpha = 0.86f),
                border = androidx.compose.foundation.BorderStroke(1.dp, StrokeSubtle.copy(alpha = 0.9f)),
                shadowElevation = 18.dp,
                modifier = Modifier.size(128.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_aetherx_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(88.dp).scale(logoScale.value).alpha(logoAlpha.value),
                    )
                }
            }

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.padding(top = 28.dp).alpha(contentAlpha.value),
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 7.dp).alpha(contentAlpha.value),
            )

            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp).alpha(contentAlpha.value),
                color = AetherCyan,
                strokeWidth = 2.4.dp,
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceCard.copy(alpha = 0.72f),
                border = androidx.compose.foundation.BorderStroke(1.dp, StrokeSubtle.copy(alpha = 0.65f)),
                modifier = Modifier.padding(top = 24.dp).alpha(contentAlpha.value),
            ) {
                Text(
                    text = "Initializing AetherX",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}
