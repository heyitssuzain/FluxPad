//! Shared abstractions used by the receiver binary and platform backends.

use std::fmt;
use std::time::Duration;

use fluxpad_protocol::Frame;

pub mod input;
pub mod store;

pub use input::{
    consumer_name, diff_keys, parse_consumer, parse_keyboard, parse_mouse, InputSink,
    KeyboardReport, KeyboardState, MouseReport, MouseState,
};
pub use store::{PairingStore, StoreError};

pub const RECEIVER_NAME: &str = "FLUXPAD Receiver";
pub const PRODUCT_NAME: &str = "FLUXPAD USB Companion";
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// A transport that can carry FLUXPAD frames. Implementations own their
/// sequence counter; the session layer only names the message type.
pub trait Link {
    fn write_frame(&mut self, kind: u8, payload: &[u8]) -> Result<(), LinkError>;
    fn read_frame(&mut self, timeout: Duration) -> Result<Option<Frame>, LinkError>;
    fn describe(&self) -> String;
}

#[derive(Debug)]
pub enum LinkError {
    Disconnected(String),
    Usb(String),
    Protocol(String),
    Timeout,
}

impl fmt::Display for LinkError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            LinkError::Disconnected(message) => write!(f, "USB link closed: {message}"),
            LinkError::Usb(message) => write!(f, "USB error: {message}"),
            LinkError::Protocol(message) => write!(f, "protocol error: {message}"),
            LinkError::Timeout => write!(f, "USB operation timed out"),
        }
    }
}

impl std::error::Error for LinkError {}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct InputError {
    pub message: String,
    pub unsupported: bool,
}

impl InputError {
    pub fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
            unsupported: false,
        }
    }

    pub fn unsupported(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
            unsupported: true,
        }
    }
}

impl fmt::Display for InputError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.message)
    }
}

impl std::error::Error for InputError {}
