# Signing the Android APK

A release APK must be signed with a private keystore. The keystore and its passwords are never committed. If no keystore is configured, the Gradle release output is **unsigned**; the GitHub release workflow falls back to a debug APK rather than publishing an unsigned APK as a production build.

## 1. Create a keystore

Use a dedicated keystore for FLUXPAD, not a debug keystore.

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias fluxpad \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Store the file and passwords somewhere safe. Losing them means you cannot publish updates that Android will accept as the same app. Back up the keystore and passwords offline.

## 2. Configure a local signed build

Create `android/keystore.properties` (already ignored by `.gitignore`):

```properties
storeFile=release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=fluxpad
keyPassword=YOUR_KEY_PASSWORD
```

Place `release.keystore` at `android/release.keystore` so the `storeFile` path is relative to the `android` root project.

Build:

```bash
cd android
./gradlew :app:assembleRelease
```

Output:

```
android/app/build/outputs/apk/release/app-release.apk
```

`app-release-unsigned.apk` appears when no signing config is present.

## 3. Verify the signature

Use the Android SDK build-tools:

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
  android/app/build/outputs/apk/release/app-release.apk
```

Check:

- `Verified using v2/v3 scheme: true`
- The certificate SHA-256 is the one you expect.
- The package is `dev.fluxpad.app` for release (debug builds use `dev.fluxpad.app.debug`).

You can also inspect the manifest:

```bash
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging \
  android/app/build/outputs/apk/release/app-release.apk | head
```

## 4. GitHub Actions secrets

Create these repository secrets:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64 of `release.keystore` (single line) |
| `ANDROID_KEYSTORE_PASSWORD` | `storePassword` |
| `ANDROID_KEY_ALIAS` | `keyAlias` (e.g. `fluxpad`) |
| `ANDROID_KEY_PASSWORD` | `keyPassword` |

Base64 encode without line wraps:

Linux:

```bash
base64 -w0 release.keystore > release.keystore.b64
```

macOS:

```bash
base64 -i release.keystore -o release.keystore.b64
```

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) |
  Set-Content -NoNewline release.keystore.b64
```

Paste the contents of `release.keystore.b64` into the `ANDROID_KEYSTORE_BASE64` secret. Do not paste the keystore binary itself.

The release workflow decodes the secret into `android/release.keystore`, writes `android/keystore.properties` with the other secrets, builds `assembleRelease`, and uploads the signed APK. If any required secret is missing, it builds and uploads a clearly named debug APK instead.

## 5. Versioning

`android/app/build.gradle.kts` currently sets:

```kotlin
versionCode = 1
versionName = "1.0.0"
```

Increment `versionCode` for every release published to the same application id. `versionName` is human-readable. Before a tagged release, update both values and commit them. The tag alone does not change the APK version.

## 6. Release checklist

- [ ] Keystore is backed up offline.
- [ ] `keystore.properties` and `release.keystore` are not tracked (`git status`).
- [ ] `./gradlew testDebugUnitTest lintDebug :app:assembleRelease` passes.
- [ ] `apksigner verify --print-certs` reports the expected certificate.
- [ ] `versionCode` is greater than the previous release.
- [ ] The APK is named clearly (e.g. `fluxpad-1.0.0.apk`) in the release assets.
- [ ] Debug APKs are not labelled as production builds.

## 7. If you lose the signing key

Android will not install an update signed by a different key over the existing app. You must either:

- keep using the old signed APK, or
- publish under a new application id with a migration note (users must uninstall/reinstall and lose app-local data).

There is no safe side-load workaround that preserves the identity of a lost key. Treat the keystore as permanent.
