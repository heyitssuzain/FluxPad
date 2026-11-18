# Compatibility

This is the expected compatibility matrix, not a hardware-certified list. Items marked **AWAITING DEVICE** have not been run on physical hardware in this build environment.

## 1. Android

| Requirement | Detail |
|---|---|
| Minimum Android | 9 / API 28. `BluetoothHidDevice` itself was added in API 28. |
| Target/compile SDK | 35 |
| CPU architecture | No native libraries; the APK is architecture-independent. |
| Bluetooth | Required for Bluetooth HID. A Bluetooth stack must expose `BluetoothProfile.HID_DEVICE`. |
| USB accessory | Required for USB Companion. `android.hardware.usb.accessory` must be advertised. |
| Permission | `BLUETOOTH_CONNECT` on Android 12+. No location/scan permission. |
| Foreground service | Android 8+; connected-device type on Android 14+. |
| Storage | Private app storage only; no external storage permission. |

### OEM caveats

- Some OEM Bluetooth stacks expose the HID Device profile to the system but reject third-party app registration. FLUXPAD reports registration failure; it cannot override the stack.
- Some OEM USB implementations hide the AOA interface in “Charging only” mode. Choose File Transfer/PTP and unlock the phone.
- Battery managers may kill the app when the screen is off. FLUXPAD offers a foreground service and diagnostics for battery optimization, but cannot override an aggressive OEM policy.
- The primary test device, Realme Narzo 50A (Realme UI), is **AWAITING DEVICE** for both Bluetooth HID and AOA.

### Android version notes

| Version | Notes |
|---|---|
| 9 (API 28) | Minimum. HID Device API exists. No `BLUETOOTH_CONNECT` runtime permission yet. |
| 10–11 | Legacy Bluetooth permissions apply. Scoped storage does not affect FLUXPAD’s private files. |
| 12 (API 31) | `BLUETOOTH_CONNECT` runtime permission required. |
| 13 (API 33) | `POST_NOTIFICATIONS` runtime permission for the optional session notification. |
| 14 (API 34) | Foreground service type `connectedDevice` and `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission. |
| 15/16 | Built with compile/target 35; behavior depends on the OEM stack. Not hardware-verified. |

## 2. Bluetooth HID hosts

| Host | Expected | Status |
|---|---|---|
| Windows 10/11 | Windows Bluetooth settings → Add device; phone presents a composite HID. | AWAITING DEVICE |
| Linux (BlueZ + libinput) | Pair in desktop Bluetooth settings; HID device appears. | AWAITING DEVICE |
| macOS | Pair in System Settings → Bluetooth; keyboard/mouse/consumer device. | AWAITING DEVICE |

Bluetooth HID requires no FLUXPAD receiver. The host’s own Bluetooth stack performs pairing and encryption. Hosts cache SDP records; descriptor changes require re-pairing.

## 3. USB Companion hosts

| Host | USB access | Input backend | Extra requirements | Status |
|---|---|---|---|---|
| Windows 10/11 | WinUSB-compatible driver (Zadig or equivalent) | `SendInput` | Interactive desktop; USB driver binding may need to be repeated after AOA re-enumeration | AWAITING DEVICE |
| Linux | udev `uaccess` for phone + `/dev/uinput` | evdev/uinput | `input` group or equivalent; C toolchain to build receiver | AWAITING DEVICE |
| macOS | libusb/IOKit access | CoreGraphics | Accessibility permission; optional codesigning for distribution | AWAITING DEVICE |

### Receiver architecture

- Windows: x86_64 MSVC build in CI.
- Linux: x86_64 GNU build in CI.
- macOS: built on the current `macos-latest` GitHub runner; not a universal binary. A local build is the safest option.

Other architectures (ARM Linux, Windows on ARM, 32-bit) can be built from source but are not shipped by the default matrix.

## 4. Feature compatibility

| Feature | Bluetooth HID | USB Companion | Notes |
|---|---|---|---|
| Pointer move | Yes | Yes | Relative motion; 16-bit deltas, split by the controller. |
| 5 mouse buttons | Yes | Yes | Back/forward mapped per OS. |
| Vertical scroll | Yes | Yes | Natural-scroll setting. |
| Horizontal scroll/pan | Yes | Yes | Host support varies. |
| 6-key rollover keyboard | Yes | Yes | Real HID limit. |
| Modifier chords | Yes | Yes | Ctrl/Alt/Shift/GUI combinations. |
| Text typing | Yes | Yes | Multi-report sequence. |
| Consumer/media keys | Yes | Yes | Host mapping varies; unsupported usages produce a receiver warning. |
| Presentation keys | Yes | Yes | Normal keyboard usages. |
| Decks/shortcuts | Yes | Yes | Stored locally. |
| Foreground session | Yes | Yes | Started only after Connected. |
| Direct USB HID | No | No | Not a stock Android capability. |
| Control UAC/lock screen | No | No | OS security boundary. |
| Control BIOS/boot menus | No | No | HID stack not available before Android boot; AOA requires Android. |

## 5. Known limitations

- Bluetooth HID availability is OEM-stack dependent, even on API 28+.
- USB Companion is not plug-and-play on Windows; WinUSB binding is a manual, potentially disruptive step.
- macOS requires Accessibility permission and may ignore synthetic input in secure fields.
- AOA reports are authenticated but not encrypted; use a trusted cable/host.
- The app has no internet permission, so no remote support, cloud sync, or telemetry.
- Reduced motion is supported in the UI, but the operating system still receives real input reports; it is not a way to disable host animations.
- The app does not use Accessibility services, so it cannot automate the phone UI itself.

## 6. Compatibility reporting

When reporting a compatibility result, include:

- Phone model, Android version, OEM/ROM build.
- Transport (Bluetooth HID or USB Companion).
- Host OS/version, Bluetooth adapter, USB cable/port.
- For USB: `fluxpad-receiver --list` output, driver/permission setup, and `--no-inject` result.
- Exact FLUXPAD APK version/build and receiver version.
- Whether the result is a build/type-check only or a physical test.

Never mark a transport as verified from a build log alone.
