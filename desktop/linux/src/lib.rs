#![cfg(target_os = "linux")]
//! Linux input backend using a `uinput` virtual device.
//!
//! The receiver creates a real kernel input device named "FLUXPAD Virtual
//! Controller". It requires write access to `/dev/uinput` (udev rule or the
//! `input` group). No X11 or Wayland compositor API is used, so it works in
//! games and native applications as well as on the desktop.

use evdev::uinput::VirtualDevice;
use evdev::{
    AttributeSet, InputEvent, KeyCode, KeyEvent, RelativeAxisCode, RelativeAxisEvent,
};
use fluxpad_shared::input::{
    KeyboardState, MouseState, MOUSE_BACK, MOUSE_FORWARD, MOUSE_LEFT, MOUSE_MIDDLE, MOUSE_RIGHT,
};
use fluxpad_shared::{consumer_name, InputError, InputSink, MouseReport};

pub struct LinuxSink {
    device: VirtualDevice,
    keyboard: KeyboardState,
    mouse: MouseState,
    modifiers: u8,
    consumer: Option<u16>,
}

impl LinuxSink {
    pub fn new() -> Result<Self, InputError> {
        let mut keys = AttributeSet::<KeyCode>::new();
        for usage in 0u8..=255 {
            if let Some(key) = usage_to_key(usage) {
                keys.insert(key);
            }
        }
        for usage in CONSUMER_USAGES {
            if let Some(key) = consumer_key(usage) {
                keys.insert(key);
            }
        }
        for button in [
            KeyCode::BTN_LEFT,
            KeyCode::BTN_RIGHT,
            KeyCode::BTN_MIDDLE,
            KeyCode::BTN_SIDE,
            KeyCode::BTN_EXTRA,
        ] {
            keys.insert(button);
        }
        let mut axes = AttributeSet::<RelativeAxisCode>::new();
        axes.insert(RelativeAxisCode::REL_X);
        axes.insert(RelativeAxisCode::REL_Y);
        axes.insert(RelativeAxisCode::REL_WHEEL);
        axes.insert(RelativeAxisCode::REL_HWHEEL);

        let device = VirtualDevice::builder()
            .map_err(|error| InputError::new(format!("uinput is unavailable: {error}")))?
            .name("FLUXPAD Virtual Controller")
            .with_keys(&keys)
            .map_err(|error| InputError::new(error.to_string()))?
            .with_relative_axes(&axes)
            .map_err(|error| InputError::new(error.to_string()))?
            .build()
            .map_err(|error| {
                InputError::new(format!(
                    "cannot create /dev/uinput device: {error}. \
                     Install a udev rule granting the input group write access to /dev/uinput."
                ))
            })?;
        Ok(Self {
            device,
            keyboard: KeyboardState::default(),
            mouse: MouseState::default(),
            modifiers: 0,
            consumer: None,
        })
    }

    fn emit(&mut self, events: Vec<InputEvent>) -> Result<(), InputError> {
        // VirtualDevice::emit appends SYN_REPORT itself.
        self.device
            .emit(&events)
            .map_err(|error| InputError::new(format!("uinput write failed: {error}")))
    }

    fn key_event(key: KeyCode, down: bool) -> InputEvent {
        KeyEvent::new(key, if down { 1 } else { 0 }).into()
    }
}

impl InputSink for LinuxSink {
    fn backend_name(&self) -> &'static str {
        "Linux uinput"
    }

    fn mouse(&mut self, report: MouseReport) -> Result<(), InputError> {
        let (released, pressed) = self.mouse.update(report.buttons);
        let mut events = Vec::new();
        for (mask, key) in [
            (MOUSE_LEFT, KeyCode::BTN_LEFT),
            (MOUSE_RIGHT, KeyCode::BTN_RIGHT),
            (MOUSE_MIDDLE, KeyCode::BTN_MIDDLE),
            (MOUSE_BACK, KeyCode::BTN_SIDE),
            (MOUSE_FORWARD, KeyCode::BTN_EXTRA),
        ] {
            if released & mask != 0 {
                events.push(Self::key_event(key, false));
            }
            if pressed & mask != 0 {
                events.push(Self::key_event(key, true));
            }
        }
        if report.dx != 0 {
            events.push(RelativeAxisEvent::new(RelativeAxisCode::REL_X, report.dx as i32).into());
        }
        if report.dy != 0 {
            events.push(RelativeAxisEvent::new(RelativeAxisCode::REL_Y, report.dy as i32).into());
        }
        if report.wheel != 0 {
            events.push(
                RelativeAxisEvent::new(RelativeAxisCode::REL_WHEEL, report.wheel as i32).into(),
            );
        }
        if report.pan != 0 {
            events.push(
                RelativeAxisEvent::new(RelativeAxisCode::REL_HWHEEL, report.pan as i32).into(),
            );
        }
        if events.is_empty() {
            Ok(())
        } else {
            self.emit(events)
        }
    }

    fn keyboard(&mut self, modifiers: u8, keys: &[u8]) -> Result<(), InputError> {
        let changed_on = modifiers & !self.modifiers;
        let changed_off = self.modifiers & !modifiers;
        let mut events = Vec::new();
        for (mask, key) in MODIFIER_KEYS {
            if changed_off & mask != 0 {
                events.push(Self::key_event(key, false));
            }
        }
        let report = fluxpad_shared::KeyboardReport {
            modifiers,
            keys: keys.to_vec(),
        };
        let (released, pressed) = self.keyboard.update(&report);
        for usage in released {
            if let Some(key) = usage_to_key(usage) {
                events.push(Self::key_event(key, false));
            }
        }
        for (mask, key) in MODIFIER_KEYS {
            if changed_on & mask != 0 {
                events.push(Self::key_event(key, true));
            }
        }
        for usage in pressed {
            let key = usage_to_key(usage).ok_or_else(|| {
                InputError::unsupported(format!("Linux has no mapping for HID usage 0x{usage:02X}"))
            })?;
            events.push(Self::key_event(key, true));
        }
        self.modifiers = modifiers;
        if events.is_empty() {
            Ok(())
        } else {
            self.emit(events)
        }
    }

    fn consumer(&mut self, usage: u16) -> Result<(), InputError> {
        if usage == 0 {
            if let Some(previous) = self.consumer.take() {
                if let Some(key) = consumer_key(previous) {
                    return self.emit(vec![Self::key_event(key, false)]);
                }
            }
            return Ok(());
        }
        let key = consumer_key(usage).ok_or_else(|| {
            InputError::unsupported(format!(
                "Linux cannot inject consumer control '{}' (0x{usage:04X})",
                consumer_name(usage)
            ))
        })?;
        let mut events = Vec::new();
        if let Some(previous) = self.consumer.take() {
            if let Some(previous_key) = consumer_key(previous) {
                events.push(Self::key_event(previous_key, false));
            }
        }
        events.push(Self::key_event(key, true));
        self.consumer = Some(usage);
        self.emit(events)
    }

    fn release_all(&mut self) -> Result<(), InputError> {
        let mut events = Vec::new();
        for usage in self.keyboard.release_all() {
            if let Some(key) = usage_to_key(usage) {
                events.push(Self::key_event(key, false));
            }
        }
        for (mask, key) in MODIFIER_KEYS {
            if self.modifiers & mask != 0 {
                events.push(Self::key_event(key, false));
            }
        }
        self.modifiers = 0;
        let released_buttons = self.mouse.release_all();
        for (mask, key) in [
            (MOUSE_LEFT, KeyCode::BTN_LEFT),
            (MOUSE_RIGHT, KeyCode::BTN_RIGHT),
            (MOUSE_MIDDLE, KeyCode::BTN_MIDDLE),
            (MOUSE_BACK, KeyCode::BTN_SIDE),
            (MOUSE_FORWARD, KeyCode::BTN_EXTRA),
        ] {
            if released_buttons & mask != 0 {
                events.push(Self::key_event(key, false));
            }
        }
        if let Some(usage) = self.consumer.take() {
            if let Some(key) = consumer_key(usage) {
                events.push(Self::key_event(key, false));
            }
        }
        if events.is_empty() {
            Ok(())
        } else {
            self.emit(events)
        }
    }
}

const MODIFIER_KEYS: [(u8, KeyCode); 8] = [
    (fluxpad_shared::input::MOD_LEFT_CTRL, KeyCode::KEY_LEFTCTRL),
    (fluxpad_shared::input::MOD_LEFT_SHIFT, KeyCode::KEY_LEFTSHIFT),
    (fluxpad_shared::input::MOD_LEFT_ALT, KeyCode::KEY_LEFTALT),
    (fluxpad_shared::input::MOD_LEFT_GUI, KeyCode::KEY_LEFTMETA),
    (fluxpad_shared::input::MOD_RIGHT_CTRL, KeyCode::KEY_RIGHTCTRL),
    (fluxpad_shared::input::MOD_RIGHT_SHIFT, KeyCode::KEY_RIGHTSHIFT),
    (fluxpad_shared::input::MOD_RIGHT_ALT, KeyCode::KEY_RIGHTALT),
    (fluxpad_shared::input::MOD_RIGHT_GUI, KeyCode::KEY_RIGHTMETA),
];

const CONSUMER_USAGES: [u16; 30] = [
    0x0030, 0x0032, 0x0040, 0x006F, 0x0070, 0x009C, 0x009D, 0x00B0, 0x00B1, 0x00B2,
    0x00B3, 0x00B4, 0x00B5, 0x00B6, 0x00B7, 0x00B8, 0x00CD, 0x00E2, 0x00E9, 0x00EA,
    0x0183, 0x018A, 0x0192, 0x0196, 0x019E, 0x0221, 0x0223, 0x0224, 0x0225, 0x0000,
];

fn usage_to_key(usage: u8) -> Option<KeyCode> {
    Some(match usage {
        0x04..=0x1D => [
            KeyCode::KEY_A,
            KeyCode::KEY_B,
            KeyCode::KEY_C,
            KeyCode::KEY_D,
            KeyCode::KEY_E,
            KeyCode::KEY_F,
            KeyCode::KEY_G,
            KeyCode::KEY_H,
            KeyCode::KEY_I,
            KeyCode::KEY_J,
            KeyCode::KEY_K,
            KeyCode::KEY_L,
            KeyCode::KEY_M,
            KeyCode::KEY_N,
            KeyCode::KEY_O,
            KeyCode::KEY_P,
            KeyCode::KEY_Q,
            KeyCode::KEY_R,
            KeyCode::KEY_S,
            KeyCode::KEY_T,
            KeyCode::KEY_U,
            KeyCode::KEY_V,
            KeyCode::KEY_W,
            KeyCode::KEY_X,
            KeyCode::KEY_Y,
            KeyCode::KEY_Z,
        ][(usage - 0x04) as usize],
        0x1E => KeyCode::KEY_1,
        0x1F => KeyCode::KEY_2,
        0x20 => KeyCode::KEY_3,
        0x21 => KeyCode::KEY_4,
        0x22 => KeyCode::KEY_5,
        0x23 => KeyCode::KEY_6,
        0x24 => KeyCode::KEY_7,
        0x25 => KeyCode::KEY_8,
        0x26 => KeyCode::KEY_9,
        0x27 => KeyCode::KEY_0,
        0x28 => KeyCode::KEY_ENTER,
        0x29 => KeyCode::KEY_ESC,
        0x2A => KeyCode::KEY_BACKSPACE,
        0x2B => KeyCode::KEY_TAB,
        0x2C => KeyCode::KEY_SPACE,
        0x2D => KeyCode::KEY_MINUS,
        0x2E => KeyCode::KEY_EQUAL,
        0x2F => KeyCode::KEY_LEFTBRACE,
        0x30 => KeyCode::KEY_RIGHTBRACE,
        0x31 => KeyCode::KEY_BACKSLASH,
        0x32 => KeyCode::KEY_102ND,
        0x33 => KeyCode::KEY_SEMICOLON,
        0x34 => KeyCode::KEY_APOSTROPHE,
        0x35 => KeyCode::KEY_GRAVE,
        0x36 => KeyCode::KEY_COMMA,
        0x37 => KeyCode::KEY_DOT,
        0x38 => KeyCode::KEY_SLASH,
        0x39 => KeyCode::KEY_CAPSLOCK,
        0x3A..=0x45 => [
            KeyCode::KEY_F1,
            KeyCode::KEY_F2,
            KeyCode::KEY_F3,
            KeyCode::KEY_F4,
            KeyCode::KEY_F5,
            KeyCode::KEY_F6,
            KeyCode::KEY_F7,
            KeyCode::KEY_F8,
            KeyCode::KEY_F9,
            KeyCode::KEY_F10,
            KeyCode::KEY_F11,
            KeyCode::KEY_F12,
        ][(usage - 0x3A) as usize],
        0x46 => KeyCode::KEY_SYSRQ,
        0x47 => KeyCode::KEY_SCROLLLOCK,
        0x48 => KeyCode::KEY_PAUSE,
        0x49 => KeyCode::KEY_INSERT,
        0x4A => KeyCode::KEY_HOME,
        0x4B => KeyCode::KEY_PAGEUP,
        0x4C => KeyCode::KEY_DELETE,
        0x4D => KeyCode::KEY_END,
        0x4E => KeyCode::KEY_PAGEDOWN,
        0x4F => KeyCode::KEY_RIGHT,
        0x50 => KeyCode::KEY_LEFT,
        0x51 => KeyCode::KEY_DOWN,
        0x52 => KeyCode::KEY_UP,
        0x53 => KeyCode::KEY_NUMLOCK,
        0x54 => KeyCode::KEY_KPSLASH,
        0x55 => KeyCode::KEY_KPASTERISK,
        0x56 => KeyCode::KEY_KPMINUS,
        0x57 => KeyCode::KEY_KPPLUS,
        0x58 => KeyCode::KEY_KPENTER,
        0x59..=0x61 => [
            KeyCode::KEY_KP1,
            KeyCode::KEY_KP2,
            KeyCode::KEY_KP3,
            KeyCode::KEY_KP4,
            KeyCode::KEY_KP5,
            KeyCode::KEY_KP6,
            KeyCode::KEY_KP7,
            KeyCode::KEY_KP8,
            KeyCode::KEY_KP9,
        ][(usage - 0x59) as usize],
        0x62 => KeyCode::KEY_KP0,
        0x63 => KeyCode::KEY_KPDOT,
        0x64 => KeyCode::KEY_102ND,
        0x65 => KeyCode::KEY_COMPOSE,
        0x66 => KeyCode::KEY_POWER,
        0x67 => KeyCode::KEY_KPEQUAL,
        0x68..=0x6F => [
            KeyCode::KEY_F13,
            KeyCode::KEY_F14,
            KeyCode::KEY_F15,
            KeyCode::KEY_F16,
            KeyCode::KEY_F17,
            KeyCode::KEY_F18,
            KeyCode::KEY_F19,
            KeyCode::KEY_F20,
        ][(usage - 0x68) as usize],
        _ => return None,
    })
}

fn consumer_key(usage: u16) -> Option<KeyCode> {
    Some(match usage {
        0x0030 => KeyCode::KEY_POWER,
        0x0032 => KeyCode::KEY_SLEEP,
        0x0040 => KeyCode::KEY_MENU,
        0x006F => KeyCode::KEY_BRIGHTNESSUP,
        0x0070 => KeyCode::KEY_BRIGHTNESSDOWN,
        0x009C => KeyCode::KEY_CHANNELUP,
        0x009D => KeyCode::KEY_CHANNELDOWN,
        0x00B0 => KeyCode::KEY_PLAY,
        0x00B1 => KeyCode::KEY_PAUSE,
        0x00B2 => KeyCode::KEY_RECORD,
        0x00B3 => KeyCode::KEY_FASTFORWARD,
        0x00B4 => KeyCode::KEY_REWIND,
        0x00B5 => KeyCode::KEY_NEXTSONG,
        0x00B6 => KeyCode::KEY_PREVIOUSSONG,
        0x00B7 => KeyCode::KEY_STOPCD,
        0x00B8 => KeyCode::KEY_EJECTCD,
        0x00CD => KeyCode::KEY_PLAYPAUSE,
        0x00E2 => KeyCode::KEY_MUTE,
        0x00E9 => KeyCode::KEY_VOLUMEUP,
        0x00EA => KeyCode::KEY_VOLUMEDOWN,
        0x0183 => KeyCode::KEY_MEDIA,
        0x018A => KeyCode::KEY_EMAIL,
        0x0192 => KeyCode::KEY_CALC,
        0x0196 => KeyCode::KEY_HOMEPAGE,
        0x019E => KeyCode::KEY_SLEEP,
        0x0221 => KeyCode::KEY_SEARCH,
        0x0223 => KeyCode::KEY_HOMEPAGE,
        0x0224 => KeyCode::KEY_BACK,
        0x0225 => KeyCode::KEY_FORWARD,
        _ => return None,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_letters_and_navigation() {
        assert_eq!(usage_to_key(0x04), Some(KeyCode::KEY_A));
        assert_eq!(usage_to_key(0x1D), Some(KeyCode::KEY_Z));
        assert_eq!(usage_to_key(0x28), Some(KeyCode::KEY_ENTER));
        assert_eq!(usage_to_key(0x4F), Some(KeyCode::KEY_RIGHT));
    }

    #[test]
    fn maps_media_keys() {
        assert_eq!(consumer_key(0x00CD), Some(KeyCode::KEY_PLAYPAUSE));
        assert_eq!(consumer_key(0x00E9), Some(KeyCode::KEY_VOLUMEUP));
        assert_eq!(consumer_key(0xFFFF), None);
    }
}
