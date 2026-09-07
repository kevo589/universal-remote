package dev.kmedrano.remote.protocol.firetv

import android.util.Base64
import com.tananaev.adblib.AdbAuthenticationFailedException
import com.tananaev.adblib.AdbBase64
import com.tananaev.adblib.AdbConnection
import com.tananaev.adblib.AdbCrypto
import com.tananaev.adblib.AdbStream
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
 * fingerprint; once approved, the same RSA keypair is reused for silent reconnects.
 *
 * Commands are written into a single persistent interactive shell (`shell:`, opened once at
 * connect time) rather than each spawning its own one-shot `shell:<command>` stream — opening a
 * fresh stream per keypress measured multiple seconds of latency per button on real hardware,
 * consistent with the device being slow to spin up (and, under rapid presses, queue behind) a
 * new shell/process for every command. Writing a line into an already-running shell avoids that
 * entirely: only the first connection pays any shell startup cost.
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

    @Volatile
    private var shellStream: AdbStream? = null

    /** Owns the background shell-output drain (see [openConnection]) so it outlives any single
     * command call but is torn down along with this client. */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        backgroundScope.cancel()
        withContext(Dispatchers.IO) {
            runCatching { shellStream?.close() }
            runCatching { connection?.close() }
        }
        shellStream = null
        connection = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> = runShell(
        "input keyevent ${command.toKeyEventCode()}",
    )

    override suspend fun launchApp(app: LaunchableApp): Result<Unit> =
        Result.failure(UnsupportedOperationException("App launching isn't implemented for Fire TV yet"))

    private suspend fun runShell(command: String): Result<Unit> {
        val shell = shellStream ?: return Result.failure(IllegalStateException("Not connected to ${device.displayName}"))
        return withContext(Dispatchers.IO) {
            // AdbStream's write(String) overload null-terminates the payload (it's meant for the
            // ADB "open" destination convention) — a shell reading from stdin needs a newline to
            // treat this as a complete command line, so this writes raw bytes instead.
            runCatching { shell.write("$command\n".toByteArray(Charsets.UTF_8)) }
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

                // open() has no built-in timeout and would otherwise block indefinitely if the
                // shell service never responds.
                val shell = withTimeout(SHELL_OPEN_TIMEOUT_MS) {
                    runInterruptible { newConnection.open("shell:") }
                }
                shellStream = shell
                startDrainingShellOutput(shell)

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

    /**
     * The persistent shell echoes input and prints prompts; nothing reads that unless we do,
     * which would otherwise pile up forever in the stream's internal queue for the life of the
     * connection. Draining it in the background also doubles as the way we notice the shell
     * session ended (the remote closes it), so we can reflect that as a dropped connection
     * instead of silently accepting writes that go nowhere.
     */
    private fun startDrainingShellOutput(shell: AdbStream) {
        backgroundScope.launch {
            while (isActive) {
                val stillOpen = runInterruptible { runCatching { shell.read() } }.isSuccess
                if (!stillOpen) {
                    if (shellStream === shell) {
                        shellStream = null
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    break
                }
            }
        }
    }

    private companion object {
        const val ADB_PORT = 5555
        const val SOCKET_CONNECT_TIMEOUT_MS = 10_000
        const val PAIRING_TIMEOUT_MS = 60_000L
        const val RECONNECT_TIMEOUT_MS = 10_000L
        const val SHELL_OPEN_TIMEOUT_MS = 10_000L
    }
}
