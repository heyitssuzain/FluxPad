# FLUXPAD — Progress

> Living status. Updated 2026-11-19.
>
> **Environment note:** A Realme RMX3430 (Android 13) on USB debugging and a Windows 11 host are now attached, so the Bluetooth HID path is hardware-verified. Linux, macOS, USB AOA and the desktop receiver remain `AWAITING DEVICE`. Build logs are not hardware evidence; the hardware section below records what was actually observed.

Legend: ✅ done and verified in this environment · 🟡 implemented, not hardware-verified · ⏳ in progress · ⛔ blocked/unsupported · ⬜ not started

## Build environment

| Item | Status | Detail |
|---|---|---|
| JDK | ✅ | Microsoft OpenJDK 17.0.20 |
| Android SDK | ✅ | Minimal CLI SDK: platform 35, build-tools 35 + 36, platform-tools |
| Gradle | ✅ | 9.8, wrapper included |
| Android build | ✅ | `:app:assembleDebug` and `:app:assembleRelease` succeed |
| Android lint | ✅ | `testDebugUnitTest lintDebug` completes with warnings only |
| Rust toolchain | ✅ | `cargo`/`rustc` on Windows; GNU host target |
| Rust pure tests | ✅ | Protocol/shared/integration tests pass |
| Android device | ⛔ | None attached |
| Linux host | ⛔ | Not available; cross-target type-check only |
| macOS host | ⛔ | Not available; cross-target type-check only |
| Emulator | ⛔ | Intentionally not installed |

## Milestone status

| # | Milestone | Status | Notes |
|---:|---|---|---|
| 1 | Feasibility report | ✅ | `docs/FEASIBILITY.md` covers API evidence, AOA, direct-USB-HID impossibility, platform requirements. |
| 2 | Android foundation + design system | ✅ | Five-module Gradle project; Compose theme, custom glyphs, Canvas visuals, reduced-motion support. |
| 3 | Bluetooth HID | 🟡 | Verified on Windows 11: registration, HID node enumeration, cursor, clicks, two-finger scroll and keystrokes. Honest link state, auto-reopen, adapter off/on handling and manual re-pair recovery documented. Linux/macOS still untested. |
| 4 | Touchpad + keyboard | ✅ | Full-screen pad, coalescing, sensitivity/acceleration, two-finger scroll, three-finger tap, two-/four-finger navigation swipes with configurable actions, 6KRO keyboard, chords, text typing. Gesture kinematics and report mapping unit-tested. |
| 5 | Media + presentation + decks + shortcuts | ✅ | Screens and input mappings implemented; deck persistence and shortcut parsing tested. |
| 6 | USB Companion (Android side) | 🟡 | AOA accessory open/permission/read/write, PBKDF2/HMAC pairing, Keystore storage, heartbeat. No hardware test. |
| 7 | Rust receiver | 🟡 | CLI, authenticated session, native Windows/Linux/macOS backends, pairing store. CI builds and unit-tests all three native targets. Hardware input injection is still unverified. |
| 8 | Premium UI / accessibility | ✅ | Dark premium UI, custom Canvas animation, reduced-motion setting, no fake state. |
| 9 | Build + release workflow | 🟡 | Android and desktop CI are green. `v1.1.0` is published with a debug APK fallback; production signing still needs secrets. |
| 10 | Documentation | ✅ | README, architecture, protocol, USB compatibility, testing, privacy, signing, release, compatibility. |
| 11 | Hardware verification | 🟡 | Bluetooth HID verified on Windows 11; Linux/macOS HID, USB AOA and the native receivers still `AWAITING DEVICE`. |

## Verified build results

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed; `app-debug.apk` produced |
| `:app:assembleRelease` | Passed; `app-release-unsigned.apk` produced (no local keystore) |
| `testDebugUnitTest` | Passed (`core:input`, `core:model`; `:app` has no test sources) |
| `lintDebug` | Passed; warnings only (SDK availability, AGP deprecations, older Compose BOM) |
| `cargo test -p fluxpad-protocol -p fluxpad-shared -p fluxpad-tests` | Passed |
| `cargo check -p fluxpad-windows --all-targets` | Passed |
| `cargo check --target x86_64-unknown-linux-gnu -p fluxpad-linux --all-targets` | Passed (type-check only) |
| `cargo check --target x86_64-apple-darwin -p fluxpad-macos --all-targets` | Passed (type-check only) |
| `cargo check -p fluxpad-usb -p fluxpad-receiver` | Passed against the published `rusb` API; a temporary local type-check stub was required, then removed. |
| Real libusb/receiver link | **Not run locally.** Local Windows GNU toolchain lacks `gcc`/`as`/`cc1`; CI is the authoritative build. |
| GitHub Actions Android | **Passed** in run [44250241158](https://github.com/heyitssuzain/FluxPad/actions/runs/44250241158): tests, lint, debug APK, artifact upload. |
| GitHub Actions desktop matrix | **Passed** in run [44250241171](https://github.com/heyitssuzain/FluxPad/actions/runs/44250241171): Linux, Windows, and macOS receiver builds plus native backend unit tests. |
| Debug APK signature | `apksigner verify --print-certs` passed; debug certificate only, not a release identity. |
| Release workflow | **Passed** for tag `v1.0.0`: published [FLUXPAD v1.0.0](https://github.com/heyitssuzain/FluxPad/releases/tag/v1.0.0) with a debug APK, three receiver binaries, and `SHA256SUMS.txt`. Signing secrets were not configured, so the APK is the debug fallback. |
| Release asset verification | Downloaded `fluxpad-1.0.0-debug.apk` from the release; SHA-256 matches `SHA256SUMS.txt` (`091831bd…32d8e`); `apksigner verify` passes with the Android debug certificate. |

## What is implemented

### Android

- Onboarding with honest capability probes and minimal permissions.
- Dashboard, Connection, Touchpad, Keyboard, Media, Presentation, Deck, Shortcuts, Settings, Privacy, Diagnostics, About.
- Real `BluetoothHidDevice` transport:
  - HID Device profile proxy and app registration.
  - Composite report descriptor: mouse ID 1, keyboard ID 2, consumer ID 3.
  - Host-initiated reconnect handling, virtual cable unplug, 12-second connect timeout.
  - No Accessibility-service input.
- Real AOA transport:
  - Accessory intent/permission handling, shared `ParcelFileDescriptor`, read loop, heartbeat.
  - Pairing code UI, PBKDF2-HMAC-SHA256, HMAC challenge/response, replay-resistant nonces.
  - Keystore-backed AES-GCM storage for pairing keys.
- `UsbDirectHidTransport` always reports unsupported. No fake toggle.
- Foreground `SessionService` starts only after Connected.
- Manual DI (`AppContainer`), shared `AppViewModel`/`DeckViewModel`.
- Custom design system and Canvas signal visual; no Lottie/GIF fake animation.

### Rust

- `fluxpad-protocol`: exact frame layout, Java-compatible CRC32, message parsing, PBKDF2/HMAC tags, constant-time comparisons, tests.
- `fluxpad-shared`: HID report parsing, pressed-key/button diffing, link trait, pairing store, tests.
- `fluxpad-usb`: AOA detection, identification strings, mode switch, bulk framing, device listing.
- `fluxpad-windows`: `SendInput` mouse/keyboard/consumer backend.
- `fluxpad-linux`: `evdev`/`uinput` virtual controller.
- `fluxpad-macos`: CoreGraphics keyboard/mouse and raw scroll-wheel event.
- `fluxpad-receiver`: CLI options, auth state machine, heartbeat, input session, release-on-disconnect.
- `fluxpad-tests`: cross-crate protocol/store/report tests.

## 1.1.0 hardware results — Windows 11 + Realme RMX3430

| Check | Result |
|---|---|
| HID app registration | ✅ `onAppStatusChanged isRegistered=true` after a Bluetooth toggle cleared a stale registration. |
| Windows HID nodes | ✅ `Bluetooth HID Device`, `HID-compliant mouse`, `HID Keyboard Device`, `HID-compliant consumer control device` all `OK`. |
| Cursor, clicks, keyboard | ✅ Reports received by Windows; cursor and typed keys act on the host. |
| Two-finger scroll | ✅ Implemented and exercised on the pad; a fast vertical flick is held for swipe recognition, otherwise it flushes as wheel. |
| Two-/four-finger swipes | 🟡 Unit-tested (12 tests); multi-touch cannot be injected over `adb`, so the host actions were verified by test + a real two-finger swipe. |
| Failure found | Broken Bluetooth link key: Windows `BTHUSB` Event 16 + `Bluetooth HID Device` Code 10. Re-pairing fixed it; FLUXPAD now reports the drop honestly and reopens the channel instead of showing a fake LIVE. |
| Recovery found | A force-stop during a session leaves a dead HID registration; turning Bluetooth off/on once restores `registerApp`. Documented in `docs/TROUBLESHOOTING.md`. |

## Hardware verification — still required

These are **not** passed. They are the next milestone.

### Bluetooth HID

- [x] Realme RMX3430 exposes `BluetoothProfile.HID_DEVICE`.
- [x] Windows 11 pairs and accepts the composite HID device.
- [ ] Linux BlueZ/libinput accepts it.
- [ ] macOS accepts it.
- [x] Mouse axes, buttons and keyboard typing on Windows.
- [ ] Media keys and presentation keys on every host.
- [x] Disconnect/reconnect, Bluetooth-off, host-rejects-connect timeout, honest drop reporting.
- [x] No Accessibility service is used.

### USB Companion

- [ ] Realme AOA accessory attach/intent/permission path.
- [ ] Windows WinUSB binding before and after AOA re-enumeration.
- [ ] Linux udev USB access and `/dev/uinput` virtual device.
- [ ] macOS Accessibility permission/refusal behavior.
- [ ] First pairing, saved-key reconnect, wrong code, `--forget`, cable pull, heartbeat timeout.
- [ ] Native input injection on all three hosts.

### Release

- [x] First GitHub Actions run succeeds (Android + Rust matrix).
- [ ] Signed APK built from repository secrets.
- [x] `apksigner verify` and checksum verification on the `v1.0.0` release APK (debug certificate).
- [ ] Receiver binaries run `--help`/`--version` on their target OSes (CI builds them, but does not execute them).

## Known blockers and risks

- No real hardware, so Bluetooth HID/AOA/input injection cannot be certified.
- The local Rust GNU toolchain cannot link vendored libusb (no C compiler); CI/MSVC must validate the real receiver build.
- Realme UI may hide AOA in charging-only mode or reject third-party HID registration.
- Windows WinUSB binding is the least plug-and-play part of USB Companion and may disrupt MTP/ADB.
- macOS requires Accessibility permission and can ignore synthetic input in secure fields.
- Hosts cache Bluetooth SDP records; descriptor changes require re-pairing.
- The local release APK is unsigned; signing secrets are not present in this workspace.
- Without signing secrets, the release workflow publishes a debug APK fallback; that is not a production signing identity.

## Next actions

1. Install the CI-built debug APK on the Realme and record the hardware checklist results.
2. Configure `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`, then run the release workflow for a signed APK.
3. Run the receiver binaries on real Windows/Linux/macOS hosts and verify input injection, permissions, pairing, reconnect, and cable-pull behavior.
4. Only then change `AWAITING DEVICE` rows to verified in this file and the compatibility docs.
