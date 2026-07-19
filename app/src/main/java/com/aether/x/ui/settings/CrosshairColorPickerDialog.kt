package com.aether.x.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.R

@Composable
fun CrosshairColorPickerDialog(
    initialColorArgb: Long,
    onDismiss: () -> Unit,
    onColorConfirmed: (Long) -> Unit,
) {
    val initialHsv = remember(initialColorArgb) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColorArgb.toInt(), hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor = remember(hue, saturation, value) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crosshair_custom_color_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SaturationValuePanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSaturationValueChange = { s, v -> saturation = s; value = v },
                )
                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Text(
                        text = "#%06X".format(currentColor.toArgb() and 0xFFFFFF),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {

                val argb = 0xFF000000L or (currentColor.toArgb().toLong() and 0xFFFFFFL)
                onColorConfirmed(argb)
            }) {
                Text(stringResource(R.string.crosshair_custom_color_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.crosshair_custom_color_cancel))
            }
        },
    )
}

@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (saturation: Float, value: Float) -> Unit,
) {
    val panelHeight = 220.dp
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(hueColor)
            .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInput(hue) {
                fun updateFromOffset(offset: Offset) {
                    val s = (offset.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                    onSaturationValueChange(s, v)
                }

                detectDragGestures(
                    onDragStart = { offset -> updateFromOffset(offset) },
                    onDrag = { change, _ -> updateFromOffset(change.position) },
                )
            },
    ) {

        val indicatorColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
        val offsetX = maxWidth * saturation
        val offsetY = maxHeight * (1f - value)
        Box(
            modifier = Modifier
                .offset(x = offsetX - 10.dp, y = offsetY - 10.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(indicatorColor)
                .border(2.dp, Color.White, CircleShape),
        )
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
) {
    val hueColors = remember {
        (0..360 step 30).map { deg ->
            Color(android.graphics.Color.HSVToColor(floatArrayOf(deg.toFloat(), 1f, 1f)))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.horizontalGradient(hueColors))
            .pointerInput(Unit) {
                fun updateFromOffset(x: Float) {
                    val h = (x / size.width).coerceIn(0f, 1f) * 360f
                    onHueChange(h)
                }
                detectDragGestures(
                    onDragStart = { offset -> updateFromOffset(offset.x) },
                    onDrag = { change, _ -> updateFromOffset(change.position.x) },
                )
            },
    )
}
