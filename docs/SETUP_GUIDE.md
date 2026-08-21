# Infans — Setup Guide

Infans is a privacy-first baby tracker. Your data lives on your phone, not in a cloud. You choose how much or how little to sync.

Three ways to use it:

1. **Offline Solo** — Just you, your phone, no internet needed
2. **Relay Backup Solo** — One parent, encrypted backups to Nostr relays, restore on a new phone
3. **Relay Backup Partner** — Two parents, encrypted sync between phones

---

## 1. Offline Solo

*Use Infans as a standalone tracker. No accounts, no internet, no setup beyond adding your child.*

**Steps**

1. Install the Infans APK.
2. Open the app. You'll see the Home screen (empty).
3. Tap the gear icon (top right) → Settings.
4. Scroll to **Children** → tap **Add Child**.
5. Enter your child's name, date of birth, and gender (optional). Tap Save.
6. Start logging. Use the bottom navigation bar:
   - **Feed** — Bottle, breast, or solids. Amount, duration, notes.
   - **Sleep** — Start and end sleep sessions.
   - **Weight** — Record weight over time.
   - **Notes** — Quick notes to yourself.
   - **Home** — Dashboard showing today's summary and recent activity.
7. From Home, you can also tap into Diaper, Pumping, Health, Milestones, and Charts.

**What happens to your data**

- Everything is stored in a local database on your phone.
- Nothing leaves your device. No network activity, no accounts.
- If you uninstall the app or lose your phone, your data is gone. Use **Settings → Backup & Restore → Export JSON Backup** to save a copy if you want one.

**Editing entries**

- On the Feed screen, each entry has three buttons: pencil (edit time), sliders (edit quantity), trash (delete).
- On the Home summary, tap the pencil icon on any entry to correct its time.

---

## 2. Relay Backup Solo

*One parent, but your data is encrypted and backed up to Nostr relays. If you lose your phone or install on a new device, you log in with the same key and your data restores automatically.*

**Step 1 — Create a Nostr identity**

1. Install Infans. Open Settings (gear icon, top right).
2. Scroll to **Nostr Identity**. You have three options:
   - **Generate New Key** — Creates a new Nostr key pair. Simplest option.
   - **Import nsec** — If you already have a Nostr private key (nsec1...), paste it here.
   - **Log in with Amber** — If you have [Amber](https://github.com/greenart7c3/Amber) installed, your private key stays in Amber and never enters Infans. Recommended if you already use Amber for other Nostr apps.
3. After creating or importing, you'll see your npub (a long string starting with `npub1...`). This is your public identity.

**Step 2 — Add your child**

1. Settings → **Children** → **Add Child**.
2. Enter name, date of birth, gender (optional). Save.

**Step 3 — Log data**

1. Use the app normally — feedings, sleep, diapers, weight, notes.
2. Every entry is saved locally and then automatically encrypted and backed up to Nostr relays (a 2-second delay batches rapid entries together).
3. You'll see your npub or NIP-05 displayed in Settings → Nostr Identity. This is what identifies you on the relay.

**Step 4 — Restore on a new phone**

1. Install Infans on the new phone.
2. Settings → **Nostr Identity** → **Import nsec** (paste the same nsec you used before) or **Log in with Amber** (same Amber account).
3. Infans connects to the relays, finds your encrypted backup, decrypts it, and restores all your data automatically.
4. You should see your child and all your history appear within a few seconds.

**If you used "Generate New Key"**

Your nsec was stored in the app's encrypted storage. To get it for transfer to a new phone:
- You need to have exported it before losing the old phone, OR
- Use the **Export JSON Backup** option (Settings → Backup & Restore) to save a plaintext copy of all data, then **Restore from JSON** on the new phone.

If you use Amber, your key lives in Amber — just log in with the same Amber on the new phone. No export needed.

**Manual backup**

- Settings → **Encrypted Backup** → **Backup Now** — forces an immediate sync to relays.
- Settings → **Backup & Restore** → **Export JSON Backup** — saves a file you can share/store anywhere.
- Settings → **Backup & Restore** → **Export to PDF** — readable report for printing or sharing with a doctor.

---

## 3. Relay Backup Partner

*Two parents, two phones. Each parent logs data on their own phone. Everything syncs to the other phone automatically through encrypted Nostr relay events. Notes left by one parent appear on the other parent's phone.*

**Step 1 — Both parents create a Nostr identity**

Each parent does this on their own phone:

1. Install Infans. Open Settings (gear icon).
2. **Nostr Identity** → choose one:
   - **Generate New Key** (simplest)
   - **Import nsec** (if you already have a Nostr key)
   - **Log in with Amber** (recommended — key stays in Amber, and after you approve permissions once, Infans runs silently with no prompts)
3. After login, you'll see your npub (`npub1...`) or NIP-05 (if you have one set up) in Settings → Nostr Identity.

**Step 2 — Both parents add the child**

1. Settings → **Children** → **Add Child**.
2. Enter the same child's name, date of birth, gender (optional).
3. Each parent adds the child on their own phone. The child entries will sync and merge.

**Step 3 — Link to each other**

Parent A:

1. Settings → **Partner Sync**.
2. Enter Parent B's npub or NIP-05 in the text field (`npub1...` or `name@domain.com`).
3. Tap **Link Partner**.

Parent B:

1. Settings → **Partner Sync**.
2. Enter Parent A's npub or NIP-05.
3. Tap **Link Partner**.

Both parents must link to each other. It's not one-directional.

**Step 4 — Use the app**

1. Log feedings, sleep, diapers, weight, health, pumping, milestones — anything.
2. Each entry saves locally, then automatically encrypts and syncs to the relay tagged for the other parent.
3. The other parent's phone receives the encrypted event, decrypts it, and saves it to their local database.
4. This happens automatically — you don't need to press anything. There's a 2-second delay after each entry to batch rapid inputs (e.g., feeding + diaper + sleep in one sitting = one sync instead of three).

**Step 5 — Notes for the other parent**

1. Open the **Notes** tab (bottom navigation bar).
2. Type a note and send it. Notes are color-coded by author — your notes are one color, your partner's are another.
3. Notes sync inside the same encrypted payload as tracking data. No separate messaging system, no extra setup.
4. Example: "Gave 60ml bottle at 2pm, she spat up a little after" or "Try the smaller nipple next time."

**Step 6 — Check sync status**

1. Settings → **Sync Diagnostic** → **Run Diagnostic**.
2. Shows three checks:
   - **Relay Match** — whether both parents are on the same relays.
   - **Partner Data on Relays** — whether your partner's encrypted events are reachable.
   - **Partner Status** — whether your partner has Infans data on the relay (Mutual / Has Different Partner / No Infans Data).
3. Per-relay connection states show as check marks (connected), dots (connecting), X (error), or dashes (disconnected).

**Troubleshooting partner sync**

- **Data not appearing on the other phone**: Run the Sync Diagnostic on both phones. Check that both show "Connected" and "Mutual."
- **Notes not syncing but feedings are**: This is a known issue being investigated. Export the debug log from both phones (Settings → Debug Log → Export) and share them.
- **Amber prompts on every entry**: After updating to v1.8.6+, approve each permission type once with "remember my choice" in Amber. Subsequent operations run silently in the background. You can re-enable biometric approval in Amber after this.
- **Partner shows "No Infans Data"**: The other parent hasn't logged in or hasn't logged any data yet. Have them open the app and add at least one entry.

**What if we want to stop syncing?**

1. Settings → **Partner Sync** → **Unlink Partner**.
2. Data already received stays on your phone. Future entries won't sync to the other parent.
3. You can re-link at any time.

---

## Privacy & Security

- All data is encrypted with NIP-44 (end-to-end). Nostr relays see only encrypted blobs — they cannot read your data.
- Your private key never leaves your device (local key) or leaves Amber (if using Amber). Infans never sees or handles your private key when using Amber.
- No accounts, no email, no phone numbers, no tracking, no analytics.
- Relays used: relay.damus.io, nos.lol, relay.primal.net by default. If you have NIP-05 relay hints (kind 10002), those are merged in automatically.
- Debug logs are PII-free by construction — no npubs, pubkeys, NIP-05s, child names, or decrypted data appear in them.

## App Updates

1. Settings → **Auto-Update** → toggle on to check automatically on launch.
2. Or tap **Check Now** to manually check for a new version.
3. If an update is available, tap **Download & Install** — the APK is fetched from GitHub and installed in-place.
