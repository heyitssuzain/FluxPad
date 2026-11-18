use crate::InputError;

pub const MOD_LEFT_CTRL: u8 = 0x01;
pub const MOD_LEFT_SHIFT: u8 = 0x02;
pub const MOD_LEFT_ALT: u8 = 0x04;
pub const MOD_LEFT_GUI: u8 = 0x08;
pub const MOD_RIGHT_CTRL: u8 = 0x10;
pub const MOD_RIGHT_SHIFT: u8 = 0x20;
pub const MOD_RIGHT_ALT: u8 = 0x40;
pub const MOD_RIGHT_GUI: u8 = 0x80;

pub const MOUSE_LEFT: u8 = 0x01;
pub const MOUSE_RIGHT: u8 = 0x02;
pub const MOUSE_MIDDLE: u8 = 0x04;
pub const MOUSE_BACK: u8 = 0x08;
pub const MOUSE_FORWARD: u8 = 0x10;

/// A platform input backend. All coordinates are relative and wheel values are
/// HID detents (±1), not OS-specific units.
pub trait InputSink {
    fn backend_name(&self) -> &'static str;
    fn mouse(&mut self, report: MouseReport) -> Result<(), InputError>;
    fn keyboard(&mut self, modifiers: u8, keys: &[u8]) -> Result<(), InputError>;
    fn consumer(&mut self, usage: u16) -> Result<(), InputError>;
    fn release_all(&mut self) -> Result<(), InputError>;
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct MouseReport {
    pub buttons: u8,
    pub dx: i16,
    pub dy: i16,
    pub wheel: i16,
    pub pan: i16,
}

impl MouseReport {
    pub fn is_neutral(&self) -> bool {
        self.dx == 0 && self.dy == 0 && self.wheel == 0 && self.pan == 0
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct KeyboardReport {
    pub modifiers: u8,
    pub keys: Vec<u8>,
}

pub fn parse_mouse(payload: &[u8]) -> Option<MouseReport> {
    if payload.len() < 9 {
        return None;
    }
    let read_i16 = |offset: usize| i16::from_le_bytes([payload[offset], payload[offset + 1]]);
    Some(MouseReport {
        buttons: payload[0],
        dx: read_i16(1),
        dy: read_i16(3),
        wheel: read_i16(5),
        pan: read_i16(7),
    })
}

pub fn parse_keyboard(payload: &[u8]) -> Option<KeyboardReport> {
    if payload.len() < 8 {
        return None;
    }
    let mut keys = Vec::with_capacity(6);
    for &key in &payload[2..8] {
        if key != 0 && !keys.contains(&key) {
            keys.push(key);
        }
    }
    Some(KeyboardReport {
        modifiers: payload[0],
        keys,
    })
}

pub fn parse_consumer(payload: &[u8]) -> Option<u16> {
    if payload.len() < 2 {
        return None;
    }
    Some(u16::from_le_bytes([payload[0], payload[1]]))
}

/// Returns `(released, pressed)` usages, deduplicated and in input order.
pub fn diff_keys(previous: &[u8], next: &[u8]) -> (Vec<u8>, Vec<u8>) {
    let released = previous
        .iter()
        .copied()
        .filter(|key| *key != 0 && !next.contains(key))
        .collect();
    let mut pressed = Vec::new();
    for &key in next {
        if key != 0 && !previous.contains(&key) && !pressed.contains(&key) {
            pressed.push(key);
        }
    }
    (released, pressed)
}

#[derive(Debug, Default)]
pub struct KeyboardState {
    pressed: Vec<u8>,
}

impl KeyboardState {
    pub fn pressed(&self) -> &[u8] {
        &self.pressed
    }

    pub fn update(&mut self, report: &KeyboardReport) -> (Vec<u8>, Vec<u8>) {
        let (released, pressed) = diff_keys(&self.pressed, &report.keys);
        self.pressed.clone_from(&report.keys);
        (released, pressed)
    }

    pub fn release_all(&mut self) -> Vec<u8> {
        std::mem::take(&mut self.pressed)
    }
}

#[derive(Debug, Default)]
pub struct MouseState {
    buttons: u8,
}

impl MouseState {
    pub fn buttons(&self) -> u8 {
        self.buttons
    }

    /// Returns `(released_mask, pressed_mask)`.
    pub fn update(&mut self, buttons: u8) -> (u8, u8) {
        let released = self.buttons & !buttons;
        let pressed = buttons & !self.buttons;
        self.buttons = buttons;
        (released, pressed)
    }

    pub fn release_all(&mut self) -> u8 {
        let previous = self.buttons;
        self.buttons = 0;
        previous
    }
}

pub fn consumer_name(usage: u16) -> &'static str {
    match usage {
        0x0000 => "release",
        0x0030 => "power",
        0x0032 => "sleep",
        0x0040 => "menu",
        0x006F => "brightness up",
        0x0070 => "brightness down",
        0x009C => "channel up",
        0x009D => "channel down",
        0x00B0 => "play",
        0x00B1 => "pause",
        0x00B2 => "record",
        0x00B3 => "fast forward",
        0x00B4 => "rewind",
        0x00B5 => "next track",
        0x00B6 => "previous track",
        0x00B7 => "stop",
        0x00B8 => "eject",
        0x00CD => "play / pause",
        0x00E2 => "mute",
        0x00E9 => "volume up",
        0x00EA => "volume down",
        0x0183 => "media select",
        0x018A => "email",
        0x0192 => "calculator",
        0x0196 => "browser",
        0x019E => "screen saver",
        0x0221 => "search",
        0x0223 => "home",
        0x0224 => "back",
        0x0225 => "forward",
        _ => "unknown consumer usage",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_mouse_report() {
        let payload = [0b101, 0xFE, 0xFF, 0x02, 0x00, 0xFF, 0xFF, 0x01, 0x00];
        let report = parse_mouse(&payload).unwrap();
        assert_eq!(report.buttons, 5);
        assert_eq!(report.dx, -2);
        assert_eq!(report.dy, 2);
        assert_eq!(report.wheel, -1);
        assert_eq!(report.pan, 1);
    }

    #[test]
    fn parses_keyboard_and_drops_zero_padding() {
        let payload = [0x02, 0, 0x04, 0x05, 0, 0, 0, 0];
        let report = parse_keyboard(&payload).unwrap();
        assert_eq!(report.modifiers, MOD_LEFT_SHIFT);
        assert_eq!(report.keys, vec![0x04, 0x05]);
    }

    #[test]
    fn diff_presses_and_releases_only_changes() {
        let (released, pressed) = diff_keys(&[0x04, 0x05], &[0x05, 0x06]);
        assert_eq!(released, vec![0x04]);
        assert_eq!(pressed, vec![0x06]);
    }

    #[test]
    fn keyboard_state_ignores_auto_repeat() {
        let mut state = KeyboardState::default();
        let report = KeyboardReport {
            modifiers: 0,
            keys: vec![0x04],
        };
        let (released, pressed) = state.update(&report);
        assert!(released.is_empty());
        assert_eq!(pressed, vec![0x04]);
        let (released, pressed) = state.update(&report);
        assert!(released.is_empty());
        assert!(pressed.is_empty());
        assert_eq!(state.release_all(), vec![0x04]);
    }

    #[test]
    fn mouse_button_diff() {
        let mut state = MouseState::default();
        assert_eq!(state.update(MOUSE_LEFT), (0, MOUSE_LEFT));
        assert_eq!(state.update(MOUSE_LEFT | MOUSE_RIGHT), (0, MOUSE_RIGHT));
        assert_eq!(state.release_all(), MOUSE_LEFT | MOUSE_RIGHT);
        assert_eq!(state.buttons(), 0);
    }

    #[test]
    fn consumer_names_are_stable() {
        assert_eq!(consumer_name(0x00CD), "play / pause");
        assert_eq!(consumer_name(0x00E9), "volume up");
        assert_eq!(consumer_name(0xFFFF), "unknown consumer usage");
    }
}
