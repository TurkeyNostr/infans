# Infans — To-Do List

## Bugs

1. ~~**Clock sync failing** — time sync between devices not working correctly~~ **Fixed** — session_ended events from the partner now force-stop the starter's local timer via a new `forceStopTick` flow, preventing stale local timers from creating duplicate records.
2. ~~**Sleep trend shows 59 hours in a 24-hour day** — sleep duration calculation/aggregation bug in trend charts~~ **Fixed** — root cause was duplicate Sleep/Feeding records from both parents independently logging the same timer session. Fixed at the source (force-stop) and in aggregation (dedup by duration + 10-min start bucket + childId).

## Features

1. ~~**Sleep timer start/stop times** — display both start and end times (e.g. "22:36 - 07:51 (9h 15m)") instead of just duration~~ **Done** — SleepCard now shows "22:36 - 07:51" range + "(9h 15m)" duration. SummaryScreen activity list shows the same range.
2. ~~**Vaccine tracker** — track vaccine type, date administered, next dose. Add to existing Health tab~~ **Done** — New Vaccine entity (DB migration v5→v6). Health screen now has Health/Vaccines tabs. Add vaccine dialog with type, date, next-due, dose number, notes. Cards show upcoming vs. history with author tag.
3. **Multi-language support** — i18n/localization so users can pick their language
4. ~~**QR code partner pairing** — replace manual npub entry with QR scan~~ **Done** — QrScanner composable (CameraX + ML Kit) + QrCodeDisplay (ZXing). Scan button in Settings partner section and onboarding. Auto-links after scan.
5. ~~**Partner pairing options** — support pasting npub, NIP-05 identifier, or scanning QR code~~ **Done** — Partner pairing now supports all three: QR scan (primary), npub paste, NIP-05 paste. Available in both onboarding and Settings.
6. ~~**Export npub from app** — when a user generates an nsec in-app, add option to copy npub to clipboard for sharing~~ **Done** — "Copy npub" / "Copy NIP-05" button added to Settings → Nostr Identity, copies to clipboard with haptic feedback.
7. ~~**Swap default relay** — replace relay.primal.net with nostr.oxtr.dev (new users only, existing users keep their saved relays)~~ **Done** — defaultRelays now uses nostr.oxtr.dev instead of relay.primal.net. Existing users with saved relays are unaffected.
8. ~~**Frictionless onboarding for Nostr newcomers** — one-tap key generation, QR-first pairing, zero relay config, hide all Nostr jargon behind sensible defaults~~ **Done** — Onboarding rewritten: "Create Your Backup Key" (one-tap generate, no nsec/relay jargon), "Sync Settings" with plain-English options, QR-first partner pairing with "Show My QR" + "Scan Partner's QR".
9. ~~**Onboarding wizard** — step-by-step guided flow for key generation and partner pairing, clear non-technical language~~ **Done** — 7-page wizard with progress dots, skip-all, back/next navigation. Pages: Welcome → Add Child → Units → Sync Settings → Create Key → Pair Partner → Done. All language non-technical.
10. **Partner pairing approval flow** — one partner sends a request, the other approves it. No manual npub entry needed on receiving side. Uses NIP-17 gift-wrapped DM for the request
11. ~~**Show author on each entry** — display which parent logged each feeding, sleep, weight, diaper, etc. record~~ **Done** — all 8 tracking entities now have an `authorPubkey` column (DB migration v4→v5). Each card displays "You" or "…abc12345" next to the timestamp.
12. ~~**Haptic feedback on button presses** — tactile vibration on all interactive buttons~~ **Done** — HapticController utility created. All "Log X" buttons across 7 screens trigger a subtle haptic click (EFFECT_CLICK on API 29+, 10ms vibration fallback).
13. ~~**Haptic feedback toggle in settings** — switch to enable/disable haptics, default on~~ **Done** — "Haptic Feedback" toggle added to Settings, stored in SharedPreferences, defaults to on.
14. **Zapstore beta channel** — publish with `--channel beta`, opt-in toggle in settings (off by default), clearly labeled as beta
15. **Built-in auto-updater (beta only)** — dormant unless beta toggle is enabled. Queries Nostr for beta channel release events (kind 30063), downloads APK, triggers install intent
16. **Self-hosted strfry relay as default** — run a dedicated relay for Infans (and future apps), bundle URL as app default. Eliminates dependence on third-party relay policies
17. **NIP-13 proof-of-work on publish** — app generates minimum 16-bit PoW nonce before publishing events. Relay Lua filter rejects events below threshold
