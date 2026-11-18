# FLUXPAD

**Your phone. Your computer. One control system.**

FLUXPAD turns an Android phone into a real input device for Windows, Linux, and macOS. The primary path is a genuine Bluetooth HID composite device built on `BluetoothHidDevice` — the computer needs no FLUXPAD desktop app, driver, or Accessibility service. A second, honest wired path uses Android Open Accessory (AOA) plus a small native Rust receiver when Bluetooth is not suitable.

> **Verification status:** the Android app builds, the unit tests and lint pass locally, and the Rust protocol/shared crates pass tests. Bluetooth HID, USB AOA, and every OS input backend still require real hardware. They are marked **AWAITING DEVICE**, not “confirmed working.” See [progress.md](progress.md) and [docs/TESTING.md](docs/TESTING.md).

---

## Transports

| Transport | How it works | Desktop software | Status |
|---|---|---|---|
| **Bluetooth HID** | Android registers a real HID device (mouse + 6-key keyboard + consumer control) over `BluetoothHidDevice`. The PC is the Bluetooth host. | None | Implemented. Hardware verification pending. Requires Android 9+ and a Bluetooth stack that exposes the HID Device role. |
| **USB Companion** | The phone and a native Rust receiver speak the FLUXPAD AOA protocol over a USB cable. The receiver injects input with native OS APIs. | FLUXPAD receiver | Implemented. Requires AOA support, a data cable, and a host driver/permission setup. Hardware verification pending. |
| **USB Direct HID** | The phone would enumerate as a USB HID gadget with no receiver. | None | **Not available on stock Android.** Android exposes no public phone-as-USB-HID-gadget API. FLUXPAD reports it as unavailable and does not fake it. Root/vendor firmware is out of scope. |

No path uses Android Accessibility services to move the mouse or type on the host.

---

## What you need

### Phone

- Android 9 (API 28) or newer.
- Bluetooth for the Bluetooth HID path; a USB data cable and AOA support for USB Companion.
- The `BLUETOOTH_CONNECT` runtime permission on Android 12+. FLUXPAD does **not** request `BLUETOOTH_SCAN`, location, internet, or accessibility permissions.

### Computer

- **Bluetooth HID:** Windows 10/11, a modern Linux desktop, or macOS. Pair the computer with the phone in the OS Bluetooth settings.
- **USB Companion:** Windows 10/11, Linux, or macOS. The receiver is a Rust binary; see [docs/USB_COMPATIBILITY.md](docs/USB_COMPATIBILITY.md) before connecting.

---

## Install the Android APK

### From GitHub Releases

Download the APK attached to a release of this repository.

- A **signed release APK** is published when the repository’s signing secrets are configured.
- Otherwise the release contains a **debug APK**. It is installable and useful for testing, but it is not a Play Store-style signed production artifact.

Always verify the source before installing an APK. Android will warn about installing apps from a browser or file manager; that warning is expected for sideloaded builds.

### Build locally

Requirements: JDK 17, Android SDK platform 35, build-tools 36.0.0. The Gradle wrapper downloads Gradle 9.8.

```bash
cd android
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # Windows: sdk.dir=C\:\\...\\Android\\Sdk
./gradlew :app:assembleDebug
```

The APK is at `android/app/build/outputs/apk/debug/app-debug.apk`.

Windows PowerShell:

```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

Build and test everything:

```bash
cd android
./gradlew testDebugUnitTest lintDebug :app:assembleDebug
```

A signed release build requires `android/keystore.properties` and a keystore; see [docs/SIGNING.md](docs/SIGNING.md).

---

## Use Bluetooth HID

1. Turn on Bluetooth on both devices.
2. Pair the phone and computer in the **computer’s** Bluetooth settings. Accept the pairing on both sides.
3. Open FLUXPAD → **Connection**. The bonded computer appears under **Bluetooth HID**.
4. Tap **Connect**. The first connection can take several seconds; the app waits up to 12 seconds for the host to confirm.
5. Use **Touchpad**, **Keyboard**, **Media**, **Presentation**, **Deck**, and **Shortcuts**.

The phone is the HID peripheral. If the computer does not complete the HID connection, the app reports a real timeout instead of pretending to be connected. Some OEM Bluetooth stacks do not expose the HID Device role; the onboarding/diagnostics screen shows that result honestly.

### Bluetooth notes

- The HID report descriptor is a composite device with report IDs `1` (mouse), `2` (keyboard), `3` (consumer control).
- Keyboard rollover is 6 keys plus modifiers. This is a real HID limit, not a UI bug.
- Hosts cache SDP records. If a future FLUXPAD release changes the descriptor, remove the old pairing on the host and pair again.
- The app does not need a desktop receiver for this path.

---

## Use USB Companion

> **Read the driver and permission details first:** [docs/USB_COMPATIBILITY.md](docs/USB_COMPATIBILITY.md). USB Companion is not plug-and-play on every OS. It is a real AOA link, not USB HID on the wire.

### 1. Build the receiver

```bash
cd desktop
cargo build --release -p fluxpad-receiver
```

The binary is `target/release/fluxpad-receiver` (`fluxpad-receiver.exe` on Windows).

Prerequisites:

- Rust stable 1.78+.
- Linux: a C toolchain plus `libudev-dev`/`pkg-config` for the vendored libusb build.
- Windows: Visual Studio Build Tools (MSVC) for the Rust toolchain and vendored libusb.
- macOS: Xcode command line tools.

### 2. Prepare the host

- **Windows:** bind the Android USB device to a WinUSB-compatible driver (for example with Zadig). The phone may re-enumerate as a Google AOA accessory and may need a second binding. This can disrupt MTP/ADB; use a dedicated cable/port or restore the original driver when finished.
- **Linux:** allow access to the phone’s USB device and `/dev/uinput`; see the udev example in the compatibility guide. The receiver creates a kernel input device named “FLUXPAD Virtual Controller”.
- **macOS:** grant **Accessibility** permission to the receiver (or the terminal that launches it). Without it, macOS silently drops synthetic events and the receiver refuses to start.

### 3. Connect

1. Start the receiver on the computer:
   ```bash
   ./target/release/fluxpad-receiver
   ```
2. On the phone, open FLUXPAD → **Connection** → **USB Companion** → **Connect USB**.
3. Connect the USB data cable. The receiver switches the phone into AOA accessory mode. The Android app opens automatically when the accessory is attached and permitted.
4. On first use, the phone shows an 8-character pairing code. Enter it in the receiver terminal.
5. On later connections, the saved key authenticates both sides automatically.

If something fails, run `fluxpad-receiver --list` to inspect USB devices and `fluxpad-receiver --no-inject` to test authentication without moving the desktop.

### Receiver options

| Option | Meaning |
|---|---|
| `--list` | List visible USB devices and AOA/accessory markers. |
| `--forget` | Delete the saved pairing key and rotate the receiver id. The phone must pair again. |
| `--pair-code CODE` | Pair non-interactively with a known code. The code is visible in shell history/process lists; prefer the interactive prompt. |
| `--name NAME` | Override the receiver name shown on the phone. |
| `--no-inject` | Authenticate and log only; input is discarded. Useful for bringing up USB/driver issues safely. |
| `--help`, `--version` | Standard help/version. |

---

## Security and privacy

- FLUXPAD has **no internet permission** and no analytics. Nothing is uploaded.
- Bluetooth HID pairing is handled by Android and the host OS. FLUXPAD stores no Bluetooth keys.
- USB Companion authentication:
  - 8-character code from a 32-symbol alphabet (no ambiguous `0/O`, `1/I`).
  - PBKDF2-HMAC-SHA256, 200,000 iterations, 256-bit key.
  - HMAC-SHA256 challenge/response with separate phone, receiver, pairing, and pairing-confirmation tags.
  - Fresh 128-bit nonces and a receiver id; replay-resistant.
  - The Android key is stored only as an AES-GCM blob wrapped by a non-exportable Android Keystore key.
- AOA itself does **not** encrypt traffic. After authentication, input reports travel over the USB cable unencrypted. Use a trusted cable and host; do not treat AOA as a network security boundary.
- The receiver never asks for a password and never sends the saved key over the wire.

See [docs/PRIVACY.md](docs/PRIVACY.md) and [docs/PROTOCOL.md](docs/PROTOCOL.md).

---

## Repository layout

```
android/
  app/                 Compose UI, onboarding, screens, foreground session service
  core/model/          Shared models, shortcut parser, keyboard mapper, decks
  core/input/          Bluetooth HID, AOA transport, capability detection, report encoding
  core/data/           DataStore settings, deck storage, Keystore-backed pairing vault
  core/design/         Flux design system and custom Canvas visuals
desktop/
  protocol/            AOA framing, CRC32, messages, PBKDF2/HMAC authentication
  shared/              Input abstraction, HID report parsing, link trait, pairing store
  usb/                 libusb AOA host transport
  windows/             SendInput backend
  linux/               evdev/uinput backend
  macos/               CoreGraphics backend
  receiver/            CLI receiver and authenticated session loop
  tests/               Cross-crate protocol/store integration tests
docs/                  Architecture, compatibility, testing, privacy, signing, release
.github/workflows/     Android and Rust CI/release workflows
```

---

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Wire protocol](docs/PROTOCOL.md)
- [USB compatibility and host setup](docs/USB_COMPATIBILITY.md)
- [Testing and the hardware verification checklist](docs/TESTING.md)
- [Privacy](docs/PRIVACY.md)
- [Signing the Android APK](docs/SIGNING.md)
- [Release process](docs/RELEASE.md)
- [General compatibility](docs/COMPATIBILITY.md)
- [Original feasibility report](docs/FEASIBILITY.md)
- [Engineering plan](plan.md)
- [Live progress and honest status](progress.md)

---

## Limitations

- Direct USB HID from a stock Android phone is not possible with public APIs. No FLUXPAD UI toggle exists for it.
- USB Companion depends on the phone’s AOA implementation, the cable, and the host driver. Windows WinUSB binding is the least plug-and-play part.
- macOS requires Accessibility permission; some secure input fields intentionally ignore synthetic events.
- Windows secure desktop (UAC, lock screen) cannot be controlled by `SendInput`.
- Linux input appears as a kernel virtual device; Wayland/X11 does not matter, but `/dev/uinput` access does.
- The HID descriptor exposes a 6-key rollover keyboard and five mouse buttons; this is intentional hardware-faithful behavior.
- Bluetooth HID availability varies by OEM Bluetooth stack. The app reports what the phone actually exposes.

---

## License

Apache-2.0. See [LICENSE](LICENSE).
