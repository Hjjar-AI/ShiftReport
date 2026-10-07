# ShiftReport current technical state

Snapshot updated: 2026-10-06.

This document describes the implementation as it exists now. Future work belongs in [workRemains.md](workRemains.md); completed history belongs in [workDone.md](workDone.md).

## Product shape

ShiftReport is a single-module, Arabic-first, RTL Android ward and shift-handoff application. It is hospital-agnostic and configured on-device during first launch. Telegram provides transport, report delivery, immutable document storage, and shared pointers; local Room data remains the working clinical store on each device. The intended workflow is retrieve, edit, and publish, with last-write-wins at the shared pinned-state boundary. Guaranteed concurrent collaboration is outside the required scope, and a separate backend is not required.

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

The ward uses persistent Patients, Dashboard, and Activity tabs with saved selection. Compact windows use bottom navigation; widths from 600dp use a navigation rail. Activity renders full content with saved Arabic search/filter state and separate audit/publication scroll state; refresh failures retain cached entries and show an error. The side drawer keeps secondary actions in accordion submenus and groups clinician/role/shift context with a data-health panel for freshness, connectivity, synchronization, and conflicts. Connectivity uses validated Android network callbacks exposed by the ward ViewModel and collected with the screen lifecycle; callbacks are released when collection stops. Connectivity does not establish that clinical data is verified or current. The default patient header stays focused on patient count and compact filter presets. The ward FAB opens a compact readiness sheet before report review/send; adding a patient remains available from the top bar.

Patient browsing uses a simultaneous list-detail layout when the ward content has at least 760dp width and 360dp height, after navigation space. The list is a single patient column in panes. A 48dp divider retains its width fraction during the session, clamps to readable minimums after resizing, and supports RTL-aware dragging, keyboard adjustment/reset, visible focus, and accessibility resizing. Without a selected patient, the second pane displays existing report readiness and recent local activity. Tall medium content can show that support below the list; compact/short layouts use a supporting sheet and full-screen detail. Phone card taps continue to edit; pane card taps select detail.

Jetpack WindowManager 1.2.0 is newly added for lifecycle-observed separating/occluding folds. Shared ward layout code translates window hinge bounds into local physical coordinates, reserves margins, and positions suitable list/detail regions on opposite sides of vertical or horizontal folds. Small regions fall back to the larger safe region. Ward bars/navigation, patient details, report/support sheets, and the add/edit/copy form container use this geometry. Selected patient, detail section/scroll, and list state are retained across presentation changes; deletion or switching to a shift without that patient clears selection. The owner confirmed a successful debug build on 2026-10-06; real-device folding/keyboard/accessibility validation remains pending. Subsequent warning cleanup has source-level verification only.

## Main technology

| Concern | Current implementation |
|---|---|
| UI | Jetpack Compose Material 3, RTL-first; WindowManager folding features |
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
- `WardMutationRepository` commits ward patient add/edit (including badges and priority), delete/restore, rollover, and shift doctor/sort changes with the pending-sync marker and audit records in one Room transaction on the IO dispatcher. Stale or failed mutations roll back the entire operation.
- Audit before-values are read within that transaction; new-patient ID collisions, capacity, and sort-order assignment are checked there as well. Current-shift editability is rechecked at this repository boundary.
- Stale saves in the patient editor, shift-doctor picker, and sort sheet open a draft/saved-value review. The rejected draft remains available during the open editor session, even after loading the saved values. Choosing a version returns to editing without saving; the next save uses the explicitly reviewed revision and can reject another intervening edit. Missing/deleted patients cannot be rebased for editing.
- Shift controls capture their opening revision instead of using a newer observed revision with an older draft. The sort sheet remains open on save failure. These review drafts are session-local, not a new persisted clinical record.
- `DoctorMutationRepository` commits doctor add/edit/delete, CSV import, admin promotion/demotion, and supervisor-group changes with live admin authorization, stale snapshot checks, pending state, and audit together. New doctors are checked for capacity and identity collisions inside the transaction. Clinical-role changes with active patient references are refused; deletion protects active references and the current shift roster. Stale doctor edits offer session-local draft/saved review, with a separate subsequent save and no PIN display or draft persistence.
- These boundaries cover local ward mutations and basic doctor edits; remote synchronization and backup restore retain separate orchestration. CSV import retains a session-local plan containing the reviewed registry, actor identity, and proposed records; confirmation rejects an intervening registry/actor change and revalidates clinical references and permission rules inside the transaction. Administrator dialogs pass their selected doctor snapshot, prevent repeated submission, and audit the verified live actor. Supervisor-group drafts retain their baseline until explicitly reloaded after an observed change; they cannot overwrite newer role, identity, or destination data.

## Project configuration and onboarding

`ProjectConfigStore` holds the hospital name, bot token, group ID, optional topic IDs, initialization state, and demo flag in encrypted device-local preferences.

- Demo creates no database patients and starts no background synchronization.
- Join is the default path. It accepts manual settings or an AES-GCM/PBKDF2 encrypted `.srjoin` file exported by an administrator.
- Create validates the Telegram bot/group, creates the initial administrator registry, and initializes the project.
- The join-file passphrase is never stored or embedded. The current file is reusable; server-enforced one-time expiry is an optional future capability, not a prerequisite for the Telegram-based app.

## Authentication and authorization

- Clinician sessions are stored in encrypted preferences.
- PINs are PBKDF2 hashes with random salts and constant-time comparison.
- Optional biometric unlock and configurable automatic locking protect foreground access.
- `AdminAuthorizer` checks both the active encrypted session and the live doctor registry.
- Doctor mutations, promotion/demotion, announcements, supervisor-group configuration, and provisioning export enforce live admin authority below navigation.
- The legacy VBA importer is navigation-guarded; domain-level authorization there remains deferred because migration/importer files are outside routine review scope.

## Synchronization and concurrency

Patient synchronization uses CSV snapshots, a saved base snapshot, three-way field merging, explicit conflict choices, immutable uploads, recovery snapshots, and a publication journal. Patient and doctor synchronization are serialized per channel on one device. Pending local edits are uploaded before a background pull can replace them.

Telegram pinned metadata is a shared pointer without atomic compare-and-swap. The owner accepts last-write-wins for competing remote publications. Existing merge and stale-pointer checks remain implemented; this scope decision does not replace them with unconditional overwrites. Consequently:

- Local stale-write protection is strong on one device.
- Patient three-way merge reduces cross-device loss.
- Simultaneous remote publication can still race at the pinned-pointer boundary; the last successful pointer write wins. Preserving every competing publication is outside the required scope.
- Doctor registry synchronization persists a PIN-free base snapshot in encrypted Room settings and merges additions, deletions, and each field supported by the existing Telegram wire format. The name, gender, clinical role/supervisor-group pair, Telegram ID, title, and admin rank/permanent pair participate in the merge. Telegram username, portable extra options, and local PIN/alias options are not sent by that legacy format and remain local or CSV-export data.
- Conflicting fields, duplicate names/Telegram IDs, competing admin-rank assignments, and protected doctor deletions appear in the doctor-registry screen for explicit admin choices. Existing installations with pending changes and no saved base require conservative review of differences. No local registry replacement occurs while conflicts remain.
- Merge application rechecks the full local snapshot and saved base, protects permanent admins, assigned-patient references, and the current shift roster, and atomically records the registry, remote base, sync cursor, pending marker, and audit before/after values. Explicit resolution requires live local admin authority plus active admin status in the reviewed remote registry, and respects higher-rank/permanent-admin restrictions.
- Doctor mutation/pending writes share a transaction. An unchanged device-local PIN is invalidated when a doctor is linked to a different Telegram identity; fresh explicitly supplied PINs are retained. Edits made during registry upload remain pending. Conflict-review state is session-local; after process death, synchronization reconstructs it from the persisted base and local registry.
- Unchanged merged doctor rows keep their existing timestamps and local name components, avoiding timestamp-only stale-editor failures after an unchanged pull.
- The full doctor pointer is checked before publication and again during pinned-state update, but this remains best-effort detection rather than an atomic cross-device transaction.
- No authoritative service is required for the accepted retrieve/edit/publish workflow. Stronger concurrent-write guarantees would be a separate future requirement.

## Reports and handoff

`ReportBuilder` reads the current shift, non-deleted patients, doctor registry, and sort specification together in a Room transaction on the IO dispatcher. The report preview places selected shift doctors in its first summary section.

Text/PDF sending retains the preview snapshot. `ReportReview` compares clinical content and relevant doctor/delivery fields before synchronization, after doctor synchronization, and after patient publication. A mismatch refreshes the preview and requires another explicit confirmation before report delivery. Delivery, including supervisor PDFs and destinations, uses the accepted snapshot rather than rereading individual doctor rows. Supervisor sends synchronize before delivery; a partial failure reports how many recipients were reached and requires deliberate recipient review instead of offering the combined-report retry.

These checks preserve review intent but do not create a transaction across synchronization and Telegram delivery. CSV synchronization may publish before a later mismatch stops text/PDF delivery; competing remote publications follow the accepted last-write-wins policy, while interrupted/partial delivery still needs manual recovery validation. Local preview/save/share continue to assemble a fresh snapshot on request.

Both PDF styles now use compact row layouts with multiple patients per page. A patient row is measured before drawing; when the remaining page space is insufficient, the complete row moves to the next page. Exceptionally large rows are fitted without discarding clinical text. Classic uses only restrained header and zebra colors, while Elegant Row uses white patient rows and slightly more generous typography to keep exported files print-friendly and smaller than the former card output.

Administrators can export and import a versioned UTF-8 doctor-registry CSV through Android's document picker. The file carries every portable doctor field, including stable ID, names, gender, clinical role, supervisor group, Telegram identity, title, rank, permanent-admin state, portable options, and timestamps. Import parses and validates the complete file on IO, validates its proposed merge transactionally before preview, and previews the resulting active/admin/deleted counts. It merges by stable ID/Telegram/name, preserves local aliases and PINs for unchanged Telegram identities, protects current/permanent administrators and rank restrictions, and rechecks the full reviewed registry and actor at commit. The local mutation, pending marker, and audit commit together; the session-local plan is cleared before publishing to Telegram, so upload failure leaves committed data pending without repeating the import. PIN hashes never enter the plaintext CSV; newly imported doctors establish a new PIN after their first verified Telegram login.

The ward FAB shows a readiness count derived from shared `ReportReadiness` rules plus unresolved merge conflicts. Opening it presents an express sheet with patient count, freshness, and the correct review/send action rather than publishing immediately. Current readiness checks cover missing resident, supervisor, diagnosis, and treatment. Structured tasks and explicit critical-change acknowledgment remain planned work; structured acuity, contingencies, guided I-PASS, extended readiness, and accepted-handoff change history were removed from the roadmap by the owner.

Ward cards support comfortable/compact density, individual expansion, session-local pinning, relative freshness, selected state, and consolidated accessibility summaries/actions. Live editable cards expose edit, badge, priority, copy, and confirmed-delete custom actions; read-only/detail-only cards omit unavailable or no-op actions. In compact layouts, tapping an editable card opens editing and long-pressing opens tabbed details; pane layouts use taps to select detail. The list keeps its existing structured grouping and adds quick filter presets. Patient details keep the section navigator sticky, expose quick actions, and show patient-specific audit history when available. Patient forms show unsaved state, section completion, all validation failures together, field error states, IME-safe layout, paired compact fields, and tinted section headers. Existing-patient editing puts clinical fields before admission and identity fields. Patient status supports an ordered list of free-text badges, each with an optional low/medium/high level; legacy fixed-warning and single-badge data is converted on read through the existing persistence columns.

## Security boundaries

- Room is encrypted through SQLCipher.
- Project configuration and sessions use encrypted preferences.
- Android application backup is disabled.
- Backup and project-join exports use authenticated AES-GCM encryption with PBKDF2-derived keys.
- Credentials and signing material are ignored by Git and must remain local.
- The release build currently references the debug signing configuration. Production distribution requires a protected release keystore and a deliberate signing setup.
- Ordinary clients still hold a broadly privileged reusable Telegram bot token. Removing it from client devices would require an optional token-proxy service; it is not a prerequisite for the current Telegram-based design.

## Known high-priority limitations

1. Doctor merge/edit review and reviewed-report sending still need device validation and the intended sequential retrieve/edit/publish and interrupted-delivery scenarios before release.
2. Production signing still needs configuration; administrative import/rank/destination transactions need runtime failure/cancellation validation.
3. Provisioning files are encrypted but reusable; credential rotation and safe project reconnection remain unfinished. Server-enforced one-time expiry is optional.
4. Structured patient tasks and closed-loop critical acknowledgments do not yet exist in the core model.
5. Adaptive panes, fold handling, Activity restoration, forms, icon direction, and accessibility actions need hands-on device validation. Structured task counts on cards remain dependent on the P1 task model.

## Documentation discipline

- Update this file when the technical reality changes.
- Keep unfinished work in `workRemains.md` and completed work in `workDone.md`.
- Keep secrets and machine-specific instructions in ignored local files.
- Follow [AGENTS.md](AGENTS.md) for automated contribution rules.
