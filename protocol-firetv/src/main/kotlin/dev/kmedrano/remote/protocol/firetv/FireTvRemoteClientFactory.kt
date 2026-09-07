package dev.kmedrano.remote.protocol.firetv

import dev.kmedrano.remote.core.RemoteClientFactory

/**
 * Public entry point for `:app` to register a Fire TV [RemoteClientFactory] —
 * [FireTvRemoteClient] itself is internal to keep ADB/adblib details out of every other
 * module's compile classpath.
 */
val FireTvRemoteClientFactory: RemoteClientFactory =
    RemoteClientFactory { device, storedCredential, onCredentialUpdated ->
        FireTvRemoteClient(device, storedCredential, onCredentialUpdated)
    }
