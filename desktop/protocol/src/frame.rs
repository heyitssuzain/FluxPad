use std::fmt;

pub const MAGIC: [u8; 4] = *b"FLXP";
pub const PROTOCOL_VERSION: u8 = 1;
pub const HEADER_SIZE: usize = 16;
pub const MAX_PAYLOAD: usize = 4096;

/// One FLUXPAD protocol frame.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Frame {
    pub kind: u8,
    pub flags: u16,
    pub seq: u32,
    pub payload: Vec<u8>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Header {
    pub version: u8,
    pub kind: u8,
    pub flags: u16,
    pub seq: u32,
    pub length: usize,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FrameError {
    TooShort { actual: usize, expected: usize },
    BadMagic,
    UnsupportedVersion(u8),
    PayloadTooLarge(usize),
    Truncated { actual: usize, expected: usize },
    CrcMismatch { expected: u32, actual: u32 },
}

impl fmt::Display for FrameError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            FrameError::TooShort { actual, expected } => {
                write!(f, "frame is {actual} bytes, need at least {expected}")
            }
            FrameError::BadMagic => write!(f, "not a FLUXPAD frame (bad magic)"),
            FrameError::UnsupportedVersion(v) => write!(f, "unsupported protocol version {v}"),
            FrameError::PayloadTooLarge(n) => {
                write!(f, "payload length {n} exceeds {MAX_PAYLOAD}")
            }
            FrameError::Truncated { actual, expected } => {
                write!(f, "frame is truncated: {actual} of {expected} bytes")
            }
            FrameError::CrcMismatch { expected, actual } => write!(
                f,
                "frame CRC mismatch (computed {actual:08x}, received {expected:08x})"
            ),
        }
    }
}

impl std::error::Error for FrameError {}

pub fn encode(kind: u8, seq: u32, payload: &[u8]) -> Result<Vec<u8>, FrameError> {
    if payload.len() > MAX_PAYLOAD {
        return Err(FrameError::PayloadTooLarge(payload.len()));
    }
    let mut out = vec![0u8; HEADER_SIZE + payload.len() + 4];
    out[0..4].copy_from_slice(&MAGIC);
    out[4] = PROTOCOL_VERSION;
    out[5] = kind;
    out[6..8].copy_from_slice(&0u16.to_le_bytes());
    out[8..12].copy_from_slice(&seq.to_le_bytes());
    out[12..16].copy_from_slice(&(payload.len() as u32).to_le_bytes());
    out[HEADER_SIZE..HEADER_SIZE + payload.len()].copy_from_slice(payload);
    let crc = crc32(&out[4..HEADER_SIZE + payload.len()]);
    out[HEADER_SIZE + payload.len()..].copy_from_slice(&crc.to_le_bytes());
    Ok(out)
}

pub fn parse_header(header: &[u8; HEADER_SIZE]) -> Result<Header, FrameError> {
    if header[0..4] != MAGIC {
        return Err(FrameError::BadMagic);
    }
    let version = header[4];
    if version != PROTOCOL_VERSION {
        return Err(FrameError::UnsupportedVersion(version));
    }
    let kind = header[5];
    let flags = u16::from_le_bytes([header[6], header[7]]);
    let seq = u32::from_le_bytes([header[8], header[9], header[10], header[11]]);
    let length = u32::from_le_bytes([header[12], header[13], header[14], header[15]]) as usize;
    if length > MAX_PAYLOAD {
        return Err(FrameError::PayloadTooLarge(length));
    }
    Ok(Header {
        version,
        kind,
        flags,
        seq,
        length,
    })
}

pub fn decode_parts(
    header: &[u8; HEADER_SIZE],
    payload: &[u8],
    crc_bytes: &[u8; 4],
) -> Result<Frame, FrameError> {
    let h = parse_header(header)?;
    if payload.len() != h.length {
        return Err(FrameError::Truncated {
            actual: payload.len(),
            expected: h.length,
        });
    }
    let expected = u32::from_le_bytes(*crc_bytes);
    let actual = {
        let mut crc = Crc32::new();
        crc.update(&header[4..HEADER_SIZE]);
        crc.update(payload);
        crc.finish()
    };
    if expected != actual {
        return Err(FrameError::CrcMismatch { expected, actual });
    }
    Ok(Frame {
        kind: h.kind,
        flags: h.flags,
        seq: h.seq,
        payload: payload.to_vec(),
    })
}

pub fn decode(bytes: &[u8]) -> Result<Frame, FrameError> {
    if bytes.len() < HEADER_SIZE + 4 {
        return Err(FrameError::TooShort {
            actual: bytes.len(),
            expected: HEADER_SIZE + 4,
        });
    }
    let mut header = [0u8; HEADER_SIZE];
    header.copy_from_slice(&bytes[..HEADER_SIZE]);
    let h = parse_header(&header)?;
    let total = HEADER_SIZE + h.length + 4;
    if bytes.len() < total {
        return Err(FrameError::Truncated {
            actual: bytes.len(),
            expected: total,
        });
    }
    let payload = &bytes[HEADER_SIZE..HEADER_SIZE + h.length];
    let mut crc = [0u8; 4];
    crc.copy_from_slice(&bytes[HEADER_SIZE + h.length..HEADER_SIZE + h.length + 4]);
    decode_parts(&header, payload, &crc)
}

/// IEEE CRC-32, matching `java.util.zip.CRC32`.
pub fn crc32(data: &[u8]) -> u32 {
    let mut crc = Crc32::new();
    crc.update(data);
    crc.finish()
}

struct Crc32 {
    value: u32,
}

impl Crc32 {
    fn new() -> Self {
        Self { value: 0xFFFF_FFFF }
    }

    fn update(&mut self, data: &[u8]) {
        for &byte in data {
            self.value ^= byte as u32;
            for _ in 0..8 {
                let mask = (self.value & 1).wrapping_neg();
                self.value = (self.value >> 1) ^ (0xEDB8_8320 & mask);
            }
        }
    }

    fn finish(self) -> u32 {
        !self.value
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip() {
        let payload = b"hello receiver";
        let bytes = encode(MessageTypeConst::HELLO, 7, payload).unwrap();
        let frame = decode(&bytes).unwrap();
        assert_eq!(frame.kind, MessageTypeConst::HELLO);
        assert_eq!(frame.seq, 7);
        assert_eq!(frame.payload, payload);
    }

    #[test]
    fn empty_payload_round_trip() {
        let bytes = encode(99, 0, &[]).unwrap();
        assert_eq!(bytes.len(), HEADER_SIZE + 4);
        assert_eq!(decode(&bytes).unwrap().payload, Vec::<u8>::new());
    }

    #[test]
    fn detects_bad_magic() {
        let mut bytes = encode(1, 1, b"x").unwrap();
        bytes[0] = b'X';
        assert!(matches!(decode(&bytes), Err(FrameError::BadMagic)));
    }

    #[test]
    fn detects_crc_mismatch() {
        let mut bytes = encode(1, 1, b"payload").unwrap();
        let last = bytes.len() - 1;
        bytes[last] ^= 0xFF;
        assert!(matches!(
            decode(&bytes),
            Err(FrameError::CrcMismatch { .. })
        ));
    }

    #[test]
    fn rejects_oversized_payload() {
        let payload = vec![0u8; MAX_PAYLOAD + 1];
        assert!(matches!(
            encode(1, 1, &payload),
            Err(FrameError::PayloadTooLarge(_))
        ));
    }

    #[test]
    fn crc_matches_java_reference() {
        // Reference value from java.util.zip.CRC32 over the ASCII string.
        assert_eq!(crc32(b"123456789"), 0xCBF4_3926);
        assert_eq!(crc32(b""), 0);
    }

    struct MessageTypeConst;
    impl MessageTypeConst {
        const HELLO: u8 = 2;
    }
}
