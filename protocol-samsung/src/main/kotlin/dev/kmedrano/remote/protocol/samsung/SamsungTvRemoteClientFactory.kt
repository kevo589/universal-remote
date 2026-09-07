package dev.kmedrano.remote.protocol.samsung

import dev.kmedrano.remote.core.RemoteClientFactory

/**
 * Public entry point for `:app` (or any other module) to register a Samsung [RemoteClient]
 * factory — [SamsungTvRemoteClient] itself is internal to keep protocol details out of every
 * other module's compile classpath.
 */
val SamsungTvRemoteClientFactory: RemoteClientFactory =
    RemoteClientFactory { device, storedCredential, onCredentialUpdated ->
        SamsungTvRemoteClient(device, storedCredential, onCredentialUpdated)
    }
