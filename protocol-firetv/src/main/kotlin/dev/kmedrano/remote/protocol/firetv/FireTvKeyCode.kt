package dev.kmedrano.remote.protocol.firetv

import dev.kmedrano.remote.core.RemoteCommand

/**
 * Maps our protocol-agnostic [RemoteCommand] to standard Android `KEYCODE_*` names, sent via
 * `input keyevent <name>` over an ADB shell stream. Unlike Samsung's fixed remote-key
 * vocabulary, ADB gives direct `android.view.KeyEvent` injection, so power can distinguish
 * wake from sleep instead of a single ambiguous toggle.
 */
internal fun RemoteCommand.toKeyEventCode(): String = when (this) {
    RemoteCommand.DpadUp -> "KEYCODE_DPAD_UP"
    RemoteCommand.DpadDown -> "KEYCODE_DPAD_DOWN"
    RemoteCommand.DpadLeft -> "KEYCODE_DPAD_LEFT"
    RemoteCommand.DpadRight -> "KEYCODE_DPAD_RIGHT"
    RemoteCommand.Select -> "KEYCODE_DPAD_CENTER"
    RemoteCommand.Back -> "KEYCODE_BACK"
    RemoteCommand.Home -> "KEYCODE_HOME"
    RemoteCommand.Menu -> "KEYCODE_MENU"
    RemoteCommand.PlayPause -> "KEYCODE_MEDIA_PLAY_PAUSE"
    RemoteCommand.VolumeUp -> "KEYCODE_VOLUME_UP"
    RemoteCommand.VolumeDown -> "KEYCODE_VOLUME_DOWN"
    RemoteCommand.MuteToggle -> "KEYCODE_VOLUME_MUTE"
    is RemoteCommand.Power -> if (on) "KEYCODE_WAKEUP" else "KEYCODE_SLEEP"
}
