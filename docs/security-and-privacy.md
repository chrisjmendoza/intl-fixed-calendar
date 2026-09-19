# Security & Privacy Plan

Status: planning (no code yet). Last verified against Android / Google Play / GitHub docs: **2026-09-17**.
Scope: the Android app (Kotlin, Compose, Glance widgets, Room + DataStore, WorkManager/AlarmManager, minSdk 26, no backend, no accounts) and its public GitHub repo.

**Posture in one paragraph.** This is an offline, single-user calendar. The platform (app sandbox + file-based encryption + encrypted Auto Backup) already covers most of what matters. Our job is mostly to *not undo* those guarantees: keep the app off the network, keep data out of places other people can see it (widgets, lock-screen notifications, plain-text exports), treat `.ics` files and URLs as hostile input, keep exported components to a minimum, and keep the signing/publishing chain clean. Database encryption, custom PINs, certificate pinning, and enterprise supply-chain tooling are **not** warranted.

Milestone labels follow the phasing in [holidays-and-import.md](holidays-and-import.md): **MVP** (v1.0: built-in features, local events/reminders, widgets, zero dangerous permissions), **v1.x** (v1.1 device-calendar overlay, v1.2 `.ics` import/export, v1.3 URL subscriptions), **later**. "Ships with X" means the control is part of feature X's definition of done, whatever release X lands in.

Items marked *not verified* could not be confirmed from a primary source on the date above.

---

## 1. Threat model (lightweight)

### 1.1 Assets

| Asset | Where it lives | Sensitivity |
|---|---|---|
| User-created events (titles, notes, times, reminders) | Room DB in app-private storage | **Personal** — reveals plans, appointments, relationships, health visits |
| Device-calendar data read via `CalendarContract` | Owned by the calendar provider; we only read it | Personal, often more sensitive than our own data (work + family calendars) |
| Imported `.ics` events / subscription feeds | Room DB; subscription URL in DB | Personal; **the URL itself can be a secret** (e.g. "secret address" calendar links carry an access token) |
| Settings | DataStore | Low |
| Backups (Android Auto Backup / device transfer) | Google's backup transport / cable transfer | Same as the DB |
| User exports (JSON / `.ics`) | Wherever the user saves them (Drive, Downloads, email) | Same as the DB, **and outside every platform protection once written** |
| Upload key, Play Console account, GitHub account | Developer machine / password manager / GitHub secrets | **Critical** — compromise = malicious update to every user |

### 1.2 Adversaries in scope

| # | Adversary | Realistic attack | Primary controls |
|---|---|---|---|
| A1 | Someone with brief access to an **unlocked** phone (partner, coworker, child) | Opens the app, scrolls the month, reads events | Optional app lock (§3); hide from recents |
| A2 | Shoulder-surfer / anyone who can see the screen or lock screen | Reads event titles from a widget, a lock-screen notification, or the recents thumbnail | Widget privacy mode, redacted public notifications, lock-screen widget opt-out (§3) |
| A3 | Malicious `.ics` file or hostile subscription server | Parser crash, memory/CPU exhaustion (RRULE bombs, huge files), notification spam via `VALARM`, tracking via embedded URLs, SSRF-style fetches to LAN hosts, downgrade to HTTP | Input limits, lazy bounded recurrence expansion, no auto-reminders from imports, HTTPS-only, fetch limits (§6) |
| A4 | Other apps on the device | Send crafted intents to exported components, hijack mutable `PendingIntent`s, read world-readable files, grab exports from shared storage | Minimal exported surface, immutable explicit `PendingIntent`s, app-private storage, SAF, scoped `FileProvider` (§6) |
| A5 | Supply-chain compromise (dependency, Gradle plugin, GitHub Action, CI secret leak, stolen developer account) | Malicious code shipped in a signed release | Few dependencies, **no `INTERNET` permission** (a compromised library cannot exfiltrate), pinned actions, Dependabot cooldown, secret scanning, 2FA, Play App Signing (§8) |
| A6 | Finder/thief of a **lost, locked** phone | Tries to read data off the device | Platform file-based encryption; nothing sensitive in device-encrypted storage; lock-screen redaction (§2, §3) |
| A7 | Whoever later obtains an export file or cloud backup | Reads plaintext export from Drive/email | Warning on plaintext export; optional passphrase-encrypted export; Auto Backup only when end-to-end encrypted (§4) |

### 1.3 Explicitly out of scope

- **Rooted / bootloader-unlocked devices, forensic extraction tools, malware with root.** If the OS is compromised, every app-level control (including SQLCipher with a Keystore key) can be bypassed on a running device. We do not detect root, and we do not claim protection against it.
- **Nation-state or targeted-surveillance adversaries.** Users with that threat model should not keep plans in a hobby calendar app; the privacy statement should not imply otherwise.
- **An attacker who knows the device PIN.** Device credential = full access, by design (app lock falls back to it).
- **Compromise of Google's backup infrastructure or of Google Play itself.**
- **Coercion / legal compulsion of the user.** No duress modes or hidden calendars.

---

## 2. Data at rest

### 2.1 What the platform already gives us (free)

- **App sandbox.** Files under `/data/data/<pkg>` are readable only by our UID. Other apps (A4) cannot read the Room DB or DataStore without root.
- **File-based encryption (FBE).** Mandatory on devices launching with Android 10+, and present on essentially all devices at minSdk 26 that matter. App data sits in **credential-encrypted (CE) storage** by default: unreadable until the user unlocks once after boot, with keys derived from the lock-screen credential. This is the control for the lost phone (A6). It is only as strong as the user's screen lock; we cannot fix a phone with no lock.
- **No `adb backup` leakage.** For apps targeting API 31+, `adb backup` excludes app data unless the app is debuggable. Release builds are not debuggable.
- **Direct Boot:** apps that are not `directBootAware` do not run at all before first unlock, and cannot touch CE storage. ([Direct Boot docs](https://developer.android.com/privacy-and-security/direct-boot))

### 2.2 SQLCipher / encrypted Room — evaluated

| Factor | Assessment |
|---|---|
| Threat actually addressed | Only offline reads of the DB file by someone who bypassed the sandbox (root/forensics) — **out of scope** (§1.3). It does nothing against A1 (unlocked phone; the app decrypts for whoever opens it) unless the key is gated behind user authentication. |
| Key management | Random DB key wrapped by an Android Keystore key. If the Keystore key requires user authentication, **widgets, reminder workers, boot rescheduling and the midnight refresh cannot open the DB while the app is locked** — it breaks the product. If it doesn't require authentication, encryption adds nothing against anyone who can already run code as the app or as root. |
| Reliability risk | Keystore key loss/corruption on some OEM devices and after restore to a new device (Keystore keys never transfer) → **unrecoverable user data**. For a calendar, silent data loss is a worse outcome than the threat being mitigated. Auto Backup of an encrypted DB whose key cannot be restored is useless. |
| Size / performance | Native library per ABI, several MB added to download size (exact figure *not verified*); slower open and queries (page-level AES + KDF on open); must track 16 KB page-size compliance for native code (supported since sqlcipher-android 4.6.1 — [Zetetic](https://www.zetetic.net/blog/2025/06/26/sqlcipher-for-android-16kb-page-size-support/)). The app otherwise has **zero native code**. |
| Maintenance | Extra dependency with native CVE surface; the old `android-database-sqlcipher` artifact is deprecated in favour of `sqlcipher-android`; Room integration via a custom `SupportSQLiteOpenHelper.Factory`. |
| Related | Jetpack `security-crypto` (`EncryptedSharedPreferences`/`EncryptedFile`) is **deprecated** with no further releases ([release notes](https://developer.android.com/jetpack/androidx/releases/security)); do not adopt it. |

**Decision: NOT NEEDED.** Use plain Room + DataStore in default (credential-encrypted) app-private storage. Revisit only if real users ask for it; keep the door open by constructing the Room database in one place so an open-helper factory could be swapped in, with an export → re-import migration path.

### 2.3 Direct Boot decision

**Decision: the app is NOT Direct Boot aware.** No component gets `android:directBootAware="true"`, and nothing is written to device-encrypted storage.

- Rationale: moving event titles/reminder text into device-encrypted storage would weaken A6 protection to deliver a marginal feature. Google's guidance is to keep private user data in CE storage.
- Accepted consequence: a reminder that falls between a reboot and the first unlock is **late**, not lost. On `BOOT_COMPLETED` (delivered after first unlock) the app reschedules all alarms and immediately posts any reminder whose time passed during the gap. Widgets are not visible before first unlock anyway.
- Document this in the reminder design and test it (reboot with a pending reminder).

### 2.4 Other at-rest rules (MVP, all size S)

- Everything user-generated lives in `filesDir`/database dir. Nothing sensitive in external storage, `getExternalFilesDir`, or world-readable modes.
- Re-fetchable or derived data (subscription response cache, rendered widget bitmaps, temp export files) goes in `cacheDir` / `noBackupFilesDir`.
- **Device-calendar data is read live and never copied into Room** (v1.1). This keeps other accounts' data out of our DB, our backups, and our exports.
- No event titles, notes, or subscription URLs in Logcat in release builds.
- A **"Delete all data"** action in Settings (clears DB, DataStore, caches, cancels alarms/work). Cheap, and it is the honest answer to "how do I erase my data" in the privacy policy.

---

## 3. App lock, widget/notification privacy, screenshots

### 3.1 Optional app lock — NEEDED, v1.x (effort M)

Addresses A1 only. Off by default.

**Design**

- `androidx.biometric` `BiometricPrompt` with `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`. This combination works across minSdk 26+; `DEVICE_CREDENTIAL` alone and `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` are unsupported on API ≤ 29 ([biometric docs](https://developer.android.com/identity/sign-in/biometric-auth)). Face/fingerprint class does not matter here because nothing cryptographic hangs off the result.
- **No custom PIN/password.** Rolling our own means storing a verifier, handling lockout and recovery, and getting it wrong. The device credential is the fallback.
- **No `CryptoObject`, no key binding.** This is a UI gate, not encryption, and the docs/settings copy must say so plainly: *"App lock stops someone who picks up your unlocked phone from opening the app. It does not encrypt your data."*
- Re-lock policy: on process start and after the app has been in the background longer than a user-chosen timeout (immediately / 1 min / 5 min). Track with a process-lifecycle observer and `SystemClock.elapsedRealtime()` (not wall-clock, which the user can change).
- **Every entry point passes through the gate**: launcher, widget taps, notification taps, the `.ics` `ACTION_VIEW` import activity, widget configuration activity, any future deep link. Implement the gate once at the root of the single-activity nav host, not per screen; secondary activities (import, widget config) share the same check.
- If the device has no secure lock screen (`canAuthenticate` → none enrolled / `KeyguardManager.isDeviceSecure()` false): app lock cannot be enabled; if it was enabled and the screen lock was later removed (which itself requires the credential), disable it and tell the user.

**What it protects:** the in-app UI — month grid, event details, search, export, settings.

**What it does not protect (and how we compensate):**

| Surface | Behaviour when app lock is ON |
|---|---|
| Widgets | Rendered by the launcher, outside our gate. Enabling app lock **switches all widgets to privacy mode** (user can override per widget). |
| Reminder notifications | Posted while locked (reminders must still work). Enabling app lock turns on **"hide details in notifications"** by default. |
| Notification actions (snooze/dismiss) | Allowed without unlocking — they reveal nothing. "Open event" goes through the gate. |
| Recents thumbnail / screenshots | `FLAG_SECURE` applied while app lock is on (see §3.4). |
| Exports, backups, the DB file | Not protected by app lock. Export requires a fresh auth prompt when app lock is on. |

### 3.2 Widget privacy mode — NEEDED, ships with the first widget that shows event titles (effort S–M)

- Per-widget setting in the Glance configuration activity: **Show titles / Show counts only ("3 events") / Date only**. The IFC date itself is never sensitive.
- Global override in Settings → Privacy: "Hide event details on all widgets".
- Default: titles shown on an agenda-style widget (that is its purpose), but the configuration screen makes the choice visible at placement time rather than burying it.
- **Lock-screen widgets:** since Android 16 QPR2, phone lock screens can host widgets, and **all widgets are eligible by default**; opt out by declaring widget category `not_keyguard` in the app-widget info XML placed in an `xml-36` resource folder ([Android Developers Blog FAQ](https://android-developers.googleblog.com/2025/03/widgets-on-lock-screen-faq.html)). **Decision:** the date-only widget stays lock-screen eligible; any widget capable of showing event titles declares `not_keyguard`. Revisit later if users ask for a redacted lock-screen agenda.
- Widgets must not cache titles in `RemoteViews` state after privacy mode is turned on: toggling the setting forces an immediate update of all widget instances.

### 3.3 Notifications — NEEDED, ships with reminders (effort S)

- Reminder channel notifications use `VISIBILITY_PRIVATE` **plus a redacted `setPublicVersion()`** ("Event reminder · 14:30", no title/notes). This respects the user's system-level "hide sensitive content on lock screen" choice at zero UX cost. **MVP.**
- In-app toggle **"Hide event details in notifications"**: the notification content itself becomes generic everywhere (shade, heads-up, wearables, notification history), details only after opening the app. Default off; forced-on suggestion when app lock is enabled. **v1.x**, with app lock.
- Never use `VISIBILITY_PUBLIC` for reminders. Never put notes/description text in the notification — title and time only.
- No `USE_FULL_SCREEN_INTENT`: Play auto-grants it only for alarm and calling apps ([Play permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)); a calendar reminder is a normal high-importance notification.

### 3.4 `FLAG_SECURE` / hide from recents — NEEDED as an option, v1.x (effort S)

- Screenshots of a calendar are a legitimate, common use (sharing a month view, and for this app, showing people what an IFC calendar looks like). **Not on by default.**
- Setting "Block screenshots & hide in recent apps" → `FLAG_SECURE` on the activity window. Automatically on while app lock is enabled (otherwise the recents thumbnail defeats the lock).
- Optional refinement on API 33+: `Activity.setRecentsScreenshotEnabled(false)` hides the recents thumbnail without blocking user screenshots — use this for the "app lock on, screenshots still allowed" combination. (*API level from memory; not verified.*)

---

## 4. Backups and exports

### 4.1 Android Auto Backup & device-to-device transfer — NEEDED, MVP (effort S)

Losing every event on a phone upgrade is the most likely "security incident" this app will ever have (availability). Backups stay **on**, constrained so they only happen encrypted.

Verified facts ([Auto Backup docs](https://developer.android.com/identity/data/autobackup), [backup security guidance](https://developer.android.com/privacy-and-security/risks/backup-best-practices)):

- Default include set: shared prefs, `filesDir`, databases, `getExternalFilesDir`. Default exclude: `cacheDir`, `codeCacheDir`, `noBackupFilesDir`. Quota **25 MB per app per user**; over quota → no cloud backup (`onQuotaExceeded()`).
- Cloud backups are **end-to-end encrypted with the device PIN/pattern/password on Android 9+** when the user has a screen lock. `disableIfNoEncryptionCapabilities="true"` on `<cloud-backup>` (API 31+) — or `requireFlags="clientSideEncryption"` in the legacy format — restricts backup to that case.
- Targeting API 31+ uses `android:dataExtractionRules` (`<cloud-backup>`, `<device-transfer>`, and since Android 16 QPR2 `<cross-platform-transfer>`); devices on API ≤ 30 still read `android:fullBackupContent`, so **both files are required** with minSdk 26.
- On some OEMs, `allowBackup="false"` no longer disables device-to-device transfer for apps targeting 31+ — another reason to control content with rules rather than the blunt switch.

**Decisions**

| Item | Decision |
|---|---|
| `android:allowBackup` | `true` |
| Cloud backup | Include Room DB + DataStore. `disableIfNoEncryptionCapabilities="true"`. Legacy `fullBackupContent` mirrors this with `requireFlags="clientSideEncryption"` on each `<include>`. Consequence: devices on Android 8.x, or with no screen lock, get no cloud backup — acceptable; those users have manual export. (*Behaviour of `requireFlags` on API 26–27 not verified; test on an emulator.*) |
| Device-to-device transfer | Include everything user-generated (local, user-initiated transfer). |
| Cross-platform (iOS) transfer | Not configured — there is no iOS app. |
| Excluded from both | Subscription response cache, temp export files, widget render caches, any diagnostics log. Put them in `cacheDir`/`noBackupFilesDir` so exclusion is structural, not rule-dependent. |
| Device-calendar data | Never stored, so never backed up (§2.4). |
| App-lock setting | Backed up. After restore, if the new device has no secure lock screen, app lock is disabled with a notice. No secrets exist to restore. |
| DB size | Keep well under 25 MB: subscription caches excluded; consider a cap/prune on imported-event volume. WAL must be checkpointed — rely on Auto Backup running while the app process is stopped; verify restore in testing. |
| Per-user "exclude my data from Android backup" toggle | **NOT NEEDED** now. Users can turn off backup system-wide. Revisit (custom `BackupAgent`) only on request. |

Acceptance test (before Play release): `adb shell bmgr backupnow <pkg>` → uninstall → reinstall → restore; confirm events, reminders re-scheduled after restore, widgets re-render.

### 4.2 User-initiated export / import — NEEDED, v1.2 (effort M)

- **Storage Access Framework only**: `ACTION_CREATE_DOCUMENT` to export, `ACTION_OPEN_DOCUMENT` to import. **No storage permissions of any kind** (`READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_*`, `MANAGE_EXTERNAL_STORAGE`). The user picks the destination; we get a URI grant for that one document. No persisted URI permissions needed.
- Formats: **JSON** (full-fidelity backup incl. IFC-specific fields, versioned schema) and **`.ics`** (interop; lossy for IFC-native recurrence).
- Plaintext export shows a one-line warning at the point of action: *"This file is not encrypted. Anyone who gets the file can read your events."* (A7)
- When app lock is on, exporting requires a fresh authentication.
- Subscription URLs are **omitted from exports by default** (they may embed access tokens), with an opt-in checkbox.
- **Restore is untrusted input** — same validation path as `.ics` import (§6.1): size cap, schema-version check, strict typed deserialization (kotlinx.serialization data classes; no polymorphic class names, no Java serialization), field/range validation, single Room transaction, explicit "merge vs replace" confirmation.
- "Share export" via the share sheet is **later** (adds a `FileProvider`, §6.5). Saving through SAF to Drive already covers the use case.

### 4.3 Password-encrypted export — NEEDED (optional feature), v1.x after plain export (effort M)

Worth doing because exports are the one copy of the data that leaves every platform protection, and users will park them in cloud drives and email.

- Passphrase → **PBKDF2-HMAC-SHA256** (available in the platform on API 26+, no new dependency), ≥ 600 000 iterations, 16-byte random salt → 256-bit key → **AES-256-GCM** over the JSON payload, 12-byte random nonce. Exports are small (well under a few MB), so single-shot in-memory AEAD is fine; no streaming construction needed.
- Small versioned header (magic, format version, KDF id + parameters, salt, nonce) authenticated as GCM associated data, so parameters can be raised later. Own file extension (e.g. `.ifcbak`).
- Use `javax.crypto` directly; no Tink/Bouncy Castle dependency for one primitive. Unit-test with fixed vectors and a wrong-passphrase/tamper test.
- UX: passphrase entered twice; clear warning that **there is no recovery**; never store the passphrase.
- Argon2id would be stronger against offline guessing but needs a native/third-party library — **NOT NEEDED** at this scale.

---

## 5. Permissions

### Current release allow-list (checked by CI)

The table below is the only machine-readable list in this doc: `scripts/check_manifest_permissions.py`
reads it (between the HTML comment markers) and fails the build if the merged `:app:assembleDebug`
manifest contains a `<uses-permission>` that isn't a row here (docs/WORKFLOW.md §4.2, "the permission
allow-list vs the merged manifest"). It lists exactly what's allowed **today**; §5.1 below is the
roadmap-wide picture, including permissions decided but not yet declared. Add a row in the same push
that adds the permission to the manifest. `android.permission.INTERNET` must never appear here before
1.3 (CLAUDE.md rule 7).

<!-- permission-allowlist:begin -->
| Permission | Release | Justification |
|---|---|---|
| `io.github.chrisjmendoza.yearal.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | 0.1 | Self-scoped custom permission that `androidx.core` injects at merge time (named after `applicationId`, `android:protectionLevel="signature"`); guards this app's own dynamically-registered broadcast receivers on API < 33. Both the `<permission>` definition and the matching `<uses-permission>` are expected in every merged manifest; it grants the app no capability beyond what it already has. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | 0.1 (added by M5 T2) | Normal, install-time, no prompt. Declared by the `:core:scheduling` library manifest so `SystemEventReceiver` gets `BOOT_COMPLETED` and can re-arm the midnight rollover alarm, which a reboot drops (§5.1, §6.3). |
| `android.permission.WAKE_LOCK` | 0.1 (added by M5 T1) | Normal, install-time, no prompt. Merged in by WorkManager (a transitive dependency of Glance, `:widget`); `androidx.work.impl.utils.WakeLocks` acquires it for every piece of work WorkManager runs, unconditionally, which is what keeps the Today widget's background render reliable (FEATURES Q10/S3). `:widget`'s manifest strips WorkManager's `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE` with `tools:node="remove"` instead, since nothing this widget does needs either (§5.1). |
<!-- permission-allowlist:end -->

### 5.1 Permission inventory

| Permission | Decision | Milestone | Type / UX | Notes |
|---|---|---|---|---|
| `POST_NOTIFICATIONS` | Request | Ships with reminders (MVP if reminders are MVP) | Runtime (API 33+). Ask **when the user first adds a reminder**, never on first launch. If denied: keep the reminder, show an inline "notifications are off" chip linking to settings. | Below API 33 notifications are on by default. |
| `USE_EXACT_ALARM` | Declare | Ships with reminders | Normal, install-time, not user-revocable (API 33+). | Play restricts it to apps whose core function needs precise timing; **"a calendar app that shows event notifications" is explicitly allowed**, and a **Play Console declaration is required** ([Play policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)). Do **not** declare it in a release that has no reminders — an unused restricted permission invites rejection. |
| `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"` | Declare | Ships with reminders | Special app access on API 31–32 (pre-granted there, user-revocable). | This is the manifest pattern Android recommends for calendar apps ([Android 14 exact-alarm change](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)). Always call `canScheduleExactAlarms()`; on `false`, fall back to `setWindow`/`setAndAllowWhileIdle` and surface a hint. Handle `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` by rescheduling ([alarms docs](https://developer.android.com/develop/background-work/services/alarms)). |
| `RECEIVE_BOOT_COMPLETED` | **Declared** (M5 T2, in the `:core:scheduling` library manifest) | Ships with reminders / widgets | Normal, install-time; no prompt. | Needed to receive `BOOT_COMPLETED`: a reboot drops every alarm, so the day rollover (and from M6 the reminder alarm) is re-armed then; also on `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `LOCALE_CHANGED`, which need no permission. It is the first platform permission in the merged manifest (the only other entry is androidx.core's own signature-level `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`), so the CI allow-list must contain it. WorkManager merges this in anyway. |
| `READ_CALENDAR` | Request | v1.1 | Runtime, dangerous. **Just-in-time**: only when the user turns on "Show my device calendars". Precede the system dialog with a short rationale screen: what is read, that it is read-only, that it never leaves the device, how to turn it off. Handle denial, "don't ask again" (deep-link to app settings), later revocation, and auto-reset for unused apps — the overlay simply disappears; the rest of the app is unaffected. | Play treats it under the general personal-and-sensitive-data rules (runtime request + clear explanation); there is currently **no calendar-specific declaration form** comparable to SMS/Call Log or the new contacts policy (*inferred from the policy pages; not an explicit statement by Google*). The rationale screen doubles as the "prominent disclosure" should a reviewer want one. |
| `WRITE_CALENDAR` | **NOT requested** | — | — | The app has its own event store. "Add to my Google calendar" uses an `ACTION_INSERT` intent to the calendar app, which needs no permission. Write access doubles the blast radius of any bug. |
| `INTERNET` | **NOT requested until URL subscriptions ship** | v1.3 | Normal, install-time, invisible to users. | "No internet permission" is a verifiable privacy claim and neuters a compromised dependency (A5). Treat adding it as a product decision with its own review (§6.2), not a line in a PR. Consider whether subscriptions are worth giving it up at all. |
| `ACCESS_NETWORK_STATE` | **NOT requested** — stripped | M5 T1: verified merged in by WorkManager (a Glance/`:widget` dependency), then removed | Normal. | Verified against work-runtime 2.10.0: only used by `ConstraintProxy$NetworkStateProxy`, a receiver WorkManager itself leaves `enabled="false"` unless a Worker declares a network `Constraints` type. Glance's own widget-update work declares none, so `:widget`'s manifest strips it with `tools:node="remove"` — keeping the "no network" claim true even though a dependency would otherwise add it. |
| `ACCESS_LOCAL_NETWORK` (API 37) | **NOT requested** | — | Runtime on Android 17+ ([docs](https://developer.android.com/privacy-and-security/local-network-permission)). | Subscriptions to LAN hosts are out of scope; not holding it means the OS blocks LAN fetches for us. |
| `WAKE_LOCK` | **Declared** (M5 T1, merged in by WorkManager via Glance/`:widget`) | Ships with widgets | Normal; no prompt. | `androidx.work.impl.utils.WakeLocks` acquires it for every piece of WorkManager work unconditionally — the mechanism that keeps the Today widget's background render reliable (FEATURES Q10/S3). Kept and allow-listed rather than stripped. |
| `FOREGROUND_SERVICE` | **NOT requested** — stripped | M5 T1: verified merged in by WorkManager, then removed | Normal. | Verified against glance-appwidget 1.2.0: the only `startForeground`/`startForegroundService` call site is the `actionStartService` trampoline, an opt-in Glance action the Today widget never uses (it only opens the app via `actionStartActivity`); the Worker Glance does run (`AsyncRequestWorker`) never calls `setForeground`. Stripped with `tools:node="remove"` in `:widget`'s manifest. |
| `VIBRATE` | Not needed | — | — | Notification channels handle vibration. |
| `USE_BIOMETRIC` | Implicit | v1.x | Normal; merged by `androidx.biometric`. | — |
| `USE_FULL_SCREEN_INTENT` | **NOT requested** | — | — | Reserved by Play for alarm/calling apps. |
| Storage permissions (`READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_*`, `MANAGE_EXTERNAL_STORAGE`) | **NOT requested** | — | — | SAF covers import/export. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | **NOT requested** | — | — | Play-restricted; exact alarms with `setExactAndAllowWhileIdle` are sufficient. |
| `GET_ACCOUNTS`, `READ_CONTACTS`, location, `AD_ID` | **NOT requested** | — | — | If any dependency ever merges `com.google.android.gms.permission.AD_ID`, strip it with `tools:node="remove"`. |

**Guard rail (MVP, effort S):** a CI step that extracts the permissions from the merged release manifest and fails if they differ from an allow-list file checked into the repo. Dependencies add permissions silently; this makes every change a reviewed diff.

**Midnight widget refresh must not depend on the exact-alarm permission.** Its policy justification is user-visible event notifications. If an MVP ships widgets without reminders, schedule the day rollover with a windowed/inexact alarm plus time-change broadcasts, and only use exact alarms once reminders (and the Play declaration) exist.

### 5.2 Google Play requirements that apply

| Requirement | Applies? | What to do |
|---|---|---|
| **Target API level** | Yes | Since 2026-08-31 new apps and updates must target **Android 16 (API 36)** or higher ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)). Expect API 37 to become mandatory around Aug 2027 (pattern, *not announced on that page*). Target the latest stable SDK from day one. |
| **Privacy policy** | **Yes — required for every app**, even with no data collection ([User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en), [Data safety help](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)) | Must be linked in the Play Console field **and inside the app**; on an active, public, non-geofenced URL; **not a PDF**; not user-editable; must name the app/developer and give a privacy contact. **Host it on GitHub Pages from this repo** (e.g. `docs/privacy-policy.md` → Pages). Version history is public, which is a feature. Also ship the same text in-app (works offline) with a link to the hosted copy. |
| **Data safety form** | Yes, required even for closed/open testing tracks | "Collect" means transmitting data off the device; data processed only on-device is not disclosed, and "Calendar events" is a listed data type only if it leaves the device ([Data safety help](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)). Expected answers: **No data collected. No data shared.** This stays true through v1.3: a subscription fetch is a GET to a user-chosen server carrying no user data (*the form's treatment of this exact case is not verified; re-read the form guidance when v1.3 lands*). OS-level Auto Backup is performed by the platform, not collected by the developer (*not verified as an explicit carve-out*). Adding any crash/analytics SDK would change these answers — see §7. |
| **Prominent disclosure & consent** | Only if access is outside users' reasonable expectation | A calendar app reading calendars on-device on explicit opt-in is expected use. The JIT rationale screen (§5.1) satisfies the spirit regardless. |
| **Exact alarm declaration** | Yes, once `USE_EXACT_ALARM` is in the manifest | Complete the Play Console declaration; calendar apps showing event notifications qualify. |
| **Account deletion** | No (no accounts) | Still provide "Delete all data" in-app. |
| **Advertising ID declaration** | Yes (form) | Answer "No"; ensure `AD_ID` is absent from the merged manifest. |
| **Play App Signing / AAB** | Yes, mandatory for new apps ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en)) | See §8.2. |
| **Closed-testing gate** | **If the Play developer account is a personal account created after 2023-11-13**: a closed test with **≥ 12 testers opted in for 14 continuous days** is required before applying for production access ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)) | Roadmap impact: budget ≥ 3 weeks and tester recruitment between "release candidate" and "public on Play". Privacy policy + Data safety form must be done *before* this test starts. |
| **Developer verification** | Yes | Play Console identity verification is expected to satisfy Android's developer-verification programme (enforced regionally from Sept 2026, globally in 2027 — [Android Developer Console Help](https://support.google.com/android-developer-console/answer/16561738?hl=en)). Relevant if APKs are also distributed via GitHub Releases. (*Details for this account not verified.*) |
| **Upcoming sensitive-permission changes** | Not currently | The previewed changes effective 2027-01-27 concern **location and contacts**, not calendar ([preview](https://support.google.com/googleplay/android-developer/answer/16909972?hl=en)). Re-check at each release; a future "calendar picker"-style minimum-scope rule would affect the v1.1 overlay. |

### 5.3 Target-SDK behaviour changes worth designing for now

- **API 37:** cleartext traffic is blocked unless a network security config allows it (`usesCleartextTraffic` deprecated); Certificate Transparency enforced by default; `ACCESS_LOCAL_NETWORK` required for LAN access; RemoteViews bitmap memory for widgets is capped ([behaviour changes](https://developer.android.com/about/versions/17/behavior-changes-17)). All align with this plan; the widget cap matters for Glance month-grid widgets that render bitmaps.
- **API 36:** opt-in stricter intent matching via `android:intentMatchingFlags="enforceIntentFilter"` ([behaviour changes](https://developer.android.com/about/versions/16/behavior-changes-16)) — adopt it (§6.3).

---

## 6. Untrusted input and component hardening

### 6.1 `.ics` parsing — NEEDED, ships with import (v1.2) (effort M)

The import doc selects **biweekly** behind an `IcsParser` interface, with lazy recurrence expansion. Whatever the parser, our wrapper enforces the limits — never rely on the library to be robust.

| Risk | Control |
|---|---|
| Huge file / memory exhaustion | Hard cap on bytes read (**5 MB** file import, **10 MB** subscription), enforced by a counting stream — never trust a declared size. Cap events per file (e.g. 20 000), properties per component, unfolded line length (e.g. 64 KB), and text field lengths (title 500 chars, description 10 000; truncate with a flag). |
| RRULE expansion bombs | **Never materialise occurrences at import.** Store the validated rule; expand lazily per query window (visible range) with an **instance cap per rule per window** and an **iteration cap** so rules that never match (`FREQ=DAILY;BYMONTH=2;BYMONTHDAY=30`) terminate. Support only `DAILY/WEEKLY/MONTHLY/YEARLY`; `SECONDLY/MINUTELY/HOURLY` import as a single occurrence with an "unsupported recurrence" flag (consistent with the import doc). Clamp `INTERVAL`, `COUNT`, `BY*` list lengths, `EXDATE`/`RDATE` counts, and the year range (e.g. 1900–2200). biweekly's iterator is wrapped, not trusted, for these caps. |
| Reminder flooding | Imported `VALARM`s are **ignored by default**; file import offers an explicit "also import reminders" choice (max N per event); subscriptions never create reminders. Only `ACTION=DISPLAY` is ever honoured; `EMAIL`/`AUDIO`/`PROCEDURE` are dropped. Alarms are scheduled rolling (next few only), never one per future occurrence — Android caps concurrent alarms per app. |
| Embedded URLs / tracking / active content | Never fetch anything referenced from inside a file (`ATTACH`, `IMAGE`, `URL`, `X-ALT-DESC` HTML). Descriptions render as **plain text**; **no WebView anywhere in the app**. Links open only on explicit tap and only for `http(s)`, `mailto`, `tel`, `geo`. |
| Malformed input / parser bugs | Parse off the main thread with cancellation + wall-clock timeout; catch `Throwable` at the boundary (including `StackOverflowError`/`OutOfMemoryError` surfaced as a clean failure); **all-or-nothing Room transaction**; preview screen ("Import 214 events from *name*?") before commit; each import is a tagged batch that can be undone/deleted as a unit. |
| Charset tricks | Decode as **UTF-8 with replacement** of malformed sequences, strip BOM; ignore `CHARSET=` parameters. Strip control characters and Unicode bidi overrides from titles shown in widgets/notifications. |
| Time zones | Ignore embedded `VTIMEZONE` definitions; map `TZID` to `java.time.ZoneId`; unknown → treat as floating with a warning. Removes a whole class of parsing complexity. |
| Compressed / container formats | Not supported (`.zip`, `.ics.gz`). |
| Entry via other apps | The `ACTION_VIEW` filter accepts `content://` only with `text/calendar`; it opens the **preview** screen, never auto-imports, and sits behind app lock. |
| Regression safety | JVM unit-test corpus of hostile files (oversize, deep nesting, never-matching RRULEs, invalid UTF-8, 1 MB single line, `COUNT=999999999`). Coverage-guided fuzzing is **NOT NEEDED**. |

### 6.2 URL subscriptions — NEEDED only if the feature ships (v1.3) (effort M–L)

This feature is what introduces `INTERNET`. If it ships:

- **HTTPS only.** `webcal://` is rewritten to `https://`; `http://` is rejected with an explanation. Network security config: `cleartextTrafficPermitted="false"` base config, system CAs only, **no custom `TrustManager`/`HostnameVerifier`, no certificate pinning** (arbitrary user-chosen hosts; pinning is meaningless). On API 37 cleartext is blocked and CT enforced by default anyway (§5.3).
- **Redirects:** max 5, HTTPS → HTTPS only, re-validate the host on every hop.
- **No LAN/loopback/link-local targets:** filter resolved addresses in the HTTP client's DNS hook (covers DNS-rebinding better than checking the hostname). Not holding `ACCESS_LOCAL_NETWORK` gives OS-level enforcement on Android 17+.
- **Limits:** connect 15 s, read 30 s, whole-call 60 s; 10 MB cap on **decompressed** bytes (gzip bombs); then the §6.1 pipeline.
- **Requests carry nothing about the user:** no cookies, no identifiers, generic `User-Agent` (app name + version), conditional GET (`ETag`/`If-Modified-Since`).
- **Scheduling:** WorkManager periodic work, unmetered-or-any network per user choice, min interval ~6 h, exponential backoff, no refresh storms on app open.
- **Atomic replace:** parse into staging, swap inside one transaction on success; a failed or hostile fetch keeps the last good copy.
- **The URL is a secret:** never logged, masked in UI after entry, excluded from exports by default, no `user:pass@` URLs or auth headers in the first version.
- Subscribed events are read-only, visually marked as external, and never raise reminders.
- Privacy statement must say: *the server you subscribe to sees your IP address and when you refresh*.
- HTTP client: platform `HttpsURLConnection` or OkHttp — either is fine; prefer whichever keeps the dependency count lower at the time.

### 6.3 Intents, deep links, exported components — NEEDED, MVP (effort S)

Target exported surface (everything else `android:exported="false"`):

| Component | Exported | Hardening |
|---|---|---|
| Main activity (launcher) | Yes | Accepts only typed extras from our own widgets/notifications (epoch-day, event ID). Validate and fail soft on unknown IDs. Never accept a URI, file path, class name, or nested `Intent` from extras (no intent redirection). |
| Glance widget receivers (`TodayWidgetReceiver`, M5 T1, in `:widget`; more follow in M5 T3/T7) | Yes (required by launchers) | Only ever re-render from the app's own bindings (the injected `Clock`/`ZoneProvider` today; `:core:data` once a widget shows events); ignore unexpected extras. Only the `APPWIDGET_UPDATE` filter is declared, so a spoofed broadcast just causes a refresh. |
| `.ics` import activity (v1.2) | Yes | `content://` + `text/calendar` only → preview screen → gate + confirm (§6.1). |
| Widget configuration activity | **No** (*launchers start it via the app-widget service; verify on Pixel + Samsung launchers*) | Behind app lock. Validates the `appWidgetId` belongs to us. |
| Boot / time-change / package-replaced receiver (`SystemEventReceiver` in `:core:scheduling`, done M5 T2) | **No** — `TIME_SET`, `TIMEZONE_CHANGED`, `LOCALE_CHANGED`, `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are protected broadcasts that only the system can send, and the system reaches non-exported receivers (*verify on a device in the M5 T8 test matrix*) | Checks `intent.action` against exactly that set and reads nothing else from the intent; any other action is ignored. The first four are on the implicit-broadcast exemption list and `MY_PACKAGE_REPLACED` is addressed to the package, so a manifest receiver is allowed; `DATE_CHANGED` is not exempt and is not registered. |
| Alarm + notification-action receivers (`DayRolloverAlarmReceiver`, done M5 T2; reminders M6) | No | Reached only through our own explicit `PendingIntent`s; no intent filter. The rollover `PendingIntent` is `FLAG_IMMUTABLE`, names the receiver class, and carries no extras. The receiver still checks the action. |
| Providers merged by libraries (androidx.startup, WorkManager) | No | Review the merged manifest once per dependency bump (covered by the CI manifest diff if extended to components). |

Also:

- **No custom-scheme deep links or App Links at MVP** — in-app navigation from widgets/notifications uses explicit intents. If deep links are added later, treat all parameters as untrusted and route through the app-lock gate.
- Add `android:intentMatchingFlags="enforceIntentFilter"` on the application (API 36+ opt-in).
- Context-registered receivers use `RECEIVER_NOT_EXPORTED`.
- **Outgoing text share and clipboard** (converter, done M3 T1; FEATURES D4, T8): a user-initiated `ACTION_SEND` of
  `text/plain` behind the system chooser and a plain-text clipboard copy of a converted date are allowed. Implicit by
  design; text only — no URI, file, component, or grant flags; nothing is logged. Event content is never shared this
  way without its own review.
- **Android Lint as the enforcement tool:** run lint in CI with the security category as errors (`ExportedReceiver`, `ExportedContentProvider`, `UnspecifiedImmutableFlag`, `MutableImplicitPendingIntent`, `UnsafeIntentLaunch`, `SetJavaScriptEnabled`, `TrustAllX509TrustManager`, …). It understands Android semantics better than generic SAST.
- Room: parameterised queries only; no `@RawQuery` built by string concatenation; escape user text in any FTS `MATCH` expression.

### 6.4 `PendingIntent` rules — NEEDED, MVP (effort S)

- **Always `FLAG_IMMUTABLE`** (a mutability flag is mandatory when targeting 31+), always an **explicit** component, combined with `FLAG_UPDATE_CURRENT` and a stable per-event request code.
- No `FLAG_MUTABLE` anywhere — no inline-reply or bubble use cases exist. If one appears, it needs a written justification.
- Notification taps open the activity directly (no broadcast/service trampolines; blocked since API 31).
- Extras carry IDs, never content (no event titles inside `PendingIntent` extras).

### 6.5 `FileProvider` / content providers

- **No `ContentProvider` exposing app data.** Widgets run in our process and read Room directly.
- `FileProvider`: **NOT NEEDED until "share export" ships (later).** When it does: `exported="false"`, `grantUriPermissions="true"`, a single `<cache-path>` for `cache/exports/` (never `<root-path>`, `<external-path>`, or `.`), grant `FLAG_GRANT_READ_URI_PERMISSION` only, delete stale export files on next app start.

---

## 7. Privacy posture

| Item | Decision | Rationale |
|---|---|---|
| Analytics | **None. Not opt-in, not "anonymous" — none.** | No backend, no business need; keeps the Data safety form at "no data collected" and keeps `INTERNET` out. |
| Ads / ad SDKs | **None, ever.** | Same, plus supply-chain and policy surface. |
| Crash reporting SDK (Crashlytics, Sentry, …) | **NOT NEEDED.** | Each needs `INTERNET`, changes Data safety answers (crash logs, diagnostics, device IDs), and Firebase would close the door on F-Droid. |
| Crash visibility | **Play Console Android vitals only** (MVP on Play) | Collected by Google Play from users who opted in at the OS level — no SDK, no code, no app-side collection. Keep R8 mapping files so traces are readable (AAB uploads include them). |
| User-driven diagnostics | **Later, optional (S–M):** a local, size-capped crash log (stack traces only — no event content) with a "Copy / share diagnostics" button that the user can paste into a GitHub issue. | Gives debuggability without any automatic transmission. Log lives in `noBackupFilesDir`. |
| Google Play Services / Firebase dependencies | **None.** | Not needed; preserves the no-network claim and F-Droid eligibility. |
| In-app privacy screen | **MVP (S):** Settings → Privacy with the statement text, link to hosted policy, permission explanations, "Delete all data", open-source licences. | Play requires an in-app policy link anyway. |

### Plain-language privacy statement — outline

Host at GitHub Pages; mirror in-app. Keep it under one screen where possible.

1. **Who we are** — app name, developer name, contact email, link to the public repo. Effective date.
2. **The short version** — "Your calendar stays on your phone. The app has no accounts, no servers, no ads, and no analytics. [Until v1.3:] It doesn't even have permission to use the internet."
3. **What the app stores and where** — events, reminders, settings; stored only in the app's private storage on the device.
4. **Permissions and why** — notifications (reminders); exact alarms (reminders on time); start at boot (re-schedule reminders); read calendar (*optional*, only if you turn on device-calendar display; read-only; read on-device and never copied or sent anywhere).
5. **Device calendars** — what is read, that it is never modified, how to switch it off / revoke.
6. **Imports and subscriptions** (when shipped) — files are read locally; subscribing to a URL contacts that server directly from your phone, which means that server can see your IP address and when you refresh. Nothing about you or your events is sent.
7. **Backups and exports** — Android's own backup may store an encrypted copy in your Google account if you have device backup enabled (we only allow it when it is end-to-end encrypted with your screen lock); how to turn that off; exports are files you create and control; plaintext vs. passphrase-protected.
8. **Widgets, notifications, app lock** — what can be visible to people near your phone and the settings that hide it; honest limits of app lock (not encryption).
9. **Data sharing** — none. No third parties. Nothing sold.
10. **Crash information** — Google Play may send the developer anonymised crash statistics if you opted in to sharing diagnostics in Android settings; the app itself sends nothing.
11. **Retention and deletion** — data stays until you delete it; "Delete all data", clearing storage, or uninstalling removes it; no server copies exist for us to delete.
12. **Children** — not directed at children; collects nothing from anyone.
13. **Security reports** — link to `SECURITY.md`.
14. **Changes** — changelog via the public Git history; material changes announced in release notes.

---

## 8. Supply chain and repo hygiene (public repo, solo developer)

### 8.1 Accounts — NEEDED, now (effort S)

The highest-value control in this whole document: **2FA with passkeys/hardware keys on the GitHub account, the Google account that owns Play Console, and the email accounts that can reset them.** A stolen account beats every other control here.

### 8.2 Signing keys — NEEDED, before first Play upload (effort S)

- **Play App Signing** (mandatory for new apps): Google holds the app signing key; we hold only an **upload key**, which can be reset through Play Console if lost or leaked ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en)).
- Upload keystore: generated locally, strong unique passwords in a password manager, one offline encrypted backup. **Never in the repo**, never in a cloud-synced project folder.
- `.gitignore` already covers `*.jks`, `*.keystore`, `keystore.properties`, `signing.properties`, `local.properties`, `google-services.json`. Suggested additions when someone next touches it: `*.p12`, `*.pem`, `*.pepk`, `.env*`, `*.der`.
- Signing config reads from environment variables / a properties file outside the repo; the build must succeed unsigned when they're absent (so forks and CI PR builds work).
- **Release builds are signed locally and uploaded manually at first.** CI release signing is **later / optional**; if adopted: keystore + passwords as secrets in a protected GitHub **environment** with required approval, used only by a tag-triggered workflow, never reachable from `pull_request` (fork PRs get no secrets) and **never use `pull_request_target`** with checkout of PR code.
- If APKs are published on GitHub Releases, note they are signed with a different key than Play-delivered builds unless the Play-signed universal APK is re-published; users cannot cross-update between the two. Decide the distribution story once, before the first public build.

### 8.3 GitHub repository settings — NEEDED, MVP (effort S, all free for public repos)

| Setting | Decision |
|---|---|
| Secret scanning + **push protection** | **On.** Free for public repos; blocks the most likely real incident (committing a keystore password or token). |
| Dependabot alerts + security updates | **On.** |
| Dependabot version updates | **On**: `gradle` (supports `gradle/libs.versions.toml` — [changelog](https://github.blog/changelog/2023-03-13-dependabot-version-updates-keeps-gradle-version-catalogs-up-to-date/)) and `github-actions` ecosystems, weekly, grouped to limit PR noise. Keep a **cooldown** (default is now 3 days; set ~7) so freshly published, possibly-compromised releases are not picked up immediately ([changelog](https://github.blog/changelog/2026-07-14-dependabot-version-updates-introduce-default-package-cooldown/)). **Dependabot over Renovate:** native, zero hosting, sufficient. |
| Private vulnerability reporting | **On** (backs `SECURITY.md`). |
| Branch protection on `main` | Block force-push and deletion; require CI to pass. PR-review requirements are pointless solo. |
| Actions: default `GITHUB_TOKEN` permissions | **Read-only**; each workflow declares `permissions:` explicitly. |
| Actions: fork PR workflows | Require approval for outside contributors. |
| Actions: **pin every action to a full commit SHA** | **Yes**, with the version in a trailing comment; Dependabot keeps SHAs current. Optionally enable the repo policy that *enforces* SHA pinning ([changelog](https://github.blog/changelog/2025-08-15-github-actions-policy-now-supports-blocking-and-sha-pinning-actions/)). Use as few third-party actions as possible (checkout, setup-java, Gradle's official action). |

### 8.4 Build and dependency integrity

| Item | Decision | Rationale |
|---|---|---|
| Minimal dependency set; no JitPack; no dynamic (`+`) or `SNAPSHOT` versions; version catalog | **NEEDED, MVP (S)** | The cheapest supply-chain control is fewer suppliers. |
| Repository content filtering (`google()` restricted to `androidx.*`/`com.android.*`/`com.google.*` groups; everything else from `mavenCentral()`; plugins from `gradlePluginPortal()`) | **NEEDED, MVP (S)** | Prevents dependency-confusion across repositories for almost no effort. |
| Gradle wrapper integrity (`distributionSha256Sum` in `gradle-wrapper.properties`; wrapper-JAR validation in CI via Gradle's official action) | **NEEDED, MVP (S)** | The wrapper JAR is an executable binary in a public repo that accepts PRs. (*Automatic validation by the current `setup-gradle` action not verified.*) |
| CI manifest-permission allow-list diff (§5.1) | **NEEDED, MVP (S)** | Catches a dependency quietly adding `INTERNET` or `AD_ID`. |
| Android Lint security checks as errors in CI (§6.3) | **NEEDED, MVP (S)** | Best signal-to-noise for Android-specific mistakes. |
| Gradle dependency verification (`verification-metadata.xml`) | **LATER / optional (M + ongoing)** | Real protection against a tampered artifact, but every AGP/Kotlin/Compose bump touches hundreds of checksums and Dependabot PRs fail until metadata is regenerated by hand (*Dependabot support for updating it not verified*). For a solo project with no network permission in the app, the cost outweighs the benefit today. If adopted: SHA-256 checksums only, no PGP trust configuration. |
| Gradle dependency locking (lockfiles) | **NOT NEEDED** | Only useful with dynamic/ranged versions, which are banned above. Dependabot's lockfile handling with version catalogs is also unreliable ([issue](https://github.com/dependabot/dependabot-core/issues/12557)). |
| CodeQL code scanning | **v1.x, low priority (S)** | Free for public repos and supports Kotlin, but Kotlin needs a real build (build-mode `none` skips Kotlin — [docs](https://docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/codeql-code-scanning-for-compiled-languages), *current status not re-verified*), so it is slow. Run weekly + on PRs to `main`; drop it if it yields only noise — Android Lint carries most of the weight. |
| R8 minification + resource shrinking on release | **NEEDED, before Play release (S–M)** | Size and performance, **not** a security control: the source is public, so obfuscation hides nothing. Either keep obfuscation with mapping files retained per release, or use `-dontobfuscate` for readable vitals traces. Test the release build, especially Room, kotlinx.serialization, Glance, and the `.ics` library (reflection). |
| Release build flags | **NEEDED, MVP (S)** | `debuggable false`, no cleartext flag, no test-only components or debug menus in release; `applicationIdSuffix ".debug"` for debug builds so debug and release data never mix. |
| Reproducible builds / F-Droid listing | **LATER** | The no-proprietary-dependency stance keeps this open; nothing to do now. |
| SBOM, SLSA provenance, artifact attestation | **NOT NEEDED** | Enterprise theatre at this scale. |

### 8.5 `SECURITY.md` — NEEDED, MVP (effort S)

Short file at repo root: report privately via GitHub's "Report a vulnerability" (private vulnerability reporting) or an email address; only the latest release is supported; best-effort response target (e.g. acknowledge within 7 days); scope notes mirroring §1.3 (rooted devices and physical access with the device PIN are out of scope); no bounty.

---

## 9. Checklist

### 9.1 Decisions

Effort: S ≤ half a day, M = 1–3 days, L = a week or more.

| # | Item | Decision | Milestone | Effort |
|---|---|---|---|---|
| 1 | Data in app-private, credential-encrypted storage only; caches in `cacheDir`/`noBackupFilesDir` | NEEDED | MVP | S |
| 2 | SQLCipher / encrypted Room | NOT NEEDED (threat out of scope; breaks widgets/reminders or adds nothing; data-loss risk) | — | — |
| 3 | Jetpack `security-crypto` / encrypted DataStore | NOT NEEDED (deprecated; no secrets stored) | — | — |
| 4 | Direct Boot awareness | NOT NEEDED (late reminder after reboot accepted; catch-up on `BOOT_COMPLETED`) | — | — |
| 5 | Never copy device-calendar data into Room | NEEDED | v1.1 | S |
| 6 | "Delete all data" action | NEEDED | MVP | S |
| 7 | No sensitive content in release logs | NEEDED | MVP | S |
| 8 | Optional app lock (BiometricPrompt, `BIOMETRIC_WEAK \| DEVICE_CREDENTIAL`, no custom PIN) | NEEDED (optional feature) | v1.x | M |
| 9 | Widget privacy mode (titles / counts / date only) | NEEDED | With first title-showing widget | S–M |
| 10 | `not_keyguard` on title-capable widgets (lock-screen widgets, Android 16 QPR2+) | NEEDED | With first title-showing widget | S |
| 11 | `VISIBILITY_PRIVATE` + redacted public notification | NEEDED | With reminders (MVP) | S |
| 12 | "Hide details in notifications" toggle | NEEDED | v1.x (with app lock) | S |
| 13 | `FLAG_SECURE` / hide-from-recents option (auto-on with app lock) | NEEDED (option, default off) | v1.x | S |
| 14 | Auto Backup on, encrypted-only (`dataExtractionRules` + legacy `fullBackupContent`), D2D allowed | NEEDED | MVP | S |
| 15 | Backup/restore acceptance test (`bmgr`) | NEEDED | Before Play release | S |
| 16 | Per-user backup opt-out toggle | NOT NEEDED (system setting exists) | — | — |
| 17 | Export/import via SAF only; no storage permissions | NEEDED | v1.2 | M |
| 18 | Plaintext-export warning; subscription URLs omitted by default | NEEDED | v1.2 | S |
| 19 | Validate JSON restore as untrusted input | NEEDED | v1.2 | S–M |
| 20 | Passphrase-encrypted export (PBKDF2-SHA256 + AES-256-GCM) | NEEDED (optional feature) | v1.x, after plain export | M |
| 21 | Argon2 / Tink / Bouncy Castle | NOT NEEDED | — | — |
| 22 | `POST_NOTIFICATIONS` requested just-in-time at first reminder | NEEDED | With reminders | S |
| 23 | `USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM` (`maxSdkVersion=32`) + Play declaration; graceful fallback | NEEDED | With reminders | S–M |
| 24 | `RECEIVE_BOOT_COMPLETED` + reschedule on boot/time/tz/package-replaced | NEEDED | With reminders/widgets | S |
| 25 | `READ_CALENDAR`, just-in-time with rationale screen | NEEDED | v1.1 | S–M |
| 26 | `WRITE_CALENDAR` | NOT NEEDED (use `ACTION_INSERT` intent) | — | — |
| 27 | `INTERNET` | NOT until URL subscriptions | v1.3 | — |
| 28 | Storage, full-screen-intent, battery-optimisation, contacts, location, `AD_ID`, local-network permissions | NOT NEEDED | — | — |
| 29 | CI merged-manifest permission allow-list | NEEDED | MVP | S |
| 30 | Privacy policy on GitHub Pages + in-app Privacy screen | NEEDED (Play requirement) | Before any Play track, incl. closed testing | S |
| 31 | Data safety form: no data collected/shared; Ad ID: no | NEEDED (Play requirement) | Before any Play track | S |
| 32 | `.ics` hardening: size/count/length caps, lazy bounded RRULE expansion, no auto-reminders, plain-text rendering, transactional import with preview + undo, hostile-file test corpus | NEEDED | v1.2 (with import) | M |
| 33 | Coverage-guided fuzzing of the parser | NOT NEEDED | — | — |
| 34 | WebView / HTML rendering of descriptions | NOT NEEDED (banned) | — | — |
| 35 | URL subscriptions: HTTPS-only, network security config, redirect/size/time limits, LAN block, atomic replace, URL treated as secret | NEEDED if feature ships | v1.3 | M–L |
| 36 | Certificate pinning | NOT NEEDED (arbitrary hosts) | — | — |
| 37 | Minimal exported components; validated typed extras; `enforceIntentFilter`; `RECEIVER_NOT_EXPORTED` | NEEDED | MVP | S |
| 38 | `PendingIntent`: immutable + explicit, IDs only | NEEDED | MVP | S |
| 39 | Custom-scheme deep links / App Links | NOT NEEDED | — | — |
| 40 | `FileProvider` for sharing exports (single cache path, read-only grants) | Only with "share export" | later | S |
| 41 | Android Lint security checks as CI errors | NEEDED | MVP | S |
| 42 | No analytics, no ads, no crash SDK, no Play Services/Firebase | NEEDED (as a rule) | MVP onward | — |
| 43 | Play Console Android vitals as the only crash signal | NEEDED | At Play release | S |
| 44 | Local, user-shared diagnostics log | Optional | later | S–M |
| 45 | 2FA/passkeys on GitHub, Google/Play, recovery email | NEEDED | Now | S |
| 46 | Play App Signing; upload key offline, never in repo; local release signing | NEEDED | Before first Play upload | S |
| 47 | CI release signing via protected environment secrets | Optional | later | M |
| 48 | Secret scanning + push protection; Dependabot alerts/security/version updates with cooldown; private vulnerability reporting; branch protection; read-only `GITHUB_TOKEN` | NEEDED | MVP (repo setup) | S |
| 49 | Pin GitHub Actions by full SHA | NEEDED | MVP (first workflow) | S |
| 50 | Repository content filtering; no JitPack/dynamic versions; wrapper checksum + validation | NEEDED | MVP | S |
| 51 | Gradle dependency verification metadata | Optional | later | M + ongoing |
| 52 | Gradle dependency lockfiles | NOT NEEDED | — | — |
| 53 | Renovate | NOT NEEDED (Dependabot suffices) | — | — |
| 54 | CodeQL | Nice-to-have | v1.x | S |
| 55 | `SECURITY.md` | NEEDED | MVP | S |
| 56 | R8 shrinking on release, mapping files retained, release build tested | NEEDED | Before Play release | S–M |
| 57 | Root detection, tamper detection, Play Integrity, obfuscation-as-security, SBOM/SLSA | NOT NEEDED | — | — |

### 9.2 Security acceptance criteria before Play production release

1. Merged release manifest contains **only** allow-listed permissions; no `INTERNET` (unless v1.3 shipped), no `AD_ID`, no storage permissions.
2. Every component is `exported="false"` except the launcher activity, widget receivers, and (if shipped) the `.ics` import activity; Android Lint security category passes with zero errors.
3. Every `PendingIntent` is immutable and explicit (lint + code search for `FLAG_MUTABLE` returns nothing).
4. `dataExtractionRules` and `fullBackupContent` are both present; cloud backup is encrypted-only; a `bmgr` backup → reinstall → restore round-trip preserves events and re-schedules reminders.
5. Reminder notifications show no event title on a secure lock screen set to hide sensitive content; title-capable widgets are not offered on the lock screen (Android 16 QPR2+ device/emulator); privacy mode removes titles from already-placed widgets immediately.
6. Reboot test: reminders due during reboot/before first unlock fire after unlock; no crash in Direct Boot.
7. Exact alarms: `USE_EXACT_ALARM` is declared only if reminders ship; Play Console exact-alarm declaration completed; on an API 31–32 device with the special access revoked the app degrades gracefully.
8. If app lock shipped: gate holds for launcher, widget tap, notification tap, import intent, and widget config; recents thumbnail is blank while enabled.
9. If import shipped: the hostile-file corpus passes (no crash, no ANR, bounded memory/time); importing never creates reminders without explicit choice; import is undoable.
10. If subscriptions shipped: `http://` rejected, HTTPS→HTTP redirect rejected, oversize/gzip-bomb response aborted, LAN/loopback targets rejected, failed refresh keeps last good data.
11. Privacy policy live on a public, non-PDF URL; linked in Play Console **and** in-app; Data safety form submitted and consistent with the policy; advertising-ID declaration answered "No".
12. Release build: not debuggable, R8-shrunk and smoke-tested, mapping file archived, signed with the upload key under Play App Signing; upload keystore exists only offline/password manager.
13. Repo: secret scanning + push protection on, Dependabot on, actions SHA-pinned, `SECURITY.md` present, no secrets in history (run a one-off full-history secret scan before the first public release).
14. Target SDK meets the current Play requirement (API 36 as of Sept 2026; re-check at release time).
15. If the Play account is a post-Nov-2023 personal account: closed test with ≥ 12 testers for 14 continuous days completed.

---

## Sources

Google Play policy / Play Console
- Target API level requirements — <https://support.google.com/googleplay/android-developer/answer/11926878?hl=en>
- User Data policy (privacy policy, prominent disclosure) — <https://support.google.com/googleplay/android-developer/answer/10144311?hl=en>
- Data safety section guidance — <https://support.google.com/googleplay/android-developer/answer/10787469?hl=en>
- Permissions and APIs that Access Sensitive Information (exact alarm, full-screen intent) — <https://support.google.com/googleplay/android-developer/answer/16558241?hl=en>
- Preview of upcoming sensitive-permission changes (location, contacts; effective 2027-01-27) — <https://support.google.com/googleplay/android-developer/answer/16909972?hl=en>
- App testing requirements for new personal developer accounts — <https://support.google.com/googleplay/android-developer/answer/14151465?hl=en>
- Play App Signing — <https://support.google.com/googleplay/android-developer/answer/9842756?hl=en>
- Android developer verification — <https://support.google.com/android-developer-console/answer/16561738?hl=en>

Android platform
- Auto Backup — <https://developer.android.com/identity/data/autobackup>
- Backup security recommendations — <https://developer.android.com/privacy-and-security/risks/backup-best-practices>
- Direct Boot — <https://developer.android.com/privacy-and-security/direct-boot>
- Schedule alarms — <https://developer.android.com/develop/background-work/services/alarms>
- Exact alarms denied by default (Android 14) — <https://developer.android.com/about/versions/14/changes/schedule-exact-alarms>
- Biometric authentication — <https://developer.android.com/identity/sign-in/biometric-auth>
- Behaviour changes, apps targeting Android 16 — <https://developer.android.com/about/versions/16/behavior-changes-16>
- Behaviour changes, apps targeting Android 17 — <https://developer.android.com/about/versions/17/behavior-changes-17>
- Local network permission — <https://developer.android.com/privacy-and-security/local-network-permission>
- Network security configuration — <https://developer.android.com/privacy-and-security/security-config>
- Widgets on lock screen FAQ — <https://android-developers.googleblog.com/2025/03/widgets-on-lock-screen-faq.html>
- Jetpack Security release notes (deprecation) — <https://developer.android.com/jetpack/androidx/releases/security>
- SQLCipher for Android 16 KB page size — <https://www.zetetic.net/blog/2025/06/26/sqlcipher-for-android-16kb-page-size-support/>

GitHub
- Dependabot + Gradle version catalogs — <https://github.blog/changelog/2023-03-13-dependabot-version-updates-keeps-gradle-version-catalogs-up-to-date/>
- Dependabot default cooldown — <https://github.blog/changelog/2026-07-14-dependabot-version-updates-introduce-default-package-cooldown/>
- Dependabot options reference — <https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference>
- Actions policy: blocking and SHA pinning — <https://github.blog/changelog/2025-08-15-github-actions-policy-now-supports-blocking-and-sha-pinning-actions/>
- CodeQL for compiled languages — <https://docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/codeql-code-scanning-for-compiled-languages>
