#![cfg(target_os = "macos")]
//! macOS input backend built on CoreGraphics events.
//!
//! macOS requires the receiver to be granted Accessibility permission
//! (System Settings → Privacy & Security → Accessibility). Without it,
//! CoreGraphics silently drops synthetic events, so the backend refuses to
//! start rather than pretending input worked.

use std::ffi::c_void;

use core_graphics::event::{CGEvent, CGEventFlags, CGEventTapLocation};
use core_graphics::event_source::{CGEventSource, CGEventSourceStateID};
use fluxpad_shared::input::{
    KeyboardState, MouseState, MOUSE_BACK, MOUSE_FORWARD, MOUSE_LEFT, MOUSE_MIDDLE, MOUSE_RIGHT,
};
use fluxpad_shared::{consumer_name, InputError, InputSink, MouseReport};

pub struct MacSink {
    source: CGEventSource,
    keyboard: KeyboardState,
    mouse: MouseState,
    modifiers: u8,
    mouse_buttons: u8,
    consumer: Option<u16>,
}

impl MacSink {
    pub fn new() -> Result<Self, InputError> {
        if unsafe { AXIsProcessTrusted() } == 0 {
            return Err(InputError::new(
                "Accessibility permission is required. Open System Settings → Privacy & Security → \
                 Accessibility and enable the FLUXPAD receiver, then restart it.",
            ));
        }
        let source = CGEventSource::new(CGEventSourceStateID::HIDSystemState).map_err(|_| {
            InputError::new("CoreGraphics event source is unavailable")
        })?;
        Ok(Self {
            source,
            keyboard: KeyboardState::default(),
            mouse: MouseState::default(),
            modifiers: 0,
            mouse_buttons: 0,
            consumer: None,
        })
    }

    fn flags(&self) -> CGEventFlags {
        flags_for(self.modifiers)
    }

    fn post_key(&self, keycode: u16, down: bool, flags: CGEventFlags) -> Result<(), InputError> {
        let event = CGEvent::new_keyboard_event(self.source.clone(), keycode, down)
            .map_err(|_| InputError::new("CoreGraphics refused a keyboard event"))?;
        event.set_flags(flags);
        event.post(CGEventTapLocation::HID);
        Ok(())
    }

    fn post_modifier(&self, keycode: u16, down: bool, flag: CGEventFlags) -> Result<(), InputError> {
        let mut flags = self.flags();
        if down {
            flags |= flag;
        } else {
            flags.remove(flag);
        }
        self.post_key(keycode, down, flags)
    }

    fn post_mouse(&self, event_type: u32, x: f64, y: f64, button: u32) -> Result<(), InputError> {
        unsafe {
            let event = CGEventCreateMouseEvent(
                std::ptr::null(),
                event_type,
                CGPointRaw { x, y },
                button,
            );
            if event.is_null() {
                return Err(InputError::new("CoreGraphics refused a mouse event"));
            }
            CGEventSetFlags(event, self.flags().bits());
            CGEventPost(HID_EVENT_TAP, event);
            CFRelease(event as *const c_void);
        }
        Ok(())
    }

    fn post_media(&self, key: u32, down: bool) -> Result<(), InputError> {
        let data1 = ((key as i64) << 16) | if down { 0x0A00 } else { 0x0B00 };
        unsafe {
            let event = CGEventCreate(std::ptr::null());
            if event.is_null() {
                return Err(InputError::new("CoreGraphics refused a media event"));
            }
            CGEventSetIntegerValueField(event, 0, data1);
            CGEventSetIntegerValueField(event, 1, key as i64);
            CGEventSetType(event, SYSTEM_DEFINED);
            CGEventPost(HID_EVENT_TAP, event);
            CFRelease(event as *const c_void);
        }
        Ok(())
    }
}

impl InputSink for MacSink {
    fn backend_name(&self) -> &'static str {
        "macOS CoreGraphics"
    }

    fn mouse(&mut self, report: MouseReport) -> Result<(), InputError> {
        let (released, pressed) = self.mouse.update(report.buttons);
        let (x, y) = cursor_position()?;
        for (mask, event_type, button) in [
            (MOUSE_LEFT, LEFT_MOUSE_UP, 0u32),
            (MOUSE_RIGHT, RIGHT_MOUSE_UP, 1),
            (MOUSE_MIDDLE, OTHER_MOUSE_UP, 2),
            (MOUSE_BACK, OTHER_MOUSE_UP, 3),
            (MOUSE_FORWARD, OTHER_MOUSE_UP, 4),
        ] {
            if released & mask != 0 {
                self.post_mouse(event_type, x, y, button)?;
            }
        }
        for (mask, event_type, button) in [
            (MOUSE_LEFT, LEFT_MOUSE_DOWN, 0u32),
            (MOUSE_RIGHT, RIGHT_MOUSE_DOWN, 1),
            (MOUSE_MIDDLE, OTHER_MOUSE_DOWN, 2),
            (MOUSE_BACK, OTHER_MOUSE_DOWN, 3),
            (MOUSE_FORWARD, OTHER_MOUSE_DOWN, 4),
        ] {
            if pressed & mask != 0 {
                self.post_mouse(event_type, x, y, button)?;
            }
        }
        self.mouse_buttons = report.buttons;
        if report.dx != 0 || report.dy != 0 {
            self.post_mouse(
                MOUSE_MOVED,
                x + report.dx as f64,
                y + report.dy as f64,
                0,
            )?;
        }
        if report.wheel != 0 || report.pan != 0 {
            unsafe {
                let event = CGEventCreateScrollWheelEvent2(
                    std::ptr::null(),
                    SCROLL_UNIT_LINE,
                    2,
                    report.wheel as i32,
                    report.pan as i32,
                    0,
                );
                if event.is_null() {
                    return Err(InputError::new("CoreGraphics refused a scroll event"));
                }
                CGEventSetFlags(event, self.flags().bits());
                CGEventPost(HID_EVENT_TAP, event);
                CFRelease(event as *const c_void);
            }
        }
        Ok(())
    }

    fn keyboard(&mut self, modifiers: u8, keys: &[u8]) -> Result<(), InputError> {
        let changed_on = modifiers & !self.modifiers;
        let changed_off = self.modifiers & !modifiers;
        for (mask, keycode, flag) in MODIFIER_KEYS {
            if changed_off & mask != 0 {
                self.post_modifier(keycode, false, flag)?;
            }
        }
        let report = fluxpad_shared::KeyboardReport {
            modifiers,
            keys: keys.to_vec(),
        };
        let (released, pressed) = self.keyboard.update(&report);
        for usage in released {
            let keycode = usage_to_keycode(usage).ok_or_else(|| {
                InputError::unsupported(format!("macOS has no mapping for HID usage 0x{usage:02X}"))
            })?;
            self.post_key(keycode, false, self.flags())?;
        }
        for (mask, keycode, flag) in MODIFIER_KEYS {
            if changed_on & mask != 0 {
                self.post_modifier(keycode, true, flag)?;
            }
        }
        for usage in pressed {
            let keycode = usage_to_keycode(usage).ok_or_else(|| {
                InputError::unsupported(format!("macOS has no mapping for HID usage 0x{usage:02X}"))
            })?;
            self.post_key(keycode, true, self.flags())?;
        }
        self.modifiers = modifiers;
        Ok(())
    }

    fn consumer(&mut self, usage: u16) -> Result<(), InputError> {
        if usage == 0 {
            if let Some(previous) = self.consumer.take() {
                if let Some(key) = consumer_media_key(previous) {
                    self.post_media(key, false)?;
                }
            }
            return Ok(());
        }
        let key = consumer_media_key(usage).ok_or_else(|| {
            InputError::unsupported(format!(
                "macOS cannot inject consumer control '{}' (0x{usage:04X})",
                consumer_name(usage)
            ))
        })?;
        if let Some(previous) = self.consumer.take() {
            if let Some(previous_key) = consumer_media_key(previous) {
                self.post_media(previous_key, false)?;
            }
        }
        self.post_media(key, true)?;
        self.consumer = Some(usage);
        Ok(())
    }

    fn release_all(&mut self) -> Result<(), InputError> {
        for usage in self.keyboard.release_all() {
            if let Some(keycode) = usage_to_keycode(usage) {
                let _ = self.post_key(keycode, false, self.flags());
            }
        }
        for (mask, keycode, flag) in MODIFIER_KEYS {
            if self.modifiers & mask != 0 {
                let _ = self.post_modifier(keycode, false, flag);
            }
        }
        self.modifiers = 0;
        let (x, y) = cursor_position().unwrap_or((0.0, 0.0));
        for (mask, button) in [
            (MOUSE_LEFT, 0u32),
            (MOUSE_RIGHT, 1),
            (MOUSE_MIDDLE, 2),
            (MOUSE_BACK, 3),
            (MOUSE_FORWARD, 4),
        ] {
            if self.mouse_buttons & mask != 0 {
                let up = if button == 0 {
                    LEFT_MOUSE_UP
                } else if button == 1 {
                    RIGHT_MOUSE_UP
                } else {
                    OTHER_MOUSE_UP
                };
                let _ = self.post_mouse(up, x, y, button);
            }
        }
        self.mouse_buttons = 0;
        self.mouse.release_all();
        if let Some(usage) = self.consumer.take() {
            if let Some(key) = consumer_media_key(usage) {
                let _ = self.post_media(key, false);
            }
        }
        Ok(())
    }
}

const MODIFIER_KEYS: [(u8, u16, CGEventFlags); 8] = [
    (fluxpad_shared::input::MOD_LEFT_CTRL, 0x3B, CGEventFlags::CGEventFlagControl),
    (fluxpad_shared::input::MOD_LEFT_SHIFT, 0x38, CGEventFlags::CGEventFlagShift),
    (fluxpad_shared::input::MOD_LEFT_ALT, 0x3A, CGEventFlags::CGEventFlagAlternate),
    (fluxpad_shared::input::MOD_LEFT_GUI, 0x37, CGEventFlags::CGEventFlagCommand),
    (fluxpad_shared::input::MOD_RIGHT_CTRL, 0x3E, CGEventFlags::CGEventFlagControl),
    (fluxpad_shared::input::MOD_RIGHT_SHIFT, 0x3C, CGEventFlags::CGEventFlagShift),
    (fluxpad_shared::input::MOD_RIGHT_ALT, 0x3D, CGEventFlags::CGEventFlagAlternate),
    (fluxpad_shared::input::MOD_RIGHT_GUI, 0x36, CGEventFlags::CGEventFlagCommand),
];

fn flags_for(modifiers: u8) -> CGEventFlags {
    let mut flags = CGEventFlags::empty();
    if modifiers & (fluxpad_shared::input::MOD_LEFT_SHIFT | fluxpad_shared::input::MOD_RIGHT_SHIFT) != 0 {
        flags |= CGEventFlags::CGEventFlagShift;
    }
    if modifiers & (fluxpad_shared::input::MOD_LEFT_CTRL | fluxpad_shared::input::MOD_RIGHT_CTRL) != 0 {
        flags |= CGEventFlags::CGEventFlagControl;
    }
    if modifiers & (fluxpad_shared::input::MOD_LEFT_ALT | fluxpad_shared::input::MOD_RIGHT_ALT) != 0 {
        flags |= CGEventFlags::CGEventFlagAlternate;
    }
    if modifiers & (fluxpad_shared::input::MOD_LEFT_GUI | fluxpad_shared::input::MOD_RIGHT_GUI) != 0 {
        flags |= CGEventFlags::CGEventFlagCommand;
    }
    flags
}

fn usage_to_keycode(usage: u8) -> Option<u16> {
    Some(match usage {
        0x04 => 0x00, // A
        0x05 => 0x0B, // B
        0x06 => 0x08, // C
        0x07 => 0x02, // D
        0x08 => 0x0E, // E
        0x09 => 0x03, // F
        0x0A => 0x05, // G
        0x0B => 0x04, // H
        0x0C => 0x22, // I
        0x0D => 0x26, // J
        0x0E => 0x28, // K
        0x0F => 0x25, // L
        0x10 => 0x2E, // M
        0x11 => 0x2D, // N
        0x12 => 0x1F, // O
        0x13 => 0x23, // P
        0x14 => 0x0C, // Q
        0x15 => 0x0F, // R
        0x16 => 0x01, // S
        0x17 => 0x11, // T
        0x18 => 0x20, // U
        0x19 => 0x09, // V
        0x1A => 0x0D, // W
        0x1B => 0x07, // X
        0x1C => 0x10, // Y
        0x1D => 0x06, // Z
        0x1E => 0x12, // 1
        0x1F => 0x13, // 2
        0x20 => 0x14, // 3
        0x21 => 0x15, // 4
        0x22 => 0x17, // 5
        0x23 => 0x16, // 6
        0x24 => 0x1A, // 7
        0x25 => 0x1C, // 8
        0x26 => 0x19, // 9
        0x27 => 0x1D, // 0
        0x28 => 0x24, // Return
        0x29 => 0x35, // Escape
        0x2A => 0x33, // Delete (backspace)
        0x2B => 0x30, // Tab
        0x2C => 0x31, // Space
        0x2D => 0x1B, // -
        0x2E => 0x18, // =
        0x2F => 0x21, // [
        0x30 => 0x1E, // ]
        0x31 => 0x2A, // backslash
        0x32 => 0x32, // non-US #
        0x33 => 0x29, // ;
        0x34 => 0x27, // '
        0x35 => 0x32, // `
        0x36 => 0x2B, // ,
        0x37 => 0x2F, // .
        0x38 => 0x2C, // /
        0x39 => 0x39, // Caps Lock
        0x3A => 0x7A, // F1
        0x3B => 0x78, // F2
        0x3C => 0x63, // F3
        0x3D => 0x76, // F4
        0x3E => 0x60, // F5
        0x3F => 0x61, // F6
        0x40 => 0x62, // F7
        0x41 => 0x64, // F8
        0x42 => 0x65, // F9
        0x43 => 0x6D, // F10
        0x44 => 0x67, // F11
        0x45 => 0x6F, // F12
        0x49 => 0x72, // Insert -> Help
        0x4A => 0x73, // Home
        0x4B => 0x74, // Page Up
        0x4C => 0x75, // Forward Delete
        0x4D => 0x77, // End
        0x4E => 0x79, // Page Down
        0x4F => 0x7C, // Right
        0x50 => 0x7B, // Left
        0x51 => 0x7D, // Down
        0x52 => 0x7E, // Up
        0x54 => 0x4B, // Keypad /
        0x55 => 0x43, // Keypad *
        0x56 => 0x4E, // Keypad -
        0x57 => 0x45, // Keypad +
        0x58 => 0x4C, // Keypad Enter
        0x59 => 0x53, // Keypad 1
        0x5A => 0x54, // Keypad 2
        0x5B => 0x55, // Keypad 3
        0x5C => 0x56, // Keypad 4
        0x5D => 0x57, // Keypad 5
        0x5E => 0x58, // Keypad 6
        0x5F => 0x59, // Keypad 7
        0x60 => 0x5B, // Keypad 8
        0x61 => 0x5C, // Keypad 9
        0x62 => 0x52, // Keypad 0
        0x63 => 0x41, // Keypad .
        0x64 => 0x0A, // Non-US backslash
        0x65 => 0x6E, // Application / menu
        0x67 => 0x51, // Keypad =
        _ => return None,
    })
}

fn consumer_media_key(usage: u16) -> Option<u32> {
    Some(match usage {
        0x0030 => 0x06, // power
        0x0032 => 0x06, // sleep / power
        0x006F => 0x02, // brightness up
        0x0070 => 0x03, // brightness down
        0x00B0 | 0x00B1 | 0x00CD => 0x10, // play/pause
        0x00B5 => 0x11, // next
        0x00B6 => 0x12, // previous
        0x00B8 => 0x0E, // eject
        0x00E2 => 0x07, // mute
        0x00E9 => 0x00, // volume up
        0x00EA => 0x01, // volume down
        _ => return None,
    })
}

fn cursor_position() -> Result<(f64, f64), InputError> {
    unsafe {
        let event = CGEventCreate(std::ptr::null());
        if event.is_null() {
            return Err(InputError::new("CoreGraphics cannot read the cursor position"));
        }
        let point = CGEventGetLocation(event);
        CFRelease(event as *const c_void);
        Ok((point.x, point.y))
    }
}

#[repr(C)]
struct CGPointRaw {
    x: f64,
    y: f64,
}

const HID_EVENT_TAP: u32 = 0;
const MOUSE_MOVED: u32 = 5;
const LEFT_MOUSE_DOWN: u32 = 1;
const LEFT_MOUSE_UP: u32 = 2;
const RIGHT_MOUSE_DOWN: u32 = 3;
const RIGHT_MOUSE_UP: u32 = 4;
const OTHER_MOUSE_DOWN: u32 = 25;
const OTHER_MOUSE_UP: u32 = 26;
const SYSTEM_DEFINED: u32 = 14;
const SCROLL_UNIT_LINE: u32 = 1;

#[link(name = "CoreGraphics", kind = "framework")]
extern "C" {
    fn CGEventCreate(source: *const c_void) -> *mut c_void;
    fn CGEventGetLocation(event: *mut c_void) -> CGPointRaw;
    fn CGEventCreateMouseEvent(
        source: *const c_void,
        mouse_type: u32,
        mouse_cursor_position: CGPointRaw,
        mouse_button: u32,
    ) -> *mut c_void;
    fn CGEventPost(tap: u32, event: *mut c_void);
    fn CGEventSetFlags(event: *mut c_void, flags: u64);
    fn CGEventSetIntegerValueField(event: *mut c_void, field: u32, value: i64);
    fn CGEventSetType(event: *mut c_void, event_type: u32);
    fn CGEventCreateScrollWheelEvent2(
        source: *const c_void,
        units: u32,
        wheel_count: u32,
        ...
    ) -> *mut c_void;
}

#[link(name = "CoreFoundation", kind = "framework")]
extern "C" {
    fn CFRelease(cf: *const c_void);
}

#[link(name = "ApplicationServices", kind = "framework")]
extern "C" {
    fn AXIsProcessTrusted() -> u8;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_letters_and_navigation() {
        assert_eq!(usage_to_keycode(0x04), Some(0x00));
        assert_eq!(usage_to_keycode(0x1D), Some(0x06));
        assert_eq!(usage_to_keycode(0x28), Some(0x24));
        assert_eq!(usage_to_keycode(0x4F), Some(0x7C));
    }

    #[test]
    fn maps_modifiers_to_flags() {
        let flags = flags_for(fluxpad_shared::input::MOD_LEFT_SHIFT);
        assert!(flags.contains(CGEventFlags::CGEventFlagShift));
        assert!(!flags.contains(CGEventFlags::CGEventFlagCommand));
    }

    #[test]
    fn maps_media_keys() {
        assert_eq!(consumer_media_key(0x00CD), Some(0x10));
        assert_eq!(consumer_media_key(0x00E9), Some(0x00));
        assert_eq!(consumer_media_key(0xFFFF), None);
    }
}
