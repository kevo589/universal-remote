package dev.kmedrano.remote.protocol.firetv

import android.util.Base64
import com.tananaev.adblib.AdbAuthenticationFailedException
import com.tananaev.adblib.AdbBase64
import com.tananaev.adblib.AdbConnection
import com.tananaev.adblib.AdbCrypto
import dev.kmedrano.remote.core.ConnectionState
import dev.kmedrano.remote.core.LaunchableApp
import dev.kmedrano.remote.core.PairingInput
import dev.kmedrano.remote.core.PairingInputKind
import dev.kmedrano.remote.core.PairingResult
import dev.kmedrano.remote.core.RemoteClient
import dev.kmedrano.remote.core.RemoteCommand
import dev.kmedrano.remote.core.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Controls a Fire TV over the standard Android Debug Bridge protocol (the same one `adb` itself
 * uses), which requires the user to enable "ADB debugging" once in the Fire TV's Developer
 * Options. Uses `com.tananaev.adblib` for the wire protocol/RSA auth handshake rather than
 * shelling out to the `adb` binary (there isn't one available on Android).
 *
 * First connection makes the Fire TV show an "Allow USB debugging?" prompt with this app's key
 * fingerprint; once approved, the same RSA keypair is reused for silent reconnects. Once
 * connected, every command is a fresh one-shot `shell:input keyevent <code>` stream — simpler
 * and more robust than keeping one long-lived interactive shell session open.
 */
internal class FireTvRemoteClient(
    override val device: TvDevice,
    storedCredential: ByteArray?,
    private val onCredentialUpdated: (ByteArray) -> Unit,
) : RemoteClient {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override val availableApps: List<LaunchableApp> = emptyList()

    private val base64Impl = AdbBase64 { data -> Base64.encodeToString(data, Base64.NO_WRAP) }

    private val crypto: AdbCrypto = run {
        val keyPair = storedCredential?.let(AdbKeyPairCodec::decode)
            ?: AdbKeyPairCodec.generate().also { onCredentialUpdated(AdbKeyPairCodec.encode(it)) }
        AdbCrypto.loadAdbKeyPair(base64Impl, keyPair)
    }

    @Volatile
    private var connection: AdbConnection? = null

    /** Owns background stream cleanup (see [runShell]) so it outlives any single command call
     * but is torn down along with this client. */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun connect(): Result<Unit> =
        openConnection(timeoutMs = RECONNECT_TIMEOUT_MS, throwOnUnauthorised = true)

    override suspend fun startPairing(): PairingResult {
        val result = openConnection(timeoutMs = PAIRING_TIMEOUT_MS, throwOnUnauthorised = false)
        return result.fold(
            onSuccess = { PairingResult.Success },
            onFailure = { PairingResult.Failed(it.message ?: "Couldn't connect to the Fire TV") },
        )
    }

    override suspend fun submitPairingInput(input: PairingInput): PairingResult =
        if (_connectionState.value is ConnectionState.Connected) {
            PairingResult.Success
        } else {
            // Nothing for Fire TV to do with typed input — pairing is entirely the on-device
            // "Allow USB debugging?" prompt.
            PairingResult.NeedsInput(PairingInputKind.ON_TV_APPROVAL)
        }

    override suspend fun disconnect() {
        cleanupScope.cancel()
        withContext(Dispatchers.IO) { runCatching { connection?.close() } }
        connection = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> = runShell(
        "input keyevent ${command.toKeyEventCode()}",
    )

    override suspend fun launchApp(app: LaunchableApp): Result<Unit> =
        Result.failure(UnsupportedOperationException("App launching isn't implemented for Fire TV yet"))

    private suspend fun runShell(command: String): Result<Unit> {
        val conn = connection ?: return Result.failure(IllegalStateException("Not connected to ${device.displayName}"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val stream = conn.open("shell:$command")

                // open() only confirms the command was accepted and its process started on the
                // device, not that it finished — closing our end too early can race the device
                // tearing that process down before it actually completes. But Fire OS's adbd
                // doesn't reliably signal completion by closing the stream back to us either, so
                // waiting for that (or a short timeout standing in for it) here made *every*
                // button press feel laggy without actually being more correct: a longer timeout
                // just blocks this call for that long, whether or not the command needed it.
                //
                // Cleanup runs in the background instead, on its own generous timer — the
                // command is already running by the time we return, so the UI doesn't wait on
                // any of this.
                cleanupScope.launch {
                    withTimeoutOrNull(SHELL_CLEANUP_TIMEOUT_MS) {
                        runInterruptible { runCatching { stream.read() } }
                    }
                    runCatching { stream.close() }
                }
                Unit
            }
        }
    }

    private suspend fun openConnection(timeoutMs: Long, throwOnUnauthorised: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            _connectionState.value = if (throwOnUnauthorised) {
                ConnectionState.Connecting
            } else {
                ConnectionState.AwaitingPairingConfirmation(PairingInputKind.ON_TV_APPROVAL)
            }

            runCatching {
                val socket = Socket()
                socket.connect(InetSocketAddress(device.host, ADB_PORT), SOCKET_CONNECT_TIMEOUT_MS)

                val newConnection = AdbConnection.create(socket, crypto)
                val connected = newConnection.connect(timeoutMs, TimeUnit.MILLISECONDS, throwOnUnauthorised)
                if (!connected) {
                    runCatching { socket.close() }
                    error("Timed out connecting to ${device.host}")
                }

                connection = newConnection
                _connectionState.value = ConnectionState.Connected
            }.onFailure { error ->
                val message = when (error) {
                    is AdbAuthenticationFailedException ->
                        "Fire TV rejected this app's pairing key — remove and re-add the device to pair again"
                    else -> error.message ?: "Couldn't reach ${device.host}"
                }
                _connectionState.value = ConnectionState.Error(message, recoverable = true)
            }
        }

    private companion object {
        const val ADB_PORT = 5555
        const val SOCKET_CONNECT_TIMEOUT_MS = 10_000
        const val PAIRING_TIMEOUT_MS = 60_000L
        const val RECONNECT_TIMEOUT_MS = 10_000L
        const val SHELL_CLEANUP_TIMEOUT_MS = 2_000L
    }
}
