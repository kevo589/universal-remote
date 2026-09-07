package dev.kmedrano.remote.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kmedrano.remote.app.di.AppContainer
import dev.kmedrano.remote.core.ProtocolType
import dev.kmedrano.remote.core.RemoteCommand
import dev.kmedrano.remote.core.TvDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Adds a device to the (currently in-memory) list. Persistence lands with the first real protocol client. */
    fun addDevice(protocol: ProtocolType, displayName: String, host: String) {
        container.addDevice(
            TvDevice(
                id = UUID.randomUUID().toString(),
                protocol = protocol,
                displayName = displayName,
                host = host,
            ),
        )
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
}
