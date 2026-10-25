package com.aether.x.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.LocalAetherStyle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/** Shared backdrop recorded from AetherX's aurora background. */
val LocalAetherLiquidBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Returns the recorded app background when rendered inside [AetherBackground].
 * A canvas backdrop is kept as a safe fallback for previews, onboarding and
 * independently composed routes that do not have the shared background yet.
 */
@Composable
fun rememberAetherLiquidBackdrop(): Backdrop {
    val shared = LocalAetherLiquidBackdrop.current
    val palette = LocalAetherPalette.current
    // A Dialog is hosted in a separate window. Never sample a GraphicsLayer from
    // the activity window there; use the palette canvas fallback to avoid cross-window crashes.
    val isSeparateWindow = LocalView.current.parent is DialogWindowProvider
    val fallback = rememberCanvasBackdrop {
        drawRect(palette.bg)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    palette.accent.copy(alpha = 0.15f),
                    palette.accentSoft.copy(alpha = 0.055f),
                    Color.Transparent,
                ),
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
            ),
        )
    }
    return if (isSeparateWindow) fallback else (shared ?: fallback)
}

private fun DrawScope.drawLiquidSurface(color: Color, glassLevel: Float) {
    drawRect(color.copy(alpha = color.alpha * (0.22f + 0.58f * glassLevel.coerceIn(0f, 1f))))
}

/** AndroidLiquidGlass/Backdrop based capsule switch. Settings remain driven by AetherStyle. */
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalAetherPalette.current
    val style = LocalAetherStyle.current
    val backdrop = rememberAetherLiquidBackdrop()
    val trackBackdrop = rememberLayerBackdrop()
    val thumbBackdrop = rememberCombinedBackdrop(
        backdrop,
        rememberBackdrop(trackBackdrop) { drawBackdrop -> drawBackdrop() },
    )
    val fraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 650f),
        label = "liquid-toggle-position",
    )
    val track = lerp(
        if (palette.isDark) Color(0xFF626268) else Color(0xFFC9C9D0),
        palette.accent,
        fraction,
    )
    val trackAlpha = (0.24f + 0.42f * style.glassLevel.coerceIn(0f, 1f)) * if (palette.isDark) 0.90f else 1f
    Box(
        modifier = modifier
            .size(width = 58.dp, height = 34.dp)
            .clip(RoundedCornerShape(50))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .alpha(if (enabled) 1f else 0.55f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop)
                .clip(RoundedCornerShape(50))
                .background(track.copy(alpha = trackAlpha))
        )
        Box(
            modifier = Modifier
                .offset(x = (3f + 24f * fraction).dp)
                .size(27.dp)
                .drawBackdrop(
                    backdrop = thumbBackdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        vibrancy()
                        if (style.blur > 0.01f) {
                            blur((blurRadiusFor(style.blur) * 0.34f).dp.toPx())
                        }
                        val level = style.glassLevel.coerceIn(0f, 1f)
                        lens(
                            (4f + 9f * level).dp.toPx(),
                            (7f + 15f * level).dp.toPx(),
                            chromaticAberration = level > 0.55f,
                        )
                    },
                    highlight = { Highlight.Ambient.copy(alpha = 0.18f + 0.32f * style.glassLevel) },
                    onDrawSurface = {
                        drawLiquidSurface(Color.White, style.glassLevel)
                    },
                )
                .border(
                    width = 0.75.dp,
                    color = Color.White.copy(alpha = if (palette.isDark) 0.30f else 0.82f),
                    shape = RoundedCornerShape(50),
                ),
        )
    }
}

/** Liquid glass button adapted from the repository's sample component. */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    isInteractive: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    LiquidActionButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        colors = colors,
        border = border,
        isInteractive = isInteractive,
        outlined = false,
        content = content,
    )
}

/** Outlined variant keeps the caller's color/border choices while using the same backdrop engine. */
@Composable
fun LiquidOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    isInteractive: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    LiquidActionButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        colors = colors,
        border = border,
        isInteractive = isInteractive,
        outlined = true,
        content = content,
    )
}

@Composable
private fun LiquidActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    contentPadding: PaddingValues,
    colors: ButtonColors,
    border: BorderStroke?,
    isInteractive: Boolean,
    outlined: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val style = LocalAetherStyle.current
    val palette = LocalAetherPalette.current
    val backdrop = rememberAetherLiquidBackdrop()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed && isInteractive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "liquid-button-press",
    )
    val containerColor = if (enabled) colors.containerColor else colors.disabledContainerColor
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    val resolvedSurface = if (outlined) {
        palette.surface.copy(alpha = 0.06f + 0.10f * style.glassLevel)
    } else {
        containerColor
    }
    val resolvedBorder = border ?: if (outlined) {
        BorderStroke(1.dp, palette.stroke.copy(alpha = 0.78f))
    } else null

    Row(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    if (style.blur > 0.01f) {
                        blur((blurRadiusFor(style.blur) * 0.32f).dp.toPx())
                    }
                    val level = style.glassLevel.coerceIn(0f, 1f)
                    lens(
                        (5f + 9f * level).dp.toPx(),
                        (8f + 18f * level).dp.toPx(),
                        chromaticAberration = level > 0.62f,
                    )
                },
                highlight = {
                    Highlight.Ambient.copy(alpha = pressProgress * (0.20f + 0.35f * style.glassLevel))
                },
                layerBlock = if (isInteractive) {
                    {
                        val scale = 1f + 0.025f * pressProgress
                        scaleX = scale
                        scaleY = scale
                    }
                } else null,
                onDrawSurface = {
                    if (outlined) {
                        drawRect(resolvedSurface.copy(alpha = 0.20f + 0.15f * style.glassLevel))
                    } else {
                        // Keep filled-action contrast usable at the lowest glass level.
                        drawRect(
                            resolvedSurface.copy(
                                alpha = resolvedSurface.alpha * (0.52f + 0.32f * style.glassLevel.coerceIn(0f, 1f)),
                            ),
                        )
                    }
                },
            )
            .then(if (resolvedBorder != null) Modifier.border(resolvedBorder, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = if (isInteractive) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .height(48.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
