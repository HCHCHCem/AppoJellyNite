package com.appojellyapp.feature.streaming.ui

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.appojellyapp.feature.streaming.moonlight.StreamState
import com.appojellyapp.feature.streaming.moonlight.input.ControllerHandler
import com.appojellyapp.feature.streaming.moonlight.input.GamepadInputMapper
import com.appojellyapp.feature.streaming.moonlight.input.TouchInputMapper

@Composable
fun StreamScreen(
    apolloAppId: String,
    gameName: String,
    onDisconnect: () -> Unit,
    viewModel: StreamViewModel = hiltViewModel(),
) {
    val state by viewModel.streamState.collectAsState()
    val error by viewModel.error.collectAsState()
    val connectionStage by viewModel.connectionStage.collectAsState()
    var showOverlay by remember { mutableStateOf(false) }

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

            StreamState.CONNECTING, StreamState.LAUNCHING_APP, StreamState.STARTING_STREAM -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (state) {
                            StreamState.CONNECTING -> "Connecting..."
                            StreamState.LAUNCHING_APP -> "Launching $gameName..."
                            StreamState.STARTING_STREAM -> "Starting stream..."
                            else -> ""
                        },
                        color = Color.White,
                    )
                    connectionStage?.let { stage ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stage,
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            StreamState.STREAMING -> {
                // Render the stream via SurfaceView
                StreamingSurfaceView(
                    viewModel = viewModel,
                    gameName = gameName,
                    onToggleOverlay = { showOverlay = !showOverlay },
                )

                // Overlay menu (shown on tap or button combo)
                if (showOverlay) {
                    StreamOverlay(
                        gameName = gameName,
                        onDismiss = { showOverlay = false },
                        onDisconnect = {
                            viewModel.stopStream()
                            onDisconnect()
                        },
                    )
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

@Composable
private fun StreamingSurfaceView(
    viewModel: StreamViewModel,
    gameName: String,
    onToggleOverlay: () -> Unit,
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )

                // Set up surface callbacks
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        viewModel.onSurfaceReady(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        // Surface dimensions changed
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        // Surface destroyed — stream will need to reconnect
                    }
                })

                // Handle touch events for mouse input
                setOnTouchListener { view, event ->
                    val connection = viewModel.getConnection()
                    if (connection != null) {
                        // Create a touch mapper on-the-fly (could be cached)
                        // For now, forward touch as mouse events
                        val inputSocket = connection // Controller handler is internal
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                if (event.pointerCount == 3) {
                                    // 3-finger tap toggles overlay
                                    onToggleOverlay()
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }

                // Handle gamepad input
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()

                setOnKeyListener { _, keyCode, event ->
                    // Guide + Select combo opens overlay
                    if (keyCode == KeyEvent.KEYCODE_BUTTON_MODE &&
                        event.action == KeyEvent.ACTION_DOWN
                    ) {
                        onToggleOverlay()
                        return@setOnKeyListener true
                    }

                    val connection = viewModel.getConnection()
                    if (connection != null) {
                        // Forward gamepad buttons
                        when (event.action) {
                            KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP -> {
                                // Map Android key codes to Moonlight button flags
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun StreamOverlay(
    gameName: String,
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .background(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.shapes.large,
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = gameName,
                style = MaterialTheme.typography.titleLarge,
            )

            Text(
                text = "Streaming",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Resume")
                }

                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}
