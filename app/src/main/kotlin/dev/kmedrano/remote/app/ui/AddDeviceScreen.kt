package dev.kmedrano.remote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.kmedrano.remote.core.ProtocolType

private fun ProtocolType.label(): String = when (this) {
    ProtocolType.APPLE_TV -> "Apple TV"
    ProtocolType.SAMSUNG_TIZEN -> "Samsung TV"
    ProtocolType.FIRE_TV_ADB -> "Fire TV"
    ProtocolType.ANDROID_TV_REMOTE_V2 -> "Chromecast with Google TV"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    onBack: () -> Unit,
    onDeviceChosen: (protocol: ProtocolType, displayName: String, host: String) -> Unit,
) {
    var selectedProtocol by remember { mutableStateOf(ProtocolType.SAMSUNG_TIZEN) }
    var displayName by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add device") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Device type", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
                ProtocolType.entries.forEach { protocol ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = protocol == selectedProtocol,
                                onClick = { selectedProtocol = protocol },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = protocol == selectedProtocol, onClick = null)
                            Text(protocol.label(), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Name (e.g. Living Room)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("IP address") },
                placeholder = { Text("192.168.1.42") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Button(
                onClick = {
                    val name = displayName.ifBlank { selectedProtocol.label() }
                    onDeviceChosen(selectedProtocol, name, host)
                },
                enabled = host.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}
