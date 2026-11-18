# FLUXPAD — Engineering Plan

> **YOUR PHONE. YOUR COMPUTER. ONE CONTROL SYSTEM.**

Status: **source/build implementation complete; CI green and v1.0.0 published; hardware verification and production signing pending**
Owner: FLUXPAD engineering
Primary device: Realme Narzo 50A (Android 11 / Realme UI 2.0 expected)
Minimum Android: 9 (API 28) — Bluetooth HID device profile itself requires API 28.
Last updated: 2026-11-18

---

## 1. Product definition

FLUXPAD turns an Android phone into a real computer input device.

Three transport modes, each honest about what it can do:

| Mode | How | Desktop receiver | Status |
|---|---|---|---|
| **Bluetooth HID** | Phone registers as a genuine Bluetooth HID composite device (mouse + keyboard + consumer control) using `BluetoothHidDevice` | Not required | Core feature. API 28+. Host pairing required. |
| **USB Direct HID** | Phone exposes a USB HID gadget over the cable | Not required | **Unavailable on stock Android.** Android exposes no public USB gadget/HID API. Never faked. |
| **USB Companion** | Phone communicates with a small Rust receiver over Android Open Accessory (AOA) | Required | Implemented, hardware/driver dependent. See `docs/USB_COMPATIBILITY.md`. |

The app never shows a connected state that the OS has not reported.

---

## 2. Feasibility summary

Full report: [`docs/FEASIBILITY.md`](docs/FEASIBILITY.md).

### Confirmed (documented public APIs)
- `BluetoothHidDevice.registerApp()` + `BluetoothHidDeviceAppSdpSettings` exist in the installed API 35 SDK and have existed since API 28.
- Composite HID descriptor (mouse + keyboard + consumer) is supported by the HID profile registration API, which accepts an arbitrary descriptor byte array.
- `BluetoothHidDevice.sendReport(device, reportId, data)` sends genuine HID reports; `onConnectionStateChanged`, `onSetReport`, `onGetReport`, `onAppStatusChanged` provide connection and host-request handling.
- Android's `UsbManager` exposes `ACTION_USB_ACCESSORY_ATTACHED`, `getAccessoryList()`, `openAccessory()` and accessory permission APIs. AOA is therefore reachable without root.
- Windows `SendInput`, Linux `uinput`, and macOS `CGEvent` are documented native input-injection paths.

### Hardware-dependent
- Whether a specific OEM firmware implements the HID **device** role (many do; some do not). Capability probe and graceful unsupported state are mandatory.
- Whether the phone's USB gadget exposes AOA accessory mode and whether the PC can open the phone's USB device (Windows needs WinUSB binding; Linux needs udev; macOS may need kernel-driver detachment).
- Realme Narzo 50A behavior. **No hardware is attached to this development environment, so no on-device claim is made.**

### Experimental
- USB Companion on Windows (requires replacing the phone's USB driver with WinUSB via Zadig or an INF; this disables MTP/ADB while bound).
- macOS USB Companion (IOKit/libusb device access + Accessibility permission).
- Media consumer controls on hosts that ignore consumer HID pages.

### Unsupported (by design, not by omission)
- Direct USB HID gadget on unrooted stock Android.
- Accessibility-service mouse emulation (explicitly rejected — it is not HID).
- Arbitrary shell command execution from control-deck buttons.
- Universal USB HID claims of any kind.

---

## 3. Technology choices

| Layer | Choice | Why |
|---|---|---|
| Android UI | Kotlin 2.4.x, Jetpack Compose, Material 3, custom design system | Required stack; no heavy UI framework |
| Async | Coroutines + `StateFlow` | Standard, testable |
| DI | Manual application container | Small dependency surface, explicit lifetimes |
| Persistence | DataStore Preferences + AES-GCM via Android Keystore | Typed settings, no DB dependency |
| Bluetooth | `BluetoothHidDevice` | The only genuine HID device API |
| USB (phone) | `UsbManager` AOA accessory streams | Only stock-compatible USB data path |
| Desktop | Rust, std + tiny crates (`sha2`, `hmac`, `pbkdf2`, `rusb` vendored) | No Electron/Java/Chromium |
| Desktop input | Win32 `SendInput`, Linux `uinput`, macOS `CGEvent` | Native, low latency |
| CI | GitHub Actions (Android + desktop matrix + signed release) | APK without local Android Studio |

**Explicitly excluded:** Unity, Unreal, Electron, WebView UI, Hilt, Room, Retrofit, analytics SDKs, ad SDKs, large 3D engines.

---

## 4. Repository layout

```
Fluxpad/
  android/
    settings.gradle.kts
    build.gradle.kts
    gradle/libs.versions.toml
    app/                        # Compose UI, navigation, DI, services
    core/
      model/                    # Pure Kotlin models, HID usages, shortcuts, protocol model
      input/                    # Transport abstraction, HID encoders, BT HID, AOA, metrics
      data/                     # DataStore settings, deck profiles, pairing vault
      design/                   # Theme, tokens, components, custom Canvas visuals
    app/src/test/               # JVM unit tests (encoders, parser, protocol)
  desktop/
    Cargo.toml                  # Workspace
    protocol/                   # Framing + auth (tested on host)
    shared/                     # HID->native key tables, input model
    usb/                        # AOA handshake / bulk transport
    windows/                    # Win32 SendInput receiver
    linux/                      # uinput receiver + udev rule
    macos/                      # CGEvent receiver
    tests/                      # Protocol integration tests
  docs/
    FEASIBILITY.md
    USB_COMPATIBILITY.md
    ARCHITECTURE.md
    TESTING.md
    PRIVACY.md
    SIGNING.md
    RELEASE.md
    COMPATIBILITY.md
  screenshots/                  # Real captures only (placeholder notes until device test)
  .github/workflows/
    android-build.yml
    desktop-build.yml
    release.yml
  README.md
  LICENSE
  CONTRIBUTING.md
  plan.md
  progress.md
```

---

## 5. Android architecture

```
UI (Compose screens)
   │ intents / events
ViewModels (StateFlow)
   │
RemoteInputController ──────────► TransportCoordinator ──► InputTransport
   │                                    │                    ├─ BluetoothHidTransport
   │                                    │                    ├─ UsbAoaTransport
   │                                    │                    └─ UsbDirectHidTransport (capability probe, unsupported)
   │                                    └─ ConnectionState StateFlow
   ├─ SettingsRepository (DataStore)
   ├─ DeckRepository (DataStore + kotlinx.serialization)
   └─ PairingVault (AES-GCM / Keystore)
```

Rules:
- UI never calls a transport directly.
- Every transport implements the same interface and reports real state.
- On disconnect, all modifiers/buttons are reset and an empty report is sent when possible.
- Mouse reports are coalesced off the UI thread; no decorative animation is allowed on the input path.
- The input controller measures real dispatch latency; no fabricated latency/signal numbers in the UI.

### HID descriptor contract
One composite report descriptor, three report IDs:
- `1` Mouse: buttons (5 bits + 3 pad), X/Y int16 relative, vertical wheel int16, horizontal pan int16.
- `2` Keyboard: 8-bit modifiers, reserved, 6-key rollover; 5-bit LED output report.
- `3` Consumer: 16-bit usage array (play, next, prev, volume, mute, etc.).

Encoders live in `core/input` and are unit-tested against the descriptor. Report data passed to `sendReport` excludes the report ID.

### Transport lifecycle
- HID app is registered when the user starts a session or auto-reconnect is armed.
- A foreground service (`connectedDevice`) keeps an active session alive and displays real status.
- No polling loops; callbacks + `StateFlow`.
- Auto-reconnect: host-initiated for BT HID; receiver-initiated for USB AOA.

---

## 6. Desktop receiver architecture

```
AOA USB bulk pipe
   │ framed, authenticated messages (FLXP v1)
   ▼
protocol crate (decode, validate, CRC, rate limit, auth)
   ▼
shared crate (HID usage → native key, modifiers, mouse deltas)
   ▼
platform backend:
   windows  SendInput
   linux    /dev/uinput virtual mouse + keyboard
   macos    CGEventPost (Accessibility permission)
```

Security:
- Receiver is visible (console window), never silent.
- First connection: phone shows an 8-character pairing code; receiver asks the user to type it.
- `PBKDF2-HMAC-SHA256(code, salt, 200k)` derives a 256-bit key.
- Subsequent connections: HMAC-SHA256 challenge/response with nonces; failed auth closes the pipe and rate-limits.
- No commands, no file access, no shell. Input reports only.
- Keys stored in a per-user config file; Android key wrapped by Keystore.

---

## 7. Design system summary

- Palette: `#090B0F` base, `#11151B` secondary, `#171D25` elevated, `#42D9FF` accent, `#8B9DFF` secondary accent, `#F4F7FA` text, `#8D99A8` muted, restrained green/amber for status only.
- Cyan used for active/primary signals, never as a wash.
- Typography: Roboto/system sans for UI, monospace for technical labels; display tracking tight, labels tracked wide and uppercase.
- Surfaces: 20dp radius, 1px top-light hairline, soft dark shadow, no cheap glass.
- Motion: spring-based presses (0.96 scale), 220–420ms screen transitions, staged reveals, subtle 3D `graphicsLayer` parallax. Reduced-motion path removes travel/parallax and shortens fades.
- Custom Canvas visuals: logo mark, connection scene, touchpad surface, signal waves, deck texture. No 3D engine.
- Touch targets ≥48dp, screen-reader descriptions for canvas surfaces, haptics never the only feedback.

---

## 8. Milestones

| # | Milestone | Deliverable | Status |
|---|---|---|---|
| 1 | Feasibility | `docs/FEASIBILITY.md`, this plan | ✅ Honest capability matrix; no unverified claims |
| 2 | Foundation + design system | Gradle project, theme, components, navigation | ✅ `assembleDebug` + unit tests pass |
| 3 | Bluetooth HID | Descriptor, encoders, transport, capability probe | 🟡 Compiled; encoder tests pass; **AWAITING DEVICE** for HID role/host pairing |
| 4 | Touchpad + keyboard | Gesture engine, key layers, modifiers, shortcuts | ✅ Input path complete; mapping/report tests pass; **AWAITING DEVICE** for end-to-end input |
| 5 | Media + presentation | Consumer controls, platform-aware shortcuts | 🟡 Real HID/consumer reports only; **AWAITING DEVICE** for host mapping |
| 6 | Premium dashboard + control deck | Custom Canvas connection scene, deck profiles, persistence | ✅ UI complete; reduced motion honored; **AWAITING DEVICE** for visual performance |
| 7 | USB + receiver | AOA transport, Rust receiver, auth protocol | 🟡 Protocol tests pass; USB/receiver type-checked; **AWAITING DEVICE** for real libusb/USB |
| 8 | Platform backends | Windows/Linux/macOS input injection | 🟡 Windows type-checks; Linux/macOS cross-target type-check; **AWAITING DEVICE** at runtime |
| 9 | Testing + optimization | Test report, lifecycle fixes | 🟡 Build/unit/lint checks pass; hardware checklist in `docs/TESTING.md`; hardware unverified |
| 10 | CI + signing + release | Workflows, signing docs, artifacts | 🟡 Workflows/docs written; not executed; local release unsigned. Signed APK requires repository secrets |

Every milestone: compile → test → update `progress.md` → note what is **not** verified on hardware.

## 8.1 Current delivery gates

1. CI: run the Android, Desktop, and Release workflows on GitHub.
2. Real libusb/MSVC/Apple builds must pass in CI; the local GNU toolchain cannot link vendored libusb.
3. Install the APK on the Realme Narzo 50A and execute the Bluetooth/AOA hardware checklists.
4. Configure signing secrets and verify a signed release APK.
5. Only then upgrade hardware rows from `AWAITING DEVICE`.

---

## 9. Acceptance policy

The final report will mark each item in the user's acceptance list as:
- **Verified in this environment** (build/test only, no hardware),
- **Implemented, awaiting hardware verification**, or
- **Blocked by platform/hardware** with the reason.

No successful Bluetooth/USB hardware test will be claimed without a real device and host.

---

## 10. Known risks

1. OEM Bluetooth stacks may reject HID device registration or omit the HID device profile. Mitigation: profile proxy check, explicit error state, diagnostics.
2. Windows may need the phone re-paired after the HID SDP record changes. Mitigation: documented remove/re-pair procedure.
3. AOA on Windows requires WinUSB binding, which breaks MTP/ADB. Mitigation: documented, optional, never automatic.
4. Compose on low-end devices (Narzo 50A, Helio G35) needs restrained effects. Mitigation: custom Canvas, no blur-heavy surfaces, reduced-motion path, input off the UI thread.
5. Android 14+ foreground-service restrictions. Mitigation: start from foreground UI, `connectedDevice` type, honest notification.
6. Build environment has no device/emulator; hardware behavior remains unverified until the user runs the test checklist.
