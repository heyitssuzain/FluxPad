# Contributing

FLUXPAD is a small, hardware-sensitive project. Changes that affect transport behavior need honest testing notes, not just compilation.

## Before you start

- Read [the architecture](docs/ARCHITECTURE.md), [protocol](docs/PROTOCOL.md), and [USB compatibility](docs/USB_COMPATIBILITY.md).
- Do not add a fake “supported” state, a placeholder success path, or a simulated device.
- Do not add internet permission, analytics, or an Accessibility-service input path.
- Keep the design system custom; do not add the extended Material icon pack.

## Local checks

Android:

```bash
cd android
./gradlew testDebugUnitTest lintDebug :app:assembleDebug
```

Rust:

```bash
cd desktop
cargo test -p fluxpad-protocol -p fluxpad-shared -p fluxpad-tests --locked
cargo build --release -p fluxpad-receiver --locked
```

The receiver build needs a C toolchain for vendored libusb:

- Windows: MSVC Build Tools.
- Linux: `build-essential pkg-config libudev-dev`.
- macOS: Xcode command line tools.

Cross-target checks are useful for the Linux/macOS backends but do not replace runtime testing:

```bash
cargo check --target x86_64-unknown-linux-gnu -p fluxpad-linux --all-targets
cargo check --target x86_64-apple-darwin -p fluxpad-macos --all-targets
```

## Pull requests

Include:

- What changed and why.
- The exact commands run and their result.
- Whether a physical device/host was used.
- Any new limitation or driver/permission requirement.
- Updates to docs and [progress.md](progress.md) when status changes.

Do not mark hardware paths as verified without the checklist in [docs/TESTING.md](docs/TESTING.md).

## Code style

- Kotlin: follow the existing Compose/state patterns; keep transport work off the main thread.
- Rust: keep platform code behind `cfg`; return `Result` rather than panicking on recoverable I/O.
- No secrets, keystores, APKs, build outputs, or `local.properties` in commits.

## Security issues

Do not open a public issue for a pairing bypass or key-storage flaw. See [SECURITY.md](SECURITY.md).
