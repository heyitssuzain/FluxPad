use fluxpad_protocol::{
    build_challenge, constant_time_eq, decode, encode, derive_key, proof, transcript, Hello,
    MessageType, TAG_PHONE, TAG_RECEIVER,
};
use fluxpad_shared::{parse_consumer, parse_keyboard, parse_mouse, PairingStore};

#[test]
fn known_frame_layout_is_stable() {
    let bytes = encode(MessageType::PING, 0x0102_0304, &[]).unwrap();
    assert_eq!(&bytes[0..4], b"FLXP");
    assert_eq!(bytes[4], 1);
    assert_eq!(bytes[5], MessageType::PING);
    assert_eq!(&bytes[8..12], &[0x04, 0x03, 0x02, 0x01]);
    assert_eq!(&bytes[12..16], &[0, 0, 0, 0]);
    let frame = decode(&bytes).unwrap();
    assert_eq!(frame.seq, 0x0102_0304);
}

#[test]
fn hello_and_transcript_match_android_shape() {
    let mut payload = Vec::new();
    payload.push(1);
    let phone_nonce = [0x33u8; 16];
    let salt = [0u8; 16];
    payload.extend_from_slice(&phone_nonce);
    payload.extend_from_slice(&salt);
    let key = derive_key("ABCD2345", &[0x44u8; 16]).unwrap();
    let auth = transcript("fluxpad-aabbccddeeff0011", &[0x22u8; 16], &phone_nonce, &salt);
    payload.extend_from_slice(&proof(&key, TAG_PHONE, &auth));
    let mut model = Vec::new();
    fluxpad_protocol::write_string(&mut model, "Test Phone").unwrap();
    payload.extend_from_slice(&model);

    let hello = Hello::parse(&payload).unwrap();
    assert!(hello.paired);
    assert_eq!(hello.phone_nonce, phone_nonce);
    assert_eq!(hello.model, "Test Phone");
    assert!(constant_time_eq(
        &hello.proof.unwrap(),
        &proof(&key, TAG_PHONE, &auth)
    ));
    assert!(!constant_time_eq(
        &proof(&key, TAG_RECEIVER, &auth),
        &proof(&key, TAG_PHONE, &auth)
    ));
}

#[test]
fn challenge_is_parseable_by_the_phone_shape() {
    let payload = build_challenge("fluxpad-0011223344556677", &[0x55; 16], "Desk").unwrap();
    let mut offset = 0;
    let (id, next) = fluxpad_protocol::read_string(&payload, offset).unwrap();
    assert_eq!(id, "fluxpad-0011223344556677");
    offset = next;
    assert_eq!(&payload[offset..offset + 16], &[0x55; 16]);
    offset += 16;
    let (name, end) = fluxpad_protocol::read_string(&payload, offset).unwrap();
    assert_eq!(name, "Desk");
    assert_eq!(end, payload.len());
}

#[test]
fn reports_use_the_android_byte_layout() {
    let mouse = [0x05, 0xFE, 0xFF, 0x02, 0x00, 0x01, 0x00, 0xFF, 0xFF];
    let report = parse_mouse(&mouse).unwrap();
    assert_eq!(report.buttons, 0x05);
    assert_eq!(report.dx, -2);
    assert_eq!(report.dy, 2);
    assert_eq!(report.wheel, 1);
    assert_eq!(report.pan, -1);

    let keyboard = [0x02, 0, 0x04, 0x05, 0, 0, 0, 0];
    let report = parse_keyboard(&keyboard).unwrap();
    assert_eq!(report.modifiers, 0x02);
    assert_eq!(report.keys, vec![0x04, 0x05]);

    assert_eq!(parse_consumer(&[0xCD, 0x00]), Some(0x00CD));
}

#[test]
fn pairing_store_round_trip_and_rotation() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("receiver.json");
    let mut store = PairingStore::open(&path).unwrap();
    let id = store.receiver_id().to_owned();
    let key = [0x77u8; 32];
    store.save_key(&key).unwrap();
    drop(store);

    let mut reopened = PairingStore::open(&path).unwrap();
    assert_eq!(reopened.receiver_id(), id);
    assert_eq!(reopened.key(), Some(key.as_slice()));
    reopened.forget().unwrap();
    assert!(!reopened.is_paired());
    assert_ne!(reopened.receiver_id(), id);
}

#[test]
fn pairing_code_derivation_is_case_and_separator_insensitive() {
    let salt = [0x99u8; 16];
    assert_eq!(
        derive_key("abcd-2345", &salt).unwrap(),
        derive_key("ABCD 2345", &salt).unwrap()
    );
}
