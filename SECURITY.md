# Security Policy

## Supported versions

FLUXPAD is pre-1.0 hardware verification. Security fixes are applied to the current `main` branch and the latest tagged release.

## Reporting

Do **not** open a public issue for a vulnerability involving:

- pairing/authentication bypass,
- key disclosure or Keystore misuse,
- replay of AOA messages,
- arbitrary code execution in the receiver,
- a way to inject input without authentication.

Use GitHub’s private **Security → Report a vulnerability** flow for `https://github.com/heyitssuzain/FluxPad`. If that is unavailable, contact the repository owner privately and avoid publishing exploit details before a fix is available.

Include:

- affected version/build,
- transport (Bluetooth HID or USB Companion),
- phone/host/OS details,
- reproduction steps and expected/actual behavior,
- whether a physical device was used.

## Scope

### In scope

- Android app, Compose UI, HID report handling.
- USB Companion protocol and receiver.
- Pairing key storage and authentication.
- Native input backends and platform permission handling.

### Out of scope / known boundaries

- The OS’s own Bluetooth pairing security.
- UAC/lock-screen/secure-input behavior; synthetic input cannot cross these boundaries.
- WinUSB/Zadig driver installation and Windows kernel policy.
- Physical USB access by a malicious host after the user connects a device.
- Rooted/custom Android firmware or vendor-specific USB gadget modifications.
- Denial of service from unplugging a cable.

## Security model summary

- Bluetooth HID uses the Android/host Bluetooth bond; FLUXPAD does not add its own encryption.
- USB Companion requires an authenticated HMAC challenge/response before accepting input.
- AOA traffic is authenticated but not encrypted; USB is treated as a local physical link.
- Android pairing keys are AES-GCM encrypted with a non-exportable Keystore key.
- Native receiver keys are stored in a user-protected file; `--forget` rotates the receiver id.
- No telemetry or network access exists in the Android app.

See [docs/PRIVACY.md](docs/PRIVACY.md) and [docs/PROTOCOL.md](docs/PROTOCOL.md).
