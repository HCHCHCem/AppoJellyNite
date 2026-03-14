package com.appojellyapp.feature.streaming.ui

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.appojellyapp.ui.theme.AppoJellyTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fullscreen activity for game streaming.
 * Keeps the screen on, hides system UI for immersive experience,
 * and forwards gamepad input to the streaming connection.
 */
@AndroidEntryPoint
class StreamActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during streaming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Enter immersive fullscreen mode
        hideSystemUI()

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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Forward analog stick and trigger motion events to the stream
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Let the stream screen handle gamepad buttons
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        const val EXTRA_APOLLO_APP_ID = "apollo_app_id"
        const val EXTRA_GAME_NAME = "game_name"
    }
}
