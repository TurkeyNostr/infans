# Infans — Native Android (Kotlin)

A privacy-first baby tracking app with Nostr-based encrypted data storage and parent-to-parent sync.

## Features

- **Feeding tracking** — bottle (ml/fl oz), breast (L/R/both + duration), solids
- **Sleep tracking** — start time + duration
- **Weight & height** — kg/lb/oz with optional height (cm/in)
- **Milestones** — first smile, first word, etc.
- **Growth charts** — WHO weight-for-age percentile bands (P3/P50/P97) for boys/girls
- **7-day trend bar charts** — feeding and sleep trends on the Summary tab
- **Encrypted backup** — all data encrypted with NIP-44 and stored on Nostr relays as kind 30078 events
- **Parent sync** — share data with co-parent via NIP-44 encrypted kind 30078 partner-sync events
- **Notes** — leave async notes for the other parent (synced inside the same encrypted payload)
- **Offline-first** — Room database is the local source of truth; Nostr syncs in the background

## Architecture

### Data Layer
- **Room** database (local source of truth)
- Entities: `Child`, `Feeding`, `Sleep`, `Weight`, `Milestone`, `Diaper`, `Pumping`, `HealthRecord`, `Note`
- `BabyRepository` wraps all DAOs

### Nostr Layer (inspired by Runstr + nospeak)
- **Key management**: dedicated nsec per parent, stored in Android Keystore via `EncryptedSharedPreferences`
- **Encrypted backup** (Runstr model):
  - All data → JSON → gzip → NIP-44 self-encrypt → kind 30078 replaceable event
  - Only the user's nsec can decrypt; works cross-device
  - d-tag: `baby-tracker-backup`
- **Partner sync**:
  - Same payload, NIP-44 encrypted to the partner's pubkey
  - Published as kind 30078 with d-tag `baby-tracker-sync`
  - No separate messaging infrastructure — notes travel inside the sync payload
- **Relays**: wss://relay.damus.io, wss://nos.lol, wss://relay.primal.net
- **Background sync**: foreground service keeps relay connections alive for incoming events
- **NIP-05**: resolve name@domain identifiers to npubs for partner pairing

### UI Layer
- Jetpack Compose with Material 3
- Tabs: Home, Feed, Sleep, Weight, Notes
- Settings: Nostr identity management, backup, child management, relay status

## Building

### Prerequisites
- Android Studio (Hedgehog or newer)
- JDK 17
- Android SDK 34

### Steps
1. Clone this repo
2. Open in Android Studio
3. Let Gradle sync
4. Connect an Android device (or start an emulator)
5. Click Run

### Building an APK
```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

For a debug APK:
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Nostr Event Types

| Kind | Purpose | Encryption |
|------|---------|------------|
| 30078 | Encrypted data backup (replaceable) | NIP-44 self-encrypt |
| 30078 | Partner sync (encrypted to partner) | NIP-44 to partner pubkey |
| 10002 | NIP-65 relay list | None (public) |
| 0 | Profile metadata (NIP-05 lookup) | None (public) |

## Credits

This project borrows code, patterns, and ideas from the following open-source Nostr projects:

### Runstr
- **Repository**: https://github.com/TheWildHustle/Runstr
- **What we borrowed**: The encrypted backup model — collecting all app data into a JSON payload, gzip-compressing it (NIP-44's 64KB limit), NIP-44 self-encrypting, and publishing as a kind 30078 replaceable parameterized event. The overall architecture of `BackupService.kt` follows this pattern.
- **License**: MIT

### nospeak
- **Repository**: https://github.com/psic4t/nospeak
- **What we borrowed**: The relay pool pattern (managing multiple WebSocket relay connections in parallel with merged event flow), the foreground service pattern for keeping relay connections alive when the app is backgrounded (`RelaySyncService.kt`), and the general approach to relay subscription management.
- **License**: MIT

### Amber
- **Repository**: https://github.com/greenart7c3/Amber
- **What we borrowed**: NIP-55 external signer integration. The `nostrsigner:` URI scheme transport, `get_public_key` / `sign_event` / `nip44_encrypt` / `nip44_decrypt` method dispatch, intent extras (`result`, `event`, `rejected`, `package`), and the `setPackage` flow for routing to a specific signer app. Implemented in `AmberSigner.kt` and `AmberBridge.kt`.
- **License**: MIT

### secp256k1-kmp
- **Repository**: https://github.com/ACINQ/secp256k1-kmp
- **What we borrowed**: Schnorr (BIP-340) event signing and public key derivation. Used in `NostrEventSigner.kt` and `NostrKeys.kt` for all local-key signing operations.
- **License**: Apache 2.0

### NIP-44 Reference Implementation
- **Specification**: https://github.com/nostr-protocol/nips/blob/master/44.md
- **What we borrowed**: The NIP-44 v2 encryption scheme — ECDH shared secret computation, HKDF key derivation (RFC 5869, salt=zero-bytes, info="nip44-v2"), power-of-2 padding with 2-byte length prefix, and AES-256-GCM authenticated encryption. Implemented from scratch in `Nip44.kt` using BouncyCastle for ECDH (secp256k1-kmp's `ecdh()` returns SHA256(compressed_point) but NIP-44 requires the raw x-coordinate).
- **License**: Public domain (specification)

### BIP-173 (Bech32)
- **Specification**: https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki
- **What we borrowed**: Bech32 encoding/decoding for Nostr keys (npub/nsec). Implemented in `Bech32.kt` with the Nostr charset.
- **License**: Public domain (specification)

### Zapstore
- **Website**: https://zapstore.dev
- **What we borrowed**: App distribution — the "Install Amber" link in Settings points to `zapstore.dev/apps/com.greenart7c3.amber` for users who need a NIP-55 signer.

## Privacy

- No central server — all data lives locally and on Nostr relays you choose
- NIP-44 authenticated encryption — relay operators cannot read your data
- nsec stored in hardware-backed Android Keystore (local signer mode)
- Amber (NIP-55) mode: private key never enters this app — all signing/encryption delegated to Amber
- No telemetry, no analytics, no tracking

## License

MIT — Copyright (c) 2026 Turkey
