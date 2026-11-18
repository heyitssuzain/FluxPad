//! FLUXPAD USB Companion receiver.
//!
//! The receiver is the USB host. It switches an Android phone into AOA
//! accessory mode, authenticates the phone with a one-time pairing code, then
//! injects the phone's HID reports through the native input API of the host.

use std::collections::HashSet;
use std::fmt;
use std::io::{self, Write};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use fluxpad_protocol::{
    build_challenge, constant_time_eq, decode_error, derive_key, normalize_code, proof,
    random_bytes, write_string, MessageType, TAG_PAIR, TAG_PAIR_OK, TAG_PHONE, TAG_RECEIVER,
};
use fluxpad_shared::{
    parse_consumer, parse_keyboard, parse_mouse, InputSink, Link, LinkError, MouseReport,
    PairingStore, RECEIVER_NAME, VERSION,
};
use fluxpad_usb::AoaLink;

const HELLO_TIMEOUT: Duration = Duration::from_secs(15);
const PAIR_CONFIRM_TIMEOUT: Duration = Duration::from_secs(20);
const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(5);
const INBOUND_TIMEOUT: Duration = Duration::from_secs(20);
const READ_POLL: Duration = Duration::from_millis(500);

fn main() {
    if let Err(error) = run() {
        eprintln!("[fluxpad] error: {error}");
        std::process::exit(1);
    }
}

fn run() -> Result<(), Box<dyn std::error::Error>> {
    let options = match Options::parse(std::env::args().skip(1))? {
        ParseOutcome::Help => {
            print_help();
            return Ok(());
        }
        ParseOutcome::Run(options) => options,
    };

    if options.list {
        println!("[fluxpad] USB inventory:");
        let devices = fluxpad_usb::list_devices()?;
        if devices.is_empty() {
            println!("  (no USB devices visible)");
        } else {
            for device in devices {
                println!("  {device}");
            }
        }
        return Ok(());
    }

    let mut store = PairingStore::open_default()?;
    if options.forget {
        store.forget()?;
        println!("[fluxpad] Pairing cleared. New receiver id: {}", store.receiver_id());
        println!("[fluxpad] Start the receiver again to pair with a new code.");
        return Ok(());
    }

    let mut sink = make_sink(options.no_inject)?;
    println!("[fluxpad] FLUXPAD receiver {VERSION}");
    println!("[fluxpad] Receiver id: {}", store.receiver_id());
    println!("[fluxpad] Pairing store: {}", store.path().display());
    println!("[fluxpad] Input backend: {}", sink.backend_name());
    println!("[fluxpad] Waiting for a phone over USB...");
    println!("[fluxpad] On the phone: FLUXPAD -> Connection -> USB Companion -> Connect USB.");
    println!("[fluxpad] Press Ctrl+C to stop.");

    let interrupted = Arc::new(AtomicBool::new(false));
    {
        let interrupted = Arc::clone(&interrupted);
        ctrlc::set_handler(move || {
            interrupted.store(true, Ordering::SeqCst);
        })?;
    }

    let mut last_note = String::new();
    let mut last_note_at = Instant::now() - Duration::from_secs(30);

    while !interrupted.load(Ordering::SeqCst) {
        let mut link = match AoaLink::connect(store.receiver_id()) {
            Ok(link) => link,
            Err(error) => {
                let note = error.to_string();
                if note != last_note || last_note_at.elapsed() > Duration::from_secs(10) {
                    println!("[fluxpad] {note}");
                    last_note = note;
                    last_note_at = Instant::now();
                }
                sleep_interruptible(Duration::from_secs(2), &interrupted);
                continue;
            }
        };
        last_note.clear();
        println!("[fluxpad] Connected: {}", link.describe());

        match session(&mut link, &mut store, &options, sink.as_mut(), &interrupted) {
            Ok(SessionOutcome::Bye) => {
                println!("[fluxpad] Phone ended the session.");
            }
            Ok(SessionOutcome::Interrupted) => {
                let _ = link.write_frame(MessageType::BYE, &[]);
                println!("[fluxpad] Stopping.");
            }
            Err(SessionError::Interrupted) => {
                println!("[fluxpad] Stopping.");
            }
            Err(error) => {
                println!("[fluxpad] Session failed: {error}");
            }
        }
        let _ = sink.release_all();
        drop(link);
        if interrupted.load(Ordering::SeqCst) {
            break;
        }
        std::thread::sleep(Duration::from_millis(700));
    }

    let _ = sink.release_all();
    println!("[fluxpad] Released input and stopped.");
    Ok(())
}

fn make_sink(no_inject: bool) -> Result<Box<dyn InputSink>, Box<dyn std::error::Error>> {
    if no_inject {
        println!("[fluxpad] --no-inject: authentication only; input will be discarded.");
        return Ok(Box::new(NullSink));
    }
    #[cfg(windows)]
    {
        return Ok(Box::new(fluxpad_windows::WindowsSink::new()));
    }
    #[cfg(target_os = "linux")]
    {
        return Ok(Box::new(fluxpad_linux::LinuxSink::new()?));
    }
    #[cfg(target_os = "macos")]
    {
        return Ok(Box::new(fluxpad_macos::MacSink::new()?));
    }
    #[cfg(not(any(windows, target_os = "linux", target_os = "macos")))]
    {
        let _ = no_inject;
        Err("this operating system is not supported; use --no-inject to test authentication".into())
    }
}

struct NullSink;

impl InputSink for NullSink {
    fn backend_name(&self) -> &'static str {
        "none (--no-inject)"
    }

    fn mouse(&mut self, _report: MouseReport) -> Result<(), fluxpad_shared::InputError> {
        Ok(())
    }

    fn keyboard(
        &mut self,
        _modifiers: u8,
        _keys: &[u8],
    ) -> Result<(), fluxpad_shared::InputError> {
        Ok(())
    }

    fn consumer(&mut self, _usage: u16) -> Result<(), fluxpad_shared::InputError> {
        Ok(())
    }

    fn release_all(&mut self) -> Result<(), fluxpad_shared::InputError> {
        Ok(())
    }
}

enum SessionOutcome {
    Bye,
    Interrupted,
}

fn session(
    link: &mut AoaLink,
    store: &mut PairingStore,
    options: &Options,
    sink: &mut dyn InputSink,
    interrupted: &AtomicBool,
) -> Result<SessionOutcome, SessionError> {
    let phone = authenticate(link, store, options, interrupted)?;
    println!("[fluxpad] Authenticated: {phone}");
    println!("[fluxpad] Input is live. Touchpad, keyboard and media should work now.");

    let mut last_inbound = Instant::now();
    let mut last_ping = Instant::now();
    let mut warned = HashSet::new();

    loop {
        if interrupted.load(Ordering::SeqCst) {
            return Ok(SessionOutcome::Interrupted);
        }
        if last_ping.elapsed() >= HEARTBEAT_INTERVAL {
            link.write_frame(MessageType::PING, &[])?;
            last_ping = Instant::now();
        }
        match link.read_frame(READ_POLL) {
            Ok(Some(frame)) => {
                last_inbound = Instant::now();
                match frame.kind {
                    MessageType::MOUSE => {
                        let report = parse_mouse(&frame.payload)
                            .ok_or_else(|| SessionError::Protocol("short mouse report".to_owned()))?;
                        if let Err(error) = sink.mouse(report) {
                            warn_once(&mut warned, &error.message);
                        }
                    }
                    MessageType::KEYBOARD => {
                        let report = parse_keyboard(&frame.payload).ok_or_else(|| {
                            SessionError::Protocol("short keyboard report".to_owned())
                        })?;
                        if let Err(error) = sink.keyboard(report.modifiers, &report.keys) {
                            warn_once(&mut warned, &error.message);
                        }
                    }
                    MessageType::CONSUMER => {
                        let usage = parse_consumer(&frame.payload).ok_or_else(|| {
                            SessionError::Protocol("short consumer report".to_owned())
                        })?;
                        if let Err(error) = sink.consumer(usage) {
                            warn_once(&mut warned, &error.message);
                        }
                    }
                    MessageType::PING => {
                        link.write_frame(MessageType::PONG, &[])?;
                    }
                    MessageType::PONG => {}
                    MessageType::BYE => return Ok(SessionOutcome::Bye),
                    MessageType::ERROR => {
                        return Err(SessionError::Auth(decode_error(&frame.payload)));
                    }
                    _ => {}
                }
            }
            Ok(None) => {
                if last_inbound.elapsed() > INBOUND_TIMEOUT {
                    return Err(SessionError::Protocol(
                        "the phone stopped sending heartbeats".to_owned(),
                    ));
                }
            }
            Err(LinkError::Timeout) => {
                return Err(SessionError::Protocol(
                    "USB read timed out in the middle of a frame".to_owned(),
                ));
            }
            Err(error) => return Err(SessionError::Link(error)),
        }
    }
}

fn authenticate(
    link: &mut AoaLink,
    store: &mut PairingStore,
    options: &Options,
    interrupted: &AtomicBool,
) -> Result<String, SessionError> {
    let receiver_nonce = random_bytes(16);
    let mut nonce = [0u8; 16];
    nonce.copy_from_slice(&receiver_nonce);
    let challenge = build_challenge(store.receiver_id(), &nonce, &options.name)?;
    link.write_frame(MessageType::CHALLENGE, &challenge)?;

    let hello = wait_for_hello(link, interrupted)?;
    let transcript = fluxpad_protocol::transcript(
        store.receiver_id(),
        &nonce,
        &hello.phone_nonce,
        &hello.salt,
    );

    if hello.paired {
        let key = match store.key() {
            Some(key) => key,
            None => {
                return Err(fail(
                    link,
                    "the phone is paired to this receiver id, but the receiver has no key. \
                     Clear the old pairing in FLUXPAD, then reconnect.",
                ))
            }
        };
        let expected = proof(key, TAG_PHONE, &transcript);
        let received = match hello.proof {
            Some(proof) => proof,
            None => return Err(fail(link, "paired HELLO did not include a proof")),
        };
        if !constant_time_eq(&expected, &received) {
            return Err(fail(link, "this receiver is not paired to the phone"));
        }
        let reply = proof(key, TAG_RECEIVER, &transcript);
        link.write_frame(MessageType::AUTH_OK, &reply)?;
        return Ok(hello.model);
    }

    let code = read_pairing_code(options, store.receiver_id())?;
    let key = match derive_key(&code, &hello.salt) {
        Ok(key) => key,
        Err(error) => return Err(fail(link, &error.to_string())),
    };
    let pair_proof = proof(&key, TAG_PAIR, &transcript);
    link.write_frame(MessageType::PAIR_AUTH, &pair_proof)?;
    println!("[fluxpad] Waiting for the phone to confirm the code...");

    let deadline = Instant::now() + PAIR_CONFIRM_TIMEOUT;
    while Instant::now() < deadline {
        if interrupted.load(Ordering::SeqCst) {
            return Err(SessionError::Interrupted);
        }
        match link.read_frame(READ_POLL) {
            Ok(Some(frame)) => match frame.kind {
                MessageType::PAIR_OK => {
                    if frame.payload.len() < 32 {
                        return Err(fail(link, "malformed pairing confirmation"));
                    }
                    let expected = proof(&key, TAG_PAIR_OK, &transcript);
                    if !constant_time_eq(&expected, &frame.payload[..32]) {
                        return Err(fail(link, "the phone rejected the pairing confirmation"));
                    }
                    store.save_key(&key)?;
                    println!("[fluxpad] Pairing saved.");
                    return Ok(hello.model);
                }
                MessageType::ERROR => {
                    return Err(SessionError::Auth(decode_error(&frame.payload)))
                }
                MessageType::PING => {
                    link.write_frame(MessageType::PONG, &[])?;
                }
                _ => {}
            },
            Ok(None) => {}
            Err(LinkError::Timeout) => {}
            Err(error) => return Err(SessionError::Link(error)),
        }
    }
    Err(fail(link, "the phone did not confirm the pairing code"))
}

fn wait_for_hello(
    link: &mut AoaLink,
    interrupted: &AtomicBool,
) -> Result<fluxpad_protocol::Hello, SessionError> {
    let deadline = Instant::now() + HELLO_TIMEOUT;
    while Instant::now() < deadline {
        if interrupted.load(Ordering::SeqCst) {
            return Err(SessionError::Interrupted);
        }
        match link.read_frame(READ_POLL) {
            Ok(Some(frame)) => match frame.kind {
                MessageType::HELLO => {
                    return fluxpad_protocol::Hello::parse(&frame.payload)
                        .map_err(|error| SessionError::Protocol(error.to_string()));
                }
                MessageType::ERROR => {
                    return Err(SessionError::Auth(decode_error(&frame.payload)));
                }
                MessageType::PING => {
                    link.write_frame(MessageType::PONG, &[])?;
                }
                _ => {}
            },
            Ok(None) => {}
            Err(LinkError::Timeout) => {}
            Err(error) => return Err(SessionError::Link(error)),
        }
    }
    Err(SessionError::Auth(
        "the phone did not answer. Open FLUXPAD, choose Connection, then tap Connect USB. \
         If the phone is locked, unlock it first."
            .to_owned(),
    ))
}

fn read_pairing_code(options: &Options, receiver_id: &str) -> Result<String, SessionError> {
    if let Some(code) = &options.pair_code {
        let normalized = normalize_code(code);
        if normalized.len() < 6 {
            return Err(SessionError::Auth(
                "--pair-code must contain at least 6 letters or digits".to_owned(),
            ));
        }
        return Ok(normalized);
    }

    println!();
    println!("  Pairing receiver id: {receiver_id}");
    for attempt in 1..=3 {
        print!("  Enter the 8-character code shown on the phone: ");
        io::stdout().flush()?;
        let mut line = String::new();
        let read = io::stdin().read_line(&mut line)?;
        if read == 0 {
            return Err(SessionError::Auth("no pairing code was entered".to_owned()));
        }
        let normalized = normalize_code(line.trim());
        if normalized.len() >= 6 {
            return Ok(normalized);
        }
        println!("  That code is too short. Use the code displayed under the QR-less pairing prompt.");
        if attempt == 3 {
            break;
        }
    }
    Err(SessionError::Auth("no valid pairing code was entered".to_owned()))
}

fn warn_once(warned: &mut HashSet<String>, message: &str) {
    if warned.insert(message.to_owned()) {
        println!("[fluxpad] input warning: {message}");
    }
}

fn fail(link: &mut AoaLink, message: &str) -> SessionError {
    let mut payload = Vec::new();
    if write_string(&mut payload, message).is_ok() {
        let _ = link.write_frame(MessageType::ERROR, &payload);
    }
    SessionError::Auth(message.to_owned())
}

fn sleep_interruptible(duration: Duration, interrupted: &AtomicBool) {
    let until = Instant::now() + duration;
    while Instant::now() < until {
        if interrupted.load(Ordering::SeqCst) {
            return;
        }
        std::thread::sleep(Duration::from_millis(100));
    }
}

#[derive(Debug, Default)]
struct Options {
    list: bool,
    forget: bool,
    no_inject: bool,
    pair_code: Option<String>,
    name: String,
}

enum ParseOutcome {
    Help,
    Run(Options),
}

impl Options {
    fn parse(args: impl Iterator<Item = String>) -> Result<ParseOutcome, String> {
        let mut options = Options {
            name: default_receiver_name(),
            ..Options::default()
        };
        let mut args = args.peekable();
        while let Some(arg) = args.next() {
            match arg.as_str() {
                "-h" | "--help" => return Ok(ParseOutcome::Help),
                "-V" | "--version" => {
                    println!("fluxpad-receiver {VERSION}");
                    std::process::exit(0);
                }
                "--list" => options.list = true,
                "--forget" => options.forget = true,
                "--no-inject" => options.no_inject = true,
                "--pair-code" => {
                    let value = args
                        .next()
                        .ok_or_else(|| "--pair-code needs a value".to_owned())?;
                    options.pair_code = Some(value);
                }
                "--name" => {
                    let value = args
                        .next()
                        .ok_or_else(|| "--name needs a value".to_owned())?;
                    if value.trim().is_empty() {
                        return Err("--name cannot be empty".to_owned());
                    }
                    options.name = value.trim().to_owned();
                }
                _ if arg.starts_with("--pair-code=") => {
                    options.pair_code = Some(arg["--pair-code=".len()..].to_owned());
                }
                _ if arg.starts_with("--name=") => {
                    let value = arg["--name=".len()..].trim();
                    if value.is_empty() {
                        return Err("--name cannot be empty".to_owned());
                    }
                    options.name = value.to_owned();
                }
                other => return Err(format!("unknown argument: {other}")),
            }
        }
        Ok(ParseOutcome::Run(options))
    }
}

fn default_receiver_name() -> String {
    for key in ["COMPUTERNAME", "HOSTNAME"] {
        if let Ok(value) = std::env::var(key) {
            let trimmed = value.trim();
            if !trimmed.is_empty() {
                return format!("{} ({})", RECEIVER_NAME, trimmed);
            }
        }
    }
    RECEIVER_NAME.to_owned()
}

fn print_help() {
    println!("FLUXPAD USB Companion receiver {VERSION}");
    println!();
    println!("USAGE:");
    println!("  fluxpad-receiver [OPTIONS]");
    println!();
    println!("OPTIONS:");
    println!("  --list              List USB devices and AOA/accessory markers");
    println!("  --forget            Delete the saved pairing and rotate the receiver id");
    println!("  --pair-code CODE    Pair non-interactively (the code shown on the phone)");
    println!("  --name NAME         Receiver name shown on the phone");
    println!("  --no-inject         Authenticate and log only; do not move the desktop");
    println!("  -h, --help          Show this help");
    println!("  -V, --version        Show the version");
    println!();
    println!("The phone must be unlocked and connected by USB. On Windows the Android");
    println!("device needs a WinUSB-compatible driver; on Linux allow /dev/uinput and USB");
    println!("access; on macOS grant Accessibility permission to this program.");
}

#[derive(Debug)]
enum SessionError {
    Link(LinkError),
    Auth(String),
    Protocol(String),
    Io(io::Error),
    Interrupted,
}

impl fmt::Display for SessionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            SessionError::Link(error) => write!(f, "{error}"),
            SessionError::Auth(message) => write!(f, "authentication failed: {message}"),
            SessionError::Protocol(message) => write!(f, "protocol error: {message}"),
            SessionError::Io(error) => write!(f, "I/O error: {error}"),
            SessionError::Interrupted => write!(f, "interrupted"),
        }
    }
}

impl std::error::Error for SessionError {}

impl From<LinkError> for SessionError {
    fn from(value: LinkError) -> Self {
        SessionError::Link(value)
    }
}

impl From<io::Error> for SessionError {
    fn from(value: io::Error) -> Self {
        SessionError::Io(value)
    }
}

impl From<fluxpad_protocol::messages::MessageError> for SessionError {
    fn from(value: fluxpad_protocol::messages::MessageError) -> Self {
        SessionError::Protocol(value.to_string())
    }
}

impl From<fluxpad_shared::StoreError> for SessionError {
    fn from(value: fluxpad_shared::StoreError) -> Self {
        SessionError::Protocol(value.to_string())
    }
}

impl From<fluxpad_shared::InputError> for SessionError {
    fn from(value: fluxpad_shared::InputError) -> Self {
        SessionError::Protocol(value.to_string())
    }
}
