use std::fmt;

pub struct MessageType;

impl MessageType {
    pub const CHALLENGE: u8 = 1;
    pub const HELLO: u8 = 2;
    pub const PAIR_AUTH: u8 = 3;
    pub const PAIR_OK: u8 = 4;
    pub const AUTH_OK: u8 = 5;
    pub const MOUSE: u8 = 10;
    pub const KEYBOARD: u8 = 11;
    pub const CONSUMER: u8 = 12;
    pub const PING: u8 = 13;
    pub const PONG: u8 = 14;
    pub const BYE: u8 = 15;
    pub const ERROR: u8 = 16;
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum MessageError {
    Truncated,
    StringTooLong(usize),
    InvalidUtf8,
}

impl fmt::Display for MessageError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            MessageError::Truncated => write!(f, "message payload is truncated"),
            MessageError::StringTooLong(n) => write!(f, "string of {n} bytes is too long"),
            MessageError::InvalidUtf8 => write!(f, "message contains invalid UTF-8"),
        }
    }
}

impl std::error::Error for MessageError {}

/// A length-prefixed UTF-8 string (u16 little-endian length).
pub fn write_string(out: &mut Vec<u8>, value: &str) -> Result<(), MessageError> {
    let bytes = value.as_bytes();
    if bytes.len() > u16::MAX as usize {
        return Err(MessageError::StringTooLong(bytes.len()));
    }
    out.extend_from_slice(&(bytes.len() as u16).to_le_bytes());
    out.extend_from_slice(bytes);
    Ok(())
}

pub fn read_string(data: &[u8], offset: usize) -> Result<(String, usize), MessageError> {
    if offset + 2 > data.len() {
        return Err(MessageError::Truncated);
    }
    let len = u16::from_le_bytes([data[offset], data[offset + 1]]) as usize;
    let start = offset + 2;
    let end = start.checked_add(len).ok_or(MessageError::Truncated)?;
    if end > data.len() {
        return Err(MessageError::Truncated);
    }
    let text = std::str::from_utf8(&data[start..end])
        .map_err(|_| MessageError::InvalidUtf8)?
        .to_owned();
    Ok((text, end))
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Hello {
    pub paired: bool,
    pub phone_nonce: [u8; 16],
    pub salt: [u8; 16],
    pub proof: Option<[u8; 32]>,
    pub model: String,
}

impl Hello {
    /// Layout: paired(1) | phone nonce(16) | salt(16) | [proof(32)] | model string
    pub fn parse(payload: &[u8]) -> Result<Self, MessageError> {
        if payload.len() < 1 + 16 + 16 {
            return Err(MessageError::Truncated);
        }
        let paired = payload[0] != 0;
        let mut phone_nonce = [0u8; 16];
        phone_nonce.copy_from_slice(&payload[1..17]);
        let mut salt = [0u8; 16];
        salt.copy_from_slice(&payload[17..33]);
        let mut offset = 33;
        let proof = if paired {
            if payload.len() < offset + 32 {
                return Err(MessageError::Truncated);
            }
            let mut proof = [0u8; 32];
            proof.copy_from_slice(&payload[offset..offset + 32]);
            offset += 32;
            Some(proof)
        } else {
            None
        };
        let (model, _) = read_string(payload, offset)?;
        Ok(Self {
            paired,
            phone_nonce,
            salt,
            proof,
            model,
        })
    }
}

pub fn build_challenge(
    receiver_id: &str,
    receiver_nonce: &[u8; 16],
    receiver_name: &str,
) -> Result<Vec<u8>, MessageError> {
    let mut out = Vec::with_capacity(receiver_id.len() + receiver_name.len() + 24);
    write_string(&mut out, receiver_id)?;
    out.extend_from_slice(receiver_nonce);
    write_string(&mut out, receiver_name)?;
    Ok(out)
}

/// The Android client sends length-prefixed errors, but its own failure path
/// writes raw UTF-8. Accept either representation.
pub fn decode_error(payload: &[u8]) -> String {
    if let Ok((text, end)) = read_string(payload, 0) {
        if end == payload.len() && !text.is_empty() {
            return text;
        }
    }
    String::from_utf8_lossy(payload).trim().to_owned()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn unpaired_payload() -> Vec<u8> {
        let mut out = Vec::new();
        out.push(0);
        out.extend_from_slice(&[0xAA; 16]);
        out.extend_from_slice(&[0xBB; 16]);
        write_string(&mut out, "Realme Narzo 50A").unwrap();
        out
    }

    #[test]
    fn parses_unpaired_hello() {
        let hello = Hello::parse(&unpaired_payload()).unwrap();
        assert!(!hello.paired);
        assert_eq!(hello.phone_nonce, [0xAA; 16]);
        assert_eq!(hello.salt, [0xBB; 16]);
        assert_eq!(hello.proof, None);
        assert_eq!(hello.model, "Realme Narzo 50A");
    }

    #[test]
    fn parses_paired_hello() {
        let mut out = Vec::new();
        out.push(1);
        out.extend_from_slice(&[0x11; 16]);
        out.extend_from_slice(&[0u8; 16]);
        out.extend_from_slice(&[0x22; 32]);
        write_string(&mut out, "Phone").unwrap();
        let hello = Hello::parse(&out).unwrap();
        assert!(hello.paired);
        assert_eq!(hello.proof, Some([0x22; 32]));
        assert_eq!(hello.salt, [0u8; 16]);
    }

    #[test]
    fn challenge_round_trip() {
        let nonce = [0x42; 16];
        let payload = build_challenge("fluxpad-aabb", &nonce, "Studio PC").unwrap();
        let (id, offset) = read_string(&payload, 0).unwrap();
        assert_eq!(id, "fluxpad-aabb");
        assert_eq!(&payload[offset..offset + 16], &nonce);
        let (name, end) = read_string(&payload, offset + 16).unwrap();
        assert_eq!(name, "Studio PC");
        assert_eq!(end, payload.len());
    }

    #[test]
    fn error_accepts_both_shapes() {
        let mut prefixed = Vec::new();
        write_string(&mut prefixed, "Pairing code did not match").unwrap();
        assert_eq!(decode_error(&prefixed), "Pairing code did not match");
        assert_eq!(decode_error(b"Raw receiver error"), "Raw receiver error");
    }

    #[test]
    fn rejects_truncated_hello() {
        assert_eq!(Hello::parse(&[0u8; 5]), Err(MessageError::Truncated));
    }
}
