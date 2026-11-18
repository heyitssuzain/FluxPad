# FLUXPAD USB Companion Wire Protocol v1

This protocol runs over Android Open Accessory (AOA) bulk endpoints between the phone (accessory) and the native receiver (USB host). It is little-endian throughout. It is authenticated but **not encrypted**; AOA does not provide encryption.

Status: implemented in Kotlin (`AoaProtocol`, `AoaCrypto`) and Rust (`fluxpad-protocol`). Cross-language vectors and layout tests pass; real USB transport is **AWAITING DEVICE**.

---

## 1. Frame

```
offset  size  field
0       4     magic = "FLXP" (46 4C 58 50)
4       1     version = 1
5       1     type
6       2     flags (reserved, normally 0)
8       4     sequence (u32, wraps)
12      4     payload length (u32, 0..4096)
16      N     payload
16+N    4     CRC-32 (u32)
```

- CRC-32 is the standard IEEE/zip polynomial, initial value `0xFFFFFFFF`, final XOR `0xFFFFFFFF`, exactly matching `java.util.zip.CRC32`.
- The CRC covers bytes `4..(16+payloadLength-1)`: version, type, flags, sequence, length, and payload. It does **not** cover the magic or the CRC field itself.
- Frame overhead is 20 bytes.
- A frame with a bad magic, unsupported version, bad length, truncation, or CRC mismatch must be rejected.
- Sequence numbers are informational; v1 does not require strict ordering. Implementations increment per sent frame.

### Worked header (before CRC)

A `PING` with sequence `0x01020304` and an empty payload encodes as:

```
46 4C 58 50   "FLXP"
01            version 1
0D            type 13 (PING)
00 00         flags 0
04 03 02 01   sequence 0x01020304 little-endian
00 00 00 00   payload length 0
<4-byte CRC-32 over 01 0D 00 00 04 03 02 01 00 00 00 00>
```

---

## 2. Message types

| Value | Name | Direction | Purpose |
|---:|---|---|---|
| 1 | `CHALLENGE` | receiver → phone | Receiver id, fresh nonce, display name |
| 2 | `HELLO` | phone → receiver | Phone nonce, salt, optional proof, model |
| 3 | `PAIR_AUTH` | receiver → phone | Proof of code-derived key |
| 4 | `PAIR_OK` | phone → receiver | Confirmation proof; key may be saved |
| 5 | `AUTH_OK` | receiver → phone | Proof for an existing pairing |
| 10 | `MOUSE` | phone → receiver | 9-byte mouse report |
| 11 | `KEYBOARD` | phone → receiver | 8-byte keyboard report |
| 12 | `CONSUMER` | phone → receiver | 2-byte consumer usage |
| 13 | `PING` | both | Heartbeat request |
| 14 | `PONG` | both | Heartbeat reply |
| 15 | `BYE` | both | Clean session end |
| 16 | `ERROR` | both | Human-readable failure |

Unknown message types are ignored during an authenticated session. Authentication messages are only valid in their expected state; out-of-order messages do not advance authentication.

---

## 3. Length-prefixed strings

```
u16 little-endian byte length | UTF-8 bytes
```

The Android implementation caps strings it writes at 255 bytes; the Rust reader accepts up to `u16::MAX`. `ERROR` payloads may also be raw UTF-8 for compatibility with the Android failure path; receivers must accept both a valid length-prefixed string and raw UTF-8.

---

## 4. `CHALLENGE` (type 1)

Sent once at the start of every session, before any `HELLO`.

```
string  receiver_id      stable id such as "fluxpad-aabbccddeeff0011"
bytes   receiver_nonce   16 fresh random bytes
string  receiver_name    e.g. "FLUXPAD Studio PC"
```

The id is persistent until `--forget`. It is not a secret; it binds saved keys to a receiver. The nonce must be fresh for every connection.

---

## 5. `HELLO` (type 2)

```
u8      paired           0 = first pairing, 1 = existing key
bytes   phone_nonce      16 fresh random bytes
bytes   salt             16 bytes
bytes   proof            32 bytes, present only if paired = 1
string  model            Android Build.MODEL (human-readable)
```

- `salt` is a fresh 16-byte value for first pairing.
- For an existing pairing the phone sends 16 zero bytes as salt. (The key is already derived; the salt has no new role.)
- `proof` for an existing pairing is `HMAC-SHA256(key, TAG_PHONE || transcript)`.
- The receiver rejects a paired HELLO with no proof or with an invalid proof.

---

## 6. Authentication

### 6.1 Constants

```
iterations = 200000
key length  = 32 bytes
KDF         = PBKDF2-HMAC-SHA256
MAC         = HMAC-SHA256
alphabet    = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"   (32 symbols, no ambiguous 0/O/1/I)
code length = 8
```

The pairing code is normalized by uppercasing and removing non-alphanumeric characters. A normalized code must be at least 6 characters; the app generates 8.

### 6.2 Transcript

For both first pairing and existing pairing:

```
transcript = receiver_id_utf8
          || receiver_nonce[16]
          || phone_nonce[16]
          || salt[16]
```

There is no length prefix between fields; the id is variable-length but is always followed by fixed 16-byte fields. Both sides build the same byte sequence.

### 6.3 Tags

```
FLUXPAD-PHONE       phone proves knowledge of the saved key
FLUXPAD-RECEIVER    receiver proves knowledge of the saved key
FLUXPAD-PAIR        receiver proves knowledge of the code-derived key
FLUXPAD-PAIR-OK     phone confirms the code-derived key
```

### 6.4 Existing pairing

1. Receiver sends `CHALLENGE`.
2. Phone looks up `receiver_id`; if found, it generates a phone nonce, uses zero salt, and sends:
   ```
   HELLO(paired=1, phone_nonce, salt=0^16,
         proof=HMAC(key, "FLUXPAD-PHONE" || transcript), model)
   ```
3. Receiver computes the same proof. If equal (constant time), it sends:
   ```
   AUTH_OK(HMAC(key, "FLUXPAD-RECEIVER" || transcript))
   ```
4. Phone validates. Only then does it emit `Connected` and accept input reports.
5. If either proof fails, both sides send/expect `ERROR` and close the session. Input is never accepted before successful authentication.

### 6.5 First pairing

1. Receiver sends `CHALLENGE`.
2. Phone sees no saved key for the receiver id, generates a code and a fresh salt, derives:
   ```
   key = PBKDF2-HMAC-SHA256(normalized_code, salt, 200000, 32)
   ```
   and displays the code. It sends:
   ```
   HELLO(paired=0, phone_nonce, salt, model)
   ```
3. Receiver prompts for the code shown on the phone. It derives the same key and sends:
   ```
   PAIR_AUTH(HMAC(key, "FLUXPAD-PAIR" || transcript))
   ```
4. Phone validates the proof with its pending key. It stores the key using AES-GCM + Android Keystore and sends:
   ```
   PAIR_OK(HMAC(key, "FLUXPAD-PAIR-OK" || transcript))
   ```
5. Receiver validates and saves the key. The session becomes authenticated only after this exchange.

If the receiver has a saved key but the phone does not (or vice versa), the phone/terminal must clear the stale pairing (`--forget` or the app’s pairing reset). A mismatch never silently falls back to an unauthenticated session.

### 6.6 Receiver id rotation

`fluxpad-receiver --forget` deletes the saved key and generates a new receiver id. The next session is a first pairing. This prevents an old phone key from being reused against a receiver that was intentionally reset.

---

## 7. Input reports

The wire reports deliberately mirror the Bluetooth HID report payloads, excluding the HID report id. This keeps the Android encoder and the receiver parser aligned.

### 7.1 Mouse — type 10 (9 bytes)

```
0  u8    buttons: bit0 left, bit1 right, bit2 middle, bit3 back, bit4 forward
1  i16   dx
3  i16   dy
5  i16   wheel
7  i16   pan
```

Little-endian signed 16-bit. Android clamps deltas to `-2000..2000` and splits larger gestures into multiple reports. HID wheel values are detents, not OS-specific scroll units.

### 7.2 Keyboard — type 11 (8 bytes)

```
0  u8    modifiers:
         bit0 left Ctrl, bit1 left Shift, bit2 left Alt, bit3 left GUI
         bit4 right Ctrl, bit5 right Shift, bit6 right Alt, bit7 right GUI
1  u8    reserved (0)
2..7     6 HID keyboard usages, zero = empty slot
```

Maximum 6 simultaneous non-modifier keys. This is a HID limitation, not an app bug. The keyboard output report (LED state) is host → phone over Bluetooth only; AOA does not carry LED state in v1.

### 7.3 Consumer — type 12 (2 bytes)

```
u16 little-endian HID Consumer Page usage
```

Examples: `0x00CD` play/pause, `0x00E9` volume up, `0x00EA` volume down, `0x00E2` mute. `0` means release/none.

The Android consumer report descriptor declares an array with logical/usage maximum `0x3FF`, so values above `0x3FF` are rejected/clamped before sending.

---

## 8. Heartbeats and teardown

- Both sides send `PING` every 5 seconds after authentication.
- A `PING` must be answered with `PONG` carrying an empty payload.
- The receiver treats 20 seconds without any inbound bytes as a dead link.
- `BYE` is a clean close. The receiver releases all pressed keys/buttons.
- An unexpected EOF or USB disconnect is treated as an error; the UI reports it as a real disconnected/error state.
- The receiver has a 15-second `HELLO` timeout and a 20-second pairing-confirmation timeout.

---

## 9. Error handling

- Malformed frames, bad CRC, bad string lengths, and unsupported versions are rejected.
- Authentication failures do not become input sessions.
- The receiver prints input-backend errors once per distinct message; it does not spam or claim success.
- `--no-inject` still performs the full protocol and prints authentication results, but discards reports.

## 10. Compatibility checklist

A conforming implementation must:

- use the exact magic/version/header/CRC layout;
- use little-endian integers;
- use the exact tags and transcript concatenation;
- use PBKDF2-HMAC-SHA256 with 200,000 iterations and a 32-byte key;
- compare proofs in constant time;
- refuse input until authentication succeeds;
- send fresh 16-byte nonces every session;
- release all input when the session ends.
