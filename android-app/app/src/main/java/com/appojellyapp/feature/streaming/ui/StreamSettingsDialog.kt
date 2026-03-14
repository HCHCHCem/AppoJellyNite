package com.appojellyapp.feature.streaming.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appojellyapp.feature.streaming.moonlight.StreamConfig
import com.appojellyapp.feature.streaming.moonlight.VideoCodec

data class ResolutionOption(val label: String, val width: Int, val height: Int)

private val resolutions = listOf(
    ResolutionOption("720p", 1280, 720),
    ResolutionOption("1080p", 1920, 1080),
    ResolutionOption("4K", 3840, 2160),
)

@Composable
fun StreamSettingsDialog(
    currentConfig: StreamConfig,
    onDismiss: () -> Unit,
    onConfirm: (StreamConfig) -> Unit,
) {
    var selectedResolution by remember {
        mutableStateOf(resolutions.find { it.width == currentConfig.width && it.height == currentConfig.height } ?: resolutions[1])
    }
    var selectedFps by remember { mutableStateOf(currentConfig.fps) }
    var bitrate by remember { mutableFloatStateOf(currentConfig.bitrate.toFloat()) }
    var selectedCodec by remember { mutableStateOf(currentConfig.codec) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stream Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Resolution", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    resolutions.forEach { res ->
                        FilterChip(
                            selected = selectedResolution == res,
                            onClick = { selectedResolution = res },
                            label = { Text(res.label) },
                        )
                    }
                }

                Text("FPS", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60).forEach { fps ->
                        FilterChip(
                            selected = selectedFps == fps,
                            onClick = { selectedFps = fps },
                            label = { Text("${fps}fps") },
                        )
                    }
                }

                Text("Bitrate: ${bitrate.toInt()} kbps", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = bitrate,
                    onValueChange = { bitrate = it },
                    valueRange = 1000f..50000f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Codec", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoCodec.entries.forEach { codec ->
                        FilterChip(
                            selected = selectedCodec == codec,
                            onClick = { selectedCodec = codec },
                            label = { Text(codec.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(StreamConfig(
                    width = selectedResolution.width,
                    height = selectedResolution.height,
                    fps = selectedFps,
                    bitrate = bitrate.toInt(),
                    codec = selectedCodec,
                ))
            }) {
                Text("Start Stream")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
