package com.appojellyapp.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.core.model.MediaType
import com.appojellyapp.core.network.NetworkHelper
import com.appojellyapp.feature.home.ui.HomeScreen
import com.appojellyapp.feature.home.ui.SearchScreen
import com.appojellyapp.feature.jellyfin.player.PlayerScreen
import com.appojellyapp.feature.jellyfin.ui.MediaBrowseScreen
import com.appojellyapp.feature.jellyfin.ui.MediaDetailScreen
import com.appojellyapp.feature.playnite.ui.GameBrowseScreen
import com.appojellyapp.feature.playnite.ui.GameDetailScreen
import com.appojellyapp.feature.streaming.ui.StreamActivity
import com.appojellyapp.ui.components.ConnectionStatusBar
import com.appojellyapp.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Games : Screen("games", "Games", Icons.Default.Gamepad)
    data object Media : Screen("media", "Media", Icons.Default.VideoLibrary)
    data object Search : Screen("search", "Search", Icons.Default.Search)
}

val bottomNavItems = listOf(Screen.Home, Screen.Games, Screen.Media, Screen.Search)

@Composable
fun AppNavHost(networkHelper: NetworkHelper? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val launchStream: (ContentItem.PcGame) -> Unit = { game ->
        val intent = Intent(context, StreamActivity::class.java).apply {
            putExtra(StreamActivity.EXTRA_APOLLO_APP_ID, game.apolloAppId)
            putExtra(StreamActivity.EXTRA_GAME_NAME, game.title)
        }
        context.startActivity(intent)
    }

    val onContentClick: (ContentItem) -> Unit = { item ->
        when (item) {
            is ContentItem.Media -> {
                navController.navigate("media_detail/${item.jellyfinItemId}")
            }
            is ContentItem.PcGame -> {
                navController.navigate("game_detail/${item.id}")
            }
            is ContentItem.LocalRom -> {
                // Phase 4: Launch emulation
            }
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                networkHelper?.let { helper ->
                    ConnectionStatusBar(connectionState = helper.connectionState)
                }
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onContentClick = onContentClick)
            }

            composable(Screen.Games.route) {
                GameBrowseScreen(
                    onGameClick = { game ->
                        navController.navigate("game_detail/${game.id}")
                    },
                )
            }

            composable(Screen.Media.route) {
                MediaBrowseScreen(
                    onItemClick = { media ->
                        navController.navigate("media_detail/${media.jellyfinItemId}")
                    },
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(onContentClick = onContentClick)
            }

            composable("game_detail/{gameId}") {
                GameDetailScreen(
                    onBack = { navController.popBackStack() },
                    onStream = { game -> launchStream(game) },
                )
            }

            composable("media_detail/{itemId}") { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                MediaDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onPlay = { id ->
                        navController.navigate("player/$id")
                    },
                )
            }

            composable("player/{itemId}") { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                PlayerScreen(itemId = itemId)
            }

            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
