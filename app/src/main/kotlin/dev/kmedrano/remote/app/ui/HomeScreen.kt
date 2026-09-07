package dev.kmedrano.remote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.kmedrano.remote.core.RemoteCommand
import dev.kmedrano.remote.core.TvDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddDeviceClick: () -> Unit,
) {
    val devices by viewModel.devices.collectAsState()
    val syncModeEnabled by viewModel.syncModeEnabled.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val supported = remember(devices) { viewModel.supportedProtocols() }

    val safeTab = if (devices.isEmpty()) 0 else selectedTab.coerceIn(0, devices.lastIndex)
    val activeDevice: TvDevice? = devices.getOrNull(safeTab)

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.commandErrors.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Universal Remote") },
                actions = {
                    if (activeDevice != null) {
                        IconButton(
                            onClick = {
                                viewModel.removeDevice(activeDevice.id)
                                selectedTab = 0
                            },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove ${activeDevice.displayName}")
                        }
                    }
                    FilterChip(
                        selected = syncModeEnabled,
                        onClick = { viewModel.toggleSyncMode() },
                        label = { Text("Sync mode") },
                        leadingIcon = { Icon(Icons.Filled.Sync, contentDescription = null) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDeviceClick) {
                Icon(Icons.Filled.Add, contentDescription = "Add device")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (syncModeEnabled) {
                SyncModeBar(onCommand = viewModel::broadcastSyncCommand)
            }

            if (activeDevice == null) {
                EmptyState(onAddDeviceClick)
            } else {
                TabRow(selectedTabIndex = safeTab) {
                    devices.forEachIndexed { index, device ->
                        Tab(
                            selected = index == safeTab,
                            onClick = { selectedTab = index },
                            text = { Text(device.displayName) },
                        )
                    }
                }
                DeviceRemoteScreen(
                    device = activeDevice,
                    isSupported = activeDevice.protocol in supported,
                    onCommand = { command -> viewModel.sendCommand(activeDevice.id, command) },
                )
            }
        }
    }
}

@Composable
private fun SyncModeBar(onCommand: (RemoteCommand) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        IconButton(onClick = { onCommand(RemoteCommand.VolumeDown) }) {
            Icon(Icons.Filled.VolumeDown, contentDescription = "Volume down, all devices")
        }
        IconButton(onClick = { onCommand(RemoteCommand.MuteToggle) }) {
            Icon(Icons.Filled.VolumeOff, contentDescription = "Mute, all devices")
        }
        IconButton(onClick = { onCommand(RemoteCommand.VolumeUp) }) {
            Icon(Icons.Filled.VolumeUp, contentDescription = "Volume up, all devices")
        }
        IconButton(onClick = { onCommand(RemoteCommand.Power(on = false)) }) {
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Power, all devices")
        }
    }
}

@Composable
private fun EmptyState(onAddDeviceClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "No devices added yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Tap + to add your Apple TV, Samsung TV, Fire TV, or Chromecast.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
