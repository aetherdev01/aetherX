package com.aether.x.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

val LocalHapticEnabled = compositionLocalOf { true }

fun HapticFeedback.performIfEnabled(enabled: Boolean, type: HapticFeedbackType) {
    if (enabled) {
        performHapticFeedback(type)
    }
}
