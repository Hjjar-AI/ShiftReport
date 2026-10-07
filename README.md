# ShiftReport

ShiftReport is an Arabic-first Android application for hospital shift handoff, ward patient tracking, Telegram-backed report delivery, and offline clinical continuity. The APK is hospital-agnostic: one build can be configured for different hospitals without embedding their bot token, group IDs, or administrator identity.

The application uses Jetpack Compose, Room with SQLCipher, Hilt, WorkManager, Kotlin coroutines, and the Telegram Bot API. It supports Android 6.0/API 23 and newer.

## Current capabilities

- Structured ward list with patient details, custom badges, priority, responsible clinicians, search, filters, grouping, and sorting. Full-screen patient editing keeps clinical sections first and demographics last; new-patient entry starts with demographics.
- Structured patient tasks with optional doctor assignment (unassigned by default), priority, deadlines, pending/done state, and completion actor/time. Collapsed cards show per-patient pending and overdue counts; the dashboard shows ward totals and task filters. Overdue tasks are included in the pending count.
- Dashboard, personal patient view (including assigned tasks), activity history, recycle bin, guided rollover of unfinished tasks, and stored-shift viewing.
- Top-bar expand/collapse-all control, individual card expansion, direct drawer destinations, and grouped Settings shortcuts.
- Classic and Elegant Row PDF reports generated from a fresh current-shift snapshot. Multiple patients share a page, but a patient row is moved intact to the next page instead of splitting.
- Administrator-controlled UTF-8 doctor-registry CSV import/export for fresh starts and spreadsheet editing.
- Telegram report delivery, immutable CSV snapshots, background synchronization, three-way patient merging, and explicit conflict review.
- Local stale-edit protection through patient and shift revisions.
- Encrypted local database, encrypted project preferences, PIN/biometric access, encrypted backups, and verified admin authorization for core privileged actions.
- Hospital/project setup with Demo, file-only Join, and Create paths, plus optional encryption of Telegram project data.
- A splash that dismisses on tap or after 10 seconds; Splash and About use the white-field emblem and current credits.
- Unified neutral surfaces with independent System/Light/Dark appearance and accent choices, readable typography, consistent status colors, compact cards, and advanced PDF settings. Individual expansion animates briefly; bulk expansion is immediate.

## First launch

Choose one of three paths:

- **Demo:** open a read-only sample ward with in-memory dummy patients named after flowers and fruits. Demo requires no Telegram credentials and saves or synchronizes no patient data.
- **Join project (default):** join the hospital Telegram group, then import the administrator-provided password-encrypted `.srjoin` file. Connection values, topic IDs, and any shared data-encryption key come from the file; manual joining is unavailable. The app validates the bot and group and downloads the pinned doctor registry.
- **Create project:** create a Telegram bot through BotFather, add it as an administrator to a new supergroup with permission to send files and pin messages, then enter the hospital name, token, group ID, optional topic IDs, and first administrator identity. The creator can optionally enable Telegram-data encryption, generating a random shared project key before the first publication.

To replace a bot token, an administrator revokes/regenerates it through [BotFather](https://t.me/BotFather), then uses **Settings → Security and privacy → Reconnect project → Replace token as administrator**. Export a fresh protected join file afterward. Other devices import it through Reconnect in Settings or login. The app validates the replacement before saving, retains the data key and local records, and rejects different-project/key/topic files. Switching projects around an existing database is blocked.

After setup, the configured hospital name appears in the clinical header and drawer. The existing application logo and About page remain the permanent product identity and credits page.

## Security and data handling

The bot token and project identifiers are stored in encrypted Android preferences. They are not compiled into the APK, stored in Room, or included in application backups. Topic IDs are optional during project creation; an empty topic posts to General. Members joining by file do not enter topic IDs.

Clinical data is held in a SQLCipher-encrypted Room database. Android backup is disabled. Exported project-join and backup files are password encrypted; their passphrases are not embedded in the files and should be shared separately.

New projects can optionally encrypt Telegram patient bundles (including tasks), doctor registries, pinned synchronization state, announcements, and text reports with a random shared AES-256-GCM key. Downloads are decrypted locally before parsing. The key is stored in encrypted device settings and travels inside the password-protected join file. Text reports are delivered as encrypted documents when encryption is enabled; PDF files and their captions remain readable. Telegram metadata remains visible. Keep the protected join file and its passphrase for key recovery. Existing projects without a key retain their previous storage mode; conversion and key rotation are not implemented.

Admin-only operations are checked against the live local doctor registry below the navigation layer. Patient edits and shift controls reject stale local revisions rather than silently overwriting newer data.

Telegram remains the shared synchronization and delivery channel. The accepted workflow is retrieve, edit, and publish; the last successful pinned-state write wins. Telegram pinned state is not an atomic multi-writer database. Guaranteed simultaneous collaboration is outside scope, and no authoritative backend is required. Existing revision checks, merges, conflict review, and report confirmation remain in place. See [designAndArchitecture.md](designAndArchitecture.md).

Appearance and accent are configured separately under **Settings → App interface**. Teal is the default accent; every accent supports light and dark appearance. Uncommon report options are under **Settings → Report → Advanced PDF options**. Collapsed cards summarize clinical content; expand a card for full details.

## Implementation status

Structured tasks and per-patient counts are implemented. Remaining near-term work is production signing. Safe same-project credential replacement/reconnect is implemented. User acknowledgment is not required. Voice dictation is a far-future proposal; specialty templates are a far-far-future proposal.

The latest changes have source/static checks only. The most recent supplied build failed on experimental Material API usage in `PatientTaskEditor.kt`; opt-ins are now corrected in source, without a subsequent build. The earlier successful debug build does not validate these changes. Database schema is 5, patient CSV schema is 7, and doctor-registry CSV schema is 1. Application version remains 1.0 (code 1); toolchain/dependency versions are unchanged.

## Project documentation

- [currentState.md](currentState.md): current technical architecture, flows, invariants, and limitations.
- [workRemains.md](workRemains.md): active roadmap ordered by safety and dependency.
- [workDone.md](workDone.md): completed work removed from the active roadmap.
- [AGENTS.md](AGENTS.md): repository rules for coding agents and automated contributors.
- [designAndArchitecture.md](designAndArchitecture.md): accepted architecture scope and external design basis.
- [debugging.md](debugging.md): focused device, import, encryption, task, and synchronization diagnostics.

## Developer commands

```bash
# Build the debug APK
./gradlew assembleDebug

# Build the release APK
./gradlew assembleRelease

# Build the release Android App Bundle (AAB)
./gradlew bundleRelease

# Compile Kotlin without packaging an APK
./gradlew :app:compileDebugKotlin

# Install the debug APK on a connected device
./gradlew installDebug

# Clean generated build files
./gradlew clean

# List all available Gradle tasks
./gradlew tasks

# Build the debug APK with detailed error output
./gradlew assembleDebug --stacktrace

# Stop active Gradle daemons
./gradlew --stop
```

Generated files are normally located under:

```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
app/build/outputs/bundle/release/
```

The release build currently uses the debug signing configuration. Configure a protected production keystore before distributing a real release; never commit signing material or credentials.
