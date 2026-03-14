package com.appojellyapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.appojellyapp.core.network.NetworkHelper
import com.appojellyapp.core.util.isTvDevice
import com.appojellyapp.ui.navigation.AppNavHost
import com.appojellyapp.ui.theme.AppoJellyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var networkHelper: NetworkHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Redirect to TV interface if running on a TV device
        if (isTvDevice(this)) {
            startActivity(Intent(this, TvActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            AppoJellyTheme {
                AppNavHost(networkHelper = networkHelper)
            }
        }
    }
}
