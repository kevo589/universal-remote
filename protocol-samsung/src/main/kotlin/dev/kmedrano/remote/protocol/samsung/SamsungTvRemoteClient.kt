package dev.kmedrano.remote.protocol.samsung

import android.util.Base64
import dev.kmedrano.remote.core.ConnectionState
import dev.kmedrano.remote.core.LaunchableApp
import dev.kmedrano.remote.core.PairingInput
import dev.kmedrano.remote.core.PairingInputKind
import dev.kmedrano.remote.core.PairingResult
import dev.kmedrano.remote.core.RemoteClient
import dev.kmedrano.remote.core.RemoteCommand
import dev.kmedrano.remote.core.TvDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume

/**
 * Talks to a Samsung Tizen TV's (unofficial but widely relied on) WebSocket remote-control API.
 *
 * Tries the modern `wss://<host>:8002/...` endpoint first, falling back to the older plaintext
 * `ws://<host>:8001/...` one some firmware still expects. First connection (no stored token)
 * makes the TV show an on-screen "Allow Universal Remote to connect?" prompt; once approved, the
 * TV sends back a token via the `ms.channel.connect` event which is persisted and reused on
 * every future connection to skip that prompt.
 */
internal class SamsungTvRemoteClient(
    override val device: TvDevice,
    storedCredential: ByteArray?,
    private val onCredentialUpdated: (ByteArray) -> Unit,
) : RemoteClient {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override val availableApps: List<LaunchableApp> = emptyList()

    private var token: String? = storedCredential?.toString(Charsets.UTF_8)
    private var webSocket: WebSocket? = null

    private val httpClient = OkHttpClient.Builder()
        .sslSocketFactory(SamsungTrust.sslContext.socketFactory, SamsungTrust.trustManager)
        .hostnameVerifier(SamsungTrust.hostnameVerifier)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun connect(): Result<Unit> = openConnection(timeoutMs = RECONNECT_TIMEOUT_MS)

    override suspend fun startPairing(): PairingResult {
        val result = openConnection(timeoutMs = PAIRING_TIMEOUT_MS)
        return result.fold(
            onSuccess = { PairingResult.Success },
            onFailure = { PairingResult.Failed(it.message ?: "Couldn't connect to the TV") },
        )
    }

    override suspend fun submitPairingInput(input: PairingInput): PairingResult =
        if (_connectionState.value is ConnectionState.Connected) {
            PairingResult.Success
        } else {
            // Nothing for Samsung to do with typed input — pairing is entirely an on-TV prompt.
            PairingResult.NeedsInput(PairingInputKind.ON_TV_APPROVAL)
        }

    override suspend fun disconnect() {
        webSocket?.close(NORMAL_CLOSURE_CODE, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> {
        val socket = webSocket ?: return Result.failure(IllegalStateException("Not connected to ${device.displayName}"))
        val sent = socket.send(buildRemoteControlMessage(command.toSamsungKeyCode()))
        return if (sent) Result.success(Unit) else Result.failure(IllegalStateException("Failed to send command"))
    }

    override suspend fun launchApp(app: LaunchableApp): Result<Unit> =
        Result.failure(UnsupportedOperationException("App launching isn't implemented for Samsung yet"))

    private suspend fun openConnection(timeoutMs: Long): Result<Unit> {
        val tlsAttempt = tryEndpoint(useTls = true, timeoutMs = timeoutMs)
        if (tlsAttempt.isSuccess) return tlsAttempt
        return tryEndpoint(useTls = false, timeoutMs = timeoutMs)
    }

    private suspend fun tryEndpoint(useTls: Boolean, timeoutMs: Long): Result<Unit> {
        _connectionState.value = if (token == null) {
            ConnectionState.AwaitingPairingConfirmation(PairingInputKind.ON_TV_APPROVAL)
        } else {
            ConnectionState.Connecting
        }

        val port = if (useTls) TLS_PORT else PLAINTEXT_PORT
        val scheme = if (useTls) "wss" else "ws"
        val appNameB64 = Base64.encodeToString(APP_NAME.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val tokenParam = token?.let { "&token=$it" }.orEmpty()
        val url = "$scheme://${device.host}:$port/api/v2/channels/samsung.remote.control?name=$appNameB64$tokenParam"

        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Result<Unit>> { continuation ->
                val request = Request.Builder().url(url).build()
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        this@SamsungTvRemoteClient.webSocket = webSocket
                        // Don't resolve yet — wait for ms.channel.connect, which is what
                        // actually carries the token and only arrives after the user approves
                        // the on-screen prompt on a first-time pairing.
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                        when (json.optString("event")) {
                            "ms.channel.connect" -> {
                                val newToken = json.optJSONObject("data")
                                    ?.optString("token")
                                    ?.takeIf { it.isNotBlank() }
                                if (newToken != null && newToken != token) {
                                    token = newToken
                                    onCredentialUpdated(newToken.toByteArray(Charsets.UTF_8))
                                }
                                _connectionState.value = ConnectionState.Connected
                                if (continuation.isActive) continuation.resume(Result.success(Unit))
                            }
                            "ms.channel.unauthorized" -> {
                                _connectionState.value = ConnectionState.Error("Pairing was declined on the TV", recoverable = true)
                                if (continuation.isActive) {
                                    continuation.resume(
                                        Result.failure(IllegalStateException("Pairing was declined on the TV")),
                                    )
                                }
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        _connectionState.value = ConnectionState.Error(
                            t.message ?: "Couldn't reach ${device.host}",
                            recoverable = true,
                        )
                        if (continuation.isActive) continuation.resume(Result.failure(t))
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (this@SamsungTvRemoteClient.webSocket === webSocket) {
                            this@SamsungTvRemoteClient.webSocket = null
                            _connectionState.value = ConnectionState.Disconnected
                        }
                    }
                }
                val openedSocket = httpClient.newWebSocket(request, listener)
                continuation.invokeOnCancellation { openedSocket.cancel() }
            }
        }

        return result ?: run {
            _connectionState.value = ConnectionState.Error("Timed out waiting for ${device.host}:$port", recoverable = true)
            Result.failure(TimeoutException("Timed out connecting to ${device.host}:$port"))
        }
    }

    private fun buildRemoteControlMessage(keyCode: String): String {
        val params = JSONObject()
            .put("Cmd", "Click")
            .put("DataOfCmd", keyCode)
            .put("Option", "false")
            .put("TypeOfRemote", "SendRemoteKey")
        return JSONObject()
            .put("method", "ms.remote.control")
            .put("params", params)
            .toString()
    }

    private companion object {
        const val APP_NAME = "Universal Remote"
        const val TLS_PORT = 8002
        const val PLAINTEXT_PORT = 8001
        const val NORMAL_CLOSURE_CODE = 1000
        const val PAIRING_TIMEOUT_MS = 60_000L
        const val RECONNECT_TIMEOUT_MS = 10_000L
    }
}
