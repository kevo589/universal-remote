# Universal Android Remote for Apple TV / Samsung TV / Fire TV / Chromecast (Google TV)

## Context

The user wants one Android app that replaces four separate remotes — Apple TV, Samsung
Smart TV (Tizen), Amazon Fire TV, and Chromecast with Google TV — by connecting to all
of them on the home network at app launch and controlling whichever one they need from
a single screen.

None of the four vendors publish a public third-party remote-control API. Every existing
"universal remote" app (and every option here) works by talking whatever
reverse-engineered/unofficial-but-stable local-network protocol each platform actually
uses internally. That's a permanent characteristic of this space, not a shortcut we're
taking — flagged clearly below so it's a conscious tradeoff, not a surprise later.

This assistant's sandbox has **no Android SDK, no physical devices, and no access to the
user's home network** — it can never build, run, or test this app directly. The plan is
built around that: a GitHub repo with a CI workflow produces an installable debug APK on
every push, the user sideloads it onto their phone and tests against their real TVs, and
reports results back for the next iteration.

## Confirmed protocols (research summary)

| Device | Protocol | Risk | Notes |
|---|---|---|---|
| Samsung Tizen TV | WebSocket JSON API (`wss://<ip>:8002/api/v2/channels/samsung.remote.control`) | **Low** | Mature, widely used (Home Assistant etc.). One-time on-TV pairing approval returns a persistent token. |
| Amazon Fire TV | Standard ADB protocol over TCP 5555 | **Medium** | Requires user to enable "ADB debugging" once in Fire TV Developer Options. Well-documented (AOSP ADB spec); a Java reference client exists. |
| Chromecast with Google TV | "Android TV Remote Protocol v2" (protobuf over mutual TLS) — same protocol as any Android TV, since this device runs Android TV OS with its own home screen | **Medium** | No official SDK, but reverse-engineered and stable (`androidtvremote2`). Pairing = self-signed cert exchange + 6-digit on-screen code, then a stored client cert re-authenticates silently. |
| Apple TV | Apple's proprietary "Companion" protocol | **High** | Fully undocumented by Apple. Only reference implementation anywhere is the Python `pyatv` project — no existing Kotlin/Java implementation. Requires porting HomeKit-style SRP/pairing crypto, Apple's OPACK binary serialization, and Companion frame encoding from Python to Kotlin. This is the single biggest risk in the project and is scheduled last for that reason. |

Confirmed product decisions (not open questions): target is **Chromecast with Google TV**
specifically (not a legacy cast-only dongle); the app auto-connects and stays connected to
all added devices in the background, with per-device tabs for D-pad/nav control plus a
**sync-mode toggle** that broadcasts power/volume/mute (not D-pad) to every connected
device at once; v1 scope is core navigation + app-launch shortcuts — no text-input
passthrough, no device renaming/reordering in v1.

## Architecture

**Module structure** — light multi-module Gradle project, not a single module, so each
protocol's dependencies and blast radius stay isolated (Bouncy Castle only pulled into the
Apple TV module, etc.) and the risky Apple TV module can be rewritten without touching the
other three:

```
app/                 → Compose UI, NavHost, RemoteConnectionService (foreground service), manual DI container
core/                → RemoteClient contract, sealed command/state types, Room DB + Keystore credential storage
protocol-apple/      → Companion protocol client
protocol-samsung/    → Samsung WebSocket client
protocol-firetv/     → Fire TV ADB client
protocol-androidtv/  → Android TV Remote v2 client (also covers Chromecast w/ Google TV)
.github/workflows/   → CI (build) + release (tagged APK) workflows
```

No Hilt/Dagger — a hand-written `AppContainer` singleton is enough for four clients and
avoids an extra build-tool learning curve for a solo project.

**Core abstraction** (`core/.../RemoteClient.kt`): a `RemoteClient` interface every
protocol module implements — `connect()`, `startPairing()`/`submitPairingInput()`,
`sendCommand(RemoteCommand)`, `launchApp(...)`, and a `connectionState: StateFlow<ConnectionState>`
— plus sealed `RemoteCommand` (DpadUp/Down/Left/Right, Select, Back, Home, Menu,
PlayPause, VolumeUp/Down, MuteToggle, Power) and `ConnectionState`
(Disconnected/Discovering/AwaitingPairingConfirmation/Connecting/Connected/Error) types.
The UI and a `SyncModeController` (broadcasts a command to every connected client) depend
only on this interface, never on protocol internals.

**Persistent background connections require a foreground `Service`**
(`app/.../service/RemoteConnectionService.kt`) — Android suspends background sockets
otherwise, so "stays connected" would silently stop working once the app isn't
foregrounded without this. Needs `foregroundServiceType="connectedDevice"` plus the
matching `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_CONNECTED_DEVICE` manifest permissions,
and shows a persistent low-priority "Universal Remote — N/4 connected" notification.

**Credential storage**: one Room table (`DeviceEntity`: id, protocol, displayName, host,
port, credential blob) with the credential blob encrypted via a hand-written AES-256-GCM
helper backed by `AndroidKeyStore` (`core/.../data/KeystoreCipherHelper.kt`) —
preferred over `androidx.security:security-crypto`'s `EncryptedSharedPreferences` because
that library models flat key-value pairs (awkward for four structured per-device records)
and its 1.1.x line has stayed in alpha with an uncertain roadmap.

**Discovery**, per device (no single strategy fits all four):
- Apple TV: mDNS (`NsdManager`, `_companion-link._tcp`) — near-required, since Companion's
  port is dynamically assigned so there's no fixed port to fall back to for manual entry.
- Android TV Remote v2 / Chromecast w/ Google TV: mDNS (`_androidtvremote2._tcp`) with a
  reliable manual-IP fallback (this protocol's port is fixed).
- Samsung: default to manual IP entry + an optional "Scan network" button that does a fast
  concurrent TCP connect-probe of the local /24 on ports 8001/8002, rather than relying on
  SSDP multicast — Android multicast is genuinely flaky across OEMs/Doze/guest Wi-Fi.
- Fire TV: manual IP entry only — ADB has no discovery protocol.

Known risk either way: `NsdManager` has long-standing OEM-dependent flakiness. Discovery
sits behind a small per-protocol `Discoverer` interface so swapping in a library like
JmDNS later doesn't touch client code.

**UI** (Compose, single Activity, flat nav graph `Home ⇄ AddDevice ⇄ Pairing → Home`):
- `HomeScreen` — a tab per added device + a "Sync Mode" toggle in the top bar (shows a
  power/volume/mute row usable regardless of the active tab).
- `DeviceRemoteScreen` — D-pad, OK, back/home/menu, play/pause, volume/mute, power, and
  app-launch chips built from whatever `RemoteClient.availableApps` reports (no dead
  buttons for protocols that don't support a given app).
- `AddDeviceScreen` — protocol picker → discovered-device list or manual IP entry as above.
- `PairingScreen` — branches on the pairing kind: PIN entry (Apple TV), 6-digit code
  (Android TV Remote v2), or an "approve on your TV" spinner (Samsung, Fire TV).

**Key dependencies** (verified against Maven Central / current docs, not assumed):
- Samsung: `com.squareup.okhttp3:okhttp-bom:5.5.0` + `okhttp-android` (5.x split the
  artifact by platform), `kotlinx-serialization-json`.
- Fire TV: `com.tananaev:adblib:1.3` (confirmed published on Maven Central; it's a small,
  last-updated-2021 fork of the standard Java ADB client — good enough to start, with a
  documented fallback of vendoring its ~10 source files in-repo if it misbehaves against
  modern Fire OS).
- Android TV Remote v2: `com.google.protobuf:protobuf-gradle-plugin` +
  `protobuf-kotlin-lite`, using the `remotemessage.proto`/`pairingmessage.proto` schema
  vendored from the `tronikos/androidtvremote2` reference project. Transport is plain
  `javax.net.ssl` mutual TLS — no exotic crypto needed here.
- Apple TV: `org.bouncycastle:bcprov-jdk18on` used as a plain library for SRP6a/X25519/
  Ed25519/ChaCha20-Poly1305 — called directly (`SRP6Client`, `X25519Agreement`,
  `Ed25519Signer`, `ChaCha20Poly1305`), **never registered as a `Security` provider**,
  since Android ships its own stripped BC provider and registering the full desktop one
  on top causes known provider/class-collision failures.

## CI / build pipeline

`.github/workflows/android-ci.yml` (on push): checkout → `actions/setup-java@v4`
(Temurin 17 — current AGP still targets JDK 17) → `gradle/actions/setup-gradle@v4` →
`./gradlew assembleDebug` → `actions/upload-artifact@v4` uploading
`app/build/outputs/apk/debug/*.apk`. Debug builds use Android's auto-generated debug
keystore, so no signing secrets are needed for personal sideloading.

`.github/workflows/release.yml` (on tag push `v*`): same build, then
`softprops/action-gh-release@v2` attaches the APK to a GitHub Release — a stable,
bookmarkable download link instead of re-navigating the Actions UI each time.

**Setup step before any of this runs**: the Gradle wrapper doesn't exist yet in an empty
repo, so it gets generated once as part of scaffolding (via `gradle wrapper`) and
committed — `./gradlew` has nothing to invoke until then.

**Repo creation**: this assistant has no `gh` CLI and no GitHub credentials configured in
its sandbox. The plan is for the user to create an empty GitHub repository via github.com,
then either push the initial scaffold from their own already-authenticated machine, or
share a repo URL the assistant can push to if the user sets up a credential the assistant
can use. We'll settle the exact mechanics at the start of implementation rather than
guessing now.

## Build order

1. **Scaffold + CI** — empty Compose shell, `:core` interfaces with zero implementations,
   Gradle wrapper, both workflows. Goal: a green CI run produces an installable APK that
   opens to an empty "no devices" screen on the user's real phone. This validates the
   entire toolchain before any protocol code exists — the most valuable early milestone
   given the assistant can't test locally at all.
2. **Samsung, end-to-end** — lowest-risk protocol, wired fully through discovery →
   pairing → persistence → D-pad control → reconnect-on-relaunch. Validates the `:core`
   abstraction and service/UI plumbing cheaply before investing in harder protocols.
3. **Fire TV** — medium risk, ADB protocol is fully documented and `adblib` gives a real
   starting point.
4. **Chromecast w/ Google TV (Android TV Remote v2)** — medium risk; mutual TLS is
   native-Java and the protobuf schema is directly vendorable, so mostly plumbing once
   the pattern is proven.
5. **Apple TV** — last, deliberately. It's the only module requiring a from-scratch port
   of real cryptographic protocol logic with no existing Kotlin/Java reference, so it's
   isolated as "one hard module" once everything else already works, rather than "one
   hard module plus an unproven UI/service skeleton at the same time." Budget this
   milestone significantly more time than the other three combined.
6. **Sync mode + polish** — wire the sync-mode toggle to the `SyncModeController`,
   reconnect/backoff handling when a device drops off Wi-Fi, error-state UI pass.

## Top risks (flagging explicitly, not blocking on)

- All four protocols are unofficial/reverse-engineered to some degree; a future TV/OS
  update from any vendor could change behavior with no warning — an inherent trait of
  this project, not a bug to eventually fix.
- Apple TV is by far the dominant risk (see above) — expect it to need multiple
  iteration passes against the user's real device.
- Samsung's self-signed-cert trust and Fire TV's ADB trust-on-first-use are both
  "trust whatever's on my LAN" security models — fine for a personal remote on a home
  network, worth the user knowingly accepting.
- `com.tananaev:adblib` hasn't been updated since 2021 — treated as a starting point,
  not a guarantee; vendoring fallback is documented if it breaks against current Fire OS.

## Verification

There is no way to verify device-facing behavior from this environment. Verification
happens in two layers:
1. **Per milestone, CI-level**: a green GitHub Actions run producing a downloadable debug
   APK is the automated check — confirms the project actually compiles and packages.
2. **Per milestone, user-level**: the user sideloads the APK from the Actions
   artifact/Release onto their phone and tests the newly added protocol against their
   real device on their home network, then reports back (works / pairing fails / wrong
   keycode / etc.) so the next iteration can fix real-world issues an emulator or unit
   test can't catch (mDNS flakiness, actual TV firmware quirks, on-device pairing UX).
