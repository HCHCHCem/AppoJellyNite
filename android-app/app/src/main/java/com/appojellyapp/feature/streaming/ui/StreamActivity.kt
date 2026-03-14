package com.appojellyapp.feature.streaming.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.appojellyapp.ui.theme.AppoJellyTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fullscreen activity for game streaming.
 * Keeps the screen on and hides system UI for an immersive streaming experience.
 */
@AndroidEntryPoint
class StreamActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during streaming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val apolloAppId = intent.getStringExtra(EXTRA_APOLLO_APP_ID) ?: ""
        val gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: ""

        setContent {
            AppoJellyTheme {
                StreamScreen(
                    apolloAppId = apolloAppId,
                    gameName = gameName,
                    onDisconnect = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_APOLLO_APP_ID = "apollo_app_id"
        const val EXTRA_GAME_NAME = "game_name"
    }
}
