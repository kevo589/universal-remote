package dev.kmedrano.remote.app.di

import dev.kmedrano.remote.core.ProtocolType
import dev.kmedrano.remote.core.RemoteClient
import dev.kmedrano.remote.core.RemoteClientFactory
import dev.kmedrano.remote.core.SyncModeController
import dev.kmedrano.remote.core.TvDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Hand-written composition root. Four protocol clients don't justify pulling in Hilt/Dagger —
 * this single object owns everything the app needs to wire together.
 *
 * [clientFactories] starts empty and gains one entry per [ProtocolType] as each protocol
 * module is implemented (Samsung, then Fire TV, then Android TV Remote v2, then Apple TV —
 * see the project plan for why that order). Device persistence (Room) also lands alongside
 * the first real protocol implementation; until then the device list is in-memory only.
 */
class AppContainer {

    private val clientFactories: MutableMap<ProtocolType, RemoteClientFactory> = mutableMapOf()

    private val activeClients: MutableMap<String, RemoteClient> = mutableMapOf()

    private val _devices = MutableStateFlow<List<TvDevice>>(emptyList())
    val devices: StateFlow<List<TvDevice>> = _devices.asStateFlow()

    val syncModeController = SyncModeController { activeClients.values }

    /** Protocols that currently have a working client implementation registered. */
    fun supportedProtocols(): Set<ProtocolType> = clientFactories.keys

    fun clientFor(deviceId: String): RemoteClient? = activeClients[deviceId]

    fun registerFactory(protocol: ProtocolType, factory: RemoteClientFactory) {
        clientFactories[protocol] = factory
    }

    fun addDevice(device: TvDevice) {
        _devices.update { it + device }
        clientFactories[device.protocol]?.let { factory ->
            activeClients[device.id] = factory.create(device)
        }
    }

    fun removeDevice(deviceId: String) {
        activeClients.remove(deviceId)
        _devices.update { list -> list.filterNot { it.id == deviceId } }
    }
}
