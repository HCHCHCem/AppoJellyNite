package com.appojellyapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.serverConfig.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Jellyfin Settings
            Text(
                text = "Jellyfin Server",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            var jellyfinUrl by remember {
                mutableStateOf(config.jellyfin?.serverUrl ?: "")
            }
            var jellyfinUser by remember {
                mutableStateOf(config.jellyfin?.username ?: "")
            }
            var jellyfinPass by remember {
                mutableStateOf(config.jellyfin?.password ?: "")
            }

            OutlinedTextField(
                value = jellyfinUrl,
                onValueChange = { jellyfinUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.100:8096") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = jellyfinUser,
                onValueChange = { jellyfinUser = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = jellyfinPass,
                onValueChange = { jellyfinPass = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                onClick = {
                    viewModel.saveJellyfin(jellyfinUrl, jellyfinUser, jellyfinPass)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Save & Test Connection")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Playnite Web Settings
            Text(
                text = "Playnite Web",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            var playniteUrl by remember {
                mutableStateOf(config.playniteWeb?.serverUrl ?: "")
            }

            OutlinedTextField(
                value = playniteUrl,
                onValueChange = { playniteUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.100:3000") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { viewModel.savePlayniteWeb(playniteUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Save & Test Connection")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Apollo Settings
            Text(
                text = "Apollo Streaming",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            var apolloLanIp by remember {
                mutableStateOf(config.apollo?.lanIp ?: "")
            }
            var apolloTailscaleIp by remember {
                mutableStateOf(config.apollo?.tailscaleIp ?: "")
            }

            OutlinedTextField(
                value = apolloLanIp,
                onValueChange = { apolloLanIp = it },
                label = { Text("LAN IP Address") },
                placeholder = { Text("192.168.1.100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = apolloTailscaleIp,
                onValueChange = { apolloTailscaleIp = it },
                label = { Text("Tailscale IP (optional)") },
                placeholder = { Text("100.x.y.z") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = {
                    viewModel.saveApollo(
                        apolloLanIp,
                        apolloTailscaleIp.ifBlank { null },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Save")
            }
        }
    }
}
