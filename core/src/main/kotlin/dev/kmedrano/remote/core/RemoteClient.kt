package dev.kmedrano.remote.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Protocol-agnostic contract for controlling one connected device.
 *
 * The UI layer (and [SyncModeController]) depend only on this interface, never on any
 * protocol-specific type — each of the four `protocol-*` modules provides one implementation.
 */
interface RemoteClient {
    val device: TvDevice
    val connectionState: StateFlow<ConnectionState>

    /** Apps this client currently knows how to launch on the device. Empty if unsupported. */
    val availableApps: List<LaunchableApp>

    /** Begin (or resume) the one-time pairing flow. May require [submitPairingInput]. */
    suspend fun startPairing(): PairingResult

    /** Submit pairing input requested by a previous [PairingResult.NeedsInput]. */
    suspend fun submitPairingInput(input: PairingInput): PairingResult

    /** Reconnect using previously stored pairing credentials, without any UI interaction. */
    suspend fun connect(): Result<Unit>

    suspend fun disconnect()

    suspend fun sendCommand(command: RemoteCommand): Result<Unit>

    suspend fun launchApp(app: LaunchableApp): Result<Unit>
}

/** Creates a [RemoteClient] for a given [TvDevice]. One factory per [ProtocolType]. */
fun interface RemoteClientFactory {
    fun create(device: TvDevice): RemoteClient
}
