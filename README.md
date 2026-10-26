# ShiftReport

ShiftReport is an Arabic-first Android application for hospital shift handoff, ward patient tracking, Telegram-backed report delivery, and offline clinical continuity. The APK is hospital-agnostic: one build can be configured for different hospitals without embedding their bot token, group IDs, or administrator identity.

The application uses Jetpack Compose, Room with SQLCipher, Hilt, WorkManager, Kotlin coroutines, and the Telegram Bot API. It supports Android 6.0/API 23 and newer.

## Repository layout

| Folder | Contents |
|---|---|
| [`android/`](android) | Android Studio/Gradle project, app source, wrapper and Android-local settings |
| [`desktop/`](desktop/README.md) | Python CustomTkinter client for Windows and Linux, setup and launch scripts |
| [`docs/`](docs/README.md) | Shared architecture, current state, guides and roadmap |
| `local/` | Ignored private local artifacts, including the old Excel workspace |

Open **`android/`** in Android Studio. For the PC client, use the launch scripts inside **`desktop/`**. Each platform keeps its existing application identity and shared Telegram formats. Root-level Git metadata and repository instructions stay shared.

## Current capabilities

Continuous ward, optional profiles, multiline clinical/labs and inline text/done tasks; search/group/stored sorting, recycle, retained drafts and Save-next. Reviewed A4/A5 reports, unlimited active roster, doctor CSV, encrypted local data/PIN/biometrics and protected-file join. Telegram uses manual complete-snapshot publication and exact-artifact delivery with durable UNKNOWN; clinical Save is last-save-wins. Demo is memory-only. [Current behavior and limits](docs/status/current-state.md).

## First launch

[Arabic Telegram setup](docs/guides/telegram-setup.md).

| Path | Use |
|---|---|
| Demo | In-memory beverage patients/tree doctors; real offline forms/tasks/cards/dashboard/preferences and explicit DEMO-labelled PDF export. No credentials, Telegram or clinical DB writes; reset/exit discards changes. |
| Join (default) | Import administrator’s protected `.srjoin.json`/password; bot/group/topics/key come from the file. Validation retrieves pinned doctors; no manual join fields. |
| Create | BotFather bot, group admin/send/pin rights, hospital/group/optional topics and first admin; optionally generate shared encryption key. Existing Android pin requires explicit reviewed override; old history remains. |

Token replacement: admin → [BotFather](https://t.me/BotFather) revoke/regenerate same bot → Settings/Security/Reconnect/Replace token → validate/save → export new protected join for other devices. Retains clinical state/key; other project/key/topics blocked. Hospital name is configuration; emblem/About remain product identity.

## Security and data handling

Credentials/topics/key live in encrypted preferences, outside APK/Room/backups. Optional topics: blank means General. SQLCipher protects clinical Room; Android backup disabled. Join/backup files are password encrypted; share passwords separately.

Optional AES-256-GCM protects Telegram bundles/tasks, doctors, pinned state and text documents; PDFs/captions/Telegram metadata deliberately readable. Keep protected join/password for key recovery; existing unencrypted projects retain their mode. Conversion/key rotation are not implemented.

Admin mutations are authorized below navigation; clinical saves retain existence/shift/assignment checks without revision prompts. Telegram offers reviewed complete snapshots/last-successful-write-wins, without atomic simultaneous collaboration or required backend. [Architecture](docs/reference/architecture.md). Appearance/accent are independent; uncommon PDF settings stay Advanced; expanded cards retain full content.

## Implementation status

[Current evidence/limits](docs/status/current-state.md#verification-and-limits) · [Production signing and deferred proposals](docs/status/roadmap.md) · [Remaining device/runtime checks](docs/status/testing-handoff.md).

## Project documentation

[Documentation index](docs/README.md) · [Arabic setup](docs/guides/telegram-setup.md) · [Desktop workflow](desktop/README.md) · [Agent rules](AGENTS.md).

## Developer commands

Run these existing Android commands from the Android project directory:

```bash
cd android

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

Generated files are normally located under these paths relative to `android/`:

```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
app/build/outputs/bundle/release/
```

Release requires a protected production keystore and has no debug-key fallback. See the [cache/signing guide](docs/guides/android-build.md); never commit signing material or credentials.
