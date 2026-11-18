//! Android Open Accessory (AOA) host transport.
//!
//! The receiver is the USB host: it asks the phone to switch into accessory
//! mode, then speaks the length-prefixed FLUXPAD protocol over two bulk
//! endpoints. AOA provides no authentication or encryption, so the session
//! layer authenticates every connection before accepting input.

use std::fmt;
use std::time::{Duration, Instant};

use fluxpad_protocol::{Frame, HEADER_SIZE, MAX_PAYLOAD};
use fluxpad_shared::{Link, LinkError};
use rusb::{ConfigDescriptor, Context, Device, DeviceHandle, Direction, TransferType};

const GOOGLE_VID: u16 = 0x18D1;
const ACCESSORY_PIDS: [u16; 2] = [0x2D00, 0x2D01];
const AOA_GET_PROTOCOL: u8 = 51;
const AOA_SEND_IDENTIFICATION: u8 = 52;
const AOA_START_ACCESSORY: u8 = 53;
const AOA_MANUFACTURER: &str = "FLUXPAD";
const AOA_MODEL: &str = "FLUXPAD Receiver";
const AOA_DESCRIPTION: &str = "FLUXPAD USB Companion";
const AOA_VERSION: &str = "1.0";
const AOA_URI: &str = "https://github.com/heyitssuzain/FluxPad";
const IO_TIMEOUT: Duration = Duration::from_secs(2);
const CONTROL_TIMEOUT: Duration = Duration::from_secs(3);

#[derive(Debug)]
pub enum UsbError {
    Usb(rusb::Error),
    NoDevice,
    NotAoa,
    NoAccessory,
    NoInterface,
    NoEndpoints,
    Protocol(String),
}

impl fmt::Display for UsbError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            UsbError::Usb(error) => write!(f, "USB error: {error}"),
            UsbError::NoDevice => write!(f, "no Android phone was found on USB"),
            UsbError::NotAoa => write!(
                f,
                "the phone does not expose Android Open Accessory; set USB mode to File Transfer"
            ),
            UsbError::NoAccessory => write!(
                f,
                "the phone did not re-appear in accessory mode; keep the cable connected and try again"
            ),
            UsbError::NoInterface => write!(f, "the accessory interface was not found"),
            UsbError::NoEndpoints => write!(f, "the accessory has no usable bulk endpoints"),
            UsbError::Protocol(message) => write!(f, "USB protocol error: {message}"),
        }
    }
}

impl std::error::Error for UsbError {}

impl From<rusb::Error> for UsbError {
    fn from(value: rusb::Error) -> Self {
        UsbError::Usb(value)
    }
}

/// One open accessory connection.
pub struct AoaLink {
    handle: DeviceHandle<Context>,
    in_endpoint: u8,
    out_endpoint: u8,
    interface: u8,
    detached_kernel_driver: bool,
    serial: String,
    seq: u32,
}

impl AoaLink {
    /// Find an Android device, switch it into accessory mode if necessary, and
    /// open the accessory interface.
    pub fn connect(serial: &str) -> Result<Self, UsbError> {
        let context = Context::new()?;
        if let Some(device) = find_accessory(&context)? {
            return Self::open_accessory(device, serial);
        }
        let device = find_aoa_device(&context)?.ok_or(UsbError::NoDevice)?;
        let opened = device.open()?;
        let protocol = read_protocol(&opened)?;
        if protocol < 1 {
            return Err(UsbError::NotAoa);
        }
        send_identification(&opened, serial)?;
        // The device may disconnect immediately after START_ACCESSORY.
        let _ = opened.write_control(
            0x40,
            AOA_START_ACCESSORY,
            0,
            0,
            &[],
            CONTROL_TIMEOUT,
        );
        drop(opened);
        drop(device);

        let accessory = wait_for_accessory(&context, Duration::from_secs(20))?
            .ok_or(UsbError::NoAccessory)?;
        Self::open_accessory(accessory, serial)
    }

    fn open_accessory(device: Device<Context>, serial: &str) -> Result<Self, UsbError> {
        let config = active_config(&device)?;
        let (interface, in_endpoint, out_endpoint) = find_accessory_interface(&config)
            .ok_or(UsbError::NoInterface)?;
        let handle = device.open()?;
        #[allow(unused_mut)]
        let mut detached = false;
        #[cfg(any(target_os = "linux", target_os = "macos"))]
        {
            match handle.kernel_driver_active(interface) {
                Ok(true) => {
                    handle.detach_kernel_driver(interface)?;
                    detached = true;
                }
                _ => {}
            }
        }
        if let Err(error) = handle.claim_interface(interface) {
            if detached {
                let _ = handle.attach_kernel_driver(interface);
            }
            return Err(error.into());
        }
        let _ = handle.clear_halt(in_endpoint);
        let _ = handle.clear_halt(out_endpoint);
        Ok(Self {
            handle,
            in_endpoint,
            out_endpoint,
            interface,
            detached_kernel_driver: detached,
            serial: serial.to_owned(),
            seq: 1,
        })
    }

    pub fn serial(&self) -> &str {
        &self.serial
    }

    fn read_exact(&mut self, buffer: &mut [u8], timeout: Duration) -> Result<bool, LinkError> {
        let mut offset = 0usize;
        while offset < buffer.len() {
            match self
                .handle
                .read_bulk(self.in_endpoint, &mut buffer[offset..], timeout)
            {
                Ok(0) => {
                    if offset == 0 {
                        return Ok(false);
                    }
                    return Err(LinkError::Disconnected(
                        "accessory returned zero bytes mid-frame".to_owned(),
                    ));
                }
                Ok(count) => offset += count,
                Err(rusb::Error::Timeout) => {
                    if offset == 0 {
                        return Ok(false);
                    }
                    return Err(LinkError::Timeout);
                }
                Err(rusb::Error::NoDevice) | Err(rusb::Error::Pipe) => {
                    return Err(LinkError::Disconnected("phone disconnected".to_owned()));
                }
                Err(error) => return Err(LinkError::Usb(error.to_string())),
            }
        }
        Ok(true)
    }

    fn write_all(&mut self, bytes: &[u8]) -> Result<(), LinkError> {
        let mut offset = 0usize;
        while offset < bytes.len() {
            match self
                .handle
                .write_bulk(self.out_endpoint, &bytes[offset..], IO_TIMEOUT)
            {
                Ok(0) => {
                    return Err(LinkError::Usb(
                        "accessory accepted zero bytes".to_owned(),
                    ))
                }
                Ok(count) => offset += count,
                Err(rusb::Error::NoDevice) | Err(rusb::Error::Pipe) => {
                    return Err(LinkError::Disconnected("phone disconnected".to_owned()));
                }
                Err(error) => return Err(LinkError::Usb(error.to_string())),
            }
        }
        Ok(())
    }
}

impl Link for AoaLink {
    fn write_frame(&mut self, kind: u8, payload: &[u8]) -> Result<(), LinkError> {
        let bytes = fluxpad_protocol::encode(kind, self.seq, payload)
            .map_err(|error| LinkError::Protocol(error.to_string()))?;
        self.seq = self.seq.wrapping_add(1).max(1);
        self.write_all(&bytes)
    }

    fn read_frame(&mut self, timeout: Duration) -> Result<Option<Frame>, LinkError> {
        let mut header = [0u8; HEADER_SIZE];
        if !self.read_exact(&mut header, timeout)? {
            return Ok(None);
        }
        let parsed = fluxpad_protocol::frame::parse_header(&header)
            .map_err(|error| LinkError::Protocol(error.to_string()))?;
        if parsed.length > MAX_PAYLOAD {
            return Err(LinkError::Protocol("payload length is out of range".to_owned()));
        }
        let mut body = vec![0u8; parsed.length + 4];
        if !self.read_exact(&mut body, IO_TIMEOUT)? {
            return Err(LinkError::Protocol(
                "timed out while reading frame body".to_owned(),
            ));
        }
        let payload_end = body.len() - 4;
        let mut crc = [0u8; 4];
        crc.copy_from_slice(&body[payload_end..]);
        let frame = fluxpad_protocol::frame::decode_parts(&header, &body[..payload_end], &crc)
            .map_err(|error| LinkError::Protocol(error.to_string()))?;
        Ok(Some(frame))
    }

    fn describe(&self) -> String {
        format!(
            "AOA accessory on interface {}, serial {}",
            self.interface, self.serial
        )
    }
}

impl Drop for AoaLink {
    fn drop(&mut self) {
        let _ = self.handle.release_interface(self.interface);
        if self.detached_kernel_driver {
            #[cfg(any(target_os = "linux", target_os = "macos"))]
            {
                let _ = self.handle.attach_kernel_driver(self.interface);
            }
        }
    }
}

fn read_protocol(handle: &DeviceHandle<Context>) -> Result<u16, UsbError> {
    let mut buffer = [0u8; 2];
    let read = handle.read_control(0xC0, AOA_GET_PROTOCOL, 0, 0, &mut buffer, CONTROL_TIMEOUT)?;
    if read < 2 {
        return Err(UsbError::Protocol("short AOA version reply".to_owned()));
    }
    Ok(u16::from_le_bytes(buffer))
}

fn identification_payload(serial: &str) -> Vec<u8> {
    let mut payload = Vec::new();
    for value in [
        AOA_MANUFACTURER,
        AOA_MODEL,
        AOA_DESCRIPTION,
        AOA_VERSION,
        AOA_URI,
        serial,
    ] {
        payload.extend_from_slice(value.as_bytes());
        payload.push(0);
    }
    payload
}

fn send_identification(handle: &DeviceHandle<Context>, serial: &str) -> Result<(), UsbError> {
    let payload = identification_payload(serial);
    handle.write_control(
        0x40,
        AOA_SEND_IDENTIFICATION,
        0,
        0,
        &payload,
        CONTROL_TIMEOUT,
    )?;
    Ok(())
}

fn active_config(device: &Device<Context>) -> Result<ConfigDescriptor, UsbError> {
    if let Ok(config) = device.active_config_descriptor() {
        return Ok(config);
    }
    Ok(device.config_descriptor(0)?)
}

fn find_accessory_interface(config: &ConfigDescriptor) -> Option<(u8, u8, u8)> {
    let mut fallback = None;
    for interface in config.interfaces() {
        let number = interface.number();
        for descriptor in interface.descriptors() {
            if descriptor.class_code() != 0xFF {
                continue;
            }
            let mut input = None;
            let mut output = None;
            for endpoint in descriptor.endpoint_descriptors() {
                if endpoint.transfer_type() != TransferType::Bulk {
                    continue;
                }
                match endpoint.direction() {
                    Direction::In => input = Some(endpoint.address()),
                    Direction::Out => output = Some(endpoint.address()),
                }
            }
            if let (Some(input), Some(output)) = (input, output) {
                let candidate = (number, input, output);
                // The AOA accessory interface is vendor-specific, subclass 0,
                // protocol 0, with one bulk IN and one bulk OUT endpoint.
                if descriptor.sub_class_code() == 0x00 && descriptor.protocol_code() == 0x00 {
                    return Some(candidate);
                }
                fallback.get_or_insert(candidate);
            }
        }
    }
    fallback
}

fn find_accessory(context: &Context) -> Result<Option<Device<Context>>, UsbError> {
    for device in context.devices()?.iter() {
        let descriptor = match device.device_descriptor() {
            Ok(descriptor) => descriptor,
            Err(_) => continue,
        };
        if descriptor.vendor_id() == GOOGLE_VID
            && ACCESSORY_PIDS.contains(&descriptor.product_id())
        {
            return Ok(Some(device));
        }
    }
    Ok(None)
}

fn find_aoa_device(context: &Context) -> Result<Option<Device<Context>>, UsbError> {
    for device in context.devices()?.iter() {
        if is_aoa_candidate(&device) {
            return Ok(Some(device));
        }
    }
    Ok(None)
}

fn is_aoa_candidate(device: &Device<Context>) -> bool {
    let descriptor = match device.device_descriptor() {
        Ok(descriptor) => descriptor,
        Err(_) => return false,
    };
    for index in 0..descriptor.num_configurations() {
        let config = match device.config_descriptor(index) {
            Ok(config) => config,
            Err(_) => continue,
        };
        for interface in config.interfaces() {
            for descriptor in interface.descriptors() {
                if descriptor.class_code() == 0xFF
                    && descriptor.sub_class_code() == 0x42
                    && descriptor.protocol_code() == 0x01
                {
                    return true;
                }
            }
        }
    }
    false
}

fn wait_for_accessory(
    context: &Context,
    timeout: Duration,
) -> Result<Option<Device<Context>>, UsbError> {
    let started = Instant::now();
    while started.elapsed() < timeout {
        if let Some(device) = find_accessory(context)? {
            return Ok(Some(device));
        }
        std::thread::sleep(Duration::from_millis(250));
    }
    Ok(None)
}

/// Human-readable USB inventory for `--list`.
pub fn list_devices() -> Result<Vec<String>, UsbError> {
    let context = Context::new()?;
    let mut out = Vec::new();
    for device in context.devices()?.iter() {
        let descriptor = match device.device_descriptor() {
            Ok(descriptor) => descriptor,
            Err(error) => {
                out.push(format!("unreadable device: {error}"));
                continue;
            }
        };
        let aoa = is_aoa_candidate(&device);
        let accessory = descriptor.vendor_id() == GOOGLE_VID
            && ACCESSORY_PIDS.contains(&descriptor.product_id());
        let mut name = String::new();
        if let Ok(handle) = device.open() {
            if let Ok(text) = handle.read_manufacturer_string_ascii(&descriptor) {
                name.push_str(text.trim());
            }
            if let Ok(text) = handle.read_product_string_ascii(&descriptor) {
                if !name.is_empty() {
                    name.push(' ');
                }
                name.push_str(text.trim());
            }
        }
        let label = if name.is_empty() {
            String::new()
        } else {
            format!(" - {name}")
        };
        let flags = format!(
            "{}{}{}",
            label,
            if aoa { " [AOA interface]" } else { "" },
            if accessory { " [accessory]" } else { "" },
        );
        out.push(format!(
            "{:04x}:{:04x} bus {} address {} {}",
            descriptor.vendor_id(),
            descriptor.product_id(),
            device.bus_number(),
            device.address(),
            flags,
        ));
    }
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn identification_payload_has_six_null_terminated_strings() {
        let payload = identification_payload("fluxpad-aabbccdd");
        let strings: Vec<&[u8]> = payload.split(|byte| *byte == 0).collect();
        assert_eq!(strings.len(), 7); // six fields plus the empty tail after the last NUL
        assert_eq!(strings[0], AOA_MANUFACTURER.as_bytes());
        assert_eq!(strings[1], AOA_MODEL.as_bytes());
        assert_eq!(strings[2], AOA_DESCRIPTION.as_bytes());
        assert_eq!(strings[3], AOA_VERSION.as_bytes());
        assert_eq!(strings[4], AOA_URI.as_bytes());
        assert_eq!(strings[5], b"fluxpad-aabbccdd");
        assert!(strings[6].is_empty());
        assert_eq!(payload.last(), Some(&0));
    }
}
