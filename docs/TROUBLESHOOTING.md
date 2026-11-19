# FLUXPAD — Troubleshooting & Recovery

This page covers the failure modes seen on real hardware. Almost all of them are
**pairing or host-driver state**, not app logic: a Bluetooth HID link can be
"connected" at the ACL layer while Windows refuses to start the HID driver, so
the pad moves nothing. FLUXPAD now reports that honestly and re-opens the
channel, but the final repair for a broken key is always the same: **forget the
computer on both sides and pair once more while FLUXPAD is open.**

Legend: 🟢 works without help · 🟠 needs one manual step · 🔴 host limitation

---

## 1. "It says CONNECTED but the laptop does nothing"

### What is really happening

On Windows the phone appears as `Bluetooth HID Device`. If that node is

```
Status: Error    Problem: CM_PROB_FAILED_START   (Code 10)
```

then Windows accepted the reports from Android and dropped them: the HID driver
never started. Windows' own system log shows the reason:

```
BTHUSB  Event 16  Error
The mutual authentication between the local Bluetooth adapter and a device
with Bluetooth adapter address (xx:xx:xx:xx:xx:xx) failed.
```

That means the **link key is out of sync** between the phone and the PC. Nothing
in an APK can renegotiate a link key; the pair has to be re-created.

### Fix (Windows) — about 60 seconds

1. Leave FLUXPAD **open** on the phone.
2. Windows → **Settings → Bluetooth & devices → Devices**.
3. Find the phone (for example `RMX`), open its menu, choose **Remove device**.
4. On the phone → **Settings → Bluetooth** → tap the PC (`DESKTOP-…`) → **Forget**.
5. On the phone, tap **Pair new device** and keep the Bluetooth screen open
   (Android makes the phone discoverable for about two minutes).
6. On Windows → **Add device → Bluetooth → RMX**.
7. Confirm the same six-digit code on both screens.
8. Return to FLUXPAD → **Connect**.

Verify on Windows: Device Manager → **Bluetooth HID Device**, plus
`HID-compliant mouse`, `HID Keyboard Device` and `HID-compliant consumer control
device`, must all be **OK** (no warning triangle).

> Do not force-stop, clear data or reinstall FLUXPAD between step 7 and the first
> successful test. The HID service record only exists while a registered app is
> running, and reinstalling during a session can leave Windows holding a dead
> binding.

### Why FLUXPAD can't just repair it

The link key lives in the Bluetooth controller/OS on both sides. Android's public
`BluetoothHidDevice` API has no "re-key" call, and Windows has no remote command
to forget a pairing. FLUXPAD therefore surfaces the state and retries the
channel, but the manual re-pair above is the only correct repair.

---

## 2. "FLUXPAD could not register the HID role"

Message in the app:

> FLUXPAD could not register the HID role … Turn Bluetooth off and on once, then
> reopen FLUXPAD.

This happens after an **update, force-stop or crash while a session was live**.
The phone's Bluetooth process can keep the previous (dead) app registration, so
every new `registerApp` is refused until the radio restarts.

### Fix

1. Turn **Bluetooth off**, wait five seconds, turn it **on**.
2. Reopen FLUXPAD. Registration now completes and the PC re-adopts the device
   automatically (no re-pair needed).
3. If it still fails, check that no other remote/keyboard app (Remote Mouse,
   Unified Remote, PC Remote, …) is running — Android allows only **one** HID
   Device registration at a time.

### Prevention

- Close FLUXPAD from its own **Disconnect** button instead of force-stopping it.
- Do not reinstall or clear data during a live session.
- Only one FLUXPAD build should be installed at a time: the debug build
  (`dev.fluxpad.app.debug`) and the release build (`dev.fluxpad.app`) are two
  separate apps and will compete for the HID role. Uninstall the one you do not
  use.

---

## 3. Installing the release APK (debug fallback)

When the repository has no signing secrets, the release workflow publishes
`fluxpad-<version>-debug.apk`. That is expected:

- It is signed with the **Android debug certificate**, not a production identity.
- It installs like any sideloaded APK: allow **Install unknown apps** for your
  browser or file manager.
- It has package `dev.fluxpad.app`; the debug build is `dev.fluxpad.app.debug`.
  Uninstall the other build first so only one app can register as the HID device.
- Grant the **Nearby devices** permission on Android 12+ (needed to register HID
  and list bonded computers).

Then pair once (section 1, steps 5–8). The APK does not include a desktop app:
Bluetooth HID needs nothing installed on the computer.

---

## 4. Two-finger scrolling and gestures

| Gesture on the pad | Result |
|---|---|
| One finger drag | pointer moves |
| **Two fingers up / down** | **scroll** (vertical wheel) |
| Two-finger tap | right click |
| Three-finger tap | middle click |
| Four-finger swipe up / down / left / right | configured shortcut (default: Task view / minimize / previous / next desktop) |
| Two-finger swipe down / up | configured shortcut (default: show desktop / previous window) |

Details that surprise people:

- A **fast, straight, vertical two-finger flick** is deliberately held for up to
  ~520 ms while FLUXPAD decides whether it is a navigation swipe. If it does not
  qualify, the buffered motion is flushed as scroll, so nothing is lost.
- The swipe thresholds are a fraction of the pad size. If navigation swipes fire
  while you only wanted to scroll, raise **Gestures → Sensitivity** toward
  *Precise*, or turn **Two-finger navigation** off in Settings → Gestures.
- **Natural scrolling** reverses the wheel direction.
- Horizontal two-finger movement does not scroll sideways by default; use the
  shift-scroll shortcut or a four-finger left/right shortcut.
- FLUXPAD never injects multi-touch into the phone; all gestures are recognised
  locally and sent as ordinary HID mouse/keyboard reports.

If scrolling does nothing while the cursor moves, the host is receiving mouse
reports but not wheel reports — check that the Windows device list shows a
`HID-compliant mouse` (not only a keyboard). The composite descriptor exposes
mouse, keyboard and consumer pages; Windows creates all three nodes.

---

## 5. The link drops every ~16 seconds

Usually the broken key from section 1, or Windows power management:

1. Device Manager → **Bluetooth HID Device** → **Properties → Power Management**
   → clear **Allow the computer to turn off this device to save power**.
2. Bluetooth adapter → **Properties → Power Management** → clear the same box.
3. Re-run the re-pair in section 1.

FLUXPAD now treats a link that dies in under 20 seconds as a failed attempt,
re-opens the channel once, and after repeated failures shows an actionable error
instead of a fake green state.

---

## 6. Bluetooth is off, or the adapter restarted

FLUXPAD listens for adapter state changes:

- Bluetooth turned **off** → the session is marked lost immediately (no stale
  "Connected").
- Bluetooth turned **on** → the last computer is re-opened automatically.

If the banner still says "Bluetooth is off", pull the dashboard down or reopen
the app; the capability probe is refreshed on every connection-state change.

---

## 7. Linux and macOS hosts

- **Linux (BlueZ)**: pair once, then `bluetoothctl connect <phone>`. HID device
  role support depends on the BlueZ version and the controller; `btmon` shows the
  HID control/interrupt channels. If input works but media keys do not, the host
  only bound the mouse/keyboard collections.
- **macOS**: pair in System Settings → Bluetooth. macOS creates a keyboard and a
  mouse; the consumer collection may be ignored. Secure input fields can reject
  synthetic keys by design.

---

## 8. What is guaranteed, and what is not

Guaranteed by the APK:

- Real `BluetoothHidDevice` input — no Accessibility emulation, no mock transport,
  no fake "connected" state.
- Honest link state, automatic re-open after a drop, adapter off/on handling.
- Gesture recognition (2- and 4-finger) with configurable actions, persisted.
- Unit-tested report encoding and gesture kinematics (`:core:model:test`).

Not guaranteed by the APK, because it is OS state:

- A Bluetooth pairing key can be invalidated by a Windows update, an adapter
  reset, or a factory reset. The repair is section 1.
- A host can refuse the HID class (locked-down corporate images, some Linux
  controllers). Check the host's HID service before blaming the phone.
- Only one app can hold the Android HID Device role at a time.

---

## Quick checklist before reporting a bug

1. FLUXPAD shows **LIVE** and the phone's Bluetooth screen shows the PC as
   **Connected / In use**.
2. Windows Device Manager shows **Bluetooth HID Device = OK**.
3. Windows Event Viewer (System) has **no** `BTHUSB` Event 16 for the phone in
   the last five minutes.
4. Only one FLUXPAD build is installed and no other remote-input app is running.
5. Bluetooth was toggled once after the last install/update.

If all five are true and the cursor still does not move, the problem is on the
host side, not in the gesture code.
