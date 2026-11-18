#![cfg(windows)]
//! Windows input backend built on `SendInput`.
//!
//! This is the same API used by hardware remotes and accessibility tools. It
//! requires an interactive desktop session; it does not inject at the kernel
//! level and cannot control the secure desktop (UAC, lock screen).

use fluxpad_shared::input::{
    KeyboardState, MouseState, MOUSE_BACK, MOUSE_FORWARD, MOUSE_LEFT, MOUSE_MIDDLE, MOUSE_RIGHT,
};
use fluxpad_shared::{consumer_name, InputError, InputSink, MouseReport};
use windows_sys::Win32::UI::Input::KeyboardAndMouse::{
    MapVirtualKeyW, SendInput, INPUT, INPUT_KEYBOARD, INPUT_MOUSE, KEYBDINPUT,
    KEYEVENTF_EXTENDEDKEY, KEYEVENTF_KEYUP, KEYEVENTF_SCANCODE, MAPVK_VK_TO_VSC,
    MOUSEEVENTF_HWHEEL, MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP, MOUSEEVENTF_MIDDLEDOWN,
    MOUSEEVENTF_MIDDLEUP, MOUSEEVENTF_MOVE, MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP,
    MOUSEEVENTF_WHEEL, MOUSEEVENTF_XDOWN, MOUSEEVENTF_XUP, MOUSEINPUT,
};
use windows_sys::Win32::UI::WindowsAndMessaging::{XBUTTON1, XBUTTON2};

pub struct WindowsSink {
    keyboard: KeyboardState,
    mouse: MouseState,
    modifiers: u8,
}

impl WindowsSink {
    pub fn new() -> Self {
        Self {
            keyboard: KeyboardState::default(),
            mouse: MouseState::default(),
            modifiers: 0,
        }
    }
}

impl Default for WindowsSink {
    fn default() -> Self {
        Self::new()
    }
}

impl InputSink for WindowsSink {
    fn backend_name(&self) -> &'static str {
        "Windows SendInput"
    }

    fn mouse(&mut self, report: MouseReport) -> Result<(), InputError> {
        let (released, pressed) = self.mouse.update(report.buttons);
        for bit in [MOUSE_LEFT, MOUSE_RIGHT, MOUSE_MIDDLE, MOUSE_BACK, MOUSE_FORWARD] {
            if released & bit != 0 {
                send_mouse_button(bit, false)?;
            }
        }
        for bit in [MOUSE_LEFT, MOUSE_RIGHT, MOUSE_MIDDLE, MOUSE_BACK, MOUSE_FORWARD] {
            if pressed & bit != 0 {
                send_mouse_button(bit, true)?;
            }
        }
        if report.dx != 0 || report.dy != 0 {
            send_mouse_move(report.dx, report.dy)?;
        }
        if report.wheel != 0 {
            send_wheel(report.wheel, false)?;
        }
        if report.pan != 0 {
            send_wheel(report.pan, true)?;
        }
        Ok(())
    }

    fn keyboard(&mut self, modifiers: u8, keys: &[u8]) -> Result<(), InputError> {
        let changed_on = modifiers & !self.modifiers;
        let changed_off = self.modifiers & !modifiers;
        // Modifiers must go down before the key they modify. Sending the key
        // first turns Ctrl+C into "C, then Ctrl" and the shortcut is missed.
        for (bit, vk) in MODIFIER_KEYS {
            if changed_on & bit != 0 {
                send_vk(vk, true)?;
            }
        }
        let report = fluxpad_shared::KeyboardReport {
            modifiers,
            keys: keys.to_vec(),
        };
        let (released, pressed) = self.keyboard.update(&report);
        for usage in released {
            send_usage(usage, false)?;
        }
        for usage in pressed {
            send_usage(usage, true)?;
        }
        for (bit, vk) in MODIFIER_KEYS {
            if changed_off & bit != 0 {
                send_vk(vk, false)?;
            }
        }
        self.modifiers = modifiers;
        Ok(())
    }

    fn consumer(&mut self, usage: u16) -> Result<(), InputError> {
        if usage == 0 {
            return Ok(());
        }
        let vk = consumer_vk(usage).ok_or_else(|| {
            InputError::unsupported(format!(
                "Windows cannot inject consumer control '{}' (0x{usage:04X})",
                consumer_name(usage)
            ))
        })?;
        send_vk(vk, true)?;
        send_vk(vk, false)
    }

    fn release_all(&mut self) -> Result<(), InputError> {
        for usage in self.keyboard.release_all() {
            let _ = send_usage(usage, false);
        }
        for (bit, vk) in MODIFIER_KEYS {
            if self.modifiers & bit != 0 {
                let _ = send_vk(vk, false);
            }
        }
        self.modifiers = 0;
        let buttons = self.mouse.release_all();
        for bit in [MOUSE_LEFT, MOUSE_RIGHT, MOUSE_MIDDLE, MOUSE_BACK, MOUSE_FORWARD] {
            if buttons & bit != 0 {
                let _ = send_mouse_button(bit, false);
            }
        }
        Ok(())
    }
}

const MODIFIER_KEYS: [(u8, u16); 8] = [
    (fluxpad_shared::input::MOD_LEFT_CTRL, 0xA2),
    (fluxpad_shared::input::MOD_LEFT_SHIFT, 0xA0),
    (fluxpad_shared::input::MOD_LEFT_ALT, 0xA4),
    (fluxpad_shared::input::MOD_LEFT_GUI, 0x5B),
    (fluxpad_shared::input::MOD_RIGHT_CTRL, 0xA3),
    (fluxpad_shared::input::MOD_RIGHT_SHIFT, 0xA1),
    (fluxpad_shared::input::MOD_RIGHT_ALT, 0xA5),
    (fluxpad_shared::input::MOD_RIGHT_GUI, 0x5C),
];

fn usage_to_vk(usage: u8) -> Option<u16> {
    Some(match usage {
        0x04..=0x1D => 0x41 + (usage - 0x04) as u16,
        0x1E..=0x26 => 0x31 + (usage - 0x1E) as u16,
        0x27 => 0x30,
        0x28 => 0x0D,
        0x29 => 0x1B,
        0x2A => 0x08,
        0x2B => 0x09,
        0x2C => 0x20,
        0x2D => 0xBD,
        0x2E => 0xBB,
        0x2F => 0xDB,
        0x30 => 0xDD,
        0x31 => 0xDC,
        0x32 => 0xDE,
        0x33 => 0xBA,
        0x34 => 0xDE,
        0x35 => 0xC0,
        0x36 => 0xBC,
        0x37 => 0xBE,
        0x38 => 0xBF,
        0x39 => 0x14,
        0x3A..=0x45 => 0x70 + (usage - 0x3A) as u16,
        0x46 => 0x2C,
        0x47 => 0x91,
        0x48 => 0x13,
        0x49 => 0x2D,
        0x4A => 0x24,
        0x4B => 0x21,
        0x4C => 0x2E,
        0x4D => 0x23,
        0x4E => 0x22,
        0x4F => 0x27,
        0x50 => 0x25,
        0x51 => 0x28,
        0x52 => 0x26,
        0x53 => 0x90,
        0x54 => 0x6F,
        0x55 => 0x6A,
        0x56 => 0x6D,
        0x57 => 0x6B,
        0x58 => 0x0D,
        0x59..=0x61 => 0x61 + (usage - 0x59) as u16,
        0x62 => 0x60,
        0x63 => 0x6E,
        0x64 => 0xE2,
        0x65 => 0x5D,
        0x67 => 0xBB,
        0x68..=0x73 => 0x7C + (usage - 0x68) as u16,
        _ => return None,
    })
}

fn consumer_vk(usage: u16) -> Option<u16> {
    Some(match usage {
        0x00B0 | 0x00B1 | 0x00CD => 0xB3, // play/pause
        0x00B5 => 0xB0,                   // next track
        0x00B6 => 0xB1,                   // previous track
        0x00B7 => 0xB2,                   // stop
        0x00E2 => 0xAD,                   // mute
        0x00E9 => 0xAF,                   // volume up
        0x00EA => 0xAE,                   // volume down
        0x0183 => 0xB5,                   // media select
        0x018A => 0xB4,                   // mail
        0x0192 => 0xB7,                   // calculator
        0x0196 => 0xAC,                   // browser home
        0x0221 => 0xAA,                   // browser search
        0x0223 => 0xAC,                   // home
        0x0224 => 0xA6,                   // browser back
        0x0225 => 0xA7,                   // browser forward
        _ => return None,
    })
}

fn is_extended(vk: u16) -> bool {
    matches!(
        vk,
        0x21..=0x2E | 0x5B..=0x5D | 0x6F | 0x90 | 0x91 | 0xA3 | 0xA5 | 0xAD..=0xB7
    )
}

fn send_usage(usage: u8, down: bool) -> Result<(), InputError> {
    let vk = usage_to_vk(usage).ok_or_else(|| {
        InputError::unsupported(format!("Windows has no mapping for HID usage 0x{usage:02X}"))
    })?;
    send_vk(vk, down)
}

fn send_vk(vk: u16, down: bool) -> Result<(), InputError> {
    let scan = unsafe { MapVirtualKeyW(vk as u32, MAPVK_VK_TO_VSC) } as u16;
    let mut flags = 0u32;
    if down {
        flags |= 0;
    } else {
        flags |= KEYEVENTF_KEYUP;
    }
    if scan != 0 {
        flags |= KEYEVENTF_SCANCODE;
    }
    if is_extended(vk) {
        flags |= KEYEVENTF_EXTENDEDKEY;
    }
    let input = INPUT {
        r#type: INPUT_KEYBOARD,
        Anonymous: windows_sys::Win32::UI::Input::KeyboardAndMouse::INPUT_0 {
            ki: KEYBDINPUT {
                wVk: if scan == 0 { vk } else { 0 },
                wScan: scan,
                dwFlags: flags,
                time: 0,
                dwExtraInfo: 0,
            },
        },
    };
    send_inputs(&[input])
}

fn send_mouse_move(dx: i16, dy: i16) -> Result<(), InputError> {
    let input = mouse_input(dx as i32, dy as i32, 0, MOUSEEVENTF_MOVE);
    send_inputs(&[input])
}

fn send_mouse_button(button: u8, down: bool) -> Result<(), InputError> {
    let (flag, data) = match (button, down) {
        (MOUSE_LEFT, true) => (MOUSEEVENTF_LEFTDOWN, 0),
        (MOUSE_LEFT, false) => (MOUSEEVENTF_LEFTUP, 0),
        (MOUSE_RIGHT, true) => (MOUSEEVENTF_RIGHTDOWN, 0),
        (MOUSE_RIGHT, false) => (MOUSEEVENTF_RIGHTUP, 0),
        (MOUSE_MIDDLE, true) => (MOUSEEVENTF_MIDDLEDOWN, 0),
        (MOUSE_MIDDLE, false) => (MOUSEEVENTF_MIDDLEUP, 0),
        (MOUSE_BACK, true) => (MOUSEEVENTF_XDOWN, XBUTTON1 as u32),
        (MOUSE_BACK, false) => (MOUSEEVENTF_XUP, XBUTTON1 as u32),
        (MOUSE_FORWARD, true) => (MOUSEEVENTF_XDOWN, XBUTTON2 as u32),
        (MOUSE_FORWARD, false) => (MOUSEEVENTF_XUP, XBUTTON2 as u32),
        _ => return Ok(()),
    };
    send_inputs(&[mouse_input(0, 0, data, flag)])
}

fn send_wheel(amount: i16, horizontal: bool) -> Result<(), InputError> {
    let data = (amount as i32).saturating_mul(120) as u32;
    let flag = if horizontal {
        MOUSEEVENTF_HWHEEL
    } else {
        MOUSEEVENTF_WHEEL
    };
    send_inputs(&[mouse_input(0, 0, data, flag)])
}

fn mouse_input(dx: i32, dy: i32, data: u32, flags: u32) -> INPUT {
    INPUT {
        r#type: INPUT_MOUSE,
        Anonymous: windows_sys::Win32::UI::Input::KeyboardAndMouse::INPUT_0 {
            mi: MOUSEINPUT {
                dx,
                dy,
                mouseData: data,
                dwFlags: flags,
                time: 0,
                dwExtraInfo: 0,
            },
        },
    }
}

fn send_inputs(inputs: &[INPUT]) -> Result<(), InputError> {
    if inputs.is_empty() {
        return Ok(());
    }
    let sent = unsafe {
        SendInput(
            inputs.len() as u32,
            inputs.as_ptr(),
            std::mem::size_of::<INPUT>() as i32,
        )
    };
    if sent == inputs.len() as u32 {
        Ok(())
    } else {
        Err(InputError::new(format!(
            "SendInput accepted {sent} of {} events: {}",
            inputs.len(),
            std::io::Error::last_os_error()
        )))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_letters_and_digits() {
        assert_eq!(usage_to_vk(0x04), Some(0x41));
        assert_eq!(usage_to_vk(0x1D), Some(0x5A));
        assert_eq!(usage_to_vk(0x1E), Some(0x31));
        assert_eq!(usage_to_vk(0x27), Some(0x30));
    }

    #[test]
    fn maps_navigation_and_media() {
        assert_eq!(usage_to_vk(0x4F), Some(0x27));
        assert_eq!(usage_to_vk(0x50), Some(0x25));
        assert_eq!(usage_to_vk(0x28), Some(0x0D));
        assert_eq!(consumer_vk(0x00CD), Some(0xB3));
        assert_eq!(consumer_vk(0x006F), None);
    }

    #[test]
    fn maps_function_row_beyond_f12() {
        assert_eq!(usage_to_vk(0x68), Some(0x7C)); // F13
        assert_eq!(usage_to_vk(0x73), Some(0x87)); // F24
    }

    #[test]
    fn extended_keys_are_flagged() {
        assert!(is_extended(0x27));
        assert!(is_extended(0x5D)); // Apps key
        assert!(is_extended(0xB3));
        assert!(!is_extended(0x41));
        assert!(!is_extended(0xDB)); // OEM bracket is not extended
    }

    #[test]
    fn input_struct_is_nonzero() {
        let _unused: INPUT = unsafe { std::mem::zeroed() };
        assert!(std::mem::size_of::<INPUT>() >= 40);
    }
}
