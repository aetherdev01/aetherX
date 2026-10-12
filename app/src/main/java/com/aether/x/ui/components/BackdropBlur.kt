package com.aether.x.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

/** Shared backdrop capture state for in-app surfaces and dialogs. */
val LocalAetherHazeState = staticCompositionLocalOf { HazeState() }
