package com.freeobd.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.freeobd.app.domain.model.ConnectionState
import com.freeobd.app.presentation.theme.*

/**
 * Bluetooth connection status indicator with animated dot.
 *
 * Shows a colored dot and optional label representing the current
 * connection state.
 */
@Composable
fun StatusIndicator(
    state: ConnectionState,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val color by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.CONNECTED -> BtConnected
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> BtConnecting
            ConnectionState.SCANNING -> BtScanning
            else -> BtDisconnected
        },
        animationSpec = tween(durationMillis = 300)
    )

    // Pulsing animation for scanning/connecting states
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        )
    )

    val shouldPulse = state == ConnectionState.SCANNING ||
        state == ConnectionState.CONNECTING ||
        state == ConnectionState.RECONNECTING

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .then(
                    if (shouldPulse) Modifier.alpha(alpha)
                    else Modifier
                )
                .clip(CircleShape)
                .background(color)
        )

        if (showLabel) {
            Text(
                text = when (state) {
                    ConnectionState.DISCONNECTED -> "Disconnected"
                    ConnectionState.SCANNING -> "Scanning"
                    ConnectionState.CONNECTING -> "Connecting"
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.DISCONNECTING -> "Disconnecting"
                    ConnectionState.RECONNECTING -> "Reconnecting"
                },
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}
