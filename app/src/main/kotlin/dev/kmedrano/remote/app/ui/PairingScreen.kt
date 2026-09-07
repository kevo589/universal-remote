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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.kmedrano.remote.core.ProtocolType

/**
 * Placeholder pairing flow. Once a protocol module registers a real [dev.kmedrano.remote.core.RemoteClientFactory]
 * (see the build-order milestones), this branches on the protocol's [dev.kmedrano.remote.core.PairingInputKind]
 * instead of always showing "not implemented yet".
 */
@Composable
fun PairingScreen(
    protocol: ProtocolType,
    isSupported: Boolean,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (isSupported) {
            CircularProgressIndicator()
            Text("Pairing…", style = MaterialTheme.typography.titleMedium)
        } else {
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
        }
        Button(onClick = onDone) {
            Text("Done")
        }
    }
}
