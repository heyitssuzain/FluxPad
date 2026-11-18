# Release Process

FLUXPAD releases contain:

- An Android APK.
- Native receiver binaries for Windows, Linux, and macOS.
- A `SHA256SUMS.txt` checksum file.

A release is produced by pushing a version tag (`v*`). The GitHub Actions workflow builds, tests, and publishes the assets.

> The workflows in this repository have **not been executed** in the local build environment. They are the delivery path; verify the first CI run before treating a release as production-ready.

## 1. Versioning

- Android: update `versionCode` and `versionName` in `android/app/build.gradle.kts`.
  - `versionCode` must increase for every APK published under `dev.fluxpad.app`.
  - `versionName` is human-readable and may match the tag.
- Rust: workspace version is in `desktop/Cargo.toml` (`version = "1.0.0"`). The receiver prints this version.
- Tag format: `v1.0.0`, `v1.0.1`, `v1.1.0`.
- Do not reuse or move a published tag. Android update identity depends on the APK package and signing key, not the tag, but reproducible releases and checksums depend on immutable tags.

## 2. Pre-release gate

Run locally (or in the Android workflow) before tagging:

```bash
cd android
./gradlew testDebugUnitTest lintDebug :app:assembleDebug

cd ../desktop
cargo test -p fluxpad-protocol -p fluxpad-shared -p fluxpad-tests --locked
cargo build --release -p fluxpad-receiver --locked
```

Review the hardware status in [progress.md](../progress.md). A release may ship with `AWAITING DEVICE` markers, but release notes must not describe unverified hardware behavior as confirmed.

Check that no secrets are tracked:

```bash
git status --short
git ls-files | grep -E 'keystore|\.jks|keystore\.properties|\.env' || true
```

## 3. Signing setup

Configure the four repository secrets described in [SIGNING.md](SIGNING.md):

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

If the secrets are absent, the release workflow deliberately publishes a **debug APK** with a `-debug` filename and a warning in the Actions log. It does not upload an unsigned release APK and call it production.

## 4. Build the tag

```bash
git add .
git commit -m "Release v1.0.0"
git tag -a v1.0.0 -m "FLUXPAD v1.0.0"
git push origin main
git push origin v1.0.0
```

The `Release` workflow starts on the tag. It:

1. Runs Android unit tests and lint.
2. Builds a signed release APK if signing secrets exist, otherwise a debug APK.
3. Builds the receiver on Ubuntu, Windows, and macOS.
4. Runs the pure Rust tests on each runner.
5. Publishes a GitHub release with the APK, receiver binaries, and `SHA256SUMS.txt`.

The Android CI workflow and Desktop receiver workflow also run on pushes/PRs to `main` and can be dispatched manually.

## 5. Release asset names

| Asset | Notes |
|---|---|
| `fluxpad-<version>.apk` | Signed release APK, when secrets are configured. |
| `fluxpad-<version>-debug.apk` | Installable debug fallback; package id ends in `.debug`. Not a production-signed artifact. |
| `fluxpad-receiver-linux-x86_64` | Linux receiver, built on Ubuntu. |
| `fluxpad-receiver-windows-x86_64.exe` | Windows receiver, built on the Windows runner with MSVC + vendored libusb. |
| `fluxpad-receiver-macos` | macOS receiver, built on the current `macos-latest` runner. Check the runner architecture in the Actions log; this is not a universal binary. |
| `SHA256SUMS.txt` | SHA-256 checksums for every asset. |

The macOS binary produced by CI is not signed/notarized by default. Gatekeeper may quarantine a downloaded binary. A local build or a future codesigning step is required for distribution outside developer use.

## 6. Verify a downloaded release

Linux/macOS:

```bash
sha256sum -c SHA256SUMS.txt
```

Windows PowerShell:

```powershell
Get-FileHash .\fluxpad-receiver-windows-x86_64.exe -Algorithm SHA256
# compare with SHA256SUMS.txt
```

Android:

```bash
apksigner verify --print-certs fluxpad-<version>.apk
```

Also confirm the APK package id:

- Release: `dev.fluxpad.app`
- Debug fallback: `dev.fluxpad.app.debug`

## 7. Release notes

Include:

- What changed.
- Transport support and any new limitations.
- The exact APK filename/signing status.
- The receiver binary architectures.
- Hardware verification status (`AWAITING DEVICE` where applicable).
- Upgrade notes for HID descriptor/pairing changes.
- Security-relevant changes.

Do not claim Bluetooth HID, AOA, or native input injection is “verified” unless the checklist in [TESTING.md](TESTING.md) has been completed on real hardware and the result recorded.

## 8. Recovering a failed release

- If the workflow fails before publishing, delete the local tag and recreate it after fixing:
  ```bash
  git tag -d v1.0.0
  git push origin :refs/tags/v1.0.0
  ```
  Only do this for an unpublished/failed release.
- If assets are incomplete, fix and use a new patch tag (`v1.0.1`); do not silently replace a published APK under the same version.
- If the signing key is lost, see [SIGNING.md](SIGNING.md). There is no way to preserve update identity with a different key.

## 9. Release checklist

- [ ] Tests and lint pass.
- [ ] `versionCode` increased.
- [ ] Signing secrets configured (or debug fallback intentionally accepted).
- [ ] Hardware status is honest in the notes.
- [ ] No keystore/properties/secrets tracked.
- [ ] Tag pushed.
- [ ] Release workflow green.
- [ ] Assets and checksums present.
- [ ] `apksigner verify` and checksum verification performed.
- [ ] Receiver binaries exercised at least with `--help`/`--version` on their target OS.
