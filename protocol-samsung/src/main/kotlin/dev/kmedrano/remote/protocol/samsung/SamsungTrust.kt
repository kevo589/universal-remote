package dev.kmedrano.remote.protocol.samsung

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Samsung TVs serve a self-signed certificate on their WebSocket control port — there's no CA
 * to validate against. This trust-everything setup is deliberately scoped to the one
 * [okhttp3.OkHttpClient] built for talking to a Samsung TV on the local network; it must never
 * be reused for any general-purpose HTTP client.
 */
internal object SamsungTrust {
    val trustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }

    val hostnameVerifier = HostnameVerifier { _, _ -> true }
}
