package com.appojellyapp.feature.streaming.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appojellyapp.feature.streaming.moonlight.StreamState

@Composable
fun StreamScreen(
    apolloAppId: String,
    gameName: String,
    onDisconnect: () -> Unit,
    viewModel: StreamViewModel = hiltViewModel(),
) {
    val state by viewModel.streamState.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(apolloAppId) {
        viewModel.startStream(apolloAppId, gameName)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopStream()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            StreamState.IDLE -> {
                // Nothing to show
            }

            StreamState.CONNECTING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Connecting...", color = Color.White)
                }
            }

            StreamState.LAUNCHING_APP -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Launching $gameName...", color = Color.White)
                }
            }

            StreamState.STREAMING -> {
                // In a full implementation, this would be a SurfaceView
                // rendering the decoded video frames from moonlight-common-c.
                // For now, we show a placeholder.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Streaming: $gameName\n(SurfaceView placeholder — requires moonlight-common-c JNI bridge)",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(32.dp),
                    )

                    Button(
                        onClick = {
                            viewModel.stopStream()
                            onDisconnect()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(32.dp),
                    ) {
                        Text("Disconnect")
                    }
                }
            }

            StreamState.DISCONNECTING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Disconnecting...", color = Color.White)
                }
            }

            StreamState.ERROR -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = error ?: "An error occurred",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onDisconnect) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}
