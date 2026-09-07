package dev.kmedrano.remote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.kmedrano.remote.core.ConnectionState
import kotlinx.coroutines.delay

/**
 * Drives pairing for the device at [deviceId]. Waits for [HomeViewModel.clientFlow] to resolve
 * (near-instant — the client is created right after the device is added), then, if the
 * protocol is implemented, kicks off [dev.kmedrano.remote.core.RemoteClient.startPairing] and
 * reflects its [ConnectionState] until the device is connected or pairing fails.
 */
@Composable
fun PairingScreen(
    deviceId: String,
    viewModel: HomeViewModel,
    onDone: () -> Unit,
) {
    val clientState by viewModel.clientFlow(deviceId).collectAsState()
    val currentClient = clientState
    val supported = currentClient?.let { it.device.protocol in viewModel.supportedProtocols() } ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        when {
            currentClient == null -> {
                CircularProgressIndicator()
                Text("Setting up…", style = MaterialTheme.typography.titleMedium)
            }

            !supported -> {
                Text(
                    "Support for this device isn't implemented yet",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "The device has been added and will show up as a tab on the home screen, " +
                        "but its remote controls won't work until this protocol is built.",
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onDone) { Text("Done") }
            }

            else -> {
                val connectionState by currentClient.connectionState.collectAsState()

                LaunchedEffect(currentClient) {
                    viewModel.startPairing(currentClient)
                }

                LaunchedEffect(connectionState) {
                    if (connectionState is ConnectionState.Connected) {
                        delay(800)
                        onDone()
                    }
                }

                when (val state = connectionState) {
                    is ConnectionState.Connected -> {
                        Text("Connected!", style = MaterialTheme.typography.titleMedium)
                    }
                    is ConnectionState.AwaitingPairingConfirmation -> {
                        CircularProgressIndicator()
                        Text("Check ${currentClient.device.displayName}", style = MaterialTheme.typography.titleMedium)
                        Text("Approve the connection request on the TV screen.", textAlign = TextAlign.Center)
                    }
                    is ConnectionState.Error -> {
                        Text(
                            "Couldn't connect",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(state.message, textAlign = TextAlign.Center)
                        Button(onClick = onDone) { Text("Back") }
                    }
                    else -> {
                        CircularProgressIndicator()
                        Text("Connecting…", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
