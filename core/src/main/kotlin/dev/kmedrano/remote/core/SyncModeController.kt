package dev.kmedrano.remote.core

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Broadcasts a single [RemoteCommand] to every currently-connected [RemoteClient] at once.
 *
 * Intended for "sync mode" (power / volume / mute) only — the UI is responsible for never
 * routing D-pad navigation through this, since broadcasting navigation to independent
 * device UIs at once doesn't make sense.
 */
class SyncModeController(private val activeClients: () -> Collection<RemoteClient>) {
    suspend fun broadcast(command: RemoteCommand): Map<TvDevice, Result<Unit>> = coroutineScope {
        activeClients()
            .map { client -> client to async { client.sendCommand(command) } }
            .associate { (client, deferred) -> client.device to deferred.await() }
    }
}
