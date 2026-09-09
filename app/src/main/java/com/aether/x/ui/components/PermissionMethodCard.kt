package com.aether.x.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.aether.x.ui.theme.AetherCyanContainer
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.SurfaceRaised
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextSecondary
import com.aether.x.ui.theme.TextPrimary

@Composable
fun PermissionMethodCard(
    title: String,
    description: String,
    statusText: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    lockedHint: String? = null,
    isRequesting: Boolean = false,
    requestingLabel: String? = null,
) {
    val contentAlpha = if (locked) 0.5f else 1f
    val shape = RoundedCornerShape(26.dp)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (granted) AetherCyanContainer.copy(alpha = 0.42f) else SurfaceRaised.copy(alpha = 0.92f),
        ),
        border = BorderStroke(
            1.dp,
            if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.48f) else StrokeSubtle.copy(alpha = 0.82f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp).alpha(contentAlpha),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (granted) MaterialTheme.colorScheme.primary else TextMuted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                if (granted) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            when {
                locked -> {
                    Text(
                        text = lockedHint.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                granted -> Unit
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusPill(
                            text = statusText,
                            containerColor = SurfaceRaised,
                            contentColor = TextSecondary,
                            dotColor = null,
                        )
                        Button(
                            onClick = onAction,
                            enabled = !isRequesting,
                            shape = RoundedCornerShape(17.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            if (isRequesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Text(
                                    text = requestingLabel ?: actionLabel,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            } else {
                                Text(actionLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}
