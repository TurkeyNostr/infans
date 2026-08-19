# Baby Tracker — Native Android (Kotlin)

A privacy-first baby tracking app with Nostr-based encrypted data storage and parent-to-parent messaging.

## Features

- **Feeding tracking** — bottle (ml/fl oz), breast (L/R/both + duration), solids
- **Sleep tracking** — start time + duration
- **Weight & height** — kg/lb/oz with optional height (cm/in)
- **Milestones** — first smile, first word, etc.
- **Growth charts** — WHO weight-for-age percentile bands (P3/P50/P97) for boys/girls
- **7-day trend bar charts** — feeding and sleep trends on the Summary tab
- **Encrypted backup** — all data encrypted with NIP-44 and stored on Nostr relays as kind 30078 events
- **Parent-to-parent messaging** — NIP-17 gift-wrap encrypted DMs via Nostr
- **Offline-first** — Room database is the local source of truth; Nostr syncs in the background

## Architecture

### Data Layer
- **Room** database (local source of truth)
- Entities: `Child`, `Feeding`, `Sleep`, `Weight`, `Milestone`, `ChatMessage`
- `BabyRepository` wraps all DAOs

### Nostr Layer (inspired by Runstr + nospeak)
- **Key management**: dedicated nsec per parent, stored in Android Keystore via `EncryptedSharedPreferences`
- **Encrypted backup** (Runstr model):
  - All data → JSON → gzip → NIP-44 self-encrypt → kind 30078 replaceable event
  - Only the user's nsec can decrypt; works cross-device
  - d-tag: `baby-tracker-backup`
- **Messaging** (nospeak model):
  - NIP-17 gift-wrap protocol: Rumor (kind 14) → Seal (kind 13) → Gift Wrap (kind 1059)
  - Triple-layer encryption with randomized timestamps
  - NIP-44 for the encryption layer
- **Relays**: wss://relay.damus.io, wss://nos.lol, wss://relay.primal.net
- **Background sync**: foreground service keeps relay connections alive for incoming DMs

### UI Layer
- Jetpack Compose with Material 3
- 7 tabs: Feed, Sleep, Weight, Milestones, Charts, Summary, Messages
- Settings: Nostr identity management, backup, child management

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
| 1059 | Gift-wrapped DM (outer layer) | NIP-44 with one-time key |
| 13 | Seal (middle layer) | NIP-44 |
| 14 | Private direct message (inner rumor) | Signed, not separately encrypted |

## Privacy

- No central server — all data lives locally and on Nostr relays you choose
- NIP-44 authenticated encryption — relay operators cannot read your data
- nsec stored in hardware-backed Android Keystore
- Gift-wrapped DMs have no metadata leakage (randomized timestamps, one-time keys)

## License

MIT — Copyright (c) 2026 Turkey
