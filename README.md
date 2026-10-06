# ShiftReport

ShiftReport is an Arabic-first Android application for hospital shift handoff, ward patient tracking, Telegram-backed report delivery, and offline clinical continuity. The APK is hospital-agnostic: one build can be configured for different hospitals without embedding their bot token, group IDs, or administrator identity.

The application uses Jetpack Compose, Room with SQLCipher, Hilt, WorkManager, Kotlin coroutines, and the Telegram Bot API. It supports Android 6.0/API 23 and newer.

## Current capabilities

- Structured ward list with patient details, warnings, priority, responsible clinicians, search, filters, grouping, and sorting.
- Dashboard, personal patient view, activity history, recycle bin, guided rollover, and stored-shift viewing.
- Classic and Elegant Row PDF reports generated from a fresh current-shift snapshot. Multiple patients share a page, but a patient row is moved intact to the next page instead of splitting.
- Administrator-controlled UTF-8 doctor-registry CSV import/export for fresh starts and spreadsheet editing.
- Telegram report delivery, immutable CSV snapshots, background synchronization, three-way patient merging, and explicit conflict review.
- Local stale-edit protection through patient and shift revisions.
- Encrypted local database, encrypted project preferences, PIN/biometric access, encrypted backups, and verified admin authorization for core privileged actions.
- Hospital/project setup with Demo, Join, and Create paths.
- Accessible semantic states and configurable themes, typography, card density, PDF layout, and palettes.

## First launch

Choose one of three paths:

- **Demo:** open a read-only sample ward with in-memory dummy patients named after flowers and fruits. Demo requires no Telegram credentials and saves or synchronizes no patient data.
- **Join project (default):** join the hospital Telegram group, then import the administrator-provided encrypted `.srjoin` file or manually enter the connection values. The app validates the bot and group and downloads the pinned doctor registry.
- **Create project:** create a Telegram bot through BotFather, add it as an administrator to a new supergroup with permission to send files and pin messages, then enter the hospital name, token, group ID, optional topic IDs, and first administrator identity.

After setup, the configured hospital name appears in the clinical header and drawer. The existing application logo and About page remain the permanent product identity and credits page.

## Security and data handling

The bot token and project identifiers are stored in encrypted Android preferences. They are not compiled into the APK, stored in Room, or included in application backups. Topic IDs are optional; an empty topic posts to General.

Clinical data is held in a SQLCipher-encrypted Room database. Android backup is disabled. Exported project-join and backup files are password encrypted; their passphrases are not embedded in the files and should be shared separately.

Admin-only operations are checked against the live local doctor registry below the navigation layer. Patient edits and shift controls reject stale local revisions rather than silently overwriting newer data.

Telegram is currently the synchronization and delivery transport, not a transactional multi-writer database. It cannot provide an atomic compare-and-swap operation for pinned state, so complete cross-device concurrency safety still requires the authoritative service described in [workRemains.md](workRemains.md).

## Project documentation

- [currentState.md](currentState.md): current technical architecture, flows, invariants, and limitations.
- [workRemains.md](workRemains.md): active roadmap ordered by safety and dependency.
- [workDone.md](workDone.md): completed work removed from the active roadmap.
- [AGENTS.md](AGENTS.md): repository rules for coding agents and automated contributors.

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
