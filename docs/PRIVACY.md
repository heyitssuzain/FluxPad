# Privacy

FLUXPAD is local-only. It has no account, cloud, analytics, advertising, crash-upload, or telemetry component.

## 1. Android permissions

### Declared

| Permission | Why |
|---|---|
| `android.permission.BLUETOOTH_CONNECT` | Required on Android 12+ to list bonded computers, register the HID device, and send HID reports. |
| `android.permission.BLUETOOTH` / `BLUETOOTH_ADMIN` (`maxSdkVersion=30`) | Legacy Bluetooth access on Android 9–11. |
| `android.permission.VIBRATE` | Optional haptic feedback. |
| `android.permission.POST_NOTIFICATIONS` | Optional; shows the foreground session notification on Android 13+. |
| `android.permission.FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Keeps the process alive while a real input session is active. |

### Not declared

- **No `INTERNET` permission.** The app cannot open a network socket, upload data, or phone home.
- **No `BLUETOOTH_SCAN`.** FLUXPAD does not scan for unpaired devices; it lists only devices Android already bonded.
- **No location permission.** No `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`.
- **No Accessibility service.** Input is sent as real HID reports or the explicit AOA protocol, not by emulating the Android UI.
- **No contacts, microphone, camera, storage, or usage-access permission.**
- **No `QUERY_ALL_PACKAGES`.** The shortcut/deck feature opens only actions the user explicitly configures.

USB accessory access is granted by Android’s per-accessory permission prompt; FLUXPAD does not ask for blanket USB host access.

## 2. Data the app stores

- **Settings** (sensitivity, scroll direction, reduced motion, onboarding state, theme) in private app storage/DataStore.
- **Decks and shortcuts** you create, in private app storage.
- **USB pairing keys**:
  - Derived from the one-time code with PBKDF2-HMAC-SHA256.
  - Encrypted with AES-GCM using a non-exportable Android Keystore key.
  - Stored only as ciphertext in private SharedPreferences.
  - Never logged, never sent over the network, and not included in backups (`android:allowBackup="false"`).
- **Diagnostics and input metrics** are in-memory only. They are not uploaded.

FLUXPAD does not store Bluetooth link keys; Android’s Bluetooth stack owns those.

## 3. Data the receiver stores

The native receiver stores:

- A random stable receiver id.
- The 32-byte pairing key in hexadecimal.
- The pairing timestamp.
- File location:
  - Windows: `%APPDATA%\FLUXPAD\receiver.json`
  - Linux: `$XDG_CONFIG_HOME/fluxpad/receiver.json` or `~/.config/fluxpad/receiver.json`
  - macOS: `~/Library/Application Support/FLUXPAD/receiver.json`

On Unix the file is written with mode `0600`. On Windows it inherits the user profile’s ACL. The key is **not** wrapped by an OS keystore on the desktop; protect your user account and the file. `fluxpad-receiver --forget` deletes the key and rotates the receiver id.

The receiver prints its receiver id and path (not the key) at startup. `--no-inject` still authenticates but discards all input.

## 4. Network and telemetry

- The Android app has no internet permission.
- The receiver does not open network sockets.
- No analytics SDK, crash reporter, ad library, or update checker is included.
- No data leaves the phone/computer pair except the input reports themselves over Bluetooth HID or the USB cable.

## 5. USB Companion transport security

- Authentication is required before any input report is accepted.
- Fresh 128-bit nonces on every session prevent replay of old proofs.
- Proof comparisons are constant-time.
- The receiver id binds saved keys to one receiver; `--forget` rotates it.
- **AOA transport is not encrypted.** After authentication, reports are sent over the USB cable in plaintext at the protocol level. USB is a physical local link, not a network; use a trusted cable/host and do not treat this as protection against a compromised host.
- The AOA accessory mode is not a security boundary. A malicious USB host could try to speak the protocol; without the pairing key it cannot authenticate, and the Android side will not send input before authentication.

## 6. Bluetooth HID security

Bluetooth HID uses the security of the Bluetooth link and the host OS pairing. FLUXPAD does not implement its own encryption or authentication for HID; Android and the host own the bond. The phone sends HID reports only to a connected/bonded host that Android’s HID stack reports as connected.

## 7. Logs

- Android does not log pairing keys or typed content. Diagnostics show counters and transport state, not keystrokes.
- The receiver prints transport/auth/input-backend errors. It does not print the saved key. Avoid running it with verbose shell tracing if you have modified it to log secrets.
- Pairing codes are displayed on the phone and typed into the receiver terminal. They are not stored after pairing.

## 8. Deleting data

- Android: **Settings → Apps → FLUXPAD → Storage → Clear data** removes settings, decks, and the encrypted pairing blobs. Uninstalling also removes them. The Keystore key is removed with the app.
- Receiver: run `fluxpad-receiver --forget`, or delete the receiver store file. Deleting the file alone also rotates the effective identity on the next run.
- Bluetooth pairing: remove the phone/computer pair in the OS Bluetooth settings; FLUXPAD cannot and does not delete OS Bluetooth bonds.

## 9. Children and sensitive data

FLUXPAD is a local input utility. It does not create profiles, track usage, or collect personal data. Do not use it to type passwords on a host you do not trust, especially over the unencrypted AOA path or on a host controlled by someone else.

## 10. Changes

If a future version adds networking, crash reporting, or cloud features, this document and the Android manifest must change before release. A build with the current manifest genuinely cannot access the internet.
