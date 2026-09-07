package dev.kmedrano.remote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kmedrano.remote.core.RemoteCommand
import dev.kmedrano.remote.core.TvDevice

/**
 * The D-pad/nav/volume/power remote for a single device. Buttons are always wired up — when
 * no protocol client is registered for [device] yet (v1 rollout hasn't reached it), presses
 * are accepted but silently do nothing, and the banner below explains why.
 */
@Composable
fun DeviceRemoteScreen(
    device: TvDevice,
    isSupported: Boolean,
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!isSupported) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    text = "Support for ${device.protocol.name.replace('_', ' ')} is coming in a future update. " +
                        "Buttons below are wired up but won't do anything yet.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        // D-pad
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectionalButton(Icons.Filled.KeyboardArrowUp, "Up") { onCommand(RemoteCommand.DpadUp) }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                DirectionalButton(Icons.Filled.KeyboardArrowLeft, "Left") { onCommand(RemoteCommand.DpadLeft) }
                Button(onClick = { onCommand(RemoteCommand.Select) }, modifier = Modifier.size(64.dp)) {
                    Text("OK")
                }
                DirectionalButton(Icons.Filled.KeyboardArrowRight, "Right") { onCommand(RemoteCommand.DpadRight) }
            }
            DirectionalButton(Icons.Filled.KeyboardArrowDown, "Down") { onCommand(RemoteCommand.DpadDown) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onCommand(RemoteCommand.Back) }) { Text("Back") }
            OutlinedButton(onClick = { onCommand(RemoteCommand.Home) }) { Text("Home") }
            OutlinedButton(onClick = { onCommand(RemoteCommand.Menu) }) { Text("Menu") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onCommand(RemoteCommand.VolumeDown) }) {
                Icon(Icons.Filled.VolumeDown, contentDescription = "Volume down")
            }
            OutlinedButton(onClick = { onCommand(RemoteCommand.MuteToggle) }) {
                Icon(Icons.Filled.VolumeOff, contentDescription = "Mute")
            }
            OutlinedButton(onClick = { onCommand(RemoteCommand.VolumeUp) }) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Volume up")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onCommand(RemoteCommand.PlayPause) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
            }
            OutlinedButton(onClick = { onCommand(RemoteCommand.Power(on = false)) }) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Power")
            }
        }
    }
}

@Composable
private fun DirectionalButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(56.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}
