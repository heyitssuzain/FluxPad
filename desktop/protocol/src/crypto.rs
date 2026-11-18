use std::fmt;

use hmac::{Hmac, Mac};
use pbkdf2::pbkdf2_hmac;
use rand::rngs::OsRng;
use rand::RngCore;
use sha2::Sha256;
use subtle::ConstantTimeEq;

pub const TAG_PHONE: &str = "FLUXPAD-PHONE";
pub const TAG_RECEIVER: &str = "FLUXPAD-RECEIVER";
pub const TAG_PAIR: &str = "FLUXPAD-PAIR";
pub const TAG_PAIR_OK: &str = "FLUXPAD-PAIR-OK";

const ITERATIONS: u32 = 200_000;
const KEY_LEN: usize = 32;

type HmacSha256 = Hmac<Sha256>;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CryptoError {
    CodeTooShort,
}

impl fmt::Display for CryptoError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            CryptoError::CodeTooShort => {
                write!(f, "pairing code must contain at least 6 letters or digits")
            }
        }
    }
}

impl std::error::Error for CryptoError {}

/// Uppercases and strips separators, matching the Android implementation.
pub fn normalize_code(raw: &str) -> String {
    raw.to_uppercase()
        .chars()
        .filter(|c| c.is_alphanumeric())
        .collect()
}

/// PBKDF2-HMAC-SHA256, 200,000 iterations, 256-bit key.
pub fn derive_key(code: &str, salt: &[u8]) -> Result<[u8; KEY_LEN], CryptoError> {
    let normalized = normalize_code(code);
    if normalized.len() < 6 {
        return Err(CryptoError::CodeTooShort);
    }
    Ok(pbkdf2_key(normalized.as_bytes(), salt, ITERATIONS))
}

fn pbkdf2_key(code: &[u8], salt: &[u8], iterations: u32) -> [u8; KEY_LEN] {
    let mut key = [0u8; KEY_LEN];
    pbkdf2_hmac::<Sha256>(code, salt, iterations, &mut key);
    key
}

pub fn random_bytes(size: usize) -> Vec<u8> {
    let mut bytes = vec![0u8; size];
    OsRng.fill_bytes(&mut bytes);
    bytes
}

/// Stable id for this receiver installation, e.g. `fluxpad-1a2b3c4d5e6f7788`.
pub fn generate_receiver_id() -> String {
    let mut id = [0u8; 8];
    OsRng.fill_bytes(&mut id);
    format!("fluxpad-{}", hex::encode(id))
}

pub fn transcript(
    receiver_id: &str,
    receiver_nonce: &[u8],
    phone_nonce: &[u8],
    salt: &[u8],
) -> Vec<u8> {
    let mut out = Vec::with_capacity(receiver_id.len() + receiver_nonce.len() + phone_nonce.len() + salt.len());
    out.extend_from_slice(receiver_id.as_bytes());
    out.extend_from_slice(receiver_nonce);
    out.extend_from_slice(phone_nonce);
    out.extend_from_slice(salt);
    out
}

pub fn hmac_sha256(key: &[u8], parts: &[&[u8]]) -> [u8; 32] {
    let mut mac = HmacSha256::new_from_slice(key).expect("HMAC accepts any key length");
    for part in parts {
        mac.update(part);
    }
    mac.finalize().into_bytes().into()
}

pub fn proof(key: &[u8], tag: &str, transcript: &[u8]) -> [u8; 32] {
    hmac_sha256(key, &[tag.as_bytes(), transcript])
}

pub fn constant_time_eq(left: &[u8], right: &[u8]) -> bool {
    left.ct_eq(right).into()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn standard_pbkdf2_vector() {
        // RFC 7914 / common reference: PBKDF2-HMAC-SHA256("password", "salt", 1, 32).
        let key = pbkdf2_key(b"password", b"salt", 1);
        assert_eq!(
            hex::encode(key),
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b"
        );
    }

    #[test]
    fn full_strength_key_is_stable() {
        let salt = [0x11u8; 16];
        let a = derive_key("ABCD-EFGH", &salt).unwrap();
        let b = derive_key("abcdefgh", &salt).unwrap();
        assert_eq!(a, b);
        assert_eq!(a.len(), 32);
    }

    #[test]
    fn rejects_short_codes() {
        assert_eq!(
            derive_key("abc", &[0u8; 16]),
            Err(CryptoError::CodeTooShort)
        );
    }

    #[test]
    fn hmac_rfc4231_case_2() {
        let digest = hmac_sha256(b"Jefe", &[b"what do ya want for nothing?"]);
        assert_eq!(
            hex::encode(digest),
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"
        );
    }

    #[test]
    fn constant_time_comparison() {
        assert!(constant_time_eq(b"same", b"same"));
        assert!(!constant_time_eq(b"same", b"diff"));
        assert!(!constant_time_eq(b"short", b"longer"));
    }

    #[test]
    fn receiver_id_shape() {
        let id = generate_receiver_id();
        assert!(id.starts_with("fluxpad-"));
        assert_eq!(id.len(), "fluxpad-".len() + 16);
        assert_ne!(id, generate_receiver_id());
    }
}
