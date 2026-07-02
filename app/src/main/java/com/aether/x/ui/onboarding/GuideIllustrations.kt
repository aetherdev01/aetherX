package com.aether.x.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aether.x.ui.theme.AccentBlue

/**
 * Ilustrasi halaman panduan — hasil rework total.
 *
 * Versi lama memakai Canvas custom (ring radar berputar, ripple sentuh,
 * dua ring dash berlawanan arah, slider demo bergerak) yang di kanvas hero
 * penuh layar terasa terlalu besar dan berlebihan. Diganti dengan pola yang
 * sama seperti badge ikon di tab Tweak ([com.aether.x.ui.components.TweakSwitch]):
 * satu ikon *filled* statis di dalam lingkaran datar, tanpa animasi looping.
 * Jauh lebih tenang untuk dilihat berulang kali dan konsisten dengan bahasa
 * visual di bagian lain aplikasi.
 */
@Composable
fun GuideIllustration(page: Int, modifier: Modifier = Modifier) {
    val icon = when (page) {
        0 -> Icons.Filled.Bolt
        1 -> Icons.Filled.TouchApp
        2 -> Icons.Filled.Shield
        else -> Icons.Filled.RocketLaunch
    }
    GuideIconBadge(icon = icon, modifier = modifier)
}

@Composable
private fun GuideIconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color.White.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}
