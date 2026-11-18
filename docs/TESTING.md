# Testing and Verification

> **Hardware status: AWAITING DEVICE.** No Android phone, no Linux host, and no macOS host were attached to the build environment. Build/test results below are real for this workspace, but they are not hardware certification.

## 1. Verification matrix

| Area | Command / method | Local result | Hardware status |
|---|---|---|---|
| Android debug build | `./gradlew :app:assembleDebug` | Passed | Install not run |
| Android release build | `./gradlew :app:assembleRelease` | Passed; unsigned local APK | Signed CI build not run |
| Android debug APK signature | `apksigner verify --print-certs` | Passed; Android debug certificate, not a release identity | N/A |
| Android unit tests | `./gradlew testDebugUnitTest` | Passed (`core:input`, `core:model`; `:app` has no test sources) | N/A |
| Android lint | `./gradlew lintDebug` | Passed; warnings only (SDK/version/deprecation) | N/A |
| Rust protocol/shared/tests | `cargo test -p fluxpad-protocol -p fluxpad-shared -p fluxpad-tests` | Passed | N/A |
| Rust Windows backend | `cargo check -p fluxpad-windows --all-targets` | Passed | Injection not run |
| Rust USB/receiver | `cargo check -p fluxpad-usb -p fluxpad-receiver` | Passed against the published `rusb` API using a temporary local type-check stub; real `rusb`/libusb receiver builds passed in CI on all three hosts | USB not run |
| Rust Linux backend | `cargo check --target x86_64-unknown-linux-gnu -p fluxpad-linux --all-targets` | Passed (cross-target type-check) | uinput not run |
| Rust macOS backend | `cargo check --target x86_64-apple-darwin -p fluxpad-macos --all-targets` | Passed (cross-target type-check) | CoreGraphics not run |
| Real libusb link/build | `cargo build -p fluxpad-receiver` on a host with a C toolchain | **Passed in CI** on Linux, Windows, and macOS; not run locally because the Windows GNU toolchain has no `gcc`/`as`/`cc1` | CI is authoritative |
| GitHub Actions Android | `.github/workflows/android.yml` | Passed: tests, lint, debug APK, artifact upload ([run](https://github.com/heyitssuzain/FluxPad/actions/runs/44250241158)) | Install not run |
| GitHub Actions desktop | `.github/workflows/desktop.yml` | Passed: Linux/Windows/macOS receiver builds and native backend tests ([run](https://github.com/heyitssuzain/FluxPad/actions/runs/44250241171)) | Injection not run |
| Release workflow | `.github/workflows/release.yml` | Written; not triggered until a `v*` tag is pushed | Pending |

The temporary type-check stub is not part of the repository and is not a runtime shim. The final workspace uses the real crates.io `rusb` dependency and a real `Cargo.lock`.

## 2. Android commands

Set the environment first (Windows PowerShell example):

```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

Run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug :app:assembleDebug
```

Release APK:

```powershell
.\gradlew.bat :app:assembleRelease
# android/app/build/outputs/apk/release/app-release-unsigned.apk
```

The local release APK is unsigned by design because no production keystore is committed. See [SIGNING.md](SIGNING.md).

### Unit test coverage

- `ShortcutParserTest`: shortcut grammar/validation.
- `KeyboardMapperTest`: printable keys, modifiers, chords, key names, layout behavior.
- `DeckPresetTest`: built-in decks and deck constraints.
- `HidReportsTest`: mouse/keyboard/consumer byte layouts, clamping, 6-key rollover.
- `AoaProtocolTest`: frame encode/decode, CRC, bad magic/length/CRC rejection, strings.
- `AoaCryptoTest`: HMAC-SHA256 RFC 4231 vector, PBKDF2 salt/code behavior, pairing code alphabet/length, constant-time comparison.

`fluxpad-tests` adds Rust cross-crate tests for frame layout, HELLO/transcript shape, report parsing, pairing-store round-trip/rotation, and error decoding.

## 3. Rust commands

```bash
cd desktop

# Pure crates: no libusb C build needed
cargo test -p fluxpad-protocol -p fluxpad-shared -p fluxpad-tests

# Windows backend
cargo check -p fluxpad-windows --all-targets

# Full receiver build (needs MSVC on Windows, build-essential + libudev-dev on Linux,
# Xcode command line tools on macOS)
cargo build --release -p fluxpad-receiver
```

On Linux, install `libudev-dev`/`pkg-config` before building. On Windows, use the MSVC Rust toolchain, not the GNU toolchain used by this build environment.

### Cross-target type checks

These validate that the Linux/macOS code compiles without a host machine:

```bash
rustup target add x86_64-unknown-linux-gnu x86_64-apple-darwin
cargo check --target x86_64-unknown-linux-gnu -p fluxpad-linux --all-targets
cargo check --target x86_64-apple-darwin -p fluxpad-macos --all-targets
```

A cross-target `cargo check` is **not** a runtime test. It cannot prove uinput permission behavior, CoreGraphics trust, or that a real key event reaches an application.

## 4. Hardware test checklist — Bluetooth HID

Use a physical Android phone and a second computer. Record OS version, phone model, and OEM Bluetooth stack.

### 4.1 Registration

- [ ] Fresh install; grant only the requested Bluetooth permission.
- [ ] Onboarding/diagnostics reports the HID Device profile accurately.
- [ ] Tap Connect with Bluetooth off → visible “Bluetooth is off” error, no fake connected state.
- [ ] Pair the computer in the computer’s Bluetooth UI; it appears as a bonded device.
- [ ] Register HID and connect; host shows a mouse/keyboard/consumer device.
- [ ] Disconnect from host side → app returns to Disconnected.
- [ ] Turn Bluetooth off mid-session → app reports an error and does not keep sending.
- [ ] Timeout path: connect to a host that does not accept → 12-second error, no stuck spinner.

### 4.2 Input fidelity

- [ ] Mouse pointer moves in all four directions; no axis inversion.
- [ ] Left/right/middle/back/forward buttons.
- [ ] Vertical and horizontal scroll; natural-scroll setting.
- [ ] Sensitivity and acceleration feel; no runaway motion.
- [ ] Left-handed swap only swaps primary buttons as documented.
- [ ] Type all letters, digits, punctuation, Shift/Ctrl/Alt/GUI combinations.
- [ ] Six-key rollover: a seventh key is a real HID limitation; no crash.
- [ ] Arrow/navigation/function keys.
- [ ] Media keys: play/pause, next/previous, volume, mute.
- [ ] Presentation keys (arrows, Page Up/Down, F5/Home/End) in a slide deck.
- [ ] Deck taps and shortcut chords.
- [ ] Release all on disconnect: no stuck key or mouse button.

### 4.3 Host OS notes

- Windows: test Bluetooth HID without the receiver installed.
- Linux: test on a desktop session; check `libinput`/`evtest` if input is odd.
- macOS: test text fields and media; note secure input fields that ignore events.

## 5. Hardware test checklist — USB Companion

### 5.1 Pre-flight

- [ ] `fluxpad-receiver --list` sees the phone/Google accessory.
- [ ] Phone is unlocked and in file-transfer/PTP mode.
- [ ] Cable is data-capable and directly connected.
- [ ] Windows has a WinUSB-compatible binding; Linux has USB and uinput access; macOS has Accessibility permission.

### 5.2 First pairing

- [ ] Start `fluxpad-receiver --no-inject`.
- [ ] Tap Connect USB; the accessory permission prompt appears if needed.
- [ ] Phone shows an 8-character code; receiver prompts for it.
- [ ] Enter the code; receiver prints Authenticated.
- [ ] Kill/restart receiver and reconnect: saved key authenticates without a code.
- [ ] `--forget` rotates the receiver id and forces a new code.
- [ ] Wrong code: phone reports a real failure; no input is accepted.
- [ ] Wrong phone key after receiver reset: real authentication failure, no silent fallback.
- [ ] Unplug cable: both sides disconnect cleanly; no stuck keys.

### 5.3 Input

Repeat the Bluetooth input checklist with `--no-inject` off. Pay particular attention to:

- [ ] Windows UAC/lock screen is intentionally not controllable.
- [ ] Linux `/dev/uinput` creation: `libinput list-devices` or `evtest` shows “FLUXPAD Virtual Controller”.
- [ ] macOS Accessibility refusal: remove permission and confirm the receiver refuses to start rather than silently doing nothing.
- [ ] Media keys and scroll direction on each OS.
- [ ] Reconnect after cable pull releases all pressed state.

## 6. Regression checks without hardware

- [ ] `git diff --check` (whitespace).
- [ ] `./gradlew testDebugUnitTest lintDebug` after any UI/transport change.
- [ ] `cargo test -p fluxpad-protocol -p fluxpad-shared -p fluxpad-tests`.
- [ ] `cargo check` for every target that can be compiled.
- [ ] Search for fake states: `SUPPORTED`, `Connected` constructions, placeholder buttons, hardcoded “device found”.
- [ ] Confirm no internet permission, no analytics, no accessibility service.
- [ ] Confirm no keystore/APK/secrets are committed.

## 7. Known untested areas

- Realme Narzo 50A HID Device role and Realme UI behavior.
- Android AOA accessory launch/permission on Realme UI.
- Windows WinUSB binding across phone re-enumeration.
- Linux uinput permission in the target desktop session.
- macOS Accessibility trust and secure input behavior.
- Bluetooth HID on each OEM Bluetooth stack.
- CI runner build of the vendored libusb dependency.
- Signed release APK produced from GitHub Actions.

Do not upgrade any row in [progress.md](../progress.md) from `AWAITING DEVICE` without a recorded physical test.
