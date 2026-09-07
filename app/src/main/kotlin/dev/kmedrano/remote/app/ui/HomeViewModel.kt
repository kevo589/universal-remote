package dev.kmedrano.remote.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kmedrano.remote.app.di.AppContainer
import dev.kmedrano.remote.core.PairingInput
import dev.kmedrano.remote.core.PairingResult
import dev.kmedrano.remote.core.ProtocolType
import dev.kmedrano.remote.core.RemoteClient
import dev.kmedrano.remote.core.RemoteCommand
import dev.kmedrano.remote.core.TvDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val devices: StateFlow<List<TvDevice>> = container.devices

    private val _syncModeEnabled = MutableStateFlow(false)
    val syncModeEnabled: StateFlow<Boolean> = _syncModeEnabled.asStateFlow()

    fun toggleSyncMode() {
        _syncModeEnabled.update { !it }
    }

    fun supportedProtocols(): Set<ProtocolType> = container.supportedProtocols()

    /** The live client for a device, once instantiated — null until then (briefly, right after adding). */
    fun clientFlow(deviceId: String): StateFlow<RemoteClient?> =
        container.activeClients
            .map { it[deviceId] }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), container.clientFor(deviceId))

    /** Adds a device, generating its id. Returns the id immediately so the caller can navigate to pairing. */
    fun addDevice(protocol: ProtocolType, displayName: String, host: String): String {
        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            protocol = protocol,
            displayName = displayName,
            host = host,
        )
        viewModelScope.launch { container.addDevice(device) }
        return device.id
    }

    fun removeDevice(deviceId: String) {
        container.removeDevice(deviceId)
    }

    fun sendCommand(deviceId: String, command: RemoteCommand) {
        val client = container.clientFor(deviceId) ?: return
        viewModelScope.launch { client.sendCommand(command) }
    }

    fun broadcastSyncCommand(command: RemoteCommand) {
        viewModelScope.launch { container.syncModeController.broadcast(command) }
    }

    suspend fun startPairing(client: RemoteClient): PairingResult = client.startPairing()

    suspend fun submitPairingInput(client: RemoteClient, input: PairingInput): PairingResult =
        client.submitPairingInput(input)
}
