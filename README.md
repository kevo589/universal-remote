# Universal Remote

A single Android app to control an Apple TV, a Samsung (Tizen) Smart TV, an Amazon Fire TV,
and a Chromecast with Google TV — all from one screen, connected at the same time.

None of these platforms publish a public third-party remote-control API. This app talks to
each one using the same reverse-engineered-but-stable local-network protocol that existing
"universal remote" apps and home-automation integrations already rely on. See
[`docs/plan.md`](docs/plan.md) for the full protocol research, architecture, risk notes, and
build order.

## Status

🚧 **M2 — Samsung confirmed working, Fire TV just landed.** Devices persist across app
restarts (Room + Keystore-encrypted credentials). Apple TV and Chromecast still show the
"not implemented yet" banner with no-op buttons.

| Device | Status |
|---|---|
| Samsung Tizen TV | ✅ confirmed working on real hardware (2 TVs) |
| Amazon Fire TV | ✅ implemented — needs real-device testing feedback |
| Chromecast with Google TV | ⏳ not started |
| Apple TV | ⏳ not started |

## Getting a build to test

Every push builds a debug APK automatically — no local Android Studio setup required just to
try it out:

1. Go to the **Actions** tab of this repo → the latest **Android CI** run → download the
   `app-debug` artifact (a zip containing the APK) from the run summary.
2. Alternatively, once a tag like `v0.1.0` is pushed, a **Release** is cut with the APK
   attached directly — no zip, no navigating the Actions UI.
3. Copy the `.apk` to your phone and install it (you'll need to allow "install unknown apps"
   for whatever app you use to open it — Files, a browser, etc.).

## Building locally

Requires JDK 17 and Android Studio (or just the command-line SDK — the Gradle wrapper handles
everything else).

```bash
./gradlew assembleDebug
```

The output APK lands in `app/build/outputs/apk/debug/`.

## Project structure

```
app/                 UI (Jetpack Compose), navigation, the foreground connection service, DI
core/                Protocol-agnostic contract (RemoteClient, commands, connection state,
                     Room + Keystore-encrypted device/credential persistence)
protocol-samsung/    Samsung Tizen TV — WebSocket JSON API     ✅ implemented
protocol-firetv/     Fire TV — ADB protocol                    ✅ implemented
protocol-apple/      Apple TV — Companion protocol             (not yet implemented)
protocol-androidtv/  Chromecast w/ Google TV — Android TV Remote Protocol v2 (not yet implemented)
```

Each protocol module implements `core`'s `RemoteClient` interface and is otherwise
independent — the UI and the "sync mode" broadcast (power/volume/mute to every connected
device at once) only ever depend on that interface.

## Build order

1. ~~Scaffold + CI~~ ✅
2. ~~Samsung~~ ✅ (lowest risk — validates the whole architecture end-to-end cheaply)
3. ~~Fire TV~~ ✅
4. Chromecast with Google TV / Android TV Remote v2
5. Apple TV (highest risk — no existing Kotlin/Java implementation of Apple's proprietary
   Companion protocol exists anywhere; this is a from-scratch port of the crypto/pairing
   logic the Python `pyatv` project reverse-engineered)
6. Sync mode wiring + reconnect/error-state polish

## Testing the Samsung protocol

Add the TV with its local IP address (find it in the TV's Settings → Network → Network
Status). On first connect, the TV shows an on-screen popup asking to allow "Universal
Remote" to connect — approve it there; the app waits up to 60 seconds for that. After
approving once, reconnecting on future app launches should be silent (no prompt).

Known rough edges worth reporting back if you hit them: powering the TV **on** from this app
probably won't work if it's fully asleep/off-network (Samsung's API can only reliably power
TVs *off* without Wake-on-LAN, which isn't implemented yet), and the play/pause button sends
a single "pause" keycode since Samsung remotes have separate physical Play/Pause buttons
rather than one toggle.

## Testing the Fire TV protocol

First, on the Fire TV itself: **Settings → My Fire TV → Developer Options** (if you don't see
Developer Options, go to **My Fire TV → About** and click the device name / build number
repeatedly to unlock it), then turn on **ADB Debugging** and **Apps from Unknown Sources**.
Find the IP under **Settings → My Fire TV → About → Network**.

Add the device in the app the same way as Samsung. On first connect, the Fire TV shows an
"Allow USB debugging from this app?" popup with a key fingerprint — approve it (checking
"Always allow" avoids being asked again). The app waits up to 60 seconds for that.

Because this is full ADB access rather than a fixed remote-key vocabulary, power is more
precise here than on Samsung: the power button sends a real sleep/wake keycode rather than a
single ambiguous toggle. If a Fire TV you've already paired ever rejects reconnecting (e.g.
you revoked USB debugging authorizations on the device, or factory reset it), you'll need to
remove and re-add it in the app to pair fresh — the error message should say so.

## A note on how this works

Every protocol here is unofficial: Apple's Companion protocol is fully undocumented, and
Samsung/Fire TV/Android TV Remote v2 are reverse-engineered-but-widely-relied-upon (the same
approach Home Assistant and similar projects use). A future TV software update from any
vendor could change behavior without notice — that's an inherent property of building a
personal universal remote, not a bug to eventually fix.

Samsung's self-signed TLS cert and Fire TV's ADB pairing are both "trust whatever's on my
home network" security models. That's an appropriate tradeoff for a personal remote used on
a home Wi-Fi network, and worth knowingly accepting rather than being surprised by later.
