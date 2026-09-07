package dev.kmedrano.remote.app.di

import android.content.Context
import dev.kmedrano.remote.core.ProtocolType
import dev.kmedrano.remote.core.RemoteClient
import dev.kmedrano.remote.core.RemoteClientFactory
import dev.kmedrano.remote.core.SyncModeController
import dev.kmedrano.remote.core.TvDevice
import dev.kmedrano.remote.core.data.DeviceRepository
import dev.kmedrano.remote.protocol.firetv.FireTvRemoteClientFactory
import dev.kmedrano.remote.protocol.samsung.SamsungTvRemoteClientFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Hand-written composition root. Four protocol clients don't justify pulling in Hilt/Dagger —
 * this single object owns everything the app needs to wire together.
 *
 * Devices and their pairing credentials are persisted via [DeviceRepository] (Room + Keystore
 * encryption). On construction, previously-added devices are loaded and a [RemoteClient] is
 * (re)created for each one whose protocol has a registered factory, then silently reconnected
 * using its stored credential — no UI interaction needed for devices that have paired before.
 */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository = DeviceRepository(context.applicationContext)

    private val clientFactories: Map<ProtocolType, RemoteClientFactory> = mapOf(
        ProtocolType.SAMSUNG_TIZEN to SamsungTvRemoteClientFactory,
        ProtocolType.FIRE_TV_ADB to FireTvRemoteClientFactory,
    )

    private val _devices = MutableStateFlow<List<TvDevice>>(emptyList())
    val devices: StateFlow<List<TvDevice>> = _devices.asStateFlow()

    private val _activeClients = MutableStateFlow<Map<String, RemoteClient>>(emptyMap())
    val activeClients: StateFlow<Map<String, RemoteClient>> = _activeClients.asStateFlow()

    val syncModeController = SyncModeController { activeClients.value.values }

    init {
        appScope.launch {
            repository.devices.collect { deviceList ->
                _devices.value = deviceList
                deviceList.forEach { device -> instantiateClientIfNeeded(device) }

                val currentIds = deviceList.map { it.id }.toSet()
                _activeClients.update { clients -> clients.filterKeys { it in currentIds } }
            }
        }
    }

    /** Protocols that currently have a working client implementation registered. */
    fun supportedProtocols(): Set<ProtocolType> = clientFactories.keys

    fun clientFor(deviceId: String): RemoteClient? = _activeClients.value[deviceId]

    /**
     * Persists the device. The `devices` collector below will pick it up and (if its protocol
     * is supported) create and connect a live client — kept as the *only* place that happens,
     * so there's no race between this call and that reactive path both creating one.
     */
    suspend fun addDevice(device: TvDevice) {
        repository.addDevice(device)
    }

    fun removeDevice(deviceId: String) {
        appScope.launch {
            _activeClients.value[deviceId]?.disconnect()
            repository.removeDevice(deviceId)
        }
    }

    private suspend fun instantiateClientIfNeeded(device: TvDevice) {
        if (_activeClients.value.containsKey(device.id)) return
        val factory = clientFactories[device.protocol] ?: return

        val credential = repository.getCredential(device.id)
        val client = factory.create(device, credential) { updated ->
            appScope.launch { repository.saveCredential(device.id, updated) }
        }
        _activeClients.update { it + (device.id to client) }

        // Only auto-reconnect devices that have paired before (i.e. we have a stored
        // credential). A brand-new device has no credential yet and is left for the explicit
        // startPairing() call PairingScreen makes — calling connect() here too would race a
        // second connection attempt against that one on the same client.
        if (credential != null) {
            appScope.launch { client.connect() }
        }
    }
}
