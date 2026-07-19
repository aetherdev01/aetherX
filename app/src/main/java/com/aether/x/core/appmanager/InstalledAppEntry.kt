package com.aether.x.core.appmanager

import androidx.compose.ui.graphics.ImageBitmap

enum class AppOrigin {

    THIRD_PARTY,

    KNOWN_BLOATWARE,
}

data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val origin: AppOrigin,

    val isFrozen: Boolean,
)
