# USB Compatibility and Host Setup

> **Read this before blaming the APK.** USB Companion is an Android Open Accessory (AOA) link, not USB HID. It depends on the phone’s USB controller/firmware, the cable, the OS driver, and host permissions. None of those paths has been verified on physical hardware in this build environment. The status is **AWAITING DEVICE**.

## 1. The three USB ideas, separated

| Idea | Wire reality | Stock Android support | FLUXPAD behaviour |
|---|---|---|---|
| **USB Direct HID** | Phone enumerates as a USB HID mouse/keyboard gadget; host needs no driver | **No public API.** Android’s USB APIs cover host mode and accessory mode, not a phone-side HID gadget. | Capability is always `UNAVAILABLE`; no fake toggle or fake success. Requires root/vendor firmware, which is out of scope. |
| **USB Companion (AOA)** | Phone enumerates as an Android Open Accessory; a receiver on the PC exchanges a private framed protocol and injects input | AOA is part of Android since 3.1; phones normally expose the AOA interface in file-transfer/PTP modes | Implemented. Requires a receiver and host driver/permission setup. |
| **USB host mode** | Phone controls a USB device (keyboard, mouse, drive) | Supported on many phones via `UsbManager` host APIs | Not the FLUXPAD direction. FLUXPAD controls the computer, not the other way round. |

AOA does **not** make the phone a USB HID device. The computer’s OS sees a vendor-specific accessory interface; only the FLUXPAD receiver turns the received reports into native input.

## 2. Phone-side requirements

- Android 9+ (the app’s `minSdk 28`).
- `android.hardware.usb.accessory` advertised by the firmware. The app checks this and reports “USB accessory mode is not advertised by this device” if it is missing.
- A **data-capable** USB cable. Many charge-only cables have no D+/D− pairs.
- The phone unlocked when attaching, because Android must show/authorize the accessory and launch FLUXPAD.
- USB configuration set to a mode that exposes AOA. On most phones this is **File transfer / Android Auto** or **PTP**; “Charging only” often hides the AOA interface. The exact label depends on Realme UI/ColorOS.
- USB debugging is **not required** for AOA, but enabling Developer options can make the USB configuration menu available on some OEM builds. It does not grant FLUXPAD extra privileges.

The app declares a USB accessory intent filter with:

```
manufacturer = FLUXPAD
model        = FLUXPAD Receiver
version      = 1.0
```

The receiver sends exactly these strings during AOA identification. A mismatched/third-party accessory is rejected with an error instead of being treated as a FLUXPAD receiver.

### Realme Narzo 50A caveat

The primary test device is a Realme Narzo 50A. Its AOA behaviour and Realme UI’s handling of the accessory re-enumeration are **not verified**. If the phone repeatedly re-enumerates or the receiver cannot open the accessory interface, capture the output of:

```bash
fluxpad-receiver --list
```

and the Android `adb logcat` USB/UsbManager lines. Do not assume the Bluetooth path has the same limitations; they are independent.

## 3. Windows 10/11

### 3.1 What the receiver needs

The receiver uses libusb (`rusb`) to open the phone, send AOA control requests, and read/write bulk endpoints. Windows does not provide a generic libusb-accessible driver for arbitrary Android interfaces. The relevant device must be bound to a **WinUSB-compatible** driver (WinUSB or libusbK).

### 3.2 Typical setup with Zadig

1. Install the receiver and plug in the phone with a data cable.
2. Set the phone’s USB mode to file transfer/PTP.
3. Open Zadig as Administrator.
4. Enable **Options → List All Devices**.
5. Select the Android device/interface used by AOA. Confirm the USB ID. The phone’s normal vendor ID is OEM-specific; after `START_ACCESSORY` it re-enumerates as Google `18d1:2d00` or `18d1:2d01`.
6. Install **WinUSB** (or libusbK) for that interface.
7. Run `fluxpad-receiver.exe --list` and confirm the device is visible.
8. Start the receiver and connect from FLUXPAD.

**Important:** replacing an MTP/ADB driver with WinUSB can disable file transfer or ADB for that device. Windows may also need a second binding after the phone re-enumerates as the Google accessory. Keep a note of the original driver and use Device Manager → **Uninstall device** / “Roll Back Driver”, or reinstall the OEM/MTP driver, when finished. FLUXPAD does not ship or install a kernel driver.

### 3.3 Windows limitations

- The receiver must run in the interactive user session; `SendInput` cannot control the UAC secure desktop or the lock screen.
- A WinUSB binding may survive reconnects for the same USB port/device but can be reset by Windows updates or a different port.
- Some corporate endpoint-protection tools block unsigned USB driver changes. That is an environment policy, not a FLUXPAD bug.
- `--no-inject` is the safe first test: it proves AOA + authentication without injecting input.

## 4. Linux

### 4.1 Packages

The receiver builds against vendored libusb. A C toolchain and headers are needed:

```bash
# Debian/Ubuntu
sudo apt install build-essential pkg-config libudev-dev

# Fedora
sudo dnf install gcc make pkgconf-pkg-config systemd-devel

# Arch
sudo pacman -S base-devel pkgconf systemd-libs
```

Build:

```bash
cd desktop
cargo build --release -p fluxpad-receiver
```

### 4.2 USB access

The receiver needs read/write access to the phone’s USB device. A udev rule can grant the active desktop user access. Example (`/etc/udev/rules.d/70-fluxpad.rules`):

```udev
# Google AOA accessory after the mode switch
SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", ATTR{idProduct}=="2d00", TAG+="uaccess"
SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", ATTR{idProduct}=="2d01", TAG+="uaccess"

# Replace 2d00 with your phone's vendor/product if your distro hides the
# pre-switch interface from the active user. Verify with `lsusb`.
# SUBSYSTEM=="usb", ATTR{idVendor}=="<vendor>", ATTR{idProduct}=="<product>", TAG+="uaccess"
```

Reload:

```bash
sudo udevadm control --reload-rules
sudo udevadm trigger
```

Unplug/replug the phone. `TAG+="uaccess"` grants the logged-in seat user access; it is preferable to world-writable `MODE="0666"`.

### 4.3 `/dev/uinput`

The Linux input backend creates a virtual kernel input device named **FLUXPAD Virtual Controller**. The user must have write access to `/dev/uinput`:

```udev
KERNEL=="uinput", SUBSYSTEM=="misc", MODE="0660", GROUP="input", OPTIONS+="static_node=uinput"
```

Add the user to the `input` group and log out/in, or use an equivalent `uaccess` rule:

```bash
sudo usermod -aG input "$USER"
```

Ensure the module is available:

```bash
sudo modprobe uinput
```

If the receiver starts but says it cannot open `/dev/uinput`, this is a permission problem; it never pretends to have a virtual device.

### 4.4 Linux limitations

- The virtual device is a kernel input device, so it works in X11, Wayland, games, and native applications, subject to the compositor’s input policy.
- Wayland security policies may restrict synthetic input in some sessions; a kernel uinput device is generally treated like real hardware, but this is environment-dependent.
- Some games use raw input/anti-cheat and may ignore or flag virtual devices. Test per application.
- `/dev/uinput` access is powerful. Grant it only to trusted users.

## 5. macOS

### 5.1 Build

Install Xcode command line tools, then:

```bash
cd desktop
cargo build --release -p fluxpad-receiver
```

The macOS backend uses CoreGraphics; no kernel extension is required.

### 5.2 Accessibility permission

macOS requires the process posting synthetic events to be trusted for Accessibility:

1. Open **System Settings → Privacy & Security → Accessibility**.
2. Add the receiver binary, or add the terminal application that launches it.
3. Enable it.
4. Restart the receiver.

The receiver checks `AXIsProcessTrusted()` at startup and refuses to start without permission. This is intentional: CoreGraphics otherwise silently drops events, which would look like a successful connection with no effect.

Some fields marked as “secure input” (password prompts, some banking apps) ignore synthetic events by design. That is a macOS security boundary, not a FLUXPAD failure.

### 5.3 macOS limitations

- The binary is ad-hoc/unsigned by default when built locally. Gatekeeper may quarantine a downloaded binary; use a locally built binary or follow the signing notes in [RELEASE.md](RELEASE.md).
- AOA USB access generally works through IOKit/libusb, but a vendor-specific interface could be claimed by another driver. `--list` and `--no-inject` are the diagnostics.
- Hardware verification for the macOS backend is **AWAITING DEVICE**.

## 6. Cables, hubs, and ports

- Use a known data cable. Try a direct port rather than an unpowered hub.
- USB-C-to-USB-C cables can behave differently from USB-A-to-USB-C; test both if possible.
- Some phones only expose AOA after unlocking and selecting a file-transfer mode.
- A charging-only cable or a charge-only hub will produce “no device” or repeated disconnects. The receiver reports these honestly.
- Do not connect through a KVM or a port that strips data.

## 7. Diagnostic sequence

1. **Phone:** connect the cable; confirm the phone notices it and offers a USB mode.
2. **Host:** run `fluxpad-receiver --list` and look for the phone/Google accessory entry.
3. **Host + phone:** start `fluxpad-receiver --no-inject`; on the phone tap **Connect USB**.
4. Enter the pairing code. Confirm the receiver prints `Authenticated` and `--no-inject` is active.
5. Stop and restart without `--no-inject` to test actual input.
6. If authentication works but input does not, the problem is the native input backend (WinUSB is no longer relevant at that point; uinput/Accessibility is).
7. If AOA never reaches authentication, the problem is USB mode/driver/accessory permission, not the HID descriptor.

## 8. What FLUXPAD will not claim

- It will not claim USB Direct HID works on stock Android.
- It will not claim USB Companion is plug-and-play on Windows.
- It will not claim macOS input works without Accessibility permission.
- It will not add a fake “connected” state when the OS has not confirmed a link.
- It will not hide driver or permission failures behind a generic success toast.

The truthful fallback is always available: **Bluetooth HID**, which requires no receiver, no USB driver, and no cable, but depends on the phone’s Bluetooth stack exposing the HID Device role.
