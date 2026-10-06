# ShiftReport current technical state

Snapshot updated: 2026-10-06.

This document describes the implementation as it exists now. Future work belongs in [workRemains.md](workRemains.md); completed history belongs in [workDone.md](workDone.md).

## Product shape

ShiftReport is a single-module, Arabic-first, RTL Android ward and shift-handoff application. It is hospital-agnostic and configured on-device during first launch. Telegram currently provides transport, report delivery, immutable document storage, and shared pointers; local Room data remains the working clinical store on each device.

The app supports API 23 through target/compile SDK 34. It uses Java 17, Kotlin 1.9.0, Android Gradle Plugin 8.5.0, and Jetpack Compose with the 2023.10.01 BOM. Do not infer that these versions should be upgraded merely because newer versions exist.

## Startup and navigation flow

```text
MainActivity
  -> RTL theme and configured font scale
  -> WardAppRoot
      -> skippable splash
      -> project setup when uninitialized
          -> Demo (in-memory dummy ward)
          -> Join (default; encrypted file or manual configuration)
          -> Create (new Telegram-backed project)
      -> clinician login
      -> PIN/biometric unlock when required
      -> WardNavHost
          -> Ward
          -> Report preview/send
          -> Admin / doctors / announcement
          -> Settings
          -> About
```

The compact ward uses bottom navigation for Patients, Dashboard, and Activity. Expanded layouts use a navigation rail. The side drawer keeps secondary actions in accordion submenus, shows clinician/role/shift/sync health, and promotes administration actions for administrators. The ward FAB opens a compact readiness sheet before report review/send; adding a patient remains available from the top bar.

## Main technology

| Concern | Current implementation |
|---|---|
| UI | Jetpack Compose Material 3, RTL-first |
| State | Hilt ViewModels with StateFlow |
| Dependency injection | Hilt |
| Local persistence | Room 2.6.1 over SQLCipher |
| Secrets/preferences | EncryptedSharedPreferences |
| Networking | OkHttp and Telegram Bot API |
| Serialization | kotlinx.serialization JSON plus CSV codecs |
| Background work | WorkManager and coroutines |
| Authentication | Clinician identity, PBKDF2 PIN hashes, optional biometric unlock |
| Reports | Text reports plus Classic and Elegant Row PDF renderers |
| Minimum Android | API 23 |

## Source layout

- `config/`: application constants, project configuration, Telegram topic resolution, and encrypted provisioning files.
- `data/db/`: Room database, entities, and DAOs.
- `data/model/`: domain-facing patient, doctor, shift, and synchronization models.
- `data/repository/`: persistence boundaries and local optimistic mutation rules.
- `domain/auth/`: session handling, PIN hashing, biometric support, bootstrap, and live admin authorization.
- `domain/backup/`: password-encrypted local backup and restore.
- `domain/patient/`: patient validation and card-related domain rules.
- `domain/doctor/`: doctor naming/validation plus the versioned UTF-8 CSV interchange codec.
- `domain/report/`: report assembly, readiness checks, and text generation.
- `domain/sort/`: stable patient sorting and grouping rules.
- `network/telegram/`: Telegram Bot API client and protocol models.
- `sync/`: CSV codecs, merge/conflict logic, publication journal, and synchronization orchestration.
- `pdf/`: Shared non-splitting row renderer used by Classic and Elegant Row PDF styles.
- `ui/`: Compose screens, navigation, themes, dialogs, and ViewModels.
- `migration/`: legacy import functionality; excluded from routine review unless explicitly requested.

## Data model and persistence

The encrypted Room database contains patients, doctors, shifts, settings, synchronization state, and audit entries. Patient and shift records include revisions used for local stale-write protection.

Current patient mutation behavior:

- Edit (including custom badge changes), delete, restore, and priority changes compare the caller's expected revision inside a Room transaction.
- Successful patient mutations increment the revision.
- Shift doctor and sort changes use revision-checked DAO updates.
- Guided rollover refuses to insert when the target shift is no longer empty, preventing duplicate local application.
- Pending-sync and audit writes are ordered safely but are not yet part of the same transaction as every mutation; this remains active P0 work.

## Project configuration and onboarding

`ProjectConfigStore` holds the hospital name, bot token, group ID, optional topic IDs, initialization state, and demo flag in encrypted device-local preferences.

- Demo creates no database patients and starts no background synchronization.
- Join is the default path. It accepts manual settings or an AES-GCM/PBKDF2 encrypted `.srjoin` file exported by an administrator.
- Create validates the Telegram bot/group, creates the initial administrator registry, and initializes the project.
- The join-file passphrase is never stored or embedded. The current file is reusable; server-issued one-time expiry remains future work.

## Authentication and authorization

- Clinician sessions are stored in encrypted preferences.
- PINs are PBKDF2 hashes with random salts and constant-time comparison.
- Optional biometric unlock and configurable automatic locking protect foreground access.
- `AdminAuthorizer` checks both the active encrypted session and the live doctor registry.
- Doctor mutations, promotion/demotion, announcements, supervisor-group configuration, and provisioning export enforce live admin authority below navigation.
- The legacy VBA importer is navigation-guarded; domain-level authorization there remains deferred because migration/importer files are outside routine review scope.

## Synchronization and concurrency

Patient synchronization uses CSV snapshots, a saved base snapshot, three-way field merging, explicit conflict choices, immutable uploads, recovery snapshots, and a publication journal. Patient and doctor synchronization are serialized per channel on one device. Pending local edits are uploaded before a background pull can replace them.

Telegram pinned metadata is still a shared pointer without atomic compare-and-swap. Consequently:

- Local stale-write protection is strong on one device.
- Patient three-way merge reduces cross-device loss.
- Simultaneous remote publication can still race at the pinned-pointer boundary.
- Doctor registry synchronization rejects stale whole-registry publication but does not yet offer a complete three-way registry merge.
- A transactional authoritative service is the required long-term fix; Telegram should then remain delivery/archive infrastructure.

## Reports and handoff

`ReportBuilder` reads the current shift, current non-deleted patients, doctors, and sort specification each time a preview, save, share, or send operation begins. Both PDF styles therefore render a fresh repository snapshot.

Both PDF styles now use compact row layouts with multiple patients per page. A patient row is measured before drawing; when the remaining page space is insufficient, the complete row moves to the next page. Exceptionally large rows are fitted without discarding clinical text. Classic uses only restrained header and zebra colors, while Elegant Row uses white patient rows and slightly more generous typography to keep exported files print-friendly and smaller than the former card output.

Administrators can export and import a versioned UTF-8 doctor-registry CSV through Android's document picker. The file carries every portable doctor field, including stable ID, names, gender, clinical role, supervisor group, Telegram identity, title, rank, permanent-admin state, portable options, and timestamps. Import validates the complete file before writing, previews active/admin/deleted counts, merges by stable ID/Telegram/name, preserves local PINs, protects the current and permanent administrators, records an audit entry, and publishes the resulting active registry. PIN hashes never enter the plaintext CSV; newly imported doctors establish a new PIN after their first verified Telegram login.

The ward FAB shows a readiness count derived from shared `ReportReadiness` rules plus unresolved merge conflicts. Opening it presents an express sheet with patient count, freshness, and the correct review/send action rather than publishing immediately. Current readiness checks cover missing resident, supervisor, diagnosis, and treatment; structured acuity, tasks, contingencies, and receiver acknowledgment remain planned work.

Ward cards support comfortable/compact density, individual expansion, session-local pinning, relative freshness, and consolidated accessibility summaries/actions. The list keeps its existing structured grouping and adds quick filter presets. Patient details keep the section navigator sticky, expose quick actions, and show patient-specific audit history when available. Patient forms show unsaved state, section completion, all validation failures together, field error states, IME-safe layout, paired compact fields, and tinted section headers. Patient status uses a free-text optional badge with an optional low/medium/high level; legacy fixed-warning data is converted on read through the existing persistence columns.

## Security boundaries

- Room is encrypted through SQLCipher.
- Project configuration and sessions use encrypted preferences.
- Android application backup is disabled.
- Backup and project-join exports use authenticated AES-GCM encryption with PBKDF2-derived keys.
- Credentials and signing material are ignored by Git and must remain local.
- The release build currently references the debug signing configuration. Production distribution requires a protected release keystore and a deliberate signing setup.
- Ordinary clients still hold a broadly privileged reusable Telegram bot token. Removing it from client devices depends on the future authoritative service.

## Known high-priority limitations

1. Telegram cannot be the final authoritative multi-writer database.
2. Patient mutation, pending-sync state, and audit recording are not universally committed in one database transaction.
3. Doctor registry changes need a last-synced base and explicit three-way conflict workflow.
4. Provisioning files are encrypted but reusable rather than server-issued, signed, one-time, and expiring.
5. Structured I-PASS fields, tasks, receiver synthesis, and closed-loop critical acknowledgments do not yet exist in the core model.
6. Large-screen patient browsing still needs a simultaneous list-detail layout.

## Documentation discipline

- Update this file when the technical reality changes.
- Keep unfinished work in `workRemains.md` and completed work in `workDone.md`.
- Keep secrets and machine-specific instructions in ignored local files.
- Follow [AGENTS.md](AGENTS.md) for automated contribution rules.
