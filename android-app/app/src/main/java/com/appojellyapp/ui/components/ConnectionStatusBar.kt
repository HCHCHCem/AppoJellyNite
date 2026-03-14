package com.appojellyapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState {
    LAN,
    TAILSCALE,
    DISCONNECTED,
}

@Composable
fun ConnectionStatusBar(
    connectionState: StateFlow<ConnectionState>,
    modifier: Modifier = Modifier,
) {
    val state by connectionState.collectAsState()

    val (color, label) = when (state) {
        ConnectionState.LAN -> Color(0xFF4CAF50) to "Connected (LAN)"
        ConnectionState.TAILSCALE -> Color(0xFFFF9800) to "Connected (Tailscale)"
        ConnectionState.DISCONNECTED -> Color(0xFFF44336) to "Disconnected"
    }

    val animatedColor by animateColorAsState(targetValue = color, label = "statusColor")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(animatedColor),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
