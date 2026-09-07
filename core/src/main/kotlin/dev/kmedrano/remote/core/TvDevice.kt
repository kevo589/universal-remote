package dev.kmedrano.remote.core

/** Which of the four supported streaming platforms a [TvDevice] talks to. */
enum class ProtocolType {
    APPLE_TV,
    SAMSUNG_TIZEN,
    FIRE_TV_ADB,
    ANDROID_TV_REMOTE_V2, // also covers Chromecast with Google TV, which runs Android TV OS
}

/**
 * A device the user has added to the app.
 *
 * [displayName] is whatever friendly/model name the protocol reports (or the manual label the
 * user typed when adding it) — v1 does not support renaming devices after the fact.
 *
 * [port] is nullable because it isn't always known up front: Apple TV's Companion protocol port
 * is assigned dynamically per-device and only becomes known once mDNS discovery resolves it.
 */
data class TvDevice(
    val id: String,
    val protocol: ProtocolType,
    val displayName: String,
    val host: String,
    val port: Int? = null,
)
