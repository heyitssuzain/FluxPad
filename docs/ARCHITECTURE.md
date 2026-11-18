# FLUXPAD Architecture

> Status: implementation complete at the source/build level. Bluetooth HID, USB AOA, and native input injection are **AWAITING DEVICE**. This document describes the intended and implemented architecture, not a hardware-confirmed result.

## 1. System overview

FLUXPAD has two independent input paths.

```
                         ┌──────────────────────────────┐
                         │ Android app (Kotlin/Compose) │
                         │                              │
 Gestures / keys ───────►│ RemoteInputController        │
                         │   ├─ BluetoothHidTransport   │──────── Bluetooth HID ──────► PC host
                         │   └─ UsbAoaTransport         │──────── USB AOA ────────────► Rust receiver
                         └──────────────────────────────┘                                  │
                                                                                            ├─ Windows SendInput
                                                                                            ├─ Linux uinput
                                                                                            └─ macOS CoreGraphics
```

- **Bluetooth HID** is the primary path. The phone is the HID peripheral; the computer is the Bluetooth host. The host needs no FLUXPAD software.
- **USB Companion** is the wired fallback. It is Android Open Accessory, not USB HID. A native receiver on the computer authenticates the phone and injects input through the host OS.
- **USB Direct HID** is modelled only as an unavailable capability. Stock Android has no public API for the phone to enumerate as a USB HID gadget.

The UI and input code never know which transport is real beyond the `InputTransport` interface and a `ConnectionState` flow. A transport may only emit `Connected` after the OS confirms a link.

## 2. Android module graph

```
:app
 ├─ :core:design   (theme, components, custom Canvas visuals)
 ├─ :core:data     (settings, decks, Keystore pairing vault)
 ├─ :core:input    (transports, capability detection, HID/AOA report code)
 └─ :core:model    (models, shortcut parser, keyboard mapper)
```

Dependency direction is one-way: `app` depends on all core modules; core modules do not depend on `app`.

### 2.1 `core:model`

- Device and transport models (`ComputerDevice`, `TransportKind`, `ConnectionState`).
- `DeviceCapabilities` for honest diagnostics.
- `HidKey`, `HidModifier`, `KeyboardLayout`, `KeyboardMapper`.
- `KeyChord` and the shortcut parser.
- Deck presets and deck models.

The keyboard mapper is pure Kotlin and unit tested. It maps printable characters and key names to HID usages, including modifier folding and 6-key rollover behaviour.

### 2.2 `core:input`

| Type | Responsibility |
|---|---|
| `InputTransport` | The only transport contract: lifecycle, state flow, mouse/keyboard/consumer reports, release. |
| `TransportCoordinator` | Selects one active transport, exposes its live state, and routes reports without pretending delivery. |
| `BluetoothHidTransport` | Real `BluetoothHidDevice` registration, connection, timeout, and report sending. |
| `UsbAoaTransport` | Real AOA accessory open/read/write, authentication state machine, heartbeat, and pairing UI events. |
| `UsbDirectHidProbe` / `UsbDirectHidTransport` | Always-unavailable capability; returns failure and never sends. |
| `CapabilityDetector` | Probes Bluetooth, HID profile proxy, USB accessory/host features, notifications, battery optimization. |
| `BluetoothDeviceLister` | Only bonded devices, only with `BLUETOOTH_CONNECT`; returns empty otherwise. |
| `RemoteInputController` | Coalesces pointer motion on one dedicated thread; applies sensitivity, acceleration, natural scroll, left-handed buttons, keyboard/chord/text/media dispatch. |
| `HidReportDescriptor` / encoders | Composite report descriptor and byte-exact mouse/keyboard/consumer reports. |
| `UsbAoaTransport` helpers (`AoaProtocol`, `AoaCrypto`, `AoaPairingStore`) | Frame protocol, PBKDF2/HMAC proofs, and an interface for Keystore-backed storage. |

`RemoteInputController` uses a dedicated single-thread executor and a channel. Pointer moves are coalesced only when reports are already queued; there is no artificial polling timer or fake smoothing. Reports return `false` when the active transport is not connected.

### 2.3 `core:data`

- `SettingsRepository`: DataStore-backed preferences (sensitivity, scroll, acceleration, natural scroll, left-handed, reduced motion, theme preference, onboarding completion).
- `DeckRepository`: local JSON/datastore persistence for user decks.
- `PairingVault`: AES-GCM encryption with a non-exportable Android Keystore key. Stored blobs live in private SharedPreferences. Plaintext pairing keys never touch disk and are never logged.

### 2.4 `core:design`

- `FluxTheme`, mottled dark background, accent cyan, typography, spacing, and shape tokens.
- Custom `FluxGlyph` icons drawn with Canvas/Path; the app intentionally does not depend on the extended Material icon pack.
- Reusable cards, dialogs, buttons, pills, sliders, headers, empty states, warning cards, and `FluxFeedback` haptics.
- A custom Canvas “signal deck” visual and reduced-motion-aware animation.

### 2.5 `:app`

- `FluxpadApplication` creates the manual dependency graph (`AppContainer`).
- `MainActivity` hosts Compose and forwards USB accessory intents.
- `AppViewModel` owns connection operations, capability refresh, bonded-device refresh, pairing prompts, and user-facing errors.
- `DeckViewModel` owns deck editing/execution.
- `SessionService` is a real foreground service started only after `Connected`; it keeps the process alive and offers a disconnect action.
- Navigation destinations: Onboarding, Dashboard, Connection, Touchpad, Keyboard, Media, Presentation, Deck, Shortcuts, Settings, Privacy, Diagnostics, About.

## 3. Bluetooth HID path

### 3.1 Registration

`BluetoothHidTransport.start()`:

1. Requires `BLUETOOTH_CONNECT` on Android 12+.
2. Verifies the adapter is enabled.
3. Obtains the `BluetoothProfile.HID_DEVICE` proxy.
4. Calls `registerApp()` with `BluetoothHidDeviceAppSdpSettings`.
5. Waits up to 8 seconds for `onAppStatusChanged(isRegistered = true)`.
6. Registers an adapter-state receiver so Bluetooth being turned off becomes an honest error.

The SDP name/description/provider are FLUXPAD. Subclass is `SUBCLASS1_COMBO`.

### 3.2 Connection

`connect(target)` requires a bonded device. It calls `hidDevice.connect(device)` and waits for the HID callback:

- `STATE_CONNECTING` → `Connecting`.
- `STATE_CONNECTED` → `Connected`.
- 12-second timer → honest error and disconnect attempt.
- `onVirtualCableUnplug` or `STATE_DISCONNECTED` → `Disconnected`.
- Bluetooth off → error.

The host must initiate/accept pairing in its Bluetooth UI. FLUXPAD lists only devices Android already trusts and does not create bonds.

### 3.3 Composite descriptor

Report IDs:

| ID | Report | Bytes (excluding report ID) |
|---|---|---|
| 1 | Mouse | `[buttons][x i16][y i16][wheel i16][pan i16]` = 9 |
| 2 | Keyboard | `[modifiers][reserved][6 keys]` = 8 |
| 3 | Consumer | `[usage u16 little-endian]` = 2 |
| 2 (output) | Keyboard LEDs | 1 byte (5 LED bits + 3 padding) |

The descriptor declares five mouse buttons, relative 16-bit X/Y/wheel/AC Pan, an 8-bit modifier field, six key slots, and a consumer array up to usage `0x3FF`. Hosts cache SDP records; descriptor changes require re-pairing on the host.

### 3.4 Failure honesty

- No fake `Connected` state.
- `onGetReport` returns zeroed real reports and uses `reportError` for unsupported IDs.
- Host LED output is acknowledged by doing nothing (HID has no SET_REPORT response).
- `registerApp()` rejection or timeout becomes a visible error, not a silent fallback.
- The app never claims boot-protocol support beyond accepting `SET_PROTOCOL`.

## 4. USB Companion path

### 4.1 AOA roles

The **receiver is the USB host**; the **phone is the accessory**. The receiver:

1. Finds an Android device with an AOA interface (`class 0xFF`, subclass `0x42`, protocol `0x01`).
2. Reads the AOA protocol version (must be ≥ 1).
3. Sends the six AOA identification strings: manufacturer `FLUXPAD`, model `FLUXPAD Receiver`, description `FLUXPAD USB Companion`, version `1.0`, project URI, serial = receiver id.
4. Sends `START_ACCESSORY`.
5. Waits for the device to re-enumerate as Google accessory VID `0x18D1`, PID `0x2D00` or `0x2D01`.
6. Claims the AOA accessory interface (vendor-specific, subclass 0, protocol 0) and uses its bulk IN/OUT endpoints.

Android launches the app through the `USB_ACCESSORY_ATTACHED` intent filter and accessory metadata matching the same manufacturer/model/version. The app requests accessory permission if needed, opens the `ParcelFileDescriptor`, and starts read/heartbeat coroutines.

### 4.2 Frame protocol

All integers are little-endian. The header is 16 bytes:

```
magic "FLXP" | version u8 | type u8 | flags u16 | seq u32 | payload length u32
payload[length]
crc32 u32
```

CRC-32 covers `version..payload` and excludes the magic and the CRC field. Both sides use the standard CRC-32 polynomial (`java.util.zip.CRC32` compatible). Payload is capped at 4096 bytes.

Message types: `CHALLENGE=1`, `HELLO=2`, `PAIR_AUTH=3`, `PAIR_OK=4`, `AUTH_OK=5`, `MOUSE=10`, `KEYBOARD=11`, `CONSUMER=12`, `PING=13`, `PONG=14`, `BYE=15`, `ERROR=16`.

See [PROTOCOL.md](PROTOCOL.md) for exact layouts.

### 4.3 Authentication

The receiver starts each session with a `CHALLENGE` containing its stable receiver id, a fresh 16-byte nonce, and its display name.

**Existing pairing**

1. The phone loads the saved key for that receiver id.
2. Phone sends `HELLO(paired=1, phoneNonce, zeroSalt, proof=HMAC(key, TAG_PHONE, transcript), model)`.
3. Receiver validates the phone proof and replies `AUTH_OK(HMAC(key, TAG_RECEIVER, transcript))`.
4. The phone validates the receiver proof and marks the session authenticated.

**First pairing**

1. The phone generates a random 8-character code and a 16-byte salt, derives a key with PBKDF2-HMAC-SHA256 (200,000 iterations, 256 bits), displays the code, and sends an unpaired `HELLO`.
2. The receiver prompts for the code shown on the phone and derives the same key.
3. Receiver sends `PAIR_AUTH(HMAC(key, TAG_PAIR, transcript))`; phone validates.
4. Phone stores the key (AES-GCM/Keystore) and replies `PAIR_OK(HMAC(key, TAG_PAIR-OK, transcript))`; receiver validates, saves, and the session is live.

Transcript = `receiverId || receiverNonce || phoneNonce || salt`, with tags `FLUXPAD-PHONE`, `FLUXPAD-RECEIVER`, `FLUXPAD-PAIR`, `FLUXPAD-PAIR-OK`. Comparisons are constant-time. `forget` rotates the receiver id so an old phone key cannot authenticate.

### 4.4 Session and heartbeat

After authentication:

- Phone sends input reports.
- Both sides send `PING` every 5 seconds and reply with `PONG`.
- The receiver fails the session after 20 seconds without inbound traffic.
- `BYE` ends the session cleanly; `ERROR` carries a human-readable failure.
- The receiver calls `release_all` on the input sink before reconnecting, so keys/buttons cannot remain stuck after a cable pull.

AOA provides no encryption. Authentication prevents an unpaired device/process from injecting input, but reports are not encrypted on the wire.

### 4.5 Receiver CLI and store

The receiver has no GUI framework, Electron, Java, or Chromium. It is a Rust binary with a small CLI and native backends.

- Stable receiver id plus hex key in:
  - Windows: `%APPDATA%\FLUXPAD`
  - Linux: `~/.config/fluxpad`
  - macOS: `~/Library/Application Support/FLUXPAD`
- `--forget` deletes the key and rotates the id.
- `--no-inject` uses a null sink so authentication/USB bring-up can be tested without moving the desktop.
- `--list` enumerates USB devices and AOA markers.

## 5. Native input backends

| Platform | Mechanism | Requirements / limits |
|---|---|---|
| Windows | `SendInput` with scan-code keyboard events and mouse/wheel events | Interactive desktop only; cannot control UAC/lock screen. USB needs a WinUSB-compatible driver. |
| Linux | `evdev`/`uinput` virtual device named “FLUXPAD Virtual Controller” | Needs write access to `/dev/uinput`; no X11/Wayland dependency. |
| macOS | CoreGraphics `CGEvent` for keyboard/mouse/scroll, including raw `CGEventCreateScrollWheelEvent2` | Requires Accessibility permission; secure input/password fields may ignore events. |

The shared layer parses the Android HID-shaped reports and tracks pressed state so a reconnect or cable pull releases everything.

## 6. Android UI and state

- The visual language is a premium dark control surface: `#090B0F`, accent `#42D9FF`, restrained borders, custom glyphs, custom Canvas signal visual.
- Motion is implemented with Compose animation and Canvas, not GIFs/Lottie or a fake “live” indicator.
- Reduced motion is a first-class setting and also follows the system animation scale where available.
- Onboarding asks only for `BLUETOOTH_CONNECT` and optionally `POST_NOTIFICATIONS`; USB permission is requested by the accessory flow when needed.
- The Session foreground service starts only after `Connected`, and stops on disconnected/error.
- Diagnostics shows real capability probes and real input metrics; no invented success states.

## 7. Build and release

- Android: AGP 9.5.1, Gradle 9.8, Kotlin 2.4.10, compile/target SDK 35, min SDK 28, Compose BOM 2025.06.01.
- Rust: workspace edition 2021, MSRV 1.78, libusb via the `rusb` vendored feature.
- GitHub Actions:
  - Android test/lint/debug build.
  - Rust test/build matrix on Windows/Linux/macOS.
  - Tag release with signed APK when secrets exist and receiver binaries for the three OSes.
