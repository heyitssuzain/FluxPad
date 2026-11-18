//! FLUXPAD USB Companion protocol v1.
//!
//! This crate is deliberately independent from USB and desktop input APIs so
//! that framing and authentication can be unit-tested on every platform.

pub mod crypto;
pub mod frame;
pub mod messages;

pub use crypto::{
    constant_time_eq, derive_key, generate_receiver_id, normalize_code, proof, random_bytes,
    transcript, TAG_PAIR, TAG_PAIR_OK, TAG_PHONE, TAG_RECEIVER,
};
pub use frame::{
    decode, encode, Frame, FrameError, HEADER_SIZE, MAGIC, MAX_PAYLOAD, PROTOCOL_VERSION,
};
pub use messages::{
    build_challenge, decode_error, read_string, write_string, Hello, MessageError, MessageType,
};
