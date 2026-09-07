package dev.kmedrano.remote.core

/**
 * A remote-control button press, expressed protocol-agnostically. Each [RemoteClient]
 * implementation maps these onto whatever its underlying protocol actually sends
 * (a keycode, a JSON command, an ADB keyevent, a protobuf message, ...).
 */
sealed interface RemoteCommand {
    data object DpadUp : RemoteCommand
    data object DpadDown : RemoteCommand
    data object DpadLeft : RemoteCommand
    data object DpadRight : RemoteCommand
    data object Select : RemoteCommand
    data object Back : RemoteCommand
    data object Home : RemoteCommand
    data object Menu : RemoteCommand
    data object PlayPause : RemoteCommand
    data object VolumeUp : RemoteCommand
    data object VolumeDown : RemoteCommand
    data object MuteToggle : RemoteCommand
    data class Power(val on: Boolean) : RemoteCommand
}

/** An app the user can jump straight to on a given device, where the protocol supports it. */
data class LaunchableApp(
    val label: String,
    /** Protocol-specific launch token: a bundle id, Android package name, or app-link URI. */
    val launchToken: String,
)
