package dev.kmedrano.remote.protocol.samsung

import dev.kmedrano.remote.core.RemoteCommand

/**
 * Maps our protocol-agnostic [RemoteCommand] to Samsung's `KEY_*` remote key names, as used by
 * the `ms.remote.control` WebSocket command and documented by the (unofficial but widely relied
 * on) Samsung Tizen TV WebSocket API.
 */
internal fun RemoteCommand.toSamsungKeyCode(): String = when (this) {
    RemoteCommand.DpadUp -> "KEY_UP"
    RemoteCommand.DpadDown -> "KEY_DOWN"
    RemoteCommand.DpadLeft -> "KEY_LEFT"
    RemoteCommand.DpadRight -> "KEY_RIGHT"
    RemoteCommand.Select -> "KEY_ENTER"
    RemoteCommand.Back -> "KEY_RETURN"
    RemoteCommand.Home -> "KEY_HOME"
    RemoteCommand.Menu -> "KEY_MENU"
    // Samsung remotes have separate physical Play and Pause buttons, not one toggle — KEY_PAUSE
    // is the closer single mapping for a "play/pause" UI button. If testing shows the opposite
    // reads better, this is the one line to flip.
    RemoteCommand.PlayPause -> "KEY_PAUSE"
    RemoteCommand.VolumeUp -> "KEY_VOLUP"
    RemoteCommand.VolumeDown -> "KEY_VOLDOWN"
    RemoteCommand.MuteToggle -> "KEY_MUTE"
    // Samsung TVs generally can't be woken from a fully-off/network-disconnected state through
    // this API (that needs Wake-on-LAN, not implemented yet) — KEY_POWER only reliably works
    // to turn the TV *off* from this app. Turning on likely needs the physical remote or a
    // future Wake-on-LAN addition.
    is RemoteCommand.Power -> "KEY_POWER"
}
