package com.aether.x.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.DeviceId
import com.aether.x.data.UserIdRepository
import com.aether.x.ui.theme.AccentBlueSoft
import com.aether.x.ui.theme.BgVoid
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun SplashScreen(
    onDone: () -> Unit,
) {
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
        withTimeoutOrNull(8_000L) {
            userIdRepository.resolveUserId()
        }

        statusLabel = context.getString(R.string.splash_status_ready)
        delay(350)
        finished = true
    }

    LaunchedEffect(finished) {
        if (finished) onDone()
    }

    SplashScreenContent(statusLabel = statusLabel)
}

@Composable
private fun SplashScreenContent(statusLabel: String) {
    val logoScale = remember { Animatable(0.6f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = EaseOutBack),
        )
    }
    LaunchedEffect(Unit) {
        logoAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 450))
    }
    LaunchedEffect(Unit) {
        delay(250)
        textAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 400))
    }

    Scaffold(containerColor = BgVoid) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_aetherx_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
            )

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .alpha(textAlpha.value),
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .alpha(textAlpha.value),
            )

            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 28.dp)
                    .size(28.dp)
                    .alpha(textAlpha.value),
                color = AccentBlueSoft,
                strokeWidth = 2.5.dp,
            )
        }
    }
}
