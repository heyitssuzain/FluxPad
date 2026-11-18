use std::fmt;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use fluxpad_protocol::generate_receiver_id;

const STORE_VERSION: u32 = 1;

#[derive(Debug, Serialize, Deserialize)]
struct StoreFile {
    version: u32,
    receiver_id: String,
    key_hex: Option<String>,
    paired_at_unix: Option<u64>,
}

/// Persistent receiver identity and the key derived from the pairing code.
///
/// The key is never printed. `forget()` rotates the receiver id so the phone
/// can no longer authenticate with the old key and must show a new code.
#[derive(Debug)]
pub struct PairingStore {
    path: PathBuf,
    data: StoreFile,
    key: Option<Vec<u8>>,
}

impl PairingStore {
    pub fn open_default() -> Result<Self, StoreError> {
        Self::open(default_path())
    }

    pub fn open(path: impl Into<PathBuf>) -> Result<Self, StoreError> {
        let path = path.into();
        if path.exists() {
            let bytes = fs::read(&path)?;
            let mut data: StoreFile = serde_json::from_slice(&bytes)?;
            let key = match data.key_hex.as_deref() {
                None => None,
                Some(hex) => Some(
                    hex::decode(hex).map_err(|_| StoreError::InvalidKey("not hexadecimal"))?,
                ),
            };
            if let Some(key) = &key {
                if key.len() != 32 {
                    return Err(StoreError::InvalidKey("key is not 32 bytes"));
                }
            }
            let mut changed = false;
            if data.version != STORE_VERSION {
                data.version = STORE_VERSION;
                changed = true;
            }
            if data.receiver_id.trim().is_empty() {
                data.receiver_id = generate_receiver_id();
                changed = true;
            }
            let store = Self { path, data, key };
            if changed {
                store.persist()?;
            }
            Ok(store)
        } else {
            let data = StoreFile {
                version: STORE_VERSION,
                receiver_id: generate_receiver_id(),
                key_hex: None,
                paired_at_unix: None,
            };
            let store = Self {
                path,
                data,
                key: None,
            };
            store.persist()?;
            Ok(store)
        }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    pub fn receiver_id(&self) -> &str {
        &self.data.receiver_id
    }

    pub fn key(&self) -> Option<&[u8]> {
        self.key.as_deref()
    }

    pub fn is_paired(&self) -> bool {
        self.key.is_some()
    }

    pub fn save_key(&mut self, key: &[u8]) -> Result<(), StoreError> {
        if key.len() != 32 {
            return Err(StoreError::InvalidKey("key is not 32 bytes"));
        }
        self.key = Some(key.to_vec());
        self.data.key_hex = Some(hex::encode(key));
        self.data.paired_at_unix = Some(
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs(),
        );
        self.persist()
    }

    pub fn forget(&mut self) -> Result<(), StoreError> {
        self.key = None;
        self.data.key_hex = None;
        self.data.paired_at_unix = None;
        self.data.receiver_id = generate_receiver_id();
        self.persist()
    }

    fn persist(&self) -> Result<(), StoreError> {
        if let Some(parent) = self.path.parent() {
            fs::create_dir_all(parent)?;
        }
        let bytes = serde_json::to_vec_pretty(&self.data)?;
        let temporary = self.path.with_extension("json.tmp");
        fs::write(&temporary, bytes)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let _ = fs::set_permissions(&temporary, fs::Permissions::from_mode(0o600));
        }
        #[cfg(windows)]
        if self.path.exists() {
            // Windows refuses to rename over an existing file.
            fs::remove_file(&self.path)?;
        }
        fs::rename(&temporary, &self.path)?;
        Ok(())
    }
}

#[derive(Debug)]
pub enum StoreError {
    Io(std::io::Error),
    Json(serde_json::Error),
    InvalidKey(&'static str),
}

impl fmt::Display for StoreError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            StoreError::Io(error) => write!(f, "cannot access pairing store: {error}"),
            StoreError::Json(error) => write!(f, "pairing store is corrupt: {error}"),
            StoreError::InvalidKey(reason) => write!(f, "pairing key is invalid: {reason}"),
        }
    }
}

impl std::error::Error for StoreError {}

impl From<std::io::Error> for StoreError {
    fn from(value: std::io::Error) -> Self {
        StoreError::Io(value)
    }
}

impl From<serde_json::Error> for StoreError {
    fn from(value: serde_json::Error) -> Self {
        StoreError::Json(value)
    }
}

pub fn default_path() -> PathBuf {
    default_dir().join("receiver.json")
}

fn default_dir() -> PathBuf {
    #[cfg(windows)]
    {
        if let Some(app_data) = std::env::var_os("APPDATA") {
            return PathBuf::from(app_data).join("FLUXPAD");
        }
        if let Some(profile) = std::env::var_os("USERPROFILE") {
            return PathBuf::from(profile).join(".fluxpad");
        }
    }
    #[cfg(target_os = "macos")]
    {
        if let Some(home) = std::env::var_os("HOME") {
            return PathBuf::from(home)
                .join("Library")
                .join("Application Support")
                .join("FLUXPAD");
        }
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        if let Some(xdg) = std::env::var_os("XDG_CONFIG_HOME") {
            return PathBuf::from(xdg).join("fluxpad");
        }
        if let Some(home) = std::env::var_os("HOME") {
            return PathBuf::from(home).join(".config").join("fluxpad");
        }
    }
    PathBuf::from(".fluxpad")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn creates_and_persists_identity() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("receiver.json");
        let mut store = PairingStore::open(&path).unwrap();
        let id = store.receiver_id().to_owned();
        assert!(id.starts_with("fluxpad-"));
        assert!(!store.is_paired());

        let key = [0x5Au8; 32];
        store.save_key(&key).unwrap();
        assert_eq!(store.key(), Some(key.as_slice()));

        let reopened = PairingStore::open(&path).unwrap();
        assert_eq!(reopened.receiver_id(), id);
        assert_eq!(reopened.key(), Some(key.as_slice()));
    }

    #[test]
    fn forget_rotates_identity_and_clears_key() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("receiver.json");
        let mut store = PairingStore::open(&path).unwrap();
        let old_id = store.receiver_id().to_owned();
        store.save_key(&[0x11; 32]).unwrap();
        store.forget().unwrap();
        assert!(!store.is_paired());
        assert_ne!(store.receiver_id(), old_id);
        let reopened = PairingStore::open(&path).unwrap();
        assert!(!reopened.is_paired());
        assert_ne!(reopened.receiver_id(), old_id);
    }

    #[test]
    fn rejects_bad_key_length() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("receiver.json");
        let mut store = PairingStore::open(&path).unwrap();
        assert!(store.save_key(&[0x11; 31]).is_err());
    }
}
