# FLUXPAD — Technical Feasibility Report

**Date:** 2026-11-17
**Scope:** Android 9+ phone as a real HID controller for Windows 10/11, Linux, macOS.
**Primary device:** Realme Narzo 50A.
**Evidence base:** Installed Android API 35 SDK (`android.jar` signatures inspected with `javap`), public Android documentation, USB-IF HID specification, Microsoft Win32 documentation, Linux kernel `uinput` documentation, Apple Core Graphics documentation.

> This report deliberately separates what is **proven by API surface**, what depends on **specific hardware/firmware**, what is **experimental**, and what is **impossible without root or vendor support**.
> No physical Android device or second computer was available during this audit. Hardware behavior is therefore **not** marked confirmed.

---

## 1. Bluetooth HID — CONFIRMED API, hardware-dependent registration

### 1.1 API evidence

`android.bluetooth.BluetoothHidDevice` exists in API 35 and has existed since API 28 (Android 9). Verified signatures:

```java
boolean registerApp(BluetoothHidDeviceAppSdpSettings sdp,
                    BluetoothHidDeviceAppQosSettings inQos,
                    BluetoothHidDeviceAppQosSettings outQos,
                    Executor executor,
                    BluetoothHidDevice.Callback callback);
boolean sendReport(BluetoothDevice device, int id, byte[] data);
boolean replyReport(BluetoothDevice device, byte type, byte id, byte[] data);
boolean reportError(BluetoothDevice device, byte error);
boolean connect(BluetoothDevice device);
boolean disconnect(BluetoothDevice device);
```

`BluetoothHidDeviceAppSdpSettings(String name, String description, String provider, byte subclass, byte[] descriptors)` carries the HID descriptor. Constants verified: `SUBCLASS1_COMBO = 0xC0`, `SUBCLASS1_KEYBOARD = 0x40`, `SUBCLASS1_MOUSE = 0x80`, `REPORT_TYPE_INPUT = 1`, `REPORT_TYPE_OUTPUT = 2`.

`BluetoothHidDevice.Callback` provides:
- `onAppStatusChanged(device, registered)`
- `onConnectionStateChanged(device, state)`
- `onGetReport(device, type, id, bufferSize)`
- `onSetReport(device, type, id, data)`
- `onSetProtocol(device, protocol)`
- `onInterruptData(device, reportId, data)`
- `onVirtualCableUnplug(device)`

### 1.2 What this means

- FLUXPAD can register a **genuine Bluetooth HID device** with an arbitrary composite report descriptor.
- The phone becomes the HID peripheral; the PC is the Bluetooth host. **No desktop application is required.**
- Mouse movement, buttons, wheel, pan, keyboard, modifiers, and consumer/media reports can be sent as standard HID reports.
- The desktop OS uses its own HID driver. There is no custom driver, no injected input, no Accessibility service.
- Host requests (e.g. keyboard LED output reports) can be answered through `onSetReport` / `replyReport`.

### 1.3 Hardware/firmware dependencies (NOT confirmed)

1. The OEM Bluetooth stack must expose the **HID Device** profile (`BluetoothProfile.HID_DEVICE`). Some Android builds only expose HID Host. If `getProfileProxy` fails, FLUXPAD shows **Bluetooth HID unavailable on this device** and explains why; it does not fake a connection.
2. SDP records are cached by the host. If the descriptor changes after the first pairing, the PC may need the phone removed and re-paired.
3. Some hosts require the phone to be discoverable/pairing-mode while FLUXPAD is registered.
4. Reconnection behavior after phone reboot, Bluetooth toggle, or host sleep differs by OEM and host. FLUXPAD exposes explicit reconnect and auto-reconnect, and reports real callback state only.
5. Report rate/throughput is firmware- and host-dependent. There is no public API to query it.

### 1.4 Permissions

- API ≤ 30: `BLUETOOTH`, `BLUETOOTH_ADMIN`.
- API 31+: `BLUETOOTH_CONNECT` (runtime), optionally `BLUETOOTH_SCAN` is **not** required for bonded-device use.
- No location permission is needed for connecting to an already-bonded computer.

### 1.5 Verdict

**Feasible and implemented with genuine HID.** Registration success on the Realme Narzo 50A is **hardware-dependent and not yet verified here**.

---

## 2. USB-C wired connection

A cable does not automatically make the phone a USB HID peripheral. Three distinct things are often conflated:

| Concept | What it actually is | FLUXPAD support |
|---|---|---|
| USB OTG | Phone acts as **USB host** (keyboard/mouse plugged into phone) | Not useful for controlling a PC |
| USB gadget/HID | Phone's device controller exposes a HID function to the PC | **Not available to apps on stock Android** |
| USB Accessory (AOA) | Phone acts as USB **accessory**; a host program speaks the AOA protocol over bulk endpoints | Implemented with optional receiver |

### 2.1 Direct USB HID gadget — UNSUPPORTED on stock Android

Android's public SDK exposes `UsbManager` for:
- the phone acting as **host** to USB devices, and
- the phone acting as a **USB accessory** to a host.

It does **not** expose a public API to configure the phone's USB device controller as a HID gadget. `UsbDeviceConnection`, `UsbInterface`, and `UsbEndpoint` describe devices attached *to* the phone, not functions exposed *by* the phone. Configfs gadget configuration (`/sys/kernel/config/usb_gadget`) is not writable by normal apps and would require root/system privileges.

**Therefore:** FLUXPAD will not claim direct USB HID. The capability probe returns `Unsupported` with the reason, and the Connection Centre directs the user to Bluetooth HID (no receiver) or USB Companion (receiver). This is a deliberate, documented fallback — not a silent substitution.

### 2.2 AOA — AVAILABLE API, host-dependent transport

Verified API surface:
- `UsbManager.ACTION_USB_ACCESSORY_ATTACHED`
- `UsbManager.getAccessoryList()`
- `UsbManager.openAccessory(UsbAccessory): ParcelFileDescriptor`
- `UsbManager.hasPermission(UsbAccessory)` / `requestPermission(UsbAccessory, PendingIntent)`
- Manifest `<usb-accessory>` filter with manufacturer/model/version metadata.

AOA mechanics:
1. The PC-side receiver sends AOA control requests (`GET_PROTOCOL` 51, `SEND_IDENTIFICATION` 52, `START_ACCESSORY` 53) to the phone.
2. The phone's gadget re-enumerates with the AOA VID/PID (`0x18D1:0x2D00` or `0x2D01`).
3. Android delivers the accessory to the app; the app opens it and exchanges framed data over the accessory file descriptor.
4. The receiver injects HID-equivalent input natively on the PC.

Limits:
- The phone must implement AOA in firmware (very common, but not guaranteed; `PackageManager.FEATURE_USB_ACCESSORY` is a hint, not proof).
- **Windows:** the phone must be bound to WinUSB (Zadig or a shipped INF). This replaces the MTP/ADB driver for that device while bound. Without it, the receiver cannot send control transfers. This is a real, documented limitation.
- **Linux:** a udev rule with `TAG+="uaccess"` grants access without root. Kernel driver detach may be required.
- **macOS:** libusb/IOKit access may require detaching the kernel driver; Accessibility permission is required for input injection. Not guaranteed.
- No AOA support on some locked-down OEM builds, and USB-C cables that are charge-only will never work.

### 2.3 USB Companion architecture chosen

```
Phone app (UsbAoaTransport)  ⇄  USB cable / AOA bulk pipe  ⇄  Rust receiver
                                                               ├─ Windows SendInput
                                                               ├─ Linux uinput
                                                               └─ macOS CGEvent
```

Authentication is application-level (PBKDF2 + HMAC challenge/response), because AOA provides no authentication. See §5.

### 2.4 Verdict

**Feasible as an optional receiver architecture.** Direct USB HID is **unsupported**; this is stated in the UI and documentation. AOA functionality is **hardware-dependent** and **not hardware-verified**.

---

## 3. Desktop receiver

| Platform | Input API | Permission / dependency | Feasibility |
|---|---|---|---|
| Windows 10/11 | `SendInput` (user32) | None for normal desktop sessions; may not affect elevated windows (UIPI) | High |
| Linux (X11/Wayland) | `/dev/uinput` virtual mouse/keyboard | User must have read/write access; udev rule + `input` group; **not root** | High, but Wayland compositor differences affect some shortcuts |
| macOS | `CGEventPost`, `CGEventCreateMouseEvent`, `CGEventCreateKeyboardEvent` | **Accessibility** permission; app must be trusted; sandbox not used | High with permission; denial handled gracefully |

USB access:
- Windows: WinUSB driver binding (Zadig/INF) for the AOA device. No libusb DLL required if statically linked.
- Linux: libusb + udev rule.
- macOS: libusb/IOKit; may require kernel driver detach.

No Electron, no Chromium, no Java runtime. Rust binaries link libusb statically and ship as standalone executables.

---

## 4. HID descriptor and report contract

Composite descriptor, one SDP record, three report IDs:

| ID | Report | Bytes (excluding ID) | Notes |
|---|---|---|---|
| 1 | Mouse | 9 | buttons, X i16, Y i16, wheel i16, pan i16 |
| 2 | Keyboard | 8 | modifiers, reserved, 6 keycodes; 1-byte LED output |
| 3 | Consumer | 2 | 16-bit usage, little-endian |

Report data passed to `BluetoothHidDevice.sendReport` **excludes** the report ID. The descriptor and encoders are kept in one module and unit-tested together. If a host rejects the descriptor, the failure is surfaced as an explicit error, not hidden.

Keyboard uses the USB HID Usage Tables (Keyboard/Keypad page 0x07), not Android key events. Character-to-usage mapping is explicit (US/UK), with unsupported characters reported to the user rather than silently dropped. IME text is never assumed to translate.

---

## 5. Security model

- Bluetooth HID pairing is handled by the OS Bluetooth stack. FLUXPAD does not intercept or store pairing keys.
- USB AOA is unauthenticated at the USB level; FLUXPAD adds:
  - one-time 8-character pairing code shown on the phone,
  - PBKDF2-HMAC-SHA256 (200,000 iterations) key derivation,
  - HMAC-SHA256 mutual challenge/response with fresh nonces on every connection,
  - rate limiting and immediate close on failure,
  - Android Keystore-wrapped key storage; receiver config file restricted to the user.
- No commands, file transfer, shell execution, or screen capture in the protocol. Input reports only.
- No analytics, ads, accounts, or cloud. No logging of key content.
- Receiver is a visible console application with explicit connection status; it never installs a service or starts silently.

Residual risk: the initial pairing code is short. It is used once over a physical cable; PBKDF2 slows offline guessing. This is documented, not overstated.

---

## 6. Capability matrix

| Capability | Android 9 | Android 10–11 | Android 12+ | Realme Narzo 50A |
|---|---|---|---|---|
| Bluetooth HID device registration | API available; OEM-dependent | same | same + runtime `BLUETOOTH_CONNECT` | **Unverified** |
| Composite mouse/keyboard/consumer | Expected | Expected | Expected | **Unverified** |
| Foreground connected-device service | Yes | Yes | FGS type rules from 14 | **Unverified** |
| USB AOA accessory | Firmware-dependent | Firmware-dependent | Firmware-dependent | **Unverified** |
| Direct USB HID gadget | No | No | No | No |
| Media consumer keys | Host-dependent | Host-dependent | Host-dependent | **Unverified** |

---

## 7. Recommended architecture (accepted)

1. **Bluetooth HID is the primary, no-install path.**
2. **USB Companion (AOA + Rust receiver) is the optional wired path**, clearly labeled hardware/driver-dependent.
3. **USB Direct HID is surfaced as unsupported**, with the technical reason, never simulated.
4. Transport abstraction keeps touchpad/keyboard/media independent of the active link.
5. Capability detection is mandatory before any connection attempt.
6. No hardware success claims without a real device test.

---

## 8. Open questions that only hardware can answer

1. Does the Narzo 50A firmware expose `BluetoothProfile.HID_DEVICE` and accept the composite descriptor?
2. Does its AOA implementation enumerate with `0x18D1:0x2D00/2D01` when the receiver sends the handshake?
3. Which Windows/Linux/macOS versions correctly consume the 16-bit relative axes and horizontal pan?
4. Does the host require re-pairing after descriptor changes?
5. Actual end-to-end latency and battery consumption on the Helio G35.
6. Does the Realme UI battery manager kill the foreground service; which OEM exemption is required?

These are tracked in `docs/TESTING.md` and remain **unverified** until tested.
